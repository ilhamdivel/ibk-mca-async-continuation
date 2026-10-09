package com.ibkglobal.integrator.engine.builder.route.mca;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.Builder;

import com.ibk.ibkglobal.data.integrator.route.RouteCreateInfo;
import com.ibkglobal.integrator.config.ConstantCode;
import com.ibkglobal.integrator.engine.bean.common.TranLogApi;
import com.ibkglobal.integrator.engine.bean.mca.log.LoggingProcessorMCA;
import com.ibkglobal.integrator.engine.builder.route.RouteCreateDefault;

public class MCADefaultAdapterOut extends RouteCreateDefault {

  public MCADefaultAdapterOut(RouteCreateInfo builderInfo) {
    super.setBuilderInfo(builderInfo);
    onMCAException();
    create();
  }

  @Override
  public void create() {

    // from
    createEndpoint("from");

    // routeId
    this.routeId(getBuilderInfo().getRouteId());

    setDefaultHeader("3", "N");

    // Tran Log(S2)
    // this.bean(com.ibkglobal.integrator.engine.bean.common.TranLogApi.class,
    // "TranLog(${body}, Start, MCA_BIZ)");

    this.process(new Processor() {
      @Override
      public void process(Exchange exchange) throws Exception {
        new TranLogApi().TranLog(exchange, "Start", "MCA_BIZ");
      }
    });

    // 컴포징
    this.setHeader(ConstantCode.PARSING_TYPE, Builder.constant(getBuilderInfo().getParsingType()));
    this.bean(com.ibkglobal.integrator.engine.bean.mca.common.ComposingMCA.class, "composing");

    // AdapterIn Logging
    this.process(new LoggingProcessorMCA());

    removeHeaders("*", getExcludePatterns());

    // to
    createEndpoint("to");

    setDefaultHeader("4", "N");

    // AdapterOut Logging
    this.process(new LoggingProcessorMCA());

    this.setHeader(ConstantCode.PARSING_TYPE, Builder.constant(getBuilderInfo().getParsingType()));

    this.bean(com.ibkglobal.integrator.engine.bean.mca.common.ParsingMCA.class, "parsing");

    // Tran Log(E2)
    // this.bean(com.ibkglobal.integrator.engine.bean.common.TranLogApi.class,
    // "TranLog(${body}, End, MCA_BIZ)");

    this.process(new Processor() {
      @Override
      public void process(Exchange exchange) throws Exception {
        new TranLogApi().TranLog(exchange, "End", "MCA_BIZ");
      }
    });

  }
}
