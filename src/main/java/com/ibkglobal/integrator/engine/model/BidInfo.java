package com.ibkglobal.integrator.engine.model;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.camel.AsyncCallback;
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

  private AsyncCallback asyncCallback;
  private long deadlineNanos;
  // volatile: armed by the dummy-ack thread AFTER the ticket is visible in
  // bidInfoList and read by whichever thread completes it. Together with
  // continuationCompleted this is a "write, then check the other flag" handshake
  // that is only safe when both sides are volatile; otherwise a release can miss
  // the timer while the arming thread misses the completion, and the 100 ms
  // fallback task would never be cancelled.
  private volatile String asyncTimerKey;
  private final AtomicBoolean timeoutQueued = new AtomicBoolean(false);
  private volatile ScheduledFuture<?> fallbackDeadline;
  private boolean asyncPermitOwned;
  private Map<String, String> asyncMdc;
  private final AtomicBoolean continuationCompleted = new AtomicBoolean(false);

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
