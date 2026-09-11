# SPEC-0004: Reproducible MVP Runtime

**Status:** Implemented by TASK-0057

**Date:** 2026-08-10

## Objective

Make the persistent Marketplace Operations MVP reproducible on a developer
machine without manually assembling a JVM classpath or coordinating database
startup.

## Runtime package

The repository provides one root `compose.yaml` containing:

- PostgreSQL pinned to `postgres:18.4`;
- the API built from its committed multi-stage Dockerfile;
- a named volume mounted at PostgreSQL 18's version-aware
  `/var/lib/postgresql` root for durability across ordinary restarts;
- a PostgreSQL health check;
- API startup conditional on PostgreSQL being healthy;
- configurable host port through `FLOOOW_PORT`;
- a local-only default password that can be overridden by `POSTGRES_PASSWORD`.

The API image:

- builds with Java 21;
- contains a Java 21 runtime but no build toolchain in its final stage;
- runs as fixed non-root UID and GID `10001`;
- starts the committed application main class directly with `/opt/flooow/lib/*`;
- exposes port 8080;
- receives database credentials only at runtime.

The image must not contain source-control metadata, local build output, local
caches, IDE state, or credentials.

## Windows distribution

The Gradle distribution includes `bin/marketplace-operations-api.ps1`. It
starts Java with the distribution `lib/*` classpath, avoiding the Windows
command-line limit reached by the generated batch launcher.

The PowerShell launcher must propagate the Java process exit code and must not
contain credentials or environment defaults.

## Acceptance

1. `docker compose config` validates the committed model.
2. `docker compose up --build` produces a healthy API and database.
3. POST returns `201` with `Location`.
4. GET through that `Location` returns the byte-identical representation.
5. PostgreSQL contains the committed row.
6. the API container runs with UID/GID `10001:10001`.
7. the installed PowerShell launcher starts the same API successfully.
8. the repository build and existing 110 tests remain green.

## Boundaries

This task does not publish an image, provision cloud infrastructure, introduce
production secrets, automate backups, add TLS, or define production scaling.
