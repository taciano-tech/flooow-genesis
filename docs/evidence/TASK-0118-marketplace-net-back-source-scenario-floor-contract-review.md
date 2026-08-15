# TASK-0118: Marketplace Net-Back Source Scenario Floor Contract Review

Status: Contract proposed; no implementation delivered

Date: 2026-08-15

## Repository finding

Inspection of current `main` at `2f9f5cd` confirms that TASK-0117 calculates a
complete derived-scenario floor while preserving the source profile and the
entire Product Cost application lineage.

The source profile has not yet been calculated in that lineage. Calculating
source and derived deltas immediately would combine a missing calculation step
with comparison semantics. The smallest safe dependency is therefore a pure
source-profile projection that retains the already-complete derived result and
derives no difference.

## Decision material

ADR-0033 and SPEC-0033 define:

- one complete applied-scenario floor as the only input;
- exact reuse of `MarketplaceNetBackEconomicFloor` on the retained source
  profile;
- Complete, Incomplete, and Unachievable mapping without loss;
- full source-to-derived lineage in every result family;
- internal reproduction of generic calculation invariants;
- no new formula, policy, ID, time, or source;
- no delta, classification, objective, recommendation, or action;
- no infrastructure, AI, or Kernel change.

## Current target-direction compatibility

This remains deterministic Pricing Intelligence. It does not accelerate Retail
Media, Digital Shelf, Catalog, Launch, Commerce State, agents, or autonomous
commerce from the TASK-0115 target enrichment.

The projection strengthens their future economic foundation by retaining both
original and selected-cost scenario calculations under one evidence lineage,
without interpreting which scenario is preferable.

## Authorization boundary

Acceptance authorizes TASK-0119 only: add the source-scenario floor projection,
focused tests, and evidence.

Floor deltas, comparison, objective, feasibility, recommendation, authority,
execution, persistence, API, UI, AI, and Kernel changes remain outside the
authorized scope.
