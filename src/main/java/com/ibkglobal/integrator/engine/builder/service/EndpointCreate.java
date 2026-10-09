package com.ibkglobal.integrator.engine.builder.service;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.ReflectionUtils;
import org.springframework.util.StringUtils;

import com.ibk.ibkglobal.data.integrator.route.RouteCreateInfo;
import com.ibk.ibkglobal.data.integrator.route.endpoint.EndpointField;
import com.ibk.ibkglobal.data.integrator.route.endpoint.EndpointInfo;
import com.ibk.ibkglobal.data.integrator.route.type.EndpointType;
import com.ibk.ibkglobal.data.integrator.route.type.ProtocolType;
import com.ibkglobal.integrator.config.EndpointCode;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.log.LogType;

public class EndpointCreate {

  public static String createEndpoint(ProtocolType type, EndpointInfo endpointInfo, RouteCreateInfo routeCreateInfo) {
    String result = null;
    LogManager.getLogger(LogType.ROOT).info("createEndpoint endpointType: " + endpointInfo.getEndpointType());

    try {
      EndpointType endpointCd = endpointInfo.getEndpointType();

      switch (endpointCd) {
      case DIRECT:
        result = createDirect(endpointInfo);
        break;
      case HTTP:
        result = createHttp(type, endpointInfo);
        break;
      case TCP:
        result = createTcp(type, endpointInfo, routeCreateInfo);
        break;
      case BEAN:
        result = createBean(endpointInfo);
        break;
      case QUEUE:
        result = createQueue(type, endpointInfo);
        break;
      case FTP:
        result = createFtp(type, endpointInfo);
        break;
      case SFTP:
        result = createSftp(type, endpointInfo);
        break;
      case FILE:
        result = createFile(type, endpointInfo);
        break;
      case DYNAMIC:
        result = createDynamic(endpointInfo);
        break;
      default:
        break;
      }

    } catch (Exception e) {
      LogManager.getLogger(LogType.ROOT).info("createEndpoint Exception: " + e.getMessage());
      e.printStackTrace();
    }

    LogManager.getLogger(LogType.ROOT).info("createEndpoint return data: " + result);
    return result;
  }

  public static String createDirect(EndpointInfo endpointInfo) throws Exception {
    String base = EndpointCode.DIRECT + endpointInfo.getDirectEndpoint().getEndpointDirect();

    return base;
  }

  public static String createHttp(ProtocolType type, EndpointInfo endpointInfo) throws Exception {
    LogManager.getLogger(LogType.ROOT).info("createHttp");

    String base = EndpointCode.HTTP + endpointInfo.getHttpEndpoint().getEndpointIp()
        + (endpointInfo.getHttpEndpoint().getEndpointPort() != null
            ? ":" + endpointInfo.getHttpEndpoint().getEndpointPort()
            : "")
        + (endpointInfo.getHttpEndpoint().getPathNm() != null ? endpointInfo.getHttpEndpoint().getPathNm() : "");

    LogManager.getLogger(LogType.ROOT).info("createHttp base: " + base);
    LogManager.getLogger(LogType.ROOT).info("createHttp protocolType: " + type);

    switch (type) {
    case CONSUMER:
      base += "?serverInitializerFactory=#ibkHttpConsumerInitializer";
      break;
    case PRODUCER:
      base += "?clientInitializerFactory=#ibkHttpProducerInitializer";
      break;
    default:
      break;
    }

    LogManager.getLogger(LogType.ROOT)
        .info("createHttp createParameter: type: " + type + ", httpEndpoint: " + endpointInfo.getHttpEndpoint());
    String parameter = createParameter(type, endpointInfo.getHttpEndpoint());

    LogManager.getLogger(LogType.ROOT).info("createHttp return data: " + base + parameter);
    return base + parameter;
  }

  public static String createTcp(ProtocolType type, EndpointInfo endpointInfo, RouteCreateInfo routeCreateInfo)
      throws Exception {
    String base = EndpointCode.TCP + endpointInfo.getTcpEndpoint().getEndpointIp()
        + (endpointInfo.getTcpEndpoint().getEndpointPort() != null
            ? ":" + endpointInfo.getTcpEndpoint().getEndpointPort()
            : "");

    switch (type) {
    case CONSUMER:
      base += "?serverInitializerFactory=#ibkTcpConsumerInitializer";
      break;
    case PRODUCER:
      base += "?clientInitializerFactory=#ibkTcpProducerInitializer";
      break;
    default:
      break;
    }

    String parameter = createParameter(type, endpointInfo.getTcpEndpoint());

    parameter += createCode(routeCreateInfo);

    return base + parameter;
  }

  public static String createCode(RouteCreateInfo routeCreateInfo) {
    String parameter = "";

    parameter += routeCreateInfo.getSysCd().isEmpty() ? "" : "&option.sysCd=" + routeCreateInfo.getSysCd();
    parameter += routeCreateInfo.getOrgCd().isEmpty() ? "" : "&option.orgCd=" + routeCreateInfo.getOrgCd();
    parameter += routeCreateInfo.getBizCd().isEmpty() ? "" : "&option.bizCd=" + routeCreateInfo.getBizCd();

    return parameter;
  }

  public static String createBean(EndpointInfo endpointInfo) throws Exception {
    String base = EndpointCode.BEAN + endpointInfo.getBeanEndpoint().getBswrNm();

    String parameter = "?method=" + endpointInfo.getBeanEndpoint().getBswrMtdNm();

    return base + parameter;
  }

  public static String createQueue(ProtocolType type, EndpointInfo endpointInfo) throws Exception {
    String base = EndpointCode.QUEUE + endpointInfo.getQueueEndpoint().getEndpointQueue();

    String parameter = createNewParameter(type, endpointInfo.getQueueEndpoint());

    return base + parameter;
  }

  public static String createFtp(ProtocolType type, EndpointInfo endpointInfo) throws Exception {
    String base = EndpointCode.FTP + endpointInfo.getFtpEndpoint().getPath();

    String parameter = createNewParameter(type, endpointInfo.getFtpEndpoint());
    String etcParameter = endpointInfo.getFtpEndpoint().getEtcParameter();

    return base + parameter + (!StringUtils.isEmpty(etcParameter) ? ("&" + etcParameter) : "");
  }

  public static String createSftp(ProtocolType type, EndpointInfo endpointInfo) throws Exception {
    String base = EndpointCode.SFTP + endpointInfo.getSftpEndpoint().getPath();

    String parameter = createNewParameter(type, endpointInfo.getSftpEndpoint());
    String etcParameter = endpointInfo.getSftpEndpoint().getEtcParameter();

    return base + parameter + (!StringUtils.isEmpty(etcParameter) ? ("&" + etcParameter) : "");
  }

  public static String createFile(ProtocolType type, EndpointInfo endpointInfo) throws Exception {
    String base = EndpointCode.FILE + endpointInfo.getFileEndpoint().getPath();

    String parameter = createNewParameter(type, endpointInfo.getFileEndpoint());

    return base + parameter;
  }

  public static String createDynamic(EndpointInfo endpointInfo) throws Exception {
    return EndpointCode.DYNAMIC;
  }

  public static List<String> createLoadBalance(ProtocolType type, EndpointInfo endpointInfo,
      RouteCreateInfo routeCreateInfo) {
    List<String> result = new ArrayList<>();

    endpointInfo.getEndpointList().forEach(endpoint -> {
      result.add(createEndpoint(type, endpoint, routeCreateInfo));
    });

    return result;
  }

  public static String createNewParameter(ProtocolType type, Object endpoint) throws Exception {
    String parameter = "";

    Map<String, Object> parameterMap = getParameter(type, endpoint);

    for (Map.Entry<String, Object> entry : parameterMap.entrySet()) {
      if (!StringUtils.isEmpty(entry.getKey()) && !StringUtils.isEmpty(entry.getValue())
          && !"etcParameter".equals(entry.getKey())) {
        if (!parameter.contains("?")) {
          parameter += "?" + entry.getKey() + "=" + entry.getValue();
        } else {
          parameter += "&" + entry.getKey() + "=" + entry.getValue();
        }
      }
    }

    return parameter;
  }

  public static String createParameter(ProtocolType type, Object endpoint) throws Exception {
    LogManager.getLogger(LogType.ROOT).info("createParameter");

    String parameter = "";

    LogManager.getLogger(LogType.ROOT).info("createParameter getParameter: type: " + type + ", endpoint: " + endpoint);
    Map<String, Object> parameterMap = getParameter(type, endpoint);
    LogManager.getLogger(LogType.ROOT).info("createParameter parameterMap: " + parameterMap);

    for (Map.Entry<String, Object> entry : parameterMap.entrySet()) {
      if (!StringUtils.isEmpty(entry.getKey()) && !StringUtils.isEmpty(entry.getValue())) {
        parameter += "&" + entry.getKey() + "=" + entry.getValue();
      }
    }

    LogManager.getLogger(LogType.ROOT).info("createParamter return data: " + parameter);
    return parameter;
  }

  public static Map<String, Object> getParameter(ProtocolType type, Object endpoint) throws Exception {
    LinkedHashMap<String, Object> parameterMap = new LinkedHashMap<>();

    Field[] fields = endpoint.getClass().getDeclaredFields();

    for (Field field : fields) {
      ReflectionUtils.makeAccessible(field);
      // field.setAccessible(true);

      if (field.getAnnotation(EndpointField.class) != null) {
        EndpointField endpointField = field.getAnnotation(EndpointField.class);

        if (type == endpointField.fieldType() || endpointField.fieldType() == ProtocolType.COMMON) {
          parameterMap.put(endpointField.fieldName(), field.get(endpoint));
        }
      }
    }

    return parameterMap;
  }
}
