package com.ibkglobal.integrator.engine.builder.route.timer;

import java.util.LinkedHashMap;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.ibk.ibkglobal.data.integrator.route.RouteCreateInfo;
import com.ibk.ibkglobal.data.integrator.route.type.InstanceRouteType;
import com.ibkglobal.integrator.config.CamelConfig;
import com.ibkglobal.integrator.engine.manager.HealthCheckManager;
import com.ibkglobal.integrator.engine.model.HealthCheckInfo;
import com.ibkglobal.integrator.exception.ErrorType;
import com.ibkglobal.integrator.exception.IBKExceptionMCA;
import com.ibkglobal.integrator.manager.instance.InstanceAdmin;
import com.ibkglobal.integrator.util.HealthCheckUtil;
import com.ibkglobal.integrator.util.RuleCommonUtil;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.log.LogType;
import com.ibkglobal.message.common.normal.StandardTelegram;
import com.ibkglobal.message.converter.service.ConverterService;

@Component
public class MCAHealthCheckTimer extends RouteBuilder {

  @Autowired
  CamelConfig camelConfig;

  @Autowired
  ConverterService converterService;

  @Autowired
  InstanceAdmin instanceAdmin;

  long checkTime = 30000; // 상태정보 1분주기로 검사

  @Override
  public void configure() {

    from("timer://healthCheck?period=" + checkTime).routeId("MCAHealthCheckTimer").process(new Processor() {

      ProducerTemplate producer = null;

      @Override
      public void process(Exchange exchange) throws IBKExceptionMCA {
        long currentTime = HealthCheckUtil.currentTime();

        LinkedHashMap<String, HealthCheckInfo> disconnectList = new LinkedHashMap<>();

        // HealthCheck
        HealthCheckManager.getClientList().forEach((k, v) -> {
          // 5분 주기
          if (currentTime - v.getHealthTime() > (65)) {
            // v.getContext().close();
            LogManager.getLogger(LogType.ROOT).info("MCAHealthCheckTimer put the disconnectList currentTime: "
                + currentTime + ", healthTime: " + v.getHealthTime() + ", k: " + k + ", v: " + v);

            disconnectList.put(k, v);
          }
        });

        LogManager.getLogger(LogType.ROOT).info("MCAHealthCheckTimer disconnectList size: " + disconnectList.size());

        // Logout And DisConnect
        disconnectList.forEach((k, v) -> {
          if (!ObjectUtils.isEmpty(v.getIp())) {
            Exchange result = exchange.copy();

            StandardTelegram standardTelegram = HealthCheckUtil.logoutMessage(v);
            String urlAdrress = "";
            try {
              LogManager.getLogger(LogType.ROOT).info("MCAHealthCheckTimer logout disconnect info: " + v);
              result.getIn().setBody(converterService.objectToJson(standardTelegram));
              urlAdrress = getIpAdress(v);
            } catch (Exception e) {
              e.printStackTrace();
            }

            try {
              LogManager.getLogger(LogType.ROOT).info("MCAHealthCheckTimer urlAddress: " + urlAdrress);
              producer = camelConfig.getCamelContext().createProducerTemplate();
              producer.asyncSend("netty4-http:http://" + urlAdrress + "?httpMethodRestrict=POST", result);
            }
            // Producer Execution Exception
            catch (CamelExecutionException camelEx) {
              System.out.println("MCAHealthCheckTime Producer AsyncSend Defined Error");
              StackTraceElement[] stacks = camelEx.getStackTrace();
              for (StackTraceElement stackTraceElement : stacks) {
                System.out.println(stackTraceElement.toString());
              }
            }
            // Producer Failure
            catch (Exception ex) {
              System.out.println("MCAHealthCheckTime Producer AsyncSend Undefined Error");
              StackTraceElement[] stacks = ex.getStackTrace();
              for (StackTraceElement stackTraceElement : stacks) {
                System.out.println(stackTraceElement.toString());
              }
            }
            // Finally
            finally {
              // Producer CleanUp & Stop
              try {
                producer.cleanUp();
                producer.stop();
                System.out.println("MCAHealthCheckTimer Producer AsyncSend CleanUp & Stop Completed");
              } catch (Exception ex) {
                System.out.println(
                    "MCAHealthCheckTimer Producer AsyncSend CleanUp & Stop Failed. Exception : " + ex.getMessage());
              }
            }

            HealthCheckManager.getClientList().remove(k);

          }
        });
      }
    }).end();
  }

  public String getIpAdress(HealthCheckInfo healthCheckInfo) throws IBKExceptionMCA {
    String urlAdr = "";
    try {
      if ("L".equals(healthCheckInfo.getEnvrDcd())) {
        String bncd = healthCheckInfo.getBncd();
        if (bncd.equals("10")) {
          urlAdr = healthCheckInfo.getIp() + ":40610";
        } else if (bncd.equals("03")) {
          urlAdr = healthCheckInfo.getIp() + ":40603";
        } else {
          urlAdr = healthCheckInfo.getIp() + ":40600";
        }
      } else {
        urlAdr = getReportAdapterDomain(healthCheckInfo);
      }
    } catch (Exception e) {
      throw new IBKExceptionMCA(ErrorType.HEALTH_CHECK, "Health Check error: " + e.getMessage(), e);
    }

    LogManager.getLogger(LogType.ROOT).info("MCAHealthCheckTimer getIpAddress: " + urlAdr);
    return urlAdr;
  }

  public String getReportAdapterDomain(HealthCheckInfo healthCheckInfo) throws IBKExceptionMCA {
    String toAdr = "";
    RouteCreateInfo info = null;
    try {

      String routeId = "M.GCB0." + RuleCommonUtil.checkFixName(healthCheckInfo.getBncd()) + ".HC406"
          + healthCheckInfo.getBncd();
      info = instanceAdmin.getAllRouteInfo(InstanceRouteType.ADAPTER).stream()
          .filter(f -> f.getRouteId().equals(routeId)).map(m -> m.getRouteCreate().getRouteCreateInfo()).findFirst()
          .get();

    } catch (Exception e) {
      throw new IBKExceptionMCA(ErrorType.HEALTH_CHECK, "Health Check error: " + e.getMessage(), e);
    }
    toAdr = info.getToEndpoint().getHttpEndpoint().getEndpointIp() + ":"
        + info.getToEndpoint().getHttpEndpoint().getEndpointPort() + info.getToEndpoint().getHttpEndpoint().getPathNm();

    LogManager.getLogger(LogType.ROOT)
        .info("toAdr: " + toAdr + ", IP: " + healthCheckInfo.getIp() + ", user: " + healthCheckInfo.getUserInfo());

    return toAdr;

  }

}
