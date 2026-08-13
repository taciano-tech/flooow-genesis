# TASK-0084 — Canonical inventory candidate snapshot

## Result

Implemented the immutable candidate-set boundary authorized by SPEC-0017. An
explicit non-empty set of V011 lineage roots can now be captured for one exact
organization-scoped target and read later with its frozen V011/V010/V008/V007
provenance and exact V008 rational quantity.

## Delivered

- Added the pure `applications:inventory-candidate-snapshot` module with
  canonical redacted IDs, bounded trusted principal, exact target, duplicate-free
  explicit capture command, immutable read models, controlled results, repository
  contract, service, and canonical unsigned UUID lineage ordering.
- Added V012 immutable snapshot headers and members with organization-scoped
  foreign keys, exact header/member target agreement, provenance validation,
  deferred exact member-count validation, and rejected update/delete operations.
- Added transactional PostgreSQL capture with replay-before-lifecycle validation,
  deterministic multi-root locking, active/suspended connection eligibility,
  exact-target enforcement, all-or-nothing inserts, and concurrent request replay.
- Added historical reads that validate the frozen selection, acceptance,
  observation, mapping, source pointer, revisions, measure, and target, then
  reconstruct the selected signed rational exclusively from V008.
- Added pure tests and PostgreSQL integration coverage for replay, target mismatch,
  atomicity, concurrency, historical reads after selection withdrawal, absence of
  quantity columns, and physical immutability.

## Verification

- `:applications:inventory-candidate-snapshot:test` — passed locally.
- `:applications:marketplace-operations-persistence-postgres:compileKotlin` —
  passed locally.
- Complete local build excluding only the PostgreSQL Testcontainers task — passed.
- The PostgreSQL suite compiled and reached Testcontainers, but execution stopped
  during setup because this desktop session has no valid Docker environment.
  PostgreSQL execution and V012 application are therefore delegated to CI.
- `git diff --check` — passed locally.

## Boundary confirmation

No Kernel, runtime composition, connector, provider, credential, public
API/OpenAPI, Marketplace Operations, assessment, event, delivery, scheduler,
automatic discovery, source authority, ranking, freshness policy, comparison,
reconciliation, aggregation, formula, rounding, business stock, economic metric,
recommendation, or operational mutation was added. V012 stores no quantity; V008
remains the exact quantity source.
