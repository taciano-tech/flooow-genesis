# EXP-0007 — Durable/Fast Sales Intelligence Projection Atomicity and Replay

Status: concluded
Decision: Strategy B — idempotent monotonic projection followed by separate checkpoint advancement
Scope: experimental only; no production implementation authorized by this document

## 1. Objective

Determine which projection materialization strategy preserves correctness under crash, replay, concurrency and rebuild while minimizing long-term coupling, operational state and write cost.

The experiment compares:

- Strategy A — projection and checkpoint in the same database transaction
- Strategy B — idempotent monotonic projection commit, then separate checkpoint advancement
- Strategy C — processed-change receipt plus projection, then separate checkpoint advancement

The experiment is constrained by the existing P0.2/P0.3 Slice A boundaries:

- durable economic evidence remains canonical authority
- the incremental change feed remains a separate concern
- checkpoint is not economic truth
- the Sales Intelligence projection must be disposable and rebuildable
- provider calls and broad history scans must remain outside the synchronous read path
- no production schema, port or adapter changes are authorized by EXP-0007

## 2. Core invariants

1. A committed economic change must never be lost.
2. Replay must never duplicate economic effect.
3. An older change must never overwrite newer materialized state.
4. A checkpoint must never claim progress beyond durable projection state.
5. The projection must be destroyable and rebuildable.
6. Durable evidence remains authoritative.
7. Change Feed and Projection responsibilities remain separated.
8. Read paths must not depend on provider calls or journal/full-history scans.
9. Organization isolation must remain absolute.
10. Worker failure must not require manual repair.

## 3. Experimental environment

- PostgreSQL 18.4 via Testcontainers
- Flyway production migrations V001 through V016
- Kotlin/JUnit harness isolated under:
  `research/experiments/exp-0007-harness`
- production persistence adapters reused where needed to bridge real P0.2/P0.3 behavior
- no production migrations or application code modified

The local timing results below are experimental evidence only. They are not production SLAs and must not be interpreted as production throughput guarantees.

## 4. Strategy definitions

### Strategy A — atomic same transaction

Concept:

    BEGIN
      projection write
      checkpoint write
    COMMIT

Property sought:

- all-or-nothing projection/checkpoint durability

Constraint discovered:

- the production `PostgresMarketplaceEconomicEvidenceChangeFeed.advanceCheckpoint(...)`
  owns its transaction and connection internally
- it does not accept an external `Connection` or transaction context
- using Strategy A in production would therefore require changing the existing Slice A boundary

### Strategy B — idempotent projection then checkpoint

Concept:

    change
      -> refetch current committed state
      -> monotonic projection upsert
      -> COMMIT projection
      -> advance checkpoint separately

Projection write is guarded by:

    incoming change_sequence
      >
    durable last_applied_change_sequence

Crash window:

    projection COMMIT
      -> crash
      -> checkpoint still behind
      -> replay same change
      -> monotonic projection no-op
      -> checkpoint advances

### Strategy C — receipt plus projection then checkpoint

Concept:

    change
      -> processed-change receipt
      -> projection write
      -> COMMIT
      -> checkpoint separately

Critical correctness rule:

- receipt and projection must be in the same transaction

Disallowed sequence:

    receipt COMMIT
      -> crash
      -> projection missing
      -> replay sees receipt
      -> skips projection
      -> permanent loss

Strategy C therefore adds durable state and write amplification but does not remove the need for an atomic local transaction between receipt and projection.

## 5. Gates

### Gate 0 — PostgreSQL/Flyway harness

PASS.

- PostgreSQL 18.4 started successfully
- all 16 production migrations V001–V016 applied
- schema reached V016
- strategies remained bounded to A/B/C

### Gate 1 — Strategy A rollback

PASS.

A projection write and experimental checkpoint write executed inside one transaction.

Injected crash before commit caused:

- projection absent
- checkpoint absent

Proves the all-or-nothing property of Strategy A in principle.

Does not prove Strategy A can be adopted without changing the production transaction boundary.

### Gate 2 — Strategy B crash after projection commit

PASS.

Sequence:

1. projection for change sequence 10 committed
2. crash injected after projection commit
3. checkpoint remained absent
4. same change replayed
5. monotonic projection returned no-op
6. original projection timestamp remained unchanged
7. checkpoint advanced after recovery

Result:

- no duplicate economic effect
- no loss
- deterministic recovery

### Gate 3 — stale/out-of-order protection

PASS.

Applied:

- newer change sequence 20
- then older change sequence 10

Older write returned no-op.

Final projection preserved:

- newer value
- newer sequence
- newer timestamp/state

Invariant proven:

- older invalidation cannot degrade newer materialized state

### Gate 4 — concurrent same-subject writers

PASS.

Two concurrent PostgreSQL writers processed older/newer changes for the same subject.

Final projection converged to the newest sequence/value.

No errors observed.

The monotonic upsert remains the final concurrency authority.

### Gate 5 — contention profile

PASS as diagnostic evidence.

Measured HOT and BROAD contention with 2, 4 and 8 workers.

No database errors were observed.

Observed timings vary significantly across runs and are not production performance evidence.

The experiment intentionally does not convert these values into SLA claims.

### Gate 6 — real P0.2 writer/feed/V016 checkpoint bridge

PASS.

Used:

- `PostgresMarketplaceIndependentEconomicEvidenceRepository`
- real durable evidence journal/change sequence
- `PostgresMarketplaceEconomicEvidenceChangeFeed`
- real V016 checkpoint persistence

Sequence:

1. real durable evidence change written
2. real feed returned change
3. experimental monotonic projection committed
4. crash injected before checkpoint advancement
5. real checkpoint remained NONE
6. change replayed from real feed
7. projection replay was no-op
8. real V016 checkpoint CAS advanced successfully

Proves Strategy B works across the real Slice A/P0.2 persistence boundary.

### Gate 7 — concurrent Strategy B chain

PASS.

Two workers processed the same real change concurrently.

Observed:

- exactly one projection mutation
- exactly one projection no-op
- exactly one checkpoint Advanced
- exactly one checkpoint Stale
- final projection correct
- final durable checkpoint correct

### Gate 8 — partial batch crash/replay

PASS.

Three real changes were available.

Before crash:

- first two materialized
- checkpoint remained NONE
- third not materialized

After replay from NONE:

- first two became deterministic no-ops
- third materialized
- checkpoint advanced to final real change

No manual cleanup required.

### Gate 9 — current-state refetch and stale invalidation

PASS.

Two evidence versions were written for the same subject.

After the newer evidence:

- repository `find(subject)` returned version 2
- projection materialized current version
- replay of the older invalidation still refetched current version 2
- stale change sequence could not overwrite the newer projection

Implication:

- the change feed can remain an invalidation stream
- the projection processor may refetch current committed aggregate state
- the feed does not need to become a full historical payload stream

### Gate 10 — rebuild from NONE over partially materialized projection

PASS.

Starting conditions:

- durable evidence preserved
- checkpoint NONE
- projection already contained some subjects
- one subject missing

Rebuild from NONE:

- already-materialized rows became no-ops
- missing row materialized
- checkpoint advanced to the latest real change

Result:

- projection is rebuildable without manual cleaning
- projection remains disposable derivative state

### Physical change-sequence gaps

Already proven by canonical Slice A tests; no duplicate EXP gate added.

Observed property:

- organization-specific changes may have physical sequence gaps because the global journal is shared
- pagination still reconstructs the organization stream correctly
- checkpoint can advance across a physical gap when destination is a real change for the organization

Implication:

- `change_sequence` is a physical journal position
- numeric subtraction must not be treated as pending-change count

### Gate 11 — Strategy C receipt semantics

PASS.

Experimental processed-change table added only to the harness.

First processing:

- receipt write = 1
- projection write = 1

Duplicate replay:

- receipt write = 0
- projection write = 0

Correctness requirement established:

- receipt and projection must share one transaction

Strategy C is therefore viable only with an additional durable receipt structure and its lifecycle.

### Gate 12 — fresh-path B vs C write cost

PASS.

Controlled batches:

- 1
- 10
- 100
- 1000

Warmups:

- 2

Measured repetitions:

- 5

Representative final run:

    batch=1
    B sqlMedianMs=1.7128
    C sqlMedianMs=3.101

    batch=10
    B sqlMedianMs=12.3716
    C sqlMedianMs=20.516

    batch=100
    B sqlMedianMs=58.758
    C sqlMedianMs=132.3211

    batch=1000
    B sqlMedianMs=639.8014
    C sqlMedianMs=1328.4724

At batch 1000:

Strategy B:

- 5000 projection writes
- 0 receipt writes

Strategy C:

- 5000 projection writes
- 5000 receipt writes

Result:

- Strategy C adds one durable receipt mutation per newly processed change
- the additional write cost becomes material at larger batches
- this is write amplification, not merely conceptual complexity

### Gate 13 — duplicate replay cost

PASS.

100% duplicate replay.

Representative final run:

    batch=1
    B sqlMedianMs=1.6316
    C sqlMedianMs=1.3644

    batch=10
    B sqlMedianMs=9.0561
    C sqlMedianMs=10.3867

    batch=100
    B sqlMedianMs=60.0925
    C sqlMedianMs=66.2814

    batch=1000
    B sqlMedianMs=628.9829
    C sqlMedianMs=603.204

Interpretation:

- small differences change direction across batch sizes/runs
- C does not demonstrate a stable replay-SQL advantage
- both paths still execute one indexed database statement per duplicate
- neither mutates rows during duplicate replay

No architectural decision is based on the small timing differences from this gate.

### Gate 14 — implicit dedup using projection sequence

PASS.

Tested whether Strategy B can use:

    projection.last_applied_change_sequence

as a cheap precheck to avoid current-state refetch on duplicate/stale changes.

Rule:

    if durable projection sequence >= incoming sequence
        skip refetch
        skip projection write
    else
        refetch current state
        perform monotonic upsert

The precheck is an optimization only.

The monotonic guarded upsert remains the concurrency/correctness authority.

Representative final run:

    batch=1
    B = 9.3388 ms
    C = 10.0613 ms

    batch=10
    B = 93.2674 ms
    C = 103.1822 ms

    batch=100
    B = 1042.1739 ms
    C = 1169.8805 ms

    batch=1000
    B = 9830.1464 ms
    C = 10850.8821 ms

Both strategies skipped all synthetic refetches.

Important limitation:

- this harness helper opens a connection per lookup
- absolute timings are therefore dominated by connection overhead
- Gate 14 proves the architectural property, not a production implementation pattern

Production must not copy the one-connection-per-subject benchmark shape.

## 6. Strategy comparison

| Property | A — same transaction | B — idempotent then checkpoint | C — receipt then checkpoint |
| --- | --- | --- | --- |
| Crash correctness | strong | strong by deterministic replay | strong only if receipt + projection atomic |
| Duplicate replay | safe | safe | safe |
| Older change protection | requires projection rule | proven | requires projection rule |
| Concurrent convergence | possible | proven | possible |
| Real V016 checkpoint compatibility | requires boundary change | proven | compatible |
| Partial batch recovery | possible | proven | possible |
| Rebuild from NONE | possible | proven | requires receipt lifecycle decision |
| Additional durable state | no | no | yes |
| Additional row per processed change | no | no | yes |
| Boundary preservation | poor | strong | strong |
| Fresh-path write amplification | low | lowest observed | highest observed |
| Explicit dedup | no | implicit via projection sequence | yes |
| Manual repair required | no | no | no if correctly implemented |

## 7. Decision

EXP-0007 selects:

**Strategy B — idempotent monotonic Sales Intelligence projection followed by separate CAS checkpoint advancement.**

Required processing shape:

    read change
      -> optional projection-sequence precheck
      -> refetch current committed evidence/economic state when needed
      -> monotonic projection upsert
      -> commit projection
      -> advance checkpoint separately

Recovery model:

    projection commit succeeds
      -> checkpoint advance fails/crash
      -> same change is replayed
      -> projection write becomes deterministic no-op
      -> checkpoint advances later

This preserves the current Evidence Store / Change Feed / Projection separation.

## 8. Why Strategy A is not selected

Strategy A provides strong atomicity but requires changing the accepted production transaction boundary.

The real change-feed adapter owns:

- its JDBC connection
- `autoCommit = false`
- checkpoint lock/CAS
- commit
- rollback

It does not expose transaction participation to a projection component.

Adopting A would therefore require:

- exposing connection/transaction context, or
- extracting checkpoint persistence into a shared unit-of-work boundary, or
- creating a coordinator that owns both projection and checkpoint persistence

EXP-0007 found no correctness property that requires this coupling because Strategy B already recovers deterministically.

## 9. Why Strategy C is not selected

Strategy C is correct only when receipt and projection are atomic locally.

It adds:

- processed-change table
- primary key/index
- one receipt mutation per fresh change
- storage growth proportional to processed history
- retention policy
- cleanup semantics
- rebuild semantics
- additional operational surface

Gate 12 demonstrated material fresh-path write amplification.

Gate 13 did not demonstrate a stable replay advantage.

Gate 14 demonstrated that Strategy B can use the projection's own durable sequence as implicit replay/stale detection without creating a second historical deduplication structure.

No property observed in EXP-0007 justifies Strategy C's permanent state cost.

## 10. Production invariants derived from EXP-0007

The future Slice B implementation must preserve:

1. Projection state is derivative, never canonical economic truth.
2. Every projection row stores the last applied physical change sequence.
3. A lower or equal incoming sequence cannot overwrite a newer projection.
4. Projection durability occurs before checkpoint advancement.
5. Failure after projection commit and before checkpoint advancement is recovered by replay.
6. Checkpoint advancement uses the existing durable CAS semantics.
7. A projection-sequence precheck may skip stale/duplicate work but is never a concurrency authority.
8. The monotonic write remains the final correctness guard.
9. Projection rebuild from NONE must work without manual clearing of already-valid rows.
10. Organization identity must participate in every projection key/access path.
11. Provider calls must remain outside synchronous list/detail read paths.
12. Read-side projection queries must be indexed and bounded.
13. No processed-change receipt table is required by the selected design.
14. No shared transaction between projection and checkpoint is required by the selected design.

## 11. Performance implications

The experiment supports the following architectural direction:

- materialize intelligence ahead of API/UI reads
- keep list/detail reads local to the projection
- use indexed access paths
- avoid journal scans in synchronous read paths
- avoid provider enrichment during page load
- process incremental changes in bounded batches
- avoid one JDBC connection per subject
- use production-appropriate connection pooling/reuse
- benchmark read p50/p95/p99 separately before declaring an SLA

Previously discussed latency values remain design budgets only and are not established by EXP-0007.

## 12. What EXP-0007 does not prove

EXP-0007 does not establish:

- production throughput at 10M/100M+ rows
- production p95/p99 latency
- optimal connection-pool sizing
- optimal batch size
- scheduler/fairness policy across organizations
- leases/claims/worker ownership
- provider retry strategy
- exact historical reconstruction from current-state invalidations
- final Sales Intelligence schema
- final API payload shape
- final UI contract
- production migration numbering
- production port/interface names
- a hard SLA

Those decisions belong to later ADR/SPEC/TASK/implementation gates.

## 13. Outcome

Functional and operational evidence favors Strategy B.

The experiment concludes that deterministic replay plus monotonic projection state gives the required crash/replay correctness while preserving the existing architecture and avoiding unnecessary permanent deduplication state.

Next governance step:

    EXP-0007
      -> ADR
      -> SPEC
      -> TASK
      -> production implementation

No production implementation is authorized by EXP-0007 itself.
