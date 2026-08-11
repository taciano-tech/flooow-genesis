# TASK-0064 Outbox Delivery Contract Review

**Date:** 2026-08-10

## Result

**PROPOSED - ready for concurrency, operations, and integration review.**

ADR-0008 and SPEC-0008 define a transport-neutral delivery state machine without
activating production delivery or selecting a broker.

## Repository evidence

- TASK-0063 produces immutable CloudEvents atomically in PostgreSQL;
- the outbox has no claiming, retry, dead-letter, retention, or delivery runtime;
- `published_at` cannot represent independent progress to multiple destinations;
- OpenTelemetry is already available, but no messaging transport is accepted;
- no tenant, credential, destination registry, or connector contract exists.

## Research evidence

- PostgreSQL 18 documents `SKIP LOCKED` for avoiding contention among multiple
  consumers of a queue-like table;
- PostgreSQL requires explicit ordering for deterministic limited subsets;
- CloudEvents defines repeated `source` plus `id` as duplicate identity, matching
  lease-expiry redelivery;
- OpenTelemetry separates producer send and consumer processing semantics and
  keeps messaging attributes transport-aware.

## Decision evidence

- delivery state is separate from the immutable occurrence;
- state is independent per logical destination;
- claims are short transactions and network calls happen afterward;
- at-least-once behavior and destination idempotency are explicit;
- bounded attempts, deterministic backoff, leases, safe error codes, metrics,
  tracing, shutdown, and retention are frozen before implementation;
- TASK-0065 remains test-only infrastructure with production startup disabled.

## Repository validation

Executed on 2026-08-10:

```text
./gradlew build --rerun-tasks --no-daemon --console=plain
exit code: 0
116 tests, 0 failures, 0 errors, 0 skipped
```

The proposal changes documentation only. All existing application, persistence,
API, tracing, and Kernel tests remain green.

## Authorization boundary

Merging this proposal authorizes only the deterministic PostgreSQL delivery
library and fake-sink validation in TASK-0065. It does not authorize an external
destination or production worker.
