# TASK-0071 Provider-Neutral Connector Runtime Evidence

**Date:** 2026-08-11

## Result

**IMPLEMENTED - ready for review.**

Genesis now has a production-inactive, provider-neutral pull runtime that can
select and commit one organization-scoped connector page without registering a
real provider, resolving a real credential, opening an external connection, or
changing any business state.

## Implemented scope

- pure `applications:connector-runtime` Kotlin module with only the accepted
  integration-control-plane and organization-context project dependencies;
- canonical capability, invocation, budget, progress, page, failure, outcome,
  cancellation, registry, and commit contracts;
- metadata-only active-provider resolution before secret-vault access;
- closed provider/capability and typed-committer registries;
- exactly one bounded adapter read per invocation;
- opaque 4096-byte maximum progress with defensive copy, redaction, scoped use,
  and zeroing;
- deterministic commit key derived only from organization, connection,
  capability, and progress version;
- atomic page-committer port with committed, already-committed, and stale-progress
  results;
- terminal exhausted state that prevents another credential resolution or
  adapter read;
- cooperative deadline and cancellation gates around connection, credential,
  adapter, and commit boundaries;
- controlled retry hints and redacted unexpected-failure handling;
- deterministic in-memory connector, connection access, and committer fakes in
  tests only.

## Focused validation

```text
./gradlew :applications:connector-runtime:test \
  --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 19s
20 tests, 0 failures, 0 errors, 0 skipped
```

The PostgreSQL lifecycle test was also run independently:

```text
./gradlew :applications:marketplace-operations-persistence-postgres:test \
  --tests '*PostgresIntegrationControlPlaneRepositoryTest' \
  --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 1m 32s
```

It proves that draft, foreign, suspended, revoked, and unbound connections never
expose a provider while an active same-organization bound connection does.

## Proven guarantees

- missing provider/capability or typed committer fails before secret resolution;
- unavailable connection states return one controlled result without revealing
  foreign-resource existence;
- credentials and loaded/returned progress bytes are zeroed after scoped use;
- raw typed records, secrets, progress, exception messages, and storage markers
  do not appear in outcomes;
- adapter invocation occurs at most once and the runtime performs no retry;
- record count, response bytes, future timestamps, progress advancement, record
  type, and exhausted-state invariants are checked before commit;
- deadline or cancellation prevents all later work at each runtime boundary;
- only rate-limited and remote-temporary failures retain a bounded retry hint;
- unexpected adapter, committer, connection, or cancellation exceptions become
  a redacted `INTERNAL` outcome;
- concurrent reads of one progress version accept records exactly once and
  return committed plus already-committed outcomes;
- a stale version is fenced as `PROGRESS_CONFLICT`;
- a completed stream remains terminal and does not reread its last page;
- no production source contains HTTP, OAuth, provider SDK, JDBC, serialization,
  scheduler, thread-pool, filesystem, or environment access.

## Complete repository validation

```text
./gradlew build --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 1m 2s
40 actionable tasks: 40 executed
151 tests, 0 failures, 0 errors, 0 skipped
```

The existing Kotlin compiler warning in
`DirectionalEvaluationRequest.kt` about future data-class copy visibility remains
unchanged and does not originate in TASK-0071.

## Production boundary

Production startup, HTTP routes, OpenAPI, PostgreSQL schema, immutable events,
outbox delivery, Marketplace Operations, research, and Kernel behavior remain
unchanged. No source or test contacts Mercado Livre, Omie, or any external
provider.

## Remaining boundary

Durable connector progress, the first typed inventory observation, mapping,
provider account configuration, Mercado Livre OAuth and adapter, Omie credential
onboarding and adapter, webhooks, missed-event recovery, scheduling, automatic
retry, outbound provider writes, and production activation remain unauthorized.
