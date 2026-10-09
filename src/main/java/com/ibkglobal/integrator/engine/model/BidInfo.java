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
