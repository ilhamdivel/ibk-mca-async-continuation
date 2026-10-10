# Claude Review Request: MCA Async Continuation Scope

## Review target

Repository: `ilhamdivel/ibk-mca-async-continuation`

Branch: `audit-fixes`

Current revision to review: `c6374c7`

Previous validated revision: `5142169`

Baseline: `office-baseline` (`d5fa993`)

## Purpose

Re-review the latest implementation against the actual scope requested by the operator:

> Replace only the thread hold caused by Dummy ACK. Preserve every normal, non-BID, BID non-transaction, approval, local, type-5, type-6, real-response, and error flow unless that flow is specifically the Dummy ACK thread-hold path.

This review must challenge the current `BID_SAFE_REPLY` gate. The requirement is not to classify Dummy ACKs as safe or unsafe. The requirement is to identify whether the current flow is the Dummy ACK path that holds a thread.

## Scope invariant

The implementation must satisfy all of these:

1. A flow with `OtptTmgtDcd="4"` that reaches the Dummy ACK wait point and would otherwise park a worker must use the async continuation.
2. A flow with `OtptTmgtDcd` other than `"4"` must remain synchronous and must not enter the async BID registry, async permit accounting, async timeout pool, or resume pool.
3. Normal transactions and non-BID transactions must preserve the office route order, headers, body, error codes, timeout behavior, response payload, and logging semantics.
4. BID non-transaction paths, including terminal type-5 and receive-confirmation type-6, must not be changed merely because the shared inbound route now uses an `AsyncProcessor` wrapper.
5. A transport or adapter property must not silently decide that a genuine Dummy ACK continues through the legacy blocking wait. Transport-specific callback protection belongs in the producer/route lifecycle; it must not reintroduce `BidManager.workWait()` for a Dummy ACK that is supposed to be non-blocking.
6. The release thread must only claim/update the ticket and signal/enqueue resumption. It must not execute the waiting transaction's mapping, parsing, composing, or remaining Camel route inline.
7. A Camel producer callback and the continuation callback must each be invoked at most once, including stale producer events, channel close, release/timeout races, shutdown, and callback exceptions.
8. Cross-node ownership is out of scope. Do not claim that this patch fixes multi-JVM ticket ownership or routing.

## Current suspected scope violation

Inspect `src/main/java/com/ibkglobal/integrator/engine/bean/mca/work/MCAWorkAfterAsync.java:46-63`.

The current logic is structurally equivalent to:

```java
if ("4".equals(otptTmgtDcd)) {
    BidInfo bidInfo = BidUtil.bidCreate(exchange, telegram);
    if (Boolean.TRUE.equals(exchange.getProperty(BidManager.BID_SAFE_REPLY, Boolean.class))) {
        return bidManager.bidStartAsync(bidInfo, guarded);
    }
    bidManager.bidStartOfficeWait(bidInfo);
}
```

This means a genuine Dummy ACK can return to the office blocking wait solely because `BID_SAFE_REPLY` is absent. That contradicts the scope invariant if the flow is the same thread-holding Dummy ACK path.

Do not accept the phrase "unsafe Dummy ACK" without identifying the exact thread that would be held, the exact producer callback contract, and why the callback lifecycle cannot be fixed at the producer/route layer.

## Required review questions

### A. Flow classification

For every route entering `MCAWorkAfterAsync`, classify the flow by observable behavior, not by a generic transport label:

- Is `OtptTmgtDcd="4"`?
- Which exact thread is held before the patch?
- Which exact thread must be freed after the patch?
- Does the exchange return `false` from `AsyncProcessor.process`?
- Does it enter `bidInfoList` and hold an async permit?
- Which callback resumes it?

Produce a matrix for:

- normal synchronous response;
- non-BID transaction;
- Dummy ACK transaction;
- approval request without Dummy ACK;
- real response `0/R/K`;
- release-first;
- terminal BID type-5;
- receive-confirmation BID type-6;
- local adapter-out;
- TCP adapter-out;
- error and timeout paths.

### B. `BID_SAFE_REPLY` gate

Determine whether `BID_SAFE_REPLY` is:

1. merely a callback-suppression mechanism; or
2. incorrectly being used as the blocking-versus-async selector.

If it is both, separate the responsibilities. The blocking decision must be based on the Dummy ACK thread-hold path, not on an unproven transport property.

### C. Second callback behavior

Audit `BidSafeHttpClientChannelHandler`, `IBKHttpProducerInitializer`, and all producer route wiring.

Prove:

- whether the handler actually prevents a second Camel producer callback;
- whether it changes only callback delivery or also changes business flow;
- whether all Dummy ACK producer paths that hold a thread receive the protection;
- whether non-BID and synchronous flows still receive their normal callback;
- whether a missing handler can cause the implementation to fall back to `bidStartOfficeWait()`.

If the handler is required, wire it at the producer lifecycle without changing which Dummy ACK paths are async.

### D. Normal-flow regression

Compare current code against `office-baseline` and verify that these flows are unchanged:

- normal `OtptTmgtDcd != "4"`;
- non-BID transaction;
- approval request that does not produce Dummy ACK;
- local transaction;
- `ITRO00000035`;
- JSON and JSON_FLAT mapping;
- type-5 terminal BID;
- type-6 receive-confirmation BID;
- mapping failure;
- routing failure;
- read timeout;
- composing failure;
- existing `EMCACOM` error mapping.

No new async registry entry, permit, timeout, resume task, or business header mutation is allowed on these paths unless the baseline already had that behavior.

### E. Dummy ACK acceptance

For every genuine Dummy ACK that previously held a thread, require:

- `MCAWorkAfterAsync.process(Exchange, AsyncCallback)` returns `false` after ticket registration;
- no `BidManager.workWait()` on the waiting path;
- one async continuation callback only;
- release thread only signals/enqueues;
- timeout and shutdown finish exactly once;
- pending registry and permits are cleaned;
- no second Camel producer callback;
- final response remains compatible with the office flow.

### F. Test requirements

Add or update tests for:

1. A real thread-holding Dummy ACK with no manually injected `BID_SAFE_REPLY`; it must use async continuation.
2. Normal response with no `BID_SAFE_REPLY`; it must remain synchronous.
3. Non-BID flow with no `BID_SAFE_REPLY`; it must remain unchanged.
4. Dummy ACK through every producer path that can hold a thread; each must use async continuation.
5. A stale or duplicate producer callback; continuation must run once.
6. Release versus timeout race.
7. Release-first and dummy-first.
8. Normal traffic mixed with Dummy ACK traffic.
9. A transport path that truly cannot use the async producer contract; document the exact reason and prove it is not the target thread-holding Dummy ACK path. Do not use this as a generic escape hatch.

Run:

```bash
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn -o test
JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64 mvn -o package -DskipTests
git diff --check office-baseline..HEAD
```

Report the exact total test count and all failures. Do not report a test as passing if it was skipped or not compiled.

## Required verdict format

Return exactly these sections:

1. `Verdict`
2. `Scope Invariant Result`
3. `Flow Classification Matrix`
4. `BID_SAFE_REPLY Analysis`
5. `Second Callback Analysis`
6. `Normal and Non-BID Regression Review`
7. `Dummy ACK Thread-Hold Review`
8. `Findings`
9. `Tests and Build Evidence`
10. `Required Changes`
11. `DEV Readiness`
12. `Final Decision`

Use severity labels: `Blocker`, `Required`, `Warning`, `Informational`.

Every finding must include:

- exact file and line;
- whether it is introduced, pre-existing, or a remediation regression;
- concrete flow/interleaving;
- observable impact;
- required fix or explicit reason for acceptance.

## Hard decision rule

If a genuine Dummy ACK that holds a thread can still fall back to `bidStartOfficeWait()` merely because `BID_SAFE_REPLY` is absent, the result is **REQUEST CHANGES**. The implementation has not met the requested scope.

If normal, non-BID, BID non-transaction, approval, local, type-5, type-6, real-response, or error flows enter the async continuation solely because the inbound route now uses an `AsyncProcessor`, the result is also **REQUEST CHANGES**.

The desired result is a narrow change:

```text
normal/non-BID/non-target flows -> unchanged office behavior
thread-holding Dummy ACK flow  -> async continuation
release thread                 -> signal/enqueue only
waiting transaction             -> resumes exactly once
```
