# SPEC-0032: Marketplace Net-Back Applied Scenario Floor

Status: Proposed

Date: 2026-08-14

Source decision: ADR-0032

## Objective

Calculate the accepted Net-Back floor of one exact Product-Cost-applied derived
scenario while retaining its complete application and selection lineage.

## Authorized next implementation

Acceptance authorizes TASK-0117 only:

1. add one pure projection in the Marketplace pricing package;
2. accept one `NetBackCostBasisAppliedScenario`, not an arbitrary profile;
3. invoke the accepted calculator against its exact derived profile;
4. map Complete, Incomplete, and Unachievable without loss;
5. retain the applied scenario with every result family;
6. internally reproduce aggregate invariants;
7. render all new aggregates as `[REDACTED]`;
8. prove behavior with pure focused tests and repository verification.

No formula, policy, comparison, objective, recommendation, decision, authority,
action, persistence, API, connector, AI, or Kernel change is authorized.

## Input and calculation

```text
MarketplaceNetBackAppliedScenarioFloor.calculate(
  appliedScenario: NetBackCostBasisAppliedScenario
)
```

There is no caller ID, time, clock, random source, policy, database, network, or
framework dependency. The implementation invokes exactly:

```text
val calculation = MarketplaceNetBackEconomicFloor.calculate(
  appliedScenario.derivedProfile
)
```

It must not calculate the `sourceProfile`, construct another profile, replace
evidence, change coverage or target, or change any policy version.

## Complete result

When the generic result is Complete:

```text
NetBackAppliedScenarioFloorResult.Calculated(
  NetBackAppliedScenarioFloor(appliedScenario, calculation.floor)
)
```

The aggregate uses internal construction and verifies that recalculating the
exact derived profile reproduces `Complete(floor)`. It renders `[REDACTED]`.

The floor therefore retains the derived profile's organization, target
scenario, marketplace, currency, normalized unit, price quantum, normalization
policy, target, and canonical component list. Calculation policy and economics
come only from the accepted calculator.

## Incomplete result

When the generic result is Incomplete:

```text
NetBackAppliedScenarioFloorResult.Incomplete(
  appliedScenario,
  calculation
)
```

Missing types, partial types, supplied derived components, normalized unit,
normalization policy, and calculation policy remain exact. No floor, fallback,
or imputation is exposed. Internal construction reproduces the exact result.

## Unachievable result

When the generic result is Unachievable:

```text
NetBackAppliedScenarioFloorResult.Unachievable(
  appliedScenario,
  calculation
)
```

Reason, normalized unit, normalization policy, and calculation policy remain
exact. No alternate formula or fallback is introduced. Internal construction
reproduces the exact result.

## Controlled output

```text
sealed interface NetBackAppliedScenarioFloorResult

Calculated(evaluation: NetBackAppliedScenarioFloor)
Incomplete(
  appliedScenario: NetBackCostBasisAppliedScenario,
  calculation: NetBackCalculationResult.Incomplete
)
Unachievable(
  appliedScenario: NetBackCostBasisAppliedScenario,
  calculation: NetBackCalculationResult.Unachievable
)
```

Every new result and aggregate renders `[REDACTED]`.

## Accepted fixtures

For source Product Cost `143.20`, selected current replacement `48.00`, derived
Product Cost `48.00`, every other cost type NOT_APPLICABLE, zero contribution
target, and quantum `0.01`, both derived floors are `48.00` in the target
scenario and normalized unit. The source remains `143.20`; no source floor is
produced.

Selected zero Product Cost produces zero floors under the same conditions.

A derived profile with another cost type MISSING or PARTIAL returns the exact
generic Incomplete result. A non-positive accepted denominator returns the
exact generic Unachievable reason. Every family retains application lineage.

## Determinism and immutability

- value-equal applied scenarios return value-equal results;
- no ID, time, version, or source is generated;
- profiles, selection, evidence, and components remain unchanged;
- profile canonicalization remains owned by the accepted profile;
- policy and mathematics remain owned by the accepted calculator.

## Implementation scope

TASK-0117 may add only:

- `MarketplaceNetBackAppliedScenarioFloor.kt` in Marketplace pricing;
- `MarketplaceNetBackAppliedScenarioFloorTest.kt`;
- TASK-0117 evidence.

No existing production type needs modification.

## Test plan

TASK-0117 proves at least:

1. projection bytecode references no Kernel type;
2. input is the application aggregate, not an arbitrary profile;
3. accepted `143.20 -> 48.00` produces both floors as `48.00`;
4. ownership uses the target scenario;
5. unit, quantum, policies, target, components, and quality match the generic
   calculator output;
6. source profile and original component remain unchanged;
7. no source-profile floor is produced;
8. explicit zero produces a calculated zero floor;
9. MISSING and PARTIAL coverage map to exact Incomplete output;
10. a non-positive denominator maps to exact Unachievable output;
11. every wrapper retains the same application value;
12. internal construction reproduces exact calculation invariants;
13. permutations and value-equal inputs produce value-equal output;
14. inputs remain unchanged;
15. all new renderings are `[REDACTED]`;
16. no comparison, recommendation, API, persistence, runtime, or AI is added;
17. no file under `platform/foundation/kernel` changes;
18. `git diff --check` and complete repository build remain green.

## Remaining boundary

Source-profile floor calculation, source/derived comparison, deltas, objective,
price feasibility, simulation, preferred basis, recommendation, authority,
execution, outcome, persistence, API/UI, quantity/kit conversion, and multiple
Product Cost allocation require later accepted specifications.

## Acceptance

Merging ADR-0032 and SPEC-0032 authorizes TASK-0117 only. It changes no runtime
behavior and authorizes no comparison, recommendation, decision, action, AI,
or Kernel modification.
