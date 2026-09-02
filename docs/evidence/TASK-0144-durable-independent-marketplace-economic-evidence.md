# TASK-0144: Durable Independent Marketplace Economic Evidence

Status: Implementation complete; awaiting commit and PR validation

Date: 2026-09-01

## Objective

Make the independent marketplace economic-evidence aggregate durable without
reusing or generalizing the external-delivery outbox. Preserve append-only
history, exact domain replay, organization isolation, duplicate-before-stale
semantics, and an organization-scoped durable `change_sequence` for future
incremental projection reads.

## Delivered slices

### Slice 1 - Persistence contract

- introduced the repository port, update commands, read and persistence
  results, and versioned read model;
- kept `change_sequence` out of the domain aggregate and public write results;
- replaced the inline evidence version with an immutable validated class so
  the raw `Long` is exposed only through the explicit persistence bridge;
- validated the contract with focused tests and JVM signature inspection.

### Slice 2 - PostgreSQL journal

- added V015 with subject roots, append-only update history, fact, component,
  external-identity, collection-attempt, correction, and identifier storage;
- added organization-scoped monotonic `change_sequence` allocation in the
  database and the `(organization_id, change_sequence)` uniqueness guarantee;
- preserved currency consistency, correction linkage, organization isolation,
  rollback safety, and append-only enforcement;
- validated V001-V015 and the initial six repository/migration scenarios
  against real PostgreSQL through Testcontainers.

### Slice 3 - PostgreSQL adapter

- implemented transactional apply and repeatable-read reconstruction through
  the canonical domain merger;
- preserved duplicate, conflict, rejection, correction, optimistic-version,
  and organization-lifecycle semantics;
- added deterministic reconstruction and concurrent-writer coverage;
- completed the adapter suite with 15 real PostgreSQL/Testcontainers tests.

## Concurrency defects found and corrected

The first concurrent run exposed two distinct database failures:

1. PostgreSQL `40P01` while concurrent aggregates locked roots and then the
   shared organization row. The adapter now retries the complete transaction
   at most three times, exclusively for `40P01` (deadlock) and `40001`
   (serialization failure). Exhausted retries fail closed through the existing
   contract result and expose no SQLSTATE or infrastructure message.
2. PostgreSQL `23505` when concurrent first writers collided with the
   three-column subject uniqueness constraint while the candidate insert named
   only the two-column primary-key conflict target. The candidate insert now
   uses `ON CONFLICT DO NOTHING` without a column list. The following locked
   root read remains authoritative for Duplicate, Applied, conflict, or
   integrity classification.

## Final validation

- focused persistence contract and JVM surface checks: passed;
- V015 structural repository/migration suite: 6/6 passed against real
  PostgreSQL/Testcontainers;
- complete `PostgresMarketplaceIndependentEconomicEvidenceRepositoryTest`:
  15 tests, 15 passed, 0 failed, 0 skipped;
- Flyway applied V001-V015 successfully in the real integration runs;
- `git diff --check`: passed;
- no commit or push was performed during implementation review.

## Scope verification

TASK-0144 touched exactly the seven files authorized by SPEC-0044:

1. `applications/marketplace-operations/src/main/kotlin/io/flooow/marketplace/operations/economics/evidence/MarketplaceIndependentEconomicEvidencePersistence.kt`
2. `applications/marketplace-operations/src/test/kotlin/io/flooow/marketplace/operations/economics/evidence/MarketplaceIndependentEconomicEvidencePersistenceTest.kt`
3. `applications/marketplace-operations-persistence-postgres/src/main/resources/db/migration/V015__create_independent_marketplace_economic_evidence.sql`
4. `applications/marketplace-operations-persistence-postgres/src/main/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceIndependentEconomicEvidenceRepository.kt`
5. `applications/marketplace-operations-persistence-postgres/src/test/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceIndependentEconomicEvidenceRepositoryTest.kt`
6. `docs/evidence/TASK-0144-durable-independent-marketplace-economic-evidence.md`
7. one appended TASK-0144 entry in `docs/journal/MGI-EXECUTIVE-JOURNAL.md`

No eighth file was touched. In particular, no existing migration, outbox,
`OutboxDeliveryRuntime`, dependency, provider, API, UI, projection,
materializer, Financial Ledger, Reconciliation, or Kernel file changed.

## Boundary preserved

Durable Economic Evidence remains evidence rather than Economic Truth,
Financial Ledger, or Reconciliation. This task adds no provider activation,
fast read model, recommendation, decision automation, or execution authority.
