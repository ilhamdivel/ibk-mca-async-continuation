package com.ibkglobal.integrator.engine.model;

import org.apache.camel.Exchange;

import lombok.Data;

@Data
public class IBKTimeoutInfo {

	private String key;
	private String bizCode;
	private String orgCode;
	private String currentDate;
	private long currentTime;
	private long timeOut;
	private Exchange exchange;
}
