# TASK-0100 - Marketplace Competitor Price Position

## Result

Implemented the pure competitor-price evidence and diagnostic position
authorized by ADR-0025 and SPEC-0025.

The implementation remains inside:

```text
io.flooow.marketplace.operations.economics.pricing
```

No Kernel, persistence, API, connector, runtime, recommendation, decision, or
price-execution behavior was added.

## Implemented values

- caller-supplied `CompetitorPriceObservationId`;
- caller-supplied `CompetitiveProductMatchId`;
- bounded internal `CompetitorSellerKey`;
- exact `AvailableMatchedCompetitorPrice` evidence;
- separate price and product-match evidence quality;
- versioned `CompetitivePriceComparisonPolicy` with bounded maximum age;
- exact `CompetitivePricePositionAssessment`;
- controlled comparison results with redacted rendering.

`EconomicPricePositionAssessment` now retains the positive price quantum
already validated by the Net-Back floor. Its previous positions, exact gaps,
and quality behavior remain unchanged.

## Deterministic evaluation

The caller supplies `evaluatedAt`; the evaluator reads no clock. Own and
competitor source times must be within the inclusive policy window.

All competitor evidence must match the own assessment by:

```text
organization
scenario
marketplace
currency
price quantum
```

Observation IDs, product-match IDs, and seller/source-reference evidence facts
must be unique. Any invalid evidence fails the whole comparison with a typed,
redacted result; no observation is rounded or silently omitted.

Valid observations are sorted by unsigned UUID identity before assessment.
Collection input order therefore does not affect output equality.

## Exact positions

For a non-empty accepted collection:

```text
lowest = minimum competitor gross price
gap = own gross price - lowest competitor gross price
```

The result is exactly one of:

```text
BELOW_LOWEST_COMPETITOR
TIED_LOWEST_COMPETITOR
ABOVE_LOWEST_COMPETITOR
```

Every observation tied at the lowest price is retained in deterministic order.
An empty collection returns `NoComparableOffers` and creates no market
position.

## Evidence quality

The assessment retains own economic quality separately from the combined
quality. Combined quality is confirmed only when:

- own economic-price evidence is confirmed;
- every competitor-price fact is confirmed;
- every upstream product-match fact is confirmed.

Otherwise it is estimated. This quality authorizes no decision or action.

## Acceptance fixtures reproduced

With own gross price `299.90`:

```text
[]
  -> NoComparableOffers

[310.00, 305.00]
  -> lowest 305.00
  -> gap -5.10
  -> BELOW_LOWEST_COMPETITOR

[299.90, 299.90, 310.00]
  -> both minimum observations retained
  -> gap 0
  -> TIED_LOWEST_COMPETITOR

[280.00, 310.00]
  -> lowest 280.00
  -> gap 19.90
  -> ABOVE_LOWEST_COMPETITOR
```

Explicit zero prices remain valid evidence.

## Focused validation

```text
./gradlew :applications:marketplace-operations:test
```

Result:

```text
94 tests
0 failures
0 errors
```

The new suite covers Kernel isolation, quantum carry-forward, caller identity,
seller key, exact price, marketplace provenance, policy bounds, microsecond
time, inclusive temporal boundaries, stale/future evidence, ownership,
scenario, marketplace, currency, quantum, duplicate evidence, empty input,
three exact positions, ties, zero values, quality propagation, collection
permutations, immutability, and redacted rendering.

## Deliberately absent

- live marketplace ingestion;
- product discovery or product matching;
- unavailable or unknown offers;
- freight, fulfillment, coupon, or reputation normalization;
- persistence or price history;
- price index, median, elasticity, or forecast;
- recommendation, strategy, approval, or execution;
- event, API, UI, connector, worker, AI, or agent;
- Kernel vocabulary or behavior.

## Boundary conclusion

Marketplace Intelligence can now describe two independent truths for the same
own observed price:

```text
economic position against exact floors
competitive position against current matched evidence
```

Neither truth is treated as a recommendation. A later specification must
govern how broader evidence, policy, simulation, and authority combine them.
