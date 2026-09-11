# SPEC-0044: Durable Independent Marketplace Economic Evidence — Change Sequence Scope

Status: Accepted

Date: 2026-09-01

## Objective

Replace the outbox-generalization scope SPEC-0043 assigned to TASK-0144 with
a `change_sequence`-based scope requiring no change to
`integration_event_outbox`, `OutboxDeliveryRuntime.kt`, or any existing
migration. SPEC-0042 remains authoritative except where SPEC-0043's outbox
sections and this specification explicitly replace it.

## V015 scope

`V015__create_independent_marketplace_economic_evidence.sql` creates only new
tables/columns for the evidence journal introduced by SPEC-0042, plus:

- `change_sequence BIGINT NOT NULL`, assigned by a per-organization sequence
  or an equivalent mechanism guaranteeing strict monotonicity under
  concurrent commits — not computed by application code from a pre-read
  maximum;
- a unique index on `(organization_id, change_sequence)`.

V015 creates no column, constraint, or index on `integration_event_outbox` or
any other existing table, and touches no existing migration file.

## Apply algorithm (first-writer + change_sequence)

1. attempt `INSERT ... ON CONFLICT (organization_id, marketplace_order_id) DO
   NOTHING` to establish a candidate root row at version zero, inside the
   transaction;
2. `SELECT ... FOR UPDATE` the root row now guaranteed to exist, regardless of
   which writer created it;
3. validate the complete subject against the locked row; return
   `IntegrityFailure` on mismatch;
4. reconstruct current evidence under lock and apply the existing domain
   merger;
5. map `Duplicate` to durable `Duplicate` using current version, regardless of
   a stale expected version — no `change_sequence` assigned;
6. require and lock an active organization before any non-duplicate result can
   write;
7. map every domain conflict without writing and without assigning
   `change_sequence`;
8. for domain `Applied`, compare expected and current version; return
   `StaleVersion` without writing and without assigning `change_sequence`
   when they differ;
9. for a genuine first `Applied` against a version-zero candidate root,
   proceed; for any other non-applied outcome against a version-zero
   candidate root, roll back the entire transaction, including the row
   created in step 1 — no version-zero root survives a rejected first
   update;
10. append the update journal and subtype rows at `current.next()`, assigning
    the next `change_sequence` for the organization in the same statement or
    transaction;
11. update root version exactly once;
12. commit;
13. return `Applied` with the domain result and new version.

A writer that loses the step-1 race proceeds through steps 2–13 exactly as a
writer that won it; it never assumes it is operating against an empty
aggregate once step 2 has locked an existing row.

## Required Postgres tests

SPEC-0042's tests 1–29 remain mandatory, substituting "no outbox event"
language with "no `change_sequence` assignment". SPEC-0043's outbox-specific
tests (inventory-risk compatibility, evidence CloudEvent shape, delivery
canonicalization) are removed — they apply only if outbox generalization is
revisited later. In their place:

30. every applied fact, attempt, and correction is assigned exactly one
    `change_sequence`, strictly greater than every previously assigned value
    for that organization;
31. duplicate, conflict, and rejected operations assign no `change_sequence`;
32. concurrent applied updates across different subjects in the same
    organization never assign the same `change_sequence` twice, and the
    assigned order matches commit order;
33. `change_sequence` values are never reused after a rolled-back transaction;
34. a projection-style read (`WHERE organization_id = ? AND change_sequence >
    :checkpoint ORDER BY change_sequence`) returns applied updates in commit
    order and is stable under repeated invocation with the same checkpoint.

## Implementation scope

TASK-0144 may alter only these seven files:

1. `applications/marketplace-operations/src/main/kotlin/io/flooow/marketplace/operations/economics/evidence/MarketplaceIndependentEconomicEvidencePersistence.kt`
2. `applications/marketplace-operations/src/test/kotlin/io/flooow/marketplace/operations/economics/evidence/MarketplaceIndependentEconomicEvidencePersistenceTest.kt`
3. `applications/marketplace-operations-persistence-postgres/src/main/resources/db/migration/V015__create_independent_marketplace_economic_evidence.sql`
4. `applications/marketplace-operations-persistence-postgres/src/main/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceIndependentEconomicEvidenceRepository.kt`
5. `applications/marketplace-operations-persistence-postgres/src/test/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceIndependentEconomicEvidenceRepositoryTest.kt`
6. `docs/evidence/TASK-0144-durable-independent-marketplace-economic-evidence.md`
7. one TASK-0144 entry in `docs/journal/MGI-EXECUTIVE-JOURNAL.md`

`OutboxDeliveryRuntime.kt` and its test are explicitly removed from scope.
No dependency file, existing migration, provider, API, UI, projection,
materializer, Ledger, Reconciliation, or Kernel file may change.

## Quality gates

All SPEC-0042 gates remain mandatory, applied to the seven-file scope above.
No merge is permitted that touches `integration_event_outbox`,
`OutboxDeliveryRuntime.kt`, V002, or V005.

## Acceptance

Merging ADR-0045 and this specification authorizes TASK-0144 under this
corrected, narrower scope. It supersedes SPEC-0043's implementation scope and
outbox-related sections. It does not authorize P0.3, provider activation, API,
UI, Economic Truth materialization, Ledger, Reconciliation, decision
automation, or Kernel changes.
