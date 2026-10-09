package com.ibkglobal.integrator.engine.model;

import java.util.concurrent.CompletableFuture;

import org.apache.camel.Exchange;

import lombok.Data;

@Data
public class BidInfo {

  private String    name;
  private Exchange  beforeExchange;
  private Exchange  afterExchange;
  private BidStatus status;
  private String    etc;
  private long      timeStamp;
  // private long defaultTimeOut = 310000;
  private long defaultTimeOut = 100000;

  private final CompletableFuture<Void> future = new CompletableFuture<>();

  private org.apache.camel.AsyncCallback asyncCallback;
  private long deadlineNanos;
  private String asyncTimerKey;
  private final java.util.concurrent.atomic.AtomicBoolean timeoutQueued =
      new java.util.concurrent.atomic.AtomicBoolean(false);
  private java.util.concurrent.ScheduledFuture<?> fallbackDeadline;
  private io.netty.channel.ChannelFutureListener closeListener;
  private io.netty.channel.Channel originalChannel;
  private boolean asyncPermitOwned;
  private java.util.Map<String, String> asyncMdc;
  private final java.util.concurrent.atomic.AtomicBoolean continuationCompleted =
      new java.util.concurrent.atomic.AtomicBoolean(false);

  // PENDING :pre-registered before the request is sent, dummy ack not yet
  // received
  // RELEASED : BID release / real response arrived BEFORE the dummy ack (result
  // parked here)
  // WAIT : dummy ack received, transaction thread is blocked waiting for the
  // release
  public static enum BidStatus {
    PENDING, RELEASED, WAIT, COMPLETE, TIMEOUT, INNERTIMEOUT
  }
}
