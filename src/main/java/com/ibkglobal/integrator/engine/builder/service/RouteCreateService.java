package com.ibkglobal.integrator.engine.builder.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ibk.ibkglobal.data.integrator.route.RouteCreateInfo;
import com.ibkglobal.integrator.engine.builder.mapper.RouteCreateMapper;
import com.ibkglobal.integrator.manager.model.RouteInfo;

@Service
public class RouteCreateService {
	
	@Autowired
	RouteCreateMapper routeCreateMapper;
	
	public RouteInfo createRouteInfo(RouteCreateInfo routeCreateInfo) {
		return routeCreateMapper.createRouteInfo(routeCreateInfo);
	}
}
