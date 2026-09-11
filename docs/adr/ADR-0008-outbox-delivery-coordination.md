# ADR-0008: Outbox Delivery Coordination

Status: Proposed

Date: 2026-08-10

## Context

The transactional outbox now records application integration facts atomically.
The next boundary must support eventual delivery without coupling the event
contract to Kafka, Temporal, a webhook, or any marketplace-specific connector.

One event may eventually feed several independently operated destinations. A
single mutable status on the outbox cannot represent independent progress,
retries, or dead letters for those destinations. Network delivery also cannot be
held inside a database transaction.

## Decision

Keep `integration_event_outbox` as the immutable occurrence record and model
delivery coordination separately, once per `(event_id, destination_id)`.

A PostgreSQL-backed delivery adapter will claim small deterministic batches with
`FOR UPDATE SKIP LOCKED`, record a bounded lease, commit the claim, and perform
delivery outside the claim transaction. It then conditionally settles success or
failure only when it still owns the lease.

Delivery is at least once. If a worker sends an event and crashes before settling
it, the expired lease makes the same CloudEvent eligible again with the same
`source` and `id`. Every destination must therefore deduplicate that pair.

## Boundaries

- the outbox remains the immutable application fact;
- a delivery row contains operational state for one logical destination;
- the delivery engine depends on a transport-neutral sink port;
- a concrete transport owns authentication, protocol, and destination mapping;
- no transport may modify the CloudEvent envelope or payload;
- no delivery type enters Marketplace Operations or the Kernel;
- `published_at` on the outbox remains `NULL` in the next implementation because
  it cannot truthfully summarize future multi-destination delivery.

## Why PostgreSQL coordination first

PostgreSQL is already required for the MVP and provides row locks, deterministic
ordering, and `SKIP LOCKED` for queue-like contention. This lets Genesis prove
claiming, lease recovery, retry, and settlement without adding another runtime.

This is not a permanent rejection of a broker. A later adapter may publish to a
broker or use CDC while preserving the event and sink contracts.

## Alternatives considered

### Mutate only `outbox.published_at`

Rejected. It cannot distinguish zero, one, or many destinations and cannot retain
independent retry or dead-letter state.

### Hold a database lock during network delivery

Rejected. External latency would prolong transactions, increase lock contention,
and still would not eliminate the send-before-settle crash window.

### Delete rows after success

Rejected. Deletion removes operational evidence and prevents bounded audit and
reproduction.

### Add Kafka, RabbitMQ, or Temporal now

Deferred. No accepted first transport or destination exists yet. Introducing a
runtime before its contract would increase MVP operations without user value.

### Exactly-once delivery

Rejected as an end-to-end claim. Genesis can make claims and settlements
transactional locally, but cannot atomically commit an arbitrary remote system.

## Consequences

- concurrent workers can scale without claiming the same delivery row;
- a slow or failing destination does not block another destination;
- duplicate delivery is explicit and testable;
- delivery history adds storage and retention responsibility;
- destination registration and concrete transports still require separate
  specifications.

## Authorization

SPEC-0008 freezes the delivery state machine and operational contracts. Accepting
this ADR does not enable a worker or external destination by itself.
