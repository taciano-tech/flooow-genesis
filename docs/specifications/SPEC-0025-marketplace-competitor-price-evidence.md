# SPEC-0025: Marketplace Competitor Price Evidence

Status: Proposed

Date: 2026-08-14

Source decision: ADR-0025

## Objective

Compare one accepted own economic-price observation with a finite set of
current, available, already-matched competitor gross prices and produce an
exact, explainable competitive position without recommending or changing a
price.

## Authorized next implementation

Acceptance authorizes TASK-0100 only:

1. extend `EconomicPricePositionAssessment` additively with the positive
   `priceQuantum` already supplied by its Net-Back floor;
2. add pure competitor observation identities, seller and product-match
   evidence values in the Marketplace pricing package;
3. add a versioned maximum-age policy and caller-supplied evaluation time;
4. validate ownership, marketplace, currency, quantum, evidence uniqueness,
   and the inclusive temporal window without normalization or silent omission;
5. calculate the exact lowest competitor gross price and signed own-price gap;
6. classify the accepted three competitive positions and retain every minimum
   tie deterministically;
7. propagate confirmed/estimated evidence quality from own price, competitor
   price, and product match;
8. prove boundary, time, equality, tie, uniqueness, quantum, quality,
   determinism, privacy, and immutability behavior with pure tests;
9. leave persistence, startup, APIs, connectors, and all other module behavior
   unchanged.

No migration, repository, HTTP, JSON, scraper, product matcher, event, worker,
clock, random source, elasticity, inventory, recommendation, decision, action,
AI, LLM, ML, or Kernel change is authorized.

## Additive economic assessment evidence

`EconomicPricePositionAssessment` gains:

```text
priceQuantum: MarketplaceMoney
```

Construction requires a positive quantum in assessment currency and exact
alignment of observed price, absolute floor, and economic floor. The existing
position and gap rules remain unchanged. The economic evaluator copies the
quantum from the accepted `NetBackEconomicFloor`.

## Identity and internal seller key

```text
CompetitorPriceObservationId
CompetitiveProductMatchId
```

Both identities wrap caller-supplied UUIDs, accept canonical lowercase UUID
text, provide value equality and internal persistence access, and render
`[INTERNAL]`. No random identity is generated.

```text
CompetitorSellerKey
```

The key accepts 1-100 lowercase ASCII letters, digits, dots, or hyphens, begins
with a letter or digit, has value equality, and renders `[INTERNAL]`. It is a
caller-controlled internal reference, not a display name, credential, or
marketplace account secret.

## Available matched competitor observation

```text
AvailableMatchedCompetitorPrice(
  organizationId,
  scenarioId,
  marketplace,
  observationId,
  productMatchId,
  sellerKey,
  grossPrice,
  source,
  occurredAt,
  priceEvidenceQuality,
  matchEvidenceQuality
)
```

Construction requires a non-negative exact `MarketplaceMoney`, a marketplace
source with a present external reference, and source time at microsecond
precision. Aggregate rendering is `[REDACTED]`.

The value means the caller asserts that the offer was available and matched to
the own comparison subject at `occurredAt`. The projection does not verify,
discover, or infer either claim. An estimated claim remains admissible but
downgrades result quality.

The observation is immutable and value-equal. It contains no own SKU/listing,
competitor display name, credential, shipping adjustment, coupon, Buy Box,
floor, recommendation, or execution state.

## Comparison policy

```text
CompetitivePriceComparisonPolicyVersion
CompetitivePriceComparisonPolicy(version, maximumObservationAge)
```

The policy version accepts canonical lowercase policy text with at most 100
characters and renders `[REDACTED]`. Maximum age is strictly positive, no more
than 31 days, and has microsecond precision. The bound makes accidental
unlimited freshness impossible while leaving the exact business duration
versioned and caller-controlled.

## Evaluation

```text
MarketplaceCompetitivePricePosition.evaluate(
  ownAssessment,
  competitorObservations,
  policy,
  evaluatedAt
)
```

`evaluatedAt` must have microsecond precision. Evaluation requires:

1. own observation time and every competitor time in the inclusive interval
   `[evaluatedAt - maximumObservationAge, evaluatedAt]`;
2. exact organization and scenario ownership;
3. exact marketplace equality;
4. competitor currency equal to own assessment currency;
5. every competitor price exactly aligned to own assessment price quantum;
6. unique observation IDs;
7. unique product-match IDs;
8. unique `(sellerKey, source system, external reference)` evidence facts.

Validation is independent of collection iteration order. Any invalid
observation fails closed; none is silently rounded, normalized, dropped, or
reclassified.

Controlled failures are:

```text
OwnObservationOutsideWindow
CompetitorObservationOutsideWindow
OwnershipMismatch
MarketplaceMismatch
CurrencyMismatch
PriceQuantumMismatch
DuplicateEvidence
```

Every failure renders `[REDACTED]` and exposes no identifier, seller, source,
time, price, policy, organization, or marketplace value.

## Empty evidence

An empty collection whose own observation is temporally valid returns:

```text
NoComparableOffers(
  organizationId,
  scenarioId,
  ownObservationId,
  marketplace,
  policyVersion,
  evaluatedAt
)
```

The result is immutable, value-equal, and `[REDACTED]`. It contains no
competitive position, minimum price, gap, quality, recommendation, or claim of
market completeness.

## Competitive position

For a valid non-empty collection:

```text
CompetitivePricePosition
  BELOW_LOWEST_COMPETITOR
  TIED_LOWEST_COMPETITOR
  ABOVE_LOWEST_COMPETITOR
```

```text
lowest = minimum competitor gross price
gapToLowest = own observed gross price - lowest

own < lowest -> BELOW_LOWEST_COMPETITOR
own = lowest -> TIED_LOWEST_COMPETITOR
own > lowest -> ABOVE_LOWEST_COMPETITOR
```

Comparison uses exact numeric money equality. No tolerance or rounding is
applied.

## Assessment

```text
CompetitivePricePositionAssessment(
  organizationId,
  scenarioId,
  ownObservationId,
  marketplace,
  currency,
  priceQuantum,
  ownObservedGrossPrice,
  ownEconomicPosition,
  competitorObservations,
  lowestCompetitorPrice,
  lowestCompetitorObservationIds,
  gapToLowestCompetitor,
  position,
  quality,
  policyVersion,
  maximumObservationAge,
  evaluatedAt
)
```

Competitor observations and lowest IDs are immutable and sorted by unsigned
UUID identity. Every observation tied at the numeric minimum appears exactly
once in `lowestCompetitorObservationIds`.

Quality is `CONFIRMED` only when own assessment quality, every competitor price
quality, and every competitor match quality are confirmed. Otherwise it is
`ESTIMATED`.

Construction is internal and validates ownership, marketplace, currency,
quantum, order, minimum, ties, gap, position, quality, policy, and temporal
consistency. Aggregate rendering is `[REDACTED]`.

## Controlled result

```text
CompetitivePricePositionResult
  Compared(assessment)
  NoComparableOffers(evidence)
  OwnObservationOutsideWindow
  CompetitorObservationOutsideWindow
  OwnershipMismatch
  MarketplaceMismatch
  CurrencyMismatch
  PriceQuantumMismatch
  DuplicateEvidence
```

All aggregate variants render `[REDACTED]`.

## Acceptance fixtures

For own observed price `299.90` and quantum `0.01`:

```text
competitors []
  -> NoComparableOffers

competitors [310.00, 305.00]
  -> lowest 305.00
  -> gap -5.10
  -> BELOW_LOWEST_COMPETITOR

competitors [299.90, 299.90, 310.00]
  -> lowest 299.90
  -> both minimum observation IDs retained
  -> gap 0
  -> TIED_LOWEST_COMPETITOR

competitors [280.00, 310.00]
  -> lowest 280.00
  -> gap 19.90
  -> ABOVE_LOWEST_COMPETITOR
```

Collection permutations produce value-equal output.

## Test plan

TASK-0100 proves at least:

1. competitor-price bytecode references no Kernel type;
2. economic assessment carries and validates its floor quantum without changing
   previous position fixtures;
3. canonical caller identities, seller key, exact non-negative price,
   marketplace source reference, microsecond time, and safe rendering;
4. policy version, positive bounded age, and microsecond precision;
5. evaluated-at microsecond precision;
6. inclusive lower and upper temporal boundaries are accepted;
7. stale own and future own observations fail closed;
8. stale or future competitor observations fail closed;
9. ownership, scenario, marketplace, and currency mismatches are controlled;
10. exact quantum misalignment is controlled and never rounded;
11. duplicate observation, match, and seller/source facts are controlled;
12. an empty set returns no comparable offers and no position;
13. below-lowest classification and exact negative gap;
14. tied-lowest classification, zero gap, and every tied identity retained;
15. above-lowest classification and exact positive gap;
16. explicit zero competitor and own prices remain evidence;
17. confirmed quality requires confirmed own, price, and match evidence;
18. any estimated evidence produces an estimated assessment;
19. collection permutations return value-equal ordered output;
20. inputs remain unchanged and output collections are immutable;
21. aggregate rendering and failures disclose no sensitive value;
22. no availability inference, product matching, price index, recommendation,
    action, API, persistence, event, connector, worker, or runtime behavior is
    added;
23. no file under `platform/foundation/kernel` changes;
24. `git diff --check` and the complete repository build remain green.

## Remaining boundary

Live ingestion, observation persistence/history, current-offer selection,
unavailable/unknown offers, product matching, shipping/fulfillment
normalization, condition and reputation, Buy Box, market completeness, price
index, median, elasticity, KVI, inventory-aware pricing, promotions,
experiments, recommendations, policies, approvals, execution, rollback,
API/UI, alerts, outcomes, and learning require later accepted specifications.

## Acceptance

Merging ADR-0025 and SPEC-0025 authorizes TASK-0100 only. It changes no runtime
behavior and authorizes no live market ingestion, product matching,
recommendation, decision, external price mutation, AI, or Kernel modification.
