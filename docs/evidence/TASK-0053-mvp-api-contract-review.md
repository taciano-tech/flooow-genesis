# TASK-0053 MVP API Contract Review

**Date:** 2026-08-10

## Result

**PROPOSED — ready for architectural review.**

ADR-0003 and SPEC-0002 define an outer Ktor API module exposing the existing
Marketplace Operations inventory-risk capability without exposing Kernel
contracts or changing runtime behavior.

The proposal pins Ktor 3.5.1, the latest stable release confirmed from the
official Ktor release index on the review date.

## Scope evidence

- two documentation files added;
- no production source, test, fixture, snapshot, or Gradle configuration changed;
- API DTOs are owned by the proposed API module;
- dependency direction is API to Marketplace Operations to Kernel;
- generic Kernel evaluation endpoints are explicitly rejected;
- persistence, authentication, telemetry, connectors, and directional production
  migration remain outside the authorized implementation.

## Repository validation

```text
./gradlew clean build --rerun-tasks --no-daemon --no-configuration-cache
BUILD SUCCESSFUL
```

The build emitted the pre-existing Kotlin warning concerning generated `copy()`
visibility for `ValidatedDirectionalEvaluationRequest`. TASK-0053 did not modify
that class. The warning should be handled by a separately authorized
compatibility task before Kotlin language version 2.5.

## Authorization boundary

Merging the proposal accepts ADR-0003 and SPEC-0002 and authorizes TASK-0054
only: the stateless HTTP adapter. It does not authorize any Kernel change.
