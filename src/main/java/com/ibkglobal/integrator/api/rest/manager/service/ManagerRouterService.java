package com.ibkglobal.integrator.api.rest.manager.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ibkglobal.integrator.engine.monitor.CamelMonitor;
import com.ibkglobal.integrator.manager.model.RouteInfo;
import com.ibkglobal.integrator.manager.service.RouteService;
import com.ibkglobal.message.converter.service.ConverterService;

@Service
public class ManagerRouterService {
	
	@Autowired
	RouteService routeService;
	
	@Autowired
	CamelMonitor camelMonitor;
	
	@Autowired
	ConverterService converterService;
	
	public void addRouter(String data) throws Exception {		
		RouteInfo routeInfo = converterService.jsonToObject(data, RouteInfo.class);
		
		routeService.addBuilder(routeInfo);
	}
	
	public void applyRouter(String itncCd, String routId) throws Exception {
		routeService.applyBuilder(itncCd, routId);
	}
	
	public void updateRouter(String data) throws Exception {		
		RouteInfo routeInfo = converterService.jsonToObject(data, RouteInfo.class);
		
		routeService.updateBuilder(routeInfo);
	}
	
	public void removeRouter(String routeId) throws Exception {		
		routeService.remove(routeId);
	}
	
	public void startRouter(String routeId) throws Exception {		
		routeService.start(routeId);
	}
	
	public void stopRouter(String routeId) throws Exception {		
		routeService.stop(routeId);
	}
}
