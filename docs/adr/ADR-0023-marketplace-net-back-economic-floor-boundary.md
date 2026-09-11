# ADR-0023: Marketplace Net-Back Economic Floor Boundary

Status: Proposed

Date: 2026-08-13

## Context

MKT-001 establishes exact economic truth, MKT-002 preserves immutable financial
evidence, and MKT-003 distinguishes pending, partial, divergent, and reconciled
facts. The first pricing capability can now be defined without pretending that
competitive price or AI is economic truth.

The next question is:

> Given complete normalized unit economics and an explicit contribution target,
> what is the lowest gross sale price that can satisfy that target?

Unsafe shortcuts would be to:

- subtract a percentage as if it were a fixed monetary amount;
- derive a floor from incomplete cost coverage;
- hide marketplace commission, tax, or rounding rules in provider code;
- round a mathematical floor down and silently violate the target;
- treat estimated economics as confirmed;
- assume a currency's minor unit from an incomplete registry;
- manufacture a recommended or competitive price from a cost floor;
- update a marketplace listing from a pure calculation;
- put SKU, marketplace, commission, tax, margin, or price vocabulary in the
  Kernel.

## Decision

Introduce a pure, production-inactive Marketplace Net-Back Economic Floor
domain in the existing `applications:marketplace-operations` module under:

```text
io.flooow.marketplace.operations.economics.pricing
```

The first slice calculates two boundaries:

```text
ABSOLUTE FLOOR = lowest price with zero contribution
ECONOMIC FLOOR = lowest price satisfying the explicit contribution target
```

It is a calculator, not a price recommendation, policy decision, experiment,
or action. No source in the package may import a Kernel type.

## Normalized pricing profile

One profile is owned by one organization and one caller-supplied scenario ID.
It freezes:

```text
organization
scenario ID
marketplace key
currency
price quantum
normalization policy version
cost components
coverage by economic cost type
contribution target
```

The profile deliberately has no SKU, listing, competitor, channel credential,
current price, order quantity, inventory, demand, Buy Box, promotion, Ads
campaign, or execution authority. Those concepts require later boundaries.

## Cost components

The profile reuses the controlled MKT-001 economic component types except
`REVENUE`:

```text
MARKETPLACE_COMMISSION
MARKETPLACE_FEE
SHIPPING
ADVERTISING
TAX
PRODUCT_COST
FINANCIAL_COST
OTHER_ADJUSTMENT
```

Each pricing component has exact organization/scenario ownership, a
caller-supplied ID, type, direction, source provenance, evidence quality, and
one controlled value:

```text
FIXED_AMOUNT(MarketplaceMoney)
REVENUE_RATE(NetBackRate)
```

`DEDUCTION` increases cost. `ADDITION` represents an explicit subsidy, credit,
or reversal and reduces cost. Magnitudes and rates are non-negative; sign is
never encoded twice.

Marketplace and ERP source facts retain stable external references. Present
source facts and component IDs are unique inside one profile. Aggregate
rendering remains redacted.

## Exact rates

`NetBackRate` is canonical decimal text from zero through one inclusive, with
at most eight decimal places. It is parsed directly to `BigDecimal`; exponent,
binary floating point, percent text, implicit division by 100, NaN, and infinity
are rejected.

Examples:

```text
0.14 = 14%
0.08102701 = 8.102701%
1 = 100%
```

The sum of deduction rates minus addition rates is the exact signed variable
deduction rate. A component rate may be at most one, while the derived net can
be negative or greater than one and is validated by the solvability rule.

## Coverage is mandatory

Every controlled non-revenue cost type is classified exactly once as:

```text
COMPLETE
NOT_APPLICABLE
PARTIAL
MISSING
```

The profile follows MKT-001 semantics: complete or partial requires at least
one component; not applicable or missing permits none. An explicit zero
component is present evidence. Absence is never assumed to be zero.

Any partial or missing type returns a typed incomplete result. No absolute or
economic floor is exposed from incomplete economics.

## Evidence quality

Components reuse `CONFIRMED` and `ESTIMATED`. A complete calculation is
`CONFIRMED` only when every supplied component is confirmed; otherwise it is
`ESTIMATED`.

This is data quality, not model or decision confidence. A later policy may
forbid action from estimated floors. This calculator has no action authority.

## Contribution targets

The first target is exactly one of:

```text
ABSOLUTE_AMOUNT(non-negative MarketplaceMoney)
MARGIN_RATE(NetBackRate strictly below one)
```

Absolute amount means minimum contribution currency per normalized commercial
unit. Margin rate means minimum contribution divided by gross price. Target
currency must match the profile when monetary.

Negative targets and a 100% margin target are rejected. There is no ROI, GMROI,
capital-day, elasticity, competitor, inventory, or strategic objective in this
slice.

## Net-back equations

The calculator first derives:

```text
F = sum(fixed deductions) - sum(fixed additions)
V = sum(rate deductions) - sum(rate additions)
```

The absolute floor solves:

```text
price * (1 - V) - F >= 0
```

The economic floor solves one target:

```text
absolute amount T:
price * (1 - V) - F >= T

margin rate M:
price * (1 - V) - F >= price * M
```

Therefore the mathematical denominators are:

```text
absolute or amount target: 1 - V
margin target:             1 - V - M
```

If a required denominator is not strictly positive, the target is typed as
`UNACHIEVABLE`. No division occurs.

When fixed additions make the numerator non-positive, the mathematical floor
is zero. The calculator never returns a negative sale-price floor.

## Conservative quantum rounding

The profile supplies an exact positive `priceQuantum` in its currency, such as
`0.01`. No ISO minor-unit registry or marketplace precision is inferred.

The final floor is the smallest non-negative multiple of the quantum that is
not below the mathematical solution. Calculation divides directly into integer
quantum units with `CEILING`; it does not create an intermediate rounded money
value. A floor is never rounded down.

If fixed-cost aggregation, target addition, or the resulting price reaches the
MKT-001 exclusive money bound, the result is typed
`UNACHIEVABLE(FLOOR_OUT_OF_RANGE)` rather than leaking an arithmetic exception.

## Result and explanation

A complete result exposes:

```text
organization and scenario ownership
marketplace and currency
normalization policy version
target
net fixed cost
net variable deduction rate
price quantum
absolute floor
economic floor
truth quality
canonical component provenance
calculation policy version
```

The economic floor is never below the absolute floor for an accepted
non-negative target. Repeated value-equal input produces value-equal output.

The words `recommended`, `optimal`, `competitive`, `dynamic`, `tactical`, and
`promotional` are intentionally absent from the result contract.

## No infrastructure activation or intelligence

This boundary adds no:

- migration, repository, ledger write, API, JSON, event, outbox, or UI;
- Mercado Livre, ERP, tax, freight, Ads, or price-update connector;
- scheduler, worker, webhook, experiment runtime, or startup wiring;
- current-price comparison, competitor matching, elasticity, KVI, Buy Box,
  inventory, promotion, portfolio, markdown, or clearance logic;
- recommendation, approval, decision, authority, action, rollback, outcome, AI,
  model, expert, or agent;
- Kernel change.

## Consequences

### Positive

- break-even and target floors use one exact explainable net-back equation;
- fixed money and revenue-linked rates cannot be confused;
- incomplete economics cannot manufacture a price boundary;
- upward quantum rounding protects the target;
- subsidies and reversals remain explicit;
- the MKT-001 fixture can be reversed from costs plus target contribution;
- later Pricing Intelligence receives a trustworthy lower boundary without
  receiving execution authority;
- Marketplace vocabulary remains outside the Kernel.

### Negative

- callers must normalize every rate, fixed amount, source, and coverage state;
- currency precision must be supplied explicitly;
- the first floor does not model tax brackets, tiered commissions, shipping
  bands, quantity, or price-dependent fixed fees;
- estimated evidence still requires later governance before action;
- a floor alone cannot say whether a product should be sold or at what market
  price.

## Alternatives considered

### Use the last order contribution as the floor

Rejected because a historical result is evidence, not the inverse function of
future fixed and revenue-linked economics.

### Treat all costs as fixed money

Rejected because commission, tax, advertising, and financial charges can be
proportional to gross price.

### Use a hard-coded BRL cent

Rejected because currency and marketplace precision are input policy, not a
universal truth demonstrated by this vertical.

### Round with HALF_EVEN

Rejected because rounding a minimum price down can violate its economic target.

### Return one recommended price

Rejected because a cost floor contains no demand, competitor, inventory,
elasticity, or organizational strategy evidence.

### Add price primitives to the Kernel

Rejected because only Marketplace Intelligence has demonstrated this need.

## Authorization

This ADR alone authorizes no implementation. SPEC-0023 may authorize only the
pure net-back profile, calculator, results, and focused tests for TASK-0096. It
authorizes no persistence, integration, price recommendation, price mutation,
decision, AI, or Kernel modification.
