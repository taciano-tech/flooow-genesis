# ADR-0035: Marketplace Net-Back Cost-Basis Price Position Boundary

Status: Proposed

Date: 2026-08-15

## Context

TASK-0121 retains the source and selected-cost derived Net-Back floors under
one lineage and calculates their exact monetary deltas. It explains how the
floor changed, but not where an observed gross price sits against the selected
cost scenario.

The repository already has `MarketplaceEconomicPricePosition`, which is the
accepted authority for comparing one exact observed price with one complete
Net-Back floor. The next smallest useful question is:

> At the price observed for the derived scenario, is the selected-cost floor
> below break-even, below target, at target, or above target?

Reimplementing gaps or classification would create a second mathematical
authority. Evaluating both source and derived scenarios would require two
scenario-owned observations or a separate evidence-rebinding contract and is
not necessary to answer this first diagnostic question.

## Decision

Introduce a pure, production-inactive cost-basis price-position projection in
the existing Marketplace pricing package.

It accepts:

```text
one complete NetBackCostBasisFloorDelta
one exact ObservedMarketplacePrice owned by the derived scenario
```

It invokes exactly:

```text
MarketplaceEconomicPricePosition.evaluate(
  floorDelta.sourceScenarioFloor.appliedScenarioFloor.floor,
  observation
)
```

The projection maps the accepted evaluator result without changing any gap,
position, quality, or failure semantics.

## Complete assessment lineage

On success, the controlled aggregate retains:

```text
floorDelta
observation
assessment
```

This connects the observed-price evidence and its diagnostic assessment to:

- the selected Product Cost evidence;
- the derived pricing profile and floor;
- the original Product Cost evidence and source floor;
- the exact source-to-derived floor deltas.

Internal construction reproduces the existing evaluator result exactly. All
renderings are `[REDACTED]`.

## Existing evaluator remains sovereign

Ownership, scenario, currency, price-quantum alignment, exact gaps, position
precedence, and quality propagation remain owned by
`MarketplaceEconomicPricePosition`.

The projection adds no new formula, rounding, tolerance, position enum,
quality rule, observation identity, time, source, or policy.

## Controlled failures

The projection maps existing failures one for one:

```text
OwnershipMismatch
CurrencyMismatch
PriceQuantumMismatch
```

Failures retain no partial aggregate and disclose no values. They do not
repair, round, rebind, or substitute the observation.

## Derived scenario only

The observation must satisfy the existing evaluator's organization and
scenario ownership against the derived floor. The projection does not silently
reuse a source-scenario observation, rewrite its scenario, or evaluate the
source floor.

The retained source floor and exact deltas are context, not a second position
assessment. Applying one source fact to multiple scenarios requires its own
explicit evidence contract if later demonstrated necessary.

## No recommendation or execution

The result derives no:

- preferred Product Cost basis;
- suggested price or price change;
- market competitiveness;
- objective fitness or optimization;
- materiality or severity;
- recommendation, approval, authority, decision, or action.

An observed price above the derived economic floor is a diagnostic fact, not
proof of realized profitability or authorization to publish that price.

## No infrastructure activation

The projection adds no persistence, migration, API, serialization, event,
connector, worker, scheduler, UI, alert, experiment, agent, AI, external price
mutation, rollback, or Kernel change.

## Consequences

### Positive

- selected-cost floor viability becomes diagnosable with accepted semantics;
- source, derived, delta, observation, and assessment lineage remain intact;
- the existing price-position evaluator stays the only authority;
- controlled mismatches remain fail closed;
- no recommendation or execution authority leaks into calculation.

### Negative

- callers must provide a derived-scenario-owned price observation;
- the source floor receives no price-position assessment;
- no comparison of source and derived positions is produced;
- the result remains diagnostic and production inactive.

## Alternatives considered

Calling the existing evaluator directly was rejected because the assessment
would lose the Product Cost application and delta lineage. Reimplementing its
calculation was rejected because it creates competing semantics. Silently
changing the observation scenario was rejected because it mutates evidence.
Evaluating both floors with one observation was rejected because existing
ownership contracts make the ambiguity explicit. Adding a recommendation was
rejected because no economic objective, market evidence, policy, or authority
exists here. Kernel promotion was rejected because this remains Marketplace
pricing vocabulary.

## Authorization

This ADR alone authorizes no implementation. SPEC-0035 may authorize only the
pure derived-scenario price-position projection, exact result mapping,
lineage/invariant retention, redaction, and focused tests for TASK-0123.

No evidence rebinding, source-floor position, cross-scenario comparison,
objective, recommendation, authority, action, persistence, API, AI, or Kernel
modification is authorized.
