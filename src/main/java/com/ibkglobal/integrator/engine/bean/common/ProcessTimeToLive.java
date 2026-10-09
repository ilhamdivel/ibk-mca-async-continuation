package com.ibkglobal.integrator.engine.bean.common;

import org.apache.camel.Exchange;
import org.springframework.beans.factory.annotation.Autowired;

import com.ibkglobal.integrator.config.ConstantCode;
import com.ibkglobal.message.common.normal.StandardTelegram;
import com.ibkglobal.message.converter.service.ConverterService;

public class ProcessTimeToLive {

	@Autowired
	ConverterService converterService;
	
	/**
	 * Set Time To Live
	 * @param exchange
	 */
	public void setTimeToLive(Exchange exchange) {		
		StandardTelegram st = exchange.getMessage().getHeader(ConstantCode.IBK_NORMAL_HEADER, StandardTelegram.class);
		
		// TTL(Time To Live) 설정
		long timeout = 30;
		if (st != null && "Y".equals(st.getSttlSysCopt().getSttlMctmUseYn())) {
			timeout = st.getSttlSysCopt().getSttlMctmSecVl();
		}
		exchange.getMessage().setHeader("STTL_MCTM_SEC_VL", timeout * 1000);
	}
}
