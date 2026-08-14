# TASK-0111 - Marketplace Net-Back normalized unit identity contract review

## Finding

The post-TASK-0110 inspection found that Product Cost Basis evidence names its
normalized commercial unit, while `NetBackPricingProfile` and its results do
not.

Currency equality alone cannot prove that a cost per piece is compatible with
fees, freight, and contribution targets normalized per kit, box, or bundle.

## Decision

ADR-0030 and SPEC-0030 define the smallest missing dependency before selected
Product Cost can be applied to Net-Back:

- require explicit `PricingCostUnitKey` on every profile;
- propagate it unchanged through complete, incomplete, and unachievable
  results;
- migrate every repository caller at compile time;
- preserve all existing mathematics and classifications.

## Why direct profile evolution

A parallel wrapper would preserve the ability to construct unitless profiles.
Making the field mandatory eliminates that unsafe state and makes later unit
mismatch a deterministic failure rather than a convention.

## Deliberate deferral

The contract does not:

- convert piece, box, kit, case, or bundle quantities;
- infer a unit from SKU, listing, normalization policy, or Product Cost;
- replace a `PRODUCT_COST` component;
- clone a pricing scenario;
- recalculate or compare a floor because of replacement cost;
- recommend, approve, or execute a price;
- add persistence, API, connector, AI, agent, or Kernel behavior.

## Sequence corrected

```text
Product Cost Basis evidence and selection
  -> explicit Net-Back normalized unit identity
  -> later unit-safe selected-cost scenario application
  -> later floor comparison and simulation
  -> later objective, recommendation, authority, action, and outcome
```

## Authorization

Acceptance authorizes TASK-0112 only: mandatory profile unit, exact result
propagation, direct caller migration, unchanged-mathematics regression tests,
and implementation evidence.

It authorizes no conversion, Product Cost application, recommendation,
runtime, infrastructure, AI, or Kernel modification.
