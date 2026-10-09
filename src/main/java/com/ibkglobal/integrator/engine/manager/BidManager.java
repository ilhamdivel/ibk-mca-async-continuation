package com.ibkglobal.integrator.engine.manager;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ibkglobal.integrator.config.ConstantCode;
import com.ibkglobal.integrator.engine.model.BidInfo;
import com.ibkglobal.integrator.engine.model.BidInfo.BidStatus;
import com.ibkglobal.integrator.engine.timer.IBKTimeout;
import com.ibkglobal.integrator.exception.ErrorType;
import com.ibkglobal.integrator.exception.IBKExceptionMCA;
import com.ibkglobal.integrator.util.ErrorUtil;
import com.ibkglobal.integrator.util.SystemUtil;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.log.LogType;
import com.ibkglobal.message.InfraType;

@Component
public class BidManager {

  @Autowired
  IBKTimeout<BidInfo> ibkTimeoutBid;

  private Map<String, BidInfo> bidInfoList = new ConcurrentHashMap<>();

  public Map<String, BidInfo> getBidInfoList() {
    return bidInfoList;
  }

  /**
   * Release outcome, so each caller can keep its own "no BID found" policy.
   */
  public static enum ReleaseResult {
    DELIVERED, // a thread was waiting (WAIT) and has been notified
    PARKED, // arrived before the dummy ack, result kept for it (RELEASED)
    DUPLICATE, // a release was already parked for this key, ignored
    NOT_FOUND // no BID registered (late, unknown, or pre-register skipped)
  }

  /**
   * Pre-register a BID key before the request is sent to the host, so a release
   * that overtakes the dummy ack has somewhere to land.
   *
   * @return the registered BidInfo, or null when the key is already in use
   */
  public BidInfo bidPreRegister(String key) {
    BidInfo bidInfo = new BidInfo();
    bidInfo.setName(key);
    bidInfo.setStatus(BidStatus.PENDING);
    bidInfo.setTimeStamp(SystemUtil.getCurrentTime());

    BidInfo existing = getBidInfoList().putIfAbsent(key, bidInfo);

    if (existing != null) {
      LogManager.getLogger(LogType.ROOT)
          .info("Bid PreRegister skipped, key already registered : " + key + " / " + existing.getStatus());
      return null;
    }

    return bidInfo;
  }

  /**
   * Remove a pre-registered entry that never turned into a wait (normal response,
   * error, or a parked release whose dummy ack never came). Identity-checked so
   * it never removes an entry owned by someone else.
   */
  public void bidUnregister(String key, BidInfo expected) {
    getBidInfoList().computeIfPresent(key, (k, cur) -> {
      if (cur != expected) {
        return cur;
      }

      if (cur.getStatus() == BidStatus.RELEASED) {
        LogManager.getLogger(LogType.ROOT).info("Bid released but dummy ack never received, discarded : " + key);
      }

      return (cur.getStatus() == BidStatus.PENDING || cur.getStatus() == BidStatus.RELEASED) ? null : cur;
    });
  }

  /**
   * Dummy ack received: wait for the release, or finish immediately when the
   * release already arrived (RELEASED) — in that case the thread is not held.
   */
  public void bidStart(BidInfo bidInfo) throws Exception {
    String key = bidInfo.getName();

    BidInfo[] parked = { null };
    BidInfo[] duplicate = { null };

    try {
      bidInfo.setStatus(BidStatus.WAIT);

      getBidInfoList().compute(key, (k, cur) -> {
        if (cur == null || cur.getStatus() == BidStatus.PENDING) {
          return bidInfo;
        }

        if (cur.getStatus() == BidStatus.RELEASED) {
          parked[0] = cur;
          return null;
        }

        duplicate[0] = cur;
        return cur;
      });
    } catch (Exception e) {
      throw new IBKExceptionMCA(ErrorType.MCA_BID, "Bid Start Error : " + e.getMessage(), e);
    }

    if (duplicate[0] != null) {
      throw new IBKExceptionMCA(ErrorType.MCA_BID,
          "Bid Start Error : duplicate dummy ack, key : " + key + " / " + duplicate[0].getStatus());
    }

    if (parked[0] != null) {
      LogManager.getLogger(LogType.ROOT).info("Bid release arrived before dummy ack, complete without wait : " + key);
      complete(bidInfo, parked[0].getAfterExchange());
      return;
    }

    try {

      ibkTimeoutBid.put(key, bidInfo, bidInfo.getDefaultTimeOut());

      workWait(bidInfo);
    } catch (Exception e) {
      getBidInfoList().remove(key, bidInfo);
      throw new IBKExceptionMCA(ErrorType.MCA_BID, "Bid Start Error : " + e.getMessage(), e);
    } finally {
      // Drops a timer that was put after a release already completed us.
      ibkTimeoutBid.remove(key);
    }
  }

  /**
   * Called by the timer eviction (holding the timer lock). Only times out the
   * exact BidInfo the timer was armed for, and only while it is still WAIT.
   */
  public void bidTimeout(String key, BidInfo expected) {
    BidInfo[] timedOut = { null };

    getBidInfoList().computeIfPresent(key, (k, cur) -> {
      if (cur == expected && cur.getStatus() == BidStatus.WAIT) {
        timedOut[0] = cur;
        return null;
      }
      return cur;
    });

    BidInfo bidInfo = timedOut[0];

    if (bidInfo == null) {
      return;
    }

    try {
      Exchange exchange = bidInfo.getBeforeExchange();

      Throwable throwable = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);

      exchange.setProperty(Exchange.EXCEPTION_CAUGHT,
          new IBKExceptionMCA(ErrorType.MCA_BID_TIMEOUT, "Bid Timeout Exception", throwable));

      Message message = exchange.getIn();

      String bizCode = message.getHeader(ConstantCode.BIZ_CODE, String.class);

      if (bizCode == null || bizCode.length() != 3)
        bizCode = InfraType.MCA.name();

      String errCd = ConstantCode.ERROR_HEAD + InfraType.MCA.name() + (bizCode)
          + ErrorType.MCA_BID_TIMEOUT.getErrorCode();

      message.setHeader(ConstantCode.ERR_SPOT, InfraType.MCA);
      message.setHeader(ConstantCode.ERR_CODE, errCd);
      message.setHeader(ConstantCode.ERR_MSG, "Transaction processing is delayed. Please wait.");

      ErrorUtil.setErrorMessage(exchange);

      // Result Set
      exchange.getOut().copyFrom(exchange.getIn());

      bidInfo.setStatus(BidStatus.TIMEOUT);
      bidInfo.setAfterExchange(exchange);
    } finally {
      // always, otherwise the waiter blocks forever because
      // the entry is already gone from bidInfoList.
      workNotify(bidInfo);
    }
  }

  /**
   * BID release (RCV_CONFIRM_BID) or real response received.
   */
  public ReleaseResult bidResult(String key, Exchange exchange) throws IBKExceptionMCA {
    BidInfo[] waiter = { null };
    ReleaseResult[] result = { ReleaseResult.NOT_FOUND };

    try {
      getBidInfoList().compute(key, (k, cur) -> {
        if (cur == null) {
          return null;
        }

        switch (cur.getStatus()) {
        case WAIT:
          waiter[0] = cur;
          result[0] = ReleaseResult.DELIVERED;
          return null;
        case PENDING:
          // copy(): the release route keeps running after this call
          cur.setAfterExchange(exchange.copy());
          cur.setStatus(BidStatus.RELEASED);
          result[0] = ReleaseResult.PARKED;
          return cur;
        default:
          result[0] = ReleaseResult.DUPLICATE;
          return cur;
        }
      });
    } catch (Exception e) {
      throw new IBKExceptionMCA(ErrorType.MCA_BID, "Bid Result Error : " + e.getMessage(), e);
    }

    if (waiter[0] != null) {

      ibkTimeoutBid.remove(key);

      complete(waiter[0], exchange);
    }

    LogManager.getLogger(LogType.ROOT).info("Bid Result : " + key + " / " + result[0]);

    return result[0];
  }

  private void complete(BidInfo bidInfo, Exchange afterExchange) throws IBKExceptionMCA {
    try {
      bidInfo.setStatus(BidStatus.COMPLETE);
      bidInfo.setAfterExchange(afterExchange);

      bidInfo.getBeforeExchange().getIn().setBody(afterExchange.getIn().getBody());
    } catch (Exception e) {
      throw new IBKExceptionMCA(ErrorType.MCA_BID, "Bid Result Error : " + e.getMessage(), e);
    } finally {
      // ?? ?? ??(Notify)
      workNotify(bidInfo);
    }
  }

  public void workWait(BidInfo info) {
    try {
      info.getFuture().get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      LogManager.getLogger(LogType.ROOT)
          .info("Bid wait completed exceptionally : " + info.getName() + " / " + e.getCause());
    }
  }

  public void workNotify(BidInfo info) {
    info.getFuture().complete(null);
  }
}
