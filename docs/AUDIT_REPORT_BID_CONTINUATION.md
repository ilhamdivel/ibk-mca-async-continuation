# Audit Report: MCA BID Async Continuation

| Item | Value |
|---|---|
| Date | 2026-10-10 |
| Auditor | Claude Opus 5.5 (Claude Code), on behalf of IBK Global IT Operation |
| Audited candidate | `95649d8` "feat: add BID async continuation candidate" |
| Baseline | tag `office-baseline` = `d5fa993` |
| Remediation branch | `audit-fixes` (on top of `main` `3b0d297`) |
| Language/runtime | Java 8, Camel 2.21.1, Netty 4.1.22, Spring Boot 1.5.10, JEUS WAR |

**For the next reviewer:** treat every statement here as a claim to verify, not as fact. Each finding names its evidence, the file:line, the test that pins it and how to reproduce it. Evidence that only exists in the office (production logs, deployed route configuration) is quoted in sanitized form and marked as such.

---

## 1. Verdict

| Subject | Verdict |
|---|---|
| Candidate `95649d8` as delivered | **Not production-ready.** The state machine is sound, but the main scope claim is false for the deployed topology and it introduced three risky behaviours (overload hard error, early timeout on channel close, memory-visibility gap) plus an observability regression. |
| Branch `audit-fixes` | **Not production-ready yet, scope-limited.** The behavioural and visibility findings are remediated per the decisions in section 6. It frees the GCB-reply IO worker (the 6 Oct "worker freeze"), but **not** the Adapter-In thread (finding F1, phase 2). Office gates in section 11 remain mandatory. |

No double completion, lost ticket, lock-held callback or permit leak was found in the continuation state machine itself, either by review or by the race tests.

---

## 2. Scope and method

1. Read `AGENTS.md`, `docs/OPUS_AUDIT_HANDOFF.md`, `docs/CLAUDE_OPUS_AUDIT_PROMPT.md`.
2. Diffed `office-baseline..95649d8` (10 files, +802/−5) and read in full: `BidManager`, `BidInfo`, `MCAWorkAfterAsync`, `MCAWorkAfterProcess`, `MCAWorkPreProcess`, `MCAInbound`, `RouteCreateFactory`, `RouteCreateDefault`, `IBKTimeout`, `IBKTimeoutBean`, `ProcessPreMCA`, `ProcessAfterMCA`, `MCAGcbComBean`, `MCABidHandle`, `MCABidProcess`, `MCABidRoute`, `MCADefaultAdapterIn/Out`, `EndpointCreate`, `IBKHttpConsumerInitializer`, `ErrorUtil`, `ErrorType`, `InstanceAdmin`, `RouteManager(Bean)`.
3. Read the Camel 2.21.1 sources from the local Maven artifacts where framework behaviour matters (section 4, F1/F2).
4. Read production logs from node 1 (office, 2026-10-06 09:26–09:30, not in this repo) to establish the **deployed** route wiring.
5. Compiled and ran all tests under JDK 1.8.0_261 against the real Camel/Netty jars (section 10), plus audit-only proof tests. Race suites were repeated (13 runs on the candidate, 10 runs on `audit-fixes` before the last two commits, 3–5 runs after each later commit).
6. Did not modify `main`, did not deploy, did not add peer forwarding or change business routing.

**Limits of the evidence:** the internal Nexus was unreachable. `ibkglobal-message` was compiled from the office SVN source; three unavailable internal types were stubbed; Spring 5.3 jars stood in for 4.3 and Mockito 4 for 1.10. This is compile, unit and route-level evidence. It is **not** an office Maven build, a Spring Boot start on JEUS, or a load test.

---

## 3. Handoff claims versus evidence (candidate `95649d8`)

| Claim in `OPUS_AUDIT_HANDOFF.md` | Result | Evidence |
|---|---|---|
| Dummy-first returns "without a per-request blocked worker" | **False for production wiring** | F1; `BidTopologyTest.prodBridgeFreesIoWorkerButStillParksAdapterInThread` |
| Release and timeout have exactly one terminal owner | True | `ConcurrentHashMap.compute` ownership plus `continuationCompleted` CAS; `concurrentReleaseAndTimeoutCompleteOnce` (100 iterations × 13 runs) |
| Early release stays parked, no ticket overwrite | True | `releaseFirstCompletesSynchronously`, `parkedReleaseCompletesWithRealResponseEvenWhenSaturated` |
| Primary timer failure still times out (fallback) | True | `failedPrimaryTimerStillExpires` |
| No callback while a timer/map lock is held | True | async timer eviction is dispatched to `mca-bid-completion` (`BidManager.java:147`); `bidResult` completes outside `compute` |
| Unique timer key per generation | True | `staleTimerCannotCompleteNewGeneration` |
| Closed channel disposes the continuation | True, but a harmful behaviour change | F4 |
| Bounded admission (512) with defined behaviour | Bounded, but the answer was a hard error | F3 |
| "Passes 10 JUnit tests", "UNCOMMITTED" | Stale | 14 tests existed (the documented command omitted `BidFaultTest`); all 14 passed; the code was committed |
| `MCABidHandle.java:98` `get(60000)` is outside the dummy path | True | it is on the type-5 terminal-BID path (`MCABidProcess`, JMS consumer); still blocks that single consumer up to 60 s (pre-existing) |

---

## 4. Findings

Severity is the impact on a banking transaction path. Status refers to branch `audit-fixes`.

### F1 — Deployed Adapter-In thread stays parked for the whole BID wait (High, OPEN, phase 2)

- **What:** production does not run the archived JSON wiring (`HS30710 -> to direct:M.GCB0.COM0.ROUTE`). Logs show the Adapter-In route calling the BEAN endpoint `MCAGcbComBean.execute`. That bean calls `producer.send(direct:M.GCB0.COM0.ROUTE, exchange)` (`MCAGcbComBean.java:55`), wired by `RouteCreateDefault.java:172-175` (`case BEAN`).
- **Camel 2.21.1 semantics:** a `ProducerTemplate.send` to an async producer is `AsyncProcessorHelper.process` → `AsyncProcessorAwaitManager.await(exchange, latch)` (`camel-core` `AsyncProcessorHelper.java:105-124`, `ProducerCache.java:540-541`). The calling thread blocks until the whole inbound route completes.
- **Effect:**

  | Thread while a dummy ack waits for its release | Office baseline | Candidate / `audit-fixes` |
  |---|---|---|
  | `NettyClientTCPWorker` (GCB reply IO worker) | parked in `BidManager.workWait` | **free** |
  | `NettyEventExecutorGroup` (Adapter-In, via `MCAGcbComBean`) | parked in `AsyncProcessorAwaitManager.await` | **still parked** until release or timeout |

  So the change fixes the IO-worker freeze, but each pending BID still holds one Adapter-In executor thread for up to 100 s. The 512 capacity is therefore not the real ceiling; the netty4-http executor pool is.
- **Evidence:** Appendix A (sanitized production log) and Appendix B (thread dumps). `BidTopologyTest` uses the real `MCAGcbComBean`, `MCAWorkAfterAsync` and `BidManager`.
- **Decision:** separate phase 2 change with its own load test (section 11). Pinned by `BidTopologyTest`; flip its assertion when phase 2 lands.

### F2 — Executor pinning can stall a release behind a parked Adapter-In thread (High, PRE-EXISTING, OPEN)

- netty4-http consumers ignore the custom `serverInitializerFactory` and use `HttpServerInitializerFactory` (`NettyHttpComponent.java:298`). That factory adds the handler on the component `EventExecutorGroup` (`HttpServerInitializerFactory.java:118-120`), which is a `DefaultEventExecutorGroup(maximumPoolSize)` (`NettyComponent.java:164-167`). Netty pins each channel to one executor thread.
- Real responses / BID releases arrive on the same Adapter-In port and are processed on these executor threads (production log: `ProcessPreMCA:93 - 실제응답 시 dummy wait 해제` on `NettyEventExecutorGroup` threads). A release whose connection is pinned to a parked thread waits behind it.
- **Reproduction (audit harness, Adapter-In pool = 1):** baseline and candidate with the `MCAGcbComBean` bridge: the release over HTTP was **stuck >4 s**. Candidate with `to(direct:)`: release **DELIVERED** immediately (`directAdapterInFreesTheOnlyExecutorSoReleaseIsServed`). With pool = 8 the release was delivered (it landed on another thread).
- **Not observed as queueing** in the 6 Oct sample (233 inbound requests; the largest sender-to-MCA delay was 6.3 s, on terminal clocks that may be skewed). It is a load-dependent capacity risk. Phase 2 removes it for the BID wait.

### F3 — Overload/stopping answered with a hard `MCA_BID` error after the host accepted the transaction (High, FIXED `c38b2ac`)

- **Candidate:** `bidStartAsync` threw `IBKExceptionMCA(MCA_BID, "BID service stopping or capacity exhausted")` when the hard-coded `Semaphore(512)` was exhausted, while stopping, or when the fallback deadline could not be armed.
- **Observed (candidate, audit test):** `overload rejection -> ERR_CODE=EGMCCOM01001 ERR_MSG=BID service stopping or capacity exhausted` versus `bid timeout -> ERR_CODE=EMCAMCA01002 ERR_MSG=Transaction processing is delayed. Please wait.` (In the test `BIZ_CODE` was unset; in production the middle segment is the business code.)
- **Risk:** a dummy ack means the host is already processing the transaction (for example BI-FAST). A hard 01001 reads as "failed" and invites a customer retry, which risks a double transfer. The office baseline never rejected; its worst case was the timeout answer.
- **Fix:** `respondAsBidTimeout()` (`BidManager.java:527`) answers exactly like a BID timeout, through the shared `applyBidTimeoutResult()` (`BidManager.java:206`, extracted unchanged from `expireTicket`). The permit is now taken **inside** `compute` and only when the ticket will really suspend (`BidManager.java:445-457`), so a release that is already parked completes with the real response even when the node is saturated or stopping. The capacity is configurable (section 8). Duplicate dummy acks keep the original `MCA_BID` error.
- **Tests:** `saturationAnswersAsBidTimeoutAndReleasedCapacityIsReusable`, `configuredCapacityIsHonouredAndValidated`, `parkedReleaseCompletesWithRealResponseEvenWhenSaturated`, `shutdownResolvesPendingAndAnswersNewRequestsAsTimeout`, `registrationAndShutdownRaceLeavesNoWaiter`, `deadlineArmFailureIsAnsweredAsTimeoutAndNotCountedAsSuspended`, `BidCapacityConfigTest`.

### F4 — Inbound channel close ended the ticket early; the later real release became "bidInfo is null" (Medium, FIXED `d1eac9a`)

- **Candidate:** `MCAWorkPreProcess` stored the inbound channel (`CamelNettyChannelHandlerContext`) as `MCA_BID_ORIGINAL_CHANNEL`. `bidStartAsync` added a `closeFuture` listener that ran the BID TIMEOUT path. In production that channel is the load-balancer connection.
- **Observed (candidate, audit test):** `channel-close: status=TIMEOUT ERR_MSG=Transaction processing is delayed. Please wait. lateRelease=NOT_FOUND`. `ProcessPreMCA.java:100-103` turns `NOT_FOUND` into `IBKExceptionMCA(BID, "bidInfo is null")`.
- **Baseline:** the ticket keeps waiting, the release is DELIVERED, and the final write to the closed channel fails silently. The candidate added a misleading timeout plus a "bidInfo is null" error for a transaction that completed at the host.
- **Decision and fix:** back to baseline semantics. The listener, the `originalChannel`/`closeListener` fields and the channel capture are removed. The ticket still ends at the latest at the 100 s monotonic deadline. Test: `closedChannelKeepsTicketUntilRelease`.

### F5 — Timer handles not safely published between threads (Medium-Low, FIXED `6b10c3d`)

- `bidStartAsync` publishes the ticket in `bidInfoList` first, then arms the timers and checks `continuationCompleted`. The completing thread does the reverse (CAS, then read the timer handles). With `fallbackDeadline`/`asyncTimerKey` as plain fields this "write, then check the other flag" handshake is not sequentially consistent (JMM; x86 store buffering allows it). Both sides can miss each other, and the `scheduleWithFixedDelay(100 ms)` fallback task is then never cancelled. It holds the `BidInfo` and its `Exchange` forever.
- **Fix:** both fields are `volatile` (`BidInfo.java:31,34`). The other async fields (`asyncCallback`, `asyncMdc`, `deadlineNanos`, `asyncPermitOwned`) are written before the ticket is published through `ConcurrentHashMap.compute` and are read via the map. This race cannot be forced deterministically in a unit test; it is a code-level finding.

### F6 — Office RCA log lines dropped and no counters (Medium, FIXED `3a42aff`, `f985e78`)

- The candidate removed `MCAWorkAfterProcess dummyCheck InterfaceID: <id>` and `Bid release arrived before dummy ack, complete without wait : <key>`. Operations used both lines to reconstruct the 25 Sep and 6 Oct BID incidents. No pending, timeout or rejection numbers were observable.
- **Fix:** both lines restored with identical text (`MCAWorkAfterAsync.java:44`, `BidManager.java:476`). Added `Bid Timeout : <key>`, counters, `getAsyncStats()`, and a 60 s stats line (section 8). `f985e78` stops a deadline-arm failure from being counted as both `suspended` and `notAccepted`.
- **Tests:** `BidObservabilityTest` (captures the ROOT logger).

### F7 — Lower-severity notes

| Item | Status |
|---|---|
| An exception thrown by the original exchange's continuation propagates into the thread that delivered the release (`callbackFailureRestoresMdcAndDoesNotLeakCapacity` asserts this by design), so GCB's release request can receive an error response | Open, accepted by the candidate design; rare because Camel catches processor exceptions in the route |
| Duplicate-dummy error text changed (`Duplicate BID dummy: <key>`); code `MCA_BID` unchanged | Accepted |
| Timer-driven completion runs on 2 `mca-bid-completion` threads with a 512 queue; rejection is retried by the fallback sweep | Accepted (with the `MCAGcbComBean` bridge the continuation only runs `ProcessAfterMCA` + mapping before handing back) |
| `MCAWorkAfterAsync.java` uses LF endings while `CLAUDE.md` asks for CRLF Java | Cosmetic, left as committed by the candidate |
| Handoff document stale and over-claiming | Fixed `076ccc1` |

### Pre-existing issues noticed (not introduced by the candidate, not changed)

- A dummy whose exchange has no `MCA_BID_OWNER` (its pre-registration was skipped because the key was in use) can still claim another exchange's PENDING slot (`BidManager.java:445`). Same as baseline.
- The release body is handed to the original exchange through `exchange.copy()`, a shallow copy, so the same `IBKMessage` instance is mutated by the release thread (`ProcessPreMCA` progress number, timeout value, BID IP) while the original composes. With the continuation the window is narrower than baseline (`ProcessAfterMCA` and mapping now run first on the release thread).
- `MCABidHandle.java:98` blocks the single BID JMS consumer for up to 60 s on the type-5 terminal-BID send. Type-6 releases share that queue.
- Cross-node case (dummy on node 2, release on node 1) stays unresolved; the continuation is per JVM.

---

## 5. Verified-correct properties

| Property | Evidence |
|---|---|
| Normal (non-dummy) responses stay synchronous and never touch timer or map | `normalResponseRemainsSynchronous`; `MCAWorkAfterAsync.java:50` |
| Real Camel 2.21.1 pipeline suspends and resumes exactly once | `camelPipelineSuspendsAndResumesAfterRelease` |
| Release landing **during** registration (`callback.done(false)` before `process` returns `false`) routes downstream exactly once with no exception | `releaseDuringRegistrationInRealCamelPipeline` |
| Release vs timeout: one completion, timer cancelled, map clean | `concurrentReleaseAndTimeoutCompleteOnce` |
| Shutdown resolves pending tickets; the baseline could wait forever because `IBKTimeout.onEviction` stops the primary timer once Camel is stopping | `shutdownResolvesPendingAndAnswersNewRequestsAsTimeout`, `registrationAndShutdownRaceLeavesNoWaiter` |
| MDC restored on the release thread; permit returned even if the callback throws | `callbackFailureRestoresMdcAndDoesNotLeakCapacity` |
| Real Netty HTTP request answered with the final response after release | `BidNettyTest` |
| Spring wiring has no cycle through `RouteCreateFactory` (component scan `com.ibkglobal`; `RouteManagerBean` → `InstanceAdmin` → `RouteCreateService` → `RouteCreateMapper` → `RouteCreateFactory` → `MCAWorkAfterAsync` → `BidManager` ↔ `IBKTimeout`). A null processor fails startup loudly (`MCAInbound` constructor) | Static review only; office start required |
| Existing error codes unchanged: `BID` 00009, `MCA_BID` 01001 (duplicate), `MCA_BID_TIMEOUT` 01002, `TTL` 00006 | Review of `ErrorType`, `ErrorUtil`, `ErrorCatchMCA` |

---

## 6. Decisions taken by IBK (2026-10-10)

| Question | Decision |
|---|---|
| F1/F2 scope | Separate phase 2; in this branch only document and pin with a test |
| Overload answer | Same as BID timeout (01002), capacity configurable |
| Channel close before release | Back to office baseline (no early timeout) |
| Delivery | Branch `audit-fixes`, one commit per finding; `main` untouched, no PR |

---

## 7. Remediation commits (`audit-fixes`)

| Commit | Finding | Files | Summary |
|---|---|---|---|
| `d1eac9a` | F4 | `BidManager`, `BidInfo`, `MCAWorkPreProcess`, `BidAsyncTest` | Remove close listener and channel capture; ticket waits for release or deadline |
| `6b10c3d` | F5 | `BidInfo` | `volatile` `asyncTimerKey`, `fallbackDeadline` |
| `c38b2ac` | F3 | `BidManager`, `BidAsyncTest`, `BidFaultTest`, `BidCapacityConfigTest` | `respondAsBidTimeout`, `applyBidTimeoutResult`, permit inside `compute`, `integrator.config.bid-async-capacity` |
| `3a42aff` | F6 | `BidManager`, `MCAWorkAfterAsync`, `BidObservabilityTest` | Office log lines restored, `Bid Timeout` line, counters, 60 s stats |
| `076ccc1` | F1/F2 (scope) | `docs/OPUS_AUDIT_HANDOFF.md`, `AGENTS.md`, `CLAUDE.md`, `BidTopologyTest` | Scope correction, decisions, real-bridge regression test |
| `f985e78` | F6 follow-up | `BidManager`, `BidObservabilityTest` | Arm failure not counted as suspended |
| this commit set | evidence | `BidAsyncTest`, this report | `releaseDuringRegistrationInRealCamelPipeline`, audit report |

---

## 8. Behaviour after remediation

| Situation | Answer to channel | Log line(s) | Counter |
|---|---|---|---|
| Normal response (otptTmgtDcd ≠ 4) | unchanged | unchanged | — |
| Dummy ack, release later | real response | `MCAWorkAfterProcess dummyCheck InterfaceID`, `Bid Result : <key> / DELIVERED` | suspended, delivered |
| Release before dummy ack | real response, no suspension | `Bid Result : <key> / PARKED`, `Bid release arrived before dummy ack, complete without wait` | earlyRelease |
| No release within 100 s | 01002 "Transaction processing is delayed. Please wait." | `Bid Timeout : <key>` | timedOut |
| Capacity exhausted / stopping / deadline arm failure | 01002, same headers as timeout | `BID continuation not accepted (<reason>), answered as BID timeout : <key>` | notAccepted |
| Second dummy for a key already waiting | `MCA_BID` 01001 (baseline) | — | duplicate |
| Inbound channel closes while waiting | nothing early; ticket waits (baseline) | — | — |
| Release after timeout | `bidInfo is null` error on the release (baseline) | `Bid Result : <key> / NOT_FOUND` | — |
| Application shutdown | pending tickets answered as timeout | `Bid Timeout : <key>` | timedOut |

Configuration: `integrator.config.bid-async-capacity` (default `512`, `>= 1`, read once at startup through `@Value` on `BidManager.setAsyncCapacity`, `BidManager.java:341`). While F1 is open, keep it below `camel.component.netty4-http.maximum-pool-size`.

Stats line, every 60 s on `mca-bid-deadline` and only when something is pending or a counter moved:
`Bid async stats : pending=x/cap, oldestPendingMs=..., suspended=.., delivered=.., earlyRelease=.., timedOut=.., notAccepted=.., duplicate=..`

---

## 9. Test inventory (25 tests, all passing on `audit-fixes`)

| Class | Tests | Proves |
|---|---|---|
| `BidAsyncTest` (10) | `camelPipelineSuspendsAndResumesAfterRelease`, `releaseDuringRegistrationInRealCamelPipeline`, `concurrentReleaseAndTimeoutCompleteOnce`, `shutdownResolvesPendingAndAnswersNewRequestsAsTimeout`, `normalResponseRemainsSynchronous`, `closedChannelKeepsTicketUntilRelease`, `staleTimerCannotCompleteNewGeneration`, `dummyReturnsBeforeRelease`, `releaseFirstCompletesSynchronously`, `failedPrimaryTimerStillExpires` | State machine, real pipeline, races, F4 |
| `BidFaultTest` (6) | `saturationAnswersAsBidTimeoutAndReleasedCapacityIsReusable`, `configuredCapacityIsHonouredAndValidated`, `parkedReleaseCompletesWithRealResponseEvenWhenSaturated`, `callbackFailureRestoresMdcAndDoesNotLeakCapacity`, `releaseDuringTimerInstallDoesNotLeaveTimer`, `registrationAndShutdownRaceLeavesNoWaiter` | F3, fault injection |
| `BidCapacityConfigTest` (2) | `defaultsTo512WhenPropertyIsAbsent`, `readsIntegratorConfigBidAsyncCapacity` | Spring `@Value` wiring |
| `BidObservabilityTest` (4) | `dummyAckKeepsOfficeRcaLogLine`, `earlyReleaseKeepsOfficeLogLineAndCounters`, `deadlineArmFailureIsAnsweredAsTimeoutAndNotCountedAsSuspended`, `countersTrackEveryOutcome` | F6 |
| `BidNettyTest` (1) | `realHttpResponseWaitsWithoutBlockingContinuation` | Real netty4-http round trip |
| `BidTopologyTest` (2) | `prodBridgeFreesIoWorkerButStillParksAdapterInThread`, `directAdapterInFreesTheOnlyExecutorSoReleaseIsServed` | F1, F2 with the real `MCAGcbComBean` |

Not covered by tests: the full `MCAInbound` business route (parsing, mapping, composing with real resources), APM/Penta TranLog, JEUS, multi-node, sustained load.

---

## 10. Reproducing the audit run without the office Nexus

The authoritative run is the office `mvn test`. The audit used this offline equivalent:

- **JDK:** 1.8.0_261.
- **Source path (javac `-sourcepath`):** `src/main/java`, then a stub directory that must come **before** the message module (it shadows `ConverterService`), then `ibkglobal-message/src/main/java` from the office SVN checkout (not in this repo), then the remaining stubs.
- **Annotation processor:** `org.projectlombok:lombok:1.18.22`.
- **Compile explicitly:** compile every needed source as an explicit file, not implicitly through `-sourcepath`. Implicitly compiled files skip Lombok (collect the list with `javac -verbose`, "parsing started").
- **Classpath (Maven coordinates):**
  - Camel / Netty: `camel-core`, `camel-netty4`, `camel-netty4-http`, `camel-jms` `2.21.1`; `io.netty:netty-all:4.1.22.Final`; `commons-pool:commons-pool:1.5.6`
  - Spring: `spring-core`, `spring-jcl`, `spring-context`, `spring-beans`, `spring-web`, `spring-aop`, `spring-expression` `5.3.31` (office uses 4.3.x); `spring-boot:1.5.19.RELEASE` (only for `@ConfigurationProperties`)
  - JMS: `javax.jms-api:2.0.1`
  - Logging: `slf4j-api:1.7.25`, `logback-classic`/`logback-core` `1.1.11`
  - Jackson: `jackson-annotations:2.8.0`, `jackson-databind:2.8.11.3`, `jackson-core:2.8.10`
  - Other: `validation-api:1.1.0.Final`, `commons-lang3:3.8.1`
  - Test: `junit:4.12`, `hamcrest-core:1.3`, `mockito-core:4.5.1`, `byte-buddy:1.12.19`, `byte-buddy-agent:1.12.23`, `objenesis:3.2`
- **Stubs** (harness only, never in `src/`):

```java
// stubs-first/com/ibkglobal/message/converter/service/ConverterService.java (real class pulls ibkglobal-mms)
package com.ibkglobal.message.converter.service;
public class ConverterService { public String objectToJson(Object o) { return String.valueOf(o); } }

// stubs/com/ibk/ibkglobal/data/integrator/route/type/InstanceType.java (ibkglobal-mms)
package com.ibk.ibkglobal.data.integrator.route.type;
public enum InstanceType { MCA, FEP, EAI }

// stubs/org/apache/activemq/ActiveMQConnectionFactory.java (field type in CamelConfig only)
package org.apache.activemq;
public abstract class ActiveMQConnectionFactory implements javax.jms.ConnectionFactory { }
```

- **Run:** `java -cp <classes>;<classpath> org.junit.runner.JUnitCore` with all six test classes. Copy the tests first and rename `verifyZeroInteractions` to `verifyNoInteractions` in the copy only (Mockito 4).
- **Pitfalls hit:**
  - `activemq-all` bundles Camel 2.24.1; keep it off the classpath. Check that the log says `Apache Camel 2.21.1`.
  - On Windows Git Bash the last `-cp` entry was silently dropped; append a dummy entry.
  - `mvn -o` fails because the Spring Boot parent POM is tracked to the internal repository.
  - The `SystemUtil.loadIpAddr` stack trace at class init is expected without office configuration.

---

## 11. Open items and production gates

1. **Office build:** `mvn test` with the real internal artifacts, Spring 4.3 and Mockito 1.10.
2. **Deployed configuration:** confirm in the admin DB/cache that `M.GCB0.COM0.ROUTE` is `MCA_INBOUND` and that HS30710 targets the BEAN `MCAGcbComBean.execute`. If `M.GCB0.COM0.ROUTE` were a CUSTOM route, `MCAInbound` (and this change) would not be on the path at all.
3. **Startup:** Spring context start on JEUS; verify `bid-async-capacity` is picked up (log or `getAsyncCapacityLimit()`).
4. **Thread dumps on a test node:** while a BID is pending, `NettyClientTCPWorker` must not be in `BidManager.workWait`. `NettyEventExecutorGroup` is expected in `AsyncProcessorAwaitManager.await` under `MCAGcbComBean.execute` until phase 2.
5. **Load/soak:** mix normal, dummy-first, release-first, timeout, duplicate and overload traffic; watch the stats line and executor saturation.
6. **Channel compatibility:** confirm channels treat the 01002 answer for overload exactly like a timeout ("check status later").
7. **Rollback drill** to `office-baseline` / the current office build.
8. **Phase 2 (design notes):** make `MCAGcbComBean` an `AsyncProcessor` that hands the Camel callback to `direct:M.GCB0.COM0.ROUTE` and keeps the `GCBO00006560` SEDA branch synchronous. Wire it through `.process()` for BEAN endpoints of that class, or change the deployed route definition. Consequence: TranLog End (Penta JNI) and composing for **every** transaction move to the thread that completes the route (GCB reply IO worker or release thread). Measure IO-worker load before and after; consider a thread hop to a bounded executor. Then flip `BidTopologyTest.prodBridgeFreesIoWorkerButStillParksAdapterInThread`.

---

## 12. Re-audit checklist

1. Re-derive F1 from source: confirm `ProducerTemplate.send` → `AsyncProcessorAwaitManager.await` in camel-core 2.21.1 and that `RouteCreateDefault` wires BEAN endpoints via `.bean(...)` (synchronous).
2. Run `BidTopologyTest` and inspect the thread states it asserts.
3. Check that `respondAsBidTimeout` produces byte-identical headers, `EXCEPTION_CAUGHT` and OUT message to `expireTicket` (both call `applyBidTimeoutResult`).
4. Walk every exit of `bidStartAsync` and confirm the permit is acquired at most once and released exactly once: suspend → `finishContinuation`; arm failure with successful remove → explicit release; not accepted / duplicate / early → never acquired.
5. Confirm `tryAcquire` inside `ConcurrentHashMap.compute` is safe: the remapping function runs at most once per call and the semaphore never blocks.
6. Confirm `volatile` on `asyncTimerKey`/`fallbackDeadline` closes the F5 handshake and that no other async field is written after publication and read by another thread without a happens-before edge.
7. Confirm the 60 s stats task on the single `mca-bid-deadline` thread cannot materially delay fallback deadlines (bounded by map size ≤ capacity + pre-registrations).
8. Challenge the decisions in section 6 (especially F4: is keeping the ticket after a client disconnect right for operations?).
9. Look for anything this audit missed on paths it did not execute (section 9 "Not covered").

---

## Appendix A — Production log evidence (sanitized)

Source: office log of node 1, 2026-10-06. IPs, global IDs and telegram bodies removed. The excerpts quote messages only; nothing in this repo contains the logs.

One request from start to finish on a single Adapter-In thread (the `MCAGcbComBean` bridge holds it across the GCB call):

```
09:28:24 [Camel (camel-1) thread #89 - NettyEventExecutorGroup] LoggingMCA            {"seq":"N1", ... "intfId":"GITO00000199" ...}
09:28:24 [Camel (camel-1) thread #89 - NettyEventExecutorGroup] ParsingMCA            ParsingMCA parsing End
09:28:24 [Camel (camel-1) thread #89 - NettyEventExecutorGroup] TranLogApi            flag: Start, serviceId: MCA_CHN
09:28:24 [Camel (camel-1) thread #89 - NettyEventExecutorGroup] MCAGcbComBean:53      send GCB.COM: GITO00000199
09:28:24 [Camel (camel-1) thread #89 - NettyEventExecutorGroup] LoggingMCA            {"seq":"N2", ...}
09:28:24 [Camel (camel-1) thread #89 - NettyEventExecutorGroup] LoggingMCA            {"seq":"N3", ...}
         ... GCB reply handled on a NettyClientTCPWorker thread ...
09:28:24 [Camel (camel-1) thread #89 - NettyEventExecutorGroup] MCAGcbComBean:83      MCAGcbComBean Producer CleanUp and Stop Completed
09:28:24 [Camel (camel-1) thread #89 - NettyEventExecutorGroup] TranLogApi            flag: End, serviceId: MCA_CHN
09:28:24 [Camel (camel-1) thread #89 - NettyEventExecutorGroup] LoggingMCA            {"seq":"N6", ...}
```

Dummy acks handled on the GCB reply IO workers (baseline blocking point):

```
09:27:01 [Camel Thread #37 - NettyClientTCPWorker] MCAWorkAfterProcess:32 - MCAWorkAfterProcess dummyCheck InterfaceID: GIBO00008317
09:28:37 [Camel Thread #42 - NettyClientTCPWorker] MCAWorkAfterProcess:32 - MCAWorkAfterProcess dummyCheck InterfaceID: GIBO00008320
```

Real responses (releases) handled on Adapter-In executor threads:

```
09:27:03 [Camel (camel-1) thread #145 - NettyEventExecutorGroup] ProcessPreMCA:93 - 실제응답 시 dummy wait 해제
09:28:00 [Camel (camel-1) thread #35  - NettyEventExecutorGroup] ProcessPreMCA:93 - 실제응답 시 dummy wait 해제
```

The N2 log's `session` field holds the inbound `ChannelHandlerContext` (local `:30710`, remote = load balancer), which shows that the header used by the candidate's channel capture was present.

## Appendix B — Thread dumps while a BID is pending (audit harness, Camel 2.21.1)

Wiring: netty4-http Adapter-In → real `MCAGcbComBean.execute` → `direct:M.GCB0.COM0.ROUTE` → netty4-http producer to a fake GCB returning a dummy → after-processor.

Office baseline (`MCAWorkAfterProcess`):

```
[Camel Thread #5 - NettyClientTCPWorker] WAITING
    at java.util.concurrent.CompletableFuture.get(CompletableFuture.java:1908)
    at com.ibkglobal.integrator.engine.manager.BidManager.workWait(BidManager.java:281)
    at com.ibkglobal.integrator.engine.manager.BidManager.bidStart(BidManager.java:133)
    at com.ibkglobal.integrator.engine.bean.mca.work.MCAWorkAfterProcess.bidWait(MCAWorkAfterProcess.java:43)
[Camel (camel-1) thread #4 - NettyEventExecutorGroup] WAITING
    at java.util.concurrent.CountDownLatch.await(CountDownLatch.java:231)
    at org.apache.camel.impl.DefaultAsyncProcessorAwaitManager.await(DefaultAsyncProcessorAwaitManager.java:75)
    at org.apache.camel.processor.SharedCamelInternalProcessor.process(SharedCamelInternalProcessor.java:100)
    at org.apache.camel.impl.ProducerCache$1.doInProducer(ProducerCache.java:541)
release over HTTP (Adapter-In pool = 1) -> STUCK >4s
```

Candidate (`MCAWorkAfterAsync`):

```
[Camel Thread #121 - NettyClientTCPWorker] RUNNABLE
    at io.netty.channel.nio.NioEventLoop.select(NioEventLoop.java:753)        <- free
[Camel (camel-2) thread #120 - NettyEventExecutorGroup] WAITING
    at java.util.concurrent.CountDownLatch.await(CountDownLatch.java:231)
    at org.apache.camel.impl.DefaultAsyncProcessorAwaitManager.await(DefaultAsyncProcessorAwaitManager.java:75)
    at org.apache.camel.impl.ProducerCache$1.doInProducer(ProducerCache.java:541)   <- still parked (F1)
release over HTTP (Adapter-In pool = 1) -> STUCK >4s
release over HTTP (Adapter-In pool = 8) -> release:DELIVERED
```

Candidate with `to(direct:)` instead of the bean (archived JSON wiring), Adapter-In pool = 1:

```
[Camel (camel-4) thread #359 - NettyEventExecutorGroup] WAITING at SingleThreadEventExecutor.takeTask   <- idle, free
release over HTTP -> release:DELIVERED ; original response -> final-response
```
