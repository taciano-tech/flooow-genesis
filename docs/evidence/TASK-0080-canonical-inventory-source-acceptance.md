# TASK-0080 — Canonical inventory source acceptance

## Result

Implemented the pure canonical inventory source acceptance boundary authorized by
SPEC-0015. One exact mapping lineage can now record an explicit immutable accepted
observation head without defining global inventory, selecting a business measure,
aggregating sources, or changing runtime/provider surfaces.

## Delivered

- Added `applications:inventory-source-acceptance` with UUID-backed redacted IDs,
  trusted principal validation, immutable acceptance decisions, controlled reasons,
  controlled results, repository contract, service, head, and ordered history.
- Added V010 acceptance revisions and separate retirement audit with one active head
  per organization and mapping root, contiguous revisions, copied V008 reference
  validation, exact V007 lineage validation, active target validation, monotonic
  succession, immutable content, and rejected deletes.
- Added transactional PostgreSQL initial acceptance, CAS replacement, withdrawal,
  replay handling, stale/conflict classification, exact lineage locking, historical
  reads, and fail-closed result translation.
- Added pure contract tests plus PostgreSQL integration coverage for initial/replay,
  later evidence, stale rejection, withdrawal, immutable history, audit rows, and
  competing replacements.

## Verification

- `:applications:inventory-source-acceptance:test` — passed locally.
- `:applications:marketplace-operations-persistence-postgres:compileKotlin` — passed.
- `:applications:marketplace-operations-persistence-postgres:testClasses` — passed.
- PostgreSQL Testcontainers execution is delegated to CI because the local desktop
  session has no available Docker engine.
- `git diff --check` — passed.

## Boundary confirmation

No runtime composition, connector, provider, credential, public API, scheduler,
outbox, workflow, reporting, quantity selection, aggregation, reservation, pricing,
or operational stock behavior was added. V008 observations and quantities remain
immutable and are only referenced by scoped internal identifiers.
