# RFC-0007: Durable Evidence Incremental Change Feed (P0.3 Boundary)

**Version:** 0.3

**Status:** Draft for Architectural Review (Pre-ADR)

## Objective

Define the smallest proposed boundary required to read committed durable
evidence updates incrementally, ordered, and resumably per organization — the
prerequisite P0.3 needs before any projection, materialization, or read API can
exist — without widening `MarketplaceIndependentEconomicEvidenceRepository`
or reopening V015.

This RFC is pre-ADR, pre-SPEC, and pre-TASK. It proposes concepts and a
two-slice validation plan. It does not authorize production implementation, a
materialized projection table, an API, a UI, or any provider work.
Experimental evidence is now sufficient for architectural review of Slice A.
Human review of this RFC remains the next gate before any ADR may be created;
no production boundary is authorized here.

## Context

TASK-0144 (P0.2) deliberately kept `change_sequence` out of the domain, out
of the economic port, and out of every public persist/read result. ADR-0045
already names `change_sequence` as "the durable, resumable cursor a future
P0.3 projection reads directly from the evidence tables," and SPEC-0044's
test 34 proves this query shape works and is stable:

```sql
WHERE organization_id = ? AND change_sequence > :checkpoint
ORDER BY change_sequence
```

The physical PostgreSQL sequence behind `change_sequence` is global. The
ordering guarantee relevant to this proposed feed is per organization. This
RFC does not claim global commit ordering between different organizations.

Reconnaissance against `main` at `61560bb` confirms three things:

1. That query pattern exists today only inside
   `PostgresMarketplaceIndependentEconomicEvidenceRepositoryTest.kt` as test
   scaffolding — no production boundary exposes it.
2. Two existing patterns are structurally adjacent but not directly reusable:
   `PostgresCanonicalInventoryObservationRepository.project()` has a
   transactional/conflict/reconstruction shape but operates on inventory, not
   economic evidence; `integration_connector_progress` has a
   checkpoint-with-lock shape but tracks inbound connector ingestion progress,
   not outbound consumption of an internal durable journal.
3. No mechanism exists today to discover which organizations have pending
   changes relative to a named projection checkpoint.

Building a projection or API directly on top of raw SQL against
`marketplace_economic_evidence_update` — as the test file does today — would
either leak persistence detail into whatever consumes it or tempt someone to
add another method to `MarketplaceIndependentEconomicEvidenceRepository`,
which SPEC-0044 deliberately kept limited to `find`/`apply`.

## Problem Statement

Given the existing, unmodified `marketplace_economic_evidence_update` and
`marketplace_economic_evidence_subject` tables created by V015, EXP-0006
experimentally validated a proposed boundary containing:

1. an incremental change feed that returns bounded evidence-change
   invalidations after a checkpoint, ordered by `change_sequence`, for one
   organization at a time;
2. a checkpoint model that lets independent named projections consume the
   same durable journal at different paces without interference;
3. explicit checkpoint inspection and compare-and-set advancement;
4. a candidate discovery query for organizations with changes pending relative
   to a named projection, including organizations that have no checkpoint row.

## Non-Goals

- No materialized Sales Intelligence read model, table, or schema in this RFC.
  That is P0.3 Slice B and requires separate authorization.
- No historical snapshot feed and no exact reconstruction of the aggregate at
  the version named by a change.
- No evidence-timeline or exact historical-replay solution.
- No API or UI. No provider work (Mercado Livre, Omie). No Economic Truth
  materialization, Ledger, or Reconciliation change.
- No modification to V015,
  `MarketplaceIndependentEconomicEvidencePersistence.kt`, or
  `PostgresMarketplaceIndependentEconomicEvidenceRepository.kt`.
- No generalized event bus, outbox, or pub/sub mechanism. The proposed feed is
  pull-based.
- No V016 or other production migration is authorized by this RFC or EXP-0006.
- No scheduling-fairness mechanism, lease, claim, worker ownership,
  `SKIP LOCKED`, retry-after, failure counter, round-robin cursor, last-served
  organization, or scheduler policy.

## Slice A Semantics: Invalidation / Change Feed

Slice A is an **invalidation/change feed**. Each returned change states that an
aggregate changed and supplies its complete
`MarketplaceEconomicEvidenceSubject`, evidence version, sequence, and change
kind. It does not carry the fact/component payload and does not represent an
exact historical snapshot at that version.

A consumer may follow this path:

```text
change
  -> complete subject
  -> MarketplaceIndependentEconomicEvidenceRepository.find(subject)
  -> current aggregate state
```

`find(subject)` may return a version later than the `evidenceVersion` that
originated the change. A batch may contain multiple changes for the same
subject while each subsequent `find(subject)` observes the same latest state.
Slice A therefore does not claim to reproduce each intermediate aggregate
state or an exact evidence timeline. Slice B must address those requirements
if its materialization contract needs them.

The complete subject is obtained in the feed adapter by joining the update
journal with `marketplace_economic_evidence_subject`. No second subject lookup
is required from the consumer, and no P0.2 contract or V015 object is modified.

## Candidate Concepts

### `MarketplaceEconomicEvidenceChangeFeed`

A new, narrow port, separate from
`MarketplaceIndependentEconomicEvidenceRepository` and never merged into it:

```kotlin
interface MarketplaceEconomicEvidenceChangeFeed {
    fun changesSince(
        organizationId: OrganizationId,
        checkpoint: ChangeSequenceCheckpoint,
        limit: Int
    ): List<MarketplaceEconomicEvidenceChange>

    fun organizationsWithPendingChanges(
        projectionName: ProjectionName,
        limit: Int
    ): List<OrganizationId>

    fun currentCheckpoint(
        organizationId: OrganizationId,
        projectionName: ProjectionName
    ): ChangeSequenceCheckpoint

    fun advanceCheckpoint(
        organizationId: OrganizationId,
        projectionName: ProjectionName,
        expected: ChangeSequenceCheckpoint,
        next: ChangeSequenceCheckpoint
    ): CheckpointAdvanceResult
}
```

Both collection-returning operations require a positive, bounded `limit`.
`changesSince` is deterministically ordered by ascending `change_sequence`
within the requested organization. Repeated calls using the last returned
sequence as the next checkpoint form deterministic forward pagination.
`organizationsWithPendingChanges` is a bounded discovery operation with stable
deterministic ordering. That ordering identifies work; it does not guarantee
fair scheduling across polling cycles.

### `MarketplaceEconomicEvidenceChange`

The proposed change contains:

```text
subject: MarketplaceEconomicEvidenceSubject
evidenceVersion: MarketplaceEconomicEvidenceVersion
changeSequence: ChangeSequenceCheckpoint
changeKind: MarketplaceEconomicEvidenceChangeKind
```

The `subject` is complete, including its organization, marketplace order,
marketplace identity, external order identity, and currency. The change does
not expose an evidence payload.

### `ChangeSequenceCheckpoint`

A proposed immutable, validated value with `NONE`/`ZERO` equal to zero and an
explicit persistence accessor. It is not `@JvmInline`, is comparable, accepts
only non-negative values, and does not expose another public raw-value
accessor.

The underlying PostgreSQL sequence is global and may contain gaps. The
checkpoint is interpreted only in conjunction with `organizationId`; it does
not assert cross-organization commit ordering.

### `ProjectionName`

A proposed immutable, validated identifier:

- not blank;
- no more than 100 characters;
- matches `^[a-z0-9][a-z0-9-]*$`.

Different projection names identify independent consumers of the same journal.

### `CheckpointAdvanceResult`

A closed result family:

- `Advanced`;
- `Stale`;
- `Regression`.

`advanceCheckpoint` uses explicit compare-and-set semantics with `expected`
and `next`:

- `next <= expected` returns `Regression` without a write;
- exactly matching durable state may advance to `next` and return `Advanced`;
- a concurrently changed durable value returns `Stale`;
- no regression is silent.

The initial checkpoint for an organization/projection pair with no durable row
is `NONE`. Creation of the first row participates in the same compare-and-set
semantics and must be safe under concurrent first writers.

Reading never advances the checkpoint. `changesSince` and
`organizationsWithPendingChanges` are observational operations. Explicit
checkpoint advancement supports at-least-once processing: a caller that fails
before a successful advance can read the same changes again.

## Candidate Checkpoint Persistence

The proposed durable state is a new table owned by this boundary, not by the
evidence journal:

```sql
projection_checkpoint(
  organization_id uuid NOT NULL REFERENCES integration_organization (organization_id),
  projection_name text NOT NULL,
  last_change_sequence bigint NOT NULL DEFAULT 0 CHECK (last_change_sequence >= 0),
  updated_at timestamptz(6) NOT NULL,
  PRIMARY KEY (organization_id, projection_name),
  CHECK (projection_name ~ '^[a-z0-9][a-z0-9-]{0,99}$')
)
```

The database is authoritative for `updated_at`. The candidate schema uses a
`BEFORE INSERT OR UPDATE` trigger that overwrites it with
`transaction_timestamp()`, following the timestamp-authority pattern already
validated in the repository.

No checkpoint row means `NONE` for that organization/projection pair. Rows for
two different `projection_name` values are independent even when they consume
the same organization and evidence journal.

This schema remains a proposal. EXP-0006 created it only as ephemeral SQL
inside its Testcontainers test setup. This RFC does not authorize V016.

## Experimental Validation Results — EXP-0006

EXP-0006 ran against PostgreSQL 18.4 through real Testcontainers with Flyway
V001–V015 applied, existing integrity constraints enabled, and the production
P0.2 writer used where composition with concurrent writes was required.

### Change feed semantics

The experiment validated only the following observed behaviors:

- `changesSince` returns changes above an exclusive checkpoint;
- each change contains a complete `MarketplaceEconomicEvidenceSubject`;
- pagination is bounded and deterministic;
- a repeated read does not advance a checkpoint implicitly;
- ordering is ascending by `change_sequence` within one organization;
- two projection names progress independently;
- the feed composes with the real P0.2 writer;
- no global ordering between organizations is claimed.

### Checkpoint CAS

The experiment observed:

- missing row + expected `NONE` + next greater than `NONE` -> `Advanced`;
- missing row + expected other than `NONE` -> `Stale`;
- `next <= expected` -> `Regression` without a write;
- durable checkpoint different from expected -> `Stale`;
- concurrent first writers -> exactly one `Advanced` and one `Stale`;
- concurrent writers against an existing checkpoint -> exactly one `Advanced`
  and one `Stale`.

These observations are limited to the exercised fixtures and concurrency
scenarios.

### Pending-discovery comparison

Three semantically equivalent query forms were exercised at cumulative scales
of 1,780 journal rows across 40 organizations, 68,940 rows across 120
organizations, and 316,080 rows across 240 organizations. Queries A, B, and C
returned the same ordered organization identifiers for no checkpoint, partial
checkpoint, checkpoint at maximum, mixed pending/non-pending states, two
projection names, and a limit smaller than the pending set.

Observed execution results from the final focused run were:

| Journal rows | Organizations | Query A ms | Query B ms | Query C ms | Query A buffer hits | Query B buffer hits | Query C buffer hits |
|---:|---:|---:|---:|---:|---:|---:|---:|
| 1,780 | 40 | 0.536 | 0.363 | 0.269 | 28 | 115 | 125 |
| 68,940 | 120 | 12.035 | 0.866 | 1.170 | 922 | 456 | 486 |
| 316,080 | 240 | 23.191 | 1.409 | 1.639 | 4,233 | 909 | 969 |

These are individual observed executions, not benchmark distributions or
formal complexity proofs.

## Candidate Pending-Organization Discovery

### Query B — candidate canonical pending-discovery query

The candidate for eventual ADR/SPEC review is:

```sql
SELECT o.organization_id
FROM integration_organization o
LEFT JOIN projection_checkpoint c
  ON c.organization_id = o.organization_id
 AND c.projection_name = ?
WHERE EXISTS (
    SELECT 1
    FROM marketplace_economic_evidence_update u
    WHERE u.organization_id = o.organization_id
      AND u.change_sequence >
          COALESCE(c.last_change_sequence, 0)
)
ORDER BY o.organization_id ASC
LIMIT ?
```

**CANDIDATE CANONICAL PENDING-DISCOVERY QUERY**

A, B, and C were semantically equivalent in the tested fixtures. Both B and C
used the existing V015 index on `(organization_id, change_sequence)`. As the
journal grew, their observed resource usage tracked the growth in organization
count much more closely than the growth in journal history. B used fewer
buffer hits than C in every measured fixture. Its execution time was lower at
68,940 and 316,080 rows; C had the lower execution time at 1,780 rows. The
`EXISTS` form lets PostgreSQL satisfy the presence of pending work without
explicitly obtaining every organization's latest change.

This is a query candidate for architectural review, not a production
implementation authorization.

### Query A — rejected candidate

The original shape:

```text
journal
-> GROUP BY organization_id
-> MAX(change_sequence)
```

is **REJECTED EXPERIMENTALLY** as the production pending-discovery candidate.
In the tested fixtures PostgreSQL aggregated the historical journal, and
observed resource usage scaled approximately with journal volume across the
tested fixtures. This statement records the observed plans and measurements;
it is not a formal Big-O proof.

### Query C — valid alternative, not selected

Query C returned equivalent results and used the existing V015 index with a
backward index-only lookup. It consistently used more buffer hits than B in
the measured fixtures. Its execution time was higher at 68,940 and 316,080
rows, while at 1,780 rows it had the lower observed execution time. Its form
explicitly determines the latest `change_sequence` for each organization
before comparing it with the checkpoint.

**VALID ALTERNATIVE — NOT SELECTED**

### Note — index-only scans and the visibility map

Observed plans included heap fetches. The fixture was analyzed but was not
prepared to represent the visibility-map state of a mature database.
Autovacuum or `VACUUM` can change heap fetches for index-only scans. Heap
fetches observed in EXP-0006 are therefore not a permanent logical property of
Query B or Query C and are not the basis for identifying Query B as the
candidate.

### Known Limitation — Discovery Fairness

`ORDER BY organization_id ASC LIMIT ?` is deterministic, but EXP-0006
empirically demonstrated starvation when the first organizations remained
pending and their checkpoints did not advance:

- this operation does not guarantee fairness between polling cycles;
- deterministic ordering is not fair scheduling;
- an organization outside the first batch may remain unselected while earlier
  organizations remain pending.

The experiment repeated the same limited poll without advancing checkpoints
and observed the same first batch every time.

This RFC does not resolve scheduling fairness. Lease, claim, worker ownership,
`SKIP LOCKED`, retry-after, failure counters, round-robin cursor, last-served
organization, and scheduler policy remain outside the current Slice A scope.
It does not select any of those mechanisms.

```text
Pending discovery identifies work.
Scheduling policy determines fair/progressive work allocation.
```

Discovery and scheduling do not necessarily belong to the same port.

## Experimental Validation Basis — EXP-0006 / Slice A

EXP-0006 remains pre-ADR evidence. It ran against real PostgreSQL through
Testcontainers without mocking persistence, adding a production migration, or
altering any P0.2 production file.

The experiment plan required validation of:

1. `changesSince` returns all bounded changes above `NONE` for an organization;
2. partial checkpoints return only later changes;
3. a checkpoint equal to the current maximum returns no changes;
4. an organization with changes and no checkpoint row appears as pending;
5. multiple organizations maintain independent checkpoint views;
6. two `ProjectionName` values consume the same journal at different speeds
   without interference;
7. `limit` produces deterministic, stable pagination without repetition or
   omission when the returned final sequence becomes the next checkpoint;
8. real concurrent writes through the existing P0.2
   `PostgresMarketplaceIndependentEconomicEvidenceRepository` can occur while
   the feed is read;
9. returned changes remain ordered by `change_sequence` within each
   organization under that concurrency;
10. the experiment does not claim cross-organization commit ordering;
11. two concurrent checkpoint writers using the same `expected` value yield
    exactly one `Advanced` and one `Stale`;
12. `next <= expected` returns `Regression` without changing durable state;
13. the first successful advance for a previously unseen
    organization/projection pair creates its checkpoint row safely;
14. repeated `changesSince` calls with the same checkpoint return the same
    result when no intervening writes occur;
15. `changesSince` never advances a checkpoint implicitly;
16. after explicit successful advancement, the next read reflects the new
    checkpoint;
17. `organizationsWithPendingChanges` is exercised with thousands of journal
    rows across multiple organizations;
18. the exact pending-discovery query is inspected using
    `EXPLAIN (ANALYZE, BUFFERS)` against real PostgreSQL;
19. the captured plan records the actual node types, indexes used, actual rows,
    rows removed by filters, loops, buffer hits/reads, planning time, and
    execution time;
20. the experiment reports the plan as observed evidence and does not convert
    an expected index strategy into a fact before execution.

The experimental `projection_checkpoint` table, trigger, and candidate queries
exist only inside the experiment's Testcontainers setup. Results are recorded
under `docs/evidence/EXP-0006-durable-evidence-incremental-change-feed.md` and
`docs/evidence/EXP-0006-pending-discovery-follow-up.md`.

## Experimental Decision Matrix

| Classification | Capability or finding | Evidence state |
|---|---|---|
| VALIDATED experimentally | Change-feed semantics | Exclusive checkpoint reads, bounded deterministic pagination, repeated reads, and no implicit checkpoint advancement passed. |
| VALIDATED experimentally | Checkpoint semantics | Missing, stale, regression, explicit advancement, and durable reads passed. |
| VALIDATED experimentally | CAS concurrency | First-row and existing-row concurrent writers each produced exactly one `Advanced` and one `Stale`. |
| VALIDATED experimentally | Per-organization ordering | Increasing `change_sequence` ordering remained stable in the tested concurrent scenario. |
| VALIDATED experimentally | Independent projections | Two projection names progressed independently. |
| VALIDATED experimentally | Real P0.2 composition | Concurrent writes used the existing P0.2 repository. |
| VALIDATED experimentally | Query B semantic correctness | Query B matched the exact ordered results from A and C across all tested checkpoint states and limits. |
| VALIDATED experimentally | Query B measured scaling behavior | Across 1,780, 68,940, and 316,080 journal rows, Query B used the existing V015 index and its measured resource use followed organization count more closely than journal history growth. |
| REJECTED experimentally | Full-journal `GROUP BY`/`MAX` as production pending-discovery candidate | Observed work and buffer usage tracked journal-volume growth across the tested fixtures. |
| KNOWN LIMITATION | Starvation under fixed organization ordering with bounded limit | The same first batch was returned while earlier organizations remained pending and checkpoints did not advance. |
| UNRESOLVED / future design | Scheduling fairness | No fairness mechanism has been selected. |
| UNRESOLVED / future design | Ownership and leases | Lease, claim, worker ownership, retry, and failure handling remain outside Slice A. |
| UNRESOLVED / future design | Materialization/checkpoint atomicity | Deferred to Slice B. |
| UNRESOLVED / future design | Exact timeline and Slice B | Exact historical reconstruction and materialization contract remain unresolved. |
| UNRESOLVED / future design | First production consumer | No production consumer has been selected. |

Experimental evidence is now sufficient for architectural review of Slice A.
The next gate is human review of RFC-0007 v0.3 before creation of an ADR. No
row in this matrix authorizes an ADR, SPEC, TASK, migration, or production
implementation automatically.

## Preserved Architectural Boundaries

Slice A remains prohibited from:

- widening `MarketplaceIndependentEconomicEvidenceRepository`;
- placing checkpoints or `change_sequence` in the P0.2 economic port;
- altering V015;
- using the outbox as an internal projection cursor;
- creating a materialized Sales Intelligence projection;
- creating API, UI, marketplace-provider, or ERP-provider behavior.

## Relationship to P0.3 Slice B

Slice B is responsible for separately proposing and validating a
read-optimized Sales Intelligence materialization on top of the accepted
invalidation feed, if Slice A is eventually accepted.

Slice B must address:

- materialized list and detail shape;
- rebuild-from-zero semantics;
- exact evidence timeline requirements;
- whether intermediate aggregate versions must be reconstructable;
- staleness and projection-lag exposure;
- first consumer identity;
- atomicity or idempotency between materialization and checkpoint advancement.

Slice A does not resolve those questions.

## Open Design Questions Carried Forward

- Who consumes the feed first: an internal validation query, a test harness, a
  service, or Slice B followed by a future API?
- Must Slice B persist a materialized read model immediately, or can its first
  milestone remain an internal demonstrable consumer?
- Does Slice B require exact per-version reconstruction or only idempotent
  convergence on the current aggregate state?
- Should checkpoint state later include projection lag, replay position,
  failure, or lease metadata?
- What measured pending-discovery cost is acceptable before a different
  discovery structure becomes necessary?
- What scheduling policy can provide fair or progressive allocation without
  merging discovery and worker ownership prematurely?
- Should fairness, ownership, and leasing remain in a separate scheduling
  boundary rather than in `MarketplaceEconomicEvidenceChangeFeed`?
