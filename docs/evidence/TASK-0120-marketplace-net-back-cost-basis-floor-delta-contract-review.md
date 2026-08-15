# TASK-0120: Marketplace Net-Back Cost-Basis Floor Delta Contract Review

Status: Contract proposed; no implementation delivered

Date: 2026-08-15

## Repository finding

Inspection of current `main` at `a745fad` confirms that TASK-0119 calculates
the accepted source floor beside the already-complete derived floor while
retaining the entire Product Cost selection and application lineage.

No boundary currently derives their exact difference. Introducing comparison
classification in the same increment would combine an arithmetic fact with a
new judgment policy. The smallest safe dependency is therefore a pure signed
money projection for the absolute-floor and economic-floor deltas.

## Decision material

ADR-0034 and SPEC-0034 define:

- one complete source-scenario floor as the only input;
- derived-minus-source subtraction for each matching floor;
- exact signed `MarketplaceMoney` without new rounding or normalization;
- retention of the complete source-to-derived lineage;
- internal reproduction of both delta invariants;
- no new ID, time, source, version, or policy;
- no classification, percentage, materiality, preference, or recommendation;
- no infrastructure, AI, or Kernel change.

## Current target-direction compatibility

This remains deterministic Pricing Intelligence built on Marketplace Economic
Truth. It does not activate agents, autonomous commerce, Retail Media, Digital
Shelf, Catalog, Launch, or Commerce State capabilities.

The projection gives future pricing, simulation, and decision boundaries an
auditable scenario-change fact without letting a numeric sign become a business
judgment.

## Authorization boundary

Acceptance authorizes TASK-0121 only: add the exact cost-basis floor delta
projection, focused tests, and evidence.

Direction classification, percentage, objective, feasibility, recommendation,
authority, execution, persistence, API, UI, AI, and Kernel changes remain
outside the authorized scope.
