package com.ibkglobal.integrator.engine.builder.route.mca;

import com.ibk.ibkglobal.data.integrator.route.RouteCreateInfo;
import com.ibkglobal.integrator.engine.builder.route.RouteCreateDefault;

public class MCAInbound extends RouteCreateDefault {
	
	private final org.apache.camel.AsyncProcessor afterProcessor;

	public MCAInbound(RouteCreateInfo builderInfo, org.apache.camel.AsyncProcessor afterProcessor) {
        if (afterProcessor == null) {
            throw new IllegalArgumentException("BID async processor is required");
        }
        this.afterProcessor = afterProcessor;
		super.setBuilderInfo(builderInfo);
		onMCAException();
		create();
	}

	@Override
	public void create() {
		createEndpoint("from");
		
		routeId(getBuilderInfo().getRouteId())		
		
		// 라우터 전 처리 : 헤더 설정 & Valid Check & Body 스키마 생성
		.bean(com.ibkglobal.integrator.engine.bean.mca.common.ProcessPreMCA.class, "preProcess")
		
		// 매핑
		.bean(com.ibkglobal.integrator.engine.bean.mca.common.MappingMCA.class, "mappingExecute")
		
		// 전 처리 업무
		.bean(com.ibkglobal.integrator.engine.bean.mca.work.MCAWorkPreProcess.class, "execute");
		
		createEndpoint("to");
		
		// 파싱 데이터 검증
		bean(com.ibkglobal.integrator.engine.bean.mca.common.ProcessHeaderMCA.class, "headerCheck")
		
		// 후 처리 업무
		.process(afterProcessor)
		
		// 라우터 후 처리 : 헤더 설정 & Body 스키마 생성
		.bean(com.ibkglobal.integrator.engine.bean.mca.common.ProcessAfterMCA.class, "afterProcess")
		
		// 매핑
		.bean(com.ibkglobal.integrator.engine.bean.mca.common.MappingMCA.class, "mappingExecute");
	}
}
