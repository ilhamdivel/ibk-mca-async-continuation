package com.ibkglobal.integrator.engine.manager;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.camel.component.netty4.NettyConfiguration;

import com.ibkglobal.integrator.engine.model.SessionInfo;
import com.ibkglobal.integrator.engine.netty.util.NettyUtil;

import io.netty.channel.ChannelHandlerContext;

public class SessionManager {

  private static Map<SessionInfo, ChannelHandlerContext> sessionList = new HashMap<>();

  public static Map<SessionInfo, ChannelHandlerContext> getSessionList() {
    synchronized (sessionList) {
      return sessionList;
    }
  }

  /**
   * 세션 키로 세션키 정보 추출
   * 
   * @param sessionKey
   * @return
   */
  public static SessionInfo getSessionInfo(String sessionKey) {

    SessionInfo sessionInfo = null;

    for (Entry<SessionInfo, ChannelHandlerContext> entry : getSessionList().entrySet()) {
      if (entry.getKey().getSessionKey().equals(sessionKey)) {
        sessionInfo = entry.getKey();
        break;
      }
    }

    return sessionInfo;
  }

  /**
   * 세션 키로 세션 추출
   * 
   * @param sessionKey
   * @return
   */
  public static ChannelHandlerContext getSession(String sessionKey) {
    return getSessionList().get(getSessionInfo(sessionKey));
  }

  /**
   * 기관 코드와 업무코드로 세션키 정보 추출
   * 
   * @param orgCd
   * @param bizCd
   * @return
   */
  public static SessionInfo getSessionInfo(String orgCd, String bizCd) {

    SessionInfo sessionInfo = null;

    for (Entry<SessionInfo, ChannelHandlerContext> entry : getSessionList().entrySet()) {
      if (entry.getKey().getOrgCd().equals(orgCd) && entry.getKey().getBizCd().equals(bizCd)) {
        sessionInfo = entry.getKey();
        break;
      }
    }

    return sessionInfo;
  }

  /**
   * 기관 코드와 업무코드로 세션 추출
   * 
   * @param sessionKey
   * @return
   */
  public static ChannelHandlerContext getSession(String orgCd, String bizCd) {
    return getSessionList().get(getSessionInfo(orgCd, bizCd));
  }

  /**
   * 세션 정보 생성
   * 
   * @param ctx
   */
  public static void putSession(ChannelHandlerContext ctx, NettyConfiguration config) {

    SessionInfo sessionInfo = new SessionInfo();

    sessionInfo.setSessionKey(NettyUtil.getSessionKey(ctx));

    if (config.getOptions() != null) {
      sessionInfo.setSysCd(config.getOptions().containsKey("sysCd") ? config.getOptions().get("sysCd").toString() : "");
      sessionInfo.setOrgCd(config.getOptions().containsKey("orgCd") ? config.getOptions().get("orgCd").toString() : "");
      sessionInfo.setBizCd(config.getOptions().containsKey("bizCd") ? config.getOptions().get("bizCd").toString() : "");
    }

    getSessionList().put(sessionInfo, ctx);
  }

  /**
   * 세션 키로 세션 정보 제거
   * 
   * @param sessionKey
   */
  public static void removeSession(ChannelHandlerContext ctx) {

    SessionInfo sessionInfo = getSessionInfo(NettyUtil.getSessionKey(ctx));

    if (sessionInfo != null) {
      getSessionList().remove(getSessionInfo(NettyUtil.getSessionKey(ctx)));
    }
  }
}
