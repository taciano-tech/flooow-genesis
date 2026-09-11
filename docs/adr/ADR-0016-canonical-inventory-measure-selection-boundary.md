# ADR-0016: Canonical Inventory Measure Selection Boundary

Status: Proposed

Date: 2026-08-12

## Context

V008 preserves five independent exact canonical measures from one immutable
source record: available to sell, on hand, reserved, pending inbound, and
pending outbound. V010 explicitly accepts one observation inside one exact
source-mapping lineage.

An accepted observation is still not a quantity that reconciliation or business
inventory can consume. The five fields describe different facts. A provider may
supply only some of them, may permit negative or decimal values, and may define
an availability field using operational rules that cannot be reconstructed from
the other fields.

Choosing a field implicitly in a consumer would erase a material decision. It
would also tempt consumers to use provider labels as universal semantics, derive
`on hand - reserved`, treat missing as zero, or silently fall back to another
field. Each behavior would change the meaning of the evidence.

Cross-source reconciliation is still premature. Before two accepted source
heads can be compared, each lineage needs a smaller reviewed policy:

> for this exact source-mapping lineage, this one canonical measure is the only
> field eligible to become a later reconciliation candidate.

## Decision

Introduce a production-inactive Canonical Inventory Measure Selection Ledger.

Selection is organization-owned, explicit, revisioned, and auditable. It chooses
one field name for one V007 mapping lineage. It does not copy or mutate a
quantity. A controlled resolver combines the active V010 accepted head with the
active selection and reads the selected exact rational from the referenced V008
observation.

```text
V008 exact independent measures
  + V010 accepted head for one exact lineage
  + V011 explicit measure selection for that lineage
  -> one provenance-preserving selected source candidate
  -> later authority, staleness, reconciliation, aggregation, and business stock
```

The controlled measure vocabulary is:

```text
AVAILABLE_TO_SELL
ON_HAND
RESERVED
PENDING_INBOUND
PENDING_OUTBOUND
```

`AVAILABLE_TO_SELL` preserves the provider-originating measure label. Selecting
it does not assert that the quantity is globally authoritative, fresh, legally
sellable, safe to publish, or appropriate for every channel.

## Exact resolution

Resolution succeeds only when:

- the organization, lineage, active acceptance, selected measure decision, and
  referenced observation agree exactly;
- the accepted observation remains the observation named by V010;
- the selected field is present in V008;
- copied target and provenance fields agree;
- no lifecycle or integrity ambiguity exists.

The result retains the exact signed rational. There is no decimal division,
rounding, truncation, absolute-value conversion, non-negative clamp, or whole
unit conversion. Present zero remains present zero. A missing selected field is
`MeasureUnavailable`; Genesis does not substitute zero or another measure.

## Policy revisions

One mapping lineage has at most one active measure selection. The first revision
uses `INITIAL_SELECTION`. A replacement retires the current revision, appends a
separate retirement audit, increments exactly one revision, and uses a controlled
reason:

- `SOURCE_SEMANTICS_CORRECTION`;
- `OPERATOR_CORRECTION`.

Replacement uses expected selection ID and revision. An identical requested
measure is an idempotent replay. A selection may be withdrawn with
`SOURCE_SEMANTICS_REVOKED` or `OPERATOR_WITHDRAWAL`. Withdrawal leaves no active
selection and never falls back to an older revision.

A new selection is anchored by the active V010 acceptance and requires the
selected field to be present in that accepted V008 observation. Later accepted
evidence may omit the selected field; in that case resolution fails closed until
an explicit selection replacement or new eligible evidence exists. The policy
does not silently change because a source omitted data.

## Lifecycle and authority

Writes require an active organization, a same-organization `ACTIVE` or
`SUSPENDED` connection, an active V010 acceptance for the exact lineage, the
active V007 leaf, and active target identities. Suspension permits deliberate
offline review without opening credentials or contacting a provider.

Selection requires a trusted internal principal and controlled reason. Provider
payloads, public callers, source values, and credentials cannot name the
principal or select the field.

Historical decision and resolution provenance remain readable after later
lifecycle retirement. New writes fail closed for draft, revoked, unknown,
foreign, divergent, or withdrawn scope.

## Concurrency

Initial selection, replacement, and withdrawal lock the lineage root and active
selection. Expected selection ID and revision provide compare-and-set fencing.
Two competing changes produce one applied revision and one controlled conflict.
Timestamps never choose the winner.

## Consequences

### Positive

- measure semantics become explicit rather than hidden in consumers;
- exact quantities and null semantics remain unchanged;
- every resolved candidate cites acceptance, observation, mapping, target, and
  selection policy revisions;
- later reconciliation can compare controlled candidates without guessing which
  source field was intended;
- measure corrections append history instead of rewriting past decisions.

### Negative

- no selected candidate exists until a trusted selection is recorded;
- a missing selected field fails closed instead of producing a convenient value;
- no cross-source authority, freshness, aggregation, or business availability
  exists yet;
- policy and accepted evidence must both be present to resolve a candidate.

## Alternatives considered

### Always use `availableToSell`

Rejected because the field is optional, provider-defined, and not universal.
Its name does not establish authority across ERP, marketplace, fulfillment, or
warehouse sources.

### Derive `onHand - reserved`

Rejected because pending movements, fulfillment ownership, safety stock, holds,
and provider-specific rules may participate in availability. Genesis does not
invent a universal formula.

### Use the first non-null field

Rejected because field order is not business meaning and a fallback would make
the same policy change meaning when payload shape changes.

### Store one mutable policy row

Rejected because past reconciliation and decisions would no longer be
reproducible after a selection correction.

### Reconcile sources in the same boundary

Rejected because source authority, location ownership, freshness, duplication,
and conflict behavior are separate policy decisions.

## Authorization

This ADR alone authorizes no implementation. SPEC-0016 freezes TASK-0082's pure
measure-selection contracts, additive V011 ledger, transactional repository,
exact resolver, history, withdrawal, and deterministic tests. It authorizes no
provider adapter, public endpoint, automatic selector, formula, fallback,
source ranking, staleness threshold, cross-source reconciliation, aggregation,
rounding, business availability, inventory mutation, assessment, event, worker,
scheduler, or external request.
