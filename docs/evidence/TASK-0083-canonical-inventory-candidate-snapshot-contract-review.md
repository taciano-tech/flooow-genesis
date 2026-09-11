# TASK-0083 Canonical Inventory Candidate Snapshot Contract Review

**Date:** 2026-08-13

## Result

**PROPOSED — ready for architecture, data, security, inventory, and integration
review.**

ADR-0017 and SPEC-0017 define the smallest safe boundary after V011: an explicit
immutable same-target evidence bundle that freezes the provenance of currently
resolved source candidates without ranking, comparing, reconciling, aggregating,
or declaring business stock.

## Product-direction analysis

The proposed Marketplace Intelligence direction is compatible with the Genesis
thesis when treated as application-level bounded contexts:

- Discover & Anticipate observes market evidence and proposes hypotheses;
- Source & Invest models purchasing, supplier, import, and capital decisions;
- Sell & Optimize models pricing, promotion, advertising, and portfolio choices;
- Control & Recover connects returns, reconciliation, recovery, and outcomes;
- the Decide loop applies evidence, constraints, policies, decisions, actions,
  outcomes, and learning already represented by the Genesis thesis.

Marketplace concepts such as GMROI, target FOB, true supplier cost, opportunity
score, Monte Carlo assumptions, reorder points, and capital allocation are not
Kernel primitives. They are versioned application policies and derived models
whose premises, evidence, uncertainty, decision, authorization, and outcome must
remain reproducible.

Recommendation remains separate from execution. Any future automated purchase,
price, promotion, inventory, or capital action requires explicit policy,
authority, approval, idempotency, safety limits, audit, and outcome
reconciliation.

## Repository evidence

- V006 preserves independent nullable provider measures without inventing one
  business quantity;
- V007 maps an exact source selector to canonical identities and rational unit
  conversion;
- V008 stores immutable exact signed-rational observations;
- V010 records one accepted head inside one exact source lineage;
- V011 explicitly selects one measure and resolves one provenance-preserving
  candidate per lineage;
- separate lineages can advance independently and no repository artifact freezes
  which exact multi-source set a later decision evaluated;
- the MVP `InventorySnapshot` and `InventoryRiskInput` still accept one whole,
  non-negative `availableUnits` value and cannot consume V011 candidates without
  losing provenance and exact semantics.

## Decision evidence

- explicit membership is separated from future source authority;
- a snapshot groups only candidates with the exact same canonical item, nullable
  location, and unit;
- null location is exact and never a wildcard;
- the caller supplies lineage roots only; controlled repositories load all
  selection, acceptance, observation, mapping, measure, and quantity facts;
- V012 stores frozen provenance references but no quantity;
- exact quantities remain in V008 and historical reads reconstruct them without
  arithmetic or fallback;
- request ID provides content-fenced idempotency;
- a shared unsigned UUID lineage lock order prevents torn capture and multi-root
  deadlocks;
- immutable snapshots have no active head, replacement, or retirement semantics.

## Kernel protection

No marketplace, inventory, supplier, financial, scoring, simulation, or capital
concept is promoted to the Kernel. The proposed module explicitly has no Kernel
dependency. ADR-0017 remains an application-level inventory integration
decision under the active knowledge-governance cycle.

## Safety boundary

The proposal changes documentation only. It does not modify V001 through V011,
Kernel, runtime, API/OpenAPI, connectors, providers, credentials, projection,
acceptance, selection, Marketplace Operations, assessments, events, delivery,
deployment, or external systems.

It defines no automatic discovery, source authority, rank, freshness, tolerance,
comparison, conflict, reconciliation outcome, aggregation, formula, rounding,
business stock, economic truth, recommendation, approval, or action.

## Authorization boundary

Acceptance authorizes only TASK-0084's pure candidate-snapshot model, additive
V012 reference ledger, transactional explicit capture, exact frozen read,
request replay, scoped lookup, and deterministic tests. All reconciliation and
Marketplace Intelligence policies remain future work requiring separate review.
