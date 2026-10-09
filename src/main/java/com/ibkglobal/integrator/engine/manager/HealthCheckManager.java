package com.ibkglobal.integrator.engine.manager;

import java.util.HashMap;
import java.util.Map;

import com.ibkglobal.integrator.engine.model.HealthCheckInfo;

public class HealthCheckManager {
	
	private static Map<String, HealthCheckInfo> clientList = new HashMap<>();
	
	public static Map<String, HealthCheckInfo> getClientList() {
		synchronized (clientList) {
			return clientList;
		}
	}
}
