# TASK-0128: Marketplace Net-Back Comparable Price Evidence

## Result

Implemented the corrected ADR-0036 / SPEC-0036 boundary as one pure,
production-inactive projection in the existing Marketplace pricing package.

No existing production type, Kernel type, persistence adapter, API, connector,
runtime, recommendation, decision, authority, or price-execution behavior was
changed.

## Explicit same-fact evidence

The projection accepts one complete `NetBackCostBasisFloorDelta` and two
caller-supplied `ObservedMarketplacePrice` values. It requires exact equality
of:

```text
organization
observation ID
gross price
source provenance
source occurrence time
evidence quality
```

The observations must retain distinct source and derived scenario ownership.
No observation is generated, copied, rounded, repaired, or rebound by the
projection.

## Exact dual assessment

After the same-fact invariant succeeds, the implementation delegates exactly
to `MarketplaceEconomicPricePosition` for:

```text
source floor  + source observation
derived floor + derived observation
```

The successful aggregate retains the original floor delta, both observation
instances, and both unmodified assessments. Internal construction reproduces
both evaluator results and the same-fact invariant.

For the accepted fixture, the same observed gross price `100.00` produces:

```text
source floor 143.20  -> BELOW_ABSOLUTE_FLOOR, gap -43.20
derived floor 48.00  -> ABOVE_ECONOMIC_FLOOR, gap 52.00
```

This is paired diagnostic evidence only. No position transition, preference,
materiality, objective, recommendation, or action is derived.

## Reachable controlled failures

The implemented result model is exactly:

```text
EvidenceMismatch
SourceOwnershipMismatch
DerivedOwnershipMismatch
CurrencyMismatch
PriceQuantumMismatch
```

Ownership remains side-specific. Currency and price quantum are shared because
the accepted cost-basis application preserves them across source and derived
profiles, while same-fact observations preserve one exact gross price.

Failures retain no partial assessment and all new renderings are `[REDACTED]`.

## Validation

Focused suite:

```text
MarketplaceNetBackComparablePriceEvidenceTest
9 tests
0 failures
0 errors
0 skipped
```

Complete Marketplace module:

```text
22 suites
200 tests
0 failures
0 errors
0 skipped
```

Broad repository build, excluding only the local PostgreSQL/Testcontainers
test task:

```text
BUILD SUCCESSFUL in 2m 45s
78 actionable tasks
```

The GitHub CI remains the authority for the complete build and persistent
runtime package with Docker.

Additional checks prove:

- projection bytecode contains no Kernel reference;
- public inputs are only the complete floor delta and two observations;
- same-fact mismatch precedes evaluator mapping;
- source and derived ownership remain distinguishable;
- unreachable side-specific currency/quantum result types are absent;
- floor policies, gaps, positions, provenance, time, identity, and quality map
  exactly;
- inputs remain unchanged and value-equal inputs are deterministic;
- internal construction rejects changed evidence or either wrong assessment;
- no transition, percentage, materiality, preference, recommendation,
  authority, or action field is introduced.

## Deliberately absent

- position-transition classification;
- percentage, ratio, tolerance, severity, or materiality;
- preferred Product Cost basis or economic objective;
- market competitiveness, simulation, or optimal price;
- recommendation, approval, decision, authority, or action;
- persistence, API, UI, event, connector, worker, agent, or AI;
- Kernel vocabulary or behavior.

## Boundary conclusion

Marketplace Pricing Intelligence can now compare the diagnostic effect of
source and selected Product Cost floors against one explicitly represented
price fact without rewriting evidence. Any interpretation of that paired fact
requires a later accepted contract.
