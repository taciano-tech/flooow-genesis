# TASK-0076 Canonical Inventory Observation Contract Review

**Date:** 2026-08-12

## Result

**PROPOSED - ready for architecture, data, security, inventory, and integration
review.**

ADR-0014 and SPEC-0014 define the immutable, exact boundary that can transform
committed provider inventory evidence and an accepted identity mapping into a
canonical Genesis observation without declaring business stock.

## Repository evidence

- V006 preserves exact source item, location, SKU, unit, timestamp, version, and
  five independent signed decimal measures per organization and connection;
- V007 resolves an exact source selector to organization-owned item, optional
  location, unit, and a reduced positive rational factor;
- V007 deliberately stores that factor as metadata and never applies it;
- current Marketplace Operations `InventorySnapshot` and `InventoryRiskInput`
  accept one whole, non-negative availability value and cannot reproduce source
  or mapping provenance;
- no canonical observation, exact converted quantity, projection revision,
  current-state selection, aggregation, or reconciliation exists;
- production startup registers no source projector, mapping resolver, adapter,
  scheduler, or worker.

## Current provider and storage evidence

- Mercado Livre documents independently managed quantities for fulfillment,
  selling-address, and seller-warehouse stock locations;
- Mercado Livre multi-origin inventory moves quantity management away from one
  item-level `available_quantity` into User Product stock locations;
- Mercado Livre `x-version` is concurrency state and not product identity;
- Omie's official inventory summary exposes decimal physical, reserved,
  predicted outbound, predicted inbound, and available values separately per
  product and stock location;
- Omie's official inventory query exposes physical, reserved, pending, and
  balance fields without defining one universal Genesis availability equation;
- Omie stock locations have independent identifiers, ownership types, and
  operational flags;
- PostgreSQL documents fixed-scale `numeric(p,s)` coercion and rounding when a
  value has more fractional digits than the declared scale;
- PostgreSQL documents floating-point types as inexact and `numeric` as exact
  where the operation permits it;
- integer/rational persistence avoids rounding for factors such as `1/3`.

## Decision evidence

- projection consumes one exact V006 record and one active exact V007 decision;
- source identifiers are not copied into the canonical observation;
- all five nullable measures are converted independently with integer
  multiplication and GCD reduction;
- null, zero, decimal precision, and negative sign are preserved exactly;
- source-updated, source-commit, and projection clocks remain distinct;
- one source and mapping decision is idempotent, while a later mapping may
  append an immutable linked projection revision;
- no projection is marked current or authoritative;
- downstream winner selection, staleness, aggregation, rounding, and business
  availability remain explicit later contracts;
- TASK-0077 remains production-inactive and exposes no public administration or
  inventory route.

## Safety boundary

The proposal changes documentation only. It does not modify V006 or V007 data,
API/OpenAPI, Marketplace Operations, assessments, events, delivery, research,
Kernel behavior, credentials, or external systems.

## Local validation

```text
./gradlew :platform:foundation:kernel:test \
  :platform:foundation:organization-context:test \
  :applications:marketplace-operations:test \
  :applications:integration-control-plane:test \
  :applications:connector-runtime:test \
  :applications:inventory-source-ingestion:test \
  :applications:inventory-identity-mapping:test \
  :applications:marketplace-operations-persistence-postgres:compileTestKotlin \
  --no-daemon --console=plain
BUILD SUCCESSFUL
```

The seven executed test modules report 125 tests, 0 failures, 0 errors, and 0
skips. PostgreSQL production and test sources compile. The TASK-0076 pull request
must repeat the complete build and persistent runtime validation in GitHub CI
before merge.

## Authorization boundary

Acceptance authorizes only TASK-0077's pure exact-projection model, additive
V008 immutable ledger, transactional projector, scoped history, deterministic
tests, and production-inactive wiring. It authorizes no provider adapter,
automatic mapping, bulk projector, public endpoint, scheduler, worker,
current-state winner, aggregation, reconciliation, rounding, business
availability, inventory mutation, assessment, event, or external request.
