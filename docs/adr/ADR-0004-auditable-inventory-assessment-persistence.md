# ADR-0004: Auditable Inventory Assessment Persistence

Status: Proposed

Date: 2026-08-10

## Context

The Marketplace Operations API can execute deterministic inventory-risk
assessments, but results exist only for the lifetime of an HTTP response. The
MVP now needs durable evidence of what was requested, what was calculated, and
what was recommended.

Persisting Kernel objects directly would turn internal compatibility contracts
into a database schema. Persisting only the latest assessment would also erase
the history needed to explain how an organizational decision evolved.

## Decision

Introduce an outer PostgreSQL adapter dedicated to Marketplace Operations:

```text
marketplace-operations-api
          |
          v
marketplace-operations-persistence-postgres
          |
          v
marketplace-operations
          |
          v
kernel
```

The persistence adapter may depend on Marketplace Operations but must not
declare a direct Kernel dependency. The Kernel, Ktor DTOs, and database records
remain separate representations.

The initial stack is:

- PostgreSQL 18.4;
- Exposed 1.4.0 JDBC DSL, without Exposed DAO entities;
- Flyway 13.2.0 with versioned SQL migrations;
- pgJDBC 42.7.12;
- Testcontainers 2.0.5 PostgreSQL module for integration verification.

## Append-only record

Each successful assessment creates one immutable record containing:

- server-generated assessment identifier;
- schema version;
- recorded timestamp supplied by an explicit clock;
- every normalized business input field;
- every projection field;
- selected intervention type, explanation, and expected units preserved;
- expected impact and complete ordered business trace;
- canonical request and result digests.

No update or delete operation is exposed by the application. A correction is a
new assessment record, never mutation of a previous record.

The first schema stores business facts in typed columns and the ordered trace in
JSONB. It does not serialize `EvaluationResult`, `DecisionContext`, legacy
`Judgment`, `StructuredJudgment`, or arbitrary Kotlin objects.

## Transaction boundary

Evaluation remains deterministic and in process. After a successful evaluation,
the complete record is inserted in one database transaction. The API returns
success only after commit. A persistence failure returns a generic service
failure and never reports a durable assessment identifier.

This task does not introduce exactly-once delivery. Retrying a request may create
another record. Idempotency keys require a later accepted specification.

## Retrieval

The authorized implementation may retrieve one record by assessment identifier
to prove round-trip durability. Listing, filtering, pagination, deletion,
retention policies, and analytics remain out of scope.

## Alternatives considered

### Persist public API JSON only

Rejected as the sole model because important fields would be difficult to
validate and query. Canonical JSON may be retained for digest verification, but
typed columns remain authoritative for the first schema.

### Persist Kernel and domain objects with an ORM DAO

Rejected because it couples storage to constructors and internal object graphs.

### Event sourcing the entire application

Rejected as premature. An immutable assessment journal provides the required
audit property without defining a universal event model.

### In-memory or file persistence

Rejected because it would not validate transactions, migrations, concurrent
database access, or production recovery characteristics.

## Consequences

### Positive

- assessments become durable and recoverable;
- history is preserved rather than overwritten;
- SQL migrations and typed columns make schema evolution explicit;
- integration tests exercise the real PostgreSQL behavior;
- Kernel and transport compatibility remain independent from storage.

### Negative

- successful requests now depend on database availability;
- the API requires environment-specific database configuration;
- Testcontainers integration tests require a compatible container runtime;
- retention, privacy, backup, and idempotency need later decisions.

## Authorization

This ADR is not implementation authorization by itself. SPEC-0003 must define
the exact schema, canonicalization, ports, errors, migrations, and tests before
production dependencies are added.
