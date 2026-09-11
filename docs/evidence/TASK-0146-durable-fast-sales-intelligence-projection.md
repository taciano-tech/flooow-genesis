# TASK-0146: Durable/Fast Sales Intelligence Projection â€” Slice B

Status: Complete - merged by PR #164

Date: 2026-09-06

## Authority

This implementation task is governed by:

- ADR-0041, fast operational-intelligence read-path boundary;
- ADR-0043, durable independent marketplace economic evidence boundary;
- ADR-0046, durable marketplace economic evidence incremental change feed;
- ADR-0047, Durable/Fast Sales Intelligence Projection Boundary, Accepted;
- ADR-0048, canonical Economic Truth Assembly semantics, Accepted;
- ADR-0049, durable OrderOccurrence persistence boundary, Accepted;
- SPEC-0045, durable incremental change feed Slice A, Accepted;
- SPEC-0046, Durable/Fast Sales Intelligence Projection Slice B, Accepted;
- SPEC-0047, canonical Economic Truth Assembly, Accepted;
- SPEC-0048, durable OrderOccurrence persistence, Accepted;
- EXP-0007, projection atomicity evidence;
- TASK-0147, canonical Economic Truth Assembly implementation, merged;
- TASK-0148, durable OrderOccurrence persistence implementation, merged by PR #162.

SPEC-0046 is normative for this implementation slice.

This TASK resumes the previously paused Sales Intelligence implementation only
because the dependency gates that caused the pause are now closed.

## Repository checkpoint

At authorization:

- canonical `main` is `8e8f4948ec4286ab6423ebd7563cc79abe126cfd`;
- PR #162 is merged;
- TASK-0148 is complete;
- V017 is merged and durable OrderOccurrence survives restart;
- canonical Economic Truth Assembly is implemented;
- `MarketplaceEconomicTruthAssembler` is the only order-assembly authority;
- `MarketplaceEconomicTruthCalculator` remains the only economic-calculation authority;
- SPEC-0046 is Accepted;
- TASK-0146 may now resume;
- V018 is the next migration available for this slice.

## Objective

Implement the smallest durable, organization-scoped Sales Intelligence projection
required by SPEC-0046 so current canonical marketplace economic truth can be:

```text
independent durable evidence
  -> incremental change feed
  -> current evidence refetch
  -> MarketplaceEconomicTruthAssembler
      -> NotReady
           -> durable unresolved Sales Intelligence state
      -> Ready(MarketplaceOrder)
           -> MarketplaceEconomicTruthCalculator
           -> durable calculated Sales Intelligence state
  -> fast local list/detail reads
```

The projection is disposable derivative state.

It must never become an authority for evidence, corrections, source provenance,
economic history, occurrence semantics, coverage semantics, or economic
calculation.

## Exact authorized implementation paths

TASK-0146 authorizes exactly these eight paths:

1. CREATE

   `applications/marketplace-operations/src/main/kotlin/io/flooow/marketplace/operations/economics/sales/MarketplaceSalesIntelligenceProjection.kt`

2. CREATE

   `applications/marketplace-operations/src/main/kotlin/io/flooow/marketplace/operations/economics/sales/MarketplaceSalesIntelligenceProjectionProcessor.kt`

3. CREATE

   `applications/marketplace-operations/src/test/kotlin/io/flooow/marketplace/operations/economics/sales/MarketplaceSalesIntelligenceProjectionProcessorTest.kt`

4. CREATE

   `applications/marketplace-operations-persistence-postgres/src/main/resources/db/migration/V018__create_marketplace_sales_intelligence_projection.sql`

5. CREATE

   `applications/marketplace-operations-persistence-postgres/src/main/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceSalesIntelligenceProjection.kt`

6. CREATE

   `applications/marketplace-operations-persistence-postgres/src/test/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceSalesIntelligenceProjectionTest.kt`

7. MODIFY only for implementation evidence

   `docs/evidence/TASK-0146-durable-fast-sales-intelligence-projection.md`

8. APPEND exactly one TASK-0146 implementation entry

   `docs/journal/MGI-EXECUTIVE-JOURNAL.md`

No ninth path is authorized.

If implementation requires a Gradle change, a second migration, a new module, a
provider path, another test file, an API file, or any other ninth path,
implementation must stop and return to governance.

## Projection identity

The projection consumer name is fixed:

```text
sales-intelligence
```

Checkpoint identity remains owned by the existing Slice A change feed:

```text
organization_id + projection_name
```

Projection subject identity is exactly:

```text
organization_id + marketplace_order_id
```

Database uniqueness must enforce one current projection row for one
organization-scoped marketplace order.

Cross-organization read, suppression, update, pagination, or checkpoint behavior
is forbidden.

## Canonical projection model

The projection contract must expose a bounded current-state model containing:

```text
organizationId
marketplaceOrderId
sourceEvidenceVersion
state
lastAppliedChangeSequence
projectedAt
```

`state` is a closed canonical derivative representation of exactly one of:

```text
Unresolved(
    assemblyPolicyVersion,
    reasons
)

Calculated(
    assemblyPolicyVersion,
    calculationPolicyVersion,
    calculationResult
)
```

The implementation may use Kotlin sealed types/data classes to represent this
closed model.

### Unresolved authority

`Unresolved` is produced only from:

```text
MarketplaceEconomicTruthAssemblyResult.NotReady
```

It must preserve only:

- the accepted assembly policy version;
- the accepted bounded NotReady reason set.

It must not:

- invoke `MarketplaceEconomicTruthCalculator`;
- manufacture a `MarketplaceOrder`;
- keep prior Ready/calculated economic values current;
- infer missing occurrence time;
- infer completeness;
- infer zero;
- inspect active facts to reproduce assembler semantics.

### Calculated authority

`Calculated` is produced only from:

```text
MarketplaceEconomicTruthAssemblyResult.Ready
  -> MarketplaceEconomicTruthCalculator.calculate
```

It must preserve the calculator's existing closed result without redefining its
economic meaning.

The projection must not invent new:

- revenue semantics;
- fee semantics;
- cost semantics;
- tax semantics;
- margin semantics;
- confidence semantics;
- allocation semantics;
- completeness semantics;
- profitability semantics.

No binary floating-point representation may be introduced for canonical money.

## Durable persistence port

`MarketplaceSalesIntelligenceProjection.kt` must own the smallest separate
application persistence contract required by SPEC-0046.

It must remain separate from:

- `MarketplaceIndependentEconomicEvidenceRepository`;
- `MarketplaceEconomicEvidenceChangeFeed`.

It must provide exactly the application capabilities needed for:

```text
currentBySubject
materializeIfNewer
listByOrganization
detailByOrganizationAndSubject
```

No checkpoint mutation belongs to this port.

No provider operation belongs to this port.

No scheduler, lease, worker ownership, retry queue, fairness, circuit breaker,
or connector concurrency operation belongs to this port.

## Closed persistence result

The application-facing write result must distinguish:

```text
Applied
NoOpAlreadyCurrent
IntegrityFailure
```

Names may vary only if semantics remain exactly closed and equivalent.

It must not expose:

- SQL;
- SQLSTATE;
- table names;
- constraint names;
- raw row counts;
- database values;
- organization IDs in diagnostics;
- subject IDs in diagnostics;
- monetary values in diagnostics.

Public diagnostic rendering must remain sanitized/fail-closed.

## Monotonic durable write

For one organization/subject:

```text
incoming change_sequence > durable last_applied_change_sequence
```

is the only condition that permits a material state mutation.

If no row exists, the incoming write may create one.

For:

```text
incoming == durable
```

the write is a deterministic no-op.

For:

```text
incoming < durable
```

the write is a deterministic stale no-op.

Correctness must be enforced by the final PostgreSQL write, not by an earlier
read/precheck.

The expected database behavior is an atomic guarded upsert equivalent to:

```sql
INSERT ...
ON CONFLICT (organization_id, marketplace_order_id)
DO UPDATE ...
WHERE EXCLUDED.last_applied_change_sequence
    > marketplace_sales_intelligence_projection.last_applied_change_sequence;
```

An optional prior sequence precheck may reduce canonical refetch work, but it
must never be correctness authority.

## V018 migration

Create exactly:

`V018__create_marketplace_sales_intelligence_projection.sql`

V018 must be additive.

It must not modify V001..V017.

It must create exactly the smallest durable projection structure required by
this task.

The table must provide:

- `organization_id uuid NOT NULL`;
- `marketplace_order_id uuid NOT NULL`;
- `source_evidence_version bigint NOT NULL`;
- closed projection-state discriminator;
- durable payload columns sufficient to reconstruct the closed application
  projection state without semantic inference;
- `last_applied_change_sequence bigint NOT NULL`;
- `projected_at timestamptz(6) NOT NULL`;
- primary/unique key enforcing `(organization_id, marketplace_order_id)`;
- a smallest organization-scoped keyset-pagination index selected together with
  the list ordering used by the implementation;
- database constraints for bounded/closed discriminator values and structurally
  valid state shape.

The projection table is derivative mutable current state. It is not an
append-only evidence ledger.

No trigger may make it a second evidence journal.

No provider payload is persisted.

No checkpoint is duplicated inside the projection table.

## List/detail read contract

### Detail

Detail lookup is exactly scoped by:

```text
organization_id + marketplace_order_id
```

It returns zero or one row.

The PostgreSQL plan must use a bounded indexed point lookup.

### List

List is scoped to one organization only.

The implementation must choose one stable deterministic keyset ordering and
freeze it in code/tests together with the supporting index.

The simplest acceptable production ordering for this slice is:

```text
projected_at DESC, marketplace_order_id DESC
```

with an organization-scoped index equivalent to:

```text
(organization_id, projected_at DESC, marketplace_order_id DESC)
```

Pagination must be keyset/cursor-based or an equivalent bounded indexed
continuation.

Unbounded OFFSET scanning is forbidden.

The implementation must define and validate a finite maximum page size before
persistence access.

No filters, full-text search, faceting, arbitrary sort, aggregation, export, or
cross-organization list behavior are authorized.

## Processor

`MarketplaceSalesIntelligenceProjectionProcessor` coordinates the existing
accepted ports for exactly one organization and one bounded batch.

The flow is:

```text
currentCheckpoint(organization, "sales-intelligence")
-> changesSince(organization, checkpoint, limit)
-> process each change in ascending change_sequence
     -> optional projection sequence precheck
     -> refetch current committed evidence when needed
     -> MarketplaceEconomicTruthAssembler.assemble
     -> NotReady:
          materialize unresolved current state
     -> Ready:
          MarketplaceEconomicTruthCalculator.calculate
          materialize calculated current state
-> only after complete batch durability:
     advanceCheckpoint(expected, destination)
```

The processor must not:

- write canonical evidence;
- interpret active facts directly;
- recreate assembler semantics;
- recreate calculator semantics;
- advance the checkpoint before projection durability;
- require a shared JDBC transaction between projection and checkpoint;
- add scheduler behavior;
- add provider behavior.

## Current-state invalidation

A change-feed record is an invalidation/cursor event, not an economic snapshot.

For each materialization that is not already current, the processor must refetch
the current committed evidence.

Therefore:

```text
sourceEvidenceVersion
```

must identify the current committed evidence version actually materialized.

```text
lastAppliedChangeSequence
```

must identify the change-feed position that authorized that write.

Those values are intentionally not required to represent the same historical
instant.

## Batch acknowledgement

For the smallest implementation, a returned batch is acknowledged only after
the entire returned batch is durably handled.

Partial-prefix checkpoint advancement is not required.

The destination passed to checkpoint CAS is exactly the final change sequence
of the successfully handled batch.

If any required materialization fails:

- checkpoint must not advance for that batch;
- no later change in that batch may be acknowledged as complete by the
  checkpoint.

## Crash/replay

The accepted at-least-once failure window is:

```text
projection COMMIT
-> crash/failure
-> checkpoint not advanced
-> same change replayed
-> monotonic write becomes no-op
-> checkpoint advancement retried
```

This behavior must require no manual repair in the accepted test scenarios.

The invariant is:

```text
checkpoint never claims progress beyond projection work already durable
```

## Concurrency

Two workers may process the same organization or same subject concurrently.

For two changes:

```text
a < b
```

every tested interleaving must converge to:

```text
lastAppliedChangeSequence = b
```

The older writer must never overwrite the newer projection.

Concurrent duplicate processing of the same sequence may produce at most one
material state transition.

Checkpoint concurrency remains solely under SPEC-0045 CAS semantics.

TASK-0146 must not add another checkpoint or worker-lock authority.

## Rebuild

The implementation must remain rebuildable from canonical evidence plus the
existing change feed.

Tests must demonstrate deterministic behavior from checkpoint NONE with:

- empty projection;
- partially populated projection;
- already-current rows;
- rows ahead of replayed invalidations;
- sequence gaps;
- repeated subject changes.

No destructive clear is required or authorized.

No shadow-table/cutover/versioned-projection design is authorized in this slice.

## Performance gate

Acceptance requires real PostgreSQL/Testcontainers evidence using the final V018
schema and representative data volume.

The test must exercise at minimum:

```text
indexed detail lookup
first list page
middle keyset page
last/reduced page
duplicate replay write
fresh projection write
```

The evidence must record or assert enough structure to prove:

- the final detail path is indexed;
- the final list path is bounded and index-compatible;
- no unbounded OFFSET path is used;
- no full-table scan is required for the accepted detail path;
- organization isolation is maintained.

This gate is characterization of the chosen production shape, not a speculative
partitioning exercise.

Range partitioning is explicitly not authorized by TASK-0146.

Any partitioning decision requires later measured evidence from real scale.

## Required tests

The application test file must cover:

- empty batch;
- deterministic ascending processing;
- NotReady materialization without calculator invocation;
- Ready -> calculator -> Calculated materialization;
- newer NotReady replacing prior calculated current state;
- no stale value retention;
- already-current precheck/no-op behavior;
- projection failure blocks checkpoint advancement;
- checkpoint failure after projection durability replays safely;
- repeated same change;
- current evidence newer than invalidation evidence version;
- organization isolation at processor boundary;
- batch destination exactly final returned sequence.

The PostgreSQL/Testcontainers test file must cover:

- V001..V018 migration success;
- schema constraints;
- projection round trip;
- unresolved round trip;
- calculated round trip;
- exact `timestamptz(6)` metadata precision;
- organization-scoped uniqueness;
- guarded insert/update;
- equal sequence no-op;
- stale sequence no-op;
- concurrent old/new writers converge to newest sequence;
- duplicate concurrent write produces at most one state mutation;
- transactional rollback on malformed/failing write;
- fail-closed malformed durable row;
- detail isolation;
- bounded keyset pagination;
- stable deterministic ordering;
- representative-volume query-plan/performance characterization;
- restart read equivalence;
- replay behavior through the real Slice A feed/checkpoint adapter where needed
  to prove the persistence boundary integrates without a second checkpoint.

## Verification commands

At minimum:

```text
:applications:marketplace-operations:compileKotlin
:applications:marketplace-operations:compileTestKotlin
:applications:marketplace-operations:test

:applications:marketplace-operations-persistence-postgres:compileKotlin
:applications:marketplace-operations-persistence-postgres:compileTestKotlin
:applications:marketplace-operations-persistence-postgres:test
```

The final implementation PR must also pass repository CI.

## Exact scope checks

Before commit and before merge, mechanically prove:

```text
exactly eight changed paths
V001..V017 unchanged
no provider path changed
no connector-runtime path changed
no API/UI path changed
no research harness changed
no TASK-0147 semantic file changed
no TASK-0148 semantic/persistence file changed
```

`git diff --check` must be clean.

## Implementation evidence - 2026-09-06

TASK-0146 production implementation was verified locally against the accepted
SPEC-0046 boundary.

Implemented:

- dedicated organization-scoped Sales Intelligence projection port;
- canonical Unresolved and Calculated current-state materialization;
- processor ordering through change feed -> current evidence refetch -> assembler
  -> calculator only for Ready -> monotonic projection write -> checkpoint CAS;
- additive V018 PostgreSQL projection schema;
- guarded monotonic upsert using `last_applied_change_sequence`;
- bounded organization-scoped detail and keyset list reads;
- deterministic replay after projection commit/checkpoint failure;
- fail-closed malformed projection reads;
- representative-volume PostgreSQL index-plan characterization.

Local verification completed successfully:

- `:applications:marketplace-operations:compileKotlin`;
- `:applications:marketplace-operations:compileTestKotlin`;
- `:applications:marketplace-operations:test`;
- `:applications:marketplace-operations-persistence-postgres:compileKotlin`;
- `:applications:marketplace-operations-persistence-postgres:compileTestKotlin`;
- `:applications:marketplace-operations-persistence-postgres:test`;
- `git diff --check`;
- exactly eight authorized changed paths;
- V001..V017 unchanged;
- no provider, connector-runtime, API/UI, research, TASK-0147 semantic, or
  TASK-0148 semantic/persistence path changed.

The completion gate remains open until the implementation PR is CI-green,
review-clean, and merged.
## Explicitly out of scope

TASK-0146 does not authorize:

- Mercado Livre ingestion;
- Omie ingestion;
- provider adapters;
- connector execution coordination;
- connector retry policy;
- connector backpressure;
- circuit breakers;
- scheduler/fairness/leases;
- API/UI;
- external payload contracts;
- Ads allocation;
- MGI decision intelligence;
- VOI;
- Bayesian belief;
- Opportunity Intelligence;
- Hypothesis Ledger;
- partitioning;
- new evidence families;
- new economic component types;
- economic semantic changes;
- assembler changes;
- calculator changes;
- OrderOccurrence changes;
- backfill;
- new change-feed semantics;
- a second checkpoint;
- outbox;
- new Gradle module;
- arbitrary filtering/sorting/search;
- analytics aggregation.

## Stop conditions

Stop implementation and return to governance if any of the following becomes
necessary:

1. a ninth changed path;
2. a second migration;
3. any modification to V001..V017;
4. a Gradle/module dependency change;
5. provider-specific code;
6. API/UI code;
7. connector-runtime code;
8. a new checkpoint store/mechanism;
9. scheduler, lease, retry queue, or worker ownership;
10. direct reconstruction of MarketplaceOrder from active facts;
11. bypass of MarketplaceEconomicTruthAssembler;
12. bypass or semantic duplication of MarketplaceEconomicTruthCalculator;
13. new economic semantics;
14. provider payload persistence;
15. destructive projection rebuild;
16. unbounded OFFSET pagination;
17. unbounded organization scan;
18. speculative partitioning;
19. weakening organization isolation;
20. exposing SQL/database detail through public failures.

## Completion gate

TASK-0146 is complete only when:

- V018 is implemented;
- projection persistence is organization-isolated;
- monotonic write semantics are proven;
- Ready/NotReady materialization semantics are proven;
- no stale Ready values survive a newer NotReady state;
- processor checkpoint ordering is proven;
- crash/replay is proven;
- concurrency convergence is proven;
- rebuild semantics are proven;
- list/detail paths are bounded and indexed;
- representative-volume PostgreSQL evidence passes;
- all authorized application and persistence tests pass;
- repository CI is green;
- exactly eight authorized paths changed;
- PR review is clean;
- implementation PR is merged.

Only after this gate is closed may the roadmap move from durable Sales
Intelligence projection to provider activation and the separate Connector
Execution Coordination contract.
## Completion evidence - 2026-09-06

TASK-0146 is complete.

Final repository evidence:

- implementation PR #164 merged into `main`;
- merge commit `2917ec20a4e4d47baf6ea64b3f642748ef9057fd`;
- final implementation head `f5d41096a94e33af0a48e5eb24c69a2aeebc9b52`;
- repository CI run #345 completed successfully;
- final PR changed exactly the eight authorized implementation paths;
- V018 was the sole migration introduced by this task;
- review threads were empty at merge;
- local and repository application/PostgreSQL acceptance gates passed.

This closes the durable Sales Intelligence projection dependency.

The roadmap may now move to provider activation. Connector Execution Coordination,
including retry scheduling, fairness, concurrency permits, backpressure, and
circuit behavior, remains a separate downstream contract and is not implicitly
authorized by this completion.
