package com.ibkglobal.integrator.engine.bean.mca.common;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.springframework.beans.factory.annotation.Autowired;

import com.ibkglobal.common.validator.ValidatorService;
import com.ibkglobal.integrator.config.ConstantCode;
import com.ibkglobal.integrator.engine.bean.mca.log.LoggingMCA;
import com.ibkglobal.integrator.exception.IBKExceptionMCA;
import com.ibkglobal.message.IBKMessage;
import com.ibkglobal.message.InfraType;

public class ProcessAfterMCA extends ProcessDefaultMCA {

  @Autowired
  ValidatorService validatorService;

  public void afterProcess(Exchange exchange) throws IBKExceptionMCA {
    // 헤더 초기화
    initSetHeader(exchange);

    // 파싱 및 초기화
    init(exchange);

    // AfterProcess Logging
    // LoggingMCA.logging(exchange);
    new LoggingMCA().logging(exchange);
  }

  public void afterProcessBid(Exchange exchange) throws IBKExceptionMCA {
    // Bid 헤더 초기화
    initSetBidHeader(exchange);

    // 파싱 및 초기화
    init(exchange);

    // AfterProcess Logging
    // LoggingMCA.logging(exchange);
    new LoggingMCA().logging(exchange);
    ;
  }

  protected void initSetHeader(Exchange exchange) throws IBKExceptionMCA {
    Message message = exchange.getIn();

    message.setHeader(ConstantCode.SEQ, "5");
    message.setHeader(ConstantCode.IBK_NORMAL_MESSAGE_YN, "Y");
    message.setHeader(ConstantCode.INFRA_TYPE, InfraType.MCA);
    message.setHeader(ConstantCode.IN_OUT, "OUT");
  }

  protected void initSetBidHeader(Exchange exchange) throws IBKExceptionMCA {
    Message message = exchange.getIn();

    message.setHeader(ConstantCode.SEQ, "3");
    message.setHeader(ConstantCode.TRADE_TYPE, "B");
    message.setHeader(ConstantCode.IBK_NORMAL_MESSAGE_YN, "Y");
    message.setHeader(ConstantCode.INFRA_TYPE, InfraType.MCA);
    message.setHeader(ConstantCode.IN_OUT, "OUT");
  }

  protected void init(Exchange exchange) throws IBKExceptionMCA {
    Message message = exchange.getIn();

    IBKMessage ibkMessage = message.getBody(IBKMessage.class);

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
      bodyParsing("OUT", ibkMessage);

      // Log 마스킹 위한 Io Set
      // 오류 발생
      // exchange.getIn().setHeader(ConstantCode.IO_INFO, getIo("OUT",
      // ibkMessage.getInterfaceId()));
    }

    // 진행번호 추가
    if (ibkMessage.getStandardTelegram() != null
        && ibkMessage.getStandardTelegram().getSttlSysCopt().getWhbnSttlPgrsDsncNo() != null) {
      ibkMessage.getStandardTelegram().getSttlSysCopt()
          .setWhbnSttlPgrsNo(ibkMessage.getStandardTelegram().getSttlSysCopt().getWhbnSttlPgrsNo() + 1);
    }
  }
}
