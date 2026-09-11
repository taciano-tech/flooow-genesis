# ADR-0047: Durable/Fast Sales Intelligence Projection Boundary

Status: Accepted

Date: 2026-09-04

## Context

ADR-0041 established the MGI operational-intelligence convergence boundary and
requires the Genesis path to terminate in a fast operational read projection
consumed by Sales Intelligence API/UI. Heavy provider work must remain outside
the synchronous read path.

ADR-0042 and ADR-0043 established independent marketplace economic evidence
and its durable PostgreSQL persistence. ADR-0046 then established a separate
incremental change-feed boundary for consuming committed evidence changes
without widening the economic repository.

P0.3 Slice A deliberately stopped before materialization. It defined:

```text
durable evidence
  -> incremental change feed
  -> durable per-projection checkpoint
```

but did not decide how a Sales Intelligence projection must persist state,
recover from crashes, tolerate replay, rebuild, or relate projection
durability to checkpoint advancement.

EXP-0007 exercised three candidate strategies against PostgreSQL 18.4 through
Testcontainers, with production Flyway migrations V001-V016 and the real P0.2
writer/change-feed/V016 checkpoint boundary where required:

```text
A. projection + checkpoint in one transaction

B. idempotent monotonic projection commit
   -> separate checkpoint advancement

C. processed-change receipt + projection
   -> separate checkpoint advancement
```

The experiment tested crash windows, replay, stale/out-of-order changes,
concurrent writers, partial batches, real V016 compare-and-set checkpoint
advancement, current-state refetch, rebuild from NONE, write amplification,
duplicate replay cost, and projection-sequence prechecks.

The architectural decision must preserve the existing responsibilities:

- durable evidence remains canonical economic truth;
- the economic repository remains independent of projection mechanics;
- the change feed remains an invalidation/cursor boundary;
- checkpoint state remains progress metadata, not economic truth;
- the Sales Intelligence projection remains disposable derivative state;
- synchronous read paths remain local, bounded, and provider-independent.

## Decision

### Independent Slice B projection boundary

P0.3 Slice B introduces a durable Sales Intelligence projection as a separate
derivative boundary downstream of the durable evidence change feed.

Conceptually:

```text
Durable Economic Evidence
  -> MarketplaceEconomicEvidenceChangeFeed
  -> Sales Intelligence projection processor
  -> durable Sales Intelligence projection
  -> future API/UI
```

The projection is not part of
`MarketplaceIndependentEconomicEvidenceRepository`.

The economic repository remains responsible for current committed economic
evidence. The change feed remains responsible for incremental discovery and
checkpoint semantics. The projection owns materialized read-side state.

### Projection authority and derivation

The Sales Intelligence projection is derivative state.

It must never become the canonical authority for economic facts, corrections,
cost components, or source evidence.

Canonical authority remains upstream:

```text
durable independent economic evidence
  -> current committed aggregate/economic result
  -> projection
```

The projection may be destroyed and rebuilt from durable upstream evidence and
the incremental change feed.

A projection inconsistency must be repairable by deterministic rebuild/replay;
it must not require editing canonical evidence to match the projection.

### Current-state invalidation semantics

Slice B consumes ADR-0046 invalidations.

A change identifies a subject and physical `change_sequence`; it does not
carry a historical snapshot of the economic aggregate.

The projection processor resolves current committed state through the
economic repository or the relevant canonical economic read boundary:

```text
change
  -> subject
  -> current committed state
  -> materialize projection
```

The resolved state may be newer than the evidence version that originally
caused the consumed invalidation.

Therefore Slice B materializes current operational intelligence. It does not
promise exact historical aggregate reconstruction at each historical
invalidation sequence.

### Monotonic projection state

Every independently materialized projection record driven by the incremental
change feed must have durable change-position metadata sufficient to determine
the newest change that materially applied to that record.

Conceptually:

```text
organization_id
subject identity
materialized Sales Intelligence fields
source/current evidence version where relevant
last_applied_change_sequence
projected_at
```

The final physical schema is not decided by this ADR.

For the same organization/subject, an incoming change may modify durable
projection state only when:

```text
incoming change_sequence
  >
durable last_applied_change_sequence
```

An equal or lower sequence is a deterministic no-op.

This monotonic guard is the final concurrency and replay correctness
authority.

### Selected materialization/checkpoint strategy

P0.3 Slice B adopts Strategy B from EXP-0007:

```text
read change
  -> optionally precheck durable projection sequence
  -> resolve current committed state when needed
  -> monotonic projection write
  -> COMMIT projection
  -> advance durable checkpoint separately
```

Projection durability occurs before checkpoint advancement.

The checkpoint is advanced using the existing ADR-0046/V016 compare-and-set
semantics. Slice B does not require a shared database transaction between the
projection and checkpoint stores.

### Crash recovery and deterministic replay

The accepted failure window is:

```text
projection COMMIT succeeds
  -> worker crashes or checkpoint advancement fails
  -> durable checkpoint remains behind
```

Recovery is:

```text
same change is observed again
  -> projection monotonic write becomes no-op
  -> checkpoint advancement is retried
```

This is intentional at-least-once processing.

A durable checkpoint must never advance beyond projection work that has
already committed.

If projection materialization fails or rolls back, checkpoint advancement for
that work must not occur.

### Projection-sequence precheck

A processor may read the durable projection's
`last_applied_change_sequence` before resolving current evidence.

If:

```text
durable sequence >= incoming sequence
```

the processor may skip:

- current-state refetch;
- projection write.

This precheck is an optimization only.

It must not be used as a lock, lease, ownership claim, compare-and-set
substitute, or concurrency authority.

Two workers may both pass the precheck concurrently. Correctness still depends
on the final monotonic projection write.

### Rebuild semantics

A projection rebuild begins from checkpoint `NONE` for the rebuilding
projection identity or equivalent isolated rebuild state.

Rebuild must tolerate:

- an empty projection;
- a partially materialized projection;
- rows already ahead of replayed invalidations;
- physical `change_sequence` gaps.

Already-current rows become deterministic no-ops. Missing/outdated rows are
materialized.

A rebuild must not require destructive cleanup of otherwise-valid projection
rows merely to make replay correct.

This ADR does not decide whether a future large-scale rebuild uses in-place
replay, a versioned projection, shadow tables, or cutover. That remains a
separate operational design decision if required by measured scale.

### Organization isolation

All projection persistence and access paths must preserve organization
identity.

A subject from one organization must never read, update, suppress, or advance
projection state for another organization.

Organization identity must participate in the durable projection key or in an
equivalent database-enforced isolation boundary.

### Fast read-path requirement

The durable Sales Intelligence projection exists to support fast local reads.

Future list/detail API and UI reads must:

- read materialized projection state;
- use indexed and bounded access paths;
- avoid provider calls;
- avoid durable-evidence journal scans;
- avoid reconstructing complete evidence history synchronously;
- avoid assembling Sales Intelligence by calling external marketplaces or ERP
  systems during page load.

Provider enrichment, reconciliation, and heavy evidence processing remain
outside the synchronous read path.

This ADR establishes that architectural requirement but does not establish a
hard latency SLA.

EXP-0007 did not benchmark a final production Sales Intelligence read model at
large projection volumes because the final row shape, payload, indexes, query
contracts, and pagination model have not yet been selected. Benchmarking the
minimal experimental projection as if it were the production read model would
create misleading performance evidence.

The bounded SPEC must define the candidate read-model shape and the subsequent
implementation gate must measure its indexed list/detail paths at representative
volume before any hard latency or throughput acceptance.

### Processed-change receipts are not required

Slice B does not require a durable processed-change receipt/deduplication
table.

EXP-0007 demonstrated that Strategy C can be made correct only when receipt
and projection writes are atomic with each other, while adding:

- an additional durable structure;
- an additional primary key/index;
- one receipt mutation per newly processed change;
- storage growth proportional to processed history;
- retention/cleanup/rebuild lifecycle concerns.

EXP-0007 also demonstrated that the projection's own
`last_applied_change_sequence` can identify duplicate/stale work without a
second historical deduplication structure.

No observed correctness property justifies permanent processed-change receipt
state for this projection.

### Shared projection/checkpoint transaction is not required

Slice B does not require projection and checkpoint writes to share one JDBC
transaction.

The real `PostgresMarketplaceEconomicEvidenceChangeFeed` owns checkpoint
transaction/connection handling internally. Strategy A would require changing
that accepted Slice A boundary or introducing a shared transaction
coordinator.

EXP-0007 demonstrated deterministic recovery without this coupling.

Therefore Slice B preserves separate projection and checkpoint durability
boundaries.

## Out of scope

This decision does not define or authorize:

- the final Sales Intelligence projection table/schema;
- a migration number;
- final persistence port/interface names;
- API endpoints;
- UI contracts or page composition;
- scheduler or cross-organization fairness policy;
- leases, claims, worker ownership, or `SKIP LOCKED`;
- retry-after policies or failure counters;
- provider adapters;
- Mercado Livre or Omie integration changes;
- Financial Ledger or Reconciliation changes;
- exact historical aggregate reconstruction at each change sequence;
- evidence timeline UX;
- final list/detail payload shape;
- pagination contract;
- connection-pool sizing;
- final batch size;
- large-scale rebuild cutover mechanics;
- a hard latency or throughput SLA;
- a SPEC, TASK, migration, or production implementation.

Those require separate bounded decisions and authorization.

## Alternatives considered

### Strategy A â€” projection and checkpoint in one transaction â€” rejected

Strategy A provides strong all-or-nothing projection/checkpoint durability.

EXP-0007 proved that rollback behaves correctly when both writes share one
experimental transaction.

However, the production change-feed adapter owns its checkpoint transaction
and JDBC connection internally. Adopting Strategy A would require widening or
restructuring the accepted Slice A persistence boundary.

Deterministic replay under Strategy B provides the required crash safety
without introducing that coupling.

### Strategy C â€” processed-change receipt â€” rejected

Strategy C can provide explicit deduplication, but receipt and projection must
be atomic with each other. Committing the receipt first would permit permanent
loss after a crash.

The strategy also adds one durable receipt row per processed change plus its
indexing and lifecycle.

EXP-0007 observed materially higher fresh-path SQL work for Strategy C at
larger tested batches, while duplicate-replay measurements did not establish a
stable advantage that justified the permanent state cost.

The projection sequence itself is sufficient for stale/duplicate detection in
the selected current-state projection model.

### Use checkpoint as projection truth â€” rejected

A checkpoint indicates consumer progress only.

It does not prove the content, completeness, or correctness of individual
projection rows and must not replace materialized row-level sequence guards.

Checkpoint state therefore remains progress metadata rather than projection
or economic truth.

### Reconstruct Sales Intelligence on synchronous reads â€” rejected

Building list/detail responses by scanning the durable evidence journal,
replaying full history, or calling providers during each read would violate
the convergence requirement for a fast operational read model.

Sales Intelligence must be materialized ahead of synchronous API/UI reads.

### Make projection-sequence precheck authoritative â€” rejected

A prior SELECT cannot safely replace the final monotonic write because
concurrent workers can observe the same earlier state.

The precheck may remove unnecessary work, but the guarded durable write
remains authoritative.

## Consequences

Positive consequences:

- durable economic evidence remains canonical and untouched;
- Slice A change-feed/checkpoint responsibilities remain intact;
- projection and checkpoint can fail independently without data loss;
- crash recovery is deterministic and requires no manual repair;
- duplicate and stale changes are replay-safe;
- concurrent writers converge through the monotonic write guard;
- projection rebuild can reuse the same durable evidence/change feed;
- no processed-change history table is required;
- no shared JDBC transaction boundary is introduced;
- future API/UI reads can be served from local materialized state.

Costs and limitations:

- processing is intentionally at-least-once;
- a crash after projection commit can cause replay before checkpoint catches
  up;
- every materialized row must retain sufficient change-position metadata;
- current-state invalidations do not reconstruct exact historical snapshots;
- scheduler/fairness remains outside Slice B;
- very large rebuild strategy remains unresolved;
- production batch size, pooling, and read latency still require measurement;
- final schema and read contract remain to be specified.

## Evidence and references

- ADR-0041, `MGI Operational Intelligence Convergence Boundary`;
- ADR-0042, `Independent Marketplace Economic Evidence Boundary`;
- ADR-0043, `Durable Independent Marketplace Economic Evidence Boundary`;
- ADR-0046, `Durable Marketplace Economic Evidence Incremental Change Feed`;
- RFC-0007, `Durable Evidence Incremental Change Feed (P0.3 Boundary)`;
- `docs/evidence/EXP-0007-sales-intelligence-projection-atomicity.md`;
- EXP-0007 experimental commit `e761520`.

## Authorization

If accepted after human review, this ADR authorizes drafting a separate,
bounded SPEC for P0.3 Slice B only.

It does not authorize a migration, TASK, scheduler/fairness mechanism, API/UI,
provider integration, or production implementation.
