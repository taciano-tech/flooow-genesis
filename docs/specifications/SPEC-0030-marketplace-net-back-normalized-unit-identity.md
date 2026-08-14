# SPEC-0030: Marketplace Net-Back Normalized Unit Identity

Status: Proposed

Date: 2026-08-14

Source decision: ADR-0030

## Objective

Make the normalized commercial unit explicit on every Net-Back pricing profile
and calculation outcome without changing any Net-Back mathematics.

## Authorized next implementation

Acceptance authorizes TASK-0112 only:

1. require `PricingCostUnitKey` on `NetBackPricingProfile`;
2. retain that exact key on complete, incomplete, and unachievable results;
3. migrate every in-repository profile caller to an explicit unit;
4. include the key in profile value equality and hash equality;
5. pass the exact profile unit through every calculator result path;
6. prove all existing Net-Back numerical fixtures remain unchanged;
7. add focused unit-identity, redaction, equality, and regression tests;
8. keep persistence, APIs, connectors, and every other module behavior
   unchanged.

No unit conversion, quantity model, kit expansion, selected Product Cost
application, scenario cloning, floor comparison, recommendation, action, AI,
or Kernel change is authorized.

## Reused value

```text
PricingCostUnitKey
```

TASK-0108 already constrains the value to 1-100 lowercase letters, digits,
dots, or hyphens and `[REDACTED]` rendering. Its semantics are a normalized
commercial-unit identity established upstream.

The value is not renamed or duplicated. No broader Kernel value is introduced.

## Pricing profile

The constructor becomes:

```text
NetBackPricingProfile(
  organizationId,
  scenarioId,
  marketplace,
  currency,
  unitKey,
  priceQuantum,
  normalizationPolicyVersion,
  components,
  coverage,
  target
)
```

`unitKey` is mandatory. Existing ownership, currency, quantum, identity,
source-fact, coverage, ordering, immutability, and redaction rules remain
unchanged.

All components and the target are asserted by the caller to be normalized to
this unit. This slice performs no conversion or independent normalization.

Profile equality and hash equality include the unit key. Profiles that differ
only by unit are not value-equal even when every monetary value is equal.

## Complete result

`NetBackEconomicFloor` adds:

```text
unitKey: PricingCostUnitKey
```

The calculator passes the profile key unchanged through its internal
construction path. No result key is generated, normalized, mapped, or inferred.

## Incomplete result

`NetBackCalculationResult.Incomplete` adds:

```text
unitKey: PricingCostUnitKey
```

Missing and partial coverage still take precedence. The result retains no
floor, but now preserves the unit alongside normalization and calculation
policy versions.

## Unachievable result

`NetBackCalculationResult.Unachievable` adds:

```text
unitKey: PricingCostUnitKey
```

Every denominator or range failure retains the exact profile unit alongside
the existing reason and policy versions.

## Calculation invariance

Given any profile before and after the migration with the same economic input:

```text
netFixedCost                 unchanged
netVariableDeductionRate     unchanged
absoluteFloor                unchanged
economicFloor                unchanged
truthQuality                 unchanged
component order              unchanged
coverage interpretation      unchanged
```

`unitKey` participates in identity and lineage, never arithmetic.

## Accepted regression fixtures

With unit `each`, all SPEC-0023 fixtures remain exact:

```text
reverse MKT-001: absolute 235.09, economic 299.90
variable margin: absolute 125.00, economic 142.86
absolute target: economic 150.00
```

Existing economic-position and competitive-position tests use explicit `each`
and retain their previous classifications and gaps.

## Repository migration scope

TASK-0112 may modify only:

- `MarketplaceNetBackEconomicFloor.kt`;
- direct pricing tests that construct or assert Net-Back profiles/results;
- TASK-0112 evidence.

The known direct test callers are:

```text
MarketplaceNetBackEconomicFloorTest
MarketplaceEconomicPricePositionTest
MarketplaceCompetitivePricePositionTest
```

If another direct caller is discovered, it may receive only the required
explicit unit argument and corresponding invariant assertion.

## Test plan

TASK-0112 proves at least:

1. pricing bytecode still references no Kernel type;
2. malformed unit keys remain rejected and render redacted;
3. a profile requires and retains the exact caller unit key;
4. profile equality and hash calculation include the unit key;
5. complete floor retains the exact profile unit;
6. incomplete result retains the exact profile unit;
7. every unachievable reason retains the exact profile unit;
8. no unit value participates in arithmetic;
9. reverse MKT-001 fixture remains `235.09` and `299.90`;
10. variable-rate margin fixture remains `125.00` and `142.86`;
11. absolute-target fixture remains `150.00`;
12. quality, component order, coverage, and redaction remain unchanged;
13. economic and competitive position suites remain green;
14. all direct callers state a unit explicitly;
15. no conversion, Product Cost application, recommendation, or runtime change;
16. no file under `platform/foundation/kernel` changes;
17. `git diff --check` and complete repository build remain green.

## Remaining boundary

Unit conversion, quantity and kit economics, profile normalization evidence,
applying a selected Product Cost to a derived scenario, component identity
across scenarios, floor comparison, economic objectives, simulation,
recommendation, authority, execution, persistence, and API/UI require later
accepted specifications.

## Acceptance

Merging ADR-0030 and SPEC-0030 authorizes TASK-0112 only. It changes no runtime
behavior and authorizes no unit conversion, cost substitution, floor decision,
recommendation, action, AI, or Kernel modification.
