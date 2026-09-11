# SPEC-0048: Durable OrderOccurrence Persistence

Status: Accepted

Date: 2026-09-06

Source decision: ADR-0049

## Objective

Define the smallest durable PostgreSQL production contract required to persist,
reload, correct, replay, and expose
`MarketplaceIndependentEconomicFact.OrderOccurrence` without creating a second
economic evidence authority or changing the accepted canonical semantics from
ADR-0049 and SPEC-0047.

This specification closes exactly one production gap:

```text
canonical OrderOccurrence evidence
  -> durable PostgreSQL evidence store
  -> reload / replay
  -> activeFacts
  -> MarketplaceEconomicTruthAssembler
```

This specification does not authorize implementation by itself.

Implementation remains blocked until a subsequent TASK explicitly authorizes the
bounded code and migration slice defined here.

## Authority and dependencies

This specification preserves and depends on:

- ADR-0042 and SPEC-0041, Independent Marketplace Economic Evidence;
- ADR-0043 and SPEC-0042, Durable Independent Marketplace Economic Evidence;
- ADR-0046 and SPEC-0045, Durable Marketplace Economic Evidence Incremental Change Feed;
- ADR-0048 and SPEC-0047, Canonical Economic Truth Assembly;
- ADR-0049, Durable OrderOccurrence Persistence Boundary;
- TASK-0147, canonical Economic Truth Assembly implementation.

Where this specification is silent, those accepted contracts remain
authoritative.

`MarketplaceIndependentEconomicEvidence` remains the canonical evidence
aggregate.

`PostgresMarketplaceIndependentEconomicEvidenceRepository` remains the durable
persistence boundary.

`MarketplaceEconomicTruthAssembler` remains the canonical authority for turning
active evidence into `Ready` or `NotReady`.

No persistence rule in this specification may reinterpret domain identity,
duplicate/conflict semantics, correction semantics, coverage, applicability, or
economic calculation.

## Production boundary

The implementation remains inside the existing PostgreSQL persistence module:

```text
applications/marketplace-operations-persistence-postgres
```

The implementation may modify only the existing independent economic evidence
repository and its persistence tests, plus one additive Flyway migration.

No new Gradle module, repository abstraction, alternate store, projection store,
or provider-specific persistence path is required or authorized.

## Migration

The next available migration is fixed as:

```text
V017__add_order_occurrence_to_independent_marketplace_economic_evidence.sql
```

V015 and V016 are historical and immutable.

V017 must be additive.

V017 must not:

- rewrite V015;
- rewrite V016;
- backfill historical OrderOccurrence rows;
- infer occurredAt from any existing timestamp;
- emit historical evidence versions;
- emit historical change_sequence values;
- mutate existing subjects;
- mutate existing facts;
- mutate existing corrections;
- create provider-specific tables;
- create a second sequence, journal, checkpoint, or receipt stream.

## Parent fact discriminator

V017 extends the existing
`marketplace_economic_evidence_fact.fact_kind` domain from:

```text
COMPONENT
EXTERNAL_IDENTITY
```

to:

```text
COMPONENT
EXTERNAL_IDENTITY
ORDER_OCCURRENCE
```

The durable representation of
`MarketplaceIndependentEconomicFact.OrderOccurrence` is exactly:

```text
fact_kind = ORDER_OCCURRENCE
family = MARKETPLACE_ORDER
```

`ORDER_OCCURRENCE` is a fact discriminator only.

It is not an `EconomicComponentType`.

It is not an external-identity kind.

It is not a collection-attempt kind.

It is not a correction kind.

## Dedicated subtype table

V017 creates exactly one new subtype table:

```text
marketplace_economic_evidence_order_occurrence_fact
```

The table must contain:

```text
organization_id uuid NOT NULL
marketplace_order_id uuid NOT NULL
fact_id uuid NOT NULL
evidence_version bigint NOT NULL
fact_kind text NOT NULL DEFAULT 'ORDER_OCCURRENCE'
family text NOT NULL DEFAULT 'MARKETPLACE_ORDER'
occurred_at timestamptz(6) NOT NULL
source_kind text NOT NULL
source_system_key text NOT NULL
source_external_reference text NULL
source_external_reference_absence_reason text NULL
```

The primary key is:

```text
(organization_id, marketplace_order_id, fact_id)
```

The subtype row must reference the parent fact using the existing structural
tuple:

```text
(
  organization_id,
  marketplace_order_id,
  fact_id,
  evidence_version,
  fact_kind,
  family
)
```

No surrogate database identity is introduced.

## Structural subtype invariants

The subtype table must enforce:

```text
fact_kind = 'ORDER_OCCURRENCE'
family = 'MARKETPLACE_ORDER'
```

The database must reject any OrderOccurrence subtype row whose parent fact is
not structurally the same fact/version/family.

The database must reject standalone subtype rows.

The database must not allow one subtype row to point at another subject or
another evidence version.

## Timestamp representation

Canonical `OrderOccurrence.occurredAt` is stored directly as:

```text
occurred_at timestamptz(6) NOT NULL
```

The existing parent fact:

```text
observed_at timestamptz(6) NOT NULL
```

remains the durable representation of `observedAt`.

The two timestamps must remain separate.

Persistence must preserve whole-microsecond precision.

The write path must not:

- derive occurredAt;
- replace occurredAt with observedAt;
- replace occurredAt with transaction time;
- round below microsecond precision;
- choose earliest evidence time;
- choose latest evidence time;
- use committed_at;
- use any component occurred_at;
- use any external-identity occurred_at.

The read path must not synthesize either timestamp.

## Source representation

The subtype reuses the accepted durable source shape:

```text
source_kind
source_system_key
source_external_reference
source_external_reference_absence_reason
```

Allowed `source_kind` values remain exactly:

```text
MARKETPLACE
ERP
MANUAL
CALCULATED
```

`source_system_key` preserves the existing controlled system-key shape.

`source_external_reference` preserves the existing bounded external-reference
shape.

`source_external_reference_absence_reason`, when present, is exactly:

```text
INTERNAL_ORIGIN
```

No new source vocabulary is introduced.

## Source-shape invariant

The subtype must enforce the same source-shape contract already used by durable
component and external-identity facts.

For `MARKETPLACE` and `ERP`:

```text
source_external_reference IS NOT NULL
source_external_reference_absence_reason IS NULL
```

For `MANUAL` and `CALCULATED`, either:

```text
source_external_reference IS NOT NULL
source_external_reference_absence_reason IS NULL
```

or:

```text
source_external_reference IS NULL
source_external_reference_absence_reason = 'INTERNAL_ORIGIN'
```

Persistence must never manufacture an external reference.

## Domain source-fact identity remains authoritative

For provider-originated `MARKETPLACE` and `ERP` OrderOccurrence facts, the
canonical source-fact key remains the accepted domain key:

```text
source.kind
+ source.systemKey
+ source.externalReference
+ ORDER_OCCURRENCE
```

The database may add structural constraints but must not create a second
semantic source-fact identity.

The repository must continue to rely on the domain aggregate for duplicate and
conflict classification.

A SQL uniqueness constraint must not silently redefine:

```text
Duplicate
SourceFactConflict
```

## Observation write transaction

Persisting one accepted OrderOccurrence observation must occur inside the same
transaction used by the existing durable evidence repository.

One successful observation commits exactly one evidence update containing:

```text
1 subject current_version transition
1 marketplace_economic_evidence_update row
1 marketplace_economic_evidence_identifier row
1 marketplace_economic_evidence_fact parent row
1 marketplace_economic_evidence_order_occurrence_fact subtype row
```

The update journal row uses:

```text
change_kind = FACT
```

The identifier row uses:

```text
identifier_kind = FACT
```

The parent fact uses:

```text
fact_kind = ORDER_OCCURRENCE
family = MARKETPLACE_ORDER
```

No second transaction is permitted for the subtype row.

## Observation rollback invariant

If any OrderOccurrence persistence step fails, the complete observation update
must roll back.

After rollback there must be no durable:

- subject version increment;
- update row;
- identifier row;
- parent fact row;
- subtype row;
- visible organization change_sequence.

Sequence allocation gaps caused by PostgreSQL sequence behavior are not treated
as committed changes.

Downstream visibility remains based only on committed journal rows.

## Correction replacement transaction

An accepted correction whose replacement fact is OrderOccurrence must use the
existing correction transaction.

One successful correction commits:

```text
1 subject current_version transition
1 CORRECTION update row
1 correction identifier row
1 replacement fact identifier row
1 replacement parent fact row
1 replacement OrderOccurrence subtype row
1 correction row
```

The replacement parent fact uses:

```text
fact_kind = ORDER_OCCURRENCE
family = MARKETPLACE_ORDER
```

The replacement subtype must be durable before the transaction becomes visible
as committed.

No partial replacement is permitted.

## Correction historical semantics

After successful correction and reload:

```text
superseded OrderOccurrence
  -> historicalFacts

replacement OrderOccurrence
  -> activeFacts

correction
  -> preserved explicit supersession
```

The old subtype row remains durable.

Correction never deletes or mutates the superseded fact.

The replacement fact receives its own fact identifier and evidence version under
the existing correction contract.

## Repository write support

`PostgresMarketplaceIndependentEconomicEvidenceRepository` must add write support
for:

```text
MarketplaceIndependentEconomicFact.OrderOccurrence
```

The temporary TASK-0147 fail-closed unsupported-OrderOccurrence branch must be
removed only as part of the authorized implementation after V017 support exists.

The repository must continue to fail closed for truly unknown fact kinds.

The repository must not route OrderOccurrence through the component or
external-identity persistence branches.

## Repository read support

Repository reconstruction must recognize parent rows with:

```text
fact_kind = ORDER_OCCURRENCE
family = MARKETPLACE_ORDER
```

and load exactly one matching subtype row.

It must reconstruct exactly:

```text
MarketplaceIndependentEconomicFact.OrderOccurrence(
  MarketplaceEconomicOrderOccurrenceObservation(
    id = fact_id,
    subject = durable subject,
    source = durable source,
    occurredAt = subtype.occurred_at,
    observedAt = parent.observed_at
  )
)
```

No reconstruction field may come from unrelated facts or update timestamps.

## Read integrity failures

Reload must fail closed through the repository's sanitized integrity surface when
any of the following durable states are observed:

- ORDER_OCCURRENCE parent with no subtype row;
- ORDER_OCCURRENCE parent with more than one matching subtype row;
- subtype row whose structural parent tuple is invalid;
- subtype row with non-ORDER_OCCURRENCE fact_kind;
- subtype row with non-MARKETPLACE_ORDER family;
- malformed durable source shape;
- unsupported durable fact_kind;
- durable value that cannot reconstruct the accepted domain object.

The repository must not skip malformed OrderOccurrence state.

The repository must not downgrade malformed state into absence.

## Restart equivalence

For any successfully persisted OrderOccurrence aggregate:

```text
persist(A)
reload()
```

must reconstruct an aggregate semantically equivalent to `A`.

After restart, the following behavior must remain identical to the in-memory
domain:

- duplicate classification;
- source-fact conflict classification;
- correction eligibility;
- activeFacts membership;
- historicalFacts membership;
- assembler occurrence resolution.

Restart must not change which OrderOccurrence is active.

## Duplicate after restart

Acceptance tests must prove:

1. persist an OrderOccurrence observation;
2. destroy/recreate repository process state;
3. reload durable evidence;
4. apply semantically duplicate OrderOccurrence evidence;
5. receive the same domain duplicate result as before restart;
6. commit no new evidence update.

No additional change_sequence may become committed for the duplicate.

## Conflict after restart

Acceptance tests must prove:

1. persist an OrderOccurrence observation;
2. reload durable evidence;
3. apply the same canonical provider source-fact identity with a different
   `occurredAt`;
4. receive the canonical conflict result;
5. commit no replacement implicitly;
6. preserve the original active fact until explicit correction.

## Correction after restart

Acceptance tests must prove:

1. persist an OrderOccurrence observation;
2. reload durable evidence;
3. apply an explicit valid correction with replacement OrderOccurrence;
4. commit one correction evidence version;
5. reload again;
6. preserve the old occurrence in historicalFacts;
7. expose only the replacement occurrence through activeFacts.

## Change-feed compatibility

OrderOccurrence introduces no new change-feed API.

A committed OrderOccurrence observation or correction changes the evidence
subject through the existing:

```text
marketplace_economic_evidence_update
organization-scoped change_sequence
```

pipeline.

The existing `MarketplaceEconomicEvidenceChangeFeed` continues to expose changed
subjects.

No OrderOccurrence-specific cursor, checkpoint, receipt, or event table is
authorized.

V016's existing projection checkpoint remains unchanged.

## Existing-data compatibility

Applying V017 to a database with pre-existing evidence must preserve all prior
rows and meanings.

For an existing subject without OrderOccurrence evidence:

```text
reload()
```

continues to produce a valid evidence aggregate with no active OrderOccurrence.

Canonical assembly therefore remains:

```text
NotReady(ORDER_OCCURRED_AT_UNRESOLVED)
```

until real authoritative OrderOccurrence evidence is ingested.

Migration success must not make historical subjects `Ready`.

## No backfill

V017 performs no data backfill.

The implementation may not derive an OrderOccurrence from:

- marketplace subject creation;
- payment facts;
- revenue facts;
- shipping facts;
- external identities;
- update committed_at;
- collection attempt timestamps;
- ERP order time;
- invoice time;
- any minimum/maximum timestamp heuristic.

Missing authoritative occurrence remains missing.

## Concurrency

OrderOccurrence uses the repository's existing subject/version concurrency
boundary.

No OrderOccurrence-specific lock is introduced.

Concurrent accepted updates to the same evidence subject must continue to obey
the existing version progression and transaction serialization rules.

The implementation must not weaken existing `FOR UPDATE` or version-progression
semantics.

## Append-only guarantees

OrderOccurrence parent rows and subtype rows are append-only historical evidence.

No production update or delete path is authorized for persisted
OrderOccurrence facts.

Corrections append new facts and supersession metadata.

They do not mutate old fact payloads.

Any database-level append-only protections that cover durable fact history must
also cover the new subtype table.

## Redaction and diagnostics

Persistence errors exposed beyond the repository must remain sanitized.

Logs, exceptions, `toString`, or integrity failures must not leak source
references beyond the repository's existing redaction policy.

This specification introduces no new diagnostic endpoint or debug API.

## Acceptance test matrix

The implementation TASK must include PostgreSQL/Testcontainers acceptance
coverage for at least:

### Migration

- V017 applies cleanly after V016;
- existing rows survive unchanged;
- existing subjects receive no OrderOccurrence backfill;
- V015 and V016 remain unchanged.

### Structural constraints

- ORDER_OCCURRENCE parent accepts MARKETPLACE_ORDER;
- subtype rejects another fact_kind;
- subtype rejects another family;
- subtype cannot exist without matching parent;
- source-shape checks reject malformed source provenance;
- timestamptz(6) precision is retained.

### Observation persistence

- MARKETPLACE OrderOccurrence round-trip;
- ERP OrderOccurrence round-trip;
- MANUAL internal-origin round-trip;
- CALCULATED internal-origin round-trip;
- observedAt and occurredAt remain distinct;
- evidence version increments once;
- exactly one committed FACT update is visible.

### Duplicate/conflict

- duplicate before restart;
- duplicate after restart;
- conflict before restart;
- conflict after restart;
- duplicate/conflict produce no implicit replacement.

### Correction

- replacement OrderOccurrence persists atomically;
- superseded occurrence remains historical;
- replacement is active after restart;
- failed correction leaves state unchanged;
- failed replacement does not expose committed change_sequence.

### Reconstruction integrity

- missing subtype fails closed;
- malformed subtype/source shape fails closed;
- unsupported durable fact kind fails closed;
- no malformed state is silently skipped.

### Change feed

- successful observation appears through the existing changed-subject feed;
- successful correction appears through the existing changed-subject feed;
- duplicate/conflict without committed update does not create downstream change;
- no second cursor/checkpoint path exists.

### Assembly after reload

- no occurrence -> `ORDER_OCCURRED_AT_UNRESOLVED`;
- one active durable occurrence -> assembler uses exact persisted occurredAt;
- conflicting active durable occurrences -> `ORDER_OCCURRED_AT_CONFLICT`;
- corrected historical occurrence does not re-enter active assembly.

## Required implementation surfaces

A future implementation TASK may authorize only the smallest required surfaces,
expected to include:

1. CREATE
   `applications/marketplace-operations-persistence-postgres/src/main/resources/db/migration/V017__add_order_occurrence_to_independent_marketplace_economic_evidence.sql`

2. MODIFY
   `applications/marketplace-operations-persistence-postgres/src/main/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceIndependentEconomicEvidenceRepository.kt`

3. MODIFY
   `applications/marketplace-operations-persistence-postgres/src/test/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceIndependentEconomicEvidenceRepositoryTest.kt`

4. CREATE or MODIFY only narrowly required migration-contract tests if the
   repository convention requires a separate migration test surface.

5. CREATE the bounded TASK evidence document.

6. Append the executive journal entry required by repository governance.

The TASK must freeze the final path list before implementation begins.

No provider, assembler, calculator, Sales Intelligence, API, UI, or unrelated
persistence file may be added merely for convenience.

## Explicitly out of scope

This specification does not authorize:

- Mercado Livre ingestion;
- Omie ingestion;
- provider timestamp mapping;
- historical OrderOccurrence backfill;
- economic component changes;
- external-identity changes unrelated to structural parent support;
- Economic Truth Assembly semantic changes;
- calculator changes;
- Sales Intelligence implementation;
- TASK-0146 resumption;
- API/UI;
- MGI;
- VOI;
- Bayesian belief;
- opportunity intelligence;
- Hypothesis Ledger;
- new evidence families;
- new component types;
- new change-feed semantics;
- a second evidence repository;
- a new projection checkpoint;
- a new event/outbox stream.

## Failure model

Until the subsequent implementation TASK is accepted and merged, the current
fail-closed PostgreSQL behavior for unsupported OrderOccurrence remains the
correct production behavior.

The implementation gate is passed only when:

- V017 is present and validated;
- write support is atomic;
- correction replacement is atomic;
- restart equivalence is proven;
- duplicate/conflict equivalence is proven;
- change-feed compatibility is proven;
- malformed durable state fails closed;
- complete repository CI is green.

## Governance effect

If this SPEC is accepted, it authorizes drafting exactly one bounded
implementation TASK for durable OrderOccurrence persistence.

That TASK may implement only the production contract frozen here.

Acceptance of this SPEC does not itself authorize code or migration changes.

TASK-0146 remains paused until the durable OrderOccurrence implementation is
merged and accepted and canonical assembly can consume authoritative occurrence
evidence after durable restart.
