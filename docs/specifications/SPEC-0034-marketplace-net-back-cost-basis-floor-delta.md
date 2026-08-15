# SPEC-0034: Marketplace Net-Back Cost-Basis Floor Delta

Status: Proposed

Date: 2026-08-15

Source decision: ADR-0034

## Objective

Derive the exact signed monetary change between accepted source and derived
Net-Back floors while preserving their complete Product Cost application
lineage and adding no interpretation.

## Authorized next implementation

Acceptance authorizes TASK-0121 only:

1. add one pure projection in the Marketplace pricing package;
2. accept one complete `NetBackSourceScenarioFloor`;
3. subtract each source floor from its corresponding derived floor;
4. retain the complete source-scenario floor with both exact deltas;
5. reproduce all new aggregate invariants internally;
6. render every new type as `[REDACTED]`;
7. prove negative, zero, and positive differences with focused tests;
8. verify the complete repository without changing the Kernel.

No percentage, ratio, classification, tolerance, objective, recommendation,
decision, authority, action, persistence, API, connector, AI, or Kernel change
is authorized.

## Input and projection

```text
MarketplaceNetBackCostBasisFloorDelta.calculate(
  sourceScenarioFloor: NetBackSourceScenarioFloor
): NetBackCostBasisFloorDelta
```

The implementation uses exactly:

```text
val derivedFloor = sourceScenarioFloor.appliedScenarioFloor.floor
val sourceFloor = sourceScenarioFloor.sourceFloor

absoluteFloorDelta =
  derivedFloor.absoluteFloor - sourceFloor.absoluteFloor

economicFloorDelta =
  derivedFloor.economicFloor - sourceFloor.economicFloor
```

It must not calculate another floor, reconstruct or mutate a profile, replace
Product Cost evidence, change coverage or target, or introduce a rounding,
normalization, comparison, or decision policy.

There is no caller ID, time, clock, random source, database, network, or
framework dependency.

## Complete aggregate

```text
NetBackCostBasisFloorDelta(
  sourceScenarioFloor,
  absoluteFloorDelta,
  economicFloorDelta
)
```

Internal construction verifies exact reproduction from the retained accepted
floors. It renders `[REDACTED]`.

Both deltas use the same currency as the source and derived floors. Existing
invariants already guarantee compatible normalized units, quantum, currency,
policies, target, and lineage; the new projection preserves those values
unchanged.

## Signed semantics

For either exact numeric delta:

```text
negative = derived numeric floor is lower than source numeric floor
zero     = numeric floors are equal
positive = derived numeric floor is higher than source numeric floor
```

These are arithmetic properties only. The implementation must expose no enum,
label, flag, category, severity, preference, or recommendation derived from the
sign.

## Accepted fixture

Given the accepted TASK-0119 fixture:

```text
source absolute floor = 143.20
source economic floor = 143.20
derived absolute floor = 48.00
derived economic floor = 48.00
currency = BRL
```

The result is:

```text
absolute floor delta = -95.20 BRL
economic floor delta = -95.20 BRL
```

Both accepted floors, profiles, components, selection evidence, unit identity,
policies, target, quality, and caller times remain unchanged.

## Distinct floor behavior

A fixture with variable cost rates or a non-zero contribution target must prove
that absolute and economic deltas are independently derived from their matching
floor fields. The implementation must not copy one delta into the other.

## Determinism and immutability

- value-equal source-scenario floors return value-equal deltas;
- no ID, time, version, source, or policy is generated;
- all retained inputs remain unchanged;
- subtraction uses exact `MarketplaceMoney` arithmetic;
- no operand is rounded again, clamped, converted, or made absolute.

## Implementation scope

TASK-0121 may add only:

- `MarketplaceNetBackCostBasisFloorDelta.kt` in Marketplace pricing;
- `MarketplaceNetBackCostBasisFloorDeltaTest.kt`;
- TASK-0121 evidence.

No existing production type needs modification.

## Test plan

TASK-0121 proves at least:

1. projection bytecode references no Kernel type;
2. public input is only a complete source-scenario floor;
3. accepted fixture returns exact `-95.20` deltas;
4. equal source and selected costs return exact zero deltas;
5. a higher selected cost returns exact positive deltas;
6. absolute and economic deltas are calculated independently;
7. source and derived floors remain exact and unchanged;
8. source and derived profiles and Product Cost evidence remain unchanged;
9. currency and normalized unit lineage remain exact;
10. no percentage, ratio, direction type, classification, tolerance,
    preference, or recommendation is produced;
11. internal construction rejects either mismatched delta;
12. permutations and value-equal inputs produce value-equal output;
13. inputs remain unchanged;
14. all new renderings are `[REDACTED]`;
15. no API, persistence, runtime, connector, event, or AI is added;
16. no file under `platform/foundation/kernel` changes;
17. `git diff --check` and complete repository build remain green.

## Remaining boundary

Direction classification, relative or percentage change, materiality,
tolerance, economic objective, price feasibility, simulation, preferred cost
basis, recommendation, authority, execution, outcome, persistence, API/UI,
quantity/kit conversion, and multiple Product Cost allocation require later
accepted specifications.

## Acceptance

Merging ADR-0034 and SPEC-0034 authorizes TASK-0121 only. It changes no runtime
behavior and authorizes no interpretation, recommendation, decision, action,
AI, or Kernel modification.
