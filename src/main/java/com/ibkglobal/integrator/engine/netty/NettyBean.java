package com.ibkglobal.integrator.engine.netty;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.ibkglobal.integrator.engine.netty.factory.IBKHttpConsumerInitializer;
import com.ibkglobal.integrator.engine.netty.factory.IBKHttpProducerInitializer;
import com.ibkglobal.integrator.engine.netty.factory.IBKTcpConsumerInitializer;
import com.ibkglobal.integrator.engine.netty.factory.IBKTcpProducerInitializer;

@Configuration
public class NettyBean {
		
	@Bean
	public IBKHttpConsumerInitializer ibkHttpConsumerInitializer() {
		IBKHttpConsumerInitializer ibkHttpConsumerInitializer = new IBKHttpConsumerInitializer();
		return ibkHttpConsumerInitializer;
	}
	
	@Bean
	public IBKHttpProducerInitializer ibkHttpProducerInitializer() {
		IBKHttpProducerInitializer ibkHttpProducerInitializer = new IBKHttpProducerInitializer();
		return ibkHttpProducerInitializer;
	}
	
	@Bean
	public IBKTcpConsumerInitializer ibkTcpConsumerInitializer() {
		IBKTcpConsumerInitializer ibkTcpConsumerInitializer = new IBKTcpConsumerInitializer();
		return ibkTcpConsumerInitializer;
	}
	
	@Bean
	public IBKTcpProducerInitializer ibkTcpProducerInitializer() {
		IBKTcpProducerInitializer ibkTcpProducerInitializer = new IBKTcpProducerInitializer();
		return ibkTcpProducerInitializer;
	}
}
