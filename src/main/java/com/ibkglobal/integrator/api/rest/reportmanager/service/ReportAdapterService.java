package com.ibkglobal.integrator.api.rest.reportmanager.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ibk.ibkglobal.data.integrator.route.RouteCreateInfo;
import com.ibk.ibkglobal.data.integrator.route.type.InstanceRouteType;
import com.ibkglobal.integrator.engine.model.RouteStatus;
import com.ibkglobal.integrator.engine.monitor.CamelMonitor;
import com.ibkglobal.integrator.manager.instance.InstanceAdmin;
import com.ibkglobal.integrator.manager.model.RouteInfo;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.message.converter.service.ConverterService;

import ch.qos.logback.classic.Logger;

/**
 * Report Adapter Service
 *
 */
@Service
public class ReportAdapterService {

	@Autowired
	InstanceAdmin instanceAdmin;
	
	@Autowired
	ConverterService converterService;
	
	@Autowired
	CamelMonitor camelMonitor;
			
	/**
	 * Get Report Adapter List
	 * @return List<Map<String, Object>>
	 */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> getReportAdapterList(String rtId) throws Exception {
		
		List<Map<String, Object>> result = new ArrayList<>();
		
		List<RouteInfo> adapterList = instanceAdmin.getAllRouteInfo(InstanceRouteType.ADAPTER);
		
		adapterList.sort((b, c) -> {
			return b.getRouteId().compareTo(c.getRouteId());
		});
		
		adapterList.stream()
			.filter(t -> t.getRouteId().contains(rtId))
			.map(m -> m.getRouteCreate().getRouteCreateInfo())
			.forEach(E -> {
				Map<String, Object> tempMap = new HashMap<>();
				
				try {
					tempMap.putAll(converterService.objectToObject(E, HashMap.class));
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				RouteStatus routeStatsModel = camelMonitor.getRouteStatus(E.getRouteId());
				
				if (routeStatsModel != null) {
					try {
						tempMap.put("dumpRouteStats", converterService.objectToObject(routeStatsModel, HashMap.class));
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
				
				Logger logger = LogManager.getLogger(E.getLogName());
				tempMap.put("logLevel", logger.getLevel().levelStr);
				
				result.add(tempMap);
			}
		);
		
		return result;
	}
	
	/**
	 * Get Report Adapter Detail
	 * @param id
	 * @return Map<String, Object>
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> getReportAdapterDetail(String id) throws Exception {
	
		RouteCreateInfo info = null;
		try {
			info = instanceAdmin.getAllRouteInfo(InstanceRouteType.ADAPTER).stream()
									.filter(f -> f.getRouteId().equals(id))
									.map(m -> m.getRouteCreate().getRouteCreateInfo())
									.findFirst()
									.get();
		} catch (Exception e) {}
		if (info == null) return null;
		
		Map<String, Object> result = new HashMap<>();
		result.putAll(converterService.objectToObject(info, HashMap.class));
		RouteStatus routeStatsModel = camelMonitor.getRouteStatus(info.getRouteId());
		if (routeStatsModel != null) {
			result.put("dumpRouteStats", converterService.objectToObject(routeStatsModel, HashMap.class));
		}
		
		Logger logger = LogManager.getLogger(info.getLogName());
		result.put("logLevel", logger.getLevel().levelStr);
		
		return result;
	}
}
