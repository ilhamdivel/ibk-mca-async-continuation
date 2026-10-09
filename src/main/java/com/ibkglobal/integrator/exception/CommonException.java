package com.ibkglobal.integrator.exception;

import lombok.Getter;
import lombok.Setter;

@SuppressWarnings("serial")
public class CommonException extends Exception {

	@Getter
	@Setter
	ErrorType errorType;

	public CommonException() {
		super();
	}

	public CommonException(String message) {
		super(message);
	}

	public CommonException(String message, Throwable cause) {
		super(message, cause);
	}

	public CommonException(Throwable cause) {
		super(cause);
	}

	public CommonException(ErrorType errorType) {
		super();
		setErrorType(errorType);
	}

	public CommonException(ErrorType errorType, String message) {
		super(message);
		setErrorType(errorType);
	}

	public CommonException(ErrorType errorType, String message, Throwable cause) {
		super(message, cause);
		setErrorType(errorType);
	}

	public CommonException(ErrorType errorType, Throwable cause) {
		super(cause);
		setErrorType(errorType);
	}
}
