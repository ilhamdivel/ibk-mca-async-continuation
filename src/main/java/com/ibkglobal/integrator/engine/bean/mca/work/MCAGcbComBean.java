package com.ibkglobal.integrator.engine.bean.mca.work;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import com.ibkglobal.integrator.config.CamelConfig;
import com.ibkglobal.integrator.config.EndpointCode;
import com.ibkglobal.integrator.engine.manager.BidManager;
import com.ibkglobal.integrator.exception.ErrorType;
import com.ibkglobal.integrator.exception.IBKExceptionMCA;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.log.LogType;
import com.ibkglobal.message.IBKMessage;
import com.ibkglobal.message.common.normal.StandardTelegram;
import com.ibkglobal.message.converter.service.ConverterService;

public class MCAGcbComBean {

  @Autowired
  BidManager bidManager;

  @Autowired
  CamelConfig camelConfig;

  @Autowired
  ConverterService converterService;

  public void execute(Exchange exchange) throws IBKExceptionMCA {

    ProducerTemplate producer = null;

    try {
      Message message = exchange.getIn();
      Exchange sendExchange = exchange.copy();
      producer = camelConfig.getCamelContext().createProducerTemplate();

      IBKMessage ibkMessage = message.getBody(IBKMessage.class);
      String intfId = ibkMessage.getStandardTelegram().getSttlSysCopt().getSttlIntfId();

      // Send to BID Queue (bid Q로 전송)
      if (intfId.equals("GCBO00006560")) {
        LogManager.getLogger(LogType.ROOT).info("send Bid Q: " + intfId);
        // Convert to JSON (메시지 공통부에 리스트 타입이 있어 큐로 전송되지 않는 문제 있어 전문을 JSON으로 변환 후 전송)
        StandardTelegram standardTelegram = ibkMessage.getStandardTelegram();
        String strBody = converterService.objectToJson(standardTelegram);
        sendExchange.getMessage().setBody(strBody);
        // Producer asyncSend
        producer.send(EndpointCode.SEDA + "M.BID0.GCB0.KR00.SEDA", sendExchange);
      } else {
        LogManager.getLogger(LogType.ROOT).info("send GCB.COM: " + intfId);
        // Producer send
        producer.send(EndpointCode.DIRECT + "M.GCB0.COM0.ROUTE", exchange);
      }
    }
    // Producer Execution Exception
    catch (CamelExecutionException camelEx) {
      LogManager.getLogger(LogType.ROOT).info("MCAGcbComBean Camel Execution Exception Occurred");
      StackTraceElement[] stacks = camelEx.getStackTrace();
      for (StackTraceElement stackTraceElement : stacks) {
        LogManager.getLogger(LogType.ROOT).info(stackTraceElement.toString());
      }
      throw new IBKExceptionMCA(ErrorType.MCA,
          "MCAGcbComBean Producer Template Send Defined Exception: " + camelEx.getMessage(), camelEx);
    }
    // Other Exception Occurred
    catch (Exception ex) {
      LogManager.getLogger(LogType.ROOT).info("MCAGcbComBean Other Exception Occurred");
      StackTraceElement[] stacks = ex.getStackTrace();
      for (StackTraceElement stackTraceElement : stacks) {
        LogManager.getLogger(LogType.ROOT).info(stackTraceElement.toString());
      }
      throw new IBKExceptionMCA(ErrorType.MCA, "MCAGcbComBean Undefined Exception: " + ex.getMessage(), ex);
    }
    // Finally
    finally {
      // Producer CleanUp & Stop
      try {
        producer.cleanUp();
        producer.stop();
        LogManager.getLogger(LogType.ROOT).info("MCAGcbComBean Producer CleanUp and Stop Completed");
      } catch (Exception ex) {
        LogManager.getLogger(LogType.ROOT)
            .info("MCAGcbComBean Producer CleanUp and Stop Failed. Exception: " + ex.getMessage());
      }
    }
  }
}
