# TASK-0106 - Marketplace Competitive Market Reference Position

## Result

Implemented the pure Market Reference Position projection authorized by
ADR-0027 and SPEC-0027 inside the existing Marketplace pricing package.

No Kernel, persistence, API, connector, runtime, recommendation, decision, or
price-execution behavior was added.

## Exact source reproduction

The evaluator accepts one completed Competitive Price Position assessment and
one completed Competitive Market Reference assessment. It reconstructs the
retained reference policy and reproduces the complete market reference from
the supplied competitive assessment.

Only a value-equal reproduced reference is accepted. A reference from another
cohort, or any reference that can no longer be reproduced, returns the single
redacted `SourceAssessmentMismatch` result.

## Exact diagnostic position

The implementation classifies the own observed gross price as:

```text
BELOW_REFERENCE_BAND
WITHIN_REFERENCE_BAND
ABOVE_REFERENCE_BAND
```

Both reference boundaries are inclusive. The result retains exact signed gaps
from the own price to the lower and upper median prices. No midpoint,
tolerance, percentage, interpolation, or rounding is used.

## Quality and lineage

The assessment retains own economic quality, market evidence quality, and
combined diagnostic quality separately. Combined quality is confirmed only
when both sources are confirmed.

Organization, scenario, own observation, marketplace, currency, price quantum,
comparison policy, source maximum age, reference policy, seller threshold, and
evaluation time remain explicit. Aggregate and failure rendering is redacted.

## Validation

```text
./gradlew :applications:marketplace-operations:test
```

Result:

```text
107 tests
0 failures
0 errors
```

The suite covers source reproduction, cross-cohort failure, below/within/above
classification, inclusive boundaries, exact signed gaps, quality separation,
redaction, plus the existing market-reference determinism and Kernel-isolation
tests. The complete Docker-backed CI remains the repository-wide authority.

## Deliberately absent

- recommendation, optimal price, objective, policy authority, or action;
- replacement cost, inventory, Ads, promotion, elasticity, or demand;
- persistence, API, event, connector, worker, AI, or agent;
- Kernel vocabulary or behavior.

## Boundary conclusion

Marketplace Intelligence can now explain the own price against economic floors,
the lowest matched competitor, and the seller-balanced market-reference band.
These remain evidence and diagnosis, not a recommendation.
