package com.ibkglobal.integrator.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.ibk.ibkglobal.data.intf.Interface;
import com.ibkglobal.integrator.config.EndpointCode;
import com.ibkglobal.message.common.normal.DataSetCode;
import com.ibkglobal.message.common.normal.StandardTelegram;
import com.ibkglobal.message.common.normal.copt.SttlMsgCopt;
import com.ibkglobal.message.common.normal.copt.SttlMsgCopt.ClotScreInfo;
import com.ibkglobal.message.common.normal.copt.SttlMsgCopt.PnmgInfo;

public class IBKMessageUtil {

  public static String getDynamicDestination(Interface intf) {

    StringBuffer sb = new StringBuffer();

    sb.append(EndpointCode.DIRECT);
    sb.append(routingRuleCheck(intf.getInterfaceType().getTarget().getSystem().getCode()));
    sb.append(".");
    sb.append(routingRuleCheck("KR"));
    sb.append(".");
    sb.append("ROUTE");

    return sb.toString();
  }

  public static String routingRuleCheck(String data) {

    String result = "";

    if (data.length() < 4) {
      result = String.format("%s%0" + (4 - data.length()) + "d", data, 0);
    } else {
      result = data;
    }

    return result;
  }

  /**
   * Get GlobalId
   * 
   * @param standardTelegram
   * @return
   */
  public static String getGid(StandardTelegram standardTelegram) {
    String whbnSttlWrtnYmd = standardTelegram.getSttlSysCopt().getWhbnSttlWrtnYmd();
    String whbnSttlCretSysNm = standardTelegram.getSttlSysCopt().getWhbnSttlCretSysNm();
    String whbnSttlSrn = standardTelegram.getSttlSysCopt().getWhbnSttlSrn();

    String gid = whbnSttlWrtnYmd + whbnSttlCretSysNm + whbnSttlSrn;

    return gid;
  }

  /**
   * Get InterfaceId
   * 
   * @param standardTelegram
   * @return
   */
  public static String getInterfaceId(StandardTelegram standardTelegram) {
    String interfaceId = standardTelegram.getSttlSysCopt().getSttlIntfId();

    return interfaceId;
  }

  /**
   * Get ReqResFlag
   * 
   * @param standardTelegram
   * @return
   */
  public static String getReqResFlag(StandardTelegram standardTelegram) {
    String rqstRspnDcd = standardTelegram.getSttlSysCopt().getRqstRspnDcd();

    return rqstRspnDcd;
  }

  public static void replyMessageDefaultSet(StandardTelegram standardTelegram, String systemCode) {
    // 송신시스템구분코드
    standardTelegram.getSttlSysCopt().setTrnmSysDcd(systemCode);
  }

  public static void replyMessageDefaultSet(StandardTelegram standardTelegram, String systemCode, String sendRecv) {
    // 송신시스템구분코드
    standardTelegram.getSttlSysCopt().setTrnmSysDcd(systemCode);
    // 요청응답구분코드
    standardTelegram.getSttlSysCopt().setRqstRspnDcd(sendRecv);
  }

  public static void replyMessageBidDefaultSet(StandardTelegram standardTelegram, String systemCode, String sendRecv,
      String resultCode) {
    LinkedHashMap<String, Object> data = new LinkedHashMap<String, Object>();
    // UserData Null 응답
    standardTelegram.getUserData().setData(data);

    // 송신시스템구분코드
    standardTelegram.getSttlSysCopt().setTrnmSysDcd(systemCode);
    // 요청응답구분코드
    standardTelegram.getSttlSysCopt().setRqstRspnDcd(sendRecv);
    // 응답처리결과구분코드
    standardTelegram.getSttlSysCopt().setRspnPcrsDcd(resultCode);

    SttlMsgCopt sttlMsgCopt = new SttlMsgCopt();
    sttlMsgCopt.setDtstDcd(DataSetCode.MSG_COPT);
    sttlMsgCopt.setMsgRpsnDcd("0");

    // 주메시지정보(PNMG_INFO)
    List<PnmgInfo> pnmgInfoList = new ArrayList<>();

    PnmgInfo pnmgInfo = new PnmgInfo();
    pnmgInfo.setPnmgCd(standardTelegram.getSttlSysCopt().getSttlErcd());
    pnmgInfo.setPnmgDsncPgrsNo(1);
    pnmgInfo.setPnmgCon("정상처리되었습니다.");

    pnmgInfoList.add(pnmgInfo);

    sttlMsgCopt.setPnmgInfo(pnmgInfoList);

    // 표출화면정보(CLOT_SCRE_INFO)
    List<ClotScreInfo> clotScreInfoList = new ArrayList<>();

    ClotScreInfo clotScreInfo = new ClotScreInfo();
    clotScreInfo.setPnmgInkyVl(1);
    clotScreInfo.setClotScreNm("00000");
    clotScreInfo.setClotScreAdr("00000");

    clotScreInfoList.add(clotScreInfo);

    sttlMsgCopt.setClotScreInfo(clotScreInfoList);
  }
}
