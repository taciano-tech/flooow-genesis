# SPEC-0017: Canonical Inventory Candidate Snapshot

Status: Proposed

Date: 2026-08-13

## Objective

Define the smallest implementation after V011 that freezes an explicit set of
currently resolved canonical inventory candidates for one exact target without
ranking, comparing, reconciling, aggregating, rounding, or declaring business
stock.

## Authorized next implementation

Acceptance authorizes TASK-0084 only:

1. add a pure `applications:inventory-candidate-snapshot` Kotlin module;
2. define snapshot/request IDs, trusted principal, exact target, immutable
   snapshot/member read models, controlled results, repository, and service;
3. add additive PostgreSQL migration V012 for immutable snapshot headers and
   frozen member provenance references;
4. implement transactional explicit capture, request replay, lookup, and exact
   frozen-member reading;
5. test atomicity, exact target agreement, idempotency, concurrency, historical
   reproducibility, isolation, immutability, and privacy;
6. leave Kernel, runtime composition, connectors, providers, API/OpenAPI,
   Marketplace Operations, assessments, events, delivery, and deployment
   behavior unchanged.

No provider adapter, credential use, external call, automatic discovery, broad
scan, source rank, authority, staleness threshold, comparison, tolerance,
reconciliation result, aggregation, formula, fallback, rounding, business
availability, mutation, economic metric, recommendation, event, worker,
scheduler, or public administration is authorized.

## Module boundary

`applications:inventory-candidate-snapshot` is a pure Kotlin/JDK module. Its only
project dependencies are:

- `platform:foundation:organization-context`;
- `applications:integration-control-plane`;
- `applications:inventory-identity-mapping`;
- `applications:inventory-canonical-observation`;
- `applications:inventory-source-acceptance`;
- `applications:inventory-measure-selection`.

It has no dependency on Kernel, Marketplace Operations, API, persistence,
connector runtime, ingestion, HTTP, JSON, SQL, logging framework, provider,
scheduler, filesystem, environment, or cryptography.

## Canonical values

The module exports canonical lowercase UUID-backed values:

```text
CanonicalInventoryCandidateSnapshotId
CanonicalInventoryCandidateSnapshotRequestId
CanonicalInventoryCandidateSnapshotCorrelationId
```

Their `toString` is `[INTERNAL]`; explicit UUID access exists only for
persistence adapters.

`InventoryCandidateSnapshotPrincipalReference` is NFC normalized, already
trimmed, contains no ISO control character, occupies 1..128 UTF-8 bytes, and
renders `[REDACTED]`.

## Exact target

```text
CanonicalInventoryCandidateTarget(
  itemId,
  locationId?,
  unitId
)
```

Equality is exact and organization-scoped. Nullable location uses null-safe
equality. Null is a real location-less target and never a wildcard.

## Capture request

```text
CaptureCanonicalInventoryCandidates(
  organizationId,
  requestId,
  target,
  lineageRootDecisionIds,
  principalReference,
  correlationId
)
```

`lineageRootDecisionIds` is a non-empty set. Duplicates are rejected at the
input boundary. Collection iteration order is non-semantic. The request contains
no connection, selection, measure, quantity, acceptance, observation, mapping
leaf, source pointer, revision, timestamp, provider key, or source reference.

## Snapshot

```text
CanonicalInventoryCandidateSnapshot(
  id,
  organizationId,
  requestId,
  target,
  principalReference,
  correlationId,
  capturedAt,
  memberCount
)
```

The snapshot is immutable. It has no active state, revision, predecessor,
replacement, retirement, or deletion operation.

## Frozen member

```text
CanonicalInventoryCandidateSnapshotMember(
  organizationId,
  snapshotId,
  connectionId,
  capability,
  lineageRootDecisionId,
  selectionId,
  selectionRevision,
  acceptanceId,
  acceptanceRevision,
  observationId,
  sourcePointer,
  projectionRevision,
  mappingDecisionId,
  mappingRevision,
  target,
  measure,
  exactQuantity
)
```

The member read model has the same provenance and exact quantity semantics as
`SelectedCanonicalInventoryMeasure`, plus its snapshot ID. It contains no source
item/location text, provider unit text, provider time, source commit time,
principal, payload, credential, rank, score, authority, freshness judgment, or
comparison result.

V012 persists every field above except `exactQuantity` and `sourcePointer` fields
that can be validated through the frozen V008 observation. The adapter may copy
source pointer and provenance references needed for independent integrity
constraints, but it never copies a quantity.

## Capture algorithm

Inside one transaction the repository:

1. checks request-ID replay before validating lifecycle or creating content;
2. for a new request, validates active organization and same-organization target
   identities;
3. orders lineage root UUIDs by the same unsigned 16-byte order used by
   PostgreSQL and locks every exact V007 root;
4. resolves each root through the current V011 resolver;
5. requires every resolved candidate to match the requested organization and
   exact target;
6. rejects unavailable, duplicated, foreign, divergent, or impossible members;
7. inserts one snapshot header and exactly one member per requested lineage;
8. commits all rows atomically.

Capture permits candidates from `ACTIVE` or `SUSPENDED` connections because it
is a deliberate offline evidence operation. Draft, revoked, unknown, or foreign
connections fail closed. Capture opens no credential or provider.

No timestamp chooses a member or establishes order. Transaction time records
capture only.

## Request replay

`(organizationId, requestId)` is unique.

```text
same request ID + same exact target + same lineage set
  => AlreadyCaptured(snapshotId, memberCount)

same request ID + different target or lineage set
  => Conflict
```

Principal, correlation, and capture time do not change after replay. A caller
that wants a later candidate set uses a new request ID.

An identical completed request remains replayable after later organization,
connection, target, mapping, acceptance, or selection retirement because it
performs no new write. Content disagreement remains `Conflict`.

## Frozen read

`find(organizationId, snapshotId)` returns the header and members ordered by
canonical lineage-root UUID solely for deterministic representation.

For each member, the adapter joins the exact frozen V011 selection, V010
acceptance, V008 observation, and V007 mapping leaf. It validates copied scope,
revisions, target, measure, source pointer, and provenance, then reads the exact
selected rational from V008.

The read does not require those historical rows to remain active. It returns
`IntegrityFailure` for missing, duplicated, divergent, or impossible frozen
provenance. A selected field that is unexpectedly null is integrity failure,
not fallback or zero.

Historical reads remain available after later organization, connection, target,
mapping, acceptance, selection, or source lifecycle changes, subject to data
retention.

## Controlled results

Capture results are:

```text
Captured(snapshotId, memberCount)
AlreadyCaptured(snapshotId, memberCount)
CandidateUnavailable
TargetUnavailable
TargetMismatch
Conflict
IntegrityFailure
```

Read results are:

```text
Found(snapshot)
NotFound
IntegrityFailure
```

Write results expose no organization, connection, target, lineage, selection,
acceptance, observation, mapping, measure, quantity, principal, correlation, or
time beyond redacted snapshot-ID wrappers and member count.

## PostgreSQL migration V012

`integration_inventory_candidate_snapshot` contains:

```text
organization_id uuid
snapshot_id uuid
request_id uuid
target_item_id uuid
target_location_id uuid nullable
target_unit_id uuid
principal_ref text
correlation_id uuid
captured_at timestamptz
member_count integer
```

Constraints include:

- primary key `(organization_id, snapshot_id)`;
- unique `(organization_id, request_id)`;
- organization-scoped target foreign keys;
- positive member count;
- immutable content and rejected delete.

`integration_inventory_candidate_snapshot_member` contains:

```text
organization_id uuid
snapshot_id uuid
connection_id uuid
capability text
lineage_root_decision_id uuid
selection_id uuid
selection_revision integer
acceptance_id uuid
acceptance_revision integer
observation_id uuid
projection_revision integer
mapping_decision_id uuid
mapping_revision integer
target_item_id uuid
target_location_id uuid nullable
target_unit_id uuid
measure text
```

Constraints include:

- primary key `(organization_id, snapshot_id, lineage_root_decision_id)`;
- organization-scoped foreign keys to snapshot, connection, root, selection,
  acceptance, observation, mapping leaf, and target identities;
- exact capability, revision, target, and frozen provenance agreement;
- exact header/member target agreement;
- exactly `member_count` immutable members at transaction commit;
- rejected update and delete.

V012 stores no quantity, source reference, provider time, payload, credential,
rank, score, weight, priority, authority, freshness threshold, tolerance,
comparison, reconciliation status, aggregate, formula, rounded value, business
availability, assessment, economic metric, recommendation, event, or action.

## Concurrency and atomicity

- lineage roots are locked in canonical unsigned 16-byte UUID order shared by
  Kotlin and PostgreSQL to prevent deadlocks;
- current acceptance and selection are resolved only after their root lock;
- one request ID creates at most one immutable snapshot;
- concurrent identical replay yields one `Captured` and one
  `AlreadyCaptured`;
- request-ID content disagreement yields `Conflict`;
- any member failure rolls back the header and every member;
- database constraints independently reject partial or divergent snapshots.

## Privacy and observability

- IDs render `[INTERNAL]`;
- principal, snapshot, and member renderings are redacted;
- exact quantities never appear in write results, exceptions, or logs;
- SQL and database messages translate to controlled outcomes;
- organization predicates are explicit on every lookup and join;
- no credential, vault, protector, provider, connector, external system, or
  Kernel component is opened by capture or reading.

## Test plan

TASK-0084 proves at least:

1. pure dependency graph and no Kernel dependency;
2. canonical UUID parsing and redaction;
3. principal normalization and UTF-8 bounds;
4. V012 applies after V001 through V011;
5. one explicit active candidate can be captured;
6. multiple connections with the exact same target can be captured;
7. null location matches only null location;
8. cross-item, cross-location, and cross-unit membership fails closed;
9. callers cannot claim measure, quantity, acceptance, or provenance;
10. quantity is absent from V012 and reconstructed exactly from V008;
11. negative, zero, and rational values remain exact;
12. member order has no rank semantics and reads deterministically;
13. identical request replay returns the same snapshot;
14. request-ID disagreement conflicts;
15. unavailable selection, acceptance, measure, mapping, or target changes
    nothing;
16. concurrent lineage replacement cannot tear one member's provenance;
17. Kotlin and PostgreSQL use the same canonical unsigned UUID lock order and
    prevent competing multi-lineage deadlocks;
18. concurrent identical capture creates one snapshot;
19. header/member count mismatch is rejected at commit;
20. direct update and delete are rejected;
21. foreign organization and connection references fail closed;
22. historical read and identical replay survive later lifecycle retirement,
    acceptance/selection replacement, or withdrawal;
23. historical divergence or missing provenance is `IntegrityFailure`;
24. renderings and controlled results expose no sensitive values;
25. no source authority, rank, freshness, comparison, aggregation, formula, or
    business stock is introduced;
26. runtime, API/OpenAPI, connectors, Kernel, assessments, events, and
    deployment remain unchanged;
27. complete repository build and persistent runtime package remain green.

## Remaining boundary

Automatic membership discovery, source ownership, authority, source health,
wall-clock staleness, provider-specific succession, tolerance, equality,
conflict classification, reconciliation choice, aggregation, location/channel
rollup, unit conversion, rounding, business availability, conversion to
`InventorySnapshot` or `InventoryRiskInput`, inventory mutation, economic truth,
profit, supplier, pricing, purchase, capital, simulation, recommendation,
approval, action, outcome, and organizational learning require later accepted
specifications.

## Acceptance

Merging ADR-0017 and SPEC-0017 authorizes TASK-0084 only. It changes no runtime
behavior and authorizes no Kernel modification or business decision.
