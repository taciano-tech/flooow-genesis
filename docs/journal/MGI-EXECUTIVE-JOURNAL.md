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

## 2026-08-28 - TASK-0140 independent evidence implementation

### Repository state before

- TASK-0139 merged through PR #134;
- canonical main: `8706c58`;
- ADR-0042 and SPEC-0041 authorized one pure implementation task;
- no production MGI feature or provider activation existed in Genesis.

### Decision

Implemented the independent marketplace economic evidence boundary as one
immutable, provider-neutral aggregate inside `marketplace-operations`. Accepted
facts, collection attempts, source conflicts, and explicit corrections remain
different domain concepts. Existing Economic Truth, ledger, and reconciliation
continue to own their respective meanings.

### Changes prepared

- organization/order/marketplace/currency subject isolation;
- exact financial and nonfinancial identity observations;
- authoritative-zero and missing-attempt separation;
- deterministic idempotency and source-fact conflict classification;
- explicit correction with preserved history and active replacement;
- canonical ordering, microsecond precision, and redacted rendering;
- 24 focused tests covering the 32 SPEC-0041 test-plan requirements;
- TASK-0140 evidence report.

### Tests and validation

- focused evidence suite: 24 passed;
- complete `marketplace-operations` suite: 231 passed;
- full non-Postgres build: passed;
- mechanical dependency, forbidden-reference, file-scope, and diff checks:
  passed locally;
- PR #135 CI: passed; merged as `e087548` on 2026-08-30.

### Risk reduced

Independently arriving shipping, product cost, invoice, tax, and Ads identity
evidence can now progress without an ERP order gate. Later missing or failed
attempts cannot erase accepted facts, and conflicting source facts cannot be
silently replaced.

### Remaining risk and roadmap impact

The evidence set is not durable across process restarts and is not yet
materialized into Economic Truth, ledger, reconciliation, or a fast read model.
P0.2 still requires its own accepted architecture and specification. No P0.2,
P0.3, provider, API, or UI work is authorized by this task.

### Next objective

After TASK-0140 passes PR review and CI, re-read canonical main and define the
smallest architecture/specification increment for durable append-only evidence
ingestion. Do not implement it as part of TASK-0140.

## 2026-08-31 - TASK-0141 strategic benchmark memory

### Repository state before

- TASK-0140 merged through PR #135 as `e087548`;
- the independent evidence boundary remained intentionally in memory only;
- core Trusted Commerce direction already existed in the Operating Model and
  Marketplace Trust roadmap;
- richer competitor, external-expert, and Commerce Network hypotheses remained
  outside GitHub in conversation and the local MGI strategic-horizon package.

### Decision

Preserve the unique strategic context as permanent innovation memory while
keeping GitHub `main`, the Constitution, accepted ADRs, and accepted
specifications sovereign. External success is useful evidence, not permission
to copy thresholds, workflows, vendors, technology stacks, or architecture.

### Changes prepared

- permanent Flooow Commerce Network strategic horizon;
- consultation and promotion rule for relevant future strategic decisions;
- distributed-commerce thesis and participant model;
- six candidate B2B, dropshipping, 3PL, allocation, and hybrid models;
- seller, supplier, logistics, payment, fiscal, return, dispute, authority, and
  network-effect hypotheses;
- explicit boundary between Flooow policy orchestration and regulated custody;
- TASK-0141 evidence and reconciliation record.

### Scope protection

No production code, module, dependency, API, schema, provider, score, payment,
fiscal rule, automation, Kernel change, or future implementation was
authorized. Existing Financial Trace, Reconciliation, inventory, connector,
and MGI boundaries remain canonical and must be extended rather than rebuilt.

### Tests and CI

- Markdown whitespace and `git diff --check`: passed;
- full local build excluding only Postgres/Testcontainers tests: passed;
- PR #136 complete CI, including persistent runtime validation: passed.

### Next objective

After PR validation and merge, inspect the latest `main` and derive the smallest
accepted ADR/specification increment for P0.2 durable append-only independent
economic evidence ingestion. Do not start P0.3, provider, API, or UI work.

## 2026-08-31 - TASK-0142 durable evidence contract

### Repository state before

- TASK-0141 merged through PR #136 as `a472f55`;
- TASK-0140 independent evidence remained deterministic but process-local;
- existing Postgres, organization, immutable Ledger, canonical observation,
  concurrency, and transactional-outbox patterns were reusable;
- no accepted durable evidence port, migration, adapter, or integration test
  existed.

### Decision

Accepted ADR-0043 and SPEC-0042 for append-oriented relational evidence history,
domain-merger replay, one optimistic subject version, duplicate-before-stale
retry semantics, and one atomic outbox notification for each newly applied
fact, attempt, or correction.

The contract follows the existing Financial Ledger lifecycle boundary:
historical reads and exact duplicate retry survive organization suspension,
while new mutations fail closed. A narrow persistence encoding bridge in the
application port avoids widening TASK-0140 domain constructors.

### Scope protection

TASK-0142 changes documentation only. It creates no database table, repository,
runtime wiring, projection, provider, API, UI, materializer, recommendation,
action, AI, or Kernel concept. Financial Ledger and Reconciliation remain
separate sovereign boundaries.

### Implementation authorization

TASK-0143 is limited to seven files enumerated by SPEC-0042 and 38 required
behaviors. If exact replay cannot be implemented without changing the accepted
TASK-0140 aggregate, implementation must stop for contract correction.

### Next objective

Implement and validate TASK-0143, then re-read canonical `main` before any P0.3
fast projection or live provider work.

## 2026-08-31 - TASK-0143 outbox contract correction

### Trigger

Implementation preparation after PR #137 inspected the actual V002/V005
outbox schema and delivery serializer. The accepted contract assumed a generic
outbox that did not yet exist.

### Finding

`assessment_id` was mandatory, database checks admitted only inventory-risk
events, delivery canonicalization retained only inventory-risk fields, and the
SPEC-0042 content type conflicted with the stored CloudEvents envelope contract.

### Decision

Stopped production implementation before commit. Accepted ADR-0044 and
SPEC-0043 to generalize the single outbox by explicit event type, preserve
inventory-risk byte compatibility, add the exact evidence CloudEvent family,
and reject arbitrary JSON or unknown event types.

### Scope protection

No schema, runtime, provider, API, UI, projection, materializer, Ledger,
Reconciliation, action, AI, or Kernel behavior changed in TASK-0143.

### Next objective

Implement TASK-0144 within the corrected nine-file scope and all combined
SPEC-0042/SPEC-0043 quality gates.

## 2026-09-01 - TASK-0144 durable independent economic evidence

### Objective

Persist independent marketplace economic evidence as an append-only,
organization-isolated journal with exact domain replay and a durable monotonic
cursor, without widening the production outbox boundary.

### Repository state before

- TASK-0140 supplied the immutable provider-neutral evidence aggregate;
- ADR-0045 and SPEC-0044 superseded the proposed outbox generalization with an
  organization-scoped `change_sequence` design;
- no accepted durable repository port, V015 schema, or PostgreSQL adapter yet
  existed on `main`.

### Decision

Delivered TASK-0144 in three reviewed slices: persistence contract, PostgreSQL
journal, and PostgreSQL adapter. The database owns `change_sequence`; the
adapter reconstructs state through the canonical domain merger and keeps
duplicate, conflict, lifecycle, and optimistic-version outcomes explicit.

### Changes delivered

- narrow persistence contract and redacted version encoding bridge;
- V015 append-only evidence schema and per-organization change sequencing;
- transactional PostgreSQL apply and repeatable-read reconstruction;
- correction history, organization isolation, rollback safety, and
  deterministic replay;
- bounded full-transaction retry for PostgreSQL `40P01` and `40001`;
- candidate-root collision handling through `ON CONFLICT DO NOTHING`, fixing
  the observed `23505` race without weakening subsequent locked validation;
- TASK-0144 evidence report.

### Tests and validation

- persistence contract and JVM surface checks: passed;
- V015 structural suite: 6/6 passed against PostgreSQL/Testcontainers;
- final adapter suite: 15/15 passed, 0 failed, 0 skipped against real
  PostgreSQL/Testcontainers;
- Flyway V001-V015 application and `git diff --check`: passed locally;
- PR and CI remain pending; no commit or push was performed at this checkpoint.

### Risks and debt discovered

Concurrent testing exposed and closed a deadlock/serialization retry gap and a
candidate-root unique-constraint collision. Durable evidence is still not a
fast projection, Economic Truth materialization, Financial Ledger entry, or
Reconciliation result, and no live provider supplies it yet.

### Roadmap impact

P0.2 now has a durable, resumable evidence journal suitable for a future
projection checkpoint query. TASK-0144 does not authorize P0.3, provider, API,
UI, outbox, Ledger, Reconciliation, automation, or Kernel work.

### Scope protection

Exactly the seven SPEC-0044 files changed. No eighth file, existing migration,
outbox runtime, dependency, provider, API, UI, projection, Ledger,
Reconciliation, or Kernel file was touched.

### Next objective

Complete commit, PR, CI, and merge review for TASK-0144. Only after canonical
`main` is updated should the next eligible roadmap increment be derived.

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
