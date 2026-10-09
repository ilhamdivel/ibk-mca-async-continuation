package com.ibkglobal.integrator.api.rest.reportmanager.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.model.RouteDefinition;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ibk.ibkglobal.data.integrator.route.RouteCreateInfo;
import com.ibk.ibkglobal.data.integrator.route.type.InstanceRouteType;
import com.ibkglobal.integrator.engine.model.RouteStatus;
import com.ibkglobal.integrator.engine.monitor.CamelMonitor;
import com.ibkglobal.integrator.manager.instance.InstanceAdmin;
import com.ibkglobal.integrator.manager.model.RouteInfo;
import com.ibkglobal.message.converter.service.ConverterService;

/**
 * Report Route Service
 *
 */
@Service
public class ReportRouterService {

	@Autowired
	InstanceAdmin instanceAdmin;
	
	@Autowired
	ConverterService converterService;
	
	@Autowired
	CamelMonitor camelMonitor;
		
	/**
	 * Get Report Route List
	 * @return List<Map<String, Object>>
	 */
	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> getReportRouteList(String rtId) {
		
		List<Map<String, Object>> result = new ArrayList<>();
		List<RouteInfo> routerList = instanceAdmin.getAllRouteInfo(InstanceRouteType.ROUTER);
		
		routerList.sort((b, c) -> {
			return b.getRouteId().compareTo(c.getRouteId());
		});
		
		routerList.stream()
			.filter(t -> t.getRouteId().contains(rtId))
			.map(m -> m.getRouteCreate())
			.forEach(p -> {
				Map<String, Object> tempMap = new HashMap<>();
				
				try {
					tempMap.putAll(converterService.objectToObject(p.getRouteCreateInfo(), HashMap.class));
				} catch (Exception e) {
					e.printStackTrace();
				}
				
				// 라우트 정보가 여러개 일 경우
				if (p.get() instanceof Collection) {
					List<RouteDefinition> rd = p.get();
					List<RouteStatus> routeStatusList = new ArrayList<>();
					
					for (RouteDefinition rf : rd) {
						RouteStatus routeStatus = camelMonitor.getRouteStatus(rf.getId());
						
						if (routeStatus != null) {
							routeStatusList.add(routeStatus);
						}
					}
					
					tempMap.put("dumpRouteStatsList", routeStatusList);
				}
				// 라우트 정보가 한개 일 경우
				else {
					RouteStatus routeStatus = camelMonitor.getRouteStatus(p.getRouteCreateInfo().getRouteId());
					
					if (routeStatus != null) {
						try {
							tempMap.put("dumpRouteStats", converterService.objectToObject(routeStatus, HashMap.class));
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
				}
				
				result.add(tempMap);
			}
		);
		
		return result;
	}
	
	/**
	 * Get Report Route Detail
	 * @param id
	 * @return Map<String, Object>
	 */
	@SuppressWarnings("unchecked")
	public Map<String, Object> getReportRouteDetail(String id) throws Exception {
		
		RouteCreateInfo info = null;
		try {
			info = instanceAdmin.getAllRouteInfo(InstanceRouteType.ROUTER).stream()
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
		
		return result;
	}
}
