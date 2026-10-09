package com.ibkglobal.integrator.engine.builder.route.mca.bid;

import org.apache.camel.builder.Builder;
import org.springframework.util.StringUtils;

import com.ibk.ibkglobal.data.integrator.route.RouteCreateInfo;
import com.ibk.ibkglobal.data.integrator.route.type.ParsingType;
import com.ibkglobal.integrator.config.ConstantCode;
import com.ibkglobal.integrator.engine.bean.mca.log.LoggingProcessorMCA;
import com.ibkglobal.integrator.engine.builder.route.RouteCreateDefault;

public class MCABidProcess extends RouteCreateDefault {

	public MCABidProcess(RouteCreateInfo builderInfo) {
		super.setBuilderInfo(builderInfo);
		onMCAException();
		create();
	}
	
	@Override
	public void create() {
		createEndpoint("from");
		
		// routeId
		if (!StringUtils.isEmpty(getBuilderInfo().getRouteId())) {
			this.routeId(getBuilderInfo().getRouteId());
		}
		
		// 파싱
		this.setHeader(ConstantCode.PARSING_TYPE, Builder.constant(ParsingType.JSON));	
		this.bean(com.ibkglobal.integrator.engine.bean.mca.common.ParsingMCA.class, "parsing");
		
		setDefaultHeader("2", "B");
		
		this.process(new LoggingProcessorMCA());
		
		this.bean(com.ibkglobal.integrator.engine.bean.mca.work.MCABidBean.class, "bidWorkCheck");
		
		// 단말 BID(단말로 비동기 전송)일 경우, 후 처리 및 매핑
		// 수신확인 BID(Dummy Notify)일 경우 Notify 시키고 그 업무에서 후 처리 및 매핑
		this.choice()
			.when(p -> p.getMessage().getHeader(ConstantCode.BID_WORK_TYPE, String.class) == "5")
			.bean(com.ibkglobal.integrator.engine.bean.mca.common.ProcessAfterMCA.class, "afterProcessBid")
			.bean(com.ibkglobal.integrator.engine.bean.mca.common.MappingMCA.class, "mappingExecute");
		this.end();
		
	    // 비드 처리
		this.bean(com.ibkglobal.integrator.engine.bean.mca.work.MCABidHandle.class, "execute");
	}
}
