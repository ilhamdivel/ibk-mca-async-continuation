package com.ibkglobal.integrator.config;

import java.util.Arrays;

public class TelegramConstants {
	
	/*------------------------------------------------------------
	 표준전문공통정보
	 -------------------------------------------------------------*/
	public static final int SYSTEM_HEADER_LENGTH = 450; 		// 시스템헤더공통부 길이
	public static final int TRANSACTION_HEADER_LENGTH = 250; 	// 거래헤더공통부 길이
	public static final int MEETING_CHANNEL_LENGTH = 132; 		// 대면채널헤더 길이
	public static final int NON_FACING_CHANNEL_LENGTH = 605; 	// 비대면채널헤더 길이

	public static final int DATASET_ARRAY_SIZE_LENGTH = 2; 		// 디폴트 반복부 반복횟수
	public static final int DATASET_CODE_LENGTH = 2;       		// 데이터셋 CODE 길이
	public static final int DATASET_LENGTH_LENGTH = 6;	   		// 데이터셋 LENGTH 길
	public static final int DATASET_TOTAL_LENGTH = DATASET_CODE_LENGTH + DATASET_LENGTH_LENGTH;

	public static final int TOTAL_LENGTH_POSITION = 6; 			// 표준전문 총 길이(6byte)
	public static final String LENGTH_STRING_FORMAT = "%06d"; 	// 표준전문 총 길이(6byte)
	public static final String END_SIGNAL = "@@"; 				// 전문 종료부(2)
	
	/**
	 * 시스템공통부 입력전문유형구분코드
	 * <ol>
	 * <li>0: 통상출력</lie>
	 * <li>1: 대량입력시작</lie>
	 * <li>2: 대량입력계속</lie>
	 * <li>3: 대량입력끝</lie>
	 * <li>8: 대량출력중지요청</lie>
	 * <li>9: 대량출력계속요청</lie>
	 * </ol>
	 */
	public enum InptTmgtDcd {
		NORMAL("0")
		, MASS_INPUT_START("1")
		, MASS_INPUT_CONTINUE("2")
		, MASS_INPUT_END("3")
		, MASS_OUT_STOP_REQUEST("8")
		, MASS_OUT_CONTINUE_REQUEST("9")
		, NONE("none")
		;
		
		private String code;
		
		private InptTmgtDcd(String code) {
			this.code = code;
		}
		
		public static InptTmgtDcd of(String code) {
			try {
				return Arrays.stream(values()).filter(v -> v.code.equals(code)).findFirst().get();
			} catch (Exception e) {}
			return InptTmgtDcd.NONE;
		}
	}
	
	/**
	 * 시스템공통부 출력전문유형구분코드 정의
	 * <ol>
	 * <li>0: 통상출력
	 * <li>1: 대량출력시작
	 * <li>2: 대량출력계속
	 * <li>3: 대량출력끝
	 * <li>4: Dummy응답출력
	 * <li>5: 단말BID출력
	 * <li>6: 수신확인BID출력
	 * <li>8: 대량입력중지요청
	 * <li>9: 대량입력계속요청
	 * </ol>
	 */
	public enum StandardTelegramType {
		
		NORMAL("0"), 
		BULK_START("1"), 
		BULK_CONTINUE("2"), 
		BULK_END("3"), 
		DUMMY("4"), 
		BID("5"), 
		RCV_CONFIRM_BID("6"), 
		BULK_STOP_REQUEST("8"), 
		BULK_CONTINUE_REQUEST("9"), 
		NONE(" ");

		private String type;

		private StandardTelegramType(String type) {
			this.type = type;
		}

		public String getString() {
			return type;
		}

		public static StandardTelegramType fromString(String type) {
			for (StandardTelegramType standardTelegramType : StandardTelegramType.values()) {
				if (standardTelegramType.type.equals(type)) {
					return standardTelegramType;
				}
			}
			return StandardTelegramType.NONE;
		}
	}
}
