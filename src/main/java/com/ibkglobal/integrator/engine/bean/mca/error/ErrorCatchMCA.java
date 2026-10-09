package com.ibkglobal.integrator.engine.bean.mca.error;

import org.apache.camel.Exchange;
import org.springframework.beans.factory.annotation.Autowired;

import com.ibkglobal.integrator.exception.ErrorType;
import com.ibkglobal.integrator.exception.IBKExceptionMCA;
import com.ibkglobal.integrator.util.ErrorUtil;
import com.ibkglobal.message.InfraType;
import com.ibkglobal.message.converter.service.ConverterService;

public class ErrorCatchMCA {

	@Autowired
	ConverterService converterService;

	public void catchError(Exchange exchange) {
		// 초기 설정
		init(exchange);
	}

	/**
	 * Catch ReadTimeoutException
	 * 
	 * @param exchange
	 */
	public void catchReadTimeoutException(Exchange exchange) {

		Throwable throwable = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);
		exchange.setProperty(Exchange.EXCEPTION_CAUGHT,
				new IBKExceptionMCA(ErrorType.TTL, "Read Timeout Exception", throwable));

		init(exchange); // 초기 설정
	}

	/**
	 * 초기 설정
	 * 
	 * @param exchange
	 */
	private void init(Exchange exchange) {
		Throwable throwable = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);

		// 에러 코드 생성
		ErrorUtil.setErrorCode(exchange, InfraType.MCA, throwable);

		// 에러 전문 Set
		ErrorUtil.setErrorMessage(exchange);

		// Result Set
		exchange.getOut().copyFrom(exchange.getIn());
	}
}
