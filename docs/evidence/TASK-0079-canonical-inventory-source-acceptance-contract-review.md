# TASK-0079 Canonical Inventory Source Acceptance Contract Review

**Date:** 2026-08-12

## Result

**PROPOSED - ready for architecture, data, security, inventory, and integration
review.**

ADR-0015 and SPEC-0015 define the smallest safe current-selection boundary after
V009: one explicit accepted head per exact source-mapping lineage, without
declaring global inventory truth.

## Repository evidence

- V006 orders immutable page commits by connection, capability, and input
  progress version;
- V007 keeps one active mapping revision per exact nullable source selector and
  retains a contiguous predecessor chain;
- V008 preserves source pointer, mapping revision, target, exact measures, and
  three distinct clocks;
- V009 proves that one accepted mapping applies to later evidence with the same
  selector rather than only its creation-evidence pointer;
- no table or service identifies one observation as accepted or current;
- no source-authority, staleness, aggregation, or reconciliation policy exists.

## Current provider evidence

- Mercado Livre maintains inventory independently by stock location and
  logistics owner;
- its `x-version` header is required to fence stock writes and a stale version
  returns conflict; the value is not cross-provider authority;
- Omie stock position is requested by date and stock location and exposes
  physical, reserved, pending, and balance measures;
- neither provider exposes a universal clock or sequence that can rank its
  evidence against another provider;
- PostgreSQL `READ COMMITTED` is statement-scoped, requiring locks and constraints
  for a stable multi-step acceptance decision.

## Decision evidence

- acceptance is scoped to the root and active leaf of one exact mapping lineage;
- later connector progress advances source evidence deterministically within one
  connection and capability;
- a same-pointer higher mapping and projection revision represents corrected
  interpretation, not new source evidence;
- record ordinals inside one page are not treated as temporal ordering;
- provider, commit, projection, mapping, and acceptance timestamps never select
  the winner;
- trusted principals, controlled reasons, CAS fencing, withdrawal, and immutable
  history make the decision explainable;
- multiple source heads for one canonical target remain deliberately unresolved.

## Safety boundary

The proposal changes documentation only. It does not modify V006 through V009,
runtime, API/OpenAPI, connectors, projection, Marketplace Operations,
assessments, events, delivery, credentials, or external systems.

## Authorization boundary

Acceptance authorizes only TASK-0080's pure source-acceptance model, additive
V010 ledger, transactional acceptance/replacement/withdrawal, exact head and
history reads, and deterministic tests. It authorizes no public route,
automatic acceptance, source ranking, staleness, aggregation, reconciliation,
business inventory mutation, assessment, event, provider call, or external
request.
