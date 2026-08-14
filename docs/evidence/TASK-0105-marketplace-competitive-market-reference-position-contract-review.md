# TASK-0105 - Marketplace Competitive Market Reference Position contract review

## Decision

ADR-0027 and SPEC-0027 define the smallest diagnostic increment after the
seller-balanced Competitive Market Reference.

The slice classifies the accepted own gross price below, within, or above the
exact market-reference band. It does not recommend a price.

## Boundary choices

- Both accepted source assessments are explicit inputs.
- Matching metadata alone is not trusted as cohort lineage.
- The supplied market reference is reproduced from the competitive assessment
  and its retained reference policy.
- Any non-reference result or value inequality fails closed as one redacted
  `SourceAssessmentMismatch`.
- Lower and upper median limits remain separate.
- Both signed own-price gaps are retained exactly.
- Band boundaries are inclusive.
- Own economic, market evidence, and combined diagnostic qualities remain
  separate.
- No tolerance, midpoint, percentage, or rounding is introduced.

## Accepted positions

```text
BELOW_REFERENCE_BAND
WITHIN_REFERENCE_BAND
ABOVE_REFERENCE_BAND
```

For band `[300.00, 310.00]`, own prices `299.90`, `305.00`, and `320.00`
produce the three positions respectively. Prices exactly `300.00` and `310.00`
are within the band.

## Deliberate deferral

The slice does not calculate price index, dispersion, completeness,
replacement cost, inventory, Ads, promotion, objective, elasticity,
recommendation, authority, action, or outcome.

It adds no persistence, API, connector, event, worker, AI, agent, or Kernel
behavior.

## Essence preserved

- Genesis remains the Organizational Computing platform.
- Marketplace Intelligence remains a vertical consumer.
- Economic and market truth precede comparison.
- Comparison precedes recommendation.
- Recommendation remains separate from authority, decision, action, outcome,
  and learning.
- Every derived value remains exact, reproducible, and evidence-linked.

## Sequence preserved

```text
economic truth -> net-back floors -> economic position
  -> matched competitor evidence -> lowest-price position
  -> seller-balanced market reference -> reference-band position
  -> later replacement cost and explicit objective
  -> later simulation and governed recommendation
```

## Authorization

Acceptance authorizes TASK-0106 only: pure source reproduction, exact
reference-band classification, signed gaps, quality propagation, controlled
results, and focused tests in the existing Marketplace pricing package.

It authorizes no persistence, runtime, API, recommendation, decision, price
action, AI, or Kernel change.
