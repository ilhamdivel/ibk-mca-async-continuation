package com.ibkglobal.integrator.engine.builder.route.mca.bid;

import org.apache.camel.ExchangePattern;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Message;

import com.ibkglobal.config.CommonConstantCode;
import com.ibkglobal.integrator.config.ConstantCode;
import com.ibkglobal.integrator.config.EndpointCode;
import com.ibkglobal.integrator.engine.bean.mca.log.LoggingProcessorMCA;
import com.ibkglobal.integrator.engine.builder.route.RouteCreateCustomDefault;

public class MCABidRoute extends RouteCreateCustomDefault {

  @Override
  public void configure() throws Exception {
    // TODO Auto-generated method stub
    onException(Exception.class).handled(true)
        .bean(com.ibkglobal.integrator.engine.bean.mca.error.ErrorCatchMCA.class, "catchError")
        .log(LoggingLevel.ERROR, "exceptionLog", "${exception.stacktrace}").end();

    this.from(EndpointCode.SEDA + "M.BID0.GCB0.KR00.SEDA?exchangePattern=InOnly")
        .setExchangePattern(ExchangePattern.InOnly).log(LoggingLevel.INFO, "MCABidRoute.java START")

        .process(p -> {
          setDefaultHeader(p.getMessage(), "1", "B");
        }).process(new LoggingProcessorMCA())

        .to(EndpointCode.QUEUE + "M.BID0.GCB0.KR00.Q?exchangePattern=InOnly").setExchangePattern(ExchangePattern.InOnly)

        .process(p -> {
          setDefaultHeader(p.getMessage(), "3", "B");
        }).process(new LoggingProcessorMCA()).log(LoggingLevel.INFO, "MCABidRoute.java END");

  }

  /**
   * 헤더 기본 Set
   * 
   * @param seq
   */
  private void setDefaultHeader(Message message, String seq, String tradeType) {

    message.setHeader(ConstantCode.SEQ, seq);
    message.setHeader(ConstantCode.TRADE_TYPE, tradeType);
    message.setHeader(CommonConstantCode.LOGGER_KEY, "GCB.BID");
    message.setHeader(ConstantCode.ORG_CODE, "-");
    message.setHeader(ConstantCode.BIZ_CODE, "COM");
    message.setHeader(ConstantCode.SYS_CODE, "GCB");
  }
}