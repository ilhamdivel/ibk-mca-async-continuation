package com.ibkglobal.integrator.api.rest.manager.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ibkglobal.integrator.engine.monitor.CamelMonitor;
import com.ibkglobal.integrator.manager.model.RouteInfo;
import com.ibkglobal.integrator.manager.service.RouteService;
import com.ibkglobal.message.converter.service.ConverterService;

@Service
public class ManagerAdapterService {
	
	@Autowired
	RouteService routeService;
	
	@Autowired
	CamelMonitor camelMonitor;
	
	@Autowired
	ConverterService converterService;
	
	public void addAdapter(String data) throws Exception {		
		RouteInfo routeInfo = converterService.jsonToObject(data, RouteInfo.class);
		
		routeService.addBuilder(routeInfo);
	}
	
	public void applyAdapter(String itncCd, String routId) throws Exception {		
		routeService.applyBuilder(itncCd, routId);
	}
	
	public void updateAdapter(String data) throws Exception {		
		RouteInfo routeInfo = converterService.jsonToObject(data, RouteInfo.class);
		
		routeService.updateBuilder(routeInfo);
	}
	
	public void removeAdapter(String routeId) throws Exception {		
		routeService.remove(routeId);
	}
	
	public void startAdapter(String routeId) throws Exception {		
		routeService.start(routeId);
	}
	
	public void stopAdapter(String routeId) throws Exception {		
		routeService.stop(routeId);
	}
	
	public void setLogLevelAdapter(Map<String, String> data) throws Exception {		
		String routeId  = data.get("routeId");
		String logLevel = data.get("logLevel");
		
		routeService.logLevelSet(routeId, logLevel);
	}
}
