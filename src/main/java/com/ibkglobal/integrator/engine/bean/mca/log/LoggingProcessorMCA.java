package com.ibkglobal.integrator.engine.bean.mca.log;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;

public class LoggingProcessorMCA implements Processor {

	@Override
	public void process(Exchange exchange) throws Exception {
		//LoggingMCA.logging(exchange);
		new LoggingMCA().logging(exchange);
	}
}
