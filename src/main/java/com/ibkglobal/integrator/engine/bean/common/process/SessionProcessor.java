package com.ibkglobal.integrator.engine.bean.common.process;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.netty4.NettyConstants;

import com.ibkglobal.integrator.config.ConstantCode;
import com.ibkglobal.integrator.engine.netty.util.NettyUtil;

import io.netty.channel.ChannelHandlerContext;

public class SessionProcessor implements Processor {

	@Override
	public void process(Exchange exchange) throws Exception {
		
		// Session Save
		Message message = exchange.getIn();
		ChannelHandlerContext ctx = message.getHeader(NettyConstants.NETTY_CHANNEL_HANDLER_CONTEXT, ChannelHandlerContext.class);
		message.setHeader(ConstantCode.SESSION_ID, NettyUtil.getSessionKey(ctx));
	}
}
