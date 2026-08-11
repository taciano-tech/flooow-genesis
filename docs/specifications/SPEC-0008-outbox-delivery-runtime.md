# SPEC-0008: Outbox Delivery Runtime

**Status:** Proposed

**Date:** 2026-08-10

**Source decision:** ADR-0008

## Objective

Specify a transport-neutral, PostgreSQL-coordinated delivery runtime that can be
validated with a deterministic fake sink before any external transport is chosen.

## Authorized next implementation

Acceptance authorizes TASK-0065 only:

1. add an additive delivery-state migration;
2. implement enqueue, claim, renew, success, retry, and dead-letter operations in
   the PostgreSQL adapter;
3. define a transport-neutral sink result contract;
4. implement a deterministic dispatcher library exercised only by tests;
5. add concurrency, crash recovery, retry, privacy, metrics, and tracing tests;
6. keep production startup disabled because no destination is accepted.

No broker, webhook, marketplace, ERP, carrier, credential, tenant, subscription,
background process, public endpoint, or production delivery is authorized.

## Delivery state

Migration `V003` creates `integration_event_delivery` with at least:

```text
event_id UUID NOT NULL REFERENCES integration_event_outbox(event_id)
destination_id TEXT NOT NULL
status TEXT NOT NULL
attempt_count INTEGER NOT NULL DEFAULT 0
next_attempt_at TIMESTAMPTZ NOT NULL
lease_owner TEXT NULL
lease_until TIMESTAMPTZ NULL
last_attempt_at TIMESTAMPTZ NULL
delivered_at TIMESTAMPTZ NULL
dead_lettered_at TIMESTAMPTZ NULL
last_error_code TEXT NULL
created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
PRIMARY KEY (event_id, destination_id)
```

`destination_id` is an opaque, validated identifier of 1 to 100 characters using
`[a-z0-9][a-z0-9._-]*`. It is not a URL, credential, tenant identifier, or provider
configuration.

Allowed states are `PENDING`, `IN_FLIGHT`, `DELIVERED`, and `DEAD_LETTER`.
Constraints enforce the following shapes:

| State | Required | Forbidden |
| --- | --- | --- |
| `PENDING` | `next_attempt_at` | lease, delivered/dead-letter timestamp |
| `IN_FLIGHT` | lease owner, future lease, last attempt | delivered/dead-letter timestamp |
| `DELIVERED` | delivered timestamp | lease, dead-letter timestamp, error code |
| `DEAD_LETTER` | dead-letter timestamp, error code | lease, delivered timestamp |

`attempt_count` is non-negative and increments exactly once when a claim changes
the row to `IN_FLIGHT`. Outbox JSON and event identity remain immutable.

## Enqueue contract

An infrastructure caller explicitly supplies an existing `event_id`, a validated
`destination_id`, and `next_attempt_at`. Insertion is idempotent by the primary
key. A duplicate enqueue neither resets state nor changes timestamps.

TASK-0065 tests this operation but does not add a production router. Destination
registration and fan-out policy remain deferred.

## Claim contract

One database transaction selects eligible rows:

- state is `PENDING`, or state is `IN_FLIGHT` with `lease_until <= now`;
- `next_attempt_at <= now`;
- ordered by `next_attempt_at`, outbox `created_at`, `event_id`, then
  `destination_id`;
- limited to a configurable batch size from 1 through 100;
- locked using `FOR UPDATE SKIP LOCKED`.

The same transaction changes selected rows to `IN_FLIGHT`, sets a nonblank worker
identifier, sets `lease_until`, increments `attempt_count`, sets
`last_attempt_at`, clears `last_error_code`, and returns the immutable structured
CloudEvent. The default lease is 30 seconds and the accepted range is 5 seconds
through 5 minutes.

Because PostgreSQL `JSONB` preserves JSON meaning rather than original property
order, the adapter must parse `event_json` and re-emit the frozen deterministic
field order from SPEC-0007. The sink receives bytes identical to the accepted
canonical fixture; it must not receive PostgreSQL's textual rendering of JSONB.

No network call occurs in this transaction. Empty claims return immediately.
Parallel claimers must receive disjoint rows.

## Sink contract

The dispatcher passes exactly these values to a transport-neutral sink:

```text
destinationId
eventId
contentType
structuredCloudEventBytes
```

The sink returns one of:

- `Delivered`;
- `RetryableFailure(errorCode)`;
- `PermanentFailure(errorCode)`.

`errorCode` is a controlled low-cardinality value of 1 to 64 uppercase ASCII
characters plus digits and underscore. Exception messages, response bodies,
URLs, credentials, and payload fragments are never persisted.

## Settlement and lease ownership

Settlement is a conditional update matching `event_id`, `destination_id`, state
`IN_FLIGHT`, and `lease_owner`.

- `Delivered` changes state to `DELIVERED`, records `delivered_at`, and clears
  lease and error fields;
- retryable failure below the limit changes state to `PENDING`, records the safe
  error code and next attempt, and clears the lease;
- permanent failure, or retryable failure at the limit, changes state to
  `DEAD_LETTER`, records the safe code and timestamp, and clears the lease;
- stale or foreign lease settlement changes nothing and returns a conflict result.

Lease renewal is allowed only for the current owner and may extend to at most the
configured lease duration from the renewal instant.

## Retry policy

Maximum attempts: 8, including the first claim.

Retry delays after failed attempts 1 through 7 are deterministic:

```text
1m, 2m, 4m, 8m, 16m, 32m, 60m
```

The dispatcher uses an injected clock. No hidden random jitter is introduced in
TASK-0065 so reproduction remains exact. A future runtime may specify bounded
jitter before production activation.

## Concurrency and delivery guarantee

The guarantee is at least once, not exactly once. A send followed by a crash
before settlement causes redelivery after lease expiry with the same CloudEvents
`source` and `id`. Destinations must deduplicate those two values.

Ordering applies only to claim priority. Parallel workers, retries, and different
destinations may complete out of order. Consumers must not infer global business
ordering.

## Shutdown

A future host must stop new claims first, then allow active sink calls up to 30
seconds to settle. It must not release another worker's lease or mark an unknown
outcome delivered. Remaining leases recover by expiration.

TASK-0065 supplies dispatcher cancellation behavior as a library test but does
not start a production background process.

## Observability

Required low-cardinality metrics:

- eligible delivery count;
- oldest eligible age in seconds;
- claims, deliveries, retries, and dead letters totals;
- sink duration histogram;
- expired lease recovery total.

Allowed metric labels are outcome and destination class defined by a future
adapter. Event ID, assessment ID, SKU, destination instance, error message, and
payload are forbidden metric labels.

Each sink call creates one producer span named `integration.event deliver` with
event ID, event type, destination ID, and attempt number as span attributes. It
records a safe error code on failure, never structured JSON or credentials.
Transport-specific OpenTelemetry messaging attributes remain the responsibility
of a concrete adapter because current messaging semantic conventions are still
technology-specific and evolving.

## Retention

No cleanup worker is authorized in TASK-0065. The future policy is:

- delivered coordination rows: retain at least 30 days;
- dead-letter coordination rows: retain at least 90 days;
- outbox events: do not delete while any delivery row is nonterminal and retain
  at least 30 days after all currently registered deliveries are terminal;
- cleanup must be bounded, observable, separately specified, and never cascade
  into assessment deletion.

## Test plan

1. V003 applies after V001 and V002 and creates all constraints and indexes;
2. enqueue is idempotent and rejects an unknown event;
3. invalid destination identifiers are rejected;
4. claim returns exact immutable CloudEvent bytes and metadata;
5. two concurrent claimers receive disjoint rows;
6. batch size and deterministic ordering are enforced;
7. network work occurs after the claim transaction commits;
8. successful settlement is terminal and clears its lease;
9. retry follows every exact delay and increments attempts once per claim;
10. attempt eight dead-letters a retryable failure;
11. permanent failure dead-letters immediately;
12. expired leases are reclaimed and use the same event ID;
13. unexpired leases are not stolen;
14. stale and foreign owners cannot renew or settle;
15. cancellation stops new claims and never invents success;
16. error messages, payloads, credentials, SKU, and reasoning never enter delivery
    state, metrics, logs, or spans;
17. outbox `published_at` remains null;
18. public API, OpenAPI, Marketplace Operations, and Kernel remain unchanged;
19. the repository build remains green.

## References

- PostgreSQL 18 `SELECT`: `SKIP LOCKED` is appropriate for multiple consumers of
  a queue-like table and explicit ordering is required for deterministic limits:
  https://www.postgresql.org/docs/18/sql-select.html
- PostgreSQL 18 `UPDATE`: a CTE plus ordered row locking supports bounded update
  batches and `SKIP LOCKED` avoids worker contention:
  https://www.postgresql.org/docs/18/sql-update.html
- CloudEvents core specification: duplicate delivery retains the same
  `source` plus `id`: https://github.com/cloudevents/spec/blob/main/cloudevents/spec.md
- OpenTelemetry messaging spans remain transport-aware and define producer send
  semantics: https://opentelemetry.io/docs/specs/semconv/messaging/messaging-spans/

## Acceptance

Merging ADR-0008 and SPEC-0008 authorizes TASK-0065 only. Production activation,
destination registration, credentials, transports, public control surfaces,
manual replay, and cleanup require separate accepted specifications.
