# TASK-0139: Independent Marketplace Economic Evidence Contract Review

Status: Contract accepted for implementation

Date: 2026-08-27

## Repository inspection

Inspected canonical main at merge commit `d5cbc9c`, including the TASK-0138
MGI/Genesis audit, ADR-0041, convergence roadmap, executive journal, existing
Economic Truth, financial ledger, reconciliation, connector-runtime, Postgres,
outbox, and organization-context foundations.

MGI v0.7.6 has a validated behavioral requirement that Genesis does not yet
express as one domain contract: evidence families must progress independently
and later empty provider results must not erase accepted facts.

## Reuse decision

ADR-0042 / SPEC-0041 reuse all existing canonical organization and economic
types. They introduce no second money, source, component, coverage, order,
currency, quality, ledger, or reconciliation model.

The contract remains in the marketplace application because order, shipment,
invoice, Ads, and provider evidence are vertical vocabulary.

## Exact boundary

```text
one organization-scoped marketplace order subject
  + independently arriving accepted facts
  + append-only collection attempts
  + explicit corrections
  -> immutable canonical evidence set
```

The evidence set is not Economic Truth, a financial ledger, a read projection,
or a provider integration. Those remain separate consumers and later tasks.

## Protected semantics

- authoritative zero is an exact observed financial fact;
- missing, ambiguous, and provider failure are attempts without amounts;
- attempts never remove accepted facts;
- ERP order identity does not gate shipping, COGS, invoice, tax, or Ads
  identity;
- invoice identity does not create tax;
- Ads identity does not create Ads allocation;
- conflicting provider facts require an explicit correction;
- correction preserves history and activates a new fact;
- organization, order, marketplace, external order, and currency never cross;
- provider occurrence time and Genesis observation time remain distinct;
- all renderings remain redacted.

## MGI regression closure

The accepted tests explicitly cover the two v0.7.6 audit gaps:

1. a missing or failed refresh after a known fact cannot regress evidence;
2. Ads or fiscal identity metadata cannot be cleared by an empty later attempt.

The contract also replaces binary floating-point evidence with the existing
exact `MarketplaceMoney` model.

## Explicit exclusions

- no live Mercado Livre or Omie adapter;
- no provider payload or credential;
- no database, migration, outbox, event, projection, or replay;
- no API, UI, worker, scheduler, or in-process background task;
- no Economic Truth calculation or coverage aggregation;
- no ledger append or reconciliation;
- no pricing, Ads allocation formula, recommendation, action, or AI;
- no existing production type or Kernel change.

## Sequence outcome

The P0 convergence chain is now:

```text
TASK-0138 audit and convergence boundary
  -> TASK-0139 independent evidence contract (this task)
  -> TASK-0140 pure evidence implementation
  -> durable append-only evidence ingestion contract
  -> durable ingestion implementation
  -> fast Sales Intelligence projection
  -> bounded read-only provider adapters
```

TASK-0137 remains accepted and deferred. It must resume before Inventory
Confidence, current-state inventory, or Safe ATP, but is not a prerequisite for
this economic evidence contract.

## Authorization outcome

Acceptance authorizes TASK-0140 only: add the pure evidence aggregate and merge
semantics, focused tests, task evidence, and executive journal entry specified
by SPEC-0041 inside the existing marketplace application.

The implementation task must not expand into persistence, provider, API, UI,
runtime, action, AI, or Kernel work.
