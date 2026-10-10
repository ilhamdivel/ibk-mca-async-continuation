package com.ibkglobal.integrator.engine.manager;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.ibkglobal.integrator.config.ConstantCode;
import com.ibkglobal.integrator.engine.model.BidInfo;
import com.ibkglobal.integrator.engine.model.BidInfo.BidStatus;
import com.ibkglobal.integrator.engine.timer.IBKTimeout;
import com.ibkglobal.integrator.exception.ErrorType;
import com.ibkglobal.integrator.exception.IBKExceptionMCA;
import com.ibkglobal.integrator.util.ErrorUtil;
import com.ibkglobal.integrator.util.SystemUtil;
import com.ibkglobal.log.LogManager;
import com.ibkglobal.log.LogType;
import com.ibkglobal.message.InfraType;

@Component
public class BidManager {

  @Autowired
  IBKTimeout<BidInfo> ibkTimeoutBid;

  private Map<String, BidInfo> bidInfoList = new ConcurrentHashMap<>();

  public Map<String, BidInfo> getBidInfoList() {
    return bidInfoList;
  }

  /**
   * Exchange property set on every exchange that enters the dummy-ack
   * continuation. The GCB HTTP producer handler uses it to ignore the stale
   * channel events Camel 2.21.1 would otherwise turn into a second producer
   * callback for an exchange that is still suspended (see
   * BidSafeHttpClientChannelHandler).
   */
  public static final String BID_CONTINUATION = "MCA_BID_CONTINUATION";

  /**
   * Exchange property set by BidSafeHttpClientChannelHandler on every exchange
   * whose reply it delivers (the GCB adapter-out, built by EndpointCreate with
   * ibkHttpProducerInitializer). Only such a dummy ack is suspended
   * (bidStartAsync). A dummy ack that arrives any other way (the LOCAL
   * adapter-out on Camel's default netty4-http client, a TCP adapter-out, ...)
   * keeps the office blocking wait (bidStartOfficeWait): Camel 2.21.1 would call
   * that producer's callback a second time while the exchange is suspended.
   */
  public static final String BID_SAFE_REPLY = "MCA_BID_SAFE_REPLY";

  /**
   * Release outcome, so each caller can keep its own "no BID found" policy.
   */
  public static enum ReleaseResult {
    DELIVERED, // a thread was waiting (WAIT) and has been notified
    PARKED, // arrived before the dummy ack, result kept for it (RELEASED)
    DUPLICATE, // a release was already parked for this key, ignored
    NOT_FOUND // no BID registered (late, unknown, or pre-register skipped)
  }

  /**
   * Pre-register a BID key before the request is sent to the host, so a release
   * that overtakes the dummy ack has somewhere to land.
   *
   * @return the registered BidInfo, or null when the key is already in use
   */
  public BidInfo bidPreRegister(String key) {
    BidInfo bidInfo = new BidInfo();
    bidInfo.setName(key);
    bidInfo.setStatus(BidStatus.PENDING);
    bidInfo.setTimeStamp(SystemUtil.getCurrentTime());

    BidInfo existing = getBidInfoList().putIfAbsent(key, bidInfo);

    if (existing != null) {
      LogManager.getLogger(LogType.ROOT)
          .info("Bid PreRegister skipped, key already registered : " + key + " / " + existing.getStatus());
      return null;
    }

    return bidInfo;
  }

  /**
   * Remove a pre-registered entry that never turned into a wait (normal response,
   * error, or a parked release whose dummy ack never came). Identity-checked so
   * it never removes an entry owned by someone else.
   */
  public void bidUnregister(String key, BidInfo expected) {
    getBidInfoList().computeIfPresent(key, (k, cur) -> {
      if (cur != expected) {
        return cur;
      }

      if (cur.getStatus() == BidStatus.RELEASED) {
        LogManager.getLogger(LogType.ROOT).info("Bid released but dummy ack never received, discarded : " + key);
      }

      return (cur.getStatus() == BidStatus.PENDING || cur.getStatus() == BidStatus.RELEASED) ? null : cur;
    });
  }

  /**
   * Dummy ack received: wait for the release, or finish immediately when the
   * release already arrived (RELEASED) — in that case the thread is not held.
   */
  public void bidStart(BidInfo bidInfo) throws Exception {
    String key = bidInfo.getName();

    BidInfo[] parked = { null };
    BidInfo[] duplicate = { null };

    try {
      bidInfo.setStatus(BidStatus.WAIT);

      getBidInfoList().compute(key, (k, cur) -> {
        if (cur == null || cur.getStatus() == BidStatus.PENDING) {
          return bidInfo;
        }

        if (cur.getStatus() == BidStatus.RELEASED) {
          parked[0] = cur;
          return null;
        }

        duplicate[0] = cur;
        return cur;
      });
    } catch (Exception e) {
      throw new IBKExceptionMCA(ErrorType.MCA_BID, "Bid Start Error : " + e.getMessage(), e);
    }

    if (duplicate[0] != null) {
      throw new IBKExceptionMCA(ErrorType.MCA_BID,
          "Bid Start Error : duplicate dummy ack, key : " + key + " / " + duplicate[0].getStatus());
    }

    if (parked[0] != null) {
      LogManager.getLogger(LogType.ROOT).info("Bid release arrived before dummy ack, complete without wait : " + key);
      complete(bidInfo, parked[0].getAfterExchange());
      return;
    }

    try {

      ibkTimeoutBid.put(key, bidInfo, bidInfo.getDefaultTimeOut());

      workWait(bidInfo);
    } catch (Exception e) {
      getBidInfoList().remove(key, bidInfo);
      throw new IBKExceptionMCA(ErrorType.MCA_BID, "Bid Start Error : " + e.getMessage(), e);
    } finally {
      // Drops a timer that was put after a release already completed us.
      ibkTimeoutBid.remove(key);
    }
  }

  /**
   * Office blocking wait (bidStart) for a dummy ack that was not delivered by
   * BidSafeHttpClientChannelHandler (see BID_SAFE_REPLY). Counted as officeWaits.
   */
  public void bidStartOfficeWait(BidInfo bidInfo) throws Exception {
    officeWaits.incrementAndGet();
    bidStart(bidInfo);
  }

  /**
   * Called by the timer eviction (holding the timer lock). Only times out the
   * exact BidInfo the timer was armed for, and only while it is still WAIT.
   */
  public void bidTimeout(String key, BidInfo expected) {
    if (expected.getAsyncCallback() != null) {
      requestAsyncTimeout(expected, false);
      return;
    }
    expireTicket(key, expected);
  }

  /**
   * Queues the timeout answer of an async ticket at most once (timeoutQueued).
   * Never runs the continuation on the calling timer thread. Until a completion
   * worker picks it up the ticket stays WAIT, so a release that still arrives
   * wins with the real response.
   */
  private void requestAsyncTimeout(BidInfo expected, boolean fromFallback) {
    if (expected.getContinuationCompleted().get()
        || !expected.getTimeoutQueued().compareAndSet(false, true)) {
      return;
    }
    try {
      timeoutCompletion.execute(() -> runIsolated("BID timeout completion", expected.getName(),
          () -> expireTicket(expected.getName(), expected)));
      (fromFallback ? fallbackTimeouts : primaryTimeouts).incrementAndGet();
    } catch (RejectedExecutionException failure) {
      // Only possible while stopping (queue >= capacity, one entry per ticket);
      // the fallback sweep retries every 100 ms.
      expected.getTimeoutQueued().set(false);
      LogManager.getLogger(LogType.ROOT).error("BID timeout completion rejected: " + expected.getName(), failure);
    }
  }

  private void expireTicket(String key, BidInfo expected) {
    BidInfo[] timedOut = { null };

    getBidInfoList().computeIfPresent(key, (k, cur) -> {
      if (cur == expected && cur.getStatus() == BidStatus.WAIT) {
        timedOut[0] = cur;
        return null;
      }
      return cur;
    });

    BidInfo bidInfo = timedOut[0];

    if (bidInfo == null) {
      return;
    }
    bidInfo.setStatus(BidStatus.TIMEOUT);
    if (bidInfo.getAsyncCallback() != null) {
      asyncTimedOut.incrementAndGet();
      // How long after its deadline the timeout answer actually starts
      // (queueing behind busy completion workers shows up here).
      long lagMillis = TimeUnit.NANOSECONDS
          .toMillis(System.nanoTime() - bidInfo.getDeadlineNanos());
      if (lagMillis >= 0) {
        lastTimeoutLagMillis.set(lagMillis);
        maxTimeoutLagMillis.accumulateAndGet(lagMillis, Math::max);
      }
    }
    LogManager.getLogger(LogType.ROOT).info("Bid Timeout : " + key);

    try {
      Exchange exchange = bidInfo.getBeforeExchange();

      applyBidTimeoutResult(exchange);

      bidInfo.setStatus(BidStatus.TIMEOUT);
      bidInfo.setAfterExchange(exchange);
    } finally {
      // always, otherwise the waiter blocks forever because
      // the entry is already gone from bidInfoList.
      workNotify(bidInfo);
    }
  }

  /**
   * The BID timeout answer ("Transaction processing is delayed. Please wait.",
   * MCA_BID_TIMEOUT). Shared by the real timeout and by every case where an
   * already-accepted (dummy ack) transaction cannot be parked, so the channel
   * never sees a hard failure for something the host is still processing.
   */
  private void applyBidTimeoutResult(Exchange exchange) {
    Throwable throwable = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Throwable.class);

    exchange.setProperty(Exchange.EXCEPTION_CAUGHT,
        new IBKExceptionMCA(ErrorType.MCA_BID_TIMEOUT, "Bid Timeout Exception", throwable));

    Message message = exchange.getIn();

    String bizCode = message.getHeader(ConstantCode.BIZ_CODE, String.class);

    if (bizCode == null || bizCode.length() != 3)
      bizCode = InfraType.MCA.name();

    String errCd = ConstantCode.ERROR_HEAD + InfraType.MCA.name() + (bizCode)
        + ErrorType.MCA_BID_TIMEOUT.getErrorCode();

    message.setHeader(ConstantCode.ERR_SPOT, InfraType.MCA);
    message.setHeader(ConstantCode.ERR_CODE, errCd);
    message.setHeader(ConstantCode.ERR_MSG, "Transaction processing is delayed. Please wait.");

    ErrorUtil.setErrorMessage(exchange);

    // Result Set
    exchange.getOut().copyFrom(exchange.getIn());
  }

  /**
   * BID release (RCV_CONFIRM_BID) or real response received.
   */
  public ReleaseResult bidResult(String key, Exchange exchange) throws IBKExceptionMCA {
    BidInfo[] waiter = { null };
    ReleaseResult[] result = { ReleaseResult.NOT_FOUND };

    try {
      getBidInfoList().compute(key, (k, cur) -> {
        if (cur == null) {
          return null;
        }

        switch (cur.getStatus()) {
        case WAIT:
          waiter[0] = cur;
          result[0] = ReleaseResult.DELIVERED;
          return null;
        case PENDING:
          // copy(): the release route keeps running after this call
          cur.setAfterExchange(exchange.copy());
          cur.setStatus(BidStatus.RELEASED);
          result[0] = ReleaseResult.PARKED;
          return cur;
        default:
          result[0] = ReleaseResult.DUPLICATE;
          return cur;
        }
      });
    } catch (Exception e) {
      throw new IBKExceptionMCA(ErrorType.MCA_BID, "Bid Result Error : " + e.getMessage(), e);
    }

    if (waiter[0] != null) {

      if (waiter[0].getAsyncCallback() == null) {
        ibkTimeoutBid.remove(key);
      } else {
        asyncDelivered.incrementAndGet();
      }

      complete(waiter[0], exchange);
    }

    if (result[0] == ReleaseResult.NOT_FOUND) {
      releaseNotFound.incrementAndGet();
    }

    LogManager.getLogger(LogType.ROOT).info("Bid Result : " + key + " / " + result[0]);

    return result[0];
  }

  private void complete(BidInfo bidInfo, Exchange afterExchange) throws IBKExceptionMCA {
    try {
      bidInfo.setStatus(BidStatus.COMPLETE);
      bidInfo.setAfterExchange(afterExchange);

      bidInfo.getBeforeExchange().getIn().setBody(afterExchange.getIn().getBody());
    } catch (Exception e) {
      throw new IBKExceptionMCA(ErrorType.MCA_BID, "Bid Result Error : " + e.getMessage(), e);
    } finally {
      // ?? ?? ??(Notify)
      // Office baseline semantics: the releasing thread (Adapter-In executor for a
      // real response, the single BID JMS consumer for RCV_CONFIRM_BID) only
      // signals the waiter. An async waiter is resumed on mca-bid-resume, never
      // here, so the dummy-ack transaction cannot hold the release flow.
      if (bidInfo.getAsyncCallback() == null) {
        workNotify(bidInfo);
      } else {
        resumeAsync(bidInfo);
      }
    }
  }

  private void resumeAsync(BidInfo info) {
    long dispatchedNanos = System.nanoTime();
    try {
      releaseResume.execute(() -> {
        long lagMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - dispatchedNanos);
        lastResumeLagMillis.set(lagMillis);
        maxResumeLagMillis.accumulateAndGet(lagMillis, Math::max);
        runIsolated("BID release resume", info.getName(), () -> finishContinuation(info, false));
      });
    } catch (RejectedExecutionException rejected) {
      // Only while stopping (queue >= capacity, one resume per ticket): answer
      // here rather than lose it; the ticket is already out of bidInfoList.
      LogManager.getLogger(LogType.ROOT).warn("BID release resume rejected, resuming on releasing thread : " + info.getName());
      runIsolated("BID release resume", info.getName(), () -> finishContinuation(info, false));
    }
  }

  public void workWait(BidInfo info) {
    try {
      info.getFuture().get();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException e) {
      LogManager.getLogger(LogType.ROOT)
          .info("Bid wait completed exceptionally : " + info.getName() + " / " + e.getCause());
    }
  }

  public void workNotify(BidInfo info) {
    if (info.getAsyncCallback() == null) {
      info.getFuture().complete(null);
      return;
    }
    finishContinuation(info, false);
  }

  private static ScheduledThreadPoolExecutor createDeadlineWatchdog() {
    ScheduledThreadPoolExecutor executor =
        new ScheduledThreadPoolExecutor(1, r -> {
          Thread thread = new Thread(r, "mca-bid-deadline");
          thread.setDaemon(true);
          return thread;
        });
    executor.setRemoveOnCancelPolicy(true);
    executor.setExecuteExistingDelayedTasksAfterShutdownPolicy(false);
    return executor;
  }

  private final ScheduledThreadPoolExecutor deadlineWatchdog = createDeadlineWatchdog();

  static final int DEFAULT_ASYNC_CAPACITY = 512;

  private volatile int asyncCapacityLimit = DEFAULT_ASYNC_CAPACITY;
  private volatile Semaphore asyncCapacity = new Semaphore(DEFAULT_ASYNC_CAPACITY);

  /**
   * Maximum number of suspended dummy-ack continuations per node
   * (integrator.config.bid-async-capacity, default 512). Startup only: the
   * semaphore cannot be swapped while permits are in use.
   */
  @Value("${integrator.config.bid-async-capacity:512}")
  public void setAsyncCapacity(int capacity) {
    if (capacity < 1) {
      throw new IllegalArgumentException("integrator.config.bid-async-capacity must be >= 1 : " + capacity);
    }
    if (asyncCapacity.availablePermits() != asyncCapacityLimit) {
      throw new IllegalStateException("BID async capacity cannot change while continuations are pending");
    }
    asyncCapacityLimit = capacity;
    asyncCapacity = new Semaphore(capacity);
    rebuildExecutors();
  }

  public int getAsyncCapacityLimit() {
    return asyncCapacityLimit;
  }

  static final int DEFAULT_COMPLETION_THREADS = 8;

  private volatile int completionThreads = DEFAULT_COMPLETION_THREADS;

  /**
   * Workers that run timeout answers, and separately release resumes
   * (integrator.config.bid-completion-threads, default 8, each pool). A timeout answer starts later than its deadline only while this
   * many timeout continuations are already running; see maxTimeoutLagMs /
   * lastTimeoutLagMs in the stats line. Startup only.
   */
  @Value("${integrator.config.bid-completion-threads:8}")
  public void setCompletionThreads(int threads) {
    if (threads < 1) {
      throw new IllegalArgumentException("integrator.config.bid-completion-threads must be >= 1 : " + threads);
    }
    if (asyncCapacity.availablePermits() != asyncCapacityLimit) {
      throw new IllegalStateException("BID completion threads cannot change while continuations are pending");
    }
    completionThreads = threads;
    rebuildExecutors();
  }

  public int getCompletionThreads() {
    return completionThreads;
  }

  private void rebuildExecutors() {
    ThreadPoolExecutor previousCompletion = timeoutCompletion;
    ThreadPoolExecutor previousResume = releaseResume;
    ThreadPoolExecutor previousTimer = timerMaintenance;
    timeoutCompletion = newElasticExecutor("mca-bid-completion", completionThreads, asyncCapacityLimit);
    releaseResume = newElasticExecutor("mca-bid-resume", completionThreads, asyncCapacityLimit);
    timerMaintenance = newTimerMaintenance(asyncCapacityLimit);
    previousCompletion.shutdown();
    previousResume.shutdown();
    previousTimer.shutdown();
  }

  /**
   * Elastic pool (threads start on demand and retire after 60 s idle). Used for
   * timeout answers (mca-bid-completion, one entry per ticket via timeoutQueued)
   * and release resumes (mca-bid-resume, one entry per ticket because the release
   * removes it from bidInfoList). At most capacity tickets exist, so the queue
   * cannot overflow outside shutdown. Separate pools: slow timeout answers never
   * delay real responses and vice versa.
   */
  private ThreadPoolExecutor newElasticExecutor(String name, int threads, int capacity) {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(threads, threads,
        60L, TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(capacity),
        daemonThreads(name), new ThreadPoolExecutor.AbortPolicy());
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }

  // Cumulative counters since start (per node) for the dummy-ack continuation.
  private final AtomicLong asyncSuspended = new AtomicLong();
  private final AtomicLong asyncDelivered = new AtomicLong();
  private final AtomicLong asyncEarlyRelease = new AtomicLong();
  private final AtomicLong asyncTimedOut = new AtomicLong();
  private final AtomicLong asyncNotAccepted = new AtomicLong();
  private final AtomicLong asyncDuplicate = new AtomicLong();
  private final AtomicLong timerTasksDropped = new AtomicLong();
  private final AtomicLong releaseNotFound = new AtomicLong();
  private final AtomicLong primaryTimeouts = new AtomicLong();
  private final AtomicLong fallbackTimeouts = new AtomicLong();
  private final AtomicLong lastTimeoutLagMillis = new AtomicLong();
  private final AtomicLong maxTimeoutLagMillis = new AtomicLong();
  private final AtomicLong lastResumeLagMillis = new AtomicLong();
  private final AtomicLong maxResumeLagMillis = new AtomicLong();
  private final AtomicLong officeWaits = new AtomicLong();
  private volatile String lastLoggedCounters = "";

  private volatile ThreadPoolExecutor timerMaintenance = newTimerMaintenance(DEFAULT_ASYNC_CAPACITY);
  private volatile ThreadPoolExecutor timeoutCompletion =
      newElasticExecutor("mca-bid-completion", DEFAULT_COMPLETION_THREADS, DEFAULT_ASYNC_CAPACITY);
  private volatile ThreadPoolExecutor releaseResume =
      newElasticExecutor("mca-bid-resume", DEFAULT_COMPLETION_THREADS, DEFAULT_ASYNC_CAPACITY);

  /** Continuations currently suspended (holding a capacity permit). */
  public int getAsyncPendingCount() {
    return asyncCapacityLimit - asyncCapacity.availablePermits();
  }

  /** Age in ms of the oldest suspended continuation, 0 when none. */
  public long getOldestAsyncPendingMillis() {
    long now = System.nanoTime();
    long oldest = 0L;
    for (BidInfo info : bidInfoList.values()) {
      if (info.getAsyncCallback() != null && info.getStatus() == BidStatus.WAIT) {
        long startedNanos = info.getDeadlineNanos()
            - TimeUnit.MILLISECONDS.toNanos(info.getDefaultTimeOut());
        oldest = Math.max(oldest, TimeUnit.NANOSECONDS.toMillis(now - startedNanos));
      }
    }
    return oldest;
  }

  private String asyncCounters() {
    return "suspended=" + asyncSuspended.get() + ", delivered=" + asyncDelivered.get()
        + ", earlyRelease=" + asyncEarlyRelease.get() + ", timedOut=" + asyncTimedOut.get()
        + ", notAccepted=" + asyncNotAccepted.get() + ", duplicate=" + asyncDuplicate.get()
        + ", timerTasksDropped=" + timerTasksDropped.get() + ", releaseNotFound=" + releaseNotFound.get()
        + ", primaryTimeouts=" + primaryTimeouts.get() + ", fallbackTimeouts=" + fallbackTimeouts.get()
        + ", maxTimeoutLagMs=" + maxTimeoutLagMillis.get() + ", maxResumeLagMs=" + maxResumeLagMillis.get()
        + ", officeWaits=" + officeWaits.get();
  }

  /** Release resumes queued but not yet started (resume workers busy). */
  public int getResumeQueueDepth() {
    return releaseResume.getQueue().size();
  }

  /** Worst observed delay (ms) between a release and the start of the waiting transaction's continuation. */
  public long getMaxResumeLagMillis() {
    return maxResumeLagMillis.get();
  }

  /** Timeout answers queued but not yet started (completion workers busy). */
  public int getCompletionQueueDepth() {
    return timeoutCompletion.getQueue().size();
  }

  /** Worst observed delay (ms) between a ticket's deadline and the start of its timeout answer. */
  public long getMaxTimeoutLagMillis() {
    return maxTimeoutLagMillis.get();
  }

  /** One-line snapshot for logs / admin endpoints. */
  public String getAsyncStats() {
    return "pending=" + getAsyncPendingCount() + "/" + asyncCapacityLimit
        + ", oldestPendingMs=" + getOldestAsyncPendingMillis() + ", " + asyncCounters()
        + ", lastTimeoutLagMs=" + lastTimeoutLagMillis.get()
        + ", completionQueue=" + getCompletionQueueDepth()
        + ", completionActive=" + timeoutCompletion.getActiveCount() + "/" + completionThreads
        + ", lastResumeLagMs=" + lastResumeLagMillis.get()
        + ", resumeQueue=" + getResumeQueueDepth()
        + ", resumeActive=" + releaseResume.getActiveCount() + "/" + completionThreads;
  }

  /**
   * Every 60 s, log the snapshot when something is pending or a counter moved,
   * so a quiet node does not spam the log.
   */
  @PostConstruct
  public void startAsyncStatsLog() {
    deadlineWatchdog.scheduleWithFixedDelay(() -> {
      try {
        String counters = asyncCounters();
        if (getAsyncPendingCount() > 0 || !counters.equals(lastLoggedCounters)) {
          lastLoggedCounters = counters;
          LogManager.getLogger(LogType.ROOT).info("Bid async stats : " + getAsyncStats());
        }
      } catch (RuntimeException failure) {
        LogManager.getLogger(LogType.ROOT).warn("Bid async stats failed", failure);
      }
    }, 60L, 60L, TimeUnit.SECONDS);
  }

  private final Object asyncLifecycle = new Object();
  private volatile boolean asyncStopping;

  public boolean bidStartAsync(BidInfo incoming, AsyncCallback callback)
      throws Exception {
    // Set before anything can suspend the exchange; read on the same Netty
    // event loop by BidSafeHttpClientChannelHandler after this call returns.
    incoming.getBeforeExchange().setProperty(BID_CONTINUATION, Boolean.TRUE);
    String key = incoming.getName();
    incoming.setAsyncCallback(callback);
    incoming.setAsyncMdc(MDC.getCopyOfContextMap());
    incoming.setStatus(BidStatus.WAIT);
    incoming.setDeadlineNanos(System.nanoTime()
        + TimeUnit.MILLISECONDS.toNanos(incoming.getDefaultTimeOut()));
    BidInfo[] early = { null };
    BidInfo[] duplicate = { null };
    String[] notAccepted = { null };
    synchronized (asyncLifecycle) {
      bidInfoList.compute(key, (k, current) -> {
      if (current != null && incoming.getBeforeExchange().getProperty("MCA_BID_OWNER") != null
          && incoming.getBeforeExchange().getProperty("MCA_BID_OWNER") != current) {
        duplicate[0] = current;
        return current;
      }
      if (current == null || current.getStatus() == BidStatus.PENDING) {
        // A permit is only needed to suspend; a parked release (below) is
        // completed synchronously even when the node is saturated or stopping.
        if (asyncStopping) {
          notAccepted[0] = "BID service is stopping";
          return current;
        }
        if (!asyncCapacity.tryAcquire()) {
          notAccepted[0] = "BID async capacity exhausted (" + asyncCapacityLimit + ")";
          return current;
        }
        incoming.setAsyncPermitOwned(true);
        return incoming;
      }
      if (current.getStatus() == BidStatus.RELEASED) {
        early[0] = current;
        return null;
      }
      duplicate[0] = current;
      return current;
      });
    }
    if (duplicate[0] != null) {
      asyncDuplicate.incrementAndGet();
      throw new IBKExceptionMCA(ErrorType.MCA_BID, "Duplicate BID dummy: " + key);
    }
    if (notAccepted[0] != null) {
      return respondAsBidTimeout(incoming, notAccepted[0]);
    }
    if (early[0] != null) {
      asyncEarlyRelease.incrementAndGet();
      LogManager.getLogger(LogType.ROOT).info("Bid release arrived before dummy ack, complete without wait : " + key);
      incoming.setStatus(BidStatus.COMPLETE);
      incoming.setAfterExchange(early[0].getAfterExchange());
      incoming.getBeforeExchange().getIn().setBody(early[0].getAfterExchange().getIn().getBody());
      bidInfoList.remove(key, early[0]);
      finishContinuation(incoming, true);
      return true;
    }
    try {
      incoming.setFallbackDeadline(deadlineWatchdog.scheduleWithFixedDelay(() -> {
        // Never let this throw: a periodic task that throws is silently cancelled
        // and the ticket would lose its independent deadline.
        try {
          if (bidInfoList.get(key) == incoming
              && System.nanoTime() - incoming.getDeadlineNanos() >= 0) {
            requestAsyncTimeout(incoming, true);
          }
        } catch (Throwable failure) {
          LogManager.getLogger(LogType.ROOT).error("BID fallback deadline check failed : " + key, failure);
        }
      }, incoming.getDefaultTimeOut(), 100L, TimeUnit.MILLISECONDS));
      if (incoming.getContinuationCompleted().get()) {
        incoming.getFallbackDeadline().cancel(false);
      }
    } catch (RuntimeException failure) {
      if (bidInfoList.remove(key, incoming)) {
        incoming.setAsyncPermitOwned(false);
        asyncCapacity.release();
        LogManager.getLogger(LogType.ROOT).error("BID deadline registration failed : " + key, failure);
        return respondAsBidTimeout(incoming, "BID deadline registration failed");
      }
      asyncSuspended.incrementAndGet();
      return false;
    }
    asyncSuspended.incrementAndGet();
    String timerKey = key + ":async:" + UUID.randomUUID().toString();
    incoming.setAsyncTimerKey(timerKey);
    // Arm the primary IBKTimeout off this (Netty IO) thread. Runs on the FIFO
    // timer thread, so a cleanup enqueued by a completion always runs after it;
    // skipped when the ticket already completed.
    submitTimerTask("Primary BID timer arm", key, () -> {
      if (incoming.getContinuationCompleted().get()) {
        return;
      }
      try {
        ibkTimeoutBid.put(timerKey, incoming, incoming.getDefaultTimeOut());
      } catch (Exception failure) {
        LogManager.getLogger(LogType.ROOT).warn("Primary BID timer unavailable; fallback deadline armed: " + key);
      }
    });
    // Inbound channel close deliberately does NOT end the ticket (office
    // baseline semantics): it keeps waiting for the release or the deadline, so
    // a release arriving after the client disconnected is still DELIVERED
    // instead of being reported as a BID timeout + "bidInfo is null".
    return false;
  }

  /**
   * The host already accepted this transaction (dummy ack) but the node cannot
   * park it. Answer exactly like a BID timeout (MCA_BID_TIMEOUT, "please wait")
   * instead of a hard MCA_BID error, so the channel does not treat it as failed
   * and invite a retry (double transfer risk). Completes synchronously.
   */
  private boolean respondAsBidTimeout(BidInfo incoming, String reason) {
    asyncNotAccepted.incrementAndGet();
    LogManager.getLogger(LogType.ROOT)
        .warn("BID continuation not accepted (" + reason + "), answered as BID timeout : " + incoming.getName());
    Exchange exchange = incoming.getBeforeExchange();
    applyBidTimeoutResult(exchange);
    incoming.setStatus(BidStatus.TIMEOUT);
    incoming.setAfterExchange(exchange);
    if (incoming.getContinuationCompleted().compareAndSet(false, true)) {
      incoming.getAsyncCallback().done(true);
    }
    return true;
  }

  private void finishContinuation(BidInfo info, boolean synchronous) {
    if (!info.getContinuationCompleted().compareAndSet(false, true)) {
      return;
    }
    Map<String, String> previous = MDC.getCopyOfContextMap();
    // Non-blocking: ScheduledFuture.cancel only touches the watchdog queue.
    if (info.getFallbackDeadline() != null) {
      info.getFallbackDeadline().cancel(false);
    }
    try {
      if (info.getAsyncMdc() == null) {
        MDC.clear();
      } else {
        MDC.setContextMap(info.getAsyncMdc());
      }
      info.getAsyncCallback().done(synchronous);
    } finally {
      if (info.isAsyncPermitOwned()) {
        info.setAsyncPermitOwned(false);
        asyncCapacity.release();
      }
      if (previous == null) {
        MDC.clear();
      } else {
        MDC.setContextMap(previous);
      }
      // Primary IBKTimeout cleanup takes the DefaultTimeoutMap lock (also held
      // by its purge). It is best effort and must never gate the response or
      // the permit, so it is queued AFTER both, on the timer thread.
      String timerKey = info.getAsyncTimerKey();
      if (timerKey != null) {
        submitTimerTask("Primary BID timer cleanup", info.getName(), () -> ibkTimeoutBid.remove(timerKey));
      }
    }
  }

  /**
   * One FIFO thread for every primary IBKTimeout put/remove of async tickets.
   * Bounded queue; when full (only if the timer lock is stuck for a long time)
   * the task is dropped and counted: a dropped arm leaves the fallback deadline
   * in charge, a dropped cleanup lets the entry expire on its own (bidTimeout
   * ignores completed tickets).
   */
  private ThreadPoolExecutor newTimerMaintenance(int capacity) {
    ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1,
        60L, TimeUnit.SECONDS,
        new ArrayBlockingQueue<Runnable>(Math.max(64, capacity * 4)),
        daemonThreads("mca-bid-timer"), (task, pool) -> timerTasksDropped.incrementAndGet());
    executor.allowCoreThreadTimeOut(true);
    return executor;
  }

  private void submitTimerTask(String what, String key, Runnable task) {
    timerMaintenance.execute(() -> runIsolated(what, key, task));
  }

  /**
   * Runs one ticket's completion so that its failure is logged and cannot stop
   * the caller from serving other tickets (shutdown drain, completion workers).
   * Errors are logged and rethrown.
   */
  private void runIsolated(String what, String key, Runnable task) {
    try {
      task.run();
    } catch (RuntimeException failure) {
      LogManager.getLogger(LogType.ROOT).error(what + " failed : " + key, failure);
    } catch (Error failure) {
      LogManager.getLogger(LogType.ROOT).error(what + " failed : " + key, failure);
      throw failure;
    }
  }

  private static ThreadFactory daemonThreads(String name) {
    return r -> {
      Thread thread = new Thread(r, name);
      thread.setDaemon(true);
      return thread;
    };
  }

  static final int SHUTDOWN_DRAIN_THREADS = 4;

  private static void awaitUntil(ExecutorService executor, long deadlineNanos)
      throws InterruptedException {
    long remaining = deadlineNanos - System.nanoTime();
    if (remaining > 0) {
      executor.awaitTermination(remaining, TimeUnit.NANOSECONDS);
    }
  }

  /** Upper bound (ms) that shutdownAsync waits for suspended continuations to be answered. */
  long shutdownDrainMillis = 10000L;

  /**
   * Answers every still-suspended continuation as a BID timeout, each in
   * isolation (one failing or hanging callback cannot stop the others), waits
   * at most shutdownDrainMillis, and always stops the owned executors.
   */
  @PreDestroy
  public void shutdownAsync() {
    synchronized (asyncLifecycle) {
      asyncStopping = true;
    }
    ExecutorService drain = null;
    long drainDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(shutdownDrainMillis);
    try {
      deadlineWatchdog.shutdown();
      List<BidInfo> pending = new ArrayList<>();
      for (BidInfo info : bidInfoList.values()) {
        if (info.getAsyncCallback() != null) {
          pending.add(info);
        }
      }
      if (!pending.isEmpty()) {
        drain = Executors.newFixedThreadPool(
            Math.min(pending.size(), SHUTDOWN_DRAIN_THREADS), daemonThreads("mca-bid-shutdown"));
        for (BidInfo info : pending) {
          drain.execute(() -> runIsolated("BID shutdown expiry", info.getName(),
              () -> expireTicket(info.getName(), info)));
        }
        drain.shutdown();
        awaitUntil(drain, drainDeadline);
      }
      // Releases and timeouts already handed over keep running until the same deadline.
      releaseResume.shutdown();
      timeoutCompletion.shutdown();
      awaitUntil(releaseResume, drainDeadline);
      awaitUntil(timeoutCompletion, drainDeadline);
      if (getAsyncPendingCount() > 0) {
        LogManager.getLogger(LogType.ROOT).warn("BID shutdown drain exceeded " + shutdownDrainMillis
            + " ms, continuations still pending : " + getAsyncPendingCount());
      }
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    } finally {
      if (drain != null) {
        drain.shutdownNow();
      }
      releaseResume.shutdownNow();
      timeoutCompletion.shutdownNow();
      timerMaintenance.shutdown();
    }
  }
}
