package com.ibkglobal.integrator.engine.bean.common.process;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.commons.lang3.SerializationUtils;

import com.ibkglobal.message.IBKMessage;

public class CloneIBKMessageProcessor implements Processor {

	@Override
	public void process(Exchange exchange) throws Exception {
		Message message = exchange.getIn();		
		message.setBody(SerializationUtils.clone(message.getBody(IBKMessage.class)));
	}
}
