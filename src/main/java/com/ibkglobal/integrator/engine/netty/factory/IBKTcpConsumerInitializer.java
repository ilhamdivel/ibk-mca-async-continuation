package com.ibkglobal.integrator.engine.netty.factory;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.component.netty4.ChannelHandlerFactory;
import org.apache.camel.component.netty4.NettyConsumer;
import org.apache.camel.component.netty4.ServerInitializerFactory;
import org.apache.camel.component.netty4.handlers.ServerChannelHandler;

import com.ibkglobal.integrator.engine.netty.channel.HealthConnectionChannel;
import com.ibkglobal.integrator.engine.netty.channel.SessionChannel;
import com.ibkglobal.integrator.engine.netty.codec.IBKDefaultDecoder;
import com.ibkglobal.integrator.engine.netty.codec.IBKDefaultEncoder;
import com.ibkglobal.integrator.engine.netty.util.NettyUtil;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.util.concurrent.EventExecutorGroup;

public class IBKTcpConsumerInitializer extends ServerInitializerFactory {

	private NettyConsumer consumer;
	
	public IBKTcpConsumerInitializer() {}
	
	public IBKTcpConsumerInitializer(NettyConsumer consumer) {
		this.consumer = consumer;
	}
	
	@Override
	public ServerInitializerFactory createPipelineFactory(NettyConsumer consumer) {
		return new IBKTcpConsumerInitializer(consumer);
	}

	@Override
	protected void initChannel(Channel ch) throws Exception {
		ChannelPipeline channelPipeline = ch.pipeline();
		
		// Encoder
		List<ChannelHandler> encoders = new ArrayList<>();
		// Decoder
		List<ChannelHandler> decoders = new ArrayList<>();
		
		// 코덱 셋
		if (consumer.getConfiguration().getOptions() != null) {
			if (consumer.getConfiguration().getOptions().containsKey("encoding") 
					&& consumer.getConfiguration().getOptions().get("encoding").equals("healthCheck")) {
				// Connection Channel(Health Check)
				addToPipeline("healthCheckManager", channelPipeline, new HealthConnectionChannel());
				
				encoders.add(new IBKDefaultEncoder());			
				decoders.add(new IBKDefaultDecoder());
			} else {
				// Connection Channel(Session Channel)
				addToPipeline("sessionManager", channelPipeline, new SessionChannel(consumer));
				
				NettyUtil.setCodec("tcp", consumer.getConfiguration(), encoders, decoders);
			}
		} else {
			NettyUtil.setCodec("tcp", consumer.getConfiguration(), encoders, decoders);
		}
		
		// 인코더 생성
		for (int x = 0; x < encoders.size(); x++) {
	    	ChannelHandler encoder = encoders.get(x);
	    	
	    	if (encoder instanceof ChannelHandlerFactory) {
	    		encoder = ((ChannelHandlerFactory) encoder).newChannelHandler();
	    	}
	    	
	    	addToPipeline("encoder-" + x, channelPipeline, encoder);
	    }
		// 디코더 생성
		for (int x = 0; x < decoders.size(); x++) {
	    	ChannelHandler decoder = decoders.get(x);
	    	
	    	if (decoder instanceof ChannelHandlerFactory) {
	    		decoder = ((ChannelHandlerFactory) decoder).newChannelHandler();
	    	}
	    	
	    	addToPipeline("decoder-" + x, channelPipeline, decoder);
	    }
	    
	    if (consumer.getConfiguration().isUsingExecutorService()) {
	    	EventExecutorGroup applicationExecutor = consumer.getEndpoint().getComponent().getExecutorService();
	    	addToPipeline("handler", channelPipeline, applicationExecutor, new ServerChannelHandler(consumer));
	    } else {
	    	addToPipeline("handler", channelPipeline, new ServerChannelHandler(consumer));
	    }
	}
	
	private void addToPipeline(String name, ChannelPipeline pipeline, ChannelHandler handler) {
        pipeline.addLast(name, handler);
    }
    
    private void addToPipeline(String name, ChannelPipeline pipeline, EventExecutorGroup executor, ChannelHandler handler) {
        pipeline.addLast(executor, name, handler);
    }
}
