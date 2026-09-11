# TASK-0148: Durable OrderOccurrence Persistence

Status: Implementation verified locally; PR/CI/merge pending

Date: 2026-09-06

## Authority

This implementation task is governed by:

- ADR-0049, Accepted;
- SPEC-0048, Accepted;
- ADR-0043 and SPEC-0042 for durable independent marketplace economic evidence;
- ADR-0046 and SPEC-0045 for the durable evidence change feed;
- ADR-0048 and SPEC-0047 for canonical Economic Truth Assembly;
- TASK-0147 for the already-implemented OrderOccurrence domain and assembler semantics.

SPEC-0048 is normative.

This TASK authorizes only the smallest durable PostgreSQL implementation slice
required to make canonical `OrderOccurrence` evidence survive restart without
changing any accepted semantic boundary.

## Repository checkpoint

At TASK drafting:

- TASK-0147 is merged;
- ADR-0049 is Accepted;
- SPEC-0048 is Accepted;
- TASK-0146 remains paused;
- V015 and V016 are immutable;
- V017 is reserved by SPEC-0048 for this task.

## Objective

Implement durable PostgreSQL support for:

```text
MarketplaceIndependentEconomicFact.OrderOccurrence
```

such that:

```text
apply
  -> commit
  -> restart
  -> reload
  -> same canonical evidence semantics
  -> same activeFacts / historicalFacts
  -> same duplicate/conflict behavior
  -> same assembler occurrence resolution
```

No provider ingestion is part of this task.

## Closed implementation scope

TASK-0148 may touch exactly these five paths:

1. CREATE
   `applications/marketplace-operations-persistence-postgres/src/main/resources/db/migration/V017__add_order_occurrence_to_independent_marketplace_economic_evidence.sql`

2. MODIFY
   `applications/marketplace-operations-persistence-postgres/src/main/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceIndependentEconomicEvidenceRepository.kt`

3. MODIFY
   `applications/marketplace-operations-persistence-postgres/src/test/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceIndependentEconomicEvidenceRepositoryTest.kt`

4. CREATE, then MODIFY only as the task evidence record
   `docs/evidence/TASK-0148-durable-order-occurrence-persistence.md`

5. MODIFY by appending exactly one TASK-0148 execution entry
   `docs/journal/MGI-EXECUTIVE-JOURNAL.md`

No sixth path is authorized.

Discovery that another production, test, build, migration, provider, API, UI,
projection, configuration, or helper file is required is a stop condition.

## Migration

Create exactly:

```text
V017__add_order_occurrence_to_independent_marketplace_economic_evidence.sql
```

V017 must be additive.

V017 must:

- extend `marketplace_economic_evidence_fact.fact_kind` to include
  `ORDER_OCCURRENCE`;
- create `marketplace_economic_evidence_order_occurrence_fact`;
- enforce `fact_kind = 'ORDER_OCCURRENCE'`;
- enforce `family = 'MARKETPLACE_ORDER'`;
- use the existing structural foreign-key tuple to the parent fact;
- persist `occurred_at timestamptz(6)`;
- persist the accepted durable source shape;
- enforce the existing MARKETPLACE/ERP versus MANUAL/CALCULATED source shape;
- preserve append-only fact history.

V017 must not:

- modify V015 or V016;
- backfill any OrderOccurrence;
- infer any timestamp;
- create a new sequence;
- create a new journal;
- create a new checkpoint;
- create a receipt table;
- create provider-specific storage;
- mutate existing evidence rows.

## Parent fact encoding

Persist canonical OrderOccurrence with exactly:

```text
fact_kind = ORDER_OCCURRENCE
family = MARKETPLACE_ORDER
```

`ORDER_OCCURRENCE` remains a fact discriminator only.

It must not become:

- an EconomicComponentType;
- an identity kind;
- an attempt kind;
- a correction kind.

## Subtype encoding

The subtype table is exactly:

```text
marketplace_economic_evidence_order_occurrence_fact
```

Required columns:

```text
organization_id
marketplace_order_id
fact_id
evidence_version
fact_kind
family
occurred_at
source_kind
source_system_key
source_external_reference
source_external_reference_absence_reason
```

No surrogate key is authorized.

## Timestamp semantics

Persist:

```text
OrderOccurrence.occurredAt
  -> subtype.occurred_at

OrderOccurrence.observedAt
  -> parent.observed_at
```

Both remain `timestamptz(6)`.

The repository must not derive, substitute, minimize, maximize, truncate below
microsecond precision, or use transaction time as either semantic timestamp.

## Source provenance

Reuse the existing durable source fields unchanged.

For `MARKETPLACE` and `ERP`:

```text
external reference present
absence reason null
```

For `MANUAL` and `CALCULATED`, either:

```text
external reference present
absence reason null
```

or:

```text
external reference null
absence reason INTERNAL_ORIGIN
```

No external reference may be invented.

## Write path

The repository must add a dedicated OrderOccurrence persistence branch.

A successful OrderOccurrence fact apply must atomically persist:

```text
subject version transition
update journal row
identifier row
parent fact row
OrderOccurrence subtype row
```

with:

```text
change_kind = FACT
identifier_kind = FACT
fact_kind = ORDER_OCCURRENCE
family = MARKETPLACE_ORDER
```

The temporary TASK-0147 fail-closed unsupported-OrderOccurrence branch must be
removed only for the now-supported durable OrderOccurrence path.

Unknown future fact kinds must continue to fail closed.

## Correction path

A correction whose replacement is OrderOccurrence must atomically persist:

```text
subject version transition
CORRECTION update row
correction identifier
replacement fact identifier
replacement parent fact
replacement OrderOccurrence subtype
correction row
```

The superseded fact remains immutable and historical.

No in-place update or delete of historical fact payload is authorized.

## Rollback

Any failure in a fact or correction transaction must leave no committed:

- subject version increment;
- update row;
- identifier row;
- parent fact row;
- subtype row;
- correction row;
- visible change_sequence.

PostgreSQL sequence gaps caused by rollback are acceptable and are not committed
evidence changes.

## Read path

Repository reconstruction must recognize:

```text
fact_kind = ORDER_OCCURRENCE
family = MARKETPLACE_ORDER
```

and rebuild exactly:

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

No field may be sourced from update timestamps, another fact subtype, or a
provider payload.

## Integrity failures

Reload must fail closed through the existing sanitized integrity surface when
durable OrderOccurrence state is malformed.

At minimum:

- parent without subtype;
- malformed source shape;
- invalid family;
- invalid fact kind;
- invalid parent/subtype structural link;
- unsupported durable fact kind;
- domain reconstruction failure.

Malformed durable state must never be silently skipped or converted to absence.

## Restart equivalence

Tests must prove:

```text
persist(A)
restart
reload()
```

reconstructs semantically equivalent evidence.

After restart:

- activeFacts remains equivalent;
- historicalFacts remains equivalent;
- duplicate classification remains equivalent;
- source-fact conflict classification remains equivalent;
- correction eligibility remains equivalent;
- assembler occurrence resolution remains equivalent.

## Duplicate semantics

For the accepted provider source-fact identity:

```text
source.kind
+ source.systemKey
+ source.externalReference
+ ORDER_OCCURRENCE
```

same identity + same occurredAt + same semantic provenance remains Duplicate.

Duplicate must not commit a new evidence version or changed-subject event.

## Conflict semantics

Same canonical provider source-fact identity with different occurredAt remains a
conflict requiring explicit correction.

The repository must not implicitly replace the active occurrence.

Conflict must not commit a new evidence version.

## Change-feed compatibility

OrderOccurrence must use only the existing:

```text
marketplace_economic_evidence_update
organization-scoped change_sequence
MarketplaceEconomicEvidenceChangeFeed
```

path.

No new cursor, checkpoint, receipt, event, outbox, or projection stream is
authorized.

## Existing-data compatibility

Existing evidence subjects without OrderOccurrence remain valid.

After V017:

```text
existing subject with no OrderOccurrence
  -> reload succeeds
  -> no active OrderOccurrence
  -> assembler remains ORDER_OCCURRED_AT_UNRESOLVED
```

No migration-time readiness may be fabricated.

## Tests

The PostgreSQL/Testcontainers suite must prove at minimum:

### Migration
- V001-V017 migrate successfully;
- V015 and V016 remain unchanged;
- existing evidence survives;
- no historical OrderOccurrence backfill occurs.

### Structural persistence
- MARKETPLACE round-trip;
- ERP round-trip;
- MANUAL internal-origin round-trip;
- CALCULATED internal-origin round-trip;
- occurredAt and observedAt remain distinct;
- whole-microsecond precision survives round-trip.

### Transactionality
- successful fact apply commits one evidence version;
- failed fact apply rolls back completely;
- successful correction commits atomically;
- failed correction rolls back completely;
- failed operations expose no committed change_sequence.

### Restart
- fact round-trip across repository/process recreation;
- duplicate after restart;
- conflict after restart;
- correction after restart;
- superseded occurrence remains historical;
- replacement occurrence remains active.

### Integrity
- malformed durable OrderOccurrence state fails closed;
- missing subtype fails closed;
- malformed source shape fails closed;
- unsupported fact kind remains fail closed.

### Change feed
- committed fact appears through existing changed-subject feed;
- committed correction appears through existing changed-subject feed;
- duplicate/conflict without commit produces no downstream change.

### Assembly compatibility
- no durable occurrence -> ORDER_OCCURRED_AT_UNRESOLVED;
- one durable active occurrence -> assembler uses exact occurredAt;
- multiple distinct active occurrences -> ORDER_OCCURRED_AT_CONFLICT;
- corrected historical occurrence does not re-enter assembly.

## Verification

Implementation evidence must include:

- focused PostgreSQL/Testcontainers tests;
- full persistence module test gate;
- repository CI gate;
- `git diff --check`;
- mechanically enumerated changed paths;
- proof that exactly five paths changed;
- proof that V015 and V016 are byte-unchanged;
- proof that no provider path changed;
- proof that TASK-0146 remained untouched.

## Implementation evidence - 2026-09-06

Local implementation verification completed on branch
`feat/task-0148-durable-order-occurrence-persistence`.

Implemented within the authorized persistence boundary:

- additive migration
  `V017__add_order_occurrence_to_independent_marketplace_economic_evidence.sql`;
- parent FACT discriminator support for `ORDER_OCCURRENCE`;
- dedicated durable subtype
  `marketplace_economic_evidence_order_occurrence_fact`;
- exact reconstruction of `occurredAt`, parent `observedAt`, and canonical source
  provenance on repository reload;
- atomic OrderOccurrence FACT writes through the existing subject version,
  update journal, identifier, parent fact, and subtype transaction;
- atomic correction replacement through the existing CORRECTION transaction;
- restart-equivalent duplicate and source-fact conflict behavior;
- append-only protection for the new subtype;
- fail-closed read behavior when the durable subtype is missing or malformed;
- unchanged existing change-journal/change-sequence authority.

Focused acceptance coverage added to
`PostgresMarketplaceIndependentEconomicEvidenceRepositoryTest.kt` includes:

- V017 schema and discriminator validation;
- `timestamptz(6)` occurrence precision;
- exact occurred-at versus observed-at round trip;
- MARKETPLACE, ERP, MANUAL, and CALCULATED source shapes;
- repository restart reconstruction;
- duplicate after restart;
- source-fact conflict after restart;
- correction after restart with historical fact retention and one active
  replacement;
- malformed subtype fail-closed behavior;
- append-only trigger coverage through the structural table set.

Local gates passed:

```text
.\gradlew.bat :applications:marketplace-operations-persistence-postgres:compileKotlin
BUILD SUCCESSFUL

.\gradlew.bat :applications:marketplace-operations-persistence-postgres:compileTestKotlin
BUILD SUCCESSFUL

.\gradlew.bat :applications:marketplace-operations-persistence-postgres:test
BUILD SUCCESSFUL

.\gradlew.bat :applications:marketplace-operations-persistence-postgres:test `
  --tests "io.flooow.marketplace.persistence.postgres.PostgresMarketplaceIndependentEconomicEvidenceRepositoryTest"
BUILD SUCCESSFUL
```

`git diff --check` reported no whitespace errors. The Windows working tree
reported only LF-to-CRLF normalization warnings for the modified Kotlin files.

At this checkpoint exactly the five TASK-0148-authorized paths are intended to
be changed. V015 and V016 remain immutable; no provider path, TASK-0146,
assembler semantic, calculator semantic, new feed, new repository abstraction,
or backfill path is authorized or required.

The TASK-0148 completion gate remains open until repository CI is green, the
implementation PR is clean, exactly-five-path verification is confirmed in the
final diff, and the PR is merged.
## Explicitly out of scope

TASK-0148 does not authorize:

- Mercado Livre ingestion;
- Omie ingestion;
- provider mapping;
- API/UI;
- Sales Intelligence implementation;
- TASK-0146 resumption;
- Economic Truth Assembly semantic changes;
- calculator changes;
- component changes;
- external-identity semantic changes;
- new evidence families;
- new component types;
- backfill;
- timestamp inference;
- MGI;
- VOI;
- Bayesian belief;
- opportunity intelligence;
- Hypothesis Ledger;
- new repository abstraction;
- new Gradle module;
- new change-feed semantics.

## Stop conditions

Stop implementation and return to governance if any of the following becomes
necessary:

1. a sixth changed path;
2. a second migration;
3. modification of V015 or V016;
4. a new repository abstraction or method outside the existing durable boundary;
5. a new evidence family;
6. a new EconomicComponentType;
7. provider-specific persistence;
8. backfill or inferred occurrence time;
9. a new change-feed/checkpoint/receipt/outbox path;
10. weakening append-only history;
11. silently skipping malformed durable OrderOccurrence state;
12. redefining duplicate/conflict identity in SQL;
13. resuming TASK-0146;
14. changing assembler or calculator semantics.

## Completion gate

TASK-0148 is complete only when:

- V017 is implemented;
- OrderOccurrence fact persistence is atomic;
- OrderOccurrence correction replacement is atomic;
- restart equivalence is proven;
- duplicate/conflict equivalence is proven;
- malformed state fails closed;
- existing change feed remains authoritative;
- all authorized tests pass;
- CI is green;
- exactly five authorized paths changed;
- PR review is clean;
- implementation PR is merged.

Only after this gate is fully closed may governance reconsider resuming
TASK-0146.
