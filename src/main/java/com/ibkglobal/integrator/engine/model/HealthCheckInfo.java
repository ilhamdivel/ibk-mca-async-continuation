package com.ibkglobal.integrator.engine.model;

import io.netty.channel.ChannelHandlerContext;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class HealthCheckInfo extends HealthCheckDefault {
  private String                userInfo;   // 접속자 정보(6)
  private String                brcd;       // 부점코드(4)
  private String                bncd;       // 은행코드 (2)
  private String                envrDcd;    // 환경구분코드(1)
  private String                ip;         // 접속자 IP(16)
  private HealthStatus          status;     // 접속상태
  private long                  healthTime; // CheckTime
  private ChannelHandlerContext context;    // Context

  public static enum HealthStatus {
    LOGIN, NORMAL, LOGOUT
  }
}
