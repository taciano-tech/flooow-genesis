# TASK-0138: MGI v0.7.6 and Genesis Convergence Audit

Status: Audit complete; convergence boundary accepted

Date: 2026-08-27

## Objective

Inspect repository reality before implementing a new MGI feature. Compare the
downloaded MGI v0.7.6 baseline with current Flooow Genesis, identify reuse,
duplication, gaps, regressions, and risks, then establish the smallest safe
convergence sequence.

No feature implementation was authorized by this audit task.

## Repository state inspected

- canonical repository: `tacianosteiner/flooow-genesis`;
- main before completion of the pending task: `dabbda8`;
- PR #132: clean, mergeable, and CI successful;
- PR #132 merged normally as `39993d3` during the audit;
- only open canonical PR at the start: #132;
- old remote task/PR branches were not treated as active roadmap authority;
- TASK-0137 remains the accepted pure freshness implementation from
  ADR-0040/SPEC-0040, but was not started.

## MGI artifact inspected

Archive:
`marketplace-growth-intelligence-v0.7.6.zip`

SHA-256:
`B1B9D9A77189CC9E21901BCA991AF5BB4FAFDACD6381D9A8D3001CE6AF8EE0F6`

Archive checks:

- 129 entries;
- no absolute or parent-traversal path;
- no `.mgi`, `.env`, `.venv`, token-, secret-, or credential-named entry;
- package version `0.7.6`;
- isolated extraction and runtime;
- no real local MGI data or credential file read or changed.

## Reproduced tests

### MGI v0.7.6

```text
194 passed in 1.20s
```

Tests ran from the extracted v0.7.6 source with `PYTHONPATH` pointed at the
isolated source, bytecode disabled, pytest cache disabled, and a separate empty
runtime directory.

### Genesis

Command:

```text
gradlew.bat build -x :applications:marketplace-operations-persistence-postgres:test --no-daemon
```

Result:

```text
BUILD SUCCESSFUL in 2m 9s
82 actionable tasks
371 tests, 0 failures, 0 errors, 0 skipped
```

The Postgres/Testcontainers test task was excluded from this local baseline;
its production and test classes still compiled. Canonical CI for PR #132 had
already passed before merge.

## Exact v0.7.5 to v0.7.6 change surface

After excluding runtime environments, caches, and packaging metadata:

- new: `MGI_V076_GUIDE.md`;
- new: `tests/test_parallel_evidence_v076.py`;
- changed: `pyproject.toml`;
- changed: `README.md`;
- changed: `src/mgi/api.py`;
- changed: `src/mgi/economic_truth.py`;
- changed: `src/mgi/sales_intelligence.py`;
- changed: `tests/test_economic_truth_v074.py`;
- changed: `tests/test_engine.py`.

The functional delta is narrow: parallel shipment, fiscal, and Ads identity
enrichment, two additional Economic Truth stages, timeline visibility, version
updates, and four direct regression tests.

## Validated MGI behavior

The following behavior is accepted as a convergence baseline:

```text
ERP order identity       PENDING
shipment cost            KNOWN or MISSING independently
product COGS             KNOWN or MISSING independently
invoice identity         KNOWN or MISSING independently
tax total                KNOWN or MISSING independently
Ads item/ad-group ID     KNOWN or MISSING independently
Ads order allocation     MISSING until explicit policy
```

An authoritative shipment cost of zero remains known. A missing shipment cost
remains absent. Invoice identity may be established by exact fiscal evidence
without using tax as a substitute. Ads identity never proves Ads cost.

## Genesis capabilities to reuse

The audit found that Genesis already provides the canonical foundations needed
for convergence:

- `MarketplaceMoney` exact decimal values with currency and bounded scale;
- `EconomicComponentCoverage` with `COMPLETE`, `PARTIAL`, `MISSING`, and
  `NOT_APPLICABLE`;
- source kind, system key, external reference, occurrence time, and evidence
  quality;
- an incomplete Economic Truth result while any required component is missing
  or partial;
- append-only expected/actual financial ledger;
- financial reconciliation with explicit status and differences;
- organization context and isolation;
- connector runtime budgets, progress, commit, and failure contracts;
- Postgres persistence, serialization, and outbox patterns;
- canonical evidence, identity, acceptance, selection, adjudication, authority,
  and linked observation foundations for inventory;
- marketplace vertical ownership outside the Kernel.

## Genesis capabilities still missing

- provider-neutral independent marketplace economic evidence ingestion;
- monotonic evidence merge and correction semantics;
- concrete read-only Mercado Livre and Omie adapters;
- durable background enrichment orchestration;
- a fast organization-scoped Sales Intelligence projection;
- list, detail, timeline, freshness, and refresh-status APIs;
- an operational Sales Intelligence screen;
- a non-financial Ads identity observation separated from allocation;
- acceptance fixtures that preserve MGI v0.7.6 behavior.

## Audit findings requiring correction during convergence

### Evidence regression risk

`apply_independent_marketplace_shipping` writes a new evidence state before
returning on a missing value. A previously known amount can therefore coexist
with newly written missing metadata when this function is reused outside the
current skip guard.

`apply_independent_fiscal_ads_discovery` always writes tax and Ads identity
metadata. A later empty discovery can replace known metadata with missing while
leaving earlier economic values present.

This violates the stated no-rollback invariant. Genesis merge semantics must
make missing observation, provider failure, correction, and explicit
invalidation distinct operations.

### Missing orchestration regression tests

The four v0.7.6 tests exercise pure merge functions. They do not run the full
background enrichment path across repeated refreshes and partial provider
failure. Canonical acceptance tests must cover repeated delivery, empty later
responses, authoritative corrections, and restart/replay.

### Financial precision and aggregate semantics

MGI uses Python floats. Genesis exact decimal money must remain canonical.

MGI exposes a `known_direct_cost_total` of `0.0` when no direct cost component
is known. Component nulls are preserved, but the aggregate can be interpreted
as evidence of zero cost. Genesis must expose coverage beside any subtotal and
must not label an unknown subtotal as complete.

### Projection durability

MGI snapshot upsert and history append are separate SQLite transactions.
Projection/history divergence is possible on process failure. In-process
FastAPI background work and SQLite status metadata are also not durable job
coordination. Genesis must use append-only ingestion, atomic commit/outbox, and
replayable projections.

### History provenance

MGI history signatures include stage, state, value, source, and ready status,
but omit authoritative occurrence time and note. A provenance-only correction
may therefore not append history. Genesis must version all material provenance.

### Isolation and maintainability

MGI SQLite rows do not carry organization scope, and the API, orchestration,
and inline UI remain concentrated in a large `api.py`. These are prototype
constraints, not patterns to port.

## Decision

ADR-0041 is accepted. MGI v0.7.6 becomes a behavioral baseline and transitional
read-only operational reference. Genesis remains the canonical architecture.

The next implementation sequence begins with an independent economic evidence
contract and executable v0.7.6 acceptance scenarios. It does not begin with a
dashboard, live write, autonomous agent, or wholesale Python migration.

## Roadmap impact

- TASK-0137 remains valid and deferred, not cancelled;
- MGI P0 convergence temporarily takes priority because of the declared urgent
  business need;
- Inventory freshness must still be implemented before Inventory Confidence,
  current-state inventory selection, or Safe ATP;
- the existing Marketplace Intelligence, Trust, policy/listing compliance,
  pricing, financial trace, and reconciliation roadmaps remain authoritative;
- `docs/roadmap/MGI-GENESIS-CONVERGENCE.md` is the single convergence backlog.

## Next objective

Create the smallest accepted contract for organization-scoped independent
economic evidence and translate the v0.7.6 scenarios into provider-free Genesis
acceptance cases. Implementation must not yet add live providers, persistence,
UI, or autonomous action.
