# SPEC-0046: Durable/Fast Sales Intelligence Projection — Slice B

Status: Accepted

Date: 2026-09-04

## Objective

Define the smallest inward-facing production contract required for P0.3 Slice B
to materialize current Sales Intelligence durably from the accepted economic
evidence change feed while preserving deterministic replay, organization
isolation, and fast local reads.

This specification covers P0.3 Slice B only:

- a durable organization-scoped Sales Intelligence projection contract;
- monotonic/idempotent projection materialization;
- a projection processor that consumes the existing Slice A change feed;
- projection commit before separate checkpoint advancement;
- deterministic replay after crash and checkpoint failure;
- bounded current-state refetch from canonical economic evidence;
- rebuild behavior;
- a candidate list/detail read-model shape;
- bounded indexed read-path requirements;
- production-focused persistence, concurrency, replay, rebuild, and
  representative-volume performance acceptance tests.

It does not define or authorize a migration, TASK, scheduler/fairness
mechanism, provider adapter, API/UI endpoint, external payload contract, or
production implementation.

## Authority and dependencies

This specification depends on and must preserve:

- ADR-0047, Accepted in `main` by PR #144 / merge commit `534da43`;
- EXP-0007, experimental commit `e761520`;
- `docs/evidence/EXP-0007-sales-intelligence-projection-atomicity.md`;
- ADR-0046 and SPEC-0045 for the independent change-feed/checkpoint boundary;
- P0.2 durable independent economic evidence and its existing repository;
- ADR-0048 for the canonical Economic Truth Assembly semantic boundary;
- SPEC-0047 for the accepted canonical Economic Truth Assembly production contract;
- ADR-0041 for the fast operational-intelligence read-path boundary.

Where this specification is silent, those accepted decisions remain
authoritative. EXP-0007 is evidence and must not be copied into production
without review.

## Scope boundary

Slice B introduces a new derivative Sales Intelligence boundary downstream of:

```text
MarketplaceIndependentEconomicEvidenceRepository
MarketplaceEconomicEvidenceChangeFeed
```

It must not extend, merge into, or alter either accepted port.

The conceptual dependency direction is:

```text
durable independent economic evidence
  -> MarketplaceEconomicEvidenceChangeFeed
  -> current evidence refetch
  -> MarketplaceEconomicTruthAssembler
      -> NotReady
           -> Sales Intelligence projection processor
      -> Ready(MarketplaceOrder)
           -> MarketplaceEconomicTruthCalculator
           -> Sales Intelligence projection processor
  -> durable Sales Intelligence projection
  -> future read API/UI
```

The projection is disposable derivative state. It is not canonical economic
truth and must never become the authority for evidence facts, corrections,
components, source provenance, or economic-history reconstruction.

## Projection identity

The production projection consumer must use one stable `ProjectionName`
constant owned by Slice B.

The value is:

```text
sales-intelligence
```

Checkpoint identity remains:

```text
organization_id + projection_name
```

The projection's own durable subject identity is:

```text
organization_id + marketplace_order_id
```

or an equivalent database-enforced key that maps one-to-one to the existing
`MarketplaceEconomicEvidenceSubject` identity.

A row for one organization must never read, suppress, update, or satisfy a
request for another organization.

## Candidate materialized record

The smallest logical Sales Intelligence record required by this slice is:

```text
organizationId
marketplaceOrderId
sourceEvidenceVersion
materializedPayload
lastAppliedChangeSequence
projectedAt
```

`marketplaceOrderId` must preserve the existing organization-scoped marketplace
economic subject identity.

`sourceEvidenceVersion` records the current canonical evidence version whose
committed state was used to derive the record.

`materializedPayload` is derivative operational read state. Its concrete
economic fields must be derived only from semantics already accepted by the
canonical economic boundary. This SPEC does not invent, rename, aggregate, or
reinterpret economic facts into new revenue, fee, cost, tax, net, margin,
confidence, completeness, or allocation fields.

If a later production TASK requires concrete economic fields that are not
already defined by an accepted canonical economic contract, that semantic
decision must be separately evidenced and accepted before those fields become
part of the projection contract.

`lastAppliedChangeSequence` records the newest consumed physical change
position that materially authorized the record state.

`projectedAt` is operational metadata for the materialization event. It is not
economic event time and must not be used for economic ordering.

## Payload authority

The projection payload is derived state only.

When materialization requires economic meaning, the processor must derive that
meaning through the accepted canonical chain:

```text
current committed MarketplaceIndependentEconomicEvidence
  -> MarketplaceEconomicTruthAssembler
      -> NotReady
           -> materialized unresolved canonical state
           -> no calculator invocation
      -> Ready(MarketplaceOrder)
           -> MarketplaceEconomicTruthCalculator
           -> materialized canonical calculation state
```

MarketplaceEconomicTruthAssemblyResult.NotReady is a valid canonical current-state
outcome, not a projection-processing or infrastructure failure.

When assembly returns NotReady, the processor must durably materialize an unresolved
projection state using only the accepted bounded assembly policy version and
NotReady reasons. It must not invoke MarketplaceEconomicTruthCalculator, manufacture
a MarketplaceOrder, or retain older economic values as if they were still current.

A successfully materialized NotReady state satisfies projection work for that
change_sequence and therefore does not, by itself, block checkpoint advancement.

Materializing a newer NotReady state must replace the prior semantic payload for
that subject atomically. Economic values from an older Ready state must not remain
addressable as current alongside the newer unresolved state.

The projection processor must not interpret activeFacts directly to reconstruct
MarketplaceOrder semantics, coverage, occurrence time, or calculator results.

It must not:

- infer missing economic meaning from provider payloads;
- reuse prior projection values as economic authority;
- treat checkpoint state as economic truth;
- convert absence/unknown evidence into zero without an accepted upstream
  semantic;
- manufacture confidence, allocation, completeness, margin, revenue, cost, or
  profitability semantics not already accepted upstream;
- bypass MarketplaceEconomicTruthAssembler when canonical order semantics are
  required;
- duplicate the assembler's occurrence, coverage, applicability, correction, or
  active-fact semantics inside the projection boundary;
- duplicate or replace MarketplaceEconomicTruthCalculator as the authority for
  economic calculation and Complete versus Incomplete results.

The projection must preserve organization and currency semantics from the
canonical subject/evidence boundary where those values participate in the
materialized payload.

Binary floating-point representation must not be introduced for canonical
monetary values.

## Projection persistence contract

Slice B introduces a dedicated persistence port. It must remain separate from
the economic repository and change feed.

Conceptually it provides exactly the capabilities required by this
specification:

```text
currentBySubject
materializeIfNewer
listByOrganization
detailByOrganizationAndSubject
```

No checkpoint operation belongs to this port.

No provider operation belongs to this port.

No scheduler, lease, claim, retry queue, fairness, or worker-ownership
operation belongs to this port.

### Closed persistence result

Infrastructure, mapping, malformed-data, transaction, and unexpected adapter
failures must fail closed without exposing SQL, SQLSTATE, table names,
constraint names, organization IDs, subject IDs, external IDs, monetary
values, or database values.

The exact result-family name is left to the future TASK, but the public
application surface must distinguish:

```text
success
integrity/infrastructure failure
```

without leaking persistence detail.

## Monotonic materialization rule

For one organization/subject, durable projection mutation is permitted only
when:

```text
incoming change_sequence > durable last_applied_change_sequence
```

If no record exists, the incoming change may create it.

If:

```text
incoming change_sequence == durable last_applied_change_sequence
```

the operation is a deterministic no-op.

If:

```text
incoming change_sequence < durable last_applied_change_sequence
```

the operation is a deterministic stale no-op.

The final durable write is the concurrency authority.

A prior read or precheck must never replace the guarded write.

Conceptual PostgreSQL behavior:

```sql
INSERT INTO <sales_intelligence_projection> (...)
VALUES (...)
ON CONFLICT (organization_id, marketplace_order_id)
DO UPDATE SET
    ...
WHERE EXCLUDED.last_applied_change_sequence
    > <sales_intelligence_projection>.last_applied_change_sequence
```

The final migration and table name are explicitly not authorized by this
specification.

## Materialization result

The materialization write must expose enough closed result information for the
processor and tests to distinguish:

```text
Applied
NoOpAlreadyCurrent
```

A lower sequence and an equal sequence may share the same no-op result if no
caller behavior depends on distinguishing them.

Infrastructure failure remains outside this semantic result and must use the
closed failure envelope.

The result must not expose raw SQL row counts as part of the domain contract.

## Processor contract

Slice B introduces an application processor that coordinates existing
accepted ports but owns no provider behavior.

For one organization and one bounded batch, the conceptual flow is:

```text
currentCheckpoint(organization, "sales-intelligence")
-> changesSince(organization, checkpoint, limit)
-> for each returned change in ascending change_sequence:
     optional durable projection-sequence precheck
     -> when needed, refetch current committed MarketplaceIndependentEconomicEvidence
     -> MarketplaceEconomicTruthAssembler.assemble
     -> if NotReady: materializeIfNewer the canonical unresolved assembly state
        without invoking MarketplaceEconomicTruthCalculator
     -> if Ready: MarketplaceEconomicTruthCalculator.calculate
        -> materializeIfNewer the canonical calculation state
-> after all intended changes through destination are durably handled
     advanceCheckpoint(expected, destination)
```

The processor must not advance the checkpoint before projection durability.

The processor must not require a shared JDBC transaction between projection
and checkpoint persistence.

The processor must not write canonical economic evidence.

## Current-state invalidation semantics

A Slice A change is an invalidation/cursor record, not an economic snapshot.

When a change requires materialization, the processor refetches the current
committed MarketplaceIndependentEconomicEvidence through the accepted
economic repository/read boundary and passes that evidence through
MarketplaceEconomicTruthAssembler. The calculator is invoked only for Ready
assembly results; NotReady is materialized without economic calculation.

The current state may be newer than the `evidenceVersion` that caused the
consumed invalidation.

Therefore this slice materializes current operational intelligence, not an
exact historical aggregate at every invalidation sequence.

The projection's `sourceEvidenceVersion` must reflect the current committed
state actually materialized, while `lastAppliedChangeSequence` must reflect the
change position that authorized the write.

## Optional sequence precheck

Before refetching canonical evidence, the processor may read the durable
projection's `lastAppliedChangeSequence`.

When:

```text
durable sequence >= incoming sequence
```

it may skip current-state refetch and projection mutation.

This optimization is never authoritative.

Two concurrent workers may both pass the precheck. Correctness must still
depend on the final monotonic durable write.

The production implementation must remain correct with the precheck disabled.

## Batch checkpoint rule

For a batch returned in ascending sequence order, the processor may advance the
checkpoint only through the last change whose required projection work is
durably complete or deterministically already complete.

If processing fails on a change, the checkpoint must not advance beyond that
change.

For the smallest authorized implementation, a batch is acknowledged only
after the complete returned batch is handled successfully.

Partial-prefix checkpoint advancement is not required by this slice.

The destination passed to `advanceCheckpoint` must be the final returned
change sequence of the successfully handled batch.

## Crash and replay semantics

The accepted failure window is:

```text
projection COMMIT succeeds
-> worker/process fails before checkpoint advancement
-> checkpoint remains behind
```

Recovery is:

```text
same change is returned again
-> monotonic projection write is no-op
-> checkpoint advancement is retried
```

This is intentional at-least-once processing.

The following invariant is mandatory:

```text
checkpoint must never claim progress beyond projection work already durable
```

If projection materialization fails or rolls back, checkpoint advancement for
that batch must not occur.

No manual data repair may be required for the tested transactional failure
windows covered by EXP-0007.

This statement does not claim resilience to untested storage corruption,
region loss, or arbitrary infrastructure catastrophe.

## Concurrency

Two workers may process the same organization or same subject concurrently.

For the same subject with sequences `a < b`, every permitted interleaving must
converge to:

```text
lastAppliedChangeSequence = b
```

and the materialized state produced by the newest accepted write.

An older writer must never overwrite a newer durable record.

Concurrent duplicate processing of the same sequence must yield at most one
material state mutation.

Checkpoint concurrency remains governed exclusively by SPEC-0045 CAS
semantics. Slice B adds no second checkpoint mechanism.

## Rebuild

A rebuild must be possible using canonical upstream evidence plus the accepted
change feed.

The logical rebuild starts from:

```text
checkpoint NONE
```

for an isolated rebuilding projection identity or equivalent isolated rebuild
state.

Replay must tolerate:

- an empty projection;
- a partially populated projection;
- already-current rows;
- rows ahead of replayed invalidations;
- global `change_sequence` gaps;
- repeated changes for the same subject.

Already-current/newer rows become deterministic no-ops.

Missing/outdated rows are materialized.

Rebuild correctness must not depend on destructive clearing of otherwise-valid
projection rows.

This specification does not authorize or choose:

```text
in-place large-scale rebuild
shadow table
versioned projection
dual-write
cutover protocol
```

Those mechanics require measured scale evidence if they become necessary.

## Read-model contract

The projection exists to support future fast local list/detail reads.

This slice defines the persistence read shape required for later API/UI work
without defining external HTTP contracts.

### Detail read

Detail lookup is scoped by exactly:

```text
organization_id + marketplace_order_id
```

It returns zero or one materialized Sales Intelligence record.

Cross-organization lookup is forbidden.

The adapter must use a bounded indexed point-lookup path.

### List read

List lookup is scoped to exactly one organization.

The smallest list contract must support deterministic bounded pagination over
an indexed stable ordering.

The implementation must use cursor/keyset semantics or an equivalent bounded
indexed continuation mechanism. Unbounded OFFSET scanning is not permitted for
the accepted production path.

The page limit must be validated before persistence access and must have a
finite upper bound defined by the future TASK together with the concrete query
and index plan.

This SPEC intentionally does not freeze:

```text
exact sort key
exact cursor token shape
exact maximum page size
```

Those are implementation-contract decisions that must be selected together
with the concrete row shape, index design, and representative-volume evidence.

The read returns at most the requested bounded page plus only the minimal
continuation metadata required to continue.

This specification does not authorize filter/sort expansion, full-text search,
analytics aggregation, faceting, export, or arbitrary user-selected ordering.

Any later external API may add those only through a separately accepted
contract and measured index plan.

## Fast-read invariants

Future synchronous list/detail callers must never:

- call Mercado Livre, Omie, or any external provider;
- scan the economic evidence journal;
- reconstruct full evidence history;
- replay corrections on demand;
- call the change-feed checkpoint path to assemble a response;
- perform unbounded organization scans;
- require cross-organization joins.

The read path must be served from local durable materialized projection state.

## Candidate index requirements

The future persistence design must provide database-enforced uniqueness for the
organization-scoped projection subject identity.

It must also provide the smallest concrete index set that proves:

```text
bounded organization-scoped detail lookup
bounded deterministic organization-scoped list pagination
no full-table scan for accepted detail access
no unbounded organization sort/scan for accepted list access
```

The exact list-order index is intentionally not frozen by this SPEC because the
final sort key and cursor contract must be selected together with the concrete
read-model shape and measured query plan.

Additional indexes are not authorized merely for speculative future filters.

## Performance acceptance gate

EXP-0007 did not benchmark a final production Sales Intelligence read model at
large projection volume. No hard production SLA is claimed by that experiment.

Before Slice B implementation can be accepted, the implementation gate must
measure the final candidate projection shape with real PostgreSQL and
representative data volume.

The minimum performance evidence must include:

```text
indexed detail lookup
first list page
middle keyset page
last/reduced page
duplicate replay write path
fresh projection write path
```

Measurements must record:

- PostgreSQL version;
- row count;
- organization distribution;
- indexes;
- query plans;
- warm/cold methodology where applicable;
- connection-pool behavior used by the production adapter;
- p50/p95 or an equivalent distribution across repeated samples.

A single best-case timing is insufficient evidence.

The gate must demonstrate bounded indexed access and absence of full-table or
full-organization sort/scan behavior for the accepted list/detail queries.

No numeric latency SLA is frozen by this SPEC.

## Representative volume

The performance gate must use a dataset large enough to expose access-path
mistakes rather than only functional correctness.

At minimum it must include:

```text
>= 1,000,000 projection rows total
>= 100,000 rows in one organization
multiple organizations with materially different sizes
```

If the implementation environment cannot execute this gate reliably, the TASK
must explicitly separate functional acceptance from performance acceptance;
the Slice B implementation must not be declared fully accepted until the
representative-volume gate passes.

## Persistence transaction boundary

A projection materialization call owns its own durable transaction.

A successful `Applied` result means the projection mutation is committed.

A successful no-op result means no projection mutation was required because the
durable record was already at the same or a newer change sequence.

Checkpoint advancement remains a separate transaction owned by the existing
change-feed adapter.

No shared connection or external transaction coordinator is required or
authorized.

## Data-integrity requirements

The future durable projection must enforce, directly or through equivalent
database constraints:

- non-null organization identity;
- non-null subject identity;
- organization/subject uniqueness;
- non-negative `lastAppliedChangeSequence`;
- valid currency representation;
- no binary floating-point monetary storage;
- no projection row that can be addressed without organization scope.

If a durable projection record cannot be mapped into the accepted application
contract, the adapter must fail closed rather than silently coerce it.

## Deletion semantics

Slice B does not infer physical deletion from absence of a current evidence
lookup.

If the canonical economic boundary has no current state for a change subject,
the processor must fail closed for that work unless an accepted upstream
semantic explicitly represents a valid tombstone/deletion state.

This slice does not introduce a projection tombstone contract.

A future deletion/tombstone requirement needs separate evidence and an
accepted specification change.

## Observability contract

The implementation must expose enough sanitized telemetry to determine:

```text
changes read
projection writes applied
projection writes no-op
checkpoint advances
checkpoint stale results
batch failures
projection integrity failures
current-state refetch failures
batch duration
```

Telemetry must be organization-safe and must not log raw economic payloads,
external marketplace order IDs, SQL, credentials, provider payloads, or
sensitive database values.

Metric cardinality must not use organization IDs, marketplace order IDs, or
external order IDs as unbounded labels.

Structured logs may carry an approved internal correlation identifier if the
existing observability conventions permit it.

## Required acceptance tests

A future TASK for Slice B must include real PostgreSQL/Testcontainers tests
covering at least:

1. first materialization creates one durable record;
2. same-sequence replay is a no-op;
3. lower-sequence replay is a no-op;
4. higher sequence replaces older projection state;
5. concurrent lower/higher writers converge to the higher sequence;
6. concurrent duplicate writers create at most one material mutation;
7. projection commit followed by simulated checkpoint failure replays safely;
8. projection failure prevents checkpoint advancement;
9. full-batch success advances checkpoint to final returned sequence;
10. failed batch leaves checkpoint behind the failed work;
11. stale invalidation may refetch newer current evidence safely;
12. optional sequence precheck can skip duplicate refetch;
13. correctness remains valid with precheck disabled;
14. rebuild from NONE over empty projection succeeds;
15. rebuild over partial projection fills missing/outdated rows;
16. rebuild over already-ahead rows leaves them unchanged;
17. organization A cannot read/update organization B projection state;
18. detail read is organization-scoped and bounded;
19. list pagination is deterministic, bounded, and continuation-based;
20. list/detail queries use the accepted concrete index plan at representative
    scale;
21. malformed durable projection data fails closed;
22. persistence failures do not leak SQL or database detail;
23. no provider call exists on projection list/detail read paths.

Mocks/fakes may not substitute for PostgreSQL where transaction,
constraint, index, query-plan, concurrency, or replay semantics are being
accepted.

## Regression invariants

The Slice B TASK must prove that it does not change:

```text
MarketplaceIndependentEconomicEvidenceRepository
MarketplaceEconomicEvidenceChangeFeed
```

The P0.2 economic repository must remain exactly its accepted `find/apply`
contract.

The Slice A change feed must remain exactly its accepted four-operation
contract.

No checkpoint or projection operation may be added to the economic repository.

No Sales Intelligence materialization operation may be added to the change
feed.

## Explicit non-goals

This SPEC does not define or authorize:

- a Flyway migration number;
- a production table name;
- final Kotlin package/file names;
- scheduler or cross-organization fairness policy;
- leases, claims, worker ownership, or `SKIP LOCKED`;
- automatic retries/backoff policy;
- dead-letter queues;
- Mercado Livre adapter behavior;
- Omie adapter behavior;
- Financial Ledger or Reconciliation changes;
- provider enrichment;
- API endpoints;
- external API response JSON;
- UI layout or page composition;
- user-configurable filters/sorts/search;
- evidence timeline UX;
- exact historical aggregate reconstruction;
- projection tombstones/deletions;
- large-scale rebuild cutover mechanics;
- a hard latency or throughput SLA;
- a TASK;
- production implementation.

## Authorization

If accepted after human review, this SPEC authorizes drafting one separately
bounded TASK for P0.3 Slice B production implementation.

That future TASK may propose the smallest concrete migration, application
types, persistence adapter, processor, tests, and measured performance gate
needed to implement this SPEC.

Acceptance of this SPEC does not itself authorize production code, a migration,
provider integration, API/UI, scheduler/fairness behavior, or implementation.
