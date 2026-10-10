package com.ibkglobal.integrator.engine.netty.factory;

import org.apache.camel.Exchange;
import org.apache.camel.component.netty4.NettyCamelState;
import org.apache.camel.component.netty4.NettyHelper;
import org.apache.camel.component.netty4.http.NettyHttpProducer;
import org.apache.camel.component.netty4.http.handlers.HttpClientChannelHandler;

import com.ibkglobal.integrator.engine.manager.BidManager;

import io.netty.channel.ChannelHandlerContext;

/**
 * Camel 2.21.1 HttpClientChannelHandler with one guard for BID dummy-ack
 * continuations.
 *
 * Camel keeps the request state (exchange + producer callback) attached to the
 * channel after the reply has been delivered, and resets its "message
 * received" flag in channelReadComplete. When that channel later becomes
 * inactive (always with disconnect=true, as configured for GCB) or fails, and
 * the exchange is not done yet, ClientChannelHandler calls the producer
 * callback a second time (exceptionCaught also sets the error on the exchange).
 * For a normal exchange this cannot happen: it finishes before the channel
 * event. A dummy-ack exchange is deliberately still suspended at that moment,
 * so the second callback would return the pooled channel twice and lead to
 * replies crossing between transactions.
 *
 * The guard applies only when the reply for the current state was already
 * delivered on this channel AND the exchange entered the BID continuation
 * (BidManager.BID_CONTINUATION). In every other case Camel's behaviour is
 * unchanged.
 *
 * Every exchange whose reply this handler delivers is marked
 * BidManager.BID_SAFE_REPLY; MCAWorkAfterAsync suspends only those. A dummy ack
 * received through any other producer keeps the office blocking wait.
 */
public class BidSafeHttpClientChannelHandler extends HttpClientChannelHandler {

  private final NettyHttpProducer producer;

  /** State whose reply has already been handed to Camel on this channel. */
  private volatile NettyCamelState answered;

  public BidSafeHttpClientChannelHandler(NettyHttpProducer producer) {
    super(producer);
    this.producer = producer;
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
    NettyCamelState state = producer.getCorrelationManager().getState(ctx, ctx.channel(), msg);
    answered = state;
    if (state != null && state.getExchange() != null) {
      state.getExchange().setProperty(BidManager.BID_SAFE_REPLY, Boolean.TRUE);
    }
    super.channelRead0(ctx, msg);
  }

  @Override
  public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    if (isAnsweredBidContinuation(ctx)) {
      // what ClientChannelHandler.channelInactive does, minus the second callback
      producer.getCorrelationManager().removeState(ctx, ctx.channel());
      producer.getAllChannels().remove(ctx.channel());
      ctx.fireChannelInactive();
      return;
    }
    super.channelInactive(ctx);
  }

  @Override
  public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
    if (isAnsweredBidContinuation(ctx)) {
      // the request was answered; an error on the idle channel must not fail the
      // suspended exchange or call its callback again
      NettyHelper.close(ctx.channel());
      return;
    }
    super.exceptionCaught(ctx, cause);
  }

  private boolean isAnsweredBidContinuation(ChannelHandlerContext ctx) {
    NettyCamelState state = producer.getCorrelationManager().getState(ctx, ctx.channel(), (Throwable) null);
    if (state == null || state != answered) {
      return false;
    }
    Exchange exchange = state.getExchange();
    return exchange != null && Boolean.TRUE.equals(exchange.getProperty(BidManager.BID_CONTINUATION, Boolean.class));
  }
}
