# TASK-0074 Inventory Identity Mapping Contract Review

**Date:** 2026-08-12

## Result

**PROPOSED - ready for architecture, data, security, catalog, inventory, and
integration review.**

ADR-0013 and SPEC-0013 define the auditable boundary between immutable provider
inventory evidence and future canonical Genesis inventory observations.

## Repository evidence

- TASK-0073 durably preserves exact source item, location, SKU, unit, version,
  timestamp, and signed decimal measures per organization and connection;
- the source ledger deliberately contains no Genesis product, warehouse, unit,
  business availability, or mapping rule;
- current `SkuRef` is an unscoped Kernel string wrapper used by an experimental
  business workflow, not an organization-owned catalog identity;
- current `InventorySnapshot` and `InventoryRiskInput` accept whole,
  non-negative units and cannot reproduce decimal or negative source evidence;
- no canonical item, location, unit, mapping decision, mapping history, or
  source-to-canonical resolver exists in the repository;
- production startup has no connector, mapper, catalog service, or worker.

## Current provider evidence

- Mercado Livre documents multiple stock-location types for one User Product;
- Mercado Livre assigns different ownership and editability to fulfillment,
  selling-address, and seller-warehouse stock;
- multi-origin seller warehouses can carry store and network-node identities,
  while the stock `x-version` header represents concurrency state;
- the evolving User Products model distinguishes user product from legacy item,
  variation, and inventory identifiers;
- Omie presents physical, reserved, and available stock separately by local de
  estoque;
- Omie availability can include expected inbound and outbound movements;
- Omie permits operations where stock becomes negative when company policy
  allows it;
- units, kits, and components prevent a universal cross-product conversion.

## Decision evidence

- organization-owned UUID anchors prevent provider strings from becoming
  canonical identity by convention;
- an exact selector includes connection plus source item, location presence, and
  unit presence, so nulls never behave as wildcards;
- every mapping cites a real immutable source-ledger row;
- quantity conversion is recorded as a reduced rational decision but remains
  unapplied until a later canonical-observation contract;
- immutable revisions, retirement audit, and compare-and-set replacement preserve
  historical meaning and prevent lost updates;
- source SKU, GTIN, titles, and descriptions are excluded from automatic identity;
- resolution returns internal identities and decision provenance only, never a
  business snapshot or command;
- TASK-0075 remains production-inactive and exposes no public administration.

## Safety boundary

The proposal changes documentation only. It does not modify existing source
ledger data, API/OpenAPI, assessments, events, delivery, Marketplace Operations,
research, or Kernel behavior. It authorizes no provider traffic and no stock
mutation.

## Local validation

```text
./gradlew :platform:foundation:kernel:test \
  :platform:foundation:organization-context:test \
  :applications:marketplace-operations:test \
  :applications:marketplace-operations-api:test \
  :applications:integration-control-plane:test \
  :applications:connector-runtime:test \
  :applications:inventory-source-ingestion:test \
  :applications:marketplace-operations-persistence-postgres:compileTestKotlin \
  :research:experiments:exp-0003-harness:test \
  --no-daemon --console=plain
BUILD SUCCESSFUL in 31s
```

The full local build was also attempted twice. All 21 PostgreSQL tests stopped
at Testcontainers startup because this host currently exposes no Docker
environment; no test assertion or application task failed. The immediately
preceding TASK-0073 GitHub CI passed all 162 tests on the same `main` baseline.
The TASK-0074 pull request must repeat the complete build in GitHub CI before
merge.

## Authorization boundary

Acceptance authorizes only TASK-0075's pure identity/mapping module, additive
V007 registry, immutable repository/service/resolver, deterministic fakes, and
tests. It authorizes no automatic mapping, mapping UI/API, provider adapter,
quantity application, aggregation, inventory mutation, assessment, event,
worker, scheduler, or external request.
