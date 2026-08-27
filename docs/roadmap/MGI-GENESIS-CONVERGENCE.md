# MGI and Flooow Genesis Convergence Roadmap

Status: Active

Baseline date: 2026-08-27

## Mission

Converge the validated Marketplace Growth Intelligence behavior into Flooow
Genesis without changing the Genesis DNA or preserving a parallel product
architecture.

The resulting vertical must support:

```text
observe
  -> accumulate evidence
  -> understand
  -> diagnose
  -> decide
  -> execute within authority
  -> measure
  -> reconcile
  -> learn
```

This roadmap is derived from repository reality. It supersedes no accepted
domain roadmap and creates no second sequence of marketplace epics.

## Verified baseline

### MGI v0.7.6

- archive SHA-256:
  `B1B9D9A77189CC9E21901BCA991AF5BB4FAFDACD6381D9A8D3001CE6AF8EE0F6`;
- package version: `0.7.6`;
- 129 archive entries, with no `.mgi`, `.env`, `.venv`, credential-named, or
  path-traversal entry;
- 194 tests passed in an isolated runtime;
- fast reads use a local SQLite projection;
- provider enrichment runs outside the page read;
- v0.7.6 adds independent shipment, invoice/tax, and Ads identity progression.

### Genesis

- canonical main: merge commit `39993d3` after PR #132;
- build successful with the local Postgres/Testcontainers test task excluded;
- 371 non-Postgres tests, zero failures, zero errors;
- Economic Truth, financial ledger, reconciliation, pricing foundations,
  organization context, connector runtime, Postgres/outbox, and inventory
  evidence foundations already exist;
- no concrete Mercado Livre or Omie adapter and no operational Sales
  Intelligence projection currently exist.

## Capability comparison

| Capability | MGI v0.7.6 | Genesis | Convergence decision |
| --- | --- | --- | --- |
| Exact financial arithmetic | Python float and rounding | Exact canonical decimal money | Reuse Genesis |
| Missing versus zero | Preserved per component | Explicit coverage model | Reuse and strengthen in Genesis |
| Independent evidence progression | Implemented for four families | Domain permits incomplete truth, but no ingestion merge contract | Build canonical observation/merge boundary |
| Economic Truth | Local operational model | Strong canonical domain | Reuse Genesis |
| Financial trace/reconciliation | Prototype capabilities | Append-only expected/actual ledger and reconciliation | Reuse Genesis |
| Organization isolation | Absent | Present | Mandatory in every new contract |
| Mercado Livre integration | Concrete, read-only | Absent | Add provider adapter after canonical ingestion |
| Omie integration | Concrete, read-only | Absent | Add provider adapter after canonical ingestion |
| Fast Sales Intelligence reads | SQLite list/detail projection | Absent | Build durable Genesis projection |
| Background refresh | In-process FastAPI task | Connector runtime foundations | Build durable bounded orchestration |
| Projection history | SQLite JSON history | Postgres/outbox foundations | Build replayable atomic projection |
| Ads identity | Present and separate from allocation | Not yet modeled | Add non-financial identity observation |
| UI | Inline operational HTML | No equivalent operational screen | Add after projection/API contract |
| Autonomous execution | Disabled | Future policy/authority direction | Keep disabled in this convergence phase |

## Prioritized convergence backlog

Priority is determined by business value, architectural dependency, and risk.

### P0.1 - Independent economic evidence contract

Define a provider-neutral, organization-scoped contract for independently
observed marketplace order, payment, shipment cost, product cost, invoice, tax,
and Ads identity evidence.

The contract must specify:

- stable order and source identity;
- exact canonical money;
- component coverage and authoritative zero;
- source occurrence and observation times;
- provenance and idempotency;
- monotonic merge rules that reject silent evidence regression;
- conflict and correction semantics;
- Ads identity without financial allocation;
- redaction and bounded rendering.

Gate: executable acceptance scenarios reproduce all v0.7.6 parallel evidence
states without a provider dependency.

### P0.2 - Durable append-only evidence ingestion

Implement the accepted contract with organization isolation, idempotency,
optimistic serialization, Postgres persistence, and outbox/replay evidence.

Gate: duplicate delivery is harmless; conflicting facts are explicit; a crash
cannot leave projection and history silently inconsistent.

### P0.3 - Fast Sales Intelligence projection

Build list and order-detail projections from committed evidence and canonical
economic results.

The read model must expose:

- order, pack, shipment, invoice, and item identities;
- component state and provenance;
- known operational net clearly separated from contribution and final profit;
- freshness and source-stage metadata;
- evidence timeline and pending requirements;
- projection lag and replay position.

Gate: list and detail execute without provider calls or full scans.

### P0.4 - Bounded read-only Mercado Livre adapter

Map official order, payment, shipment, shipment-cost, item, and Ads identity
facts into the accepted evidence contract.

Gate: provider failures do not erase committed evidence; zero shipment cost is
observed as known; Ads spend remains unallocated without policy.

### P0.5 - Bounded read-only Omie adapter

Map product CMC, exact invoice identity, and explicit tax-total evidence into
the accepted contract without requiring ERP sales-order identity first.

Gate: COGS and fiscal evidence can progress independently; ambiguous matches
remain unresolved; no ERP write exists.

### P0.6 - Operational API and controlled Sales Intelligence screen

Expose the durable projection through authenticated, organization-scoped list,
detail, refresh-status, and evidence-history APIs, then add the minimum useful
operator screen.

Gate: the screen is a projection consumer, not a provider orchestrator.

### P1 - Reconciliation and decision readiness

Connect committed observations to the existing financial trace and
reconciliation domains. Define decision readiness from required component
coverage, source confidence, freshness, and reconciliation status.

Gate: the system never labels Economic Truth complete while a required
component is missing or partial.

### P1 - Integration health and durable enrichment orchestration

Add bounded retries, leases, checkpoints, provider budgets, failure evidence,
and operator-visible health. Reuse connector runtime and control-plane
contracts.

Gate: work survives process restart and does not depend on in-process web
background tasks.

### P1 - MGI behavioral retirement gate

Run the canonical Genesis projection against the accepted MGI fixtures and a
controlled real evidence sample. Retire the local MGI authority only after
semantic parity and operational performance are demonstrated.

Gate: no local credential, runtime database, or silent data migration is
required.

### P2 - Growth and decision intelligence

Only after P0/P1 truth and projection gates:

- profitable Ads analysis;
- pricing and promotion decisions;
- inventory-aware growth recommendations;
- situations and diagnosis;
- outcome comparison and organizational learning.

Existing pricing, Trust, listing compliance, fulfillment, and operating-model
roadmaps remain authoritative for their domains.

## Accepted sequencing decision

The current repository sequence becomes:

```text
TASK-0136 freshness contract merged
  -> TASK-0137 freshness implementation remains accepted but temporarily deferred
  -> TASK-0138 MGI/Genesis audit and convergence decision
  -> independent economic evidence contract
  -> durable evidence ingestion
  -> fast Sales Intelligence projection
  -> read-only provider adapters
  -> operational API/UI
  -> reconciliation and decision readiness
  -> later intelligence and bounded action
```

The deferral of TASK-0137 does not invalidate ADR-0040 or SPEC-0040. It only
reflects the user's urgent MGI business priority. The freshness implementation
must resume before Inventory Confidence, current-state selection, or Safe ATP.

## Risks discovered in v0.7.6

1. Money uses binary floating point and local rounding rather than canonical
   decimal money.
2. A missing later refresh can overwrite evidence metadata with `MISSING` while
   leaving an earlier amount present, creating an inconsistent row.
3. Fiscal/Ads discovery can reset previously known identity metadata when a
   later response is empty.
4. The new background enrichment path has pure-function tests but no end-to-end
   integration test for provider failure and repeated refresh.
5. Snapshot upsert and Economic Truth history append use separate SQLite
   transactions.
6. History signatures omit authoritative occurrence time and notes, so some
   provenance changes do not create history.
7. In-process background tasks and SQLite metadata are not durable job leases.
8. JSON snapshot rows have no organization boundary or schema lineage.
9. `known_direct_cost_total` becomes `0.0` when no direct cost is known; the
   component nulls remain correct, but the aggregate can be misread as a known
   zero-cost total.
10. `api.py` remains a large combined API, orchestration, and UI unit.

These are convergence requirements, not reasons to discard the validated MGI
behavior.

## Explicitly not now

- provider writes;
- automatic price, Ads, stock, order, fiscal, or financial action;
- silent Ads allocation;
- bulk historical migration from `.mgi`;
- a second ledger or Economic Truth implementation;
- marketplace concepts in the Kernel;
- dashboard-first development;
- agentic autonomy before policy, authority, reconciliation, and outcome gates.

## Success measures

- percentage of orders with each evidence family observed;
- evidence and projection lag by source;
- duplicate and conflict rate;
- provider refresh failure and recovery rate;
- percentage of orders with complete Economic Truth;
- percentage fully reconciled;
- list/detail p95 latency with zero provider calls;
- divergence between expected and actual financial components;
- number and value of decisions blocked due to insufficient evidence;
- later: incremental contribution, loss avoided, money recovered, and decision
  accuracy.
