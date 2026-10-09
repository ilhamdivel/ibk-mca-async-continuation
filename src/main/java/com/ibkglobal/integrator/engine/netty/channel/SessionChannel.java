package com.ibkglobal.integrator.engine.netty.channel;

import org.apache.camel.component.netty4.NettyConsumer;

import com.ibkglobal.integrator.engine.manager.SessionManager;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandler.Sharable;

@Sharable
public class SessionChannel implements ChannelHandler {
	
	NettyConsumer consumer;
	
	public SessionChannel(NettyConsumer consumer) {
		this.consumer = consumer;
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		SessionManager.putSession(ctx, consumer.getConfiguration());
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		SessionManager.removeSession(ctx);
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
		
	}

}
