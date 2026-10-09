package com.ibkglobal.integrator.engine.bean.mca.work;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import com.ibkglobal.config.RouteProperties;
import com.ibkglobal.integrator.config.CamelConfig;
import com.ibkglobal.integrator.config.EndpointCode;
import com.ibkglobal.integrator.config.TelegramConstants.StandardTelegramType;
import com.ibkglobal.integrator.engine.manager.BidManager;
import com.ibkglobal.integrator.exception.ErrorType;
import com.ibkglobal.integrator.exception.IBKExceptionMCA;
import com.ibkglobal.integrator.util.BidUtil;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.log.LogType;
import com.ibkglobal.message.IBKMessage;
import com.ibkglobal.message.common.normal.StandardTelegram;
import com.ibkglobal.message.converter.ConverterByte;
import com.ibkglobal.message.converter.service.ConverterService;

public class MCABidHandle {

  @Autowired
  BidManager bidManager;

  @Autowired
  CamelConfig camelConfig;

  @Autowired
  ConverterService converterService;

  @Autowired
  RouteProperties routeProperties;

  public void execute(Exchange exchange) throws IBKExceptionMCA {

    try {
      Message message = exchange.getIn();

      IBKMessage ibkMessage = message.getBody(IBKMessage.class);

      StandardTelegram standardTelegram = ibkMessage.getStandardTelegram();

      String bidWorkCheck = standardTelegram.getSttlSysCopt().getOtptTmgtDcd();

      // 비드 구분
      switch (StandardTelegramType.fromString(bidWorkCheck)) {
      case BID:
        bidWork(exchange);
        break;
      case RCV_CONFIRM_BID:
        bidWorkWait(exchange, standardTelegram);
        break;
      default:
        break;
      }
    } catch (Exception e) {
      throw new IBKExceptionMCA(ErrorType.MCA_BID, "비드 업무 처리중 에러");
    }
  }

  /**
   * 일단 비드 처리
   * 
   * @param exchange
   * @throws IBKExceptionMCA
   */
  public void bidWork(Exchange exchange) throws Exception {

    ProducerTemplate producer = null;
    CompletableFuture<Object> completableFuture = null;

    Message message = exchange.getIn();
    IBKMessage ibkMessage = message.getBody(IBKMessage.class);

    String sendMessage = converterService.objectToJson(ibkMessage.getStandardTelegram());
    sendMessage = ConverterByte.fieldStringFormat("INTEGER", sendMessage.getBytes().length, 8, 0) + sendMessage;

    // BID Port DEV : 52600 / QA : 52700 / PROD : 52800
    String bidRspnIp = ibkMessage.getStandardTelegram().getSttlSysCopt().getBidRspnRcvNdIp();
    String uri = EndpointCode.TCP + bidRspnIp + ":" + routeProperties.getBidRspnPort()
        + "?textline=true&disconnect=true&sync=false&requestTimeout=60000";
    LogManager.getLogger(LogType.ROOT).info("send BidWork Url: " + uri);

    try {
      // Create New ProducerTemplate instead of using Singleton
      producer = camelConfig.getCamelContext().createProducerTemplate();
      // If want to separate successful & failure callback, we can use
      // asyncCallBackSendBody
      completableFuture = producer.asyncSendBody(uri, sendMessage);
      // Timeout Future Added, can be done by YML Configuration
      completableFuture.get(60000, TimeUnit.MILLISECONDS);
    }
    // Producer Execution Exception
    catch (CamelExecutionException camelEx) {
      throw new IBKExceptionMCA(ErrorType.BID,
          "MCABidHandle(BID) Producer Template Send Defined Exception: " + camelEx.getMessage(), camelEx);
    }
    // Producer Undefined Exception
    catch (Exception ex) {
      throw new IBKExceptionMCA(ErrorType.BID,
          "MCABidHandle(BID) Producer Template Send Undefined Exception: " + ex.getMessage(), ex);
    }
    // Finally
    finally {
      // Close Future
      try {
        if (!completableFuture.isCancelled()) {
          completableFuture.cancel(true);
          LogManager.getLogger(LogType.ROOT).info("MCABidHandle(BID) Async Future Cancel Completed");
        }
      } catch (Exception ex) {
        LogManager.getLogger(LogType.ROOT)
            .info("MCABidHandle(BID) Async Future Cancel Failed. Exception : " + ex.getMessage());
      }

      // Producer CleanUp & Stop
      try {
        producer.cleanUp();
        producer.stop();
        LogManager.getLogger(LogType.ROOT).info("MCABidHandle(BID) Producer CleanUp & Stop Completed");
      } catch (Exception ex) {
        LogManager.getLogger(LogType.ROOT)
            .info("MCABidHandle(BID) Producer CleanUp & Stop Failed. Exception: " + ex.getMessage());
      }
    }

    // camelConfig.getProducerTemplate().asyncCallbackSendBody(uri, sendMessage, new
    // Synchronization() {
    //
    // @Override
    // public void onFailure(Exchange exchange) {
    // }
    //
    // @Override
    // public void onComplete(Exchange exchange) {
    // }
    // });
  }

  /**
   * Wait 비드 처리
   * 
   * @param exchange
   * @param standardTelegram
   * @throws IBKExceptionMCA
   */
  public void bidWorkWait(Exchange exchange, StandardTelegram standardTelegram) throws IBKExceptionMCA {

    String key = BidUtil.getBidKey(standardTelegram);

    BidManager.ReleaseResult result = bidManager.bidResult(key, exchange);

    if (result == BidManager.ReleaseResult.NOT_FOUND) {
      LogManager.getLogger(LogType.ROOT).info("MCABidHandle(RCV_CONFIRM_BID) no Bid registered, ignored : " + key);
    }
  }
}
