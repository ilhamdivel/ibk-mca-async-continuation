package com.ibkglobal.integrator.engine.builder.route.mca;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.Builder;

import com.ibk.ibkglobal.data.integrator.route.RouteCreateInfo;
import com.ibkglobal.integrator.config.ConstantCode;
import com.ibkglobal.integrator.engine.bean.common.TranLogApi;
import com.ibkglobal.integrator.engine.bean.mca.log.LoggingProcessorMCA;
import com.ibkglobal.integrator.engine.builder.route.RouteCreateDefault;
import com.ibkglobal.message.IBKMessage;

public class MCADefaultAdapterIn extends RouteCreateDefault {

	public MCADefaultAdapterIn(RouteCreateInfo builderInfo) {
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
		
		setDefaultHeader("1", "N");
		
		// AdapterIn Logging
		this.process(new LoggingProcessorMCA());
		
		// 파싱
		this.setHeader(ConstantCode.PARSING_TYPE, Builder.constant(getBuilderInfo().getParsingType()));	
		this.bean(com.ibkglobal.integrator.engine.bean.mca.common.ParsingMCA.class, "parsing");
		
		this.bean(com.ibkglobal.integrator.engine.bean.common.TranControl.class, "execute");
		
		// Tran Log(S1)
//		this.bean(com.ibkglobal.integrator.engine.bean.common.TranLogApi.class, "TranLog(${body}, Start, MCA_CHN)");
		
		this.process(new Processor() {
			@Override
			public void process(Exchange exchange) throws Exception {
				new TranLogApi().TranLog(exchange, "Start", "MCA_CHN");
			}
		});

		// to
		createEndpoint("to");

		setDefaultHeader("6", "N");
		
		// Tran Log(S1)
//		this.bean(com.ibkglobal.integrator.engine.bean.common.TranLogApi.class, "TranLog(${body}, End, MCA_CHN)");
		
		this.process(new Processor() {
			@Override
			public void process(Exchange exchange) throws Exception {
				new TranLogApi().TranLog(exchange, "End", "MCA_CHN");
			}
		});
		
		// 컴포징
		this.setHeader(ConstantCode.PARSING_TYPE, Builder.constant(getBuilderInfo().getParsingType()));
		
		this.bean(com.ibkglobal.integrator.engine.bean.mca.common.ComposingMCA.class, "composing");
		
		// AdapterOut Logging
		this.process(new LoggingProcessorMCA());
		
		removeHeaders("*", getExcludePatterns());
	}
}
