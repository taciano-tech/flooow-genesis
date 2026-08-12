# TASK-0069 Organization Context Propagation Evidence

**Date:** 2026-08-11

## Result

**IMPLEMENTED - ready for review.**

Authenticated organization authority now propagates from the current service
principal through Marketplace Operations, PostgreSQL journal, CloudEvent V2,
outbox, and delivery coordination. No real provider, OAuth flow, external sink,
or production delivery worker was enabled.

## Implemented scope

- pure `platform:foundation:organization-context` module with canonical UUID
  parsing and no Kernel or application dependency;
- one server-configured organization bound to the existing service bearer;
- organization-scoped recording and lookup without changing HTTP payloads;
- canonical organization reuse by the integration control plane;
- expansion-only Flyway `V005`, preserving null-scoped legacy rows;
- immutable inventory-assessment CloudEvent V2 with column, extension, subject,
  data, type, and schema agreement constraints;
- active same-organization destination validation at enqueue;
- organization predicates on claims, leases, renewals, settlement, retry, and
  dead-letter fencing;
- Compose and CI organization configuration without introducing a secret.

## Focused reproduction

```text
./gradlew :platform:foundation:organization-context:test \
  :applications:marketplace-operations-api:test \
  :applications:marketplace-operations-persistence-postgres:test \
  --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL
35 tests, 0 failures, 0 errors, 0 skipped
```

## Proven guarantees

- malformed or noncanonical organization configuration fails closed;
- authenticated organization comes from server configuration and cannot be
  overridden by a request header;
- cross-organization assessment reads reproduce the existing not-found result;
- request and result digests and Kernel behavior remain unchanged;
- V001 through V005 migrate in order;
- V1 fixture bytes remain unchanged while V2 bytes reproduce exactly;
- new journal and event rows carry the same non-null organization;
- database constraints reject disagreement between V2 organization locations;
- legacy null-scoped rows remain stored but are invisible to scoped reads and
  claims;
- unknown, foreign, suspended, or revoked destinations cannot be enqueued;
- a foreign worker cannot claim or settle another organization's delivery;
- existing retry, lease recovery, fencing, telemetry, and dead-letter behavior
  remains deterministic.

## Complete repository validation

```text
./gradlew build --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 1m 32s
36 actionable tasks: 36 executed
131 tests, 0 failures, 0 errors, 0 skipped
```

The local host does not expose a Docker CLI, so Compose execution remains part
of the GitHub CI package check. The YAML and runtime configuration changes are
covered by that required remote check before merge.

## Remaining boundary

The runtime still has one technical credential bound to one organization. Human
identity, memberships, roles, multiple service principals, token inventory,
legacy ownership assignment, real vaults, OAuth, provider adapters, webhooks,
sync, mapping, and production dispatch remain outside TASK-0069.
