# SPEC-0007: Inventory Assessment Recorded Integration Event

**Status:** Proposed

**Date:** 2026-08-10

**Source decision:** ADR-0007

## Objective

Persist one versioned CloudEvents representation of every successfully recorded
inventory-risk assessment in the same PostgreSQL transaction as the assessment,
without publishing it externally yet.

## Authorized implementation

Acceptance authorizes TASK-0063 only:

1. add a versioned PostgreSQL outbox migration;
2. create one immutable application integration-event representation;
3. serialize `InventoryRiskAssessmentRecorded.v1` as CloudEvents structured JSON;
4. insert assessment and event atomically;
5. expose outbox reads only to test and future adapter infrastructure, not the
   public business API;
6. add compatibility, atomicity, idempotency, privacy, and reproduction tests;
7. document retention and future-dispatch boundaries.

No broker, webhook, connector, dispatcher, retry worker, CloudEvents HTTP
endpoint, Marketplace Operations public API change, or Kernel change is
authorized.

## CloudEvents contract

The event uses CloudEvents 1.0.2 semantics and the `specversion` value `1.0` in
structured JSON format.

Required envelope:

```json
{
  "specversion": "1.0",
  "id": "7f578731-c11f-4a06-8fce-0ab16fe77967",
  "source": "https://flooow.io/marketplace-operations",
  "type": "io.flooow.marketplace.inventory-risk-assessment.recorded.v1",
  "subject": "/inventory-risk-assessments/773afbc1-6e04-41ef-9f30-0974d7b31a90",
  "time": "2026-08-10T13:00:00Z",
  "datacontenttype": "application/json",
  "dataschema": "https://flooow.io/schemas/events/inventory-risk-assessment-recorded.v1.json",
  "data": {
    "assessmentId": "773afbc1-6e04-41ef-9f30-0974d7b31a90",
    "sku": "RED-MOTO-001",
    "observedOn": "2026-08-10",
    "shortageProjected": true,
    "unitsAtRiskAgainstGoal": 225,
    "recommendationType": "EXPEDITE_REPLENISHMENT",
    "expectedUnitsPreserved": 135
  }
}
```

Semantics:

- `id` is a canonical random UUID identifying the integration event, not the
  assessment;
- `source` is constant and, with `id`, forms the deduplication key;
- `type` contains the incompatible contract version;
- `subject` identifies the assessment within the source;
- `time` equals the committed assessment `recordedAt`, not dispatch time;
- `dataschema` is a stable identifier; serving a schema endpoint is deferred;
- `data` is the minimal integration fact and is not the public HTTP response.

No extension attribute is authorized in v1. In particular, no tenant, user,
credential, trace context, delivery destination, retry count, or broker metadata
is invented.

## Payload contract

The `data` object has exactly seven fields:

| Field | Type | Rule |
| --- | --- | --- |
| `assessmentId` | canonical UUID string | equals the committed assessment ID |
| `sku` | string | equals the validated application SKU |
| `observedOn` | ISO-8601 date string | equals the assessment observation date |
| `shortageProjected` | boolean | equals the committed projection |
| `unitsAtRiskAgainstGoal` | non-negative integer | equals the committed projection |
| `recommendationType` | string enum | committed recommendation type |
| `expectedUnitsPreserved` | non-negative integer | committed recommendation value |

Unknown fields are not emitted. The event excludes request totals not needed by
consumers, expected-impact prose, recommendation explanation, reasoning trace,
internal judgment/evidence objects, database metadata, authentication data, and
transport details.

## Serialization

- UTF-8 JSON;
- deterministic field order matching the frozen fixture;
- no insignificant leading or trailing bytes;
- no `null` values;
- integer values remain JSON numbers;
- timestamps use UTC RFC 3339 with `Z`;
- content type for the stored envelope is
  `application/cloudevents+json; charset=UTF-8`;
- the serialized event is immutable after insertion.

The official CloudEvents Java SDK may be used only in the application adapter if
its pinned license and transitive dependencies are accepted. A hand-written
envelope is also permitted if all frozen conformance tests pass. Neither option
may enter Marketplace Operations or the Kernel.

## Transactional outbox

The migration creates `integration_event_outbox` with, at minimum:

```text
event_id UUID PRIMARY KEY
event_source TEXT NOT NULL
event_type TEXT NOT NULL
subject TEXT NOT NULL
occurred_at TIMESTAMPTZ NOT NULL
content_type TEXT NOT NULL
event_json JSONB NOT NULL
created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
published_at TIMESTAMPTZ NULL
```

Constraints:

- unique `(event_source, event_id)`;
- `event_type`, `source`, `subject`, `time`, and `id` columns agree with
  `event_json`;
- `published_at` is the only field a future dispatcher may mutate;
- there is an index over `(published_at, created_at, event_id)` for deterministic
  undispatched scans;
- event JSON and assessment row are inserted in the same transaction;
- any failure inserting either record rolls back both;
- retrying the same application append must not create a second assessment or
  event for the same assessment ID.

The first implementation creates exactly one outbox event per committed
assessment. Ordering is defined only by `(created_at, event_id)` and is not a
global business-order guarantee.

## Delivery boundary

TASK-0063 does not claim, lease, publish, retry, or mark records as published.
`published_at` remains `NULL`. This deliberately proves event production before
delivery mechanics.

A future dispatcher specification must define claiming, concurrency, retry,
backoff, dead-letter behavior, shutdown, metrics, tracing, and retention. It may
use a broker, webhook, CDC, or connector adapter without changing the accepted
event contract.

## Compatibility

- additive optional data fields require a reviewed v1 compatibility decision;
- removing, renaming, retyping, or changing semantics creates a new event type
  suffix such as `.v2`;
- database migrations never rewrite previously stored event JSON;
- consumers route by the complete event `type` and ignore types they do not
  support;
- `source` is stable across deployment environments;
- staging and production are distinguished by transport configuration, not by
  changing event semantics.

## Privacy and authority

The event is operational business data and must be authenticated and authorized
when later delivered. The outbox is not exposed through the current HTTP API.

The event contains no service token, password, user, tenant, pricing, margin,
supplier, financial, customer, address, or free-form reasoning content. Its
recommendation is advisory; consuming the event does not authorize changing
inventory, price, order, or marketplace state.

## Test plan

1. migration creates the constrained outbox schema and index;
2. successful assessment append creates exactly one outbox row;
3. assessment and event commit in one transaction;
4. forced assessment failure leaves no outbox row;
5. forced outbox failure leaves no assessment row;
6. retry cannot create a second event for one assessment ID;
7. frozen event JSON is byte-identical;
8. envelope contains all required CloudEvents attributes and no extensions;
9. `source` plus `id` is unique;
10. `subject`, `time`, and payload agree with the assessment;
11. schema identifier and versioned type are exact;
12. unknown or internal assessment fields are absent;
13. token, password, reasoning trace, explanation, and expected-impact prose are
    absent from JSON, logs, artifacts, and traces;
14. `published_at` remains null;
15. deterministic undispatched ordering is reproduced;
16. public POST/GET/OpenAPI representations remain unchanged;
17. repository build and frozen snapshots remain green;
18. Marketplace Operations and Kernel production sources remain unchanged.

## Rollout and rollback

Deploy the additive migration before the application version that writes outbox
events. Existing assessments are not backfilled because an event represents an
occurrence at its original commit boundary, not retrospective reconstruction.

Rollback stops producing new events and may leave the additive table in place.
Committed event records are never deleted as part of application rollback. No
consumer may be enabled until a separate delivery specification is accepted.

## Out of scope

- event broker, topic, queue, routing key, partition, or exchange;
- webhooks, connectors, marketplace, ERP, or carrier integrations;
- dispatcher, polling worker, CDC process, retries, DLQ, or delivery logs;
- tenant and credential models;
- notification, BI, workflow, OMS, pricing, fiscal, or AI consumers;
- generic Connector SDK or mapping language;
- universal Event, Process, State, or Entity changes in the Kernel;
- event sourcing, assessment reconstruction, and historical backfill;
- public schema registry and subscription API.

## Acceptance

Merging ADR-0007 and SPEC-0007 authorizes TASK-0063 only. Any delivery mechanism,
consumer, connector, workflow, tenant concept, or Kernel change requires a new
accepted specification.
