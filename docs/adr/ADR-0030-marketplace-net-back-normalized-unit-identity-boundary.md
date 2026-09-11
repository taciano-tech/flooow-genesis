# ADR-0030: Marketplace Net-Back Normalized Unit Identity Boundary

Status: Proposed

Date: 2026-08-14

## Context

Net-Back calculates deterministic floors from normalized unit economics, but
`NetBackPricingProfile` does not currently name the normalized commercial unit.
TASK-0108 and TASK-0110 make the Product Cost Basis unit explicit.

Applying a selected product cost to a profile without proving that both
represent the same unit could compare or combine incompatible economics:

```text
product cost per piece
fees per kit
freight per box
target contribution per sale bundle
```

Currency equality does not prove unit equality. A normalization policy version
describes rules but is not itself a unit identity.

## Decision

Require an explicit `PricingCostUnitKey` on every `NetBackPricingProfile` and
carry it through every Net-Back calculation result.

The existing value is reused because TASK-0108 defines it as a normalized
commercial-unit identity, not as a SKU, product-cost-only identifier, or unit
conversion formula.

No default or inferred unit is permitted.

## Profile meaning

The profile unit key states that all supplied fixed costs, contribution
targets, and rate economics were normalized upstream to one commercial unit.

Examples of valid opaque identities include:

```text
each
case-12
kit-standard
sale-bundle-v2
```

The key does not define quantity, dimensions, weight, package composition, SKU,
GTIN, listing, or conversion. Those remain upstream facts.

## Result propagation

The exact profile unit key must be retained by:

```text
NetBackEconomicFloor
NetBackCalculationResult.Incomplete
NetBackCalculationResult.Unachievable
```

A result must never require callers to infer the unit from the source profile
after calculation. Complete and non-complete outcomes remain independently
auditable.

## No mathematical change

Unit identity does not alter:

- fixed-cost netting;
- variable-rate netting;
- absolute or economic denominator;
- target contribution;
- quantum ceiling;
- truth-quality propagation;
- incomplete precedence;
- unachievable reasons.

All accepted Net-Back fixtures must remain numerically identical.

## No unit conversion

The boundary validates identity only. It does not convert piece to box, divide
case cost, expand kit composition, allocate freight, infer order quantity, or
round a converted amount.

If two values have different unit keys, a later application must fail rather
than convert or assume equivalence.

## Compatibility choice

The unit is added directly to the profile instead of wrapping the profile in a
parallel envelope. A wrapper would allow existing code to continue creating
unitless profiles and would preserve the unsafe state.

This is a deliberate compile-time migration of the pure Marketplace pricing
contract. Every in-repository caller must state its unit explicitly.

## No Product Cost application yet

This change only makes safe comparison possible. It does not apply a selected
cost, replace a `PRODUCT_COST` component, clone a scenario, recalculate a floor,
or compare old and new economics.

## No infrastructure or Kernel change

The boundary adds no persistence, migration, API, JSON, connector, event,
worker, scheduler, UI, AI, model, agent, or Kernel vocabulary.

## Consequences

### Positive

- unit economics become explicit rather than conventional;
- complete and failed Net-Back results retain their unit;
- later Product Cost application can fail closed on unit mismatch;
- all repository callers must acknowledge the normalized unit;
- no existing monetary calculation changes.

### Negative

- every profile constructor call must be migrated;
- the unit key still depends on upstream normalization truth;
- quantity, kit composition, and conversion remain unavailable.

## Alternatives considered

### Infer unit from normalization policy version

Rejected because one policy version may normalize multiple commercial units
and a version is not a unit identity.

### Infer unit from product-cost evidence

Rejected because a Net-Back profile can exist before Product Cost Basis and
must be internally explicit.

### Add an optional unit key

Rejected because optionality preserves the unsafe unitless state.

### Wrap existing profiles only when applying Product Cost

Rejected because ordinary floor results would remain ambiguous and callers
could bypass the wrapper.

### Add commercial-unit vocabulary to the Kernel

Rejected because the demonstrated semantics remain Marketplace Pricing
normalization vocabulary.

## Authorization

This ADR alone authorizes no implementation. SPEC-0030 may authorize only the
required unit-key field, propagation through controlled Net-Back results,
repository caller migration, unchanged calculation proofs, and focused tests
for TASK-0112.

It authorizes no conversion, Product Cost application, recommendation,
infrastructure, AI, or Kernel modification.
