# MCA BID Async Continuation Re-audit

## Verdict

**REQUEST CHANGES before production deployment.** The remediation improves error compatibility, publication safety and observability. The native suite passes, but shutdown exception isolation and fallback completion independence still have reproducible failure modes. End-to-end worker liberation remains a separate, acknowledged phase.

## Scope and revision

- Repository: private `ilhamdivel/ibk-mca-async-continuation`.
- Branch reviewed: `origin/audit-fixes`.
- Revision: `ec28074a3ed915800e22342fdc0b1c10beb122cb`.
- Baseline: `office-baseline`, `d5fa993`.
- Review workspace: isolated detached worktree; tracked source and tests unchanged.
- Read the audit report, handoff and original audit prompt; reviewed every changed production/test file and relevant request, release, error and route source. Broad blocking/context scan covered all 118 production Java files.
- No deployment, no production database query, no commit or push.
- Office log excerpts in the preceding audit are not independently available here. The bridge behavior is independently supported by source and the topology tests, not by a new inspection of production logs.

All source references below are relative to `src/main/java/com/ibkglobal/integrator/` unless explicitly marked as test or documentation paths.

## Verified evidence

Commands:

```bash
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn -o test
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn -o package -DskipTests
git diff --check office-baseline..HEAD
```

- Initial Maven test attempt compiled all 118 production files and six test files, then failed only because the public Surefire JUnit provider 2.18.1 was absent.
- Fetched that provider's POM and JAR from Maven Central into the Maven cache, without changing the project.
- Three subsequent full native Maven test runs: **25 tests, zero failures, zero errors** each.
- Offline WAR package: **BUILD SUCCESS**.
- Diff whitespace check: passed. Working tree: clean.
- Classpath: Spring Boot 1.5.10.RELEASE, Spring Core 4.3.14.RELEASE, Mockito 1.10.19, Camel Core 2.21.1, Netty All 4.1.22.Final, cached `ibkglobal-message` JAR.
- No substitute Spring/Mockito versions or source stubs used for the native build.
- Internal snapshot artifacts were consumed from the existing cache; their equality to deployed office artifacts was not established.
- Additional audit-only probes are under `target/audit-probes/`; they do not alter the six committed test classes. The probes use production classes, controlled callback fault injection, and (for the primary-lock case) the actual IBKTimeout and Camel DefaultTimeoutMap.

## R1: One throwing continuation aborts shutdown drain

**Priority: required. Severity: high under callback failure. Introduced with async shutdown.**

Evidence:

- `engine/manager/BidManager.java:577-587`: shutdown stops the watchdog, iterates pending tickets without per-ticket exception isolation, then shuts down the completion executor only after the loop.
- `engine/manager/BidManager.java:193-196,306-311,541-573`: expiry invokes the callback, and its exception propagates despite permit/MDC cleanup in the callback's finally block.
- Existing `BidFaultTest.java:102-125` deliberately allows a callback exception to propagate on release; it does not combine that failure with multi-ticket shutdown.

Reproduction, three identical runs:

```text
PROBE shutdown: exception=Injected continuation failure callbacks=1 map=2 permitsHeld=2 watchdogShutdown=true completionShutdown=false
```

Three pending tickets were registered with callbacks that throw. The first callback was invoked and cleaned up; shutdown exited before visiting the other two. The watchdog was already stopped, and the completion executor was left running. Surviving primary timers might still resolve remaining tickets in some environments, but shutdown itself no longer guarantees draining; primary eviction also stops when Camel is stopping.

Required remedy:

1. Isolate and log each ticket's completion failure; continue processing other tickets.
2. Place executor lifecycle cleanup in a guaranteed outer finally path.
3. Define a bounded shutdown/drain policy; a callback that never returns must not indefinitely hold the shutdown caller.
4. Add a multi-ticket shutdown regression where one callback throws. Assert remaining callbacks run once, permits return, timers cancel, registry empties and both owned executors stop.

## R2: Independent fallback completion still waits on the primary timer lock

**Priority: required resilience fix. Severity: high if primary cleanup stalls. Introduced in finishContinuation.**

Evidence:

- `engine/manager/BidManager.java:541-562`: terminal completion is claimed, then `ibkTimeoutBid.remove()` is called synchronously before invoking `callback.done()`.
- `engine/timer/IBKTimeout.java:139-140`: remove delegates to Camel's timeout map.
- Camel 2.21.1 `DefaultTimeoutMap.remove()` bytecode acquires its lock; purge/eviction uses the same lock. Confirmed against the exact cached JAR with javap.
- The existing fallback test injects failure at timer put; it does not freeze the primary map lock during cleanup.

Reproduction with the actual primary timer map, three identical runs:

```text
PROBE real-primary-lock: timeoutMs=50 observedMs=300 callbackCompleted=false status=TIMEOUT terminalClaimed=true mapContains=false permitsHeld=1
```

The fallback reached expiry, removed the ticket and claimed terminal completion. It then blocked removing the primary timer, so no response callback ran and the permit remained held until the primary lock was released. This is controlled lock fault injection, not a claim of a production lock stall.

Required remedy:

- Separate response completion from potentially blocking primary-timer cleanup.
- Primary cleanup must be identity-safe and best-effort/asynchronously bounded, rather than a prerequisite for the response callback.
- Guarantee timer, permit and MDC cleanup across failure paths; retain exactly-once callback semantics.
- Add the real primary-lock fault injection to regression coverage.

## R3: Completion worker saturation defeats the response deadline

**Priority: required verification/design bound. Severity: medium to high depending on downstream latency. Introduced with async completion executor.**

Evidence:

- `engine/manager/BidManager.java:147-158`: expiry is queued once; timeoutQueued stays true after successful submission.
- `engine/manager/BidManager.java:415-422`: two completion workers, queue length 512.
- `engine/manager/BidManager.java:485-490`: fallback retries submission, but the timeoutQueued guard prevents another attempt for an already queued ticket.
- `engine/manager/BidManager.java:562`: callback executes the route continuation on the completion worker.

Reproduction, three identical runs:

```text
PROBE deadline: timeoutMs=30 observedMs=300 completed=false status=WAIT timeoutQueued=true mapContains=true completionQueue=1
```

Two controlled slow timeout callbacks occupied both completion workers. A third ticket expired at 30 ms but remained WAIT after a further 300 ms observation. It completed after those workers were released. The timers are independently scheduled; final completion is not independently guaranteed.

Required remedy:

- Define an expiry-to-response completion latency bound, not only a ticket's expiry timestamp.
- Measure real downstream parsing, mapping, logging/error composing latency under simultaneous timeouts.
- Isolate terminal ownership/cleanup from slow downstream work and choose a bounded execution/backpressure policy. Simply enlarging a thread pool or resetting timeoutQueued is not a proof of correctness.
- Cover blocked completion workers, queue saturation/rejection/retry, and capacities above the fixed completion queue size.
- Do not describe the current 100-second deadline as an unconditional upper bound on response completion or resource lifetime.

## Known open scope: Adapter-In still blocked through MCAGcbComBean

This was correctly identified in the previous audit and is not a newly discovered regression.

- `engine/bean/mca/work/MCAGcbComBean.java:55`: synchronous ProducerTemplate.send.
- `engine/builder/route/RouteCreateDefault.java:172-175`: BEAN endpoints are wired synchronously.
- `src/test/java/com/ibkglobal/integrator/engine/manager/BidTopologyTest.java:163-176`: passing test explicitly asserts the Adapter-In await remains.
- The IO reply worker is freed by the continuation. The incoming request and its connection remain pending, and the Adapter-In executor thread still awaits completion under the bridge topology.

Keeping async capacity below the executor pool size does not guarantee a release has an unblocked executor: Netty pins channels to executor children. Capacity limits aggregate suspended continuations, not the availability of the exact executor assigned to a release connection. Phase 2 must remove the synchronous bridge or provide a proven topology-specific alternative.

The committed bridge topology test resolves the ticket by a direct manager call; only the direct-topology test sends the release through real HTTP. Add a bridge-topology HTTP release starvation regression so the prior audit's pool-size-one experiment is reproducible from committed tests.

## Accepted fixes and verified properties

- Timer handles are volatile; the post-publication cancellation handshake is improved.
- Overload/stopping answers share the existing timeout result builder instead of returning a new hard failure.
- Early parked releases can finish without capacity admission.
- Original operational dummy and early-release log lines are restored.
- Pending/outcome counters are present.
- Release versus timeout atomic registry ownership and guarded callbacks pass existing race coverage.
- Original channel-close semantics are restored: disconnect does not prematurely turn a later release into a missing-ticket error. This is a deliberate compatibility decision, not a cleanup regression to undo casually.
- Normal post-host response bypasses the timer and registry in the targeted processor test.
- Stale internal timer generations do not complete a replacement ticket in the targeted test.

## Pre-existing issues and coverage gaps

Do not present these as regressions from the remediation:

- A request without MCA_BID_OWNER can claim another request's PENDING/RELEASED ticket (`BidManager.java:440-461`, `MCAWorkPreProcess.java:75-82`). The prior audit notes this correctly; explicit duplicate request ownership tests are still missing.
- Exchange copying does not deep-copy the mutable business body. Release-route changes after bidResult can overlap original-response processing (`ProcessPreMCA.java:100,110-147`, `BidManager.java:286`, `ProcessAfterMCA.java:90-94`). Preserve the wire/business contract when isolating the response snapshot; do not blindly remove processing-number updates.
- Stale external releases are correlated only by business key; unique timer keys do not prove protection against an old external release after key reuse.
- Type-5 terminal BID still blocks its JMS consumer in `MCABidHandle.java:98`; this is a separate path from dummy-ACK waiting.
- Cross-node ownership is still unresolved and is not addressed by local async continuation.
- The admission semaphore only covers suspended continuations, not all pre-registration entries or in-flight upstream work.
- `engine/builder/route/mca/bid/MCABidProcess.java:42` compares the type-5 header with `== "5"`, not content equality. A non-interned type-5 string can skip afterProcessBid and mapping. This is unchanged from baseline; use null-safe content equality and test a non-interned value.
- `engine/bean/mca/work/MCABidBean.java:48` uses `isEmpty(value) && !(value.equals("5") || value.equals("6"))`. Unsupported non-empty values bypass validation; null enters the condition and fails with NPE before being wrapped. This is unchanged from baseline; use an explicit null-safe allowed-value predicate and cover null, empty, unsupported, 5 and 6.

## Background reviewer limitations

The background reviews completed after the main report and were iteration-truncated. Their returned metadata identifies a Gemini model rather than the requested session model. They are not accepted as the user's required same-model independent sign-off. Main verdict, native test results and three repeated fault-injection probes are based on direct parent execution. Two additional baseline findings above were verified directly against source after reading the background suggestions. Unsupported background claims about Hazelcast, exact production saturation thresholds and automatic propagation of all mapping exceptions were excluded.

## Production acceptance criteria

1. Fix R1 and R2 with regression tests; explicitly bound/measure R3.
2. Verify native cached internal artifact versions/checksums against the office build inputs.
3. Start the real Spring/JEUS application and inspect instantiated active routes using approved admin/cache/log interfaces; do not query production DB for this review.
4. Run complete normal and BID flows through actual parsing, mapping, error handling and composing: normal, local, ITRO00000035, JSON/JSON_FLAT, dummy-first, release-first, timeout, duplicate, overload, channel close and key reuse.
5. Verify byte-level payload/header/error compatibility and N1-N6 correlation; N6 alone is not evidence of client receipt.
6. Exercise normal traffic mixed with pending BID, simultaneous timeouts, slow mapping/logging, release HTTP traffic and disconnects. Collect worker stack traces, callback delay, permit count, registry/timer/queue sizes and heap retention.
7. Confirm APM/Penta context assumptions on real office dependencies; MDC restoration alone is not proof of all thread-local context.
8. Keep phase 2 scope explicit. Full end-to-end async approval requires proving Adapter-In no longer waits in the bridge.
9. Perform rollback/drain rehearsal using the actual office build and configuration, not the sanitized WAR alone.

## Documentation follow-up

- The original handoff reproduction command lists only two test classes and an old ten-test result. Update the active instructions to run all six classes or native mvn test.
- AGENTS.md references a tests/README.md that is absent from the reviewed tree. Replace the stale link with the current verified commands.
- Add fallback activation/failure, late/missing release, completion queue depth and expiry-to-callback latency metrics; current delivered counts terminal release selection, not confirmed client receipt.
- Keep historical audit evidence separate from current verification so a future reviewer does not mistake old runtime limitations for today's Maven result.

## Final decision

The remediation is materially better than the original candidate. It is a valid scope-limited candidate for further testing, not an unconditional production approval. The three reproduced failure paths above and real-route/deployment gates remain outstanding. No production source was changed during this re-audit.
