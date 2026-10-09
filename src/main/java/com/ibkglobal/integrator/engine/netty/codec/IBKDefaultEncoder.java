package com.ibkglobal.integrator.engine.netty.codec;

import java.util.List;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

public class IBKDefaultEncoder extends MessageToMessageEncoder<byte[]> {

	@Override
	protected void encode(ChannelHandlerContext ctx, byte[] msg, List<Object> out) throws Exception {
		out.add(Unpooled.wrappedBuffer(msg));
	}	
}
