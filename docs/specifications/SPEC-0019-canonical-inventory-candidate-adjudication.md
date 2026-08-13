# SPEC-0019: Canonical Inventory Candidate Adjudication

Status: Proposed

Date: 2026-08-13

## Objective

Define the smallest implementation after TASK-0086 that immutably records one
trusted, explicit member choice from one V012 snapshot without automatic source
authority, arithmetic, or business-stock semantics.

## Authorized next implementation

Acceptance authorizes TASK-0088 only:

1. add pure `applications:inventory-candidate-adjudication` Kotlin contracts;
2. depend on organization context, candidate snapshot, and candidate comparison;
3. define canonical IDs, bounded principal, controlled reasons, commands,
   decisions, results, repository, and service;
4. add additive PostgreSQL migration V013 for immutable adjudication references;
5. implement transactional explicit adjudication, replay, conflict, and frozen
   read behavior;
6. test scope, reason/comparison agreement, idempotency, concurrency, lifecycle
   independence, immutability, privacy, and exact provenance reconstruction;
7. leave Kernel, runtime, API/OpenAPI, connectors, providers, Marketplace
   Operations, assessments, events, and deployment unchanged.

No automatic selection, rank, score, priority, reusable authority, ownership,
source-health rule, freshness threshold, tolerance, percentage, severity,
aggregation, unit conversion, rounding, clamp, business availability, inventory
mutation, recommendation, approval workflow, event, notification, worker,
scheduler, provider call, credential use, or public route is authorized.

## Canonical values

```text
CanonicalInventoryCandidateAdjudicationId
CanonicalInventoryCandidateAdjudicationRequestId
CanonicalInventoryCandidateAdjudicationCorrelationId
```

Values wrap canonical lowercase UUIDs, expose UUIDs only for persistence, and
render `[INTERNAL]`.

`InventoryCandidateAdjudicationPrincipalReference` is NFC normalized, already
trimmed, contains no ISO control character, occupies 1..128 UTF-8 bytes, and
renders `[REDACTED]`.

## Command

```text
AdjudicateCanonicalInventoryCandidate(
  organizationId,
  requestId,
  snapshotId,
  chosenLineageRootDecisionId,
  reason,
  principalReference,
  correlationId
)
```

The command contains no connection, target, measure, quantity, member count,
comparison kind, selection, acceptance, observation, mapping leaf, source
pointer, revision, provider value, source time, or decision time.

## Reasons and comparison agreement

```text
SINGLE_CANDIDATE_CONFIRMATION -> SingleCandidate
EXACT_AGREEMENT_CONFIRMATION -> ExactAgreement
MEASURE_POLICY_REVIEW -> MeasureMismatch
EVIDENCE_QUALITY_REVIEW -> ExactDivergence
CONTROLLED_EXCEPTION -> MeasureMismatch | ExactDivergence
```

Any other pairing returns `ReasonMismatch`. `IntegrityFailure` comparison cannot
be adjudicated.

The chosen lineage must occur exactly once in the snapshot. Missing or duplicate
membership returns `CandidateUnavailable` or `IntegrityFailure`. The repository
loads all member facts from V012; callers cannot claim them.

## Decision and read model

```text
CanonicalInventoryCandidateAdjudication(
  id,
  organizationId,
  requestId,
  snapshotId,
  chosenLineageRootDecisionId,
  reason,
  principalReference,
  correlationId,
  decidedAt
)
```

```text
AdjudicatedCanonicalInventoryCandidate(
  adjudication,
  comparison,
  chosenMember
)
```

The read model contains the exact immutable V012 member, including its unchanged
V008 rational quantity and frozen provenance. The adjudication stores none of
that duplicated content. Rendering is redacted.

## Controlled results

Write results:

```text
Adjudicated(adjudicationId)
AlreadyAdjudicated(adjudicationId)
SnapshotUnavailable
CandidateUnavailable
ReasonMismatch
Conflict
IntegrityFailure
```

Read results:

```text
Found(adjudicatedCandidate)
NotFound
IntegrityFailure
```

No result rendering exposes organization, snapshot, member, lineage, reason,
measure, quantity, connection, provenance, principal, correlation, or time.

## Replay and concurrency

- `(organizationId, requestId)` is unique;
- `(organizationId, snapshotId)` is unique;
- identical request content returns the same adjudication;
- request disagreement returns `Conflict`;
- a second request for an already adjudicated snapshot returns `Conflict`;
- snapshot header and chosen member are locked before insertion;
- a new adjudication requires the owning organization to be active;
- no current connection, target, mapping, acceptance, selection, or source
  lifecycle state participates in a new adjudication;
- concurrent decisions create exactly one row;
- completed identical replay occurs before current lifecycle validation;
- any failure changes nothing.

## V013

`integration_inventory_candidate_adjudication` contains:

```text
organization_id uuid
adjudication_id uuid
request_id uuid
snapshot_id uuid
chosen_lineage_root_decision_id uuid
reason text
principal_ref text
correlation_id uuid
decided_at timestamptz
```

Constraints include organization-scoped primary and unique keys, foreign keys to
the V012 snapshot and exact chosen member, controlled reason and principal checks,
deferred reason/comparison agreement validation, immutable update rejection, and
delete rejection.

V013 stores no quantity, measure, target, connection, selection, acceptance,
observation, mapping leaf, source pointer, copied provenance, comparison kind,
rank, score, authority, freshness, tolerance, aggregate, business value,
recommendation, event, or action.

## Test plan

TASK-0088 proves at least:

1. pure contract dependency graph and no Kernel dependency;
2. canonical UUID parsing, principal bounds, and redaction;
3. V013 applies after V001 through V012;
4. each valid reason/comparison pairing can be adjudicated;
5. every invalid pairing returns `ReasonMismatch`;
6. chosen lineage must be a frozen member;
7. caller cannot claim measure, quantity, comparison, or provenance;
8. identical request replay returns the same adjudication;
9. request disagreement and second snapshot decision conflict;
10. concurrent decisions create exactly one adjudication;
11. read reconstructs the exact frozen member and V008 rational;
12. negative, zero, and rational quantities remain exact;
13. new adjudication requires an active organization but no current source
    lifecycle state;
14. historical read and replay survive lifecycle retirement;
15. foreign organization and divergent provenance fail closed;
16. direct update/delete and invalid SQL inserts are rejected;
17. V013 contains no quantity or business-stock field;
18. renderings and controlled results expose no sensitive values;
19. no automatic authority, ranking, freshness, tolerance, aggregation, or
    business stock is introduced;
20. complete repository build and persistent runtime package remain green.

## Remaining boundary

Reusable source authority, ownership, source health, staleness, provider
succession, tolerance, materiality, automated reconciliation, aggregation,
location/channel rollup, conversion, rounding, business availability, inventory
mutation, economic truth, supplier, pricing, capital, simulation,
recommendation, approval, event, action, outcome, and organizational learning
require later accepted specifications.

## Acceptance

Merging ADR-0019 and SPEC-0019 authorizes TASK-0088 only. It changes no runtime
behavior and authorizes no Kernel modification or business-stock decision.
