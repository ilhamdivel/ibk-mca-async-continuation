package com.ibkglobal.integrator.engine.bean.mca.log;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibkglobal.common.convert.Converter;
import com.ibkglobal.common.log.LogMCA;
import com.ibkglobal.config.CommonConstantCode;
import com.ibkglobal.integrator.config.ConstantCode;
import com.ibkglobal.integrator.engine.bean.common.TranLogApi;
import com.ibkglobal.integrator.exception.ErrorType;
import com.ibkglobal.integrator.exception.IBKExceptionMCA;
import com.ibkglobal.integrator.util.CommonUtil;
import com.ibkglobal.integrator.util.SystemUtil;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.log.LogType;
import com.ibkglobal.message.IBKMessage;
import com.ibkglobal.message.common.normal.StandardTelegram;

import ch.qos.logback.classic.Logger;

public class LoggingMCA {

  public void loggingError(Exchange exchange) throws IBKExceptionMCA {

    Message message = exchange.getIn();

    // 에러 로그 파일 생성
    Logger logger = LogManager.getLogger(LogType.EXCEPTION);

    // 로깅
    logger.info(createLogFormat(message));

    callTranManager(message);
  }

  public void logging(Exchange exchange) throws IBKExceptionMCA {

    Message message = exchange.getIn();

    String logName = message.getHeader(CommonConstantCode.LOGGER_KEY, String.class);

    // 다이나믹 로그 파일 생성
    LogManager.putMdc(logName);
    Logger logger = LogManager.getLogger(LogType.DYNAMIC);

    // 로깅
    logger.info(createLogFormat(message));
  }

  public Logger getLogger(Exchange exchange) throws IBKExceptionMCA {

    Message message = exchange.getIn();

    String logName = message.getHeader(CommonConstantCode.LOGGER_KEY, String.class);

    // 다이나믹 로그 파일 생성
    LogManager.putMdc(logName);
    Logger logger = LogManager.getLogger(LogType.DYNAMIC);

    return logger;
  }

  /**
   * MCA 로그 포멧
   * 
   * @param message
   * @return
   * @throws IBKExceptionMCA
   */
  protected String createLogFormat(Message message) throws IBKExceptionMCA {

    LogMCA logMca = new LogMCA();
    TranLogApi tranLogApi = new TranLogApi();

    // base
    logMca.setSeq(CommonUtil.nullCheck(message.getHeader(ConstantCode.TRADE_TYPE, String.class))
        + CommonUtil.nullCheck(message.getHeader(ConstantCode.SEQ, String.class)));
    logMca.setSysCd(CommonUtil.nullCheck(message.getHeader(ConstantCode.SYS_CODE, String.class)));
    logMca.setBizCd(CommonUtil.nullCheck(message.getHeader(ConstantCode.BIZ_CODE, String.class)));
    // logMca.setTransTime(CommonUtil.nullCheck(message.getHeader(ConstantCode.TRANS_TIME,
    // String.class)));
    logMca.setTransTime(SystemUtil.getCurrentDate("yyyyMMddHHmmssSSS"));
    logMca.setOriRecvTime(CommonUtil.nullCheck(message.getHeader(ConstantCode.ADAPTER_RECV_TIME, String.class)));

    logMca.setWorkFlow(getWorkFlow(logMca.getSeq()));

    logMca.setSttlYn("N");

    String session = message.getHeader("CamelNettyChannelHandlerContext") == null ? ""
        : message.getHeader("CamelNettyChannelHandlerContext").toString();
    logMca.setSession(session);

    String strLog = "";

    if (message.getBody() instanceof IBKMessage) {
      IBKMessage ibkMessage = message.getBody(IBKMessage.class);

      // StandardTelegram standardTelegram = null;
      //
      // if (ibkMessage.getStandardTelegram() != null) {
      // standardTelegram =
      // SerializationUtils.clone(ibkMessage.getStandardTelegram());
      // }

      // // 표준 전문일 경우 마스킹 처리
      // if (message.getHeader(ConstantCode.IO_INFO) != null) {
      // MaskingUtil.setMasking(standardTelegram,
      // message.getHeader(ConstantCode.IO_INFO, Io.class));
      // }

      setDefaultLog(logMca, ibkMessage.getStandardTelegram());
      // 더미 거래인 경우 트랜매니저에 종료 신호 전송
      if (!StringUtils.isEmpty(ibkMessage.getStandardTelegram().getSttlSysCopt().getOtptTmgtDcd())
          && "0".equals(ibkMessage.getStandardTelegram().getSttlSysCopt().getOtptTmgtDcd())
          && "R".equals(ibkMessage.getStandardTelegram().getSttlSysCopt().getRqstRspnDcd())
          && "K".equals(ibkMessage.getStandardTelegram().getSttlSysCopt().getSyncRspnWaitDcd())) {
        // Dummy이면서 N6종료 거래 시 트랜매니져로 END 신호 송신

        LogManager.getLogger(LogType.ROOT).info("Dummy End..");
        try {
          tranLogApi.TranLog(ibkMessage, "End", "MCA_CHN");
        } catch (Exception e) {
          LogManager.getLogger("in createLogFormat stack trace : " + e.getStackTrace());
          LogManager.getLogger(LogType.ROOT)
              .info("TranApi Error: " + message.getBody(IBKMessage.class).getInterfaceId() + ": " + e.getMessage());
        }
      }

    } else if (message.getBody() instanceof String) {
      strLog = message.getBody(String.class);
      logMca.setMsg(convertStandardTelegram(logMca, strLog));
    } else if (message.getBody() instanceof byte[]) {
      strLog = new String(message.getBody(byte[].class));
      logMca.setMsg(convertStandardTelegram(logMca, strLog));
    } else {
      strLog = new String(message.getBody(String.class));
      logMca.setMsg(convertStandardTelegram(logMca, strLog));
    }

    // 로그가 표준전문이 아닐 경우
    if (logMca.getMsg() == null) {
      logMca.setBaseMsg(strLog);
    }

    String result = "";
    try {

      result = Converter.mapper.writeValueAsString(logMca);
      result += " " + Converter.mapper.writeValueAsString(logMca.getMsg());

    } catch (Exception e) {
      LogManager.getLogger("in createLogFormat stack trace : " + e.getStackTrace());
      throw new IBKExceptionMCA(ErrorType.LOG_ERROR, "로그 변환 오류");
    }

    return result;
  }

  /**
   * 표준전문 기본 셋
   * 
   * @param logMca
   * @param standardTelegram
   */
  protected void setDefaultLog(LogMCA logMca, StandardTelegram standardTelegram) {

    if (standardTelegram != null) {

      logMca.setMsg(standardTelegram);
      logMca.setSttlYn("Y");

      if (standardTelegram.getSttlSysCopt() != null) {

        // 전행표준전문작성년월일
        String whbnSttlWrtnYms = standardTelegram.getSttlSysCopt().getWhbnSttlWrtnYmd();
        // 전행표준전문생성시스템명
        String whbnSttlCretSysNm = standardTelegram.getSttlSysCopt().getWhbnSttlCretSysNm();
        // 전행표준전문일련번호
        String whbnSttlSrn = standardTelegram.getSttlSysCopt().getWhbnSttlSrn();

        logMca.setIntfId(standardTelegram.getSttlSysCopt().getSttlIntfId());
        logMca.setGlobalId(whbnSttlWrtnYms + whbnSttlCretSysNm + whbnSttlSrn);
        logMca.setRqstRspnDcd(standardTelegram.getSttlSysCopt().getRqstRspnDcd());
        logMca.setRspnPcrsDcd(standardTelegram.getSttlSysCopt().getRspnPcrsDcd());
        logMca.setOtptTmgtDcd(standardTelegram.getSttlSysCopt().getOtptTmgtDcd());
      }
    }
  }

  /**
   * 표준 전문 변환 안되면 기존 데이터로 리턴
   * 
   * @param logMca
   * @param data
   * @return
   */
  protected Object convertStandardTelegram(LogMCA logMca, String data) {

    try {
      StandardTelegram standardTelegram = Converter.mapper.readValue(data, StandardTelegram.class);

      setDefaultLog(logMca, standardTelegram);

      return standardTelegram;
    } catch (Exception e) {
      LogManager.getLogger("in convertStandardTelegram stack trace : " + e.getStackTrace());

      return null;
    }
  }

  /**
   * 업무 흐름
   * 
   * @param seq
   * @return
   */
  protected String getWorkFlow(String seq) {

    String result = null;

    switch (seq) {
    case "0":
      result = "ERROR";
      break;
    case "1":
      result = "FROM IN ADAPTER";
      break;
    case "2":
      result = "PreProcess";
      break;
    case "3":
      result = "TO IN ADAPTER";
      break;
    case "4":
      result = "TO OUT ADAPTER";
      break;
    case "5":
      result = "AfterProcess";
      break;
    case "6":
      result = "FROM OUT ADAPTER";
      break;
    default:
      result = "DEFAULT";
      break;
    }

    return result;
  }

  public void callTranManager(Message message) throws IBKExceptionMCA {
    try {
      TranLogApi tranLogApi = new TranLogApi();

      IBKMessage ibkMessage = message.getBody(IBKMessage.class);
      if (StringUtils.isEmpty(CommonUtil.nullCheck(message.getHeader(ConstantCode.SEQ, String.class)))) {
        if (ibkMessage == null && !StringUtils.isEmpty(message.getBody(String.class))) {
          ObjectMapper objMapper = new ObjectMapper();
          ibkMessage = new IBKMessage();
          StandardTelegram standardTelegram = new StandardTelegram();

          standardTelegram = objMapper.readValue(message.getBody(String.class), StandardTelegram.class);
          ibkMessage.setStandardTelegram(standardTelegram);
        }

        tranLogApi.TranLog(ibkMessage, "End", "MCA_BIZ");
        tranLogApi.TranLog(ibkMessage, "End", "MCA_CHN");
      } else if (message.getHeader(ConstantCode.SEQ, String.class).equals("1")
          || message.getHeader(ConstantCode.SEQ, String.class).equals("2")
          || message.getHeader(ConstantCode.SEQ, String.class).equals("5")) {
        tranLogApi.TranLog(ibkMessage, "End", "MCA_CHN");
      } else if (message.getHeader(ConstantCode.SEQ, String.class).equals("3")
          || message.getHeader(ConstantCode.SEQ, String.class).equals("4")) {
        tranLogApi.TranLog(ibkMessage, "End", "MCA_BIZ");
        tranLogApi.TranLog(ibkMessage, "End", "MCA_CHN");
      }

    } catch (Exception ex) {
      LogManager.getLogger("in callTranManager stack trace : " + ex.getStackTrace());
      LogManager.getLogger(LogType.ROOT)
          .info("TranApi Error: " + message.getBody(IBKMessage.class).getInterfaceId() + ": " + ex.getMessage());
    }
  }
}
