# MCA Async Continuation

## Overview
Private Java 8 / Camel 2.21.1 office-source baseline and isolated BID continuation implementation. Maven is the package manager.

## Commands
- Build: `mvn test` (requires internal dependencies).
- Offline verification: see `tests/README.md` once the harness is available.

## Conventions
Preserve existing route order and CRLF Java endings. New code and documentation use English.
```java
callback.done(false);
```

## Boundaries
**NEVER** deploy automatically, modify business routing, commit credentials, `.svn`, `target`, logs, or binaries. **ALWAYS** test normal responses and BID races.

## Dependencies
Java 8, Camel 2.21.1, Spring Boot 1.5.10; exact dependencies remain in `pom.xml`.

## Config
Runtime resources with environment configuration are excluded from the public-facing git payload even though this repository is private. Use office configuration locally; never commit it.

## Error Handling
Preserve existing error codes and complete continuations once only.

## Troubleshooting
- Missing internal Maven artifacts: use the real-artifact offline harness.
- Callback on a different JVM: outside async scope.
- Channel closed: dispose pending continuation without duplicate writes.
