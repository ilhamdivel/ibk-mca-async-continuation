package com.ibkglobal.integrator.engine.builder.route.mca;

import com.ibk.ibkglobal.data.integrator.route.RouteCreateInfo;
import com.ibkglobal.integrator.engine.builder.route.RouteCreateDefault;

public class MCAHealthCheck extends RouteCreateDefault {

	public MCAHealthCheck(RouteCreateInfo builderInfo) {
		super.setBuilderInfo(builderInfo);
		onMCAException();
		create();
	}
	
	@Override
	public void create() {
		onException(Exception.class)
		.handled(true)
		.bean(com.ibkglobal.integrator.engine.bean.mca.error.ErrorCatchHealthCheck.class, "catchError");
		
		createEndpoint("from");
		
		routeId(getBuilderInfo().getRouteId())
		.bean(com.ibkglobal.integrator.engine.bean.mca.work.HealthCheck.class, "execute");
	}
}
