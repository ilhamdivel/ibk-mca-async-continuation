# Opus Audit Handoff: MCA BID Continuation

## Audit Follow-up (2026-10-10, branch `audit-fixes`)

Independent audit of `95649d8` against `office-baseline`. Verdict: **not production-ready as delivered**. The state machine held up (no double completion, lost ticket or lock-held callback found), but the main scope claim was wrong for the deployed topology and three new behaviours needed decisions. This branch applies the decisions, one commit per finding.

### Scope correction (proven, not fixed in this branch)

Production logs (node mca01, 2026-10-06) show the deployed Adapter-In `M.GCB0.COM0.HS30710` routes to the BEAN endpoint `MCAGcbComBean.execute`, which calls `ProducerTemplate.send(direct:M.GCB0.COM0.ROUTE, exchange)`. The archived JSON (`to direct:`) is not what runs. In Camel 2.21.1 that send is `AsyncProcessorHelper.process` -> `AsyncProcessorAwaitManager.await(latch)`.

| Thread while a dummy ack waits for its release | Office baseline | This continuation |
|---|---|---|
| `NettyClientTCPWorker` (GCB reply IO worker, the 6 Oct "worker freeze") | parked in `BidManager.workWait` | **freed** |
| `NettyEventExecutorGroup` (Adapter-In, via `MCAGcbComBean`) | parked in `AsyncProcessorAwaitManager.await` | **still parked** until release/timeout |

The statement "returns without a per-request blocked worker" is therefore only true for the IO worker. Because netty4-http pins each channel to one `DefaultEventExecutorGroup` thread, a release arriving on a connection pinned to a parked Adapter-In thread waits behind it (pre-existing, reproduced with pool size 1; not observed as queueing in the 6 Oct sample of 233 requests). `BidTopologyTest` pins this behaviour with the real `MCAGcbComBean`.

**Phase 2 (separate change, own load test):** make the `MCAGcbComBean` bridge asynchronous (an `AsyncProcessor` that hands the Camel callback to `direct:M.GCB0.COM0.ROUTE`; the `GCBO00006560` branch stays on SEDA). This moves TranLog End and composing onto the reply thread for every transaction, so it must be measured separately.

### Decisions applied

| Finding | Decision | Commit |
|---|---|---|
| #4 inbound channel close ran the BID timeout immediately; a later real release became `bidInfo is null` | Back to office baseline: close does not end the ticket; release or the 100 s deadline does | `d1eac9a` |
| #5 `fallbackDeadline`/`asyncTimerKey` plain fields in a "write, then check the other flag" handshake (rare permanent 100 ms task leak) | `volatile` | `6b10c3d` |
| #3 overload/stopping threw `MCA_BID` (01001, "capacity exhausted") after the host had accepted the transaction (double-transfer risk on retry) | Answer exactly like a BID timeout (01002, "Transaction processing is delayed. Please wait."); a parked release still completes with the real response; capacity configurable | `c38b2ac` |
| #6 office RCA log lines dropped, no counters | Lines restored with identical text; counters + 60 s stats log | `3a42aff` |

Configuration: `integrator.config.bid-async-capacity` (default `512`, `>= 1`, read once at startup). It caps suspended continuations per node; with the current `MCAGcbComBean` bridge the real ceiling is the netty4-http executor pool (`camel.component.netty4-http.maximum-pool-size`), so keep the capacity below that pool size.

Operational log lines: `MCAWorkAfterProcess dummyCheck InterfaceID: <id>`, `Bid release arrived before dummy ack, complete without wait : <key>`, `Bid Result : <key> / <result>`, `Bid Timeout : <key>`, `BID continuation not accepted (<reason>), answered as BID timeout : <key>`, and every 60 s when active `Bid async stats : pending=x/cap, oldestPendingMs=..., suspended=.., delivered=.., earlyRelease=.., timedOut=.., notAccepted=.., duplicate=..`.

### Audit verification

JDK 1.8.0_261 with the real `camel-core`, `camel-netty4`, `camel-netty4-http` 2.21.1 and `netty-all` 4.1.22.Final jars. The internal Nexus was not reachable, so `ibkglobal-message` was compiled from the office SVN source, three unavailable internal types were stubbed (`InstanceType`, `ConverterService`, `ActiveMQConnectionFactory`), Spring 5.3 jars stood in for 4.3 and Mockito 4 for 1.10 (`verifyZeroInteractions` renamed in the harness copy only). Result on `audit-fixes`: `OK (24 tests)` in every run (BidAsync, BidFault, BidNetty, BidCapacityConfig, BidObservability, BidTopology). This is compile/unit/route-level evidence, not an office build.

### Still required before production

1. Office Maven build and `mvn test` with the real internal artifacts and Spring 4.3/Mockito 1.10.
2. Confirm in the admin DB/cache that `M.GCB0.COM0.ROUTE` is `MCA_INBOUND` and HS30710 targets `MCAGcbComBean`.
3. Spring context start on JEUS (wiring `RouteCreateFactory` -> `MCAWorkAfterAsync` -> `BidManager`).
4. Mixed load/soak (normal, dummy-first, release-first, timeout, duplicate, overload) with thread dumps; set `bid-async-capacity` from the executor pool size.
5. Rollback drill to `office-baseline`.

## Status (original candidate `95649d8`)

The candidate passed 10 JUnit tests under Java 8, Camel 2.21.1 and the cached office artifacts. Coverage includes a real direct Camel pipeline, a real localhost Netty HTTP request returning the final response after release, dummy-first, release-first, 100 release/timeout race iterations, stale timer/key reuse, closed channel, shutdown, normal response, and failed primary timer with real fallback scheduling. Primary IBKTimeout and business telegram objects are mocked where specified in the tests. No office host, database, APM or full MCAInbound business route is exercised. This remains an audit candidate, NOT production approval.

### Outstanding blocking review items

- Independent same-model review has not run. User excluded Gemini delegation; do not use that provider as a substitute.
- Registration failure after release, callback exceptions, admission saturation, shutdown races and context propagation need additional targeted fault-injection coverage.
- Pre-registered tickets are still replaced on dummy transition as in the office baseline; the plan's proposed same-object lifecycle is not implemented. An owner identity property is added, but concurrent duplicate business requests require additional proof.
- Closed-channel cleanup is tested with a real EmbeddedChannel, but extraction of the original channel through the full Adapter IN/OUT route needs office-route proof. *(Superseded on `audit-fixes`: channel close no longer ends the ticket, decision #4.)*
- Local Netty HTTP proof does not cover the vendor message pipeline, APM thread-local pairing, full parsing/mapping/error compatibility or sustained overload.
- Completion queue is now bounded with a 512-ticket admission cap and retrying fallback sweep. These limits introduce explicit overload rejection and need capacity review. *(Superseded on `audit-fixes`: overload is answered as a BID timeout and the cap is `integrator.config.bid-async-capacity`, decision #3.)*
- Primary timeout keys are unique per continuation generation; no reused business-key timer removal on the async release path.
- Fallback tasks are cancelled on completion with remove-on-cancel enabled. The deadline uses monotonic time.
- `MCABidHandle.java:98` still calls `CompletableFuture.get(60000, ...)` for the separate BID type-5 outbound notification path. It is outside the dummy-ACK continuation path and remains a separate performance workstream; this patch does not claim to remove every blocking wait in MCA.
- The archived project excludes active runtime route resources from this sanitized repository. Full `MCAInbound`/database-cache route verification and office endpoint regression require the office checkout/configuration.

### Reproduce current local verification

```bash
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn -o test-compile -q
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn -o dependency:build-classpath -Dmdep.outputFile=target/test-classpath.txt -q
/usr/lib/jvm/java-8-openjdk-amd64/bin/java -cp "target/classes:target/test-classes:$(<target/test-classpath.txt)" org.junit.runner.JUnitCore com.ibkglobal.integrator.engine.manager.BidAsyncTest com.ibkglobal.integrator.engine.manager.BidNettyTest
```

Observed: `OK (10 tests)`. A baseline `SystemUtil.loadIpAddr` initialization stack trace occurs in the environment without office configuration; it is not a passed production environment validation. Offline `mvn test` itself cannot start because the cached Surefire JUnit provider 2.18.1 is missing; direct JUnitCore above exercises the compiled tests as the fallback.


## Baseline

- Repository: `ilhamdivel/ibk-mca-async-continuation` (private).
- Baseline tag: `office-baseline`.
- Baseline commit: `d5fa9932478b827699e8361034ff24ccab0cca31`.
- Baseline contains the uploaded office Java source unchanged. Environment resources, SVN metadata, credentials, and build artifacts are deliberately excluded.
- Compare against this tag, not the separate historical MCA checkout.

## Verified Problem

`BidManager.workWait()` calls `info.getFuture().get()`. CompletableFuture is a completion signal but this invocation still blocks the caller. Office pre-registration handles early releases; it does not eliminate dummy-first thread parking.

`MCAInbound` currently invokes the post-host handler as a synchronous bean. It is manually constructed by `RouteCreateFactory`, so field injection on the route object must not be assumed.

## Required Behavioral Contract

1. Normal, non-dummy responses retain their existing processing, payloads, routing, and error behavior.
2. Dummy-first suspends the Camel route through a real `AsyncProcessor`, returning without a per-request blocked worker. *(Audit: holds for the GCB reply IO worker only; the deployed `MCAGcbComBean` bridge still parks the Adapter-In thread, see Scope correction.)*
3. Early release remains parked until the dummy consumes the final response; no ticket overwrite.
4. Release and timeout have exactly one terminal owner. Duplicate completion is prohibited.
5. Primary timer failure cannot leave an indefinite suspended request; independent deadline fallback is required.
6. Timer and map locks must not be held while invoking continuation callbacks.
7. Shutdown, cancellation, registration failure, callback failure, and channel-close require bounded cleanup, explicitly tested or declared unresolved. *(Audit decision: channel close keeps the ticket until release or the 100 s deadline, as in the office baseline.)*
8. No peer forwarding, business route changes, or automatic production deployment.

## Review Commands

```bash
git diff office-baseline -- src/main/java src/test/java
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn -o test-compile
git diff --check
```

Parent directly ran offline `test-compile` on the unchanged baseline successfully. This is a compilation result, NOT async behavior evidence. Updated test commands and results must be appended after implementation.

## Blocking Gate

`.process(asyncProcessor)` must preserve the async contract end to end. Any synchronous ProducerTemplate bridge, transacted Exchange, or endpoint with `synchronous=true` may defeat it. Archived route JSONs indicate direct inbound routing, but deployed cache/database routes can differ. Verify office active routes before approval.

## Mandatory Tests

- Normal response with and without a valid BID key.
- Dummy-first: worker returns before release, then correct final response.
- Release-first, including release racing continuation registration.
- Primary timer success and deliberately disabled eviction with fallback success.
- Stale timer versus reused key and object-identity cleanup.
- Release versus timeout: one response and one callback invocation.
- Duplicate/late releases and duplicate dummy requests.
- Scheduler rejection, callback exception, shutdown, and closed channel.
- Context/MDC restoration and cleanup on reused executor threads.
- Real Camel 2.21.1 route proof and office Netty response lifecycle test.

## Tradeoffs Compared With Blocking Baseline

- More complex completion state and concurrency testing.
- Exchange and connection remain pending; memory and connection limits still apply.
- Callback-thread context and APM pairing require explicit verification.
- Deadline watchdog and executor lifecycle add operational overhead.
- JVM restart still loses in-memory continuation; async is not durable recovery.
- Cross-node callback mismatch remains unresolved and must not be claimed fixed.
- Legacy entry points can still block unless separately migrated; document exact scope.

## Office Release Gate

Require independent Opus review, full office Maven build, actual normal/BID route regression, Netty thread dumps, load/soak measurements, and verified rollback before production. No local unit-test pass guarantees banking transaction correctness.

## Rollback

Keep existing office build/configuration intact. Use `office-baseline` as source comparison; do not force-reset shared work or deploy this baseline without restoring the office environment configuration. Drain pending requests before any operator-managed application restart.
