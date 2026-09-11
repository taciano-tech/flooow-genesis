# ADR-0027: Marketplace Competitive Market Reference Position Boundary

Status: Proposed

Date: 2026-08-14

## Context

Marketplace Intelligence now retains two complementary competitor facts:

```text
own price position against the lowest accepted competitor
seller-balanced lower and upper median market-reference prices
```

It still cannot answer the next narrow question:

> Is the observed own price below, inside, or above the accepted market
> reference band?

Unsafe shortcuts would be to compare inputs from different evidence cohorts,
use tolerance or rounding without policy, collapse an even median band into an
invented midpoint, call above-market wrong, or turn a descriptive position
into a price recommendation.

## Decision

Introduce a pure, production-inactive Competitive Market Reference Position
projection in the existing Marketplace pricing package.

It consumes:

```text
one accepted CompetitivePricePositionAssessment
one accepted CompetitiveMarketReferenceAssessment
```

It returns an exact diagnostic position and signed gaps. It performs no I/O,
recommendation, decision, or action.

## Reproducible source lineage

Matching organization, scenario, marketplace, currency, quantum, own
observation, policy, and evaluation time is necessary but not sufficient. Two
different competitor cohorts could share those values.

The projection reconstructs the reference policy retained by the supplied
market assessment, re-evaluates `MarketplaceCompetitiveMarketReference` from
the supplied competitive assessment, and requires the reproduced reference to
be value-equal to the supplied reference.

Any insufficiency or inequality returns typed `SourceAssessmentMismatch`. The
projection does not partially compare or explain which sensitive field
diverged.

This creates exact lineage without introducing a digest, persistence identity,
or generic evidence primitive.

## Exact position

For own observed gross price `own`, lower median `lower`, and upper median
`upper`:

```text
own < lower                 -> BELOW_REFERENCE_BAND
lower <= own <= upper       -> WITHIN_REFERENCE_BAND
own > upper                 -> ABOVE_REFERENCE_BAND
```

The projection retains:

```text
gap to lower = own - lower
gap to upper = own - upper
```

Comparison uses exact money values. No tolerance, percentage, midpoint,
interpolation, or rounding is applied.

For an odd seller cohort, the band has equal limits. Equality remains
`WITHIN_REFERENCE_BAND`.

## Quality separation

The result retains independently:

- own economic-price quality;
- market-reference evidence quality;
- combined diagnostic quality.

Combined quality is confirmed only when both source qualities are confirmed.
This is intentionally conservative because the own price reached this boundary
through Economic Price Position.

Quality is not market completeness, model confidence, decision confidence, or
authority.

## No recommendation

The result does not say:

- raise, lower, hold, match, undercut, test, or publish a price;
- that below-reference is economically unsafe;
- that above-reference is commercially wrong;
- that within-reference is optimal;
- which business objective should govern an action;
- what demand, conversion, inventory, Ads, margin, Buy Box, or contribution
  will do.

The diagnostic remains independent from replacement cost, inventory,
promotion, Ads, economic objective, policy, simulation, authority, and outcome.

## No infrastructure activation

This boundary adds no migration, repository, API, connector, event, worker,
UI, alert, experiment, price mutation, rollback, AI, model, agent, or Kernel
change.

## Consequences

### Positive

- own price gains an exact position against the seller-balanced market band;
- source-cohort substitution fails closed through full reproduction;
- even reference bands remain intact;
- signed gaps stay quantum-aligned and explainable;
- economic, lowest-competitor, and reference-band positions remain separate;
- no descriptive classification gains execution authority.

### Negative

- evaluation repeats the pure market-reference calculation;
- combined quality remains estimated when own economics is estimated even if
  competitor evidence is confirmed;
- the result still says nothing about optimal price or causal impact;
- no percentage index, severity, recommendation, or action is produced.

## Alternatives considered

### Compare only matching organization and currency

Rejected because a reference from another competitor cohort could pass.

### Add a cohort digest now

Rejected because the pure source assessment is available for exact
reproduction. Digest canonicalization belongs with later persistence/history.

### Use the midpoint of an even median band

Rejected because it may create an unobserved price and require rounding.

### Produce a recommended price

Rejected because market position has no replacement cost, inventory, demand,
objective, policy, simulation, or authority evidence.

### Add market position to the Kernel

Rejected because the vocabulary remains specific to Marketplace Intelligence.

## Authorization

This ADR alone authorizes no implementation. SPEC-0027 may authorize only the
pure source-reproduction check, exact reference-band position, signed gaps,
quality propagation, controlled result, and focused tests for TASK-0106.

It authorizes no persistence, API, recommendation, decision, price action, AI,
or Kernel modification.
