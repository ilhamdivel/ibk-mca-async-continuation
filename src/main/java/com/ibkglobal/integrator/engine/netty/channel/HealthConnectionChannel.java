package com.ibkglobal.integrator.engine.netty.channel;

import com.ibkglobal.integrator.engine.manager.HealthCheckManager;
import com.ibkglobal.integrator.engine.model.HealthCheckInfo;
import com.ibkglobal.integrator.engine.model.HealthCheckInfo.HealthStatus;
import com.ibkglobal.integrator.engine.netty.util.NettyUtil;
import com.ibkglobal.integrator.util.HealthCheckUtil;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelHandler.Sharable;

@Sharable
public class HealthConnectionChannel implements ChannelHandler {

	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		HealthCheckInfo healthCheckInfo = new HealthCheckInfo();
		healthCheckInfo.setStatus(HealthStatus.LOGIN);
		healthCheckInfo.setHealthTime(HealthCheckUtil.currentTime());
		healthCheckInfo.setContext(ctx);
		
		HealthCheckManager.getClientList().put(NettyUtil.getHost(ctx), healthCheckInfo);
	}

	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		// 헬스체크 회선 종료
		HealthCheckManager.getClientList().remove(NettyUtil.getHost(ctx));
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
	}
}
