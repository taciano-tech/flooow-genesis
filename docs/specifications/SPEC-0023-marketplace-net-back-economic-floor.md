# SPEC-0023: Marketplace Net-Back Economic Floor

Status: Proposed

Date: 2026-08-13

Source decision: ADR-0023

## Objective

Calculate exact, explainable absolute and target-contribution sale-price floors
from complete normalized unit economics, while producing no price
recommendation, decision, or external action.

## Authorized next implementation

Acceptance authorizes TASK-0096 only:

1. add pure pricing contracts under
   `io.flooow.marketplace.operations.economics.pricing`;
2. reuse MKT-001 organization, marketplace, currency, money, direction, source,
   evidence-quality, economic-type, and coverage values where their semantics
   are unchanged;
3. add caller-supplied scenario/component identities, exact rates, fixed/rate
   cost values, complete profile, targets, controlled results, and calculator;
4. calculate signed fixed cost and variable deduction rate exactly;
5. solve absolute and economic floors with strict solvability checks and
   conservative quantum rounding;
6. preserve canonical provenance, deterministic equality, immutable
   collections, and redacted rendering;
7. prove incomplete, estimated, exact, variable-rate, subsidy, rounding,
   unachievable, privacy, and boundary behavior with pure tests;
8. leave persistence, startup, APIs, connectors, and every other module
   behavior unchanged.

No migration, repository, JDBC, HTTP, JSON, event, worker, clock, random source,
provider rule, recommendation, decision, action, AI, LLM, ML, or Kernel change
is authorized.

## Package boundary

```text
applications/marketplace-operations/src/main/kotlin/
  io/flooow/marketplace/operations/economics/pricing/
```

No source in this package may import `io.flooow.kernel`. A bytecode boundary
test must enforce the absence of Kernel references.

## Canonical values

```text
NetBackPricingScenarioId
NetBackCostComponentId
```

Each wraps a caller-supplied UUID, accepts only canonical lowercase UUID text,
has value equality, an internal persistence accessor for future adapters, and
`[INTERNAL]` rendering. No random ID is generated.

```text
NetBackNormalizationPolicyVersion
NetBackCalculationPolicyVersion
```

Versions match `[a-z0-9][a-z0-9./-]{0,99}`, have value equality, and render
`[REDACTED]`. The calculator freezes
`marketplace-net-back-economic-floor/1` as its first calculation version.

## Exact rate

```text
NetBackRate
```

`parse(canonicalDecimal)` accepts only numeric values from zero through one
inclusive with at most eight fraction digits. Canonical decimal input has no
sign, exponent, percent character, leading zero ambiguity, or binary floating
point. Numeric equality and hash equality ignore insignificant trailing zeros.

`NetBackSignedRate` is an internal-result value for exact derived rates. It
supports scale at most eight and may be negative or greater than one. It cannot
be supplied as a component magnitude or contribution target.

Both values render `[REDACTED]`.

## Cost value and component

```text
NetBackCostValue
  FixedAmount(non-negative MarketplaceMoney)
  RevenueRate(NetBackRate)
```

```text
NetBackCostComponent(
  organizationId,
  scenarioId,
  componentId,
  economicType,
  direction,
  value,
  source,
  evidenceQuality
)
```

`economicType` reuses `EconomicComponentType`; `REVENUE` is rejected. Fixed
amount currency must later match the profile. Both directions are accepted.
Source shape reuses `EconomicSource` exactly. Aggregate rendering is redacted.

## Contribution target

```text
NetBackContributionTarget
  AbsoluteAmount(non-negative MarketplaceMoney)
  MarginRate(NetBackRate strictly less than one)
```

An absolute target must later match the profile currency. A zero amount or zero
margin is valid and makes the economic floor value-equal to the absolute floor.

## Pricing profile

```text
NetBackPricingProfile(
  organizationId,
  scenarioId,
  marketplace,
  currency,
  priceQuantum,
  normalizationPolicyVersion,
  components,
  coverage,
  target
)
```

Construction requires:

1. positive price quantum in profile currency;
2. every component has exact organization and scenario ownership;
3. every fixed amount and absolute target uses profile currency;
4. component IDs are unique;
5. present source facts are unique by source kind, system key, external
   reference, economic type, and fixed/rate value kind;
6. coverage keys are exactly every `EconomicComponentType` except `REVENUE`;
7. `COMPLETE` or `PARTIAL` has at least one component of that type;
8. `NOT_APPLICABLE` or `MISSING` has no component of that type;
9. components are stored by unsigned component UUID;
10. coverage is stored in economic-type enum order;
11. caller collections are copied and exposed as unmodifiable values.

The profile has value equality and `[REDACTED]` rendering.

## Duplicate provenance

A present source fact key is unique within one profile by:

```text
source kind
source system key
external reference
economic component type
value kind (FIXED_AMOUNT or REVENUE_RATE)
```

The direction and magnitude are deliberately absent from the identity. Reusing
the same source fact with a different amount or direction is still a duplicate
and fails closed. Fixed and rate facts may cite the same source reference only
when the source explicitly supplies both distinct facts.

## Controlled results

```text
NetBackCalculationResult
  Complete(NetBackEconomicFloor)
  Incomplete(missingTypes, partialTypes, suppliedComponents,
             normalizationPolicyVersion, calculationPolicyVersion)
  Unachievable(reason, normalizationPolicyVersion,
               calculationPolicyVersion)
```

Reasons are:

```text
NON_POSITIVE_ABSOLUTE_DENOMINATOR
NON_POSITIVE_ECONOMIC_DENOMINATOR
FLOOR_OUT_OF_RANGE
```

Incomplete takes precedence over mathematical solvability. It exposes no
floor, total, rate, denominator, or target calculation.

## Calculation

For complete coverage, calculate:

```text
fixed deductions = sum fixed DEDUCTION
fixed additions  = sum fixed ADDITION
F = fixed deductions - fixed additions

rate deductions = sum rate DEDUCTION
rate additions  = sum rate ADDITION
V = rate deductions - rate additions
```

Rates use exact scale-eight-or-less decimals. Money uses MKT-001 exact values.

The absolute denominator is `1 - V`. If it is not positive, return
`NON_POSITIVE_ABSOLUTE_DENOMINATOR`.

The economic numerator and denominator are:

```text
AbsoluteAmount(T): numerator = F + T, denominator = 1 - V
MarginRate(M):     numerator = F,     denominator = 1 - V - M
```

If the economic denominator is not positive, return
`NON_POSITIVE_ECONOMIC_DENOMINATOR`.

Each numerator is clamped to at least zero only for price-floor solving. The
reported `netFixedCost` remains signed and is not clamped.

## Quantum ceiling

For numerator `N`, denominator `D`, and positive quantum `Q`:

```text
units = ceiling(N / (D * Q))
floor = units * Q
```

The division rounds directly to a scale-zero integer with `CEILING`. The
calculator does not first round `N / D` to a money scale. A zero numerator
produces zero units and a zero floor.

Fixed aggregation, target addition, and resulting floors must be representable
by `MarketplaceMoney`; otherwise return `FLOOR_OUT_OF_RANGE`. The economic floor
must not be less than the absolute floor; any internal contradiction fails
closed without a partial result.

## Complete output

```text
NetBackEconomicFloor(
  organizationId,
  scenarioId,
  marketplace,
  currency,
  priceQuantum,
  normalizationPolicyVersion,
  calculationPolicyVersion,
  target,
  netFixedCost,
  netVariableDeductionRate,
  absoluteFloor,
  economicFloor,
  truthQuality,
  components
)
```

Truth quality is `ESTIMATED` when any component is estimated and `CONFIRMED`
otherwise. Output components preserve canonical provenance. Aggregate rendering
is `[REDACTED]`.

## Acceptance fixtures

### Reverse MKT-001 truth

All accepted MKT-001 costs are normalized as fixed deductions:

```text
commission       41.99
shipping         18.40
advertising       7.20
tax              24.30
product cost    143.20
----------------------
net fixed cost  235.09
```

With zero variable rate, quantum `0.01`, and absolute contribution target
`64.81`:

```text
absolute floor = 235.09
economic floor = 299.90
```

This reverses the first exact economic-truth fixture without declaring 299.90
recommended or optimal.

### Variable rate and margin

For fixed cost `100.00`, net variable deduction rate `0.20`, target margin
`0.10`, and quantum `0.01`:

```text
absolute floor = ceiling(100 / 0.80) = 125.00
economic floor = ceiling(100 / 0.70) = 142.86
```

### Absolute target

For fixed cost `100.00`, net variable deduction rate `0.20`, contribution
target `20.00`, and quantum `0.01`:

```text
economic floor = ceiling(120 / 0.80) = 150.00
```

### Subsidy

A fixed addition reduces `F`; a rate addition reduces `V`. Neither is stored as
a negative input magnitude.

## Test plan

TASK-0096 proves at least:

1. pricing bytecode references no Kernel type;
2. canonical IDs/versions/rates reject malformed values and render safely;
3. no `Float`, `Double`, clock, random, network, database, or framework type is
   used;
4. profile ownership, currency, positive quantum, type, duplicate ID, and
   source-fact rules;
5. all non-revenue types receive exact coverage and revenue is rejected;
6. coverage/component disagreement fails construction;
7. caller lists and maps are copied, immutable, and canonical;
8. missing or partial coverage exposes no floor;
9. fixed deductions/additions net exactly;
10. revenue-rate deductions/additions net exactly;
11. fixed and rate values cannot be confused;
12. zero target makes economic and absolute floor equal;
13. MKT-001 reverse fixture yields `235.09` and `299.90`;
14. variable-rate margin fixture yields `125.00` and `142.86`;
15. absolute-target fixture yields `150.00`;
16. quantum rounding is always upward and already aligned values remain equal;
17. non-positive denominators return exact controlled reasons;
18. negative net fixed cost clamps solved floor to zero but remains reported;
19. out-of-range floor returns a controlled result;
20. confirmed/estimated quality derives only from component evidence;
21. input order cannot change result or provenance order;
22. value-equal input returns value-equal output;
23. aggregate rendering and errors disclose no sensitive input;
24. no recommendation, optimal price, competitor, inventory, action, API,
    persistence, event, connector, worker, or runtime behavior is added;
25. no file under `platform/foundation/kernel` changes;
26. `git diff --check` and the complete repository build remain green.

## Remaining boundary

Tiered and conditional fee/tax/freight rules, quantity and kit economics,
historical rule persistence, reconciled-profile construction, policy registry,
current-price evaluation, competitor matching, price index, elasticity, KVI,
inventory-aware pricing, promotions, experiments, dynamic/tactical/promotional
floors, ceiling price, recommendation, approvals, execution, rollback, API/UI,
outcomes, and learning require later accepted specifications.

## Acceptance

Merging ADR-0023 and SPEC-0023 authorizes TASK-0096 only. It changes no runtime
behavior and authorizes no live pricing data, price recommendation, decision,
external price mutation, AI, or Kernel modification.
