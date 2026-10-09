package com.ibkglobal.integrator.exception.controlleradvice;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;

import com.ibkglobal.integrator.exception.ApiException;
import com.ibkglobal.integrator.exception.model.ResultResponse;
import com.ibkglobal.integrator.exception.model.ResultResponse.Error;
import com.ibkglobal.integrator.exception.model.ResultResponse.Error.ErrorFactory;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.log.LogType;

@ControllerAdvice
@RestController
public class ApiExceptionhandler {

	@Autowired
	private ErrorFactory errorFactory;
	
	@ExceptionHandler(value = ApiException.class)
	public ResultResponse apiException(Exception e) {
		Error error = errorFactory.getError(e);
		
		LogManager.getLogger(LogType.SYSTEM).info(error.toString());
		return new ResultResponse(false, error);
	}
}
