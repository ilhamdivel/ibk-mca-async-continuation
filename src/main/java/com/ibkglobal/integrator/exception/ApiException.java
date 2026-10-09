package com.ibkglobal.integrator.exception;

@SuppressWarnings("serial")
public class ApiException extends Exception {
	
	public ApiException() {
		super();
	}

	public ApiException(String message) {
		super(message);
	}
}
