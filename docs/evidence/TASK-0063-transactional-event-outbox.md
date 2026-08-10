# TASK-0063 Transactional Event Outbox Evidence

**Date:** 2026-08-10

## Result

**IMPLEMENTED - ready for review.**

Marketplace Operations PostgreSQL persistence now stores one frozen
`InventoryRiskAssessmentRecorded.v1` CloudEvent for every committed assessment.
The assessment and outbox record are inserted in the same Exposed transaction.

## Implemented scope

- additive Flyway migration `V002` for `integration_event_outbox`;
- stable event source, versioned type, subject, schema identifier, and content type;
- deterministic structured CloudEvents JSON fixture;
- exactly one event per assessment enforced by a unique foreign key;
- database checks that envelope identifiers agree with indexed columns;
- deterministic undispatched index over `(published_at, created_at, event_id)`;
- `published_at` remains null and no delivery behavior exists;
- adapter README documents the delivery and retention boundary.

Marketplace Operations production sources, Kernel production sources, HTTP
routes, DTOs, and OpenAPI were not changed.

## Reproduction evidence

Focused adapter validation:

```text
./gradlew :applications:marketplace-operations-persistence-postgres:test \
  --rerun-tasks --no-daemon --console=plain
5 tests, 0 failures, 0 errors, 0 skipped
```

Complete repository validation:

```text
./gradlew build --rerun-tasks --no-daemon --console=plain
exit code: 0
116 tests, 0 failures, 0 errors, 0 skipped
```

## Proven guarantees

- migrations `V001` and `V002` apply successfully to PostgreSQL 18.4;
- a successful append stores one assessment and one outbox record;
- retrying the same assessment identifier stores no duplicate;
- a forced outbox insert failure rolls back the assessment insert;
- the canonical serializer is byte-identical to the frozen fixture;
- persisted envelope columns and JSON agree;
- the envelope and payload contain exactly the authorized fields;
- tokens, passwords, trace, explanation, and expected-impact prose are absent;
- no event is marked as published.

## Remaining boundary

No dispatcher, broker, webhook, connector, retry, claiming, dead-letter,
retention worker, public outbox read, or consumer is implemented. Those require a
separate accepted specification.
