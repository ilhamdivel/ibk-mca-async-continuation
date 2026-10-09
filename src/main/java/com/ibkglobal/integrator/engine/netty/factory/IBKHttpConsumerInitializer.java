package com.ibkglobal.integrator.engine.netty.factory;

import org.apache.camel.component.netty4.NettyConsumer;
import org.apache.camel.component.netty4.ServerInitializerFactory;
import org.apache.camel.component.netty4.handlers.ServerChannelHandler;

import com.ibkglobal.integrator.engine.netty.channel.SessionChannel;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelPipeline;
import io.netty.util.concurrent.EventExecutorGroup;

public class IBKHttpConsumerInitializer extends ServerInitializerFactory {
	
	private NettyConsumer consumer;
	
	public IBKHttpConsumerInitializer() {}
	
	public IBKHttpConsumerInitializer(NettyConsumer consumer) {
		this.consumer = consumer;
	}
	
	@Override
	public ServerInitializerFactory createPipelineFactory(NettyConsumer consumer) {
		return new IBKHttpConsumerInitializer(consumer);
	}

	@Override
	protected void initChannel(Channel ch) throws Exception {
		ChannelPipeline channelPipeline = ch.pipeline();
		
		// Connection Channel
		addToPipeline("sessionManager", channelPipeline, new SessionChannel(consumer));
	    
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
