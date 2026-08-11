# TASK-0065 Outbox Delivery Runtime Evidence

**Date:** 2026-08-10

## Result

**IMPLEMENTED - ready for review.**

The PostgreSQL adapter now contains a transport-neutral, production-disabled
delivery coordination library for immutable integration events.

## Implemented scope

- additive Flyway migration `V003` with delivery state per event and destination;
- idempotent enqueue guarded by `(event_id, destination_id)`;
- deterministic bounded claims using `FOR UPDATE SKIP LOCKED`;
- 5-second to 5-minute leases and 1-to-100 event batches;
- attempt number used as a fencing token alongside owner and unexpired lease;
- immutable canonical CloudEvent bytes reconstructed from JSONB;
- sink calls after the claim transaction commits;
- success, retryable failure, permanent failure, lease renewal, and dead letter;
- eight-attempt deterministic retry schedule ending at 60 minutes;
- controlled low-cardinality error codes without raw external failures;
- transport-neutral telemetry observations without payload or credentials;
- cancellation before claim and lease recovery for interrupted batches.

The API does not instantiate the store or dispatcher. No worker, destination,
credential, connector, webhook, broker, marketplace, ERP, or carrier call is
enabled.

## Focused reproduction

```text
./gradlew :applications:marketplace-operations-persistence-postgres:test \
  --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL
9 tests, 0 failures, 0 errors, 0 skipped
```

## Proven guarantees

- V001, V002, and V003 migrate in order on PostgreSQL 18.4;
- unknown events and invalid destination or error identifiers are rejected;
- duplicate enqueue does not reset coordination state;
- two simultaneous workers claim disjoint rows;
- claim order is deterministic and batch size is bounded;
- a lease cannot be stolen before expiry;
- an expired lease is recovered with the same CloudEvents identity;
- expired, foreign, or older attempts cannot renew or settle a newer claim;
- retry delays are exactly `1m, 2m, 4m, 8m, 16m, 32m, 60m`;
- attempt eight and permanent failures become dead letters;
- sink exceptions persist only `SINK_EXCEPTION`, never the exception message;
- the sink receives bytes identical to the SPEC-0007 fixture;
- `outbox.published_at` remains null;
- cancellation before dispatch claims nothing;
- production startup and public HTTP contracts remain unchanged.

## Complete repository validation

```text
./gradlew build --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 1m 41s
37 actionable tasks: 37 executed
120 tests, 0 failures, 0 errors, 0 skipped
```

## Remaining boundary

Destination registration, credentials, routing, production scheduling, concrete
transports, manual replay, cleanup, dashboards, alerts, ERP APIs, and marketplace
APIs remain outside this implementation and require accepted specifications.
