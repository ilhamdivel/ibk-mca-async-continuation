package com.ibkglobal.integrator.engine.bean.mca.work;

import org.apache.camel.Exchange;
import org.apache.camel.Message;

import com.ibkglobal.integrator.engine.manager.HealthCheckManager;
import com.ibkglobal.integrator.engine.model.HealthCheckInfo;
import com.ibkglobal.integrator.exception.CommonException;
import com.ibkglobal.integrator.exception.ErrorType;
import com.ibkglobal.integrator.exception.IBKExceptionMCA;
import com.ibkglobal.integrator.util.HealthCheckUtil;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.log.LogType;

import io.netty.channel.ChannelHandlerContext;

public class HealthCheck {

  public void execute(Exchange exchange) throws CommonException {
    try {
      LogManager.getLogger(LogType.ROOT).info("HealthCheck execute");
      Message message = exchange.getIn();

      parsing(message);

      composing(message);
      LogManager.getLogger(LogType.ROOT).info("HealthCheck execute End");
    } catch (Exception e) {
      LogManager.getLogger(LogType.ROOT).info("HealthCheck Exception: " + e.getMessage());
      throw new IBKExceptionMCA(ErrorType.HEALTH_CHECK, e.getMessage(), e);
    }
  }

  public void parsing(Message message) throws CommonException {
    if (message.getBody() instanceof byte[]) {
      LogManager.getLogger(LogType.ROOT).info("HealthCheck parsing message : " + message.getBody(String.class));
      
      HealthCheckInfo healthCheckInfo = HealthCheckUtil.createInfo(message);

      // String ip = healthCheckInfo.getIp();

      String key = healthCheckInfo.getBncd() + "." + healthCheckInfo.getUserInfo() + "." + healthCheckInfo.getIp();
      LogManager.getLogger(LogType.ROOT).info("HealthCheck parsing key: " + key);
      
      healthCheckInfo.setContext((ChannelHandlerContext) message.getHeader(HealthCheckUtil.CONTEXTHEADER));
      
      LogManager.getLogger(LogType.ROOT).info("HealthCheck parsing put the clientList key: " + key + ", healthCheckInfo: " + healthCheckInfo);
      HealthCheckManager.getClientList().put(key, healthCheckInfo);
      
      LogManager.getLogger(LogType.ROOT).info("HealthCheck parsing set Header: CHECKRESULT: true");
      message.setHeader(HealthCheckUtil.CHECKRESULT, true);
    } else {
      LogManager.getLogger(LogType.ROOT).info("HealthCheck parsing set Header: CHECKRESULT: false");
      message.setHeader(HealthCheckUtil.CHECKRESULT, false);
    }
  }

  public void composing(Message message) {
    if (message.getHeader(HealthCheckUtil.CHECKRESULT) == null) {
      LogManager.getLogger(LogType.ROOT).info("HealthCheck composing get Header is null: CHECKRESULT");
      
      message.setBody(HealthCheckUtil.nakMessage());
      return;
    }

    boolean result = (boolean) message.getHeader(HealthCheckUtil.CHECKRESULT);
    LogManager.getLogger(LogType.ROOT).info("HealthCheck composing get Header: " + result);
    
    if (result) {
      message.setBody(HealthCheckUtil.ackMessage());
    } else {
      message.setBody(HealthCheckUtil.nakMessage());
    }
  }
}
