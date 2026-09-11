# TASK-0082 — Canonical inventory measure selection

## Result

Implemented the pure canonical inventory measure-selection boundary authorized by
SPEC-0016. One exact source-mapping lineage can now explicitly select one of the
five canonical measures and resolve it against the current active accepted
observation without fallback, formulas, aggregation, rounding, or business-stock
mutation.

## Delivered

- Added `applications:inventory-measure-selection` with redacted UUID-backed IDs,
  trusted principal validation, closed measure vocabulary, controlled reasons and
  results, immutable decisions, repository contract, service, resolver, head, and
  ordered history.
- Added V011 selection revisions and separate retirement audit with one active
  selection per organization and mapping root, contiguous immutable succession,
  active acceptance and exact lineage validation, active target validation,
  present-measure enforcement, matched retirement reasons, and rejected deletes.
- Added transactional PostgreSQL initial selection, replay, CAS replacement,
  withdrawal, fail-closed current resolution, and exact signed-rational quantity
  reconstruction from the current accepted observation.
- Added pure contract tests and PostgreSQL integration coverage for exact negative
  and zero quantities, replay, correction, withdrawal, missing current measures
  without fallback, immutable history, retirement audits, and competing
  replacements.

## Verification

- `:applications:inventory-measure-selection:test` — passed locally.
- `:applications:marketplace-operations-persistence-postgres:testClasses` — passed
  locally, including the new adapter and integration-test compilation.
- `git diff --check` — passed locally.
- The complete PostgreSQL suite reached Testcontainers, but all 35 existing and new
  database tests stopped during setup because this desktop session has no valid
  Docker environment. PostgreSQL execution is therefore delegated to CI.

## Boundary confirmation

No runtime composition, connector, provider, credential, public API/OpenAPI,
Marketplace Operations, assessment, event, delivery, workflow, scheduler,
automatic selection, fallback, source ranking, staleness rule, reconciliation,
aggregation, rounding, reservation, pricing, or operational-stock behavior was
added. V008 remains the only quantity ledger; V011 stores policy and provenance,
not duplicated quantities.
