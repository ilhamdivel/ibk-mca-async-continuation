package com.ibkglobal.integrator.manager.instance;

import java.util.HashMap;
import java.util.Map;

import com.ibkglobal.integrator.manager.model.RouteInfo;

import lombok.Getter;

public class InstanceRoute {
	
	@Getter
	private Map<String, RouteInfo> adapterList; // 어댑터 리스트
	
	@Getter
	private Map<String, RouteInfo> routeList;   // 라우터 리스트
	
	@Getter
	private Map<String, RouteInfo> etcList;     // 기타 리스트
	
	public void init() {
		adapterList = new HashMap<>();
		routeList   = new HashMap<>();
		etcList     = new HashMap<>();
	}
}
