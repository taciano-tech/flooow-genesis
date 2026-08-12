# TASK-0073 Durable Inventory Source Ledger Evidence

**Date:** 2026-08-12

## Result

**IMPLEMENTED - ready for review.**

Genesis can now append one provider-neutral page of source inventory balances and
advance its protected connector position in the same organization-scoped
PostgreSQL transaction. The capability remains production-inactive and performs
no provider call, product mapping, business inventory mutation, or assessment.

## Implemented scope

- pure `applications:inventory-source-ingestion` module depending only on the
  connector runtime;
- exact `inventory.source-balance.read` capability and typed source record;
- opaque NFC-normalized source references and metadata with bounded UTF-8 sizes;
- signed fixed-decimal source quantities that preserve negative values, zero,
  missing measures, and up to six decimal places without rounding or clamping;
- transport-neutral progress-protector port with organization, connection,
  capability, and version context;
- opaque, defensively copied, redacted, bounded, and zeroable sealed envelopes;
- additive Flyway `V006` tables for scoped connector progress, immutable page
  commits, and ordered inventory source balances;
- PostgreSQL page committer with lifecycle revalidation, row locking,
  deterministic idempotency, compare-and-set progress, and atomic rollback;
- deterministic context-bound protection fake under test source only.

## Focused validation

```text
./gradlew :applications:inventory-source-ingestion:test \
  :applications:connector-runtime:test \
  :applications:marketplace-operations-persistence-postgres:test \
  --tests '*PostgresInventorySourceBalanceCommitterTest' \
  --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 1m
```

The source-value module has 3 tests, the connector runtime has 21 tests, and the
PostgreSQL ledger class has 7 Testcontainers tests, all passing.

## Proven guarantees

- absent state loads as version zero without inserting a database row;
- active, same-organization connection and current credential binding are
  required for both load and commit;
- plaintext progress never becomes a SQL parameter and cannot be found in the
  persisted envelope;
- wrong protection context or a corrupted envelope fails closed without marker
  disclosure;
- successful commits atomically append page metadata and ordered records while
  advancing exactly one progress version;
- terminal progress stores no envelope and prevents later protector, credential,
  and adapter calls;
- identical concurrent pages produce one commit and one idempotent success;
- divergent concurrent pages for one position produce one commit and one
  controlled integrity failure without a second record;
- database constraint failures and lifecycle suspension roll back progress,
  page, and records together;
- signed decimal quantities round-trip through `numeric(24,6)` exactly;
- source identifiers can be reused by different organizations and connections
  without leakage;
- source values, quantities, progress, envelopes, commit keys, and injected
  database markers are absent from controlled outcomes.

## Complete repository validation

```text
./gradlew build --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 1m 32s
53 actionable tasks: 53 executed
162 tests, 0 failures, 0 errors, 0 skipped
```

The existing Kotlin compiler warning in
`DirectionalEvaluationRequest.kt` about future data-class copy visibility remains
unchanged and does not originate in TASK-0073.

## Production boundary

Production startup registers no committer, progress protector, connector,
scheduler, worker, credential, or provider. Existing API, OpenAPI, assessments,
events, delivery, research, and Kernel behavior remain unchanged.

## Remaining boundary

A production authenticated-encryption implementation and key lifecycle, source
identity and unit mapping, provider account configuration, Mercado Livre and Omie
adapters, scheduling, replay generations, retention, operational aggregation,
business inventory mutation, and real-time execution remain separate contracts.
