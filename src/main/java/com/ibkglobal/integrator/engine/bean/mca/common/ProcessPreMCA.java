package com.ibkglobal.integrator.engine.bean.mca.common;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import com.ibk.ibkglobal.data.intf.Interface;
import com.ibkglobal.common.validator.ValidatorService;
import com.ibkglobal.config.RouteProperties;
import com.ibkglobal.integrator.config.ConstantCode;
import com.ibkglobal.integrator.engine.bean.mca.log.LoggingMCA;
import com.ibkglobal.integrator.engine.manager.BidManager;
import com.ibkglobal.integrator.exception.ErrorType;
import com.ibkglobal.integrator.exception.IBKExceptionMCA;
import com.ibkglobal.integrator.util.BidUtil;
import com.ibkglobal.integrator.util.SystemUtil;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.log.LogType;
import com.ibkglobal.message.IBKMessage;
import com.ibkglobal.message.InfraType;

import io.netty.util.internal.StringUtil;

public class ProcessPreMCA extends ProcessDefaultMCA {

  @Autowired
  ValidatorService validatorService;

  @Autowired
  BidManager bidManager;

  @Autowired
  RouteProperties routeProperties;

  public void preProcess(Exchange exchange) throws IBKExceptionMCA {
    // 헤더 초기화
    initSetHeader(exchange);

    // Valid Check 및 바디 파싱
    init(exchange);

    // PreProcess Logging
    // LoggingMCA.logging(exchange);
    new LoggingMCA().logging(exchange);
  }

  protected void initSetHeader(Exchange exchange) throws IBKExceptionMCA {
    Message message = exchange.getIn();

    message.setHeader(ConstantCode.SEQ, "2");
    message.setHeader(ConstantCode.IBK_NORMAL_MESSAGE_YN, "Y");
    message.setHeader(ConstantCode.INFRA_TYPE, InfraType.MCA);
    message.setHeader(ConstantCode.IN_OUT, "IN");
  }

  protected void init(Exchange exchange) throws IBKExceptionMCA {
    IBKMessage ibkMessage = exchange.getIn().getBody(IBKMessage.class);

    // ValidCheck
    // ValidResult validResult =
    // validatorService.validationSttl(ibkMessage.getStandardTelegram(),
    // resourceMCA);
    // if (!validResult.isResult()) {
    // String errorContent = "Valid Error";
    // if (validResult.getErrorList().size() > 0) {
    // errorContent = validResult.getErrorList().get(0);
    // }
    // throw new IBKExceptionMCA(ErrorType.VALID, errorContent);
    // }

    // Body Parsing
    if (!ibkMessage.getInterfaceId().equals("ITRO00000035")) {
      bodyParsing("IN", ibkMessage);

      // Log 마스킹 위한 Io Set
      // 오류 발생
      // exchange.getIn().setHeader(ConstantCode.IO_INFO, getIo("IN",
      // ibkMessage.getInterfaceId()));
    }

    // 실제응답 시 dummy wait 해제
    if (ibkMessage.getStandardTelegram() != null) {
      String dummyCheck = ibkMessage.getStandardTelegram().getSttlSysCopt().getOtptTmgtDcd();
      String rqstRspnDcd = ibkMessage.getStandardTelegram().getSttlSysCopt().getRqstRspnDcd();
      String syncRspnWaitDcd = ibkMessage.getStandardTelegram().getSttlSysCopt().getSyncRspnWaitDcd();

      LogManager.getLogger(LogType.ROOT).info("dummyCheck: " + dummyCheck);
      LogManager.getLogger(LogType.ROOT).info("rqstRspnDcd: " + rqstRspnDcd);

      if (!StringUtils.isEmpty(dummyCheck) && "0".equals(dummyCheck) && "R".equals(rqstRspnDcd)
          && "K".equals(syncRspnWaitDcd)) {
        LogManager.getLogger(LogType.ROOT).info("실제응답 시 dummy wait 해제");
        String bidKey = BidUtil.getBidKey(ibkMessage.getStandardTelegram());

        LogManager.getLogger(LogType.ROOT).info("MCAWorkAfterProcess bidKey: " + bidKey);

        // WAIT -> notify the waiting thread; PENDING (dummy ack not 
        // processed yet) -> park the result so the dummy ack completes without waiting.
        BidManager.ReleaseResult result = bidManager.bidResult(bidKey, exchange.copy());

        if (result == BidManager.ReleaseResult.NOT_FOUND) {
          throw new IBKExceptionMCA(ErrorType.BID, "bidInfo is null, bidKey: " + bidKey);
        }

        exchange.setProperty(Exchange.ROUTE_STOP, Boolean.TRUE);
      }
    }

    // 진행번호 추가
    if (ibkMessage.getStandardTelegram() != null
        && ibkMessage.getStandardTelegram().getSttlSysCopt().getWhbnSttlPgrsDsncNo() != null) {
      ibkMessage.getStandardTelegram().getSttlSysCopt()
          .setWhbnSttlPgrsNo(ibkMessage.getStandardTelegram().getSttlSysCopt().getWhbnSttlPgrsNo() + 1);
    }

    // 표준전문유지시간 -5초
    if (ibkMessage.getStandardTelegram() != null
        && "Y".equals(ibkMessage.getStandardTelegram().getSttlSysCopt().getSttlMctmUseYn())) {
      Interface intf = null;

      try {
        intf = resourceMCA.getInterface(ibkMessage.getInterfaceId());
      } catch (Exception e) {
        throw new IBKExceptionMCA(ErrorType.MESSAGE, "Message Search Error : " + ibkMessage.getInterfaceId());
      }

      String tgSysCd = intf.getInterfaceType().getTarget().getSystem().getCode();

      int timeoutDefault = "GCB".equals(tgSysCd) ? routeProperties.getGcbTimeoutDefault()
          : ("GED".equals(tgSysCd) ? routeProperties.getGedTimeoutDefault()
              : ibkMessage.getStandardTelegram().getSttlSysCopt().getSttlMctmSecVl());

      if ((ibkMessage.getStandardTelegram().getSttlSysCopt().getSttlMctmSecVl() - 5) < timeoutDefault) {
        ibkMessage.getStandardTelegram().getSttlSysCopt().setSttlMctmSecVl(timeoutDefault);
      } else {
        ibkMessage.getStandardTelegram().getSttlSysCopt()
            .setSttlMctmSecVl(ibkMessage.getStandardTelegram().getSttlSysCopt().getSttlMctmSecVl() - 5);
      }
    }

    // MCA 노드 IP, PORT 세팅 (FEP 당발 응답 대기를 위한 설정)
    if (ibkMessage.getStandardTelegram() != null
        && !"ITRO00000035".equals(ibkMessage.getStandardTelegram().getSttlSysCopt().getSttlIntfId())
        && StringUtil.isNullOrEmpty(ibkMessage.getStandardTelegram().getSttlSysCopt().getBidRspnRcvNdIp())) {
      ibkMessage.getStandardTelegram().getSttlSysCopt().setBidRspnRcvNdIp(SystemUtil.getIpAddr());
      ibkMessage.getStandardTelegram().getSttlSysCopt().setBidRspnRcvPortNo(routeProperties.getGcbInPort());
    }
  }
}
