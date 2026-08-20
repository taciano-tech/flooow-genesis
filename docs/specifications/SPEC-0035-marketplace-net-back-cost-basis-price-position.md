# SPEC-0035: Marketplace Net-Back Cost-Basis Price Position

Status: Proposed

Date: 2026-08-15

Source decision: ADR-0035

## Objective

Evaluate one exact derived-scenario observed gross price against the accepted
selected-cost Net-Back floor, preserving complete cost-basis lineage and
delegating all diagnostic semantics to the existing economic price-position
evaluator.

## Authorized next implementation

Acceptance authorizes TASK-0123 only:

1. add one pure projection in the Marketplace pricing package;
2. accept one complete `NetBackCostBasisFloorDelta` and one
   `ObservedMarketplacePrice`;
3. invoke the accepted evaluator against the exact retained derived floor;
4. map assessed and mismatch results without loss or fallback;
5. retain floor delta, observation, and exact assessment on success;
6. reproduce the successful aggregate invariant internally;
7. render every new type as `[REDACTED]`;
8. prove behavior with focused tests and complete repository verification.

No evidence rewriting, source-floor position, cross-scenario comparison,
objective, recommendation, decision, authority, action, persistence, API,
connector, AI, or Kernel change is authorized.

## Inputs and evaluation

```text
MarketplaceNetBackCostBasisPricePosition.evaluate(
  floorDelta: NetBackCostBasisFloorDelta,
  observation: ObservedMarketplacePrice
): NetBackCostBasisPricePositionResult
```

The implementation invokes exactly:

```text
MarketplaceEconomicPricePosition.evaluate(
  floorDelta.sourceScenarioFloor.appliedScenarioFloor.floor,
  observation
)
```

It must not calculate another floor, reconstruct a profile, replace Product
Cost evidence, recalculate floor deltas, copy the observation to another
scenario, or introduce any rounding, tolerance, comparison, or decision policy.

There is no generated ID, time, clock, random source, database, network, or
framework dependency.

## Successful aggregate

When the existing evaluator returns `Assessed`:

```text
NetBackCostBasisPricePosition(
  floorDelta,
  observation,
  assessment
)
```

Internal construction requires exact reproduction of:

```text
EconomicPricePositionResult.Assessed(assessment)
```

from the retained derived floor and observation. The aggregate renders
`[REDACTED]`.

The assessment retains the exact organization, derived scenario, observation
identity, marketplace, currency, quantum, gross price, derived floors, signed
gaps, position, quality, floor policies, source provenance, and occurrence
time produced by the existing evaluator.

The surrounding floor delta retains the source profile/floor, selected Product
Cost evidence, derived profile/floor, and exact floor changes unchanged.

## Controlled result

```text
sealed interface NetBackCostBasisPricePositionResult

Assessed(evaluation: NetBackCostBasisPricePosition)
OwnershipMismatch
CurrencyMismatch
PriceQuantumMismatch
```

Each mismatch maps exactly from the existing evaluator, returns no partial
assessment, and renders `[REDACTED]`.

## Existing evaluator semantics

TASK-0123 inherits without modification:

- organization and derived-scenario ownership checks;
- exact currency equality;
- exact price-quantum alignment without rounding;
- absolute and economic gap formulas;
- four accepted economic price positions and their precedence;
- confirmed/estimated quality propagation.

The new projection defines no duplicate enum or helper for these concerns.

## Accepted fixture

Given:

```text
source Product Cost = 143.20
selected Product Cost = 48.00
source floors = 143.20
derived floors = 48.00
floor deltas = -95.20
derived-scenario observed gross price = 100.00
all evidence confirmed
```

The successful assessment is:

```text
absolute floor gap = 52.00
economic floor gap = 52.00
position = ABOVE_ECONOMIC_FLOOR
quality = CONFIRMED
scenario = derived scenario
```

The source floor remains `143.20` and is not evaluated against the observation.
No preferred cost basis or price recommendation is produced.

## Controlled failures

- source-scenario or foreign-organization observation returns
  `OwnershipMismatch`;
- different currency returns `CurrencyMismatch`;
- a gross price not aligned to the derived floor quantum returns
  `PriceQuantumMismatch`.

No failure rewrites the observation or returns a partial lineage aggregate.

## Determinism and immutability

- value-equal inputs return value-equal output;
- no ID, time, version, source, or policy is generated;
- floor delta, both floors, profiles, evidence, and observation remain
  unchanged;
- exact evaluator output is retained without translation.

## Implementation scope

TASK-0123 may add only:

- `MarketplaceNetBackCostBasisPricePosition.kt` in Marketplace pricing;
- `MarketplaceNetBackCostBasisPricePositionTest.kt`;
- TASK-0123 evidence.

No existing production type needs modification.

## Test plan

TASK-0123 proves at least:

1. projection bytecode references no Kernel type;
2. public inputs are only complete floor delta and observed price;
3. accepted fixture returns the exact existing assessment;
4. successful output retains the same floor delta and observation instances;
5. source floor and `-95.20` deltas remain unchanged and are not evaluated;
6. derived floor, scenario, gaps, position, policies, source, time, and quality
   remain exact;
7. below-absolute, below-economic, at-economic, and above-economic positions
   map without change;
8. estimated quality maps without change;
9. ownership, currency, and quantum mismatches map one for one;
10. source-scenario observation is rejected rather than rebound;
11. internal aggregate construction rejects a mismatched assessment;
12. value-equal inputs are deterministic and inputs remain unchanged;
13. all new renderings are `[REDACTED]`;
14. no source-floor assessment, position comparison, preferred basis,
    recommendation, or action is produced;
15. no API, persistence, runtime, connector, event, or AI is added;
16. no file under `platform/foundation/kernel` changes;
17. `git diff --check` and complete repository build remain green.

## Remaining boundary

Observation rebinding or explicit cross-scenario evidence, source/derived
position comparison, percentage or materiality, economic objective, market
competitiveness, price simulation, preferred cost basis, recommendation,
authority, execution, outcome, persistence, API/UI, quantity/kit conversion,
and multiple Product Cost allocation require later accepted specifications.

## Acceptance

Merging ADR-0035 and SPEC-0035 authorizes TASK-0123 only. It changes no runtime
behavior and authorizes no recommendation, decision, action, AI, or Kernel
modification.
