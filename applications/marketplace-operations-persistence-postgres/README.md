# Marketplace Operations PostgreSQL Persistence

Outer adapter implementing the Marketplace Operations assessment journal with
PostgreSQL, Exposed JDBC DSL, and Flyway migrations.

The module has no direct Kernel dependency. It stores application-owned typed
values and an ordered JSONB trace, then verifies canonical SHA-256 request and
result digests on retrieval.

Every committed assessment also creates one
`io.flooow.marketplace.inventory-risk-assessment.recorded.v1` CloudEvent in the
`integration_event_outbox` table in the same database transaction. The envelope
is stored as structured JSON and is not exposed by the application API.

This module only produces immutable outbox records. It does not claim, publish,
retry, delete, or mark them as delivered; `published_at` remains `NULL`. A future
dispatcher must define retention, concurrency, retry, dead-letter, observability,
and shutdown behavior before delivery is enabled.

Integration tests use Testcontainers with the pinned `postgres:18.4` image and
require an available Docker-compatible container runtime.
