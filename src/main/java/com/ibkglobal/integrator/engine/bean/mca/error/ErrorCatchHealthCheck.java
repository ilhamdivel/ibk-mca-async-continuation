package com.ibkglobal.integrator.engine.bean.mca.error;

import org.apache.camel.Exchange;
import org.apache.camel.Message;

import com.ibkglobal.integrator.util.HealthCheckUtil;

public class ErrorCatchHealthCheck {
	
	public void catchError(Exchange exchange) {
		Message message = exchange.getIn();
		message.setBody(HealthCheckUtil.nakMessage());
	}
}
