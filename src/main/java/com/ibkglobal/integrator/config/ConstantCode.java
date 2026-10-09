package com.ibkglobal.integrator.config;

public class ConstantCode {

  /** MCA, FEP, EAI 코드명 **/
  public final static String MCA_CODE = "GMC";
  public final static String FEP_CODE = "FEP";
  public final static String EAI_CODE = "EAI";
  
  /** 메시지 형식 관련 **/
  public final static String EAI_PARSING   = "EAI_PARSING";
  public final static String EAI_COMPOSING = "EAI_COMPOSING";

  /** Header 관련 **/
  public final static String DYNAMICIPNAME  = "dynamicIp";               // 동적 목적지 헤더
  public final static String DYNAMICIP      = "${header.dynamicIp}";     // 동적 목적지

  public final static String COMPOSING_HEADER = "COMPOSING_HEADER"; // 컴포징 타입
  public final static String PARSING_TYPE     = "PARSING_TYPE";
  
  /** Log 관련 **/
  public final static String SEQ        = "SEQ";        // 거래 발생 위치 1: Adapter IN, 2: Biz IN, 3: Biz OUT, 4: Adapter OUT
  public final static String TRADE_TYPE = "TRADE_TYPE"; // 거래 타입 헤더
  public final static String INFRA_TYPE = "INFRA_TYPE"; // MCA, EAI, FEP
  public final static String IN_OUT     = "IN_OUT";     // 인바운드, 아웃바운드

  public final static String JMS_CORRELATION_ID = "JMS_CORRELATION_ID";
  
  public final static String IO_INFO    = "IO_INFO";    // 진행중인 IO

  /** Error 관련 **/
  public final static String ERR_SPOT   = "ERR_SPOT";   // 에러 발생위치
  public final static String ERR_CODE   = "ERR_CODE";   // 에러코드
  public final static String ERR_MSG    = "ERR_MSG";    // 에러내용
  public final static String TMSG_ERROR = "TMSG_ERROR"; // 에러내용

  public final static String ERR_CODE_NORMAL = "1"; // 거래처리오류 코드
  public final static String ERR_CODE_TTL    = "8"; // 타임아웃오류 코드
  public final static String ERROR_HEAD      = "E"; // 헤더 index - 0
  public final static String ERROR_HEADER    = "ERROR_HEADER"; // 헤더 index - 0
  
  /** BID 관련 **/
  public final static String BID_WORK_TYPE   = "BID_WORK_TYPE";

  /** Exchange 헤더값 **/
  public final static String FROM_FLAG     = "FROM_FLAG";     // FROM Host인지 여부 "H","E","F","S"
  public final static String START_FLAG    = "START_FLAG";    // 시작이 호스트인지 기관인지 구분 "H" OR "E"
  public final static String FROM_QUE      = "FROM_QUE";      // FROM QUE
  public final static String TO_QUE        = "TO_QUE";        // TO QUE
  public final static String SYS_CODE      = "SYS_CODE";      // 시스템코드
  public final static String BIZ_CODE      = "BIZ_CODE";      // 업무코드
  public final static String ORG_CODE      = "ORG_CODE";      // 기관코드
  public final static String MSG_ORG_CODE  = "MSG_ORG_CODE";  // 메시지에서 사용하는 기관 코드 (COMM 대비)
  public final static String HEADER_ID     = "HEADER_ID";     // 헤더 ID
  public final static String WORK_CODE     = "WORK_CODE";     // 거래구분
  public final static String MSG_CODE      = "MSG_CODE";      // 전문종별
  public final static String TX_CODE       = "TX_CODE";       // 거래구분
  public final static String ETC_CODE      = "ETC_CODE";      // 기타구분
  public final static String AUDIT_NO      = "AUDIT_NO";      // 전문추적번호
  public final static String TRXCQ_NO      = "TRXCQ_NO";      // 전문고유거래번호
  public final static String TRXCQ2_NO     = "TRXCQ2_NO";     // 전문고유거래번호 (all-time)
  public final static String TRXCQ_NO_SRC  = "TRXCQ_NO_SRC";  // 전문고유거래번호 (SOURCE 쪽, 송신에러 등 발생 시 전문복원을 위해)
  public final static String TRXCQ2_NO_SRC = "TRXCQ2_NO_SRC"; // 전문고유거래번호 (all-time, SOURCE 쪽, 송신에러 등 발생 시 전문복원을 위해)
  public final static String RESP_CODE     = "RESP_CODE";     // 응답코드
  public final static String WORK_FLOW     = "WORK_FLOW";     // 업무 플로우
  public final static String ADAPTER_RECV_TIME = "ADAPTER_RECV_TIME"; // 어댑터 수신시간
  public final static String BNCD          = "BNCD";          // 은행코드
  public final static String BRCD          = "BRCD";          // 부점코드
  public final static String TLN           = "TLN";           // 텔러번호

  public final static String TMSG_SUBKEY_NAME = "TMSG_SUBKEY_NAME"; // 조회용 SUBKEY 필드명 ( RFFB - 업체번호)
  public final static String TMSG_SUBKEY      = "TMSG_SUBKEY";      // 조회용 SUBKEY ( RFFB - 업체번호)
  public final static String LOG_YN           = "LOG_YN";           // 로깅 여부
  public final static String XCID             = "XCID";             // 메시지 ID
  
  public final static String GLOBAL_ID     = "GLOBAL_ID";     // globalId
  
  /** 로컬 관련 **/
  public final static String LOCAL_IP         = "LOCAL_IP";         // 로컬 IP
  public final static String LOCAL_PORT       = "LOCAL_PORT";       // 로컬 PORT
  
  /** Session 관련 **/
  public final static String SESSION_ID       = "SESSION_ID";
  
  /** Batch 관련 **/
  public final static String BATCH_MODEL      = "BATCH_MODEL";

  // 아래 두개는 기존 호환성을 위해
  public final static String CONN_NAME      = "JLinkConnName";        // 커넥션 이름
  public final static String CONN_TIME      = "JLinkConnectTime";     // 커넥션이 연결된 시간
  public final static String CONN_NAME_SEND = "JLinkSendConnName";    // 송신 커넥션 이름
  public final static String CONN_TIME_SEND = "JLinkSendConnectTime"; // 송신 커넥션이 연결된 시간
  public final static String CONN_NAME_RECV = "JLinkRecvConnName";    // 수신 커넥션 이름
  public final static String CONN_TIME_RECV = "JLinkRecvConnectTime"; // 수신 커넥션이 연결된 시간
  public final static String ADT_UID        = "ADT_UID";              // 고유 TRASACTION CODE
  public final static String SAF_QUEUE_NAME = "SAF_QUEUE_NAME";       // SAF queue 이름

  public final static String IBK_NORMAL_MESSAGE_YN          = "IBK_NORMAL_MESSAGE_YN";          // IBK 표준전문 여부
  public final static String IBK_NORMAL_MESSAGE_RECOVERY_YN = "IBK_NORMAL_MESSAGE_RECOVERY_YN"; // IBK 표준전문 복원 여부
  public final static String IBK_NORMAL_HEADER              = "IBK_NORMAL_HEADER";              // IBK 표준전문 공통부
  public final static String IBK_GID                        = "IBK_GID";                        // IBK 표준전문 GID
  public final static String IBK_GID_WRTN_YMD               = "IBK_GID_WRTN_YMD";               // IBK 표준전문 작성년월일
  public final static String IBK_GID_CRET_SYS_NM            = "IBK_GID_CRET_SYS_NM";            // IBK 표준전문 생성시스템명
  public final static String IBK_GID_SRN                    = "IBK_GID_SRN";                    // IBK 표준전문 일련번호
  public final static String IBK_INTERFACE_ID               = "IBK_INTERFACE_ID";               // IBK 인터페이스 아이디
  public final static String IBK_INTERFACE_ID_REF           = "IBK_INTERFACE_ID_REF";           // IBK 상위 (전문복원용) 인터페이스
                                                                                                // 아이디
  public final static String IBK_HTDSP                      = "IBK_HTDSP";                      // IBK 당타발 구분
  public final static String IBK_SRFLAG                     = "IBK_SRFLAG";                     // IBK 송수신 플래그 (인터페이스
                                                                                                // 정의)
  public final static String IBK_REQ_RES_FLAG               = "IBK_REQ_RES_FLAG";               // IBK 표준전문 요청/응답 코드
  public final static String IBK_REQ_FLAG_VALUE             = "S";                              // IBK 표준전문 요청 코드 값
  public final static String IBK_RES_FLAG_VALUE             = "R";                              // IBK 표준전문 응답 코드 값
  public final static String IBK_PCRS_DCD                   = "IBK_PCRS_DCD";                   // IBK 표준전문 처리결과구분코드
  public final static String IBK_OTPT_TMGT_DCD              = "IBK_OTPT_TMGT_DCD";              // IBK 표준전문 출력전문유형구분코드
  public final static String IBK_BIZKEY                     = "IBK_BIZKEY";                     // IBK 표준전문 대외 거래 KEY
  public final static String IBK_RES_ERRORCODE              = "IBK_RES_ERRORCODE";              // IBK 응답처리결과 오류구분
  public final static String IBK_DEFAULT_ERROR_RESPONSE     = "IBK_DEFAULT_ERROR_RESPONSE";     // IBK 기본 오류 응답 여부
  public final static String IBK_MAPPING_FLAG               = "IBK_MAPPING_FLAG";               // 매핑 (bypass) 여부

  public final static String IBK_IFEP_MESSAGE = "IBK_IFEP_MESSAGE"; // IFEP 와의 통신을 위한 헤더 여부
  public final static String IBK_IFEP_HEADER  = "IBK_IFEP_HEADER";  // IFEP 와의 통신을 위한 헤더

  public final static String USERBEAN_RETURN = "USERBEAN_RETURN"; // 사용자빈의 리턴값
  public final static String USERBEAN_PARAM  = "USERBEAN_PARAM";  // 사용자빈의 파라미터 값

  public final static String TRANS_TIME            = "TRANS_TIME";             // 거래 발생 시간
  public final static String SERVER_NAME           = "SERVER_NAME";            // 거래 발생 위치 1: Adapter IN, 2: Biz IN, 3:
                                                                               // Biz OUT, 4: Adapter OUT
  public final static String TIMEOUT_VALUE         = "TIMEOUT_VALUE";          // 타임아웃 시간
  public final static String REMOVE_TIMER_IFIDS    = "REMOVE_TIMER_IFIDS";     // 타임아웃 제거 인터페이스 아이디들
  public final static String AUTORESIST_TIMER_FLAG = "AUTORESTIST_TIMER_FLAG"; // PREPROCESSOR 에서 자동 타이머 등록 여부

  public final static String SOURCE_QUEUE = "SOURCE_QUEUE"; // 시작 큐 이름
  public final static String TARGET_QUEUE = "TARGET_QUEUE"; // 전송 큐 이름

  // 원거래, 결번
  public final static String ORG_TRAN_KEY      = "ORG_TRAN_KEY";
  public final static String SR_FLAG           = "SR_FLAG";
  public final static String MISS_NUM_KEY      = "MISS_NUM_KEY";
  public final static String MISS_SEQ_NUM      = "MISS_SEQ_NUM";
  public final static String MISS_NUM_SRCH_KEY = "MISS_NUM_SRCH_KEY";
  public final static String MISS_NUM_MSG      = "MISS_NUM_MSG";

  // batch file handing variables
  public final static String CONN_TYPE = "CONN_TYPE"; // connection type, ex)SEND_CONN(send only) or RECV_CONN(recv
                                                      // only).
  public final static String FILE_NAME = "FILE_NAME"; // batch file name.
  public final static String FILE_PATH = "FILE_PATH"; // batch file path.
  public final static String FILE_SIZE = "FILE_SIZE"; // batch file size.
  public final static String TO_DIRECT = "TO_DIRECT"; // 보내려는 direct 이믈

  public final static String PROC_STATE    = "PROC_STATE";    // batch file state in process.
  public final static String NEXT_MSG_CODE = "NEXT_MSG_CODE"; // expected message code on next turn.

  // 고정큐
  public final static String QUEUE_REGISTER_TIMER = "TIMER.QUEUE";
  public final static String QUEUE_TIMEOUT_TIMER  = "DLQ." + QUEUE_REGISTER_TIMER;

  /****************************************************************/
}
