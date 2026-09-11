# TASK-0092 - Marketplace Financial Trace and Economic Ledger

## Implemented boundary

- Added the pure Marketplace financial-ledger domain under the existing
  `applications:marketplace-operations` module.
- Added canonical trace, entry, open-request, and append-request identities;
  controlled financial stages and expected/actual basis; immutable drafts,
  recorded facts, traces, results, and repository port.
- Reused MKT-001 organization, order, marketplace, currency, exact money,
  direction, and source provenance contracts.
- Added PostgreSQL V014 trace and ledger tables with organization isolation,
  immutable update/delete triggers, transaction-time stamping, stable-source
  uniqueness, request replay, and linear correction constraints.
- Added a transactional PostgreSQL repository for open, append, find, and
  find-by-order behavior.

## Safety properties

- Financial-ledger bytecode contains no Kernel reference.
- One organization has at most one trace for a normalized order.
- Expected and actual facts are independent immutable entries.
- Internal request IDs and present external source facts provide separate
  idempotency boundaries.
- One present external fact cannot attach to two traces in one organization.
- Corrections append replacements and preserve originals; they cannot branch,
  cross traces, stages, bases, or organizations.
- Economic reversals remain ordinary opposite-direction facts and are not
  database corrections.
- PostgreSQL itself stamps `openedAt` and `recordedAt`; callers cannot manufacture
  database audit time.
- New writes require an active organization, while historical reads and exact
  request replays remain available after suspension.
- Aggregate rendering and controlled failures disclose no financial values or
  database diagnostics.
- No file under `platform/foundation/kernel` changed.

## Scenario reproduction

The acceptance fixture persists six expected order facts followed by three
actual facts:

```text
SALE expected                           299.90 BRL
MARKETPLACE_COMMISSION expected          41.99 BRL
SHIPPING expected                        18.40 BRL
ADVERTISING expected                      7.20 BRL
TAX expected                             24.30 BRL
PRODUCT_COST expected                   143.20 BRL
SETTLEMENT actual                        65.31 BRL
PAYMENT_ACCOUNT actual                   65.31 BRL
BANK actual                              65.31 BRL
```

All nine facts and their provenance remain available. TASK-0092 deliberately
does not calculate the R$ 0.50 difference from the MKT-001 contribution and does
not assign a reconciliation status; those judgments remain MKT-003.

## Verification

- `:applications:marketplace-operations:test` - passed locally, including the
  pure ledger invariant and bytecode tests.
- `:applications:marketplace-operations-persistence-postgres:testClasses` -
  passed locally, compiling the V014 Testcontainers suite.
- PostgreSQL integration tests cover migration order, replay, source collision,
  organization isolation, lifecycle, complete scenario round-trip, correction
  chains, concurrency, immutability, malformed persistence, and rollback.
- The local desktop has no Docker runtime; the complete PostgreSQL suite is
  executed by GitHub CI.
- Static forbidden-boundary scans and `git diff --check` passed locally.

The implementation is production-inactive. It adds no connector, API, worker,
event, reconciliation, pricing, recommendation, action, AI, or runtime wiring.
