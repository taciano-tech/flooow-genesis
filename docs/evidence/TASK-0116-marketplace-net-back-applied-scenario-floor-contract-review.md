# TASK-0116: Marketplace Net-Back Applied Scenario Floor Contract Review

Status: Contract proposed; no implementation delivered

Date: 2026-08-14

## Repository finding

Inspection of current `main` at `d7b6453` confirms that TASK-0114 already
constructs an immutable, unit-compatible derived Net-Back profile from one
explicit Product Cost Basis selection while preserving source profile,
selection, evidence, component, policy, and time lineage.

The accepted generic calculator can calculate that profile, but its standalone
result does not retain the application aggregate. The smallest missing
dependency before any source/derived comparison is therefore a pure
lineage-preserving projection of the already-derived scenario through the
existing calculator.

## Decision material

ADR-0032 and SPEC-0032 define:

- one applied scenario as the only input;
- exact reuse of `MarketplaceNetBackEconomicFloor`;
- Complete, Incomplete, and Unachievable mapping without loss;
- full application lineage in every result family;
- internal reproduction of result invariants;
- no new formula, policy, ID, time, or source;
- no source-profile floor or baseline comparison;
- no recommendation, authority, action, runtime, or Kernel change.

## Target-capability compatibility

The TASK-0115 enrichment remains directional only. This contract continues
deterministic Pricing Intelligence and does not jump to Retail Media, Digital
Shelf, Catalog, Product Launch, Commerce State, agents, or autonomous commerce.

It strengthens the evidence foundation those future capabilities will need by
keeping a replacement-cost scenario floor traceable to its exact evidence and
application policy.

## Authorization boundary

Acceptance authorizes TASK-0117 only: the pure applied-scenario floor
projection, focused tests, and evidence.

Source/derived comparison, deltas, objective, feasibility, recommendation,
authority, execution, persistence, API, UI, AI, and Kernel changes remain
outside the authorized scope.
