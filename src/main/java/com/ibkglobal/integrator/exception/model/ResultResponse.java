package com.ibkglobal.integrator.exception.model;

import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ResultResponse {

	private Boolean	succeded;
	private Object	data;
	private Error	error;
	private int totalcount;
	//private String  error;
	
	public ResultResponse(Object data) {
		this.succeded = Boolean.TRUE;
		this.data = data;
	}
	
	public ResultResponse(Object data, int totalcount) {
		this.succeded = Boolean.TRUE;
		this.data = data;
		this.totalcount = totalcount;
	}
	
	public ResultResponse(Boolean succeded, Object data) {
		this.succeded = succeded;
		this.data = data;
	}
	
	public ResultResponse(Boolean succeded, Object data, int totalcount) {
		this.succeded = succeded;
		this.data = data;
		this.totalcount = totalcount;
	}
	
	public ResultResponse(Boolean succeded, Error error) {
		this.succeded = succeded;
		this.error = error;
	}

	public static ResultResponse successResponse() {
		ResultResponse response = new ResultResponse();
		response.setSucceded(true);
		return response;
	}

	@Data
	@AllArgsConstructor
	public static class Error {

		private String	code;
		private String	message;

		@Component
		public static class ErrorFactory {

			public Error getError(Exception e) {
				return new Error("0000", e.getMessage());
			}
			
			public Error getError(String code, Exception e) {
				return new Error(code, e.getMessage());
			}
		}
	}

}