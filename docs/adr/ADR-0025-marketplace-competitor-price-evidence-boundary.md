# ADR-0025: Marketplace Competitor Price Evidence Boundary

Status: Proposed

Date: 2026-08-14

## Context

MKT-004 can now calculate exact economic floors and diagnose one observed own
price against them. It still cannot answer the next independent question:

> Where does that own price sit relative to current, already-matched competitor
> offers in the same marketplace?

Unsafe shortcuts would be to:

- assume that two marketplace offers represent the same product;
- scrape, match, or select a competitor inside the price evaluator;
- compare observations from different organizations, scenarios, marketplaces,
  currencies, or price quanta;
- compare current own evidence with stale or future competitor evidence;
- trust a seller, offer, source reference, or occurrence time hidden in text;
- treat the lowest observed competitor as a recommended or optimal price;
- combine market position with economic safety, authority, or execution;
- put competitor, seller, offer, SKU, or marketplace pricing vocabulary in the
  Kernel.

## Decision

Introduce a pure, production-inactive Competitor Price Evidence projection in
the existing Marketplace pricing package:

```text
io.flooow.marketplace.operations.economics.pricing
```

It consumes one accepted `EconomicPricePositionAssessment`, one explicit
comparison policy, one caller-supplied evaluation time, and zero or more
available competitor-price observations whose product correspondence was
established upstream. It returns a deterministic diagnostic comparison and
performs no discovery, matching, I/O, recommendation, decision, or action.

The accepted economic assessment is extended additively to retain the positive
price quantum already validated by its Net-Back floor. This does not change its
classification; it carries the normalization fact needed by later price
evidence.

## Already-matched available offer evidence

One competitor observation freezes:

```text
organization and scenario ownership
marketplace
caller-supplied observation ID
caller-supplied product-match evidence ID
internal competitor seller key
non-negative exact gross price
source provenance
source occurrence time
price evidence quality
product-match evidence quality
```

The type represents an offer that the caller asserts was available at source
time. It does not infer availability from the presence of a price. Unavailable,
unknown, suppressed, historical, shipping-adjusted, coupon-adjusted, member,
installment, Buy Box, and landed-price observations require later contracts.

Product matching remains upstream. The match ID is evidence linkage, not proof
created by this projection. Estimated match evidence is accepted but makes the
comparison estimated.

Marketplace source provenance requires a stable external reference. Identity,
seller key, match ID, aggregate evidence, and results use safe rendering.

## Explicit temporal window

A versioned comparison policy supplies a positive maximum observation age.
The caller supplies `evaluatedAt` at microsecond precision. The projection
reads no clock.

Own and competitor source times must lie in the inclusive window:

```text
[evaluatedAt - maximumObservationAge, evaluatedAt]
```

Any own or competitor observation outside that window fails closed with a
typed result. The projection never silently drops stale or future evidence and
never substitutes processing time for source time.

## Exact comparison

All accepted competitor prices must share ownership, marketplace, currency,
and price quantum with the economic assessment. Evidence IDs, match IDs,
seller/source-reference facts, and observations are unique within one
comparison.

For a non-empty accepted set, the projection derives:

```text
lowest competitor price = numeric minimum of competitor gross prices
gap to lowest competitor = own observed gross price - lowest competitor price
```

Classification is exact:

```text
own < lowest competitor -> BELOW_LOWEST_COMPETITOR
own = lowest competitor -> TIED_LOWEST_COMPETITOR
own > lowest competitor -> ABOVE_LOWEST_COMPETITOR
```

Every observation tied at the minimum is retained in deterministic identity
order. No percentage, price index, median, average, severity, tolerance, or
rounding is derived.

An empty accepted set returns `NoComparableOffers`; it does not manufacture a
market position or claim that the own offer is the only offer.

## Quality propagation

A completed comparison is `CONFIRMED` only when:

- the own economic-price assessment quality is confirmed;
- every competitor price evidence quality is confirmed;
- every product-match evidence quality is confirmed.

Otherwise it is `ESTIMATED`. This is evidence quality, not model confidence,
decision confidence, competitive completeness, or authorization.

## No recommendation or commerce strategy yet

The result does not say:

- raise, lower, hold, match, undercut, test, or publish a price;
- that the lowest competitor is equivalent in freight, fulfillment, condition,
  reputation, taxes, payment terms, service, or availability duration;
- that a below-market price is economically safe;
- that an above-market price is wrong;
- that the observed set represents the whole market;
- what demand, conversion, Buy Box, margin, inventory, Ads, or contribution
  will do.

Economic position and competitor position remain separate evidence. A later
governed strategy may combine them with broader evidence, policies,
simulations, authority, and outcomes.

## No infrastructure activation

This boundary adds no migration, repository, API, JSON, connector, scraper,
product matcher, event, worker, scheduler, webhook, UI, alert, experiment,
price mutation, rollback, AI, model, expert, agent, or Kernel change.

## Consequences

### Positive

- market comparison cannot bypass economic-price evidence;
- product correspondence is explicit instead of inferred;
- stale and future observations fail visibly;
- exact price gaps remain explainable and quantum-aligned;
- ties are deterministic and preserve all lowest evidence;
- estimated price or match evidence cannot masquerade as confirmed;
- market diagnosis remains separated from recommendation and authority.

### Negative

- callers must establish product matching and availability before evaluation;
- one current observation set does not describe market history or completeness;
- the first slice compares gross offer prices only;
- any invalid observation fails the whole comparison instead of being silently
  excluded;
- no price index, median, recommendation, or automatic action is produced.

## Alternatives considered

### Match competitor products inside the evaluator

Rejected because matching has independent evidence, confidence, review, and
outcome requirements.

### Ignore stale competitors

Rejected because silent exclusion changes the apparent market and weakens
auditability.

### Recommend the lowest safe price immediately

Rejected because a current low price contains no elasticity, demand,
inventory, strategy, or authority evidence.

### Fold competitive position into economic position

Rejected because economic safety and market position are independent facts and
may legitimately point in different directions.

### Add competitor evidence to the Kernel

Rejected because it remains specific to Marketplace Intelligence.

## Authorization

This ADR alone authorizes no implementation. SPEC-0025 may authorize only the
pure price-quantum carry-forward, competitor evidence values, comparison
policy, diagnostic evaluator, result, and focused tests for TASK-0100. It
authorizes no ingestion, product matching, persistence, recommendation,
decision, price action, AI, or Kernel modification.
