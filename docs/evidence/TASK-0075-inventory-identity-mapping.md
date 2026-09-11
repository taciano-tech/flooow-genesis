# TASK-0075 Inventory Identity Mapping Evidence

**Date:** 2026-08-12

## Result

**IMPLEMENTED - ready for review.**

Genesis now owns organization-scoped inventory item, location, and unit anchors
and an immutable, evidence-backed registry that maps an exact source-ledger
selector to those canonical identities. The capability remains
production-inactive and performs no provider call, automatic match, quantity
conversion, inventory mutation, assessment, or event.

## Implemented scope

- pure `applications:inventory-identity-mapping` module with only the three
  contract-authorized project dependencies;
- canonical, redacted UUID values for identities, decisions, and correlations;
- NFC-normalized bounded trusted principals and controlled reason values;
- exact nullable source selectors and exact source-ledger evidence pointers;
- reduced positive rational quantity factors stored only as mapping metadata;
- lifecycle operations for identity creation and retirement, initial mapping,
  replacement, mapping retirement, exact resolution, and ordered history;
- additive Flyway `V007` identity, mapping-revision, and retirement-audit schema;
- PostgreSQL repository with transactional lifecycle revalidation, row locks,
  idempotent replay, optimistic fencing, and controlled redacted failures.

## Proven guarantees

- canonical UUID parsing rejects noncanonical text and all internal identifiers
  render as `[INTERNAL]`;
- selector values retain exact case and null presence, with no source SKU,
  source version, timestamp, or quantity in the mapping key;
- source and target location presence must agree exactly;
- evidence must identify an existing V006 record whose item, location, and unit
  values match the selector, including nulls;
- targets must be active and owned by the authorized organization;
- active and suspended connections permit deliberate administration, while
  unavailable or foreign scope fails closed;
- one exact selector has at most one active decision and one decision per
  revision, with nullable values using `NULLS NOT DISTINCT` semantics;
- concurrent initial assignments accept one revision and reject the competing
  write without producing a duplicate active mapping;
- exact replay is idempotent only when the complete stored decision agrees;
- replacement increments exactly one revision, links its predecessor, retires
  the former decision, and appends its audit row in one transaction;
- a forced audit failure rolls the retirement and successor back together;
- retirement removes future resolution while preserving immutable ordered
  history;
- retired targets stop future resolution without erasing history;
- resolution returns only canonical target, factor, decision ID, and revision;
- source values, trusted principal, organization, connection, evidence pointer,
  correlation, and internal identifiers are absent from controlled diagnostics.

## Repository validation

GitHub Actions CI run `#147` validated the implementation commit with:

```text
./gradlew clean build --no-daemon --stacktrace
Build and Test / Validate repository: success
```

The complete suite contains 172 tests, including 5 pure mapping tests and 5 new
PostgreSQL/Testcontainers integration tests. Existing modules, migrations V001
through V006, API behavior, OpenAPI, and packaging remain covered by the same
repository build.

## Production boundary

Production startup registers no mapping repository, administration endpoint,
resolver, provider, connector, scheduler, or worker. Existing ingestion,
Marketplace Operations inventory risk, API, assessments, events, and delivery
behavior remain unchanged.

## Remaining boundary

Friendly catalog metadata, human users and roles, mapping UI/API, automatic
candidate suggestions, provider-specific adapters, source progress encryption,
sync scheduling, application of quantity factors, canonical observations,
aggregation, reconciliation, business inventory mutation, and outbound stock
writes remain separate contracts.
