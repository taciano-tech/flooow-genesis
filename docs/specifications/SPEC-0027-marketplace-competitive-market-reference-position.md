# SPEC-0027: Marketplace Competitive Market Reference Position

Status: Proposed

Date: 2026-08-14

Source decision: ADR-0027

## Objective

Classify one accepted own observed price below, within, or above its exact
seller-balanced competitive market-reference band while proving that both
inputs describe the same source evidence cohort.

## Authorized next implementation

Acceptance authorizes TASK-0106 only:

1. add a pure Market Reference Position evaluator in the existing Marketplace
   pricing package;
2. accept only completed Competitive Price Position and Competitive Market
   Reference assessments;
3. reproduce the reference from the competitive assessment and the retained
   reference policy;
4. fail with one typed redacted result when the supplied reference is not the
   exact reproduced value;
5. classify the own price exactly against both reference limits;
6. retain exact signed gaps, source qualities, combined quality, and policy
   lineage;
7. prove boundary, mismatch, equality, odd/even bands, gaps, quality,
   determinism, privacy, and immutability with pure tests;
8. leave every runtime and external boundary unchanged.

No migration, repository, HTTP, JSON, connector, clock, random source,
tolerance, percentage, price index, recommendation, decision, action, AI, LLM,
ML, or Kernel change is authorized.

## Evaluation

```text
MarketplaceCompetitiveMarketReferencePosition.evaluate(
  competitiveAssessment,
  marketReferenceAssessment
)
```

Evaluation reads no clock and performs no I/O.

The retained reference policy is reconstructed exactly as:

```text
CompetitiveMarketReferencePolicy(
  marketReferenceAssessment.referencePolicyVersion,
  marketReferenceAssessment.minimumDistinctSellers
)
```

The evaluator calls `MarketplaceCompetitiveMarketReference.evaluate` with the
supplied competitive assessment and reconstructed policy.

The result must be `Referenced`, and its assessment must be value-equal to the
supplied market reference. Otherwise evaluation returns:

```text
SourceAssessmentMismatch
```

The mismatch is `[REDACTED]` and exposes no organization, seller, observation,
source, price, time, or policy value. Evaluation performs no partial fallback.

## Position

```text
CompetitiveMarketReferencePosition
  BELOW_REFERENCE_BAND
  WITHIN_REFERENCE_BAND
  ABOVE_REFERENCE_BAND
```

For exact money values:

```text
own = competitiveAssessment.ownObservedGrossPrice
lower = marketReferenceAssessment.lowerMedianPrice
upper = marketReferenceAssessment.upperMedianPrice

own < lower -> BELOW_REFERENCE_BAND
own <= upper -> WITHIN_REFERENCE_BAND
own > upper -> ABOVE_REFERENCE_BAND
```

The reproduced source boundary already guarantees common currency and quantum.
No tolerance or rounding is applied.

## Exact gaps

```text
gapToLowerReference = own - lower
gapToUpperReference = own - upper
```

Both signed values retain assessment currency and exact price quantum.

Expected signs are:

```text
BELOW:   gapToLower < 0 and gapToUpper < 0
WITHIN:  gapToLower >= 0 and gapToUpper <= 0
ABOVE:   gapToLower > 0 and gapToUpper > 0
```

For a zero-width band and exact equality, both gaps are zero.

## Assessment

```text
CompetitiveMarketReferencePositionAssessment(
  organizationId,
  scenarioId,
  ownObservationId,
  marketplace,
  currency,
  priceQuantum,
  ownObservedGrossPrice,
  lowerMedianPrice,
  upperMedianPrice,
  gapToLowerReference,
  gapToUpperReference,
  position,
  ownEconomicQuality,
  marketEvidenceQuality,
  quality,
  comparisonPolicyVersion,
  maximumObservationAge,
  referencePolicyVersion,
  minimumDistinctSellers,
  evaluatedAt
)
```

Construction is internal. It validates shared currency, positive quantum,
quantum alignment, ordered band limits, exact gaps, exact position, quality,
policy values, and microsecond evaluation time. Aggregate rendering is
`[REDACTED]`.

The assessment does not copy seller references. Exact source reproduction is
required before construction, while the original accepted inputs remain the
audit evidence.

## Quality

```text
quality = CONFIRMED
  only when ownEconomicQuality is CONFIRMED
  and marketEvidenceQuality is CONFIRMED

quality = ESTIMATED otherwise
```

The assessment retains all three qualities separately. It uses
`MarketplaceEconomicTruthQuality` for own and combined quality and
`EconomicEvidenceQuality` for market evidence quality.

## Controlled result

```text
CompetitiveMarketReferencePositionResult
  Assessed(assessment)
  SourceAssessmentMismatch
```

Both variants render `[REDACTED]`.

## Acceptance fixtures

```text
band [300.00, 310.00]
own 299.90
-> BELOW_REFERENCE_BAND
-> gaps -0.10 and -10.10
```

```text
band [300.00, 310.00]
own 300.00, 305.00, or 310.00
-> WITHIN_REFERENCE_BAND
```

```text
band [300.00, 310.00]
own 320.00
-> ABOVE_REFERENCE_BAND
-> gaps 20.00 and 10.00
```

```text
band [300.00, 300.00]
own 300.00
-> WITHIN_REFERENCE_BAND
-> both gaps zero
```

## Test plan

TASK-0106 proves at least:

1. reference-position bytecode references no Kernel type;
2. exact source reference reproduces successfully;
3. altered organization, scenario, own observation, marketplace, currency,
   quantum, cohort, seller reference, count, median, quality, time, comparison
   policy, reference policy, or threshold fails with source mismatch;
4. reproduction that becomes insufficient fails with source mismatch;
5. below-band classification and two exact negative gaps;
6. lower-bound equality is within the band;
7. an interior price is within the band;
8. upper-bound equality is within the band;
9. above-band classification and two exact positive gaps;
10. zero-width band equality produces two zero gaps;
11. explicit zero prices remain exact evidence;
12. odd and even reference cohorts are accepted without midpoint creation;
13. confirmed combined quality requires confirmed own and market qualities;
14. estimated own quality retains confirmed market quality separately;
15. estimated market quality retains confirmed own quality separately;
16. source policy versions, maximum age, threshold, and evaluation time remain
    exact;
17. inputs remain unchanged;
18. aggregate rendering and mismatch disclose no sensitive value;
19. no recommendation, action, API, persistence, event, connector, worker, or
    runtime behavior is added;
20. no file under `platform/foundation/kernel` changes;
21. `git diff --check` and the complete repository build remain green.

## Remaining boundary

Market completeness, source history, weighted market share, dispersion,
outlier policy, percentage price index, replacement cost, inventory state,
promotion state, Ads state, economic objective, elasticity, demand, KVI,
simulation, recommendation, policy, approval, execution, rollback, outcome,
learning, API/UI, and alerts require later accepted specifications.

## Acceptance

Merging ADR-0027 and SPEC-0027 authorizes TASK-0106 only. It changes no runtime
behavior and authorizes no recommendation, decision, external price mutation,
AI, or Kernel modification.
