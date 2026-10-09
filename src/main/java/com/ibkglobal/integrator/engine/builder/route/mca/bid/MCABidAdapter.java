package com.ibkglobal.integrator.engine.builder.route.mca.bid;

import org.apache.camel.builder.Builder;
import org.springframework.util.StringUtils;

import com.ibk.ibkglobal.data.integrator.route.RouteCreateInfo;
import com.ibkglobal.integrator.config.ConstantCode;
import com.ibkglobal.integrator.engine.bean.common.process.CloneIBKMessageProcessor;
import com.ibkglobal.integrator.engine.bean.mca.log.LoggingProcessorMCA;
import com.ibkglobal.integrator.engine.builder.route.RouteCreateDefault;

public class MCABidAdapter extends RouteCreateDefault {
	
	public MCABidAdapter(RouteCreateInfo builderInfo) {
		super.setBuilderInfo(builderInfo);
		onMCAException();
		create();
	}

	@Override
	public void create() {
		
		// from
		createEndpoint("from");
		
		// routeId
		if (!StringUtils.isEmpty(getBuilderInfo().getRouteId())) {
			this.routeId(getBuilderInfo().getRouteId());
		}
				
		setDefaultHeader("1", "B");
		
		// AdapterIn Logging
		this.process(new LoggingProcessorMCA());
		
		// 파싱
		this.setHeader(ConstantCode.PARSING_TYPE, Builder.constant(getBuilderInfo().getParsingType()));	
		this.bean(com.ibkglobal.integrator.engine.bean.mca.common.ParsingMCA.class, "parsing");
		
		// To WireTap
		this.wireTap(createStringEndpoint("to"))
		    .onPrepare(new CloneIBKMessageProcessor())
		.end();
		
		// Ack Message Return
		this.bean(com.ibkglobal.integrator.engine.bean.mca.work.MCABidBean.class, "ackMessage");
	}
}
