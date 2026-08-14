# ADR-0026: Marketplace Competitive Market Reference Boundary

Status: Proposed

Date: 2026-08-14

## Context

Marketplace Intelligence can now diagnose one economically validated own price
against the lowest current, available, already-matched competitor price. That
comparison answers a narrow question, but the lowest observed offer is not a
stable representation of the observed market.

The current evidence may contain:

- one unusually low offer;
- several offers controlled by the same competitor seller;
- an even number of seller prices whose arithmetic midpoint is not a valid
  marketplace price quantum;
- estimated price or product-match evidence;
- too few distinct sellers to support a useful market reference.

Unsafe shortcuts would be to:

- call the lowest observed price the market price;
- average every offer and allow sellers with more offers to receive more
  weight;
- calculate an arithmetic median that invents an unobserved or misaligned
  price;
- silently discard high, low, estimated, or inconvenient observations;
- claim that a finite observed cohort represents the complete marketplace;
- turn a descriptive reference into a recommended price;
- add seller, offer, marketplace, or price-reference vocabulary to the Kernel.

## Decision

Introduce a pure, production-inactive Competitive Market Reference projection
in the existing Marketplace pricing package:

```text
io.flooow.marketplace.operations.economics.pricing
```

It consumes one accepted `CompetitivePricePositionAssessment` and one explicit
reference policy. It derives a deterministic seller-balanced median band from
the already-validated competitor observations.

It performs no discovery, ingestion, matching, normalization, persistence,
recommendation, decision, or action.

## Reuse the accepted comparison boundary

The projection accepts only a completed competitive-price assessment. It does
not accept raw observations or a `NoComparableOffers` result.

The source assessment already freezes and validates:

```text
organization and scenario ownership
marketplace
currency and positive price quantum
available matched competitor observations
marketplace provenance
source occurrence times and temporal window
price and product-match evidence quality
observation and source-fact uniqueness
deterministic observation order
comparison policy version and evaluation time
```

The market-reference projection must preserve that lineage. It does not widen
the source time window, replace the comparison policy, or reinterpret product
matching and availability.

## One reference per competitor seller

All accepted observations are grouped by `CompetitorSellerKey`.

For each seller, the projection selects the exact lowest available gross price
observed for that seller. Every observation from that seller tied at the
selected price is retained in deterministic observation-identity order.

The resulting seller reference contains:

```text
seller key
exact selected gross price
all supporting tied observation IDs
```

This version deliberately uses the lowest available gross price per seller
because it is the seller price visible to a buyer within the accepted gross
price evidence. It does not claim equivalence in shipping, fulfillment,
condition, reputation, payment terms, coupon eligibility, or Buy Box status.

One seller contributes exactly one numeric price to the market distribution,
regardless of how many accepted offers the seller controls.

## Explicit seller-diversity policy

A versioned `CompetitiveMarketReferencePolicy` supplies a required minimum
number of distinct sellers. The value is at least two and bounded to prevent
an accidental impossible or unbounded threshold.

If the observed seller count is below the policy minimum, the projection
returns typed `InsufficientSellerDiversity` evidence. It does not manufacture
a median band.

The policy does not assert market completeness. Passing the threshold only
means the accepted observed cohort is large enough for this versioned
descriptive projection.

## Exact median band

Seller references are sorted deterministically by:

```text
selected gross price ascending
then internal seller key ascending
```

For `n` accepted seller references:

```text
lower index = (n - 1) / 2
upper index = n / 2
lower median price = seller price at lower index
upper median price = seller price at upper index
```

Integer division is used for the indexes.

For an odd seller count, lower and upper median prices are equal. For an even
seller count, the result is a closed observed-price band. The projection does
not average the two prices and therefore never invents a price or applies
rounding.

Every seller reference is retained in deterministic order so the band can be
reproduced directly from its evidence.

## Temporal description

The assessment retains the originating comparison policy version, maximum
observation age, evaluation time, earliest competitor occurrence time, and
latest competitor occurrence time.

These values describe the temporal spread of the accepted evidence. They do
not claim simultaneity, continuous availability, or market history.

## Evidence quality

Market-reference quality is `CONFIRMED` only when every underlying competitor
price fact and every product-match fact is confirmed. Otherwise it is
`ESTIMATED`.

Own economic-price quality is deliberately excluded from market-reference
quality. The source own observation establishes the comparison boundary, but
the median band describes competitor market evidence rather than own economic
truth.

This is evidence quality, not sample completeness, model confidence, decision
confidence, or authority.

## No recommendation or strategy

The reference does not say:

- raise, lower, hold, match, undercut, test, or publish a price;
- that either median boundary is economically safe or commercially optimal;
- that an observed seller is truly independent from another seller;
- that the observed sellers represent the complete market;
- what demand, conversion, Buy Box, inventory, Ads, margin, contribution, or
  elasticity will do;
- which organizational objective should govern a future decision.

Economic position, lowest-competitor position, and competitive market
reference remain separate evidence. A later governed layer may combine them
with replacement cost, inventory, promotion, Ads, objectives, policies,
simulation, authority, and outcome evidence.

## No infrastructure activation

This boundary adds no migration, repository, API, JSON, connector, scraper,
product matcher, current-offer selector, event, worker, scheduler, webhook, UI,
alert, experiment, price mutation, rollback, AI, model, expert, agent, or
Kernel change.

## Consequences

### Positive

- one seller cannot gain extra distribution weight merely by exposing more
  accepted offers;
- the reference is less sensitive than the minimum to one low outlier;
- every median boundary is an actually observed, quantum-aligned price;
- even cohorts produce an honest band instead of an invented midpoint;
- insufficient seller diversity fails explicitly;
- evidence lineage, time, quality, and deterministic reproduction are kept;
- market description remains separate from recommendation and authority.

### Negative

- seller identity supplied upstream may not reveal common ownership;
- the lowest gross price per seller still ignores fulfillment, shipping,
  coupons, reputation, and condition;
- a median band does not measure dispersion, completeness, elasticity, or
  market share;
- callers must first produce a valid competitive-price assessment;
- no recommendation, price index, or automatic action is produced.

## Alternatives considered

### Average every accepted offer

Rejected because offer count would weight sellers unequally and the average
could require policy-dependent rounding to an unobserved price.

### Use the lowest competitor as the market reference

Rejected because the minimum is already available as a separate diagnostic
and is maximally sensitive to one outlier.

### Average the two middle seller prices

Rejected because an arithmetic midpoint may not be observed or aligned to the
accepted price quantum. A median band preserves both exact observations.

### Select one offer globally before grouping sellers

Rejected because global selection would lose seller diversity and recreate a
minimum-price diagnostic rather than a market reference.

### Accept raw observations directly

Rejected because it would duplicate ownership, marketplace, currency,
quantum, freshness, provenance, and uniqueness rules already established by
Competitive Price Position.

### Add market-reference primitives to the Kernel

Rejected because seller-balanced price distributions remain specific to
Marketplace Intelligence.

## Authorization

This ADR alone authorizes no implementation. SPEC-0026 may authorize only the
pure policy, seller-reference values, deterministic median-band projection,
controlled insufficient-diversity result, and focused tests for TASK-0104.

It authorizes no ingestion, persistence, API, product matching,
recommendation, decision, price action, AI, or Kernel modification.
