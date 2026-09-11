# ADR-0046: Durable Marketplace Economic Evidence Incremental Change Feed

Status: Accepted

Date: 2026-09-02

## Context

P0.2/TASK-0144 established durable independent marketplace economic evidence
without widening the economic domain boundary. V015 owns an append-only,
organization-isolated evidence journal whose committed applied updates receive
a physical PostgreSQL `change_sequence`. The physical sequence is global, and
its values can be consumed in increasing order within one organization.

`MarketplaceIndependentEconomicEvidenceRepository` deliberately remains the
economic persistence port with exactly two operations: `find` and `apply`.
It reconstructs current economic evidence and applies domain updates. It does
not expose projection cursors, checkpoint state, journal discovery, or
incremental-consumption mechanics.

A future projection needs to discover and consume committed evidence changes
incrementally without:

- widening the economic repository;
- exposing persistence SQL to consumers;
- using the external-delivery outbox as an internal projection cursor;
- reopening V015;
- mixing discovery, scheduling, and materialization responsibilities.

RFC-0007 v0.3 proposed a separate Slice A boundary. EXP-0006 exercised that
proposal against PostgreSQL 18.4 through Testcontainers, with Flyway V001–V015,
the existing integrity constraints, and the real P0.2 writer. It validated the
change-feed and checkpoint semantics and compared three pending-discovery
query forms at 1,780, 68,940, and 316,080 journal rows.

## Decision

### Independent Slice A boundary

Introduce a boundary conceptually named
`MarketplaceEconomicEvidenceChangeFeed`, separate from
`MarketplaceIndependentEconomicEvidenceRepository`.

The economic repository remains limited to exactly:

```text
find
apply
```

The new Slice A boundary provides exactly four conceptual capabilities:

```text
changesSince(...)
organizationsWithPendingChanges(...)
currentCheckpoint(...)
advanceCheckpoint(...)
```

No fifth operation is part of this decision.

### Invalidation/change-feed semantics

Slice A is an **invalidation/change feed**. It is not a historical snapshot
feed, evidence timeline, or exact aggregate-at-version replay mechanism.

Each change identifies:

```text
subject: complete MarketplaceEconomicEvidenceSubject
evidenceVersion: MarketplaceEconomicEvidenceVersion
changeSequence: ChangeSequenceCheckpoint
changeKind: MarketplaceEconomicEvidenceChangeKind
```

The change does not carry the complete economic payload. A consumer can
resolve current evidence through:

```text
change
  -> subject
  -> MarketplaceIndependentEconomicEvidenceRepository.find(subject)
  -> current aggregate
```

`find(subject)` may return a version later than the `evidenceVersion` that
originated the consumed change. Slice A identifies committed invalidation; it
does not promise reconstruction of the aggregate exactly as it existed at
that change.

### Change-sequence semantics

`change_sequence` remains outside the P0.2 economic port. It belongs to the
durable persistence/feed boundary and is exposed through the change-feed
cursor type only where incremental consumption requires it.

The physical PostgreSQL sequence is global, but a checkpoint is interpreted
only together with its organization. Gaps are valid. The feed promises
increasing `change_sequence` ordering within one organization and makes no
global ordering promise between different organizations. V015 remains
unchanged.

### Durable checkpoint state

Slice A requires future durable checkpoint state keyed by:

```text
organization_id + projection_name
```

Its conceptual fields are:

```text
organization_id
projection_name
last_change_sequence
updated_at
```

The future persistence contract must enforce:

- a foreign key from `organization_id` to `integration_organization`;
- a primary key over organization and projection name;
- `last_change_sequence >= 0`;
- a database-authoritative `updated_at` timestamp.

No durable row means checkpoint `NONE`/`ZERO`. `ProjectionName` identifies an
independent consumer, so two projections can consume the same journal at
different rates without interfering. Reading never advances a checkpoint
implicitly.

This ADR decides the need and semantics of that state. It does not create or
number a migration.

### Compare-and-set checkpoint advancement

`advanceCheckpoint(expected, next)` uses a closed result family:

```text
Advanced
Stale
Regression
```

Its normative semantics are:

```text
next <= expected
  -> Regression
  -> no write

durable == expected
  -> advancement may occur
  -> Advanced

durable != expected
  -> Stale
  -> no write
```

A missing row is durable `NONE`:

```text
missing + expected NONE + next > NONE
  -> compare-and-set creation
  -> Advanced

missing + expected != NONE
  -> Stale
  -> no row created
```

EXP-0006 observed exactly one `Advanced` and one `Stale` for two concurrent
first writers. It also observed exactly one `Advanced` and one `Stale` for two
concurrent writers against an existing checkpoint.

Explicit advancement keeps reads compatible with at-least-once processing. A
consumer that fails before successful advancement can observe the same change
again and must tolerate replay.

### Canonical pending-discovery strategy

`organizationsWithPendingChanges` is a positive-limit, bounded discovery
operation. The canonical Slice A query strategy for eventual implementation
is Query B from EXP-0006:

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

Queries A, B, and C returned the same ordered organizations across the tested
checkpoint states and limits. B and C used the existing V015 index on
`(organization_id, change_sequence)`. B used fewer buffer hits than C in each
measured fixture. Its execution time was lower at 68,940 and 316,080 journal
rows; C had the lower execution time at 1,780 rows.

The full-journal `GROUP BY organization_id`/`MAX(change_sequence)` Query A is
rejected as the production pending-discovery candidate. Across the tested
fixtures its observed work and resource usage tracked journal-history growth.
This is measured evidence, not a formal Big-O proof.

Query C remains a semantically valid, index-using alternative but is not
selected. No V015 change is needed for Query B.

### Known limitation — discovery fairness

`organizationsWithPendingChanges` identifies work; it is not a scheduler.
EXP-0006 demonstrated starvation under:

```sql
ORDER BY organization_id ASC
LIMIT ?
```

when the earliest organizations remained pending and their checkpoints did
not advance. Therefore:

- deterministic ordering is not fair scheduling;
- Slice A does not guarantee fair progress between polling cycles;
- organizations outside the first batch can remain unselected while earlier
  organizations remain pending.

This ADR records starvation as a known limitation and does not select a
fairness mechanism.

```text
Pending discovery identifies work.
Scheduling policy determines fair/progressive work allocation.
```

Discovery and scheduling are separate responsibilities and need not belong to
the same port.

## Out of scope

This decision does not define or authorize:

- leases, claims, worker ownership, or `SKIP LOCKED`;
- retry-after, failure counters, round-robin, or last-served organization;
- a scheduler or scheduling-fairness policy;
- materialized Sales Intelligence;
- an exact evidence timeline or historical reconstruction by version;
- Slice B materialization/checkpoint atomicity;
- API or UI;
- Mercado Livre or Omie providers;
- Financial Ledger or Reconciliation changes;
- outbox changes;
- the first production consumer;
- a migration, SPEC, TASK, or production implementation.

P0.3 Slice B requires a separate architectural decision and authorization.

## Alternatives considered

### Widen the economic repository — rejected

Adding feed and checkpoint operations to
`MarketplaceIndependentEconomicEvidenceRepository` would mix economic-domain
persistence with projection cursor mechanics and violate the P0.2 boundary.

### Use the outbox — rejected

P0.2 deliberately adopted `change_sequence` in the durable evidence journal
as the resumable internal cursor. No demonstrated external-distribution need
justifies reopening the inventory-specific outbox or its delivery runtime.

### Full-journal `GROUP BY`/`MAX` discovery — rejected experimentally

EXP-0006 observed Query A aggregating the historical journal. Its measured
work and buffer usage followed historical-volume growth across the tested
fixtures, so it is not selected as the production discovery candidate.

### Query C latest-sequence lookup — valid alternative, not selected

Query C was semantically equivalent and used the existing V015 index. Query B
performed less observed buffer work in all measured fixtures and avoids
explicitly obtaining each organization's latest sequence before comparison.

### Merge discovery and scheduling — rejected for this slice

Starvation is established, but EXP-0006 did not evaluate leases, claims,
worker ownership, round-robin, or other fairness policies. Selecting one now
would expand Slice A beyond the available evidence.

## Consequences

Positive consequences:

- P0.2 and its two-operation economic repository remain intact;
- committed evidence can be consumed incrementally and resumably;
- named projections progress independently;
- explicit compare-and-set advancement supports controlled concurrency;
- persistence and cursor mechanics stay behind a dedicated boundary;
- a future rebuild or materialization can consume the same durable journal.

Costs and limitations:

- a future durable checkpoint table is required;
- at-least-once behavior requires idempotent or replay-tolerant consumers;
- an invalidation does not represent an exact historical snapshot;
- starvation and fair scheduling remain unresolved;
- Slice B must still define materialization, rebuild, lag, and checkpoint
  atomicity semantics.

## Evidence and references

- RFC-0007 v0.3, `Durable Evidence Incremental Change Feed (P0.3 Boundary)`;
- experimental authority commit `1898629`;
- `docs/evidence/EXP-0006-durable-evidence-incremental-change-feed.md`;
- `docs/evidence/EXP-0006-pending-discovery-follow-up.md`;
- ADR-0045, `Revert Outbox Generalization — Durable Change Sequence Instead`;
- SPEC-0044, `Durable Independent Marketplace Economic Evidence — Change
  Sequence Scope`.

## Authorization

If accepted after human review, this ADR authorizes drafting a separate,
bounded SPEC for P0.3 Slice A only. It does not authorize P0.3 Slice B, a
migration, a TASK, or production implementation.
