# TASK-0055 Auditable Persistence Specification Review

**Date:** 2026-08-10

## Result

**PROPOSED - ready for architectural review.**

ADR-0004 and SPEC-0003 define an append-only PostgreSQL assessment journal
owned by Marketplace Operations. The proposal makes API assessments durable
without persisting Kernel objects or changing Kernel production behavior.

## Decision evidence

- PostgreSQL 18.4 is the pinned database and integration-test image;
- Exposed 1.4.0 is limited to its JDBC DSL; DAO entities are excluded;
- Flyway 13.2.0 owns all schema creation and evolution;
- pgJDBC 42.7.12 is the production driver;
- Testcontainers 2.0.5 exercises migrations and persistence against real
  PostgreSQL behavior;
- dependency licenses must be reviewed and recorded by TASK-0056 before merge.

The selected versions were checked against their official project release or
support pages on the review date.

## Contract evidence

- each successful assessment creates an immutable UUID-addressed record;
- typed business columns preserve queryable facts and JSONB preserves ordered
  trace evidence;
- canonical request and result SHA-256 digests detect stored-data corruption;
- success is returned only after transaction commit;
- POST creates the durable resource with `201 Created` and a matching `Location`;
- GET-by-ID proves exact persisted round-trip reconstruction;
- persistence failures use sanitized 503 or 500 problem responses;
- update, delete, list, analytics, idempotency, and Kernel persistence remain
  outside the authorized scope.

## Repository validation

```text
./gradlew clean build --rerun-tasks --no-daemon --stacktrace
BUILD SUCCESSFUL
```

The validation completed with exit code 0. Subsequent identical builds reported:

```text
Configuration cache entry stored.
Configuration cache entry reused.
```

## Authorization boundary

Merging this proposal accepts ADR-0004 and SPEC-0003 and authorizes TASK-0056
only: implementation of the PostgreSQL journal and its API integration. It does
not authorize changes to Kernel production source.
