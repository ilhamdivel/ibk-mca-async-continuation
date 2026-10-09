package com.ibkglobal.integrator.exception;

@SuppressWarnings("serial")
public class IBKExceptionMCA extends CommonException {
	
	public IBKExceptionMCA(ErrorType errorType) {
		super();
		setErrorType(errorType);
	}
	
	public IBKExceptionMCA(ErrorType errorType, String message) {
		super(message);
		setErrorType(errorType);
	}
	
	public IBKExceptionMCA(ErrorType errorType, Throwable cause) {
		super(cause);
		setErrorType(errorType);
	}
	
	public IBKExceptionMCA(ErrorType errorType, String message, Throwable cause) {
		super(message, cause);
		setErrorType(errorType);
	}
	
	public IBKExceptionMCA(ErrorType errorType, String message, Exception cause) {
		super(message, cause);
		setErrorType(errorType);
	}
}
