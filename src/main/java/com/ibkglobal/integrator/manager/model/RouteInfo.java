package com.ibkglobal.integrator.manager.model;

import com.ibk.ibkglobal.data.integrator.route.type.InstanceRouteType;
import com.ibk.ibkglobal.data.integrator.route.type.InstanceType;
import com.ibk.ibkglobal.data.integrator.route.type.RouteType;
import com.ibkglobal.integrator.engine.builder.route.RouteCreate;

import lombok.Data;

@Data
public class RouteInfo {
	
	private InstanceType      instanceType;      // 초기 업무 타입(MCA, FEP, EAI)
	private InstanceRouteType instanceRouteType; // 라우터 타입(ADAPTER, ROUTER, ...)
	private String            routeId;           // 라우트 아이디
	private RouteType         routeType;         // 라우트 타입
	private RouteCreate       routeCreate;       // 생성 된 라우트 객체
	
	public RouteInfo(RouteCreate routeCreate) {
		this.instanceType      = routeCreate.getRouteCreateInfo().getInstanceType();
		this.instanceRouteType = routeCreate.getRouteCreateInfo().getInstanceRouteType();
		this.routeId           = routeCreate.getRouteCreateInfo().getRouteId();
		this.routeType         = routeCreate.getRouteCreateInfo().getRouteType();
		this.routeCreate       = routeCreate;
	}
}
