# TASK-0122: Marketplace Net-Back Cost-Basis Price Position Contract Review

Status: Contract proposed; no implementation delivered

Date: 2026-08-15

## Repository finding

Inspection of current `main` at `4fd06ec` confirms that TASK-0121 retains both
accepted Net-Back floors and their exact deltas under the complete selected
Product Cost lineage.

The existing `MarketplaceEconomicPricePosition` already owns exact observed
price gaps, diagnostic classification, mismatch handling, and quality
propagation. Recreating those semantics or adding a delta direction enum would
not advance the product. The smallest useful dependency is therefore a pure
projection that applies the existing evaluator to the retained derived floor
and keeps the result connected to the complete cost-basis lineage.

## Decision material

ADR-0035 and SPEC-0035 define:

- one complete cost-basis floor delta and one derived-scenario observation as
  the only inputs;
- exact reuse of `MarketplaceEconomicPricePosition` on the derived floor;
- assessed and controlled mismatch mapping without loss;
- successful retention of floor delta, observation, and exact assessment;
- internal reproduction of the accepted evaluator result;
- no observation rebinding, duplicate formula, ID, time, source, or policy;
- no source-floor assessment, recommendation, action, or authority;
- no infrastructure, AI, or Kernel change.

## Current target-direction compatibility

This advances deterministic Economics and Pricing by diagnosing whether a
selected-cost scenario is below break-even, below target, at target, or above
target at an observed gross price.

It does not claim optimal price, realized profitability, market
competitiveness, or objective fitness. It does not accelerate Commerce
Strategy, agents, automation, or autonomous commerce from the approved target
operating model.

## Authorization boundary

Acceptance authorizes TASK-0123 only: add the derived-scenario price-position
projection, focused tests, and evidence.

Evidence rebinding, source/derived position comparison, objective,
recommendation, authority, execution, persistence, API, UI, AI, and Kernel
changes remain outside the authorized scope.
