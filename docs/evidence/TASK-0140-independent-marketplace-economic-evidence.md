# TASK-0140: Independent Marketplace Economic Evidence

Status: Implementation complete; PR validation pending

Date: 2026-08-28

## Objective

Implement the pure, organization-scoped independent marketplace economic
evidence contract authorized by ADR-0042 and SPEC-0041 without activating
persistence, providers, APIs, projections, runtime work, external action, AI,
or Kernel behavior.

## Repository baseline

- canonical starting commit:
  `8706c58a49afbb04e3a05607f4fdaa14f406d1d9`;
- source contract: `SPEC-0041`;
- implementation branch: `agent/task-0140-independent-economic-evidence`;
- no existing production type or module dependency changed.

## Delivered boundary

The implementation adds one pure domain file containing:

- an immutable organization/order/marketplace/currency subject;
- a canonical lowercase UUID observation identifier;
- independent financial component and external identity observations;
- collection attempts that contain no amount and cannot erase facts;
- an immutable evidence aggregate with active and historical views;
- deterministic append, duplicate, conflict, and correction behavior;
- source-fact conflict detection based on existing economic provenance;
- explicit supersession that preserves the prior fact;
- canonical microsecond/time-plus-unsigned-UUID ordering;
- redacted rendering for all new domain objects and controlled results.

The implementation reuses `OrganizationId`, marketplace order and source value
types, exact `MarketplaceMoney`, `EconomicComponent`, evidence quality, and
component coverage. It creates no second money, source, order, component,
ledger, reconciliation, or truth model.

## Accepted MGI behavior reproduced

- exact authoritative-zero shipping is retained as a complete fact;
- missing shipping is an append-only attempt and does not regress known data;
- product COGS progresses without ERP order identity;
- invoice identity and tax progress independently;
- multiple Ads identities are retained without allocating Ads spend;
- a failed refresh after a known fact preserves amount and provenance;
- source-fact disagreement is a controlled conflict;
- correction retains history and activates only the replacement.

## SPEC-0041 test-plan reconciliation

| # | Requirement | Evidence | Status |
| --- | --- | --- | --- |
| 1 | Module dependency allow-list unchanged | file-scope and diff verification | Passed |
| 2 | No forbidden bytecode references | compiled-boundary inspection test | Passed |
| 3 | Identifier parsing/equality/ordering/rendering | focused identifier test | Passed |
| 4 | Subject equality and redaction | subject/isolation test | Passed |
| 5 | Family/component and family/identity mappings | exhaustive enum-product tests | Passed |
| 6 | Organization/order/currency isolation | constructor and merger tests | Passed |
| 7 | Only complete/partial observation coverage | coverage test | Passed |
| 8 | Exact zero accepted | authoritative-zero test | Passed |
| 9 | Missing attempt has no amount or mutation | reflection and merge test | Passed |
| 10 | Timestamp precision | component, identity, attempt, correction tests | Passed |
| 11 | Manual/calculated time ordering | source-clock test | Passed |
| 12 | External source clocks gain no ordering inference | source-clock test | Passed |
| 13 | Empty aggregate and immutable snapshots | aggregate immutability test | Passed |
| 14 | Exact duplicate idempotency | duplicate test | Passed |
| 15 | Reused identifier conflict | fact and attempt conflict tests | Passed |
| 16 | Financial source-fact equality/conflict | amount/direction/time/quality/coverage tests | Passed |
| 17 | External identity adds no component | identity test | Passed |
| 18 | Multiple Ads identities | Ads relationship test | Passed |
| 19 | Ads identity does not satisfy allocation | identity/allocation separation test | Passed |
| 20 | Attempts after known facts do not regress | missing/failure regression tests | Passed |
| 21 | Correction target existence/type | missing, attempt, and correction target tests | Passed |
| 22 | Replacement identifier uniqueness | correction collision test | Passed |
| 23 | One supersession per active fact | repeated-correction test | Passed |
| 24 | Correction preserves history and activates replacement | active/historical test | Passed |
| 25 | Unrelated source conflict remains visible | replacement source-conflict test | Passed |
| 26 | Deterministic canonical ordering | fact/attempt/correction order tests | Passed |
| 27 | Active versus historical access | correction history test | Passed |
| 28 | Redaction of all new types/results | rendering matrix test | Passed |
| 29 | Deterministic value-equal results | equality/determinism tests | Passed |
| 30 | All accepted MGI v0.7.6 scenarios | combined provider-free scenario tests | Passed |
| 31 | No existing domain/runtime behavior changed | four-file scope and full module suite | Passed |
| 32 | Diff, focused/module/full gates | commands below | Passed locally |

## Local validation

```text
gradlew.bat -g .gradle-user-home \
  :applications:marketplace-operations:test \
  --tests io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceTest \
  --no-daemon

24 tests, 0 failures, 0 errors, 0 skipped

gradlew.bat -g .gradle-user-home \
  :applications:marketplace-operations:test \
  --no-daemon

231 tests, 0 failures, 0 errors, 0 skipped

gradlew.bat -g .gradle-user-home build \
  -x :applications:marketplace-operations-persistence-postgres:test \
  --no-daemon

BUILD SUCCESSFUL
```

The excluded Postgres task is the established local non-Postgres build gate.
No failing test was bypassed in the changed module. CI remains the authority
for the repository PR gate.

## Mechanical scope and dependency checks

- `git diff --check` passes;
- production source contains no `Float` or `Double`;
- compiled boundary contains no Kernel, connector, API, persistence, JDBC,
  HTTP, JSON, event, UI, AI, infrastructure, or provider reference;
- no Gradle dependency or settings file changed;
- only the four files authorized by SPEC-0041 are included in the task diff.

## Interpretation

SPEC-0041 requires the superseded fact's observation time not to follow the
correction. Because the controlled result list defines no invalid-time result,
this is enforced as a generic, redacted domain precondition after target
resolution. No additional result or contract surface was invented.

## Remaining boundary

This task is in-memory and pure. Durable persistence, process-restart
idempotency, outbox/replay, projection, provider adapters, freshness, Economic
Truth materialization, ledger append, reconciliation, API, UI, policy,
authority, execution, and learning remain unauthorized future work.
