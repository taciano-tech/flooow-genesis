# MGI Executive Engineering Journal

Status: Active

Purpose: provide a durable executive record of MGI/Genesis convergence,
decisions, delivered changes, validation, risk, debt, and the next objective.

This journal records repository evidence. It is not a substitute for ADRs,
specifications, task evidence, commits, CI, or pull requests.

## Initial state - 2026-08-27

### Mission

Move Marketplace Growth Intelligence toward reliable real marketplace
decisions while preserving Flooow Genesis as the canonical Organizational
Computing platform.

### Starting repository state

- Genesis main: `dabbda8`;
- open PR: #132, TASK-0136 freshness contract;
- MGI code absent from Genesis and project `sources/`;
- user supplied the MGI v0.7.6 archive during the audit;
- no MGI branch or repository was found under the inspected GitHub accounts.

### MGI baseline received

- version: 0.7.6, Parallel Evidence Resolution;
- SHA-256:
  `B1B9D9A77189CC9E21901BCA991AF5BB4FAFDACD6381D9A8D3001CE6AF8EE0F6`;
- 194 isolated tests passed;
- real `.mgi` data and credentials were not inspected or changed.

### Genesis baseline verified

- PR #132 had successful CI, no conflict, and was merged normally;
- new main: `39993d3`;
- non-Postgres build baseline passed;
- 371 tests passed with zero failures/errors/skips;
- Postgres/Testcontainers tests were excluded locally, not bypassed in the
  already-green PR CI.

### Principal discoveries

1. MGI supplies valuable operational behavior and concrete provider learning.
2. Genesis already supplies the stronger canonical economic, financial,
   organization, connector, persistence, and audit foundations.
3. A wholesale port would create duplicate authorities.
4. The correct first convergence boundary is independent economic evidence,
   followed by durable ingestion and a fast projection.
5. MGI v0.7.6 has evidence-regression, orchestration-test, precision,
   transactionality, provenance-history, and tenant-isolation debt that must
   not be copied.

### Decisions

- accepted ADR-0041;
- established one convergence backlog in
  `docs/roadmap/MGI-GENESIS-CONVERGENCE.md`;
- retained MGI v0.7.6 only as behavioral baseline and controlled read-only
  transitional reference;
- preserved fast local reads as an invariant;
- preserved missing-is-not-zero and Ads-identity-is-not-allocation invariants;
- temporarily deferred, but did not cancel, TASK-0137 freshness implementation;
- prohibited live writes and autonomous action in the convergence foundation.

### Delivered in TASK-0138

- repository, branch, PR, task, architecture, and roadmap audit;
- archive integrity and content validation;
- v0.7.5-to-v0.7.6 semantic diff;
- isolated MGI test reproduction;
- Genesis build/test baseline;
- convergence ADR, roadmap, evidence report, and this journal;
- completion and merge of the already-green PR #132.

### Current risks

- no canonical live Mercado Livre or Omie adapter exists;
- no durable Sales Intelligence projection exists;
- local MGI remains a single-user prototype with local credentials and SQLite;
- Postgres/Testcontainers tests were not run in the local audit environment;
- canonical `main` was reported by GitHub as unprotected during the repository
  audit, although the engineering workflow still forbids direct pushes and CI
  bypass;
- old remote branches remain and need later non-destructive repository hygiene
  review.

### Debt accepted temporarily

- TASK-0137 freshness implementation is deferred;
- MGI v0.7.6 may remain a controlled read-only prototype until parity gates;
- operational UI work waits for canonical ingestion and projection.

### Next objective

Define the provider-neutral, organization-scoped independent economic evidence
contract and executable acceptance scenarios for:

- authoritative zero shipment cost;
- missing shipment cost;
- product COGS without ERP sales-order identity;
- invoice and tax evidence without ERP sales-order identity;
- Ads identity without Ads allocation;
- repeated missing refresh that does not erase accepted evidence;
- explicit correction and conflict behavior.

The task must remain pure: no live provider, persistence, UI, action, or Kernel
change.

## 2026-08-27 - TASK-0139 contract review

### Repository state before

- TASK-0138 merged through PR #133;
- canonical main: `d5cbc9c`;
- MGI v0.7.6 behavioral baseline and one convergence roadmap accepted;
- no production MGI feature started in Genesis.

### Decision

Accepted ADR-0042 and SPEC-0041 for one pure independent marketplace economic
evidence boundary. The contract reuses Genesis economics and makes accepted
facts, empty/failed collection attempts, conflicts, and explicit corrections
different domain events.

### Changes prepared

- exact evidence families and financial component mapping;
- external identity observations for payment, ERP order, invoice, and
  marketplace item-to-Ad-Group relationships;
- authoritative-zero and missing-attempt semantics;
- append-only evidence set and deterministic merge classification;
- explicit correction/supersession with retained history;
- provider-free MGI v0.7.6 acceptance scenarios;
- narrow TASK-0140 implementation authorization.

### Risk reduced

A later missing, ambiguous, or failed refresh is no longer allowed to overwrite
accepted financial or identity evidence. Ads identity is structurally unable to
become Ads allocation inside this boundary.

### Remaining risk

The contract is not durable until a later persistence task. No live provider,
projection, API, or operational screen exists yet. TASK-0137 inventory
freshness remains deferred.

### Next objective

Implement TASK-0140 exactly as specified, prove the MGI scenarios and
no-regression rules, then return to the durable ingestion dependency.

## Journal update template

Each completed convergence task appends:

```text
Date / task / PR / merge commit
Objective
Repository state before
Decision
Changes delivered
Tests and CI
Risks and debt discovered
Roadmap impact
Next objective
```
