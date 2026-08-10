# TASK-0054 Stateless HTTP Adapter

**Date:** 2026-08-10

## Result

**PASS**

The new `applications:marketplace-operations-api` module exposes the existing
deterministic inventory-risk capability through the HTTP contract accepted in
ADR-0003 and SPEC-0002.

## Implemented boundary

```text
marketplace-operations-api -> marketplace-operations -> kernel
```

- Ktor 3.5.1 server on JVM 21;
- API-owned JSON parsing and rendering with kotlinx.serialization JSON;
- synchronous assessment endpoint;
- liveness and readiness endpoints;
- committed and served OpenAPI 3.1 contract;
- RFC-style problem responses for 400, 404, 415, 422, and 500;
- host and port configuration through `HOST` and `PORT`;
- no persistence, messaging, external network call, or autonomous action.

The API module declares no direct Kernel dependency. Compilation also confirmed
that Kernel result types are not available through the application's
`implementation` dependency. Marketplace Operations therefore exposes only the
additive calculated `selectedAlternative` business property required for DTO
mapping.

## Contract verification

The API test suite executed 12 tests with:

```text
tests=12
skipped=0
failures=0
errors=0
```

Coverage includes:

- exact Red Moto golden response and media type;
- repeated byte-equivalent response;
- no-shortage result;
- malformed JSON, wrong types, every missing property, unknown property, and
  invalid date;
- representative domain invariant failures;
- unsupported media type and problem content type;
- unmatched route;
- generic internal error without exception or filesystem disclosure;
- health endpoints without business evaluation;
- served OpenAPI equality with the committed resource.

The Gradle `verifyApiDependencyBoundary` task runs as part of `check` and rejects
a direct `io.flooow:kernel` dependency from the API module.

## Repository validation

```text
./gradlew clean build --rerun-tasks --no-daemon --no-configuration-cache
BUILD SUCCESSFUL
```

Repository comparison against `origin/main` confirmed:

```text
KERNEL_DIFF=0
SNAPSHOT_DIFF=0
```

The pre-existing Kotlin warning about `ValidatedDirectionalEvaluationRequest`
copy visibility remains unchanged and outside this task.

## Rollback

Rollback removes the API module, its settings registration, the additive
`selectedAlternative` calculated property, and the acceptance/evidence updates.
No Kernel or frozen experimental evidence requires restoration.
