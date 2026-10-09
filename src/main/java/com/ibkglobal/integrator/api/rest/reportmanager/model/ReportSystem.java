package com.ibkglobal.integrator.api.rest.reportmanager.model;

import lombok.Data;

@Data
public class ReportSystem {
	
	private String  systemName;
	private Integer cpuStatus;
	private Integer memStatus;
	private Integer diskStatus;
}
