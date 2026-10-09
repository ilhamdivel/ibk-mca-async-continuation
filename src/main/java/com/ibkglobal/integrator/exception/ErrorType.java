package com.ibkglobal.integrator.exception;

import java.util.Arrays;

public enum ErrorType {
	// Common Error
	ROUTE			("00000"), 	// 라우터	
	MESSAGE			("00001"), 	// 메시지 정보 Search
	PARSING			("00002"), 	// 파싱
	MAPPING			("00003"), 	// 메시지 매핑
	COMPOSING		("00004"), 	// 메시지 컴포징
	VALID			("00005"), 	// 유효성
	TTL				("00006"), 	// Time To Live	
	HEALTH_CHECK	("00007"), 	// Health Check
	TIMEOUT			("00008"),	// TIMEOUT
	BID				("00009"),	// BID
	LOG_ERROR		("00010"),  // LOG TRANSFORM 오류

	// MCA Error
	MCA				("01000"),
	MCA_BID			("01001"),
	MCA_BID_TIMEOUT	("01002"),  // BID TIMEOUT

	// FEP Error
	FEP				("02000"),
	FEP_RECOVERY	("02001"), 	// 복원
	FEP_HOST_SEND	("02002"),  // HOST 전송 시 에러
	FEP_EXT_SEND	("02003"),  // EXT  전송 시 에러
	
	EAI				("03000"),
	EAI_BATCH		("03001"),
	EAI_FITE_TO_FILE("03002"),
	EAI_FTP_TO_FTP	("03003"),
	EAI_DB_TO_DB	("03004"),
	EAI_MMS_SVC_ID	("03005"),
	
	ETC				("99999") 	// 기타
	;

	private String	errorCode;

	private ErrorType(String errorCode) {
		this.errorCode = errorCode;
	}
	
	public String getErrorCode() {
		return errorCode;
	}

	public static ErrorType of(String code) {
		try {
			return Arrays.stream(values()).filter(v -> v.errorCode.equals(code)).findFirst().get();
		} catch (Exception e) {
		}
		return ErrorType.ETC;
	}
}
