package com.ibkglobal.integrator.engine.bean.mca.work;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.netty4.NettyConfiguration;
import org.apache.camel.component.netty4.NettyEndpoint;
import org.apache.camel.support.SynchronizationAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;

import com.ibk.ibkglobal.data.intf.Interface;
import com.ibkglobal.integrator.config.CamelConfig;
import com.ibkglobal.integrator.config.ConstantCode;
import com.ibkglobal.integrator.config.EndpointCode;
import com.ibkglobal.integrator.engine.bean.common.TranLogApi;
import com.ibkglobal.integrator.engine.manager.BidManager;
import com.ibkglobal.integrator.engine.model.BidInfo;
import com.ibkglobal.integrator.exception.ErrorType;
import com.ibkglobal.integrator.exception.IBKExceptionMCA;
import com.ibkglobal.integrator.util.BidUtil;
import com.ibkglobal.integrator.util.IBKMessageUtil;
import com.ibkglobal.integrator.util.RuleCommonUtil;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.log.LogType;
import com.ibkglobal.message.IBKMessage;
import com.ibkglobal.message.common.normal.StandardTelegram;
import com.ibkglobal.message.converter.ConverterByte;
import com.ibkglobal.message.converter.service.ConverterService;
import com.ibkglobal.message.struct.resource.ResourceMCA;

public class MCAWorkPreProcess {

  @Autowired
  CamelConfig camelConfig;

  @Autowired
  ResourceMCA resourceMCA;

  @Autowired
  ConverterService converterService;

  @Autowired
  TranLogApi tranLogApi;

  @Autowired
  BidManager bidManager;

  public void execute(Exchange exchange) throws Exception {
    // 비드 거래 업무일 경우
    if (!bidHttp(exchange)) {
      // 로컬 거래 업무일 경우
      if (!localWork(exchange)) {
        // 일반업무
        nomalWork(exchange);
      }

      bidPreRegister(exchange);
    }
  }

  /**
   * Register the BID key BEFORE the request goes to the host.
   */
  private void bidPreRegister(Exchange exchange) {
    IBKMessage ibkMessage = exchange.getIn().getBody(IBKMessage.class);
    StandardTelegram standardTelegram = ibkMessage.getStandardTelegram();

    if (!BidUtil.hasBidKey(standardTelegram)) {
      return;
    }

    String key = BidUtil.getBidKey(standardTelegram);
    BidInfo bidInfo = bidManager.bidPreRegister(key);

    if (bidInfo == null) {
      return;
    }

    exchange.setProperty("MCA_BID_OWNER", bidInfo);
    exchange.addOnCompletion(new SynchronizationAdapter() {
      @Override
      public void onDone(Exchange done) {
        bidManager.bidUnregister(key, bidInfo);
      }
    });
  }

  public void nomalWork(Exchange exchange) throws Exception {
    Message message = exchange.getIn();
    IBKMessage ibkMessage = message.getBody(IBKMessage.class);

    Interface intf = resourceMCA.getInterface(ibkMessage.getInterfaceId());

    String sysCode = intf.getInterfaceType().getTarget().getSystem().getCode();
    String bizCode = intf.getInterfaceType().getTarget().getBswr().getCode();

    String bncd = ibkMessage.getStandardTelegram().getSttlTrnCopt().getBncd();

    // 계정계 정보계는 COM으로 고정
    if (sysCode.equals("GCB") || sysCode.equals("GED")) {
      bizCode = "COM";
    }

    // 계정계, 인뱅
    // 01: 미국, 02: 일본, 03: 중국, 04: 영국, 05: 홍콩, 06: 베트남, 07: 인도, 08: 필리핀, 09: 캄보디아,
    // 10: 인니
    if ((sysCode.equals("GCB") || sysCode.equals("GIB"))
        && (bncd.equals("01") || bncd.equals("02") || bncd.equals("03") || bncd.equals("04") || bncd.equals("05")
            || bncd.equals("06") || bncd.equals("07") || bncd.equals("08") || bncd.equals("09") || bncd.equals("10"))) {
      bizCode = bncd;
    }

    // 정보계
    // 10: 인니
    if (sysCode.equals("GED") && (bncd.equals("10") || bncd.equals("03"))) {
      bizCode = bncd;
    }

    // ex) M.GCB0.COM0.ADAPTER.OUT
    String endPoint = EndpointCode.DIRECT + "M." + RuleCommonUtil.checkFixName(sysCode) + "."
        + RuleCommonUtil.checkFixName(bizCode) + "." + "ADAPTER." + "OUT";

    if (StringUtils.isEmpty(endPoint)) {
      // tranManager End API
      int nSeq = Integer.parseInt(message.getHeader(ConstantCode.SEQ, String.class));

      if (nSeq == 2) {
        try {
          tranLogApi.TranLog(message.getBody(IBKMessage.class), "End", "MCA_CHN");
        } catch (Exception ex) {
          throw new IBKExceptionMCA(ErrorType.ROUTE, "TranApi Error: " + ibkMessage.getInterfaceId(), ex);
        }
      }

      throw new IBKExceptionMCA(ErrorType.ROUTE, "Routing Error : " + endPoint);
    }

    message.setHeader(EndpointCode.DYNAMIC_ENDPOINT, endPoint);
  }

  /**
   * Bid Http
   * 
   * @param exchange
   * @return
   * @throws Exception
   */
  public boolean bidHttp(Exchange exchange) throws Exception {

    Message message = exchange.getIn();
    IBKMessage ibkMessage = message.getBody(IBKMessage.class);

    ProducerTemplate producer = null;

    // ITRO00000035 : BID Message I/F ID
    if ("ITRO00000035".equals(ibkMessage.getStandardTelegram().getSttlSysCopt().getSttlIntfId())) {
      // 비드응답수신노드IP
      String bidRspnRcvNdIp = ibkMessage.getStandardTelegram().getSttlSysCopt().getBidRspnRcvNdIp();
      // 비드응답수신포트번호
      Integer bidRspnRcvPortNo = ibkMessage.getStandardTelegram().getSttlSysCopt().getBidRspnRcvPortNo();

      String sendMessage = converterService.objectToJson(ibkMessage.getStandardTelegram());
      sendMessage = ConverterByte.fieldStringFormat("INTEGER", sendMessage.getBytes().length, 8, 0) + sendMessage;

      NettyEndpoint nettyEndpoint = camelConfig.getCamelContext().getEndpoint(
          "netty4:tcp://" + bidRspnRcvNdIp + ":" + bidRspnRcvPortNo + "?textline=true", NettyEndpoint.class);
      NettyConfiguration nettyConfiguration = nettyEndpoint.getConfiguration();
      nettyConfiguration.setDisconnect(true);
      nettyConfiguration.setSync(false);
      nettyConfiguration.setRequestTimeout(10000);

      // Try
      try {
        producer = camelConfig.getCamelContext().createProducerTemplate();
        producer.sendBody(nettyEndpoint, sendMessage);
      }
      // Producer Execution Exception
      catch (CamelExecutionException camelEx) {
        throw new IBKExceptionMCA(ErrorType.BID, "Producer Template Send Defined Exception: " + camelEx.getMessage(),
            camelEx);
      }
      // Producer Other Exception
      catch (Exception ex) {
        throw new IBKExceptionMCA(ErrorType.BID, "Producer Template Other Exception: " + ex.getMessage(), ex);
      }
      // Finally Producer CleanUp & Stop
      finally {
        try {
          producer.cleanUp();
          producer.stop();
          LogManager.getLogger(LogType.ROOT).info("MCAWorkPreProcess(BID) Producer CleanUp and Stop Completed");
        } catch (Exception ex) {
          LogManager.getLogger(LogType.ROOT)
              .info("MCAWorkPreProcess(BID) Producer CleanUp and Stop Failed. Exception: " + ex.getMessage());
        }
      }

      IBKMessageUtil.replyMessageBidDefaultSet(ibkMessage.getStandardTelegram(), ConstantCode.MCA_CODE,
          ConstantCode.IBK_RES_FLAG_VALUE, "0");

      exchange.getOut().setFault(true);
      exchange.getOut().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
      exchange.getOut().setHeader(Exchange.CONTENT_TYPE, MediaType.APPLICATION_JSON);

      exchange.getOut().setBody(converterService.objectToJson(ibkMessage.getStandardTelegram()));

      new TranLogApi().TranLog(ibkMessage, "End", "MCA_CHN");

      return true;
    }
    return false;
  }

  public boolean localWork(Exchange exchange) throws Exception {

    boolean chk = false;

    Message message = exchange.getIn();
    IBKMessage ibkMessage = message.getBody(IBKMessage.class);

    if ("L".equals(ibkMessage.getStandardTelegram().getSttlSysCopt().getSysEnvrInfoDcd())) {
      message.setHeader(EndpointCode.DYNAMIC_ENDPOINT, EndpointCode.DIRECT + "M.LOCAL.LOCAL.ADAPTER.OUT");
      String destinationIp = ibkMessage.getStandardTelegram().getSttlSysCopt().getSttlIp();
      String bncd = ibkMessage.getStandardTelegram().getSttlTrnCopt().getBncd();

      if (bncd.equals("10")) {
        message.setHeader(EndpointCode.LOCAL_ENDPOINT, EndpointCode.HTTP + destinationIp
            + ":40610/service/sync?httpMethodRestrict=POST&disconnect=true&requestTimeout=300000");
      } else if (bncd.equals("03")) {
        message.setHeader(EndpointCode.LOCAL_ENDPOINT, EndpointCode.HTTP + destinationIp
            + ":40603/service/sync?httpMethodRestrict=POST&disconnect=true&requestTimeout=300000");
      } else {
        message.setHeader(EndpointCode.LOCAL_ENDPOINT, EndpointCode.HTTP + destinationIp
            + ":40600/service/sync?httpMethodRestrict=POST&disconnect=true&requestTimeout=300000");
      }
      chk = true;
    }

    return chk;
  }
}
