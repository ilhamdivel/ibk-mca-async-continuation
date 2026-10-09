package com.ibkglobal.integrator.engine.netty.channel;

import java.util.LinkedHashMap;

import com.ibkglobal.integrator.engine.netty.util.NettyUtil;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;

@Sharable
public class TcpConnectionChannel implements ChannelHandler {
	
	private LinkedHashMap<String, ChannelHandlerContext> map;
	private Integer maxConnection;
	
	public TcpConnectionChannel(LinkedHashMap<String, ChannelHandlerContext> map, Integer maxConnection) {
		this.map = map;
		this.maxConnection = maxConnection;
	}

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		map.put(NettyUtil.getKey(ctx), ctx);
		
		if (map.size() <= maxConnection)
			ctx.close();
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		map.remove(NettyUtil.getKey(ctx));
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
	}
}
