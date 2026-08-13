# ADR-0024: Marketplace Economic Price Position Boundary

Status: Proposed

Date: 2026-08-13

## Context

MKT-004 now calculates an absolute floor and a target-contribution economic
floor from complete normalized economics. A floor alone does not say where a
price currently observed in a marketplace sits relative to those boundaries.

The next safe Pricing Intelligence question is:

> Is one source-observed gross sale price below break-even, below the economic
> target, exactly at the target floor, or above it?

Unsafe shortcuts would be to:

- treat a current price without source provenance as trusted evidence;
- compare different organizations, scenarios, or currencies;
- compare an estimated floor as if it were confirmed;
- hide price precision or silently round an observed price;
- calculate a percentage gap when the accepted money contract provides exact
  monetary differences;
- call "above floor" profitable after quantity, returns, or actual settlement;
- convert a diagnostic position into a recommendation or price update;
- put listing, marketplace price, or economic-floor vocabulary in the Kernel.

## Decision

Introduce a pure, production-inactive Economic Price Position projection in the
existing Marketplace pricing package:

```text
io.flooow.marketplace.operations.economics.pricing
```

It consumes one complete `NetBackEconomicFloor` and one exact observed gross
price. It returns a deterministic diagnostic comparison and performs no I/O,
recommendation, decision, or action.

## Observed price evidence

One observation freezes:

```text
organization and scenario ownership
caller-supplied observation ID
non-negative exact gross price
source provenance
source occurrence time
CONFIRMED or ESTIMATED evidence quality
```

Marketplace and ERP source shape reuses MKT-001 and therefore requires a stable
external reference. The occurrence time is supplied source time with
microsecond precision. The projection reads no clock.

The observed price must use the floor currency and be an exact multiple of the
floor's positive price quantum. It is never rounded or normalized by the
projection. Zero is accepted as an explicit observed price and is not absence.

The observation deliberately contains no SKU, listing, seller, Buy Box,
competitor, inventory, promotion, Ads, credential, or API payload.

## Exact gaps

The projection derives:

```text
absolute gap = observed price - absolute floor
economic gap = observed price - economic floor
```

Both are exact signed `MarketplaceMoney`. A negative gap is a shortfall; zero
means exact equality; positive means the observed price is above the named
boundary. No percentage, ratio, rounding, or severity score is derived.

## Position classification

Classification follows this exact precedence:

```text
observed < absolute floor -> BELOW_ABSOLUTE_FLOOR
observed < economic floor -> BELOW_ECONOMIC_FLOOR
observed = economic floor -> AT_ECONOMIC_FLOOR
observed > economic floor -> ABOVE_ECONOMIC_FLOOR
```

When absolute and economic floors are equal, an equal observed price is
`AT_ECONOMIC_FLOOR`. When they differ, equality with the absolute floor remains
`BELOW_ECONOMIC_FLOOR` because break-even does not satisfy the contribution
target.

## Quality propagation

The assessment quality is `CONFIRMED` only when both:

- the Net-Back floor truth quality is confirmed;
- the observed-price evidence quality is confirmed.

Otherwise it is `ESTIMATED`. This is evidence quality, not model or decision
confidence. The projection does not block, approve, or authorize anything.

## Result and explanation

The assessment retains structured ownership, marketplace, currency, floor
policy versions, observation identity/source/time, exact prices, exact gaps,
position, and combined quality. Aggregate rendering is redacted.

Repeated value-equal inputs return value-equal output. No assessment ID or
assessment time is generated; persistence and historical position tracking are
later contracts.

## No recommendation or market intelligence yet

The result does not say:

- raise, lower, hold, or test a price;
- how much to change;
- whether a price is competitive or optimal;
- whether demand, conversion, margin, inventory, Buy Box, or Ads will improve;
- whether a floor profile is approved for execution;
- whether a marketplace action is authorized.

Those require market evidence, policies, simulation, authority, and outcomes.

## No infrastructure activation

This boundary adds no migration, repository, API, JSON, event, connector,
worker, scheduler, webhook, UI, alert, experiment, price mutation, rollback, AI,
model, expert, agent, or Kernel change.

## Consequences

### Positive

- a current price can be diagnosed against break-even and target floors without
  conflating the two;
- exact monetary shortfalls remain explainable;
- source provenance and occurrence time travel with the comparison;
- estimated evidence cannot masquerade as confirmed;
- later market and decision layers receive a controlled economic position;
- no execution authority leaks into calculation.

### Negative

- callers must normalize and quantum-validate observed price evidence;
- one observation says nothing about price history or competitors;
- above-floor does not prove realized profitability;
- the first output has no percentage gap, severity, recommendation, or action.

## Alternatives considered

### Return only above/below target

Rejected because a price below break-even is materially different from a price
above break-even but below the target.

### Calculate a recommended price immediately

Rejected because the floor contains no competitor, elasticity, inventory,
demand, or strategy evidence.

### Round an observed price to the quantum

Rejected because normalization would change source evidence and hide invalid
input.

### Persist current position on the floor

Rejected because observations change over time while the floor is an immutable
calculation result.

### Add price position to the Kernel

Rejected because this remains demonstrated only by Marketplace Intelligence.

## Authorization

This ADR alone authorizes no implementation. SPEC-0024 may authorize only the
pure observed-price evidence, assessment, evaluator, and focused tests for
TASK-0098. It authorizes no persistence, market comparison, recommendation,
decision, price action, AI, or Kernel modification.
