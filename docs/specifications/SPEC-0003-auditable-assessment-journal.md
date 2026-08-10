# SPEC-0003: Auditable Assessment Journal

**Status:** Proposed

**Date:** 2026-08-10

**Source decision:** ADR-0004

## Objective

Make successful inventory-risk assessments durable, immutable, retrievable, and
independently verifiable without persisting Kernel object graphs or changing
Kernel behavior.

## Authorized implementation

Acceptance authorizes TASK-0056, divided into one reviewable implementation PR:

1. add an application-owned journal port and recording service;
2. add `applications:marketplace-operations-persistence-postgres`;
3. add the first Flyway migration and Exposed JDBC implementation;
4. wire persistence into the API composition root;
5. extend POST responses with record identity and add GET-by-ID;
6. verify migrations, round trips, failures, and immutability with PostgreSQL 18.4
   through Testcontainers 2.0.5.

No Kernel production source may change.

## Module boundaries

```text
marketplace-operations-api
        |              |
        |              v
        |   marketplace-operations-persistence-postgres
        |              |
        +--------------+
                       v
             marketplace-operations
                       |
                       v
                    kernel
```

Marketplace Operations owns these framework-free contracts:

```text
InventoryRiskAssessmentJournal
RecordedInventoryRiskAssessment
InventoryRiskAssessmentRecorder
AssessmentIdentifierFactory
```

The journal port accepts and returns Marketplace Operations values only. It has
no SQL, Exposed, Flyway, Ktor, JSON, or Kernel type in its public signature.

The PostgreSQL adapter depends directly only on Marketplace Operations. The API
depends on the adapter solely in its composition root; routes depend on the
application-owned recorder contract.

## Identity and time

- `assessmentId` is a lowercase canonical UUID string generated before insert.
- production uses UUID v4;
- tests inject a deterministic identifier factory;
- `recordedAt` is an `Instant` supplied by an injected `Clock`;
- the database does not invent application identity or time;
- `assessmentId` is returned only after the insert transaction commits.

## Database configuration

The API process requires:

```text
DATABASE_URL
DATABASE_USER
DATABASE_PASSWORD
```

Missing or blank configuration prevents application startup. Credentials must
not be logged or returned in problem responses. Connection-pool tuning and
secret-manager integration remain out of scope.

## Migration V001

Flyway owns the schema. Runtime application code must never create or alter
tables through Exposed.

```sql
CREATE TABLE inventory_risk_assessment_journal (
    assessment_id uuid PRIMARY KEY,
    schema_version smallint NOT NULL,
    recorded_at timestamptz NOT NULL,
    sku text NOT NULL,
    period_end date NOT NULL,
    target_units integer NOT NULL,
    units_sold integer NOT NULL,
    available_units integer NOT NULL,
    daily_sales_velocity integer NOT NULL,
    observed_on date NOT NULL,
    expected_replenishment_on date NOT NULL,
    stock_coverage_days integer NOT NULL,
    projected_stockout_on date NOT NULL,
    projected_stockout_days integer NOT NULL,
    units_potentially_unavailable integer NOT NULL,
    units_remaining_to_goal integer NOT NULL,
    units_at_risk_against_goal integer NOT NULL,
    shortage_projected boolean NOT NULL,
    recommendation_type text NOT NULL,
    recommendation_explanation text NOT NULL,
    expected_units_preserved integer NOT NULL,
    expected_impact text NOT NULL,
    trace jsonb NOT NULL,
    request_digest char(64) NOT NULL,
    result_digest char(64) NOT NULL,
    CONSTRAINT inventory_risk_schema_version CHECK (schema_version = 1),
    CONSTRAINT inventory_risk_nonnegative_values CHECK (
        target_units > 0 AND units_sold >= 0 AND available_units >= 0 AND
        daily_sales_velocity > 0 AND stock_coverage_days >= 0 AND
        projected_stockout_days >= 0 AND units_potentially_unavailable >= 0 AND
        units_remaining_to_goal >= 0 AND units_at_risk_against_goal >= 0 AND
        expected_units_preserved >= 0
    ),
    CONSTRAINT inventory_risk_trace_array CHECK (jsonb_typeof(trace) = 'array')
);

CREATE INDEX inventory_risk_assessment_sku_recorded_at_idx
    ON inventory_risk_assessment_journal (sku, recorded_at DESC);
```

The implementation migration may format this SQL differently but may not weaken
the types or constraints without amending this specification.

## Canonical digests

Digests use SHA-256 over UTF-8 bytes of compact canonical JSON.

Rules:

- object properties appear in the exact order defined below;
- strings use JSON escaping and are otherwise unchanged;
- integers use base-10 with no leading zero;
- booleans are lowercase JSON literals;
- dates use `YYYY-MM-DD`;
- instants use UTC ISO-8601 with normalized `Z`;
- arrays retain order;
- there is no insignificant whitespace or trailing newline.

Request property order:

```text
sku, periodEnd, targetUnits, unitsSold, availableUnits,
dailySalesVelocity, observedOn, expectedReplenishmentOn
```

Result property order:

```text
sku, observedOn, projection, recommendation, expectedImpact, trace
```

Nested property order is the order already committed in SPEC-0002. Identity and
`recordedAt` are metadata and are excluded from both digests. This keeps the
result digest deterministic for equivalent evaluations.

On retrieval the adapter reconstructs typed values and verifies both digests.
A mismatch is an integrity failure, not a partially successful read.

## Write semantics

`InventoryRiskAssessmentRecorder.record(input)` performs:

1. validate and evaluate with the existing deterministic evaluator;
2. select the domain recommendation;
3. allocate identifier and recorded time;
4. calculate canonical digests;
5. append the full record in one database transaction;
6. return the committed record.

No database record is written for malformed input, domain validation failure, or
evaluation failure. A duplicate identifier fails atomically and is not retried
with a different identifier inside the same call.

There is no update or delete method in the journal port. Direct SQL verification
tests must prove a second append does not mutate the first record.

## HTTP contract changes

Successful POST returns `201 Created` and adds these leading properties:

```json
{
  "assessmentId": "11111111-1111-4111-8111-111111111111",
  "recordedAt": "2026-08-10T13:00:00Z",
  "sku": "RED-MOTO-001"
}
```

All SPEC-0002 business fields follow unchanged.

After the database transaction commits, the response includes:

```text
Location: /v1/marketplace-operations/inventory-risk-assessments/{assessmentId}
```

The response body and `Location` identifier must match. Neither a success body
nor a `Location` header may be emitted before the commit succeeds.

Retrieval:

```text
GET /v1/marketplace-operations/inventory-risk-assessments/{assessmentId}
```

- `200`: exact persisted record representation;
- `400 MALFORMED_ASSESSMENT_ID`: path value is not a canonical UUID;
- `404 ASSESSMENT_NOT_FOUND`: valid identifier has no record;
- `503 PERSISTENCE_UNAVAILABLE`: connection or transaction unavailable;
- `500 PERSISTENCE_INTEGRITY_FAILURE`: stored record fails reconstruction or
  digest verification.

POST uses the same 503 and integrity failure contracts. No database exception,
SQL, host, username, credential, or stack trace may appear in a response.

## OpenAPI

The committed OpenAPI 3.1 resource must include record metadata, GET-by-ID, and
all new problem responses. The served document must remain byte-equal to the
committed resource.

## Dependencies

Pinned production dependencies:

```text
org.jetbrains.exposed:exposed-core:1.4.0
org.jetbrains.exposed:exposed-jdbc:1.4.0
org.jetbrains.exposed:exposed-java-time:1.4.0
org.flywaydb:flyway-core:13.2.0
org.flywaydb:flyway-database-postgresql:13.2.0
org.postgresql:postgresql:42.7.12
```

Pinned test dependencies:

```text
org.testcontainers:testcontainers-postgresql:2.0.5
```

Use Exposed JDBC DSL, not DAO. The implementation must review resolved licenses
and record them in TASK-0056 evidence before merge.

## Test plan

1. Flyway migrates an empty PostgreSQL 18.4 database to V001.
2. Re-running migration is idempotent and leaves the schema current.
3. Every request, result, metadata, trace, and digest field round-trips exactly.
4. Known-answer canonical request and result digests are stable.
5. Two equivalent assessments have equal business digests but distinct IDs.
6. A second append cannot modify the first record.
7. Duplicate identifier fails atomically.
8. Missing record returns absence without exception leakage.
9. Tampered typed data or digest is detected as an integrity failure.
10. Database unavailability maps to 503 without sensitive information.
11. POST commits before returning `201`, its identifier, and matching `Location`.
12. GET returns the committed representation.
13. Existing API validation and deterministic golden response remain covered
    after adding metadata.
14. API and persistence modules have no direct Kernel dependency.
15. Existing frozen snapshots remain byte-identical.

Integration tests must use the Testcontainers 2.x PostgreSQL module and a pinned
`postgres:18.4` image. Tests may skip only when explicitly running a documented
unit-only task; repository CI must execute the integration suite and must fail
if a container runtime is unavailable.

Repository validation:

```text
./gradlew clean build --rerun-tasks --no-daemon
```

The build must store and reuse the configuration cache without problems.

## Rollout and rollback

The database must be migrated successfully before the persistent API starts.
Rollback of application code does not automatically reverse V001. V001 is
forward-compatible additive schema and remains in place; destructive down
migrations are prohibited.

## Out of scope

- update or delete APIs;
- list, search, pagination, and analytics;
- idempotency keys and exactly-once semantics;
- retention, archival, backup automation, and privacy erasure workflows;
- authentication and authorization;
- multi-tenancy;
- events, brokers, and CDC;
- persistence of Kernel judgments or evidence relationships;
- production migration to directional reasoning.

## Acceptance

Merging ADR-0004 and SPEC-0003 authorizes TASK-0056 only. Any weakened
immutability, new endpoint, new persisted concept, or Kernel change requires an
amendment before implementation.
