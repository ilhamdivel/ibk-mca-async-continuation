package com.ibkglobal.integrator.engine.builder.mapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ibk.ibkglobal.data.integrator.route.RouteCreateInfo;
import com.ibkglobal.integrator.engine.builder.RouteCreateFactory;
import com.ibkglobal.integrator.engine.builder.route.RouteCreate;
import com.ibkglobal.integrator.manager.model.RouteInfo;

@Component
public class RouteCreateMapper {
	
	@Autowired
	RouteCreateFactory routeCreateFactory;
	
	public RouteInfo createRouteInfo(RouteCreateInfo routeCreateInfo) {
		RouteCreate routeCreate = routeCreateFactory.getCreate(routeCreateInfo);	
		
		if (routeCreate == null) {
			return null;
		}
		
		RouteInfo routeInfo = new RouteInfo(routeCreate);
		
		return routeInfo;
	}
}
