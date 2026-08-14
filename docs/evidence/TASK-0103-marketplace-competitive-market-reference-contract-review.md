# TASK-0103 - Marketplace Competitive Market Reference contract review

## Decision

ADR-0026 and SPEC-0026 define the smallest market-context increment after
Competitive Price Position. The slice derives a seller-balanced median band
from the competitor observations already validated by the accepted comparison
boundary.

It does not recommend a price.

## Boundary choices

- Only a completed `CompetitivePricePositionAssessment` crosses the boundary;
  raw competitor evidence is not reaccepted or revalidated.
- Observations are grouped by internal competitor seller key.
- Each seller contributes its exact lowest accepted gross price once.
- Every same-seller observation tied at that selected price remains linked.
- A versioned policy requires at least two and at most 100 minimum distinct
  sellers.
- Insufficient diversity returns typed evidence and no reference band.
- Seller references sort by price and then seller key.
- Odd cohorts produce one exact median price represented by equal band limits.
- Even cohorts preserve the two observed middle prices as a closed band.
- No mean, interpolation, midpoint, or rounding can manufacture a price.
- Market evidence quality depends only on competitor price and product-match
  evidence, not own economic-price quality.
- Source comparison policy, evidence window, occurrence spread, counts, and
  reference policy remain explicit.

## Why seller balancing

Weighting every offer would allow one seller with many accepted listings to
dominate the distribution. Grouping by seller gives each observed competitor
one numeric contribution while retaining source evidence for reproduction.

The contract does not infer common ownership between seller accounts. That
requires a later identity and evidence boundary.

## Why a median band

The minimum is already retained by Competitive Price Position and is highly
sensitive to one low outlier. A seller-balanced median is more robust as a
descriptive market anchor.

For an even cohort, averaging the two middle prices could create an unobserved
or quantum-misaligned value. The band retains both exact order statistics and
requires no rounding policy.

## Deliberate deferral

The slice does not:

- claim market or source completeness;
- normalize shipping, fulfillment, condition, coupons, reputation, or Buy Box;
- infer common ownership between sellers;
- calculate mean, dispersion, price index, elasticity, or market share;
- compare the own price to the median band;
- combine economic and market evidence into a recommendation;
- introduce replacement cost, inventory, Ads, promotion, objectives, policy,
  authority, action, outcome, or learning;
- add persistence, API, connector, event, worker, AI, agent, or Kernel behavior.

## Essence preserved

- Genesis remains the Organizational Computing platform.
- Marketplace Intelligence remains a vertical consumer.
- Economic Truth precedes economic floors and price diagnosis.
- Economic diagnosis precedes competitor evidence.
- Competitor evidence precedes a descriptive market reference.
- A market reference precedes any governed recommendation.
- Recommendation, authority, decision, action, outcome, and learning remain
  separate stages.
- Every derived value retains exact source lineage, time, and quality.

## Sequence preserved

```text
economic truth -> financial evidence -> reconciliation
  -> net-back floors -> observed economic position
  -> matched competitor evidence -> lowest-price position
  -> seller-balanced competitive market reference
  -> later replacement cost and explicit objective
  -> later simulation and governed recommendation
  -> later approval, execution, reconciliation, and learning
```

## Authorization

Acceptance authorizes TASK-0104 only: pure policy values, per-seller price
references, seller-diversity control, exact median-band projection, evidence
quality, deterministic results, and focused tests in the existing Marketplace
pricing package.

It authorizes no persistence, runtime, ingestion, product matching, API,
recommendation, decision, price action, AI, or Kernel change.
