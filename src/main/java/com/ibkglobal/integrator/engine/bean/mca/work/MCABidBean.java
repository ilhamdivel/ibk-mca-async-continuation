package com.ibkglobal.integrator.engine.bean.mca.work;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import com.ibkglobal.integrator.config.CamelConfig;
import com.ibkglobal.integrator.config.ConstantCode;
import com.ibkglobal.integrator.exception.ErrorType;
import com.ibkglobal.integrator.exception.IBKExceptionMCA;
import com.ibkglobal.message.IBKMessage;

public class MCABidBean {
	
	@Autowired
	CamelConfig camelConfig;
	
	/**
	 * Ack 메시지 지정
	 * @param message
	 */
	public void ackMessage(Exchange exchange) throws IBKExceptionMCA {
		
		try {
			Message message = exchange.getIn();
			
			IBKMessage ibkMessage = message.getBody(IBKMessage.class);			
			ibkMessage.getStandardTelegram().getSttlSysCopt().setRqstRspnDcd("K");
			
			message.setBody(ibkMessage);
			
		} catch (Exception e) {
			throw new IBKExceptionMCA(ErrorType.MCA_BID, "Ack Message Create Error");
		}
	}
	
	public void bidWorkCheck(Exchange exchange) throws IBKExceptionMCA {
		
		try {
			Message message = exchange.getIn();
			
			IBKMessage ibkMessage = message.getBody(IBKMessage.class);
			
			// OTPT_TMGT_DCD
			String otptTmgtDcd = ibkMessage.getStandardTelegram().getSttlSysCopt().getOtptTmgtDcd();
			
			if (StringUtils.isEmpty(otptTmgtDcd) && !(otptTmgtDcd.equals("5") || otptTmgtDcd.equals("6"))) {
				throw new IBKExceptionMCA(ErrorType.MCA_BID, "[Bid Work Type Error] OTPT_TMGT_DCD : " + otptTmgtDcd);
			}
			
			// 단말BID출력 : 5, 수신확인BID출력 : 6
			message.setHeader(ConstantCode.BID_WORK_TYPE, otptTmgtDcd);			
		} catch (Exception e) {
			throw new IBKExceptionMCA(ErrorType.MCA_BID, "Bid Work Type Error : " + e);
		}
	}
}
