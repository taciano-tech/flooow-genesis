# Marketplace Operations API

Stateless HTTP adapter for the deterministic Marketplace Operations
inventory-risk capability.

## Run

```text
./gradlew :applications:marketplace-operations-api:run
```

For the complete persistent MVP:

```text
docker compose up --build
```

Copy `.env.example` to `.env` to customize the local database password or host
port. The committed defaults are intended only for isolated local development.
The committed service token is public and provides no protection. Replace
`FLOOOW_SERVICE_TOKEN` and set `FLOOOW_ENVIRONMENT` to a non-`local` value in
every shared environment.

On Windows, an installed distribution can be started without the generated
batch classpath limit:

```text
./gradlew :applications:marketplace-operations-api:installDist
powershell -File applications/marketplace-operations-api/build/install/marketplace-operations-api/bin/marketplace-operations-api.ps1
```

The server listens on `0.0.0.0:8080` by default. Set `HOST` and `PORT` to
override those values. Production also requires `DATABASE_URL` as a PostgreSQL
JDBC URL plus `DATABASE_USER` and `DATABASE_PASSWORD`. Flyway migrations finish
before the server accepts traffic. Startup also requires a service token of at
least 43 characters in `FLOOOW_SERVICE_TOKEN`; the local placeholder is rejected
unless `FLOOOW_ENVIRONMENT=local`.

## Routes

```text
POST /v1/marketplace-operations/inventory-risk-assessments
GET  /v1/marketplace-operations/inventory-risk-assessments/{assessmentId}
GET  /health/live
GET  /health/ready
GET  /openapi.json
```

The POST, GET-by-ID, and OpenAPI routes require:

```text
Authorization: Bearer <FLOOOW_SERVICE_TOKEN>
```

Health routes remain public for orchestration probes. Bearer credentials must
use HTTPS outside isolated local development; TLS termination belongs to the
deployment platform or reverse proxy.

The committed `openapi.json` is the public contract. Transport DTOs remain in
this module; the API depends on Marketplace Operations and has no direct Kernel
dependency.
