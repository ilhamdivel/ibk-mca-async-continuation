package com.ibkglobal.integrator.api.rest.manager.model;

import lombok.Data;

public class ManagerModel {
	
	@Data
	public static class systemStatus {
		private String srvrIp;
		private String srvrNm;
		private String cpuUsage;
		private String diskUsage;
		private String memoryUsage;
		private String dbInfo;
	}
}

