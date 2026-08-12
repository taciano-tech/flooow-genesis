# ADR-0015: Canonical Inventory Source Acceptance Boundary

Status: Proposed

Date: 2026-08-12

## Context

V006 preserves immutable inventory evidence in connector commit order. V007
records an organization's reviewed interpretation of one exact source selector.
V008 and V009 apply that mapping to each matching source record and preserve an
exact canonical observation without rounding or aggregation.

The resulting ledger deliberately has no current row. Multiple observations may
exist for one source mapping lineage because:

- later connector pages contain later readings for the same exact selector;
- a mapping correction may reinterpret one source record without rewriting it;
- an older observation must remain reproducible after a successor exists;
- ingestion order, provider time, projection time, and business validity are
  different facts.

Selecting the row with the greatest timestamp would be unsafe. Provider time is
nullable and provider-controlled. Projection time describes Genesis processing,
not source succession. Mercado Livre's `x-version` is optimistic concurrency
state for its stock resource, not cross-provider business authority. Omie stock
position is requested for a product, date, and stock location and exposes no
universal sequence comparable with another source.

Selecting one row across connections would also be premature. ERP, marketplace,
fulfillment, operator, and warehouse systems may each own different locations or
measures. A valid credential proves protocol access, not authority over business
inventory.

Genesis therefore needs a smaller decision before reconciliation:

> for this one exact source-mapping lineage, this exact immutable canonical
> observation is the currently accepted head.

## Decision

Introduce a production-inactive Canonical Inventory Source Acceptance Ledger.

Acceptance is organization-owned, explicit, revisioned, and auditable. It
selects at most one active V008 observation for one V007 mapping lineage. The
lineage is rooted at the initial mapping decision and retains the same exact
organization, connection, capability, and nullable source selector through all
mapping revisions.

```text
V006 committed source sequence
  + V007 exact mapping lineage
  -> V008/V009 immutable canonical observations
  -> V010 accepted head for that one lineage
  -> later source authority, reconciliation, aggregation, and business stock
```

Acceptance does not assert that the source is correct, fresh, globally
authoritative, sellable, or compatible with another source. It only records the
current accepted interpretation inside one source lineage.

## Succession rules

The accepted head advances by evidence succession, not wall-clock comparison.

For two candidates in the same lineage:

1. a greater `inputProgressVersion` is later committed source evidence;
2. the same source pointer with a greater projection revision is a later mapping
   reinterpretation;
3. an identical observation is an idempotent replay;
4. a lower progress version or lower projection revision is stale;
5. different ordinals in the same progress version are incomparable and cause a
   controlled conflict, because record order inside one page is not business
   time.

`sourceUpdatedAt`, `sourceCommittedAt`, `projectedAt`, mapping decision time, and
acceptance time remain recorded facts but do not override this succession. A
future provider-specific contract may validate monotonic provider versions or
timestamps, but TASK-0080 does not.

## Mapping reinterpretation

A later active mapping revision may reinterpret the exact same V006 record. Its
observation may replace the accepted head when it belongs to the same mapping
lineage and has a greater projection and mapping revision.

The old acceptance and old observation remain immutable. A target correction can
therefore be reproduced without pretending the earlier interpretation never
existed.

A mapping for a different exact selector, connection, organization, or lineage
cannot replace the head. Cross-selector consolidation requires a separate
reviewed identity or reconciliation decision.

## Authority and withdrawal

Acceptance requires a trusted internal principal reference and a controlled
reason. The source record, provider payload, request body, and public caller
cannot name that principal or self-authorize acceptance.

An active head may be explicitly withdrawn with compare-and-set fencing. A
withdrawal leaves no active accepted observation for the lineage and appends an
immutable audit record. It does not delete source, mapping, observation, or prior
acceptance history.

Acceptance administration permits an active organization and a same-organization
connection in `ACTIVE` or `SUSPENDED` state. Suspension supports offline review.
Draft, revoked, unknown, and foreign connections cannot receive a new acceptance.
Historical reads remain available after later lifecycle retirement.

## Concurrency

Initial acceptance, replacement, and withdrawal lock the lineage head and use
expected acceptance ID and revision. Two competing changes produce one accepted
revision and one controlled conflict. No last-write-wins timestamp or arbitrary
row ordering is allowed.

Every write validates organization, connection, mapping lineage, observation,
source position, mapping revision, target, expected head, principal, and reason
inside one PostgreSQL transaction. Any failure changes nothing.

## Consequences

### Positive

- later source readings can become current without deleting evidence;
- mapping corrections remain historically explainable;
- stale replay cannot regress the accepted head;
- provider clocks and concurrency tokens do not become accidental authority;
- source-lineage acceptance is isolated from cross-source reconciliation;
- a withdrawn lineage fails closed instead of silently falling back to old data.

### Negative

- no globally current quantity exists yet for a canonical item;
- acceptance requires an internal trusted workflow not provided in TASK-0080;
- equal-page duplicate selectors remain unresolved conflicts;
- no wall-clock freshness or service-level policy is applied;
- consumers must still reconcile multiple accepted source heads.

## Alternatives considered

### Use the greatest provider timestamp

Rejected because the timestamp is nullable, provider-controlled, may have coarse
precision, and is not comparable across systems.

### Use projection or commit time

Rejected as business succession. Those times describe Genesis processing and
ingestion, although connector progress remains the deterministic sequence within
one connection and capability.

### Treat Mercado Livre `x-version` as authority

Rejected because it fences writes to one Mercado Livre stock resource and does
not rank that resource against ERP, Full, operator, or warehouse evidence.

### Automatically choose one source per canonical target

Rejected because location ownership, measure semantics, staleness, and business
authority require an explicit reconciliation policy.

### Update one mutable current observation

Rejected because acceptance history, actor, reason, and the decision used by a
past consumer would no longer be reproducible.

## Authorization

This ADR alone authorizes no implementation. SPEC-0015 freezes TASK-0080's pure
acceptance contracts, additive V010 ledger, transactional repository, scoped
head and history reads, withdrawal, and deterministic tests. It authorizes no
provider adapter, public endpoint, automatic acceptance worker, source ranking,
staleness threshold, aggregation, measure selection, business availability,
inventory mutation, assessment, event, or external request.
