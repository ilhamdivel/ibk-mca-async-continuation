package com.ibkglobal.integrator.engine.builder.route;

import java.nio.charset.Charset;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Processor;
import org.apache.camel.builder.Builder;
import org.apache.camel.model.RouteDefinition;
import org.springframework.util.Base64Utils;
import org.springframework.util.StringUtils;

import com.ibk.ibkglobal.data.integrator.route.RouteCreateInfo;
import com.ibk.ibkglobal.data.integrator.route.endpoint.EndpointInfo;
import com.ibk.ibkglobal.data.integrator.route.type.EndpointType;
import com.ibk.ibkglobal.data.integrator.route.type.ProtocolType;
import com.ibkglobal.config.CommonConstantCode;
import com.ibkglobal.integrator.config.ConstantCode;
import com.ibkglobal.integrator.engine.bean.mca.log.LoggingMCA;
import com.ibkglobal.integrator.engine.builder.service.EndpointCreate;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.log.LogType;

import io.netty.handler.timeout.ReadTimeoutException;
import lombok.Getter;
import lombok.Setter;

public abstract class RouteCreateDefault extends RouteDefinition implements RouteCreate {

  @Getter
  @Setter
  private RouteCreateInfo builderInfo;

  @Getter
  private static String excludePatterns[] = { "connection", "content-length", "Content-Type", "LOCAL_ENDPOINT",
      "Authorization", "SESSION_ID" };

  @Override
  public RouteCreateInfo getRouteCreateInfo() {
    return builderInfo;
  }

  @Override
  public void setRouteCreateInfo(RouteCreateInfo routeCreateInfo) {
    setBuilderInfo(routeCreateInfo);
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T get() {
    return (T) this;
  }

  /**
   * On MCA Exception
   */
  protected void onMCAException() {

    onException(Exception.class).handled(true)
        .bean(com.ibkglobal.integrator.engine.bean.mca.error.ErrorCatchMCA.class, "catchError")

        .choice().when(p -> getBuilderInfo().getParsingType() != null)
        .setHeader(ConstantCode.PARSING_TYPE, Builder.constant(getBuilderInfo().getParsingType())).end()

        .bean(com.ibkglobal.integrator.engine.bean.mca.common.ComposingMCA.class, "errorComposing")
        .setHeader(ConstantCode.TRADE_TYPE, Builder.constant("E")).process(new Processor() {
          @Override
          public void process(Exchange exchange) throws Exception {
            //LoggingMCA.loggingError(exchange);
        	  new LoggingMCA().loggingError(exchange);
          }
        }).removeHeaders("*", excludePatterns).log(LoggingLevel.ERROR, "${exception.stacktrace}").end();

    onException(ReadTimeoutException.class).handled(true)
        .bean(com.ibkglobal.integrator.engine.bean.mca.error.ErrorCatchMCA.class, "catchReadTimeoutException")

        .choice().when(p -> getBuilderInfo().getParsingType() != null)
        .setHeader(ConstantCode.PARSING_TYPE, Builder.constant(getBuilderInfo().getParsingType())).end()

        .bean(com.ibkglobal.integrator.engine.bean.mca.common.ComposingMCA.class, "errorComposing")
        .setHeader(ConstantCode.TRADE_TYPE, Builder.constant("E")).process(new Processor() {
          @Override
          public void process(Exchange exchange) throws Exception {
            //LoggingMCA.loggingError(exchange);
        	  new LoggingMCA().loggingError(exchange);
          }
        }).removeHeaders("*", excludePatterns).log(LoggingLevel.ERROR, "${exception.stacktrace}").end();
  }

  /**
   * 헤더 기본 Set
   * 
   * @param seq
   */
  protected void setDefaultHeader(String seq, String tradeType) {

    this.setHeader(ConstantCode.SEQ, Builder.constant(seq));
    this.setHeader(ConstantCode.TRADE_TYPE, Builder.constant(tradeType));
    this.setHeader(CommonConstantCode.LOGGER_KEY, Builder.constant(builderInfo.getLogName()));
    this.setHeader(ConstantCode.ORG_CODE, Builder.constant(builderInfo.getOrgCd()));
    this.setHeader(ConstantCode.BIZ_CODE, Builder.constant(builderInfo.getBizCd()));
    this.setHeader(ConstantCode.SYS_CODE, Builder.constant(builderInfo.getSysCd()));
  }

  /**
   * Endpoint 생성
   * 
   * @param endpointType
   */
  protected String createStringEndpoint(String endpointType) {
    String result = null;

    EndpointInfo endpointInfo = null;

    if (endpointType.equals("from")) {
      endpointInfo = builderInfo.getFromEndpoint();

      result = EndpointCreate.createEndpoint(ProtocolType.CONSUMER, endpointInfo, builderInfo);
    } else if (endpointType.equals("to")) {
      endpointInfo = builderInfo.getToEndpoint();
      LogManager.getLogger(LogType.ROOT).info("createEndpoint toEndpointType: " + endpointInfo.getEndpointType());

      switch (endpointInfo.getEndpointType()) {
      case HTTP:
        // Basic Auth 기능 추가
        if (!StringUtils.isEmpty(endpointInfo.getHttpEndpoint().getAuthNm())
            && !StringUtils.isEmpty(endpointInfo.getHttpEndpoint().getAuthPw())) {
          this.setHeader("Authorization", Builder.constant(
              createAuthKey(endpointInfo.getHttpEndpoint().getAuthNm(), endpointInfo.getHttpEndpoint().getAuthPw())));
        }
        
        LogManager.getLogger(LogType.ROOT).info("createEndpoint HTTP toEndpointCreate start");
        result = EndpointCreate.createEndpoint(ProtocolType.PRODUCER, endpointInfo, builderInfo);
        LogManager.getLogger(LogType.ROOT).info("createEndpoint HTTP toEndpointCreate end");
        break;
      default:
        result = EndpointCreate.createEndpoint(ProtocolType.PRODUCER, endpointInfo, builderInfo);
        break;
      }
    }

    return result;
  }

  protected void createEndpoint(String endpointType) {
    EndpointInfo endpointInfo = null;

    if (endpointType.equals("from")) {
      endpointInfo = builderInfo.getFromEndpoint();

      switch (endpointInfo.getEndpointType()) {
      case BEAN:
        try {
          this.bean(Class.forName(endpointInfo.getBeanEndpoint().getBswrNm()),
              endpointInfo.getBeanEndpoint().getBswrMtdNm());
        } catch (ClassNotFoundException e) {
          e.printStackTrace();
        }
        break;
      case LOADBALANCE:
        this.from(EndpointCreate.createLoadBalance(ProtocolType.CONSUMER, endpointInfo, builderInfo).stream()
            .toArray(String[]::new));
        break;
      default:
        this.from(EndpointCreate.createEndpoint(ProtocolType.CONSUMER, endpointInfo, builderInfo));
        break;
      }
    } else if (endpointType.equals("to")) {
      endpointInfo = builderInfo.getToEndpoint();

      switch (endpointInfo.getEndpointType()) {
      case BEAN:
        try {
          this.bean(Class.forName(endpointInfo.getBeanEndpoint().getBswrNm()),
              endpointInfo.getBeanEndpoint().getBswrMtdNm());
        } catch (ClassNotFoundException e) {
          e.printStackTrace();
        }
        break;
      case HTTP:
        // Basic Auth 기능 추가
        if (!StringUtils.isEmpty(endpointInfo.getHttpEndpoint().getAuthNm())
            && !StringUtils.isEmpty(endpointInfo.getHttpEndpoint().getAuthPw())) {
          this.setHeader("Authorization", Builder.constant(
              createAuthKey(endpointInfo.getHttpEndpoint().getAuthNm(), endpointInfo.getHttpEndpoint().getAuthPw())));
        }

        this.to(EndpointCreate.createEndpoint(ProtocolType.PRODUCER, endpointInfo, builderInfo));
        break;
      case LOADBALANCE:
        this.to(EndpointCreate.createLoadBalance(ProtocolType.PRODUCER, endpointInfo, builderInfo).stream()
            .toArray(String[]::new));
        break;
      case DYNAMIC:
        this.toD(EndpointCreate.createEndpoint(ProtocolType.PRODUCER, endpointInfo, builderInfo));
        break;
      default:
        this.to(EndpointCreate.createEndpoint(ProtocolType.PRODUCER, endpointInfo, builderInfo));
        break;
      }
    }
  }

  protected EndpointType getEndpointType(EndpointInfo endpointInfo) {

    EndpointType endpointType = null;

    switch (endpointInfo.getEndpointType()) {
    case LOADBALANCE:
      if (endpointInfo.getEndpointList() != null && endpointInfo.getEndpointList().size() > 0) {
        endpointType = endpointInfo.getEndpointList().get(0).getEndpointType();
      }
      break;
    default:
      endpointType = endpointInfo.getEndpointType();
      break;
    }

    return endpointType;
  }

  /**
   * Basic Auth Key 생성
   * 
   * @param id
   * @param password
   * @return
   */
  public String createAuthKey(String id, String password) {
    String autorizationKey = "Basic "
        + Base64Utils.encodeToString((id + ":" + password).getBytes(Charset.forName("UTF-8")));

    return autorizationKey;
  }
}
