# TASK-0081 Canonical Inventory Measure Selection Contract Review

**Date:** 2026-08-12

## Result

**PROPOSED — ready for architecture, data, security, inventory, and integration
review.**

ADR-0016 and SPEC-0016 define the smallest safe boundary after V010: one explicit
measure policy per exact source-mapping lineage, resolved against its active
accepted observation without formulas, fallback, reconciliation, or business
stock.

## Repository evidence

- V006 preserves five independent nullable source measures;
- V008 converts every present measure independently to an exact signed rational;
- V008 intentionally does not choose `availableToSell`, derive availability, or
  clamp negative values;
- V010 selects one accepted observation per exact mapping lineage but copies no
  quantities and declares no measure authoritative;
- the current MVP `InventorySnapshot` and `InventoryRiskInput` accept one whole,
  non-negative `availableUnits` value and cannot preserve integration provenance;
- no measure policy, fallback contract, reconciliation candidate, or business
  inventory bridge exists.

## Decision evidence

- measure selection is a reviewed policy, not an inference from field order;
- the vocabulary is closed to the five canonical V008 measures;
- the policy is scoped to one exact mapping lineage and anchored by an active
  V010 acceptance whose selected field is present;
- replacement is revisioned, audited, CAS-fenced, and controlled by reasons;
- resolution reads the current active acceptance and selected field exactly;
- missing remains missing, zero remains present zero, and negative/rational
  values remain exact;
- `AVAILABLE_TO_SELL` is a source measure name, not global business authority;
- multiple selected source candidates remain deliberately unreconciled.

## Safety boundary

The proposal changes documentation only. It does not modify V006 through V010,
runtime, API/OpenAPI, connectors, providers, projection, acceptance,
Marketplace Operations, assessments, events, delivery, credentials, deployment,
or external systems.

## Verification

- ADR/SPEC terminology matches the current domain and persistence names;
- every mutable decision requires trusted principal, controlled reason, history,
  and compare-and-set fencing;
- exact quantity storage remains exclusively in V008;
- no formula, fallback, aggregation, reconciliation, rounding, source rank,
  staleness threshold, or business availability is authorized;
- `git diff --check` and the complete repository build must pass before merge.

## Authorization boundary

Acceptance authorizes only TASK-0082's pure measure-selection model, additive
V011 ledger, transactional selection/replacement/withdrawal, exact current
resolver, scoped history, and deterministic tests. It authorizes no public route,
automatic selection, provider call, formula, fallback, source ranking,
staleness, aggregation, reconciliation, business inventory mutation, assessment,
event, worker, scheduler, or external request.
