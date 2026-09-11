# SPEC-0020: Marketplace Economic Truth

Status: Accepted

Date: 2026-08-13

## Objective

Define the smallest deterministic domain foundation that reconstructs and
explains the economic result of one normalized marketplace order without
changing Genesis, inventing missing values, or introducing infrastructure.

## Authorized next implementation

Acceptance authorizes TASK-0090 only:

1. add `io.flooow.marketplace.operations.economics` production code inside
   `applications:marketplace-operations`;
2. add canonical organization-owned order/component identities and bounded
   marketplace/source/external-reference values;
3. add exact currency-aware money, controlled component type/direction/quality,
   and complete coverage contracts;
4. add normalized order validation and duplicate protection;
5. add a pure deterministic calculator with complete/incomplete results,
   structured breakdown, policy version, exact provenance, truth quality, and
   typed margin;
6. add focused tests for every acceptance invariant;
7. leave all other modules, Kernel, runtime, API, persistence, connectors,
   research experiments, and deployment unchanged.

No new Gradle module, dependency, third-party money library, clock, random ID,
network, filesystem, environment, log framework, database, repository,
migration, HTTP/JSON, marketplace payload, dynamic fee rule, FX conversion,
event, Kernel conversion, confidence score, forecast, simulation,
recommendation, action, agent, LLM, or ML is authorized.

## Package boundary

```text
applications/marketplace-operations/src/main/kotlin/
  io/flooow/marketplace/operations/economics/
```

The implementation should remain cohesive and may group small values in a few
files. File count is not an objective.

No source in this package may import `io.flooow.kernel`.

## Canonical identities

```text
MarketplaceOrderId
EconomicComponentId
```

Both wrap canonical lowercase UUIDs, expose UUID only for controlled use, use
value equality, and render `[INTERNAL]`.

The caller supplies IDs. The domain creates no random identifier.

## Bounded references

```text
MarketplaceKey                 1..100 UTF-8 bytes
MarketplaceExternalOrderId     1..256 UTF-8 bytes
EconomicSourceSystemKey        1..100 UTF-8 bytes
EconomicExternalReference      1..256 UTF-8 bytes
EconomicCalculationPolicyVersion 1..64 UTF-8 bytes
```

Text is NFC normalized, already trimmed, contains no ISO control character, and
renders `[REDACTED]`. `MarketplaceKey` and `EconomicSourceSystemKey`
additionally match:

```text
[a-z0-9][a-z0-9.-]*
```

within their byte limit.

`EconomicCalculationPolicyVersion` matches
`[a-z0-9][a-z0-9./-]*` within its byte limit.

## Money

```text
MarketplaceMoney(currency, amount)
MarketplaceCurrency(code)
```

Currency is exactly three uppercase ASCII letters intended for ISO 4217 codes.
TASK-0090 does not claim registry validation and need not query the JVM or an
external ISO registry.

Canonical amount input matches:

```text
-?(0|[1-9][0-9]*)(\.[0-9]{1,6})?
```

It is parsed directly to `BigDecimal`, strips insignificant trailing zeroes,
canonicalizes negative zero to zero, has scale at most 6, and absolute value
less than `1000000000000000000`.

The value object supports signed calculated totals. `EconomicComponent`
requires its magnitude to be non-negative. Arithmetic rejects currency
disagreement and remains exact for addition/subtraction.

No `Double`, `Float`, implicit rounding, locale parsing, exponent form,
`MathContext`, or third-party money dependency is permitted.

## Marketplace order

```text
MarketplaceOrder(
  organizationId,
  id,
  marketplace,
  externalOrderId,
  occurredAt,
  currency,
  components,
  coverage
)
```

The order copies input collections and exposes immutable canonical lists/maps.
Components are ordered by unsigned UUID byte order so collection input order has
no calculation or rendering semantics.

`MarketplaceKey` is open; tests use `mercado-livre`, and no production enum
lists marketplaces.

## Economic source provenance

```text
EconomicSourceKind
  MARKETPLACE
  ERP
  MANUAL
  CALCULATED

EconomicExternalReferenceState
  Present(reference)
  Absent(INTERNAL_ORIGIN)

EconomicSource(
  kind,
  systemKey,
  externalReference
)
```

`MARKETPLACE` and `ERP` require `Present`. `MANUAL` and `CALCULATED` may use
`Present` or `Absent(INTERNAL_ORIGIN)`. If an external source cannot provide the
stable reference required for duplicate protection, no component is created and
the affected type must be declared `MISSING`.

The source is provenance only. It opens no connector or credential.

## Economic component

```text
EconomicComponentType
  REVENUE
  MARKETPLACE_COMMISSION
  MARKETPLACE_FEE
  SHIPPING
  ADVERTISING
  TAX
  PRODUCT_COST
  FINANCIAL_COST
  OTHER_ADJUSTMENT

EconomicDirection
  ADDITION
  DEDUCTION

EconomicEvidenceQuality
  CONFIRMED
  ESTIMATED
```

```text
EconomicComponent(
  organizationId,
  id,
  orderId,
  type,
  direction,
  magnitude,
  source,
  occurredAt,
  quality
)
```

The component contains no formula, marketplace payload, tax rule, fee rule,
confidence score, recommendation, or mutable state.

All types may use either direction. A direction describes the economic effect
of the exact fact and allows controlled reversal without a negative input
magnitude. The calculator does not infer direction from type.

## Coverage

```text
EconomicComponentCoverage
  COMPLETE
  NOT_APPLICABLE
  PARTIAL
  MISSING
```

The order supplies exactly one coverage entry for every component type.

Rules:

- `COMPLETE` requires one or more components of the type and declares the
  supplied set complete;
- `PARTIAL` requires one or more components of the type and declares known
  missing facts;
- `NOT_APPLICABLE` requires zero components of the type;
- `MISSING` requires zero components of the type;
- zero magnitude is still a complete present component;
- omitted coverage, extra keys, or disagreement fails construction.

`REVENUE` may be a complete explicit zero to test zero-revenue behavior. It may
not be `NOT_APPLICABLE`.

## Ownership, currency, and duplicates

Order construction rejects:

- any foreign organization or order ID;
- any component currency different from the order currency;
- duplicate component IDs;
- duplicate present source fact keys.

A present source fact key is:

```text
(source kind, source system key, external reference, component type)
```

An absent external reference has no source fact key; its internal component ID
still must be unique.

These checks occur before calculation and do not replace later ingestion or
persistence idempotency.

## Calculation results

```text
MarketplaceEconomicTruthCalculationResult
  Complete(result)
  Incomplete(
    organizationId,
    orderId,
    missingTypes,
    partialTypes,
    suppliedComponents,
    calculationPolicyVersion
  )
```

Incomplete rendering exposes no values. `missingTypes` and `partialTypes` use
controlled enum names and may be inspected structurally. `suppliedComponents`
preserve the exact canonical input objects in component-ID order, but no
contribution, margin, or economic total is created.

Complete result:

```text
MarketplaceEconomicResult(
  organizationId,
  orderId,
  marketplace,
  externalOrderId,
  orderOccurredAt,
  currency,
  grossRevenue,
  totalMarketplaceFees,
  totalShipping,
  totalAdvertising,
  totalTaxes,
  totalProductCost,
  totalFinancialCost,
  totalOtherAdjustments,
  contribution,
  contributionMargin,
  truthQuality,
  calculationPolicyVersion,
  components
)
```

`components` are the exact canonical input objects in component-ID order. No
identity, source, external reference, time, quality, direction, or amount is
discarded.

## Netting algorithm

For any component collection:

```text
netAddition(type) = additions(type) - deductions(type)
netDeduction(type) = deductions(type) - additions(type)
```

Breakdown:

```text
grossRevenue = netAddition(REVENUE)

totalMarketplaceFees =
  netDeduction(MARKETPLACE_COMMISSION) +
  netDeduction(MARKETPLACE_FEE)

totalShipping = netDeduction(SHIPPING)
totalAdvertising = netDeduction(ADVERTISING)
totalTaxes = netDeduction(TAX)
totalProductCost = netDeduction(PRODUCT_COST)
totalFinancialCost = netDeduction(FINANCIAL_COST)
totalOtherAdjustments = netDeduction(OTHER_ADJUSTMENT)

contribution =
  grossRevenue
  - totalMarketplaceFees
  - totalShipping
  - totalAdvertising
  - totalTaxes
  - totalProductCost
  - totalFinancialCost
  - totalOtherAdjustments
```

Every result money value uses the exact order currency.

## Contribution margin

```text
ContributionMargin
  Defined(decimalValue)
  Undefined(NON_POSITIVE_GROSS_REVENUE)
```

For positive gross revenue:

```text
contribution / grossRevenue
```

uses scale 8 and `RoundingMode.HALF_EVEN`. The decimal ratio is not a percent
string and has no UI formatting.

For zero or negative gross revenue, margin is typed undefined.

For the acceptance fixture:

```text
64.81 / 299.90 = 0.21610537
```

## Truth quality and calculation version

```text
MarketplaceEconomicTruthQuality
  CONFIRMED
  ESTIMATED
```

A complete result is `ESTIMATED` if any present component is estimated;
otherwise it is `CONFIRMED`.

TASK-0090 uses the explicit constant policy version:

```text
marketplace-economic-truth/1
```

The policy version is output metadata. No registry, dynamic rule lookup, or
persistence is authorized.

## Pure calculator

```text
MarketplaceEconomicTruthCalculator.calculate(order)
```

The calculator is stateless and deterministic. It creates no ID or timestamp.
The same value-equal order produces a value-equal result across independent
invocations.

## Rendering

Domain renderings expose no organization, order, marketplace, external
reference, system, component ID, currency, amount, direction, quality,
timestamp, total, margin, missing type, or policy value.

Controlled enums may retain their standard enum names when accessed explicitly;
aggregate object `toString` methods remain redacted.

## Acceptance fixture

One confirmed `mercado-livre` order in BRL contains:

```text
REVENUE / ADDITION                 299.90
MARKETPLACE_COMMISSION / DEDUCTION  41.99
SHIPPING / DEDUCTION                18.40
ADVERTISING / DEDUCTION              7.20
TAX / DEDUCTION                     24.30
PRODUCT_COST / DEDUCTION            143.20
```

`MARKETPLACE_FEE`, `FINANCIAL_COST`, and `OTHER_ADJUSTMENT` are explicitly
`NOT_APPLICABLE`; every supplied type is `COMPLETE`.

Expected:

```text
grossRevenue            299.90 BRL
totalMarketplaceFees     41.99 BRL
totalShipping             18.40 BRL
totalAdvertising           7.20 BRL
totalTaxes                24.30 BRL
totalProductCost         143.20 BRL
totalFinancialCost         0.00 BRL
totalOtherAdjustments      0.00 BRL
contribution              64.81 BRL
contributionMargin         0.21610537
truthQuality              CONFIRMED
```

## Test plan

TASK-0090 proves at least:

1. economics code remains in the existing marketplace module and imports no
   Kernel type;
2. canonical UUID, text, marketplace key, source key, and rendering behavior;
3. exact canonical decimal parsing, bounds, equality, negative-zero handling,
   and no binary floating point API;
4. mixed currencies are rejected;
5. cross-organization and cross-order components are rejected;
6. duplicate component IDs are rejected;
7. duplicate present source fact keys are rejected even with different internal
   IDs;
8. coverage must classify every type consistently;
9. absent advertising marked `NOT_APPLICABLE` contributes exact zero;
10. missing advertising returns `Incomplete`, not zero-cost truth;
11. partially received fees return `Incomplete` while preserving supplied
    provenance;
12. the confirmed acceptance fixture produces contribution `64.81 BRL` and
    margin `0.21610537` exactly;
13. zero revenue produces typed undefined margin without division;
14. negative contribution is preserved exactly;
15. additions and deductions net reversals without signed input magnitude;
16. multiple components of one type aggregate exactly;
17. estimated input derives estimated truth without a confidence score;
18. complete results preserve exact component objects and canonical order;
19. two independent calculations are value-equal and input order does not
    change the result;
20. no clock, random, connector, persistence, API, rule engine, AI, action, or
    Kernel integration is introduced;
21. complete repository tests/build and `git diff --check` remain green;
22. no file under `platform/foundation/kernel` changes.

## Remaining boundary

Normalization from Mercado Livre, Amazon, Shopee, ERP, Ads, tax, freight,
returns, settlements, and bank sources; ingestion idempotency; durable economic
ledger; calculation rule history; corrections; order lifecycle; allocation to
items; discounts; refunds; reconciliation; confidence scoring; APIs; dashboards;
profit analytics; pricing; simulations; recommendations; decisions; outcomes;
and learning require later accepted specifications.

## Acceptance

Merging ADR-0020 and SPEC-0020 authorizes TASK-0090 only. It changes no runtime
behavior and authorizes no Kernel modification, infrastructure, live data, or
economic action.
