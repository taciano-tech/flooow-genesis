# TASK-0126: Marketplace Net-Back Comparable Price Evidence Contract Review

Status: Contract proposed; no implementation delivered

Date: 2026-08-20

## Repository finding

Inspection of current `main` at `a2548ef` confirms that TASK-0123 evaluates one
derived-scenario observed gross price against the selected-cost Net-Back floor
while retaining the complete source-to-derived Product Cost and floor-delta
lineage.

The source floor is deliberately not evaluated because the existing
`ObservedMarketplacePrice` is scenario-owned. Reusing the derived observation
against the source floor would fail ownership; changing its scenario would
rewrite evidence. The smallest safe dependency is therefore explicit
cross-scenario evidence for the same caller-supplied source fact.

## Decision material

ADR-0036 and SPEC-0036 define:

- one complete floor delta plus two caller-supplied scenario-owned observations;
- exact equality of organization, observation ID, gross price, source,
  occurrence time, and evidence quality;
- source and derived scenario ownership retained separately;
- independent delegation to the accepted economic price-position evaluator;
- exact retention of both assessments and the complete cost-basis lineage;
- deterministic evidence/source/derived failure precedence;
- no generated, copied, repaired, rounded, or rebound evidence;
- no transition judgment, preferred basis, recommendation, or action;
- no infrastructure, AI, or Kernel change.

## Why the contract is intentionally narrow

The paired assessments answer only:

> Where does the same explicit observed-price fact sit against each accepted
> Net-Back floor?

They do not answer whether the selected Product Cost is commercially valid,
whether one position is better, whether the price is competitive, or whether a
price should be published. Those questions need separate objective, market,
inventory, simulation, policy, authority, and outcome contracts.

Keeping evidence pairing separate prevents a future recommendation layer from
manufacturing its own historical observation or competing price-position
mathematics.

## Current target-direction compatibility

This remains deterministic Pricing Intelligence built on Marketplace Economic
Truth and Financial Trace/Reconciliation foundations. It does not interrupt
the protected Trust / Operational Fulfillment roadmap and does not activate
Policy Compliance, Digital Shelf, Retail Media, Returns, Import, Foresight,
agents, or autonomous commerce.

The Kernel remains unaware of Marketplace, Product Cost, observed price, and
Net-Back concepts.

## Authorization boundary

Acceptance authorizes TASK-0127 only: add the pure comparable-price evidence
projection, focused tests, and implementation evidence in the existing
Marketplace pricing package.

Observation generation or rebinding, position-transition classification,
percentage, materiality, objective, preferred Product Cost basis,
recommendation, authority, execution, persistence, API, UI, AI, and Kernel
changes remain outside the authorized scope.
