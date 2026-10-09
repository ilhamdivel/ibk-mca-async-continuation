package com.ibkglobal.integrator.engine.bean.common;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.stream.Collectors;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ibk.ibkglobal.data.integrator.manage.TranControlInfo;
import com.ibkglobal.common.repository.CacheFactory;
import com.ibkglobal.common.repository.CacheType;
import com.ibkglobal.integrator.exception.ErrorType;
import com.ibkglobal.integrator.exception.IBKExceptionMCA;
import com.ibkglobal.message.IBKMessage;
import com.ibkglobal.message.common.normal.StandardTelegram;

import net.sf.ehcache.Cache;
import net.sf.ehcache.Element;

public class TranControl {

  @Autowired
  CacheFactory cacheFactory;

  public void execute(Exchange exchange) throws Exception {
    try {
      Message message = exchange.getIn();
      IBKMessage ibkMessage = message.getBody(IBKMessage.class);
      StandardTelegram standardTelegram = ibkMessage.getStandardTelegram();
      String intfId = StringUtils.isEmpty(ibkMessage.getInterfaceId()) ? "" : ibkMessage.getInterfaceId();
      String sysCd = StringUtils.isEmpty(standardTelegram.getSttlSysCopt().getRqstSysDcd()) ? ""
          : standardTelegram.getSttlSysCopt().getRqstSysDcd();
      String bizChnlCd = StringUtils.isEmpty(standardTelegram.getSttlTrnCopt().getTrnChnlDcd()) ? ""
          : standardTelegram.getSttlTrnCopt().getTrnChnlDcd();

      // 대면공통부 또는 비대면공통부 둘 중 하나는 들어오겠지?
      String brnc = standardTelegram.getSttlAmgcCopt() != null ? standardTelegram.getSttlAmgcCopt().getBrcd()
          : (standardTelegram.getSttlNfchCopt() != null ? standardTelegram.getSttlNfchCopt().getBrcd() : "");
      String svcId = StringUtils.isEmpty(standardTelegram.getSttlSysCopt().getRqstRcvSvcId()) ? ""
          : standardTelegram.getSttlSysCopt().getRqstRcvSvcId();
      String ip = StringUtils.isEmpty(standardTelegram.getSttlSysCopt().getSttlIp()) ? ""
          : standardTelegram.getSttlSysCopt().getSttlIp();
      String ntcd = StringUtils.isEmpty(standardTelegram.getSttlTrnCopt().getNtcd()) ? ""
          : standardTelegram.getSttlTrnCopt().getNtcd();

      TranControlInfo trnCtrl = new TranControlInfo();
      
      Cache cache = cacheFactory.getCacheRepository(CacheType.IBK_MCA_TRANCONTROL);
      
      ObjectMapper mapper = new ObjectMapper();

      List<TranControlInfo> tempList = cache.getAll(cache.getKeys()).values().stream()
          .map(p -> mapper.convertValue(p.getObjectValue(), TranControlInfo.class)).collect(Collectors.toList());

      List<String> cacheKeys = cache.getKeys();

      for (int i = 0; i < cacheKeys.size(); i++) {
        String cacheKey = "";

        String cacheNatl = cacheKeys.get(i).split("_")[0].toString();
        String cacheType = cacheKeys.get(i).split("_")[1].toString();
        String cacheVal = cacheKeys.get(i).split("_")[2].toString();

        String checkVal = cacheType.equals("INTF") ? intfId
            : (cacheType.equals("SYS") ? sysCd
                : (cacheType.equals("SVC") ? svcId
                    : (cacheType.equals("BIZCHNL") ? bizChnlCd
                        : (cacheType.equals("BRNC") ? brnc : cacheType.equals("IP") ? ip : ""))));

        if (cacheNatl.equals("AL") || cacheNatl.equals(ntcd)) {
          if (cacheVal.contains("*")) {
            cacheKey = cacheVal.replaceAll("\\*", "").equals(checkVal.substring(0, cacheVal.length() - 1))
                ? cacheKeys.get(i)
                : "";
          } else {
            cacheKey = cacheVal.equals(checkVal) ? cacheKeys.get(i) : "";
          }
        }

        if (!StringUtils.isEmpty(cacheKey)) {
          try {
            Element element = cache.get(cacheKey);
            trnCtrl = mapper.convertValue(element.getObjectValue(), TranControlInfo.class);
            this.tranControl(trnCtrl);
          } catch (Exception ex) {
            throw new IBKExceptionMCA(ErrorType.MCA, ex.getMessage(), ex);
          }
        }
      }
    } catch (Exception e) {
      throw new IBKExceptionMCA(ErrorType.MCA, e.getMessage(), e);
    }
  }

  public void tranControl(TranControlInfo trnCtrl) throws Exception {
    if (trnCtrl.getAplyYn().equals("Y")) {
      try {
        DateFormat dtFormat = new SimpleDateFormat("HHmmss");

        dtFormat.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));

        Date ctrlStartTime = new SimpleDateFormat("HHmmss").parse(trnCtrl.getSttgTim());
        Date ctrlEndTime = StringUtils.isEmpty(trnCtrl.getFnshTim()) ? null : new SimpleDateFormat("HHmmss").parse(trnCtrl.getFnshTim());
        Date dt = new SimpleDateFormat("HHmmss").parse(dtFormat.format(new Date()));
        
      	if (dt.compareTo(ctrlStartTime) > 0 && (ctrlEndTime == null || Integer.parseInt(trnCtrl.getFnshTim()) == 0 || dt.compareTo(ctrlEndTime) < 0)) {
          throw new IBKExceptionMCA(ErrorType.MCA, trnCtrl.getRspnCon());
        }
      } catch (Exception ex) {
        throw new IBKExceptionMCA(ErrorType.MCA, "MCA Transaction Control Exception: " + ex.getMessage(), ex);
      }
    }
  }
}