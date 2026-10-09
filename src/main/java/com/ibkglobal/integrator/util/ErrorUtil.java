package com.ibkglobal.integrator.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.springframework.util.StringUtils;

import com.ibkglobal.common.convert.Converter;
import com.ibkglobal.integrator.config.ConstantCode;
import com.ibkglobal.integrator.exception.CommonException;
import com.ibkglobal.integrator.exception.ErrorType;
import com.ibkglobal.integrator.exception.IBKExceptionMCA;
import com.ibkglobal.message.IBKMessage;
import com.ibkglobal.message.InfraType;
import com.ibkglobal.message.common.normal.DataSetCode;
import com.ibkglobal.message.common.normal.StandardTelegram;
import com.ibkglobal.message.common.normal.copt.SttlMsgCopt;
import com.ibkglobal.message.common.normal.copt.SttlMsgCopt.MsgErrorInfo;
import com.ibkglobal.message.common.normal.copt.SttlMsgCopt.PnmgInfo;

public class ErrorUtil {

  /**
   * 일반 오류 시
   * 
   * @param exchange
   */
  public static void setErrorMessage(Exchange exchange) {
    Message message = exchange.getIn();

    String errorSpot = message.getHeader(ConstantCode.ERR_SPOT, String.class);
    String errorCode = message.getHeader(ConstantCode.ERR_CODE, String.class);

    StandardTelegram standardTelegram = null;

    if (message.getBody() instanceof IBKMessage) {
      IBKMessage ibkMessage = message.getBody(IBKMessage.class);

      standardTelegram = ibkMessage.getStandardTelegram();
    } else if (message.getBody() instanceof String) {
      try {
        standardTelegram = Converter.mapper.readValue(message.getBody(String.class), StandardTelegram.class);
      } catch (Exception e) {
        e.printStackTrace();
      }
    }

    if (standardTelegram != null) {
      switch (ErrorType.of(errorCode.substring(7))) {
      case TTL:
        standardTelegram.getSttlSysCopt().setRspnPcrsDcd(ConstantCode.ERR_CODE_TTL); // 응답처리결과구분코드(오류 시 8로)
        break;
      default:
        standardTelegram.getSttlSysCopt().setRspnPcrsDcd(ConstantCode.ERR_CODE_NORMAL); // 응답처리결과구분코드(오류 시 1로)
        break;
      }

      standardTelegram.getSttlSysCopt().setErocSysDcd(errorSpot); // 오류 발생위치
      standardTelegram.getSttlSysCopt().setSttlErcd(errorCode); // 표준전문오류코드

      standardTelegram.setSttlMsgCopt(createSttlMsgCopt(message, standardTelegram)); // 에러 메시지 설정

      message.setHeader(ConstantCode.ERROR_HEADER, standardTelegram);
    }
  }

  /**
   * 헬스체크 오류 시
   * 
   * @param exchange
   */
  public static void setErrorMessageHealth(Exchange exchange) {

  }

  public static SttlMsgCopt createSttlMsgCopt(Message message, StandardTelegram standardTelegram) {
    String errorSeq = message.getHeader(ConstantCode.SEQ, String.class);
    String errorMsg = message.getHeader(ConstantCode.ERR_MSG, String.class);

    if (StringUtils.isEmpty(errorSeq)) {
      errorSeq = "0";
    }

    SttlMsgCopt sttlMsgCopt = new SttlMsgCopt();
    sttlMsgCopt.setDtstDcd(DataSetCode.MSG_COPT);
    sttlMsgCopt.setMsgRpsnDcd("0");

    // 주메시지정보(PNMG_INFO)
    List<PnmgInfo> pnmgInfoList = new ArrayList<>();

    PnmgInfo pnmgInfo = new PnmgInfo();
    pnmgInfo.setPnmgCd(standardTelegram.getSttlSysCopt().getSttlErcd());
    pnmgInfo.setPnmgDsncPgrsNo(1);
    pnmgInfo.setPnmgCon(errorMsg);

    pnmgInfoList.add(pnmgInfo);

    sttlMsgCopt.setPnmgInfo(pnmgInfoList);

    // 메시지 오류정보(MSG_EROR_INFO)
    sttlMsgCopt.setMsgErifItrnNbt(1);

    List<MsgErrorInfo> msgErrorInfoList = new ArrayList<>();

    MsgErrorInfo msgErrorInfo = new MsgErrorInfo();
    msgErrorInfo.setPnmgInkyVl(Integer.parseInt(errorSeq));
    msgErrorInfo.setMsgErifCon(errorMsg);

    msgErrorInfoList.add(msgErrorInfo);

    sttlMsgCopt.setMsgErrorInfo(msgErrorInfoList);

    return sttlMsgCopt;
  }

  /**
   * Exchange 에러코드 셋
   * 
   * @param exchange
   * @param throwable
   */
  public static void setErrorCode(Exchange exchange, InfraType infraType, Throwable throwable) {
    Message message = exchange.getIn();

    String bizCode = message.getHeader(ConstantCode.BIZ_CODE, String.class);

    message.setHeader(ConstantCode.ERR_SPOT, infraType);
    message.setHeader(ConstantCode.ERR_CODE, createErrorCode(infraType, bizCode, throwable));
    message.setHeader(ConstantCode.ERR_MSG, createErrorMsg(throwable));
  }

  public static String createErrorSpot(Throwable throwable) {
    String sysCode = "SYS";

    if (throwable instanceof IBKExceptionMCA) {
      sysCode = ConstantCode.MCA_CODE;
    }

    return sysCode;
  }

  /**
   * 에러 코드 생성(12자리)
   * 
   * @param bizCode
   * @param throwable
   * @return
   */
  public static String createErrorCode(InfraType infraType, String bizCode, Throwable throwable) {
    String sysCode = "SYS";

    // 시스템 구분
    if (throwable instanceof IBKExceptionMCA) {
      sysCode = ConstantCode.MCA_CODE;
    } else {
      sysCode = infraType.name();
    }

    // 업무 구분
    if (bizCode == null || bizCode.length() != 3) {
      bizCode = sysCode;
    }

    // 일련번호
    String code = "99999";

    if (throwable instanceof CommonException) {
      ErrorType errorType = ((CommonException) throwable).getErrorType();
      if (errorType != null) {
        code = errorType.getErrorCode();
      }
    }

    return ConstantCode.ERROR_HEAD + sysCode + bizCode + code;
  }

  /**
   * 에러 메시지 생성
   * 
   * @param throwable
   * @return
   */
  public static String createErrorMsg(Throwable throwable) {
    if (throwable == null) {
      return "[Error Content Null]";
    }

    String errorMsg = throwable.getMessage();

    if (StringUtils.isEmpty(errorMsg)) {
      errorMsg = throwable.getLocalizedMessage();
    }

    return errorMsg;
  }
}
