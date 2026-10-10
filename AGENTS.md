# MCA Async Continuation

## Overview
Private Java 8 / Camel 2.21.1 office-source baseline and isolated BID continuation implementation. Maven is the package manager.

## Commands
- Build: `mvn test` (requires internal dependencies).
- Offline verification: `mvn -o test` (needs the Surefire JUnit4 provider 2.18.1 cached), or JUnitCore over every `*Test` class in `src/test/java/com/ibkglobal/integrator/engine/manager` (expected 40). Without internal artifacts see `docs/AUDIT_REPORT_BID_CONTINUATION.md` section 10.

## Conventions
Preserve existing route order and CRLF Java endings. New code and documentation use English.
```java
callback.done(false);
```

## Boundaries
**NEVER** deploy automatically, modify business routing, commit credentials, `.svn`, `target`, logs, or binaries. **ALWAYS** test normal responses and BID races.
**SCOPE RULE:** the async change may only stop the dummy ack from holding other transactions. Normal, local, ITRO00000035, type-5, type-6 (RCV_CONFIRM_BID), real-response, approval and error flows keep office behaviour; a releasing thread only signals and never runs the waiting transaction's continuation (see `docs/REAUDIT_RESPONSE.md` section 4a).

## Dependencies
Java 8, Camel 2.21.1, Spring Boot 1.5.10; exact dependencies remain in `pom.xml`.

## Config
Runtime resources with environment configuration are excluded from the public-facing git payload even though this repository is private. Use office configuration locally; never commit it.

## Error Handling
Preserve existing error codes and complete continuations once only.

## Troubleshooting
- Missing internal Maven artifacts: use the real-artifact offline harness.
- Callback on a different JVM: outside async scope.
- Channel closed: keep the ticket until the release or the 100 s deadline (office baseline); never write a second response.
- Overload, stopping or deadline-arm failure after a dummy ack: answer as BID timeout (01002), never a hard MCA_BID error.
- Never take the primary IBKTimeout lock before answering or releasing a permit; primary put/remove run on `mca-bid-timer`.
- Suspend only a dummy ack delivered by `BidSafeHttpClientChannelHandler` (`BID_SAFE_REPLY`, GCB adapter-out). Any other client (LOCAL adapter-out, TCP) keeps the office wait (`officeWaits`): Camel 2.21.1 calls a producer callback twice when the channel closes under a suspended exchange (`docs/REAUDIT_RESPONSE.md` section 4b).
- Shutdown drains each ticket in isolation and is bounded (`shutdownDrainMillis`); timeout answers run on `mca-bid-completion` (`integrator.config.bid-completion-threads`).
