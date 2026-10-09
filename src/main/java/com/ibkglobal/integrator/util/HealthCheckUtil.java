package com.ibkglobal.integrator.util;

import java.nio.ByteBuffer;
import java.util.Date;
import java.util.LinkedHashMap;

import org.apache.camel.Message;

import com.ibkglobal.common.validator.sttl.SttlFieldUtil;
import com.ibkglobal.integrator.engine.model.HealthCheckInfo;
import com.ibkglobal.integrator.engine.model.HealthCheckInfo.HealthStatus;
import com.ibkglobal.integrator.exception.CommonException;
import com.ibkglobal.message.common.normal.StandardTelegram;

public class HealthCheckUtil {

  private static int HEADERLENGTH   = 8;
  private static int USERINFOLENGTH = 6;
  private static int BRCDLENGTH     = 4;
  private static int BANKCDLENGTH   = 2;
  private static int IPLENGTH       = 16;
  private static int SYSENVRINFODCD = 1;

  public static String CONTEXTHEADER = "CamelNettyChannelHandlerContext";
  public static String CHECKRESULT   = "CHECKRESULT";

  /**
   * 정보 접속 시
   * 
   * @param data
   * @return
   */
  public static HealthCheckInfo createInfo(Message message) throws CommonException {
    byte[] data = message.getBody(byte[].class);

    HealthCheckInfo healthCheckInfo = new HealthCheckInfo();

    ByteBuffer buffer = ByteBuffer.wrap(data);

    byte[] header = new byte[HEADERLENGTH];
    byte[] userinfo = new byte[USERINFOLENGTH];
    byte[] brcd = new byte[BRCDLENGTH];
    byte[] ip = new byte[IPLENGTH];
    byte[] bank = new byte[BANKCDLENGTH];
    byte[] envrDcd = new byte[SYSENVRINFODCD];

    buffer.get(header);
    buffer.get(userinfo);
    buffer.get(brcd);
    buffer.get(ip);
    buffer.get(bank);
    buffer.get(envrDcd);
    int dataLength = Integer.parseInt((new String(header)));

    if (dataLength != USERINFOLENGTH + BRCDLENGTH + BANKCDLENGTH + IPLENGTH + SYSENVRINFODCD) {
      return null;
    }
    healthCheckInfo.setLength(Integer.parseInt((new String(header))));
    healthCheckInfo.setUserInfo(new String(userinfo).trim());
    healthCheckInfo.setBrcd(new String(brcd).trim());
    healthCheckInfo.setIp(new String(ip).trim());
    healthCheckInfo.setBncd(new String(bank).trim());
    healthCheckInfo.setEnvrDcd(new String(envrDcd).trim());
    healthCheckInfo.setStatus(HealthStatus.NORMAL);

    healthCheckInfo.setHealthTime(currentTime());

    return healthCheckInfo;
  }

  public static long currentTime() {
    long currentTime = new Date().getTime() / 1000;

    return currentTime;
  }

  public static byte[] ackMessage() {
    // byte[] data = {0, 0, 0, 0, 0, 0, 0, 1, 0};
    String data = "000000010";

    return data.getBytes();
  }

  public static byte[] nakMessage() {
    // byte[] data = {0, 0, 0, 0, 0, 0, 0, 1, 1};
    String data = "000000011";

    return data.getBytes();
  }

  public static StandardTelegram logoutMessage(HealthCheckInfo healthCheckInfo) {
    StandardTelegram standardTelegram = SttlFieldUtil.defaultInit();

    standardTelegram.getSttlSysCopt().setSttlLen(123456);
    standardTelegram.getSttlSysCopt().setSttlCmrsYn("N");
    standardTelegram.getSttlSysCopt().setSttlEncpDcd("0");
    standardTelegram.getSttlSysCopt().setSttlVerDsnc("001");
    standardTelegram.getSttlSysCopt().setTrnmSysDcd("GMC");
    standardTelegram.getSttlSysCopt().setRqstSysDcd("GMC");
    standardTelegram.getSttlSysCopt().setRqstChptDcd("GMC");
    standardTelegram.getSttlSysCopt().setRqstChptDtlsDcd("GMC");
    standardTelegram.getSttlSysCopt().setRqstSysBswrDcd("COM");
    standardTelegram.getSttlSysCopt().setSysEnvrInfoDcd(healthCheckInfo.getEnvrDcd());

    standardTelegram.getSttlSysCopt().setWhbnSttlWrtnYmd(SystemUtil.getDate());
    standardTelegram.getSttlSysCopt().setWhbnSttlCretSysNm(healthCheckInfo.getEnvrDcd() + "0745038");
    standardTelegram.getSttlSysCopt().setWhbnSttlSrn(SystemUtil.getCurrentDateLong("yyyymmddHHmmssSSS") + "00001");
    standardTelegram.getSttlSysCopt().setWhbnSttlPgrsDsncNo(0);
    standardTelegram.getSttlSysCopt().setWhbnSttlPgrsNo(1);
    standardTelegram.getSttlSysCopt().setSttlIp(healthCheckInfo.getIp());
    standardTelegram.getSttlSysCopt().setRqstSysDcd("S");
    standardTelegram.getSttlSysCopt().setSttlIntfId("GITO00000299");
    standardTelegram.getSttlSysCopt().setInptTmgtDcd("1");
    standardTelegram.getSttlSysCopt().setRqstRcvSvcId("GCBCOM148021");

    standardTelegram.getSttlTrnCopt().setBncd(healthCheckInfo.getBncd());
    standardTelegram.getSttlTrnCopt().setSttlXcd("GCBCOM148021610");
    standardTelegram.getSttlTrnCopt().setSttlRqstFuncDsncId("610");
    standardTelegram.getSttlTrnCopt().setAhdIqtrYn("N");

    standardTelegram.getSttlAmgcCopt().setOptoEmn(healthCheckInfo.getUserInfo());

    LinkedHashMap<String, Object> data = new LinkedHashMap<>();
    data.put("empNo", healthCheckInfo.getUserInfo());
    // data.put("secrtNo", "1234");

    standardTelegram.getUserData().setData(new LinkedHashMap<String, Object>());
    standardTelegram.getUserData().setData(data);

    return standardTelegram;
  }

}
