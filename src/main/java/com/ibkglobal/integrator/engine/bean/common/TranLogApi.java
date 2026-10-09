package com.ibkglobal.integrator.engine.bean.common;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.springframework.stereotype.Repository;

import com.ibkglobal.common.convert.Converter;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.log.LogType;
import com.ibkglobal.message.IBKMessage;
import com.ibkglobal.message.common.normal.StandardTelegram;
import com.penta.tran.api.TranApiAdapter;

@Repository
public class TranLogApi {
	
	public void TranLog(IBKMessage ibkMessage, String flag, String serviceId) throws Exception {
		String globalId = "";
		
		try {
			//Message message = exchange.getIn();
			
			//IBKMessage ibkMessage = message.getBody(IBKMessage.class);
			StandardTelegram standardTelegram = ibkMessage.getStandardTelegram();
			
			//String globalId = standardTelegram.getSttlSysCopt().getSttlIntfAnunId();
			globalId = standardTelegram.getSttlSysCopt().getWhbnSttlWrtnYmd() 
					+ standardTelegram.getSttlSysCopt().getWhbnSttlCretSysNm() 
					+ standardTelegram.getSttlSysCopt().getWhbnSttlSrn();
			
			// 로컬 테스트 전문 제외
			if (!standardTelegram.getSttlSysCopt().getSysEnvrInfoDcd().equals("L")) {
				if (flag.equals("Start")) {
					TranApiAdapter.getInstance().Tran_StartX(globalId, serviceId, Converter.mapper.writeValueAsString(standardTelegram));
				} else if (flag.equals("End")) {
					TranApiAdapter.getInstance().Tran_EndX(globalId, serviceId, Converter.mapper.writeValueAsString(standardTelegram));
				}
			}
			
			LogManager.getLogger(LogType.ROOT).info("TranLog globalId: " + globalId + ", flag: " + flag + ", serviceId: " + serviceId);
		} catch (Exception e) {
			LogManager.getLogger(LogType.ROOT).info("TranLog Exception globalId: " + globalId + ", flag: " + flag + ", serviceId: " + serviceId + ", error: " + e.getMessage());
		}
	}
	
	public void TranLog(Exchange exchange, String flag, String serviceId) throws Exception {
		String globalId = "";
		try {
			Message message = exchange.getIn();
			
			IBKMessage ibkMessage = message.getBody(IBKMessage.class);
			StandardTelegram standardTelegram = ibkMessage.getStandardTelegram();
			
			//String globalId = standardTelegram.getSttlSysCopt().getSttlIntfAnunId();
			globalId = standardTelegram.getSttlSysCopt().getWhbnSttlWrtnYmd() 
					+ standardTelegram.getSttlSysCopt().getWhbnSttlCretSysNm() 
					+ standardTelegram.getSttlSysCopt().getWhbnSttlSrn();
			
			// 로컬 테스트 전문 제외
			if (!standardTelegram.getSttlSysCopt().getSysEnvrInfoDcd().equals("L")) {
				if (flag.equals("Start")) {
					TranApiAdapter.getInstance().Tran_StartX(globalId, serviceId, Converter.mapper.writeValueAsString(standardTelegram));
				} else if (flag.equals("End")) {
					TranApiAdapter.getInstance().Tran_EndX(globalId, serviceId, Converter.mapper.writeValueAsString(standardTelegram));
				}
			}
			
			LogManager.getLogger(LogType.ROOT).info("TranLog Exchange globalId: " + globalId + ", flag: " + flag + ", serviceId: " + serviceId);
		} catch (Exception e) {
			LogManager.getLogger(LogType.EXCEPTION).info("TranLog Exchange Exception globalId: " + globalId + ", flag: " + flag + ", serviceId: " + serviceId + ", error: " + e.getMessage());
		}
	}
}
