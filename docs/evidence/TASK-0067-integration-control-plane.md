# TASK-0067 Integration Control Plane Evidence

**Date:** 2026-08-10

## Result

**IMPLEMENTED - ready for review.**

The repository now contains a production-inactive, provider-neutral control
plane for organization-scoped integrations. No public route, startup wiring,
external API, OAuth flow, or real secret manager was enabled.

## Implemented scope

- pure Kotlin `applications:integration-control-plane` module with a forbidden
  dependency guard;
- immutable organization, connection, destination, credential-binding, and
  audit contracts;
- active-only credential access through a transport-neutral `SecretVault` port;
- draft, activation, suspension, resumption, rotation, and terminal revocation
  lifecycle operations;
- optimistic credential rotation with stale-version rejection and a controlled
  old-secret cleanup result;
- additive Flyway migration `V004` with organization-scoped composite keys;
- raw JDBC PostgreSQL repository with scoped predicates and transactional audit
  appends;
- deterministic service tests backed by an ephemeral PostgreSQL 18.4 instance
  and a fake vault that owns, copies, and zeroes credential buffers.

## Focused reproduction

```text
./gradlew :applications:integration-control-plane:test \
  :applications:marketplace-operations-persistence-postgres:test \
  --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 1m 11s
14 tests, 0 failures, 0 errors, 0 skipped
```

## Proven guarantees

- provider and destination identifiers use the accepted canonical formats;
- secret references redact their string representation;
- V001 through V004 migrate successfully in order;
- cross-organization reads return no data and composite foreign keys reject a
  destination attached to another organization;
- initial binding activates a draft connection without persisting secret bytes;
- successful rotation increments exactly one version and revokes the old secret;
- stale rotations revoke the uncommitted new secret;
- old-secret cleanup failure retains the committed new binding and returns only
  `ROTATED_CLEANUP_REQUIRED`;
- suspended organizations and connections cannot expose credentials or register
  destinations;
- revoked connections lose their current binding and cannot resume;
- lifecycle changes append controlled audit actions without secret material.

## Complete repository validation

```text
./gradlew build --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 1m 48s
41 actionable tasks: 41 executed
125 tests, 0 failures, 0 errors, 0 skipped
```

## Remaining boundary

The control plane remains disconnected from global assessments, events, outbox,
and delivery rows until organization identity is specified and propagated end to
end. Real vaults, provider adapters, OAuth, ERP and marketplace APIs, sync,
webhooks, administration endpoints, and production activation remain outside
TASK-0067.
