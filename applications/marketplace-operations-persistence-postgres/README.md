# Marketplace Operations PostgreSQL Persistence

Outer adapter implementing the Marketplace Operations assessment journal with
PostgreSQL, Exposed JDBC DSL, and Flyway migrations.

The module has no direct Kernel dependency. It stores application-owned typed
values and an ordered JSONB trace, then verifies canonical SHA-256 request and
result digests on retrieval.

Integration tests use Testcontainers with the pinned `postgres:18.4` image and
require an available Docker-compatible container runtime.
