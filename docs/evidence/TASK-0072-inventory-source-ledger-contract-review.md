# TASK-0072 Inventory Source Ledger Contract Review

**Date:** 2026-08-12

## Result

**PROPOSED - ready for architecture, data, security, inventory, and integration
review.**

ADR-0012 and SPEC-0012 define the first durable typed ingestion boundary after
the provider-neutral connector runtime, without changing the MVP's current
business inventory semantics.

## Repository evidence

- TASK-0071 can read and atomically consume one typed page but only has in-memory
  test committers and progress;
- no connector progress, page commit, source inventory ledger, or durable typed
  ingestion schema exists;
- the existing `InventorySnapshot` uses a Kernel-backed Genesis `SkuRef`, integer
  non-negative units, and an effective timestamp;
- `InventoryRiskInput` additionally assumes goals, units sold, daily velocity,
  expected replenishment, and whole non-negative available units;
- direct provider mapping would therefore invent product identity, unit,
  aggregation, time, and availability decisions;
- the current PostgreSQL module already owns V001 through V005 and the
  organization-scoped control-plane repository;
- production startup registers no connector, committer, protector, scheduler,
  or provider.

## Provider research evidence

- Mercado Livre stock may belong to different User Product locations and
  warehouse types with different ownership and editability;
- Mercado Livre inventory version and location identity are distinct from a
  seller SKU and cannot be collapsed into one global item quantity safely;
- Omie exposes product, stock location, physical, reserved, pending, balance,
  and other inventory fields independently;
- Omie documents operational negative-stock scenarios, so clamping source
  quantities to zero would corrupt evidence;
- source identifiers and unit codes need an explicit future mapping rather than
  convention-based reuse as Genesis identities.

## Decision evidence

- one explicit `inventory.source-balance.read` record preserves source item,
  optional location/SKU/unit/version, timestamps, and separate measures;
- quantities are signed fixed decimals and missing remains different from zero;
- arbitrary JSON and provider payloads remain outside persistence;
- the source ledger is append-only and cannot mutate inventory or trigger an
  assessment;
- page records and progress advance in one PostgreSQL transaction;
- progress is sealed with organization, connection, capability, and version as
  protection context, never stored in plaintext;
- TASK-0073 remains production-inactive and uses deterministic fakes only;
- source-to-Genesis identity, units, aggregation, and business availability are
  deferred to an auditable mapping contract.

## Validation

```text
./gradlew build --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 1m 22s
151 tests, 0 failures, 0 errors, 0 skipped
```

This task changes documentation only. Existing connector runtime, control plane,
API, persistence, delivery, Marketplace Operations, research, and Kernel
behavior remain unchanged.

## Authorization boundary

Acceptance authorizes only TASK-0073's pure typed record, progress-protector port,
V006 ledger, PostgreSQL committer, deterministic fakes, and tests. It authorizes
no real credential, key, provider, mapping, worker, external request, business
mutation, assessment, or event.
