# SPEC-0033: Marketplace Net-Back Source Scenario Floor

Status: Proposed

Date: 2026-08-15

Source decision: ADR-0033

## Objective

Calculate the accepted Net-Back result of the exact source profile retained by
one complete applied-scenario floor, preserving the entire source-to-derived
lineage without comparing the two floors.

## Authorized next implementation

Acceptance authorizes TASK-0119 only:

1. add one pure projection in the Marketplace pricing package;
2. accept one complete `NetBackAppliedScenarioFloor`;
3. invoke the accepted calculator against its exact source profile;
4. map Complete, Incomplete, and Unachievable without loss;
5. retain the complete applied-scenario floor in every result family;
6. reproduce all new aggregate invariants internally;
7. render all new aggregates as `[REDACTED]`;
8. prove behavior with focused tests and repository verification.

No formula, policy, delta, comparison, objective, recommendation, decision,
authority, action, persistence, API, connector, AI, or Kernel change is
authorized.

## Input and calculation

```text
MarketplaceNetBackSourceScenarioFloor.calculate(
  appliedScenarioFloor: NetBackAppliedScenarioFloor
)
```

The implementation invokes exactly:

```text
val sourceCalculation = MarketplaceNetBackEconomicFloor.calculate(
  appliedScenarioFloor.appliedScenario.sourceProfile
)
```

It must not calculate another derived floor, construct or mutate a profile,
replace Product Cost evidence, alter coverage or target, or change any policy
version.

There is no caller ID, time, clock, random source, policy input, database,
network, or framework dependency.

## Complete result

When the source calculation is Complete:

```text
NetBackSourceScenarioFloorResult.Calculated(
  NetBackSourceScenarioFloor(
    appliedScenarioFloor,
    sourceCalculation.floor
  )
)
```

The internal aggregate verifies exact reproduction from the retained source
profile. It renders `[REDACTED]`.

The source floor retains the source profile's exact organization, source
scenario, marketplace, currency, normalized unit, quantum, normalization
policy, target, components, and truth quality. Calculation policy comes only
from the accepted calculator.

The already-derived floor remains unchanged inside `appliedScenarioFloor`.

## Incomplete result

When the source calculation is Incomplete:

```text
NetBackSourceScenarioFloorResult.Incomplete(
  appliedScenarioFloor,
  sourceCalculation
)
```

Missing types, partial types, supplied source components, normalized unit,
normalization policy, and calculation policy remain exact. No source floor or
fallback is exposed. Internal construction verifies exact reproduction.

This branch is retained even though current application invariants make it
normally unreachable after a complete derived floor. The projection must map
the generic calculator exhaustively and fail closed if upstream contracts
evolve.

## Unachievable result

When the source calculation is Unachievable:

```text
NetBackSourceScenarioFloorResult.Unachievable(
  appliedScenarioFloor,
  sourceCalculation
)
```

Reason, unit, normalization policy, and calculation policy remain exact. No
alternate formula or fallback is introduced. Internal construction verifies
exact reproduction.

## Controlled output

```text
sealed interface NetBackSourceScenarioFloorResult

Calculated(evaluation: NetBackSourceScenarioFloor)
Incomplete(
  appliedScenarioFloor: NetBackAppliedScenarioFloor,
  calculation: NetBackCalculationResult.Incomplete
)
Unachievable(
  appliedScenarioFloor: NetBackAppliedScenarioFloor,
  calculation: NetBackCalculationResult.Unachievable
)
```

All new aggregates and result variants render `[REDACTED]`.

## Accepted fixture

Given:

```text
source Product Cost = 143.20
derived Product Cost = 48.00
all other cost types = NOT_APPLICABLE
contribution target = 0
price quantum = 0.01
```

The input already contains derived floors of `48.00`. The new source result is:

```text
source absolute floor = 143.20
source economic floor = 143.20
scenario = source scenario
unit = shared normalized unit
```

No delta is produced. Both profiles and the derived floor remain unchanged.

## Source-only unachievability

A source profile can exceed accepted representable floor bounds while a
lower-cost derived profile remains calculable. In this case the projection
returns the exact source Unachievable reason and retains the complete derived
floor. It must not discard the derived calculation or clamp the source.

## Determinism and immutability

- value-equal complete applied-scenario floors return value-equal results;
- no ID, time, version, or source is generated;
- both profiles, selection, evidence, components, and derived floor remain
  unchanged;
- calculator policy and mathematics remain owned by the accepted calculator.

## Implementation scope

TASK-0119 may add only:

- `MarketplaceNetBackSourceScenarioFloor.kt` in Marketplace pricing;
- `MarketplaceNetBackSourceScenarioFloorTest.kt`;
- TASK-0119 evidence.

No existing production type needs modification.

## Test plan

TASK-0119 proves at least:

1. projection bytecode references no Kernel type;
2. public input is only a complete applied-scenario floor;
3. accepted fixture calculates source floors as `143.20`;
4. source floor uses source scenario ownership;
5. source unit, quantum, policies, target, components, and quality match the
   generic calculator exactly;
6. derived floor remains `48.00` and unchanged;
7. source and derived Product Cost evidence remain distinct and unchanged;
8. no delta, ratio, direction, comparison, or recommendation is produced;
9. source-only out-of-range calculation maps to exact Unachievable output;
10. every wrapper retains the same complete applied-scenario floor;
11. internal complete, incomplete, and unachievable construction rejects a
    mismatched generic calculation;
12. permutations and value-equal inputs produce value-equal output;
13. inputs remain unchanged;
14. all new renderings are `[REDACTED]`;
15. no API, persistence, runtime, connector, event, or AI is added;
16. no file under `platform/foundation/kernel` changes;
17. `git diff --check` and complete repository build remain green.

## Remaining boundary

Exact source/derived floor deltas, comparison classification, percentage,
economic objective, price feasibility, simulation, preferred basis,
recommendation, authority, execution, outcome, persistence, API/UI,
quantity/kit conversion, and multiple Product Cost allocation require later
accepted specifications.

## Acceptance

Merging ADR-0033 and SPEC-0033 authorizes TASK-0119 only. It changes no runtime
behavior and authorizes no delta, comparison, recommendation, decision, action,
AI, or Kernel modification.
