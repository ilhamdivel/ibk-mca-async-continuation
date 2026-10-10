# Response to the Re-audit of `ec28074`

| Item | Value |
|---|---|
| Date | 2026-10-10 |
| Re-audit input | [`REAUDIT_REPORT_GPT_ec28074.md`](REAUDIT_REPORT_GPT_ec28074.md) (verbatim copy of the report received) |
| Revision re-audited | `ec28074` (branch `audit-fixes`) |
| Response commits | `b7f346d` (R1), `11608eb` (R2), `3dc1cc8` (R3), `94ea233` (bridge HTTP regression), `819bdb9` (docs), `dbef4c3` (scope rule: releasing threads only signal), this documentation commit |
| Responder | Claude Opus 5.5 (Claude Code), on behalf of IBK Global IT Operation |
| Historical report | [`AUDIT_REPORT_BID_CONTINUATION.md`](AUDIT_REPORT_BID_CONTINUATION.md) describes the state at `ec28074` and is kept unchanged as history. **This document is the current state.** |

## 1. Summary

- Every new finding in the re-audit was **reproduced before any change**: R1, R2 and R3 each 3/3 runs on `ec28074`, with output matching the re-audit, plus the two pre-existing `MCABidProcess`/`MCABidBean` defects.
- R1, R2 and R3 are fixed with regression tests. The same probes now pass on the new HEAD (section 3).
- The R3 completion latency now has an explicit, tested and measured bound. It is not just a bigger pool (section 4).
- The bridge-topology starvation (known scope, phase 2) is now reproducible through real HTTP from the committed tests.
- The two pre-existing type-5 BID defects are **confirmed but not changed**. They alter business routing of terminal BIDs, which `AGENTS.md` forbids without a business decision (section 6). IBK confirmed: leave them.
- **Scope rule (IBK, 2026-10-10):** the async change may only stop the dummy ack from holding other transactions; no other flow may change. Checking that rule found one more deviation: the releasing thread (real response, RCV_CONFIRM_BID on the single JMS consumer) ran the waiting transaction's continuation. Fixed in `dbef4c3`; compliance matrix in section 4a.
- Status: still **not production-approved**. The office acceptance criteria 2–9 of the re-audit and phase 2 remain open (section 9).

## 2. Claim-by-claim verdict

| Re-audit item | Verdict | Action |
|---|---|---|
| R1 throwing continuation aborts shutdown drain | **Confirmed** | Fixed `b7f346d` |
| R2 fallback completion waits on the primary timer lock | **Confirmed** (and the primary `put` also ran on the Netty IO thread) | Fixed `11608eb` |
| R3 completion-worker saturation defeats the deadline | **Confirmed** | Bounded and measured, `3dc1cc8` |
| Known scope: Adapter-In blocked through `MCAGcbComBean` | Agreed | Phase 2. HTTP starvation regression added in `94ea233` |
| "Capacity below pool size does not guarantee a free executor" | Agreed | Stated in docs. The new test proves the pinning |
| Accepted fixes list | Agreed | — |
| Missing `MCA_BID_OWNER` can claim another request's ticket | Agreed, pre-existing | Unchanged |
| Shallow `exchange.copy()` shares the business body | Agreed, pre-existing | Unchanged (must preserve the wire contract) |
| Stale external release correlated only by business key | Agreed, pre-existing | Unchanged |
| `MCABidHandle.java:98` blocks the type-5 JMS consumer | Agreed, pre-existing | Unchanged |
| Cross-node ownership unresolved | Agreed | Unchanged |
| Admission covers only suspended continuations | Agreed | Documented |
| `MCABidProcess.java:42` `== "5"` | **Confirmed** (section 6) | Decision required |
| `MCABidBean.java:48` validation | **Confirmed** (section 6) | Decision required |
| Documentation follow-ups | Agreed | Done (section 8) |

## 3. Reproduction before and after

The probes use production classes. R2 uses the **real** `IBKTimeout` and Camel 2.21.1 `DefaultTimeoutMap`, whose private `lock` is held from another thread. They ran under JDK 1.8.0_261 with the real Camel 2.21.1 / Netty 4.1.22 jars (harness in section 8), three runs each.

| Probe | `ec28074` (3/3 identical) | HEAD after fixes (3/3 identical) |
|---|---|---|
| R1: 3 tickets, callbacks throw, `shutdownAsync()` | `exception=Injected continuation failure callbacks=1 map=2 permitsHeld=2 watchdogShutdown=true completionShutdown=false` | `exception=none callbacks=3 map=0 permitsHeld=0 watchdogShutdown=true completionShutdown=true` |
| R2: primary map lock held, 50 ms ticket | `observedMs=300 callbackCompleted=false status=TIMEOUT terminalClaimed=true mapContains=false permitsHeld=1` | `observedMs=300 callbackCompleted=true status=TIMEOUT terminalClaimed=true mapContains=false permitsHeld=0` |
| R3: 2 slow timeout callbacks, third ticket 30 ms | `observedMs=340 completed=false status=WAIT timeoutQueued=true mapContains=true completionQueue=1` | `observedMs=340 completed=true status=TIMEOUT mapContains=false completionQueue=0` |

The R3 probe uses two slow callbacks. With the new default of 8 workers it no longer saturates; saturation itself is covered by `BidCompletionTest` (section 4).

## 4. Fixes

### R1 — shutdown drain (`b7f346d`)

- `shutdownAsync` (`BidManager.java:739`) snapshots suspended tickets and expires each on a small daemon pool `mca-bid-shutdown` (at most 4 threads). Every expiry is wrapped in `runIsolated()` (`BidManager.java:709`): a failure is logged and the others continue.
- The caller waits at most `shutdownDrainMillis` (10 s). After that the drain pool is interrupted and the pending count is logged. Executor shutdown is in a `finally` block.
- Timer-driven completions are also run through `runIsolated()`. The periodic fallback check catches everything (`BidManager.java:589`), because `ScheduledThreadPoolExecutor` silently cancels a periodic task that throws.
- Tests: `BidFaultTest.shutdownIsolatesFailingCallbacksAndDrainsEveryTicket` (3 throwing callbacks → 3 answers, map empty, permits back, deadlines cancelled, every owned executor stopped) and `shutdownDrainIsBoundedWhenACallbackNeverReturns`.
- Residual (documented): if 4 or more callbacks hang, tickets queued behind them stay unanswered after the bound. The bound protects the shutdown caller; it cannot answer a continuation that never returns.

### R2 — primary timer off the critical path (`11608eb`)

- All primary `IBKTimeout` put/remove for async tickets run on one FIFO daemon thread `mca-bid-timer` (`newTimerMaintenance`, `BidManager.java:691`; arm at `:611`, cleanup at `:679`). Its queue is bounded (4× capacity, at least 64). Dropped tasks are counted as `timerTasksDropped`.
- The dummy-ack (Netty IO) thread no longer touches the `DefaultTimeoutMap` lock.
- `finishContinuation` (`:648`) order is: claim → cancel fallback (non-blocking) → callback → release permit → restore MDC → queue primary cleanup. Nothing before the answer or the permit takes the timer lock.
- FIFO keeps arm-then-cleanup order, and an arm is skipped if the ticket already completed. A dropped arm leaves the fallback deadline in charge. A dropped cleanup lets the entry expire, and `bidTimeout` ignores completed tickets.
- Test: `BidPrimaryTimerTest` holds the real lock. Two dummy acks register in under 500 ms, a release answers immediately, the 50 ms fallback answers, and permits return. After unlock no primary entry is left behind.
- Note: the office baseline's release path also removed the primary entry before notifying (`bidResult`). This is now gone for async tickets; the legacy blocking path is unchanged and unused by `MCAInbound`.

### R3 — bounded, measured timeout answers (`3dc1cc8`)

- The completion pool is elastic: `completionThreads` workers (default 8, `integrator.config.bid-completion-threads`, startup only), created on demand and retired after 60 s idle (`newCompletionExecutor`, `:424`).
- Its queue is sized to the async capacity. Each ticket is queued at most once (`timeoutQueued`) and at most `capacity` tickets exist, so the queue cannot overflow outside shutdown. The previous fixed 512 queue could overflow with capacity > 512.
- **Bound.** A timeout answer starts at its deadline plus timer granularity (≤ 100 ms fallback, ≤ 200 ms primary purge), unless `completionThreads` timeout continuations are already running. Then it waits for the next free worker and is never dropped. Worst-case start delay ≈ (queued ahead ÷ workers) × continuation time.
- With the deployed `MCAGcbComBean` bridge, a timeout continuation on a worker runs only `ProcessAfterMCA` + mapping before handing back to the parked Adapter-In thread, so continuation time is short. It must be measured in the office.
- **Ownership semantics (deliberate).** While queued the ticket stays `WAIT`, so a real response that still arrives wins and is delivered (better than "please wait"). The queued timeout is then a no-op. Terminal ownership is still decided atomically in `ConcurrentHashMap.compute`, and the callback still runs once.
- **Measured** (stats line and getters):
  - `lastTimeoutLagMs` / `maxTimeoutLagMs`: deadline → start of the timeout answer
  - `completionQueue`, `completionActive=x/threads`
  - `primaryTimeouts` / `fallbackTimeouts`: which mechanism fired
  - `releaseNotFound`: late or unknown releases
- Tests, `BidCompletionTest`:
  - 7 slow + 1 → the 8th answers at its deadline;
  - 2 workers saturated → timeouts queue (depth 2, still `WAIT`), a release still wins once, and the queued timeout answers when workers free up with lag ≥ 250 ms reported;
  - capacity 600 → 600 timeouts each answered exactly once;
  - thread count is validated and startup-only.
- Spring wiring: `BidCapacityConfigTest`.

### 4a. Scope rule: releasing threads only signal (`dbef4c3`)

**Rule:** the change exists only so that a dummy ack does not hold other transactions. Normal, local, ITRO00000035, type-5 terminal BID, type-6 RCV_CONFIRM_BID, real-response, approval and error flows must keep their office behaviour.

**Deviation found.** Before `dbef4c3`, `bidResult` ran the waiting transaction's continuation **inline** on the releasing thread. That thread is either the Adapter-In executor handling GCB's real response (`ProcessPreMCA.java:100`) or the single BID JMS consumer handling RCV_CONFIRM_BID (`MCABidHandle.java:158`). The release request therefore waited for another transaction's post-processing, could receive its exception, and (if that continuation blocked) was blocked with it. The office baseline only called `CompletableFuture.complete()` and returned.

**Fix.** `complete()` still hands the response body over and sets `COMPLETE` on the releasing thread, exactly as the baseline did. For an async waiter it then queues the continuation on a separate elastic pool `mca-bid-resume` and returns. Details:
- The pool is separate from `mca-bid-completion`, so slow timeout answers cannot delay real responses and vice versa.
- Its queue equals the capacity: one resume per ticket, because the release removes the ticket from the map.
- On rejection (only while stopping) the continuation is resumed inline rather than lost.
- New metrics: `lastResumeLagMs` / `maxResumeLagMs`, `resumeQueue`, `resumeActive`.

**Proof.** `BidReleaseIsolationTest` fails on `819bdb9` with `TimeoutException` in both tests and passes on `dbef4c3`:
- the releasing thread must return within 1 s while the continuation is blocked and failing;
- the real `MCABidHandle` handling RCV_CONFIRM_BID on a "JMS consumer" thread must return within 1 s.

The earlier probe of the old code deadlocked instead of failing.

**Compliance matrix (against `office-baseline`):**

| Flow | Entry | Office behaviour kept? | Evidence |
|---|---|---|---|
| Normal response (otptTmgtDcd ≠ 4) | `MCAInbound` → `MCAWorkAfterAsync` | Yes: synchronous `done(true)`, no timer/map access, same field reads as `MCAWorkAfterProcess` | `normalResponseRemainsSynchronous` |
| Local (`sysEnvrInfoDcd = L`) | same | Yes, same as normal | code path identical |
| ITRO00000035 BID HTTP | `MCAWorkPreProcess.bidHttp` | Yes: returns before pre-registration and before the after-processor (fault) | unchanged code |
| Type-5 terminal BID | SEDA → JMS → `MCABidProcess` → `MCABidHandle.bidWork` | Yes, untouched (pre-existing defects left as is) | unchanged code |
| Type-6 RCV_CONFIRM_BID | `MCABidHandle.bidWorkWait` → `bidResult` | Yes since `dbef4c3`: the consumer only signals | `rcvConfirmBidOnTheSingleJmsConsumerIsNotHeldByTheWaitingTransaction` |
| Real response (0/R/K) | `ProcessPreMCA` → `bidResult` → `ROUTE_STOP` | Yes since `dbef4c3`: same return value, same `NOT_FOUND` → "bidInfo is null", only signals | `releasingThreadOnlySignalsEvenWhileTheContinuationIsBlocked` |
| Release before dummy ack | `bidResult` → PARKED; dummy completes it | Yes: the dummy-ack thread completes its own transaction synchronously | `releaseFirstCompletesSynchronously` |
| Approval / error paths | `onException` → `ErrorCatchMCA` | Yes: error codes unchanged; overload answered as the existing BID timeout | `BidFaultTest` |
| **Dummy ack (in scope)** | `MCAWorkAfterAsync` → `bidStartAsync` | **Changed by design**: the GCB reply IO worker is no longer parked | `BidTopologyTest` |

Additive only (no flow change):
- an exchange property `MCA_BID_OWNER` on BID-key requests;
- the log lines `Bid Timeout`, `BID continuation not accepted`, and the 60 s stats line;
- daemon threads `mca-bid-*`.

Known limitation, unchanged (phase 2): with the deployed `MCAGcbComBean` bridge the Adapter-In thread of the waiting transaction itself stays parked.

### Bridge topology through real HTTP (`94ea233`)

`BidTopologyTest.prodBridgeStarvesAnHttpReleasePinnedToTheParkedExecutor` uses one Adapter-In executor and the real `MCAGcbComBean`:
1. The release sent over real netty4-http is not served within 2 s.
2. The original completes only after a direct release.
3. The starved HTTP release is then served and finds no ticket (`NOT_FOUND`).

This reproduces the first audit's pool-size-one experiment from committed code.

## 5. Threads owned by `BidManager` (current)

| Thread | Count | Work | Must never |
|---|---|---|---|
| `mca-bid-deadline` | 1 | fallback deadline checks (every 100 ms after a ticket's deadline), 60 s stats line | run continuations or take the timer lock |
| `mca-bid-completion` | 0..`completionThreads` (default 8) | timeout answers (continuation) | — |
| `mca-bid-resume` | 0..`completionThreads` (default 8) | continuation after a release (real response / RCV_CONFIRM_BID) | run on the releasing thread |
| `mca-bid-timer` | 0..1 | primary `IBKTimeout` put/remove | gate an answer or a permit |
| `mca-bid-shutdown` | ≤ 4, shutdown only | drain | hold the caller beyond `shutdownDrainMillis` |

The thread that delivers a release (Adapter-In executor or BID JMS consumer) only signals, as in the office baseline; the waiting transaction continues on `mca-bid-resume`.

## 6. Pre-existing type-5 BID defects (confirmed, not changed)

**`MCABidProcess.java:42`** routes on `p.getMessage().getHeader(BID_WORK_TYPE, String.class) == "5"`, an identity comparison. The header comes from the JSON-parsed telegram (`MCABidBean.bidWorkCheck`).
- Probe with the project's Jackson 2.8: parsed value `.equals("5")` = `true`, `== "5"` = `false`.
- So `afterProcessBid` and the mapping in the `choice` are effectively never applied to terminal (type-5) BIDs.
- This has been the production behaviour since the baseline.

**`MCABidBean.java:48`**, `isEmpty(v) && !(v.equals("5") || v.equals("6"))`, evaluated verbatim:

| Value | Result |
|---|---|
| `null` | `NullPointerException`, then wrapped as `MCA_BID` |
| `""` | `MCA_BID` error |
| `"4"`, `"9"` | accepted (validation bypassed) |
| `"5"`, `"6"` | accepted |

**Why not changed:** fixing either one changes what is sent to terminals on the type-5 path, or which BID messages are rejected. That is a business-routing change (`AGENTS.md`: never modify business routing). It needs confirmation from the business owner and the terminal/channel team on the expected type-5 payload. Proposed separate change once approved:
- `"5".equals(header)`;
- an explicit null-safe allowed set `{5, 6}` with tests for null, empty, unsupported, 5 and 6.

## 7. Behaviour, configuration, metrics (current)

The behaviour table in the historical report (section 8 there) still holds, with these additions:

| Situation | Change |
|---|---|
| Timeout while all completion workers are busy | Answer queued, never dropped; a real response arriving meanwhile wins; lag reported |
| Shutdown with failing or hanging callbacks | Others still answered; caller bounded by `shutdownDrainMillis` |
| Primary timer lock stuck | No effect on dummy ack, release, fallback answer or permits; timer entries catch up later |

| Property | Default | Meaning |
|---|---|---|
| `integrator.config.bid-async-capacity` | 512 | Max suspended continuations per node; also sizes the completion queue |
| `integrator.config.bid-completion-threads` | 8 | Max concurrent timeout answers |

Stats line (every 60 s on `mca-bid-deadline`, only when something is pending or a counter moved):

```
Bid async stats : pending=x/cap, oldestPendingMs=.., suspended=.., delivered=.., earlyRelease=.., timedOut=..,
notAccepted=.., duplicate=.., timerTasksDropped=.., releaseNotFound=.., primaryTimeouts=.., fallbackTimeouts=..,
maxTimeoutLagMs=.., maxResumeLagMs=.., lastTimeoutLagMs=.., completionQueue=.., completionActive=x/threads,
lastResumeLagMs=.., resumeQueue=.., resumeActive=x/threads
```

`delivered` counts the release that won the ticket, not confirmed client receipt (N6 is the closest log evidence; byte-level receipt needs the office test).

## 8. Verification

Test inventory: **36 tests in 9 classes**, all passing (harness runs listed below).

| Class | Tests |
|---|---|
| `BidAsyncTest` | 10 (state machine, real pipeline, races) |
| `BidFaultTest` | 8 (overload, MDC/permit on throw, timer install race, **R1** drain isolation and bound, shutdown race) |
| `BidCompletionTest` | 4 (**R3**) |
| `BidPrimaryTimerTest` | 1 (**R2**, real `DefaultTimeoutMap` lock) |
| `BidObservabilityTest` | 4 (log lines, counters, arm failure) |
| `BidCapacityConfigTest` | 3 (Spring `@Value` for both properties) |
| `BidTopologyTest` | 3 (real `MCAGcbComBean` bridge incl. HTTP starvation, direct wiring) |
| `BidReleaseIsolationTest` | 2 (scope rule: releasing thread and real `MCABidHandle` RCV_CONFIRM_BID only signal) |
| `BidNettyTest` | 1 |

Commands:

```bash
# Native (re-audit environment; needs the Surefire JUnit4 provider 2.18.1 in the local Maven cache)
JAVA_HOME=<jdk8> mvn -o test
# Fallback without Surefire: run every *Test class with JUnitCore
java -cp "target/classes:target/test-classes:<test classpath>" org.junit.runner.JUnitCore \
  com.ibkglobal.integrator.engine.manager.BidAsyncTest com.ibkglobal.integrator.engine.manager.BidFaultTest \
  com.ibkglobal.integrator.engine.manager.BidCompletionTest com.ibkglobal.integrator.engine.manager.BidPrimaryTimerTest \
  com.ibkglobal.integrator.engine.manager.BidObservabilityTest com.ibkglobal.integrator.engine.manager.BidCapacityConfigTest \
  com.ibkglobal.integrator.engine.manager.BidTopologyTest com.ibkglobal.integrator.engine.manager.BidNettyTest \
  com.ibkglobal.integrator.engine.manager.BidReleaseIsolationTest
```

Responder's environment: the offline harness from `AUDIT_REPORT_BID_CONTINUATION.md` section 10 (JDK 8, real Camel 2.21.1 / Netty 4.1.22, Spring 5.3 / Mockito 4 substitutes, three stubbed internal types). Each commit passed 3 consecutive runs before it was made; `819bdb9` passed 10 of 10 consecutive runs (`OK (34 tests)`), and `dbef4c3` passed **10 of 10 consecutive runs, `OK (36 tests)`** each.

The re-audit's native run (Spring 4.3.14, Mockito 1.10.19, cached `ibkglobal-message`) was on `ec28074`. **A native `mvn -o test` on this HEAD is still required.** All new tests use Mockito 1.10-compatible APIs only (`timeout()`, `doAnswer`, `RETURNS_DEEP_STUBS`, `verifyZeroInteractions`).

## 9. Still open (unchanged by this response)

1. Phase 2: remove the `MCAGcbComBean` synchronous bridge for the BID wait. Until then the Adapter-In thread is parked per pending BID and a release can starve on a pinned executor (pinned by `BidTopologyTest`).
2. Re-audit acceptance criteria 2–9:
   - internal artifact checksums;
   - real Spring/JEUS start and active routes;
   - full normal/BID flows through real parsing, mapping and composing;
   - byte-level payload and N1–N6 correlation;
   - mixed load with stack traces, lag and heap;
   - APM/Penta context;
   - rollback/drain rehearsal.
3. Office measurement of `maxTimeoutLagMs` under simultaneous timeouts, to set `bid-completion-threads`.
4. Business decision on the two type-5 BID defects (section 6).

## 10. Checklist for the next review

1. Re-run the three probes of section 3 on HEAD; or derive them from `BidFaultTest`, `BidPrimaryTimerTest` and `BidCompletionTest`.
2. Check every exit of `finishContinuation`/`respondAsBidTimeout`/`shutdownAsync` for exactly-once callback, exactly-once permit release, and no timer-lock acquisition before the answer.
3. Check the FIFO argument for arm/cleanup on `mca-bid-timer` (arm skipped after completion; cleanup always queued after the arm it pairs with).
4. Check the queue-cannot-overflow argument for `mca-bid-completion` (one queue entry per ticket via `timeoutQueued`, at most `capacity` tickets).
5. Challenge the R3 ownership choice (queued timeout lets a later real response win) against operations expectations.
6. Run native `mvn -o test` and report the count (expected 36).
7. Re-check the scope rule (section 4a): no thread other than the dummy-ack transaction's own threads and the `mca-bid-*` pools may execute its continuation.
