package com.ibkglobal.integrator.engine.bean.mca.common;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

import com.ibkglobal.integrator.config.ConstantCode;
import com.ibkglobal.integrator.engine.bean.mca.log.LoggingMCA;
import com.ibkglobal.integrator.exception.ErrorType;
import com.ibkglobal.integrator.exception.IBKExceptionMCA;
import com.ibkglobal.message.IBKMessage;
import com.ibkglobal.message.common.normal.StandardTelegram;
import com.ibkglobal.message.converter.service.ConverterService;

public class ProcessHeaderMCA {
	
	@Autowired
	ConverterService converterService;
	
	public void headerCheck(Exchange exchange) throws IBKExceptionMCA {
		
		try {
			Message message = exchange.getIn();

			IBKMessage ibkMessage = message.getBody(IBKMessage.class);
			
			StandardTelegram standardTelegram = ibkMessage.getStandardTelegram();
			
			if(!StringUtils.isEmpty(standardTelegram.getSttlSysCopt().getRspnPcrsDcd()) 
					&& !standardTelegram.getSttlSysCopt().getRspnPcrsDcd().equals("0")) {
				message.setHeader(ConstantCode.SEQ, "5");
				message.setHeader(ConstantCode.IBK_NORMAL_MESSAGE_YN, "Y");
				message.setHeader(ConstantCode.TRADE_TYPE, "E");
				
				//LoggingMCA.loggingError(exchange);
				new LoggingMCA().loggingError(exchange);
				
				exchange.getOut().setFault(true);
				exchange.getOut().setBody(converterService.objectToJson(standardTelegram));
				exchange.getOut().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON);
				exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
			}			
		} catch (Exception e) {
			throw new IBKExceptionMCA(ErrorType.PARSING, "Header Check Error");
		}
	}
}
