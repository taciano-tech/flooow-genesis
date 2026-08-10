# Marketplace Operations API

Stateless HTTP adapter for the deterministic Marketplace Operations
inventory-risk capability.

## Run

```text
./gradlew :applications:marketplace-operations-api:run
```

The server listens on `0.0.0.0:8080` by default. Set `HOST` and `PORT` to
override those values. Production also requires `DATABASE_URL` as a PostgreSQL
JDBC URL plus `DATABASE_USER` and `DATABASE_PASSWORD`. Flyway migrations finish
before the server accepts traffic.

## Routes

```text
POST /v1/marketplace-operations/inventory-risk-assessments
GET  /v1/marketplace-operations/inventory-risk-assessments/{assessmentId}
GET  /health/live
GET  /health/ready
GET  /openapi.json
```

The committed `openapi.json` is the public contract. Transport DTOs remain in
this module; the API depends on Marketplace Operations and has no direct Kernel
dependency.
