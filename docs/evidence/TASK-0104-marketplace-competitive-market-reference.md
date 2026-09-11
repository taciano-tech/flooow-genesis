# TASK-0104 - Marketplace Competitive Market Reference

## Result

Implemented the pure Competitive Market Reference projection authorized by
ADR-0026 and SPEC-0026.

The implementation remains inside:

```text
io.flooow.marketplace.operations.economics.pricing
```

No Kernel, persistence, API, connector, runtime, recommendation, decision, or
price-execution behavior was added.

## Implemented values

- versioned `CompetitiveMarketReferencePolicy` with a bounded minimum seller
  threshold;
- exact `SellerCompetitivePriceReference` with immutable supporting evidence;
- typed `InsufficientSellerDiversityEvidence`;
- exact `CompetitiveMarketReferenceAssessment`;
- controlled redacted results;
- pure `MarketplaceCompetitiveMarketReference` evaluator.

## Seller-balanced evidence

The evaluator accepts only a completed `CompetitivePricePositionAssessment`.
It reuses the already-validated organization, scenario, marketplace, currency,
price quantum, provenance, temporal window, evidence quality, and uniqueness
boundary.

Accepted observations are grouped by internal competitor seller key. Each
seller contributes its exact lowest gross price once. Every observation from
that seller tied at the selected price remains attached in deterministic UUID
order.

A seller with multiple accepted offers therefore cannot receive additional
weight in the market distribution.

## Exact median band

Seller references sort by exact gross price and then internal seller key. For
`n` sellers:

```text
lower index = (n - 1) / 2
upper index = n / 2
```

Odd cohorts produce equal lower and upper median prices. Even cohorts preserve
the two observed middle prices as a closed band. No mean, midpoint,
interpolation, tolerance, or rounding creates a price.

## Seller diversity and quality

The versioned policy requires between 2 and 100 minimum distinct sellers. A
cohort below the configured threshold returns typed insufficient-diversity
evidence and no reference band.

Market evidence quality is confirmed only when every competitor price fact and
every product-match fact is confirmed. Own economic-price quality is retained
by its source assessment but does not downgrade a confirmed market reference.

This quality is not market completeness, model confidence, decision
confidence, or authority.

## Lineage and deterministic behavior

The result retains:

```text
organization and scenario
own observation lineage
marketplace, currency, and price quantum
seller and offer counts
seller references and supporting IDs
source comparison policy and maximum age
reference policy and minimum seller threshold
earliest and latest competitor source times
caller-supplied evaluation time
market evidence quality
```

Input permutations produce value-equal output. Collections are defensively
copied and immutable. Aggregate rendering exposes no seller, organization,
source, price, or policy value.

## Acceptance fixtures reproduced

```text
seller-a [280.00, 280.00, 285.00]
seller-b [300.00]
seller-c [310.00]

seller references [280.00, 300.00, 310.00]
median band [300.00, 300.00]
```

```text
seller references [280.00, 300.00, 310.00, 320.00]
median band [300.00, 310.00]
```

No `305.00` midpoint is manufactured.

## Focused validation

```text
./gradlew :applications:marketplace-operations:test
```

Result:

```text
104 tests
0 failures
0 errors
```

The new suite covers Kernel isolation, policy bounds, seller balancing,
same-seller minimum ties, diversity insufficiency, exact threshold, odd/even
median bands, equal middle prices, explicit zero, quality propagation,
economic-quality independence, source lineage, time spread, counts,
permutations, immutability, validation, and redacted rendering.

The repository build excluding local PostgreSQL Testcontainers also passed.
The complete CI build remains the authority for the Docker-backed persistence
suite.

## Deliberately absent

- live marketplace ingestion or observation persistence;
- product discovery, matching, or current-offer selection;
- seller common-ownership inference;
- freight, fulfillment, coupon, reputation, condition, or Buy Box
  normalization;
- market completeness, mean, dispersion, price index, elasticity, or forecast;
- own-price comparison to the median band;
- replacement cost, economic objective, recommendation, strategy, approval,
  or execution;
- event, API, UI, connector, worker, AI, or agent;
- Kernel vocabulary or behavior.

## Boundary conclusion

Marketplace Intelligence now has three separate pricing facts:

```text
economic position against exact floors
competitive position against the lowest matched competitor
seller-balanced competitive market reference band
```

None is treated as a recommendation. A later specification must explicitly
govern which additional economic, inventory, replacement-cost, objective,
policy, simulation, and authority evidence is required before recommending an
action.
