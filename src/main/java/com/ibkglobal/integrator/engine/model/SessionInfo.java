package com.ibkglobal.integrator.engine.model;

import lombok.Data;

@Data
public class SessionInfo {
	
	private String              sysCd;             // 시스템
	private String              bizCd;             // 업무
	private String              orgCd;             // 기관
	
	private String              sessionKey;        // 세션키
}
