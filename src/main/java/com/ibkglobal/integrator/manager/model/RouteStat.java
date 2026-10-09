package com.ibkglobal.integrator.manager.model;

import lombok.Data;

@Data
public class RouteStat {

	String routeId;
	String group;
	String description;
	
	boolean isStarted;
	boolean isStopped;
	
	String from;
	String[] outputs;
	
	boolean isConsumerStarted;
}
