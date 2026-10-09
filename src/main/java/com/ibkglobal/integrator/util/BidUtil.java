package com.ibkglobal.integrator.util;

import org.apache.camel.Exchange;
import org.springframework.util.StringUtils;

import com.ibkglobal.integrator.engine.model.BidInfo;
import com.ibkglobal.integrator.engine.model.BidInfo.BidStatus;
import com.ibkglobal.message.common.normal.StandardTelegram;

public class BidUtil {

  public static BidInfo bidCreate(Exchange exchange, StandardTelegram standardTelegram) {

    String key = getBidKey(standardTelegram);

    BidInfo bidInfo = new BidInfo();
    bidInfo.setName(key);
    bidInfo.setBeforeExchange(exchange);
    bidInfo.setStatus(BidStatus.WAIT);
    bidInfo.setTimeStamp(SystemUtil.getCurrentTime());

    return bidInfo;
  }

  public static boolean hasBidKey(StandardTelegram standardTelegram) {

    if (standardTelegram == null || standardTelegram.getSttlSysCopt() == null) {
      return false;
    }

    return !StringUtils.isEmpty(standardTelegram.getSttlSysCopt().getWhbnSttlWrtnYmd())
        && !StringUtils.isEmpty(standardTelegram.getSttlSysCopt().getWhbnSttlCretSysNm())
        && !StringUtils.isEmpty(standardTelegram.getSttlSysCopt().getWhbnSttlSrn());
  }

  public static String getBidKey(StandardTelegram standardTelegram) {

    String key = standardTelegram.getSttlSysCopt().getWhbnSttlWrtnYmd()
        + standardTelegram.getSttlSysCopt().getWhbnSttlCretSysNm() + standardTelegram.getSttlSysCopt().getWhbnSttlSrn();

    return key;
  }
}
