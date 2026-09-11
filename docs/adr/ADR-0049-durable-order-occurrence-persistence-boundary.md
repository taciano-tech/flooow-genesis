# ADR-0049: Durable OrderOccurrence Persistence Boundary

Status: Accepted

Date: 2026-09-05

## Context

TASK-0147 introduced the canonical OrderOccurrence evidence semantic and the
Canonical Economic Truth Assembly boundary.

The accepted runtime path is now:

    MarketplaceIndependentEconomicEvidence
      -> activeFacts
      -> MarketplaceEconomicTruthAssembler
      -> MarketplaceOrder
      -> MarketplaceEconomicTruthCalculator

OrderOccurrence is now required as explicit semantic authority for
MarketplaceOrder.occurredAt.

TASK-0147 deliberately did not authorize durable PostgreSQL persistence for
OrderOccurrence. The current PostgreSQL repository therefore fails closed when
an OrderOccurrence fact, or a correction whose replacement is OrderOccurrence,
is submitted.

That temporary compatibility boundary is correct but prevents real durable
production evidence from supplying canonical order occurrence after restart.

The durable evidence subsystem already provides:

- organization/order scoped evidence subjects;
- monotonic evidence versions;
- append-only update journal;
- globally ordered organization change_sequence;
- fact identifiers;
- parent fact records;
- subtype-specific fact tables;
- corrections and supersession;
- transactional persistence;
- reconstruction of canonical evidence;
- incremental downstream change feed.

OrderOccurrence must become durable without introducing a parallel source of
truth or bypassing those invariants.

## Decision

Persist OrderOccurrence as a first-class fact subtype inside the existing
durable MarketplaceIndependentEconomicEvidence model.

Conceptually:

    marketplace_economic_evidence_update
      -> marketplace_economic_evidence_identifier
      -> marketplace_economic_evidence_fact
           fact_kind = ORDER_OCCURRENCE
           family = MARKETPLACE_ORDER
      -> marketplace_economic_evidence_order_occurrence_fact

The persistence boundary remains the existing
PostgresMarketplaceIndependentEconomicEvidenceRepository.

No separate order-time repository, mutable subject field, shadow table,
projection-owned timestamp, or provider-owned persistence path is permitted.

## Fact discriminator

The durable parent fact discriminator gains exactly one semantic value:

    ORDER_OCCURRENCE

ORDER_OCCURRENCE represents
MarketplaceIndependentEconomicFact.OrderOccurrence only.

It must not become:

- an EconomicComponentType;
- an ExternalIdentity kind;
- a collection-attempt type;
- a correction type;
- a projection field with independent authority.

The durable family is structurally:

    MARKETPLACE_ORDER

No caller-selectable alternate family is permitted.

## Physical subtype

OrderOccurrence receives a dedicated subtype relation associated with the
existing marketplace_economic_evidence_fact parent row.

The subtype must retain exactly the canonical semantic payload required to
reconstruct MarketplaceEconomicOrderOccurrenceObservation:

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

The exact SQL names and constraint definitions belong to the subsequent SPEC.

The durable representation must not reuse:

- marketplace_economic_evidence_component_fact;
- marketplace_economic_evidence_external_identity_fact;
- another fact's occurred_at column;
- subject timestamps;
- update committed_at;
- collection attempt timestamps.

## Timestamp semantics

Canonical OrderOccurrence.occurredAt is persisted directly.

Its physical representation must preserve the accepted whole-microsecond
precision.

The durable database must not:

- derive it from another fact;
- truncate below accepted precision;
- replace it with transaction time;
- replace it with observedAt;
- choose earliest or latest component time;
- synthesize a value during read or migration.

Existing durable rows receive no inferred or fabricated occurrence timestamp.

## Observation time

The existing parent fact observed_at remains the durable representation of the
OrderOccurrence observation's observedAt.

The subtype occurred_at and parent observed_at retain their separate meanings.

Accepted MANUAL/CALCULATED source-clock validation remains domain authority and
must not be weakened by persistence.

## Source provenance

The durable subtype preserves EconomicSource exactly using the existing durable
source vocabulary:

    source_kind
    source_system_key
    source_external_reference
    source_external_reference_absence_reason

Persistence must not manufacture provider references.

For provider-originated MARKETPLACE and ERP observations, the accepted external
reference semantics remain authoritative.

For MANUAL/CALCULATED observations, persistence preserves the accepted internal
origin/reference shape without inventing provider identity.

## Journal and version authority

OrderOccurrence uses the existing durable update pipeline.

An accepted OrderOccurrence observation advances exactly one evidence version
and emits exactly one existing FACT update journal entry.

No new sequence, journal, checkpoint, receipt table, or parallel event stream is
introduced.

The existing organization change_sequence remains the downstream incremental
change authority.

Therefore the existing MarketplaceEconomicEvidenceChangeFeed contract does not
gain alternate ordering semantics.

## Atomic write semantics

Persisting an OrderOccurrence fact must be atomic with the existing evidence
update transaction.

A successful observation commits together:

    subject/version transition
    update journal row
    identifier row
    parent fact row
    OrderOccurrence subtype row

A successful correction whose replacement is OrderOccurrence commits together:

    subject/version transition
    correction update journal row
    correction identifier row
    replacement fact identifier row
    replacement parent fact row
    replacement OrderOccurrence subtype row
    correction row

Any failure rolls back the complete evidence update.

Partial OrderOccurrence persistence is forbidden.

change_sequence must not become visible for a rolled-back update.

## Correction semantics

Existing MarketplaceEconomicEvidenceCorrection semantics remain unchanged.

A correction may replace a superseded fact with OrderOccurrence when the
replacement is otherwise valid under the canonical evidence domain contract.

After durable reload:

    old fact
      -> historicalFacts

    correction
      -> explicit supersession

    replacement OrderOccurrence
      -> activeFacts

Superseded OrderOccurrence facts remain durable audit evidence and never
re-enter activeFacts.

## Duplicate and conflict semantics

Persistence must preserve the canonical source-fact identity and duplicate /
conflict behavior already accepted for OrderOccurrence.

Database representation must not create a second definition of semantic
identity.

After restart/reconstruction, applying semantically equivalent evidence must
produce the same Duplicate / SourceFactConflict outcomes as the in-memory
aggregate.

Database uniqueness constraints may strengthen structural integrity but must
not silently redefine canonical domain meaning.

## Reconstruction

The existing PostgreSQL repository becomes capable of loading
ORDER_OCCURRENCE parent facts and reconstructing exactly:

    MarketplaceIndependentEconomicFact.OrderOccurrence(
      MarketplaceEconomicOrderOccurrenceObservation(...)
    )

Reconstruction must preserve:

- observation id;
- subject;
- source provenance;
- occurredAt;
- observedAt;
- evidence version/history relationship.

No field may be reconstructed from unrelated rows.

Malformed durable OrderOccurrence state must fail closed through a sanitized
persistence/integrity surface.

## Migration policy

V015 is historical and immutable.

OrderOccurrence durable support must be introduced through a new additive
migration.

The subsequent SPEC may assign the next available migration number and freeze
the concrete SQL representation.

The migration must not:

- edit or reinterpret V015 history;
- backfill fabricated OrderOccurrence facts;
- convert Component or ExternalIdentity rows;
- derive occurredAt for existing orders;
- mutate existing evidence versions;
- emit synthetic change_sequence values for historical subjects.

Existing subjects without OrderOccurrence remain valid durable evidence and
their canonical assembly remains NotReady until authoritative evidence is
actually observed.

## Existing data compatibility

All pre-migration durable evidence retains its existing meaning.

Absence of a durable OrderOccurrence row means exactly absence of authoritative
OrderOccurrence evidence.

It does not mean:

- zero time;
- earliest known time;
- provider order creation time;
- payment time;
- revenue time;
- NOT_APPLICABLE;
- migration failure.

No historical backfill is authorized by this ADR.

## Change-feed compatibility

The incremental change feed remains based on existing durable evidence updates
and organization change_sequence.

Once OrderOccurrence becomes a supported FACT update, downstream consumers see
the corresponding changed evidence subject through the existing feed.

No OrderOccurrence-specific checkpoint or cursor is introduced.

The change feed does not interpret OrderOccurrence semantics; it only exposes
changed canonical evidence subjects as already designed.

## Relationship to Economic Truth Assembly

After this boundary is implemented:

    durable MarketplaceIndependentEconomicEvidence
      -> reload
      -> activeFacts
      -> MarketplaceEconomicTruthAssembler
      -> Ready / NotReady

The assembler itself remains pure and unchanged in authority.

Persistence does not decide:

- which active occurrence wins;
- how conflicts are adjudicated;
- coverage;
- applicability;
- economic completeness.

Those remain canonical domain/assembly responsibilities.

## Relationship to Sales Intelligence

TASK-0146 remains paused while this persistence boundary is only Proposed or
specified but not implemented and accepted.

Sales Intelligence must not infer or persist an alternate order occurrence
timestamp.

The intended downstream chain remains:

    durable independent economic evidence
      -> incremental change feed
      -> canonical Economic Truth Assembly
      -> MarketplaceEconomicTruthCalculator
      -> durable Sales Intelligence projection

## Failure model

The current TASK-0147 fail-closed rejection is temporary compatibility behavior.

It remains correct until durable support is implemented.

The later implementation may remove that temporary rejection only when all
write, read, correction, replay, rollback, and reconstruction acceptance tests
for OrderOccurrence are present and passing.

Unknown or malformed durable fact kinds must continue to fail closed.

## Out of scope

This ADR does not authorize:

- production Kotlin implementation;
- SQL migration implementation;
- final table or constraint names;
- provider adapters;
- Mercado Livre mapping changes;
- Omie mapping changes;
- historical timestamp backfill;
- Sales Intelligence implementation;
- TASK-0146 resumption;
- API/UI;
- scheduler/fairness behavior;
- MGI opportunity intelligence;
- VOI;
- Bayesian belief;
- Hypothesis Ledger;
- new evidence families;
- new EconomicComponentType values;
- new assembly-policy semantics.

## Alternatives considered

### Store occurredAt directly on evidence subject - rejected

Order occurrence is independently observed evidence. Making it mutable subject
state would bypass append-only fact history and correction semantics.

### Reuse Component occurred_at - rejected

A financial component timestamp is not authoritative marketplace-order
occurrence time.

### Reuse ExternalIdentity occurred_at - rejected

Identity linkage and marketplace-order occurrence are different semantics.

### Create a separate order-time repository - rejected

That would introduce a second source of truth outside the canonical evidence
version, correction, replay, and change-feed model.

### Store OrderOccurrence only in projection state - rejected

Projection state is derivative and rebuildable. It cannot become economic
evidence authority.

### Backfill historical orders from convenient timestamps - rejected

Deterministic inference does not create semantic authority.

## Consequences

Positive consequences:

- OrderOccurrence survives process restart and replay;
- canonical assembly can operate on real durable evidence;
- no second evidence store is introduced;
- existing correction semantics remain authoritative;
- existing change_sequence remains the incremental ordering authority;
- historical evidence keeps its original meaning;
- Sales Intelligence receives a stable upstream truth boundary.

Costs and limitations:

- one additive migration is required;
- repository read/write mapping must support a third fact subtype;
- PostgreSQL acceptance tests must cover atomicity and restart behavior;
- historical orders remain NotReady until real authoritative occurrence
  evidence is collected;
- provider ingestion remains separate future work.

## Evidence and references

- ADR-0042, Independent Marketplace Economic Evidence Boundary;
- ADR-0043, Durable Independent Marketplace Economic Evidence Boundary;
- ADR-0046, Durable Marketplace Economic Evidence Incremental Change Feed;
- ADR-0048, Canonical Economic Truth Assembly Semantics;
- SPEC-0042, durable independent economic evidence;
- SPEC-0047, canonical Economic Truth Assembly;
- TASK-0147, canonical Economic Truth Assembly;
- PR #156 / merge commit 8038b299a97ae20069ae77cb7f8dd55491dbb450.

## Authorization

If accepted after review, this ADR authorizes drafting one bounded SPEC for
durable OrderOccurrence persistence only.

That SPEC may freeze:

- the next additive migration;
- ORDER_OCCURRENCE physical discriminator;
- subtype table shape;
- source provenance encoding;
- timestamp encoding;
- write/read/reconstruction behavior;
- correction replacement behavior;
- transaction and rollback invariants;
- restart/replay acceptance evidence.

This ADR does not itself authorize implementation, migration execution, provider
work, Sales Intelligence implementation, API/UI work, or TASK-0146 resumption.
