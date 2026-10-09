package com.ibkglobal.integrator.engine.timer;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

import com.ibkglobal.integrator.engine.model.BidInfo;
import com.ibkglobal.integrator.engine.model.IBKTimeoutInfo;

@Component
public class IBKTimeoutBean {
	
	@Bean
	IBKTimeout<IBKTimeoutInfo> ibkTimeoutTimeCom() throws Exception {
		IBKTimeout<IBKTimeoutInfo> ibkTimeoutTimeCom = new IBKTimeout<>();
		ibkTimeoutTimeCom.timeCom();
		ibkTimeoutTimeCom.init();
		return ibkTimeoutTimeCom;
	}
	
	@Bean
	IBKTimeout<BidInfo> ibkTimeoutBid() throws Exception {
		IBKTimeout<BidInfo> ibkTimeoutBid = new IBKTimeout<>();
		ibkTimeoutBid.timeBid();
		ibkTimeoutBid.init();
		return ibkTimeoutBid;
	}
}
