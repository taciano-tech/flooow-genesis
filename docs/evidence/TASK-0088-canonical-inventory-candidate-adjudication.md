# TASK-0088 — Canonical inventory candidate adjudication

## Implemented boundary

- Added the pure `applications:inventory-candidate-adjudication` module.
- Added canonical redacted identifiers, bounded principal, controlled reasons,
  explicit command, immutable decision/read models, closed results, repository,
  and service.
- Added V013 as an additive immutable reference ledger.
- Added a transactional PostgreSQL adapter with request replay, one-decision-per-
  snapshot conflict, active-organization validation for new writes, exact member
  locking, deterministic comparison, reason validation, and historical reads.
- Reused the V012 historical reader in the same transaction; V013 copies no
  quantity, measure, target, connection, or provenance.

## Safety properties

- The caller chooses an exact frozen lineage explicitly.
- No ordering, source name, timestamp, freshness, score, rank, or automatic
  heuristic chooses a member.
- The Kotlin comparator and deferred PostgreSQL validation enforce the same
  reason/comparison matrix.
- V008 remains the only exact quantity ledger.
- V013 rejects updates and deletes.
- Identical replay is evaluated before current lifecycle state; a new write
  requires an active organization.
- No Kernel, runtime, API/OpenAPI, connector, provider, event, or deployment
  behavior changed.

## Verification

- `:applications:inventory-candidate-adjudication:test` — passed locally.
- `:applications:marketplace-operations-persistence-postgres:testClasses` —
  passed locally, including compilation of all transactional tests.
- The complete Testcontainers suite requires GitHub CI because Docker was not
  available in the local desktop environment.
- `git diff --check` — passed locally.

The implementation remains production-inactive and creates no business-stock
authority.
