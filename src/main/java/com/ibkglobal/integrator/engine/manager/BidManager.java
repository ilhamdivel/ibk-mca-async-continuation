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
    if (expected.getAsyncCallback() != null) {
      if (expected.getContinuationCompleted().get()
          || !expected.getTimeoutQueued().compareAndSet(false, true)) {
        return;
      }
      try {
        timeoutCompletion.execute(() -> expireTicket(expected.getName(), expected));
      } catch (java.util.concurrent.RejectedExecutionException failure) {
        expected.getTimeoutQueued().set(false);
        LogManager.getLogger(LogType.ROOT).error("BID timeout completion rejected: " + expected.getName(), failure);
      }
      return;
    }
    expireTicket(key, expected);
  }

  private void expireTicket(String key, BidInfo expected) {
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
    bidInfo.setStatus(BidStatus.TIMEOUT);
    if (bidInfo.getAsyncCallback() != null) {
      asyncTimedOut.incrementAndGet();
    }
    LogManager.getLogger(LogType.ROOT).info("Bid Timeout : " + key);

    try {
      Exchange exchange = bidInfo.getBeforeExchange();

      applyBidTimeoutResult(exchange);

      bidInfo.setStatus(BidStatus.TIMEOUT);
      bidInfo.setAfterExchange(exchange);
    } finally {
      // always, otherwise the waiter blocks forever because
      // the entry is already gone from bidInfoList.
      workNotify(bidInfo);
    }
  }

  /**
   * The BID timeout answer ("Transaction processing is delayed. Please wait.",
   * MCA_BID_TIMEOUT). Shared by the real timeout and by every case where an
   * already-accepted (dummy ack) transaction cannot be parked, so the channel
   * never sees a hard failure for something the host is still processing.
   */
  private void applyBidTimeoutResult(Exchange exchange) {
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

      if (waiter[0].getAsyncCallback() == null) {
        ibkTimeoutBid.remove(key);
      } else {
        asyncDelivered.incrementAndGet();
      }

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
    if (info.getAsyncCallback() == null) {
      info.getFuture().complete(null);
      return;
    }
    finishContinuation(info, false);
  }

  private static java.util.concurrent.ScheduledThreadPoolExecutor createDeadlineWatchdog() {
    java.util.concurrent.ScheduledThreadPoolExecutor executor =
        new java.util.concurrent.ScheduledThreadPoolExecutor(1, r -> {
          Thread thread = new Thread(r, "mca-bid-deadline");
          thread.setDaemon(true);
          return thread;
        });
    executor.setRemoveOnCancelPolicy(true);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    return executor;
  }

  private final java.util.concurrent.ScheduledThreadPoolExecutor deadlineWatchdog =
      createDeadlineWatchdog();

  static final int DEFAULT_ASYNC_CAPACITY = 512;

  private volatile int asyncCapacityLimit = DEFAULT_ASYNC_CAPACITY;
  private volatile java.util.concurrent.Semaphore asyncCapacity =
      new java.util.concurrent.Semaphore(DEFAULT_ASYNC_CAPACITY);

  /**
   * Maximum number of suspended dummy-ack continuations per node
   * (integrator.config.bid-async-capacity, default 512). Startup only: the
   * semaphore cannot be swapped while permits are in use.
   */
  @org.springframework.beans.factory.annotation.Value("${integrator.config.bid-async-capacity:512}")
  public void setAsyncCapacity(int capacity) {
    if (capacity < 1) {
      throw new IllegalArgumentException("integrator.config.bid-async-capacity must be >= 1 : " + capacity);
    }
    if (asyncCapacity.availablePermits() != asyncCapacityLimit) {
      throw new IllegalStateException("BID async capacity cannot change while continuations are pending");
    }
    asyncCapacityLimit = capacity;
    asyncCapacity = new java.util.concurrent.Semaphore(capacity);
  }

  public int getAsyncCapacityLimit() {
    return asyncCapacityLimit;
  }

  // Cumulative counters since start (per node) for the dummy-ack continuation.
  private final java.util.concurrent.atomic.AtomicLong asyncSuspended = new java.util.concurrent.atomic.AtomicLong();
  private final java.util.concurrent.atomic.AtomicLong asyncDelivered = new java.util.concurrent.atomic.AtomicLong();
  private final java.util.concurrent.atomic.AtomicLong asyncEarlyRelease = new java.util.concurrent.atomic.AtomicLong();
  private final java.util.concurrent.atomic.AtomicLong asyncTimedOut = new java.util.concurrent.atomic.AtomicLong();
  private final java.util.concurrent.atomic.AtomicLong asyncNotAccepted = new java.util.concurrent.atomic.AtomicLong();
  private final java.util.concurrent.atomic.AtomicLong asyncDuplicate = new java.util.concurrent.atomic.AtomicLong();
  private volatile String lastLoggedCounters = "";

  /** Continuations currently suspended (holding a capacity permit). */
  public int getAsyncPendingCount() {
    return asyncCapacityLimit - asyncCapacity.availablePermits();
  }

  /** Age in ms of the oldest suspended continuation, 0 when none. */
  public long getOldestAsyncPendingMillis() {
    long now = System.nanoTime();
    long oldest = 0L;
    for (BidInfo info : bidInfoList.values()) {
      if (info.getAsyncCallback() != null && info.getStatus() == BidStatus.WAIT) {
        long startedNanos = info.getDeadlineNanos()
            - java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(info.getDefaultTimeOut());
        oldest = Math.max(oldest, java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(now - startedNanos));
      }
    }
    return oldest;
  }

  private String asyncCounters() {
    return "suspended=" + asyncSuspended.get() + ", delivered=" + asyncDelivered.get()
        + ", earlyRelease=" + asyncEarlyRelease.get() + ", timedOut=" + asyncTimedOut.get()
        + ", notAccepted=" + asyncNotAccepted.get() + ", duplicate=" + asyncDuplicate.get();
  }

  /** One-line snapshot for logs / admin endpoints. */
  public String getAsyncStats() {
    return "pending=" + getAsyncPendingCount() + "/" + asyncCapacityLimit
        + ", oldestPendingMs=" + getOldestAsyncPendingMillis() + ", " + asyncCounters();
  }

  /**
   * Every 60 s, log the snapshot when something is pending or a counter moved,
   * so a quiet node does not spam the log.
   */
  @javax.annotation.PostConstruct
  public void startAsyncStatsLog() {
    deadlineWatchdog.scheduleWithFixedDelay(() -> {
      try {
        String counters = asyncCounters();
        if (getAsyncPendingCount() > 0 || !counters.equals(lastLoggedCounters)) {
          lastLoggedCounters = counters;
          LogManager.getLogger(LogType.ROOT).info("Bid async stats : " + getAsyncStats());
        }
      } catch (RuntimeException failure) {
        LogManager.getLogger(LogType.ROOT).warn("Bid async stats failed", failure);
      }
    }, 60L, 60L, java.util.concurrent.TimeUnit.SECONDS);
  }

  private final java.util.concurrent.ExecutorService timeoutCompletion =
      new java.util.concurrent.ThreadPoolExecutor(2, 2, 0L,
          java.util.concurrent.TimeUnit.MILLISECONDS,
          new java.util.concurrent.ArrayBlockingQueue<Runnable>(512), r -> {
        Thread thread = new Thread(r, "mca-bid-completion");
        thread.setDaemon(true);
        return thread;
      }, new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());

  private final Object asyncLifecycle = new Object();
  private volatile boolean asyncStopping;

  public boolean bidStartAsync(BidInfo incoming, org.apache.camel.AsyncCallback callback)
      throws Exception {
    String key = incoming.getName();
    incoming.setAsyncCallback(callback);
    incoming.setAsyncMdc(org.slf4j.MDC.getCopyOfContextMap());
    incoming.setStatus(BidStatus.WAIT);
    incoming.setDeadlineNanos(System.nanoTime()
        + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(incoming.getDefaultTimeOut()));
    BidInfo[] early = { null };
    BidInfo[] duplicate = { null };
    String[] notAccepted = { null };
    synchronized (asyncLifecycle) {
      bidInfoList.compute(key, (k, current) -> {
      if (current != null && incoming.getBeforeExchange().getProperty("MCA_BID_OWNER") != null
          && incoming.getBeforeExchange().getProperty("MCA_BID_OWNER") != current) {
        duplicate[0] = current;
        return current;
      }
      if (current == null || current.getStatus() == BidStatus.PENDING) {
        // A permit is only needed to suspend; a parked release (below) is
        // completed synchronously even when the node is saturated or stopping.
        if (asyncStopping) {
          notAccepted[0] = "BID service is stopping";
          return current;
        }
        if (!asyncCapacity.tryAcquire()) {
          notAccepted[0] = "BID async capacity exhausted (" + asyncCapacityLimit + ")";
          return current;
        }
        incoming.setAsyncPermitOwned(true);
        return incoming;
      }
      if (current.getStatus() == BidStatus.RELEASED) {
        early[0] = current;
        return null;
      }
      duplicate[0] = current;
      return current;
      });
    }
    if (duplicate[0] != null) {
      asyncDuplicate.incrementAndGet();
      throw new IBKExceptionMCA(ErrorType.MCA_BID, "Duplicate BID dummy: " + key);
    }
    if (notAccepted[0] != null) {
      return respondAsBidTimeout(incoming, notAccepted[0]);
    }
    if (early[0] != null) {
      asyncEarlyRelease.incrementAndGet();
      LogManager.getLogger(LogType.ROOT).info("Bid release arrived before dummy ack, complete without wait : " + key);
      incoming.setStatus(BidStatus.COMPLETE);
      incoming.setAfterExchange(early[0].getAfterExchange());
      incoming.getBeforeExchange().getIn().setBody(early[0].getAfterExchange().getIn().getBody());
      bidInfoList.remove(key, early[0]);
      finishContinuation(incoming, true);
      return true;
    }
    asyncSuspended.incrementAndGet();
    try {
      incoming.setFallbackDeadline(deadlineWatchdog.scheduleWithFixedDelay(() -> {
        if (bidInfoList.get(key) == incoming
            && System.nanoTime() - incoming.getDeadlineNanos() >= 0) {
          bidTimeout(key, incoming);
        }
      }, incoming.getDefaultTimeOut(), 100L, java.util.concurrent.TimeUnit.MILLISECONDS));
      if (incoming.getContinuationCompleted().get()) {
        incoming.getFallbackDeadline().cancel(false);
      }
    } catch (RuntimeException failure) {
      if (bidInfoList.remove(key, incoming)) {
        incoming.setAsyncPermitOwned(false);
        asyncCapacity.release();
        LogManager.getLogger(LogType.ROOT).error("BID deadline registration failed : " + key, failure);
        return respondAsBidTimeout(incoming, "BID deadline registration failed");
      }
      return false;
    }
    incoming.setAsyncTimerKey(key + ":async:" + java.util.UUID.randomUUID().toString());
    try {
      ibkTimeoutBid.put(incoming.getAsyncTimerKey(), incoming, incoming.getDefaultTimeOut());
      if (incoming.getContinuationCompleted().get()) {
        ibkTimeoutBid.remove(incoming.getAsyncTimerKey());
      }
    } catch (Exception failure) {
      LogManager.getLogger(LogType.ROOT).warn("Primary BID timer unavailable; fallback deadline armed: " + key);
    }
    // Inbound channel close deliberately does NOT end the ticket (office
    // baseline semantics): it keeps waiting for the release or the deadline, so
    // a release arriving after the client disconnected is still DELIVERED
    // instead of being reported as a BID timeout + "bidInfo is null".
    return false;
  }

  /**
   * The host already accepted this transaction (dummy ack) but the node cannot
   * park it. Answer exactly like a BID timeout (MCA_BID_TIMEOUT, "please wait")
   * instead of a hard MCA_BID error, so the channel does not treat it as failed
   * and invite a retry (double transfer risk). Completes synchronously.
   */
  private boolean respondAsBidTimeout(BidInfo incoming, String reason) {
    asyncNotAccepted.incrementAndGet();
    LogManager.getLogger(LogType.ROOT)
        .warn("BID continuation not accepted (" + reason + "), answered as BID timeout : " + incoming.getName());
    Exchange exchange = incoming.getBeforeExchange();
    applyBidTimeoutResult(exchange);
    incoming.setStatus(BidStatus.TIMEOUT);
    incoming.setAfterExchange(exchange);
    if (incoming.getContinuationCompleted().compareAndSet(false, true)) {
      incoming.getAsyncCallback().done(true);
    }
    return true;
  }

  private void finishContinuation(BidInfo info, boolean synchronous) {
    if (!info.getContinuationCompleted().compareAndSet(false, true)) {
      return;
    }
    Map<String, String> previous = org.slf4j.MDC.getCopyOfContextMap();
    if (info.getAsyncTimerKey() != null) {
      try {
        ibkTimeoutBid.remove(info.getAsyncTimerKey());
      } catch (RuntimeException failure) {
        LogManager.getLogger(LogType.ROOT).warn("BID timer cleanup failed: " + info.getName(), failure);
      }
    }
    if (info.getFallbackDeadline() != null) {
      info.getFallbackDeadline().cancel(false);
    }
    try {
      if (info.getAsyncMdc() == null) {
        org.slf4j.MDC.clear();
      } else {
        org.slf4j.MDC.setContextMap(info.getAsyncMdc());
      }
      info.getAsyncCallback().done(synchronous);
    } finally {
      if (info.isAsyncPermitOwned()) {
        info.setAsyncPermitOwned(false);
        asyncCapacity.release();
      }
      if (previous == null) {
        org.slf4j.MDC.clear();
      } else {
        org.slf4j.MDC.setContextMap(previous);
      }
    }
  }

  @javax.annotation.PreDestroy
  public void shutdownAsync() {
    synchronized (asyncLifecycle) {
      asyncStopping = true;
    }
    deadlineWatchdog.shutdown();
    for (Map.Entry<String, BidInfo> entry : bidInfoList.entrySet()) {
      if (entry.getValue().getAsyncCallback() != null) {
        expireTicket(entry.getKey(), entry.getValue());
      }
    }
    timeoutCompletion.shutdown();
  }
}
