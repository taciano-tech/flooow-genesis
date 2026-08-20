# ADR-0036: Marketplace Net-Back Comparable Price Evidence Boundary

Status: Proposed

Date: 2026-08-20

## Context

TASK-0123 evaluates an exact observed gross price against the selected-cost
derived Net-Back floor while retaining the complete source-to-derived Product
Cost lineage. It deliberately does not evaluate the source floor because an
`ObservedMarketplacePrice` belongs to exactly one pricing scenario.

The repository must not silently copy that observation to another scenario.
Doing so would rewrite evidence and make two assessments appear comparable
without proving that they describe the same source fact.

The next smallest safe question is therefore:

> Can the caller explicitly provide the same observed-price fact under both
> scenario ownerships so the accepted evaluator can assess each floor without
> rebinding, inventing, or changing evidence?

This is an evidence and diagnostic boundary. It is not yet a judgment about
which Product Cost basis, floor, position, or price is preferable.

## Decision

Introduce a pure, production-inactive comparable-price evidence projection in
the existing Marketplace pricing package.

It accepts:

```text
one complete NetBackCostBasisFloorDelta
one source-scenario ObservedMarketplacePrice
one derived-scenario ObservedMarketplacePrice
```

The two observations must explicitly represent one source-observed fact. They
therefore retain equal:

```text
organization
observation identity
gross price
source provenance
source occurrence time
evidence quality
```

Only scenario ownership differs. The source observation belongs to the source
floor scenario and the derived observation belongs to the selected-cost
derived floor scenario.

The projection generates neither observation, changes neither scenario, and
does not copy one observation internally.

## Existing evaluator remains sovereign

After confirming that the two caller-supplied observations are the same source
fact, the projection invokes `MarketplaceEconomicPricePosition` independently:

```text
source floor  + source observation  -> source assessment
derived floor + derived observation -> derived assessment
```

Organization and scenario ownership, currency, price-quantum alignment, exact
gaps, position classification, floor policy lineage, and quality propagation
remain entirely owned by the accepted evaluator.

The new projection must not recreate any price-position formula or translate
either assessment.

## Successful aggregate

On success, one controlled aggregate retains:

```text
floor delta and complete Product Cost lineage
source observation
derived observation
source assessment
derived assessment
```

Internal construction reproduces both evaluator results exactly and proves
that both observations still satisfy the same-fact invariant. All renderings
are `[REDACTED]`.

The aggregate exposes two accepted diagnostic facts side by side. It derives
no transition, direction, rank, score, severity, percentage, or materiality.

## Controlled failures

The projection fails closed when:

- observations differ in any same-fact field;
- the source observation does not belong to the source floor;
- the derived observation does not belong to the derived floor;
- either currency differs from its floor;
- either gross price is not aligned to its floor quantum.

Source and derived evaluator failures remain distinguishable so callers know
which explicit evidence failed without receiving a partial assessment or any
sensitive value.

Failure precedence is deterministic: same-fact evidence is checked first,
then source evaluation, then derived evaluation. No fallback, repair, rounding,
or partial aggregate is permitted.

## No preference or recommendation

Two positions can differ solely because the accepted Product Cost evidence
changed the floor. That is useful diagnostic evidence, but it does not prove
that the selected cost is operationally available, strategically preferred,
market competitive, profitable after settlement, or authorized for pricing.

The projection derives no:

- source-to-derived position transition classification;
- preferred Product Cost basis;
- price increase, decrease, hold, or test instruction;
- percentage, materiality, feasibility, or objective fitness;
- recommendation, approval, authority, decision, or action.

Those require later explicit contracts and additional economic, market,
inventory, policy, simulation, and authority evidence.

## No infrastructure activation

The projection adds no persistence, migration, API, serialization, event,
connector, worker, scheduler, UI, alert, experiment, AI, agent, external price
mutation, rollback, or Kernel change.

## Consequences

### Positive

- cross-scenario assessment becomes explicit rather than silently rebound;
- one source fact can be traced through both scenario-owned observations;
- each floor is evaluated only by the existing price-position authority;
- source, selected-cost, floor-delta, observation, and assessment lineage stay
  auditable;
- controlled failures remain deterministic and fail closed;
- diagnostic evidence stays separate from preference and action.

### Negative

- callers must construct two explicit scenario-owned observations;
- exact same-fact equality is intentionally stricter than approximate matching;
- the aggregate does not say whether the resulting position change is useful;
- no preferred cost basis or recommended price is produced.

## Alternatives considered

Silently copying the derived observation to the source scenario was rejected
because it rewrites evidence. Evaluating both floors against one scenario-owned
observation was rejected because it violates existing ownership semantics.
Accepting unrelated observations was rejected because price or time changes
would confound the cost-basis comparison. Adding a transition classification
was rejected because the paired assessments are the prerequisite fact for that
later judgment. Adding a recommendation was rejected because no objective,
market, inventory, policy, or authority contract exists here. Kernel promotion
was rejected because this remains Marketplace pricing vocabulary.

## Authorization

This ADR alone authorizes no implementation. SPEC-0036 may authorize only the
pure comparable-price evidence projection, exact dual evaluator delegation,
controlled result mapping, invariant retention, redaction, and focused tests
for TASK-0127.

No observation generation or rebinding, position-transition classification,
percentage, materiality, preferred basis, recommendation, authority, action,
persistence, API, AI, or Kernel modification is authorized.
