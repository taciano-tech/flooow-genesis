# SPEC-0018: Canonical Inventory Candidate Comparison

Status: Proposed

Date: 2026-08-13

## Objective

Define the smallest implementation after V012 that classifies one immutable
candidate snapshot by canonical measure and exact rational equality without
authority, freshness, tolerance, reconciliation, aggregation, rounding, or
business-stock semantics.

## Authorized next implementation

Acceptance authorizes TASK-0086 only:

1. add a pure `applications:inventory-candidate-comparison` Kotlin module;
2. depend only on `applications:inventory-candidate-snapshot`;
3. define a deterministic comparator and closed controlled results;
4. classify single candidate, measure mismatch, exact agreement, exact
   divergence, and integrity failure;
5. test exact rational, signed, zero, ordering, redaction, and boundary behavior;
6. leave persistence, migrations, runtime, API/OpenAPI, connectors, providers,
   Marketplace Operations, Kernel, assessments, events, and deployment unchanged.

No ID generator, clock, repository, SQL, HTTP, JSON, filesystem, environment,
logging framework, credential, provider adapter, scheduler, worker, public route,
source rank, authority, staleness threshold, tolerance, percentage, severity,
winner, fallback, aggregation, conversion, rounding, business availability,
mutation, recommendation, event, or action is authorized.

## Input

The comparator accepts one `CanonicalInventoryCandidateSnapshotView` already
validated by the V012 reader. It accepts no independent organization, target,
member, quantity, measure, timestamp, connection, or policy override.

It defensively verifies:

- positive header member count equal to the member list size;
- every member belongs to the header organization and snapshot ID;
- every member has the exact header target, including null-safe location;
- lineage roots are unique;
- members are in canonical unsigned UUID representation order.

Failure is controlled `IntegrityFailure`; there is no repair, fallback, or scan.

## Results

```text
CanonicalInventoryCandidateComparisonResult
  SingleCandidate(snapshotId, measure, exactQuantity)
  MeasureMismatch(snapshotId, memberCount, distinctMeasureCount)
  ExactAgreement(snapshotId, memberCount, measure, exactQuantity)
  ExactDivergence(snapshotId, memberCount, measure, distinctQuantityCount)
  IntegrityFailure
```

Snapshot IDs remain redacted wrappers. Result `toString` methods expose no ID,
measure, quantity, target, organization, connection, lineage, provenance, or
principal. Counts may be rendered.

`SingleCandidate` carries its unchanged internal measure and exact quantity for a
later policy, but makes no agreement claim. `MeasureMismatch` exposes only counts,
not an ordering. `ExactDivergence` exposes only the number of distinct exact
values, not a winner or distance.

## Algorithm

1. validate the complete snapshot view defensively;
2. if exactly one member exists, return `SingleCandidate`;
3. calculate the set of canonical measures;
4. if more than one measure exists, return `MeasureMismatch` without comparing
   quantities;
5. calculate the set of exact reduced rational quantities;
6. one distinct quantity returns `ExactAgreement`;
7. more than one distinct quantity returns `ExactDivergence`.

Collection order never changes the result. No arithmetic, timestamp, epsilon,
floating-point number, decimal formatting, source metadata, or current lifecycle
state participates.

## Module boundary

`applications:inventory-candidate-comparison` is pure Kotlin/JDK. Its only direct
project dependency is `applications:inventory-candidate-snapshot`. A build-time
dependency guard rejects any other project dependency, including Kernel.

It exports no repository or service with side effects and opens no connection,
credential, provider, database, network, file, process, clock, or random source.

## Test plan

TASK-0086 proves at least:

1. allowed dependency graph and no Kernel dependency;
2. one candidate is `SingleCandidate`, not agreement;
3. different measures are `MeasureMismatch` before quantity comparison;
4. two equal positive rationals are `ExactAgreement`;
5. equivalent reduced rationals compare equal;
6. equal negative rationals compare equal;
7. equal zero values compare equal;
8. unequal signed or rational values are `ExactDivergence`;
9. distinct quantity count is exact and carries no rank;
10. input/member order has no classification semantics;
11. duplicate lineage or divergent scope fails integrity validation;
12. result rendering exposes no sensitive value;
13. no authority, freshness, tolerance, aggregation, rounding, or business stock
    is introduced;
14. complete repository build and persistent runtime package remain green.

## Remaining boundary

Source authority, ownership, source health, wall-clock staleness, provider
succession, tolerance, materiality, severity, reconciliation choice, aggregation,
location/channel rollup, unit conversion, rounding, business availability,
inventory mutation, economic truth, profit, supplier, pricing, capital,
simulation, recommendation, approval, action, outcome, and organizational
learning require later accepted specifications.

## Acceptance

Merging ADR-0018 and SPEC-0018 authorizes TASK-0086 only. It changes no runtime
behavior and authorizes no Kernel modification or business decision.
