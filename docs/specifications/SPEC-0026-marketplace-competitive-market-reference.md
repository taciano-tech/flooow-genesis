# SPEC-0026: Marketplace Competitive Market Reference

Status: Proposed

Date: 2026-08-14

Source decision: ADR-0026

## Objective

Derive an exact, seller-balanced, auditable median reference band from one
accepted Competitive Price Position assessment without inventing a price,
claiming market completeness, or recommending an action.

## Authorized next implementation

Acceptance authorizes TASK-0104 only:

1. add a bounded, versioned Competitive Market Reference policy in the
   existing Marketplace pricing package;
2. derive one exact lowest-price reference per competitor seller from the
   observations already accepted by `CompetitivePricePositionAssessment`;
3. retain all same-seller observations tied at that seller's selected price;
4. require an explicit minimum number of distinct sellers and return a typed
   insufficient-diversity result when it is not met;
5. sort seller references deterministically and derive lower and upper median
   order statistics without averaging or rounding;
6. retain source comparison policy, temporal spread, evidence counts, and
   separate market-evidence quality;
7. prove seller balancing, ties, odd/even medians, insufficiency, quality,
   determinism, privacy, and immutability with pure tests;
8. leave persistence, startup, APIs, connectors, and every other module
   unchanged.

No migration, repository, HTTP, JSON, connector, scraper, product matcher,
clock, random source, mean, price index, dispersion score, completeness score,
elasticity, inventory, recommendation, decision, action, AI, LLM, ML, or
Kernel change is authorized.

## Reference policy

```text
CompetitiveMarketReferencePolicyVersion
CompetitiveMarketReferencePolicy(
  version,
  minimumDistinctSellers
)
```

The version accepts canonical lowercase policy text using the same bounded
syntax as existing Marketplace pricing policies and renders `[REDACTED]`.

`minimumDistinctSellers` is an integer from 2 through 100 inclusive. The lower
bound prevents one seller from being described as a market cohort. The upper
bound prevents an accidentally impossible policy while permitting a caller to
set a stricter business threshold.

The policy is immutable and value-equal. It contains no freshness duration;
the input assessment has already applied and retained the versioned comparison
window.

## Seller price reference

```text
SellerCompetitivePriceReference(
  sellerKey,
  grossPrice,
  supportingObservationIds
)
```

For each distinct seller key in the source assessment:

```text
grossPrice = minimum exact gross price for that seller
supportingObservationIds = every observation ID from that seller
  whose gross price equals the minimum
```

Supporting IDs are unique, immutable, and sorted by unsigned UUID identity.
The collection is never empty.

Construction is internal and validates non-empty, unique, deterministically
ordered support plus a non-negative exact price. The evaluator creates the
value only from source observations that share the seller and selected exact
price. Aggregate rendering is `[REDACTED]`.

Seller display name, marketplace credential, common ownership, shipping,
fulfillment, coupons, condition, reputation, installment terms, Buy Box, and
availability duration are absent.

## Evaluation

```text
MarketplaceCompetitiveMarketReference.evaluate(
  competitiveAssessment,
  policy
)
```

`competitiveAssessment` must be an accepted
`CompetitivePricePositionAssessment`. Consequently its competitor collection
is non-empty and already satisfies:

```text
organization and scenario ownership
marketplace equality
currency and quantum equality
inclusive source-time window
marketplace provenance
observation, match, and source-fact uniqueness
deterministic identity order
```

Evaluation reads no clock and performs no I/O. It does not modify or silently
drop any input observation.

## Seller-balanced cohort

Evaluation groups the source competitor observations by seller key. Each group
produces exactly one `SellerCompetitivePriceReference` using that seller's
minimum exact gross price.

Seller references are sorted by:

```text
gross price numeric ascending
then seller key canonical value ascending
```

The seller key tiebreaker affects only deterministic order. Sellers with equal
prices contribute equal numeric evidence.

Every accepted source observation remains represented by the source
assessment. A non-selected higher offer from a seller does not become a
numeric market-distribution member, but it is not deleted or reclassified.

## Insufficient seller diversity

When the distinct seller count is less than the policy minimum, evaluation
returns:

```text
InsufficientSellerDiversity(
  organizationId,
  scenarioId,
  ownObservationId,
  marketplace,
  observedOfferCount,
  observedSellerCount,
  requiredSellerCount,
  comparisonPolicyVersion,
  referencePolicyVersion,
  evaluatedAt
)
```

The evidence is immutable, value-equal, and `[REDACTED]`. It contains no
seller key, source reference, price, median, market position, recommendation,
or claim about why sellers are absent.

## Exact median reference band

For a sufficient seller cohort of size `n`, using zero-based indexes:

```text
lowerIndex = (n - 1) / 2
upperIndex = n / 2

lowerMedianPrice = sellerReferences[lowerIndex].grossPrice
upperMedianPrice = sellerReferences[upperIndex].grossPrice
```

Index division is integer division.

For odd `n`, both values are equal. For even `n`, they define a closed band.
Both values are exact source prices in assessment currency and aligned to the
assessment price quantum.

No midpoint, average, interpolation, percentage, tolerance, or rounding is
calculated.

## Market-reference assessment

```text
CompetitiveMarketReferenceAssessment(
  organizationId,
  scenarioId,
  ownObservationId,
  marketplace,
  currency,
  priceQuantum,
  sellerReferences,
  observedOfferCount,
  observedSellerCount,
  lowerMedianPrice,
  upperMedianPrice,
  marketEvidenceQuality,
  comparisonPolicyVersion,
  maximumObservationAge,
  referencePolicyVersion,
  minimumDistinctSellers,
  earliestOccurredAt,
  latestOccurredAt,
  evaluatedAt
)
```

`sellerReferences` is immutable and uses the deterministic price/seller order.
Observed offer count equals the source competitor-observation count. Observed
seller count equals the seller-reference count and meets the policy minimum.

The two median prices must equal the order statistics derived from the stored
seller references. They use assessment currency and price quantum.

`earliestOccurredAt` and `latestOccurredAt` are the exact minimum and maximum
source occurrence times among all accepted competitor observations. Both lie
inside the source assessment's retained inclusive time window.

Construction is internal and validates ownership lineage, counts, order,
seller uniqueness, non-empty support, price selection, quantum, median band,
quality, policy values, and temporal consistency. Aggregate rendering is
`[REDACTED]`.

## Market evidence quality

```text
CONFIRMED
  when every source competitor priceEvidenceQuality is CONFIRMED
  and every source matchEvidenceQuality is CONFIRMED

ESTIMATED
  otherwise
```

The value uses `EconomicEvidenceQuality`. It deliberately does not inherit own
economic-price quality because the assessment describes competitor evidence.

This value does not measure seller independence, sample completeness, temporal
simultaneity, model confidence, or decision confidence.

## Controlled result

```text
CompetitiveMarketReferenceResult
  Referenced(assessment)
  InsufficientSellerDiversity(evidence)
```

Both variants render `[REDACTED]`. Evaluation has no raw-input validation
variants because raw observations cannot cross this boundary; they must first
pass Competitive Price Position.

## Acceptance fixtures

The examples use price quantum `0.01`, confirmed evidence, and distinct seller
keys unless repeated explicitly.

### Multiple offers from one seller

```text
seller-a [280.00, 280.00, 285.00]
seller-b [300.00]
seller-c [310.00]

seller references [280.00, 300.00, 310.00]
lower median 300.00
upper median 300.00
```

Both `seller-a` observations tied at `280.00` are retained as support. Its
`285.00` offer does not give the seller a second distribution vote.

### Even seller cohort

```text
seller references [280.00, 300.00, 310.00, 320.00]
lower median 300.00
upper median 310.00
```

No `305.00` midpoint is created.

### Equal middle prices

```text
seller references [280.00, 300.00, 300.00, 320.00]
lower median 300.00
upper median 300.00
```

### Insufficient diversity

```text
policy minimum sellers 3
observed sellers 2
-> InsufficientSellerDiversity
```

Collection permutations produce value-equal output.

## Test plan

TASK-0104 proves at least:

1. market-reference bytecode references no Kernel type;
2. policy version syntax, redaction, and value equality;
3. minimum seller bounds reject values below 2 and above 100;
4. only an accepted competitive assessment crosses the boundary;
5. one seller with multiple offers contributes one numeric seller reference;
6. the exact lowest gross price is selected within each seller group;
7. every same-seller tie at the selected price is retained deterministically;
8. higher same-seller offers remain unchanged in the source assessment;
9. seller references sort by exact price and then canonical seller key;
10. insufficient diversity returns typed evidence with exact counts and policy
    lineage but no price;
11. the policy threshold boundary is accepted exactly;
12. odd seller cohorts produce equal lower and upper medians;
13. even seller cohorts produce two observed median boundaries without a
    midpoint;
14. equal middle prices produce a zero-width band;
15. explicit zero seller prices remain valid evidence;
16. median prices retain source currency and exact price quantum;
17. offer and seller counts are exact;
18. earliest and latest source times are exact and retained;
19. market quality is confirmed only when all price and match facts are
    confirmed;
20. estimated own economic quality alone does not downgrade confirmed market
    evidence;
21. any estimated competitor price or match evidence makes market quality
    estimated;
22. source comparison and reference policy versions are retained separately;
23. collection permutations return value-equal ordered output;
24. inputs remain unchanged and output collections are immutable;
25. aggregate rendering and failures disclose no sensitive value;
26. no mean, midpoint, price index, recommendation, action, API, persistence,
    event, connector, worker, or runtime behavior is added;
27. no file under `platform/foundation/kernel` changes;
28. `git diff --check` and the complete repository build remain green.

## Remaining boundary

Live ingestion, observation persistence/history, current-offer selection,
unavailable offers, seller common ownership, source completeness, freight and
fulfillment normalization, condition, coupons, reputation, Buy Box, weighted
market share, dispersion, outlier policy, price index, own-price comparison to
the median band, replacement cost, economic objectives, elasticity, KVI,
inventory-aware pricing, promotions, experiments, recommendations, policies,
approvals, execution, rollback, API/UI, alerts, outcomes, and learning require
later accepted specifications.

## Acceptance

Merging ADR-0026 and SPEC-0026 authorizes TASK-0104 only. It changes no runtime
behavior and authorizes no live market ingestion, product matching,
recommendation, decision, external price mutation, AI, or Kernel modification.
