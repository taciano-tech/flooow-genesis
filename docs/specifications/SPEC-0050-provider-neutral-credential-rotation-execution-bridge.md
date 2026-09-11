# SPEC-0050: Provider-Neutral Credential Rotation Execution Bridge

Status: Accepted

Date: 2026-09-06

Governing ADR: ADR-0051

Implementation task: TASK-0150

## Objective

Implement a production-capable but production-inactive provider-neutral
credential-rotation execution bridge that fences one rotating credential version
before any remote refresh attempt.

Reuse Integration Control Plane for secret custody and final versioned
replacement.

Connector Runtime production code remains unchanged.

TASK-0150 makes no real provider request.

## New module

Create:

```text
applications:credential-rotation-execution
```

Allowed project dependencies:

```text
applications:integration-control-plane
platform:foundation:organization-context
```

Forbidden dependencies include Connector Runtime, Marketplace Operations,
provider-ingestion production code, JDBC/PostgreSQL, HTTP/OAuth SDK,
serialization framework, scheduler framework, and Kernel.

## Control Plane extension

The Control Plane may add:

```text
ActiveCredentialContext(
  providerKey,
  credentialKind,
  bindingVersion
)

withActiveCredentialContext(
  organizationId,
  connectionId,
  operation: (ActiveCredentialContext, ByteArray) -> T
)
```

Rules:

- active organization;
- active connection;
- current binding exists;
- connection binding version equals current binding version;
- no secret reference exposed;
- credential bytes remain scoped by `SecretVault`;
- existing `withActiveCredential` remains compatible.

## Invocation

A bounded invocation carries:

```text
organizationId
connectionId
executionId
deadline
```

Execution ID is canonical UUID.

One invocation performs at most one provider credential refresh request.

No sleep or internal retry loop.

## Provider rotator registry

A rotator declares:

```text
providerKey
credentialKind
```

One provider/kind pair may be registered once.

Unknown/duplicate registration fails closed.

Unknown rotator fails before secret resolution.

TASK-0150 uses deterministic fake rotators only.

## Assessment

Local assessment returns exactly:

```text
USABLE
REFRESH_REQUIRED
AUTHENTICATION_REQUIRED
```

Assessment makes no network call and no durable write.

`USABLE` returns success with no claim.

`AUTHENTICATION_REQUIRED` returns controlled failure with no claim.

Only `REFRESH_REQUIRED` enters durable coordination.

## Durable store

Define:

```text
CredentialRotationExecutionStore
```

Durable identity:

```text
organizationId
connectionId
bindingVersion
```

Required operations are equivalent to:

```text
claim
markRemoteStarted
markRetryable
markCompleted
markInDoubt
```

Claim results distinguish:

```text
ACQUIRED
BUSY
STALE_VERSION
CONNECTION_UNAVAILABLE
IN_DOUBT
ALREADY_COMPLETED
```

## States

Freeze:

```text
CLAIMED
REMOTE_STARTED
RETRYABLE
COMPLETED
IN_DOUBT
```

Allowed transitions:

```text
(no row) -> CLAIMED

CLAIMED
  -> CLAIMED        lease reclaim before remote start only
  -> REMOTE_STARTED
  -> COMPLETED      superseded before remote start

REMOTE_STARTED
  -> RETRYABLE      only explicit proof same credential is safe to retry
  -> COMPLETED
  -> IN_DOUBT

RETRYABLE
  -> CLAIMED        at/after retry_not_before
  -> COMPLETED      superseded by newer credential version

COMPLETED terminal
IN_DOUBT terminal
```

`REMOTE_STARTED` and `IN_DOUBT` never return to CLAIMED because a lease expired.

## Lease

Only CLAIMED is safely reclaimable by lease expiry.

REMOTE_STARTED may retain deadline metadata, but expiry never authorizes
same-version replay.

## Refresh result

After durable REMOTE_STARTED, provider refresh returns one of:

```text
REPLACEMENT
RETRYABLE_FAILURE
TERMINAL_FAILURE
INDETERMINATE
```

### REPLACEMENT

Contains only owned zeroizable opaque replacement credential bytes.

The bridge passes them to existing Control Plane:

```text
rotateCredential(
  organizationId,
  connectionId,
  expectedBindingVersion,
  replacementBytes
)
```

Mapping:

```text
ROTATED
  -> COMPLETED
  -> success ROTATED

ROTATED_CLEANUP_REQUIRED
  -> COMPLETED
  -> success ROTATED_CLEANUP_REQUIRED

STALE_VERSION
  -> COMPLETED as superseded
  -> failure CREDENTIAL_VERSION_CHANGED
```

No automatic refresh loop follows STALE_VERSION.

### RETRYABLE_FAILURE

Permitted only when provider semantics prove same-credential retry is safe and no
replacement was issued/consumed.

Transition to RETRYABLE with bounded `retry_not_before`.

### TERMINAL_FAILURE

Definitive provider rejection. Invalid refresh credential maps to
AUTHENTICATION_REQUIRED. No same-version loop.

### INDETERMINATE

Provider may have consumed/replaced the credential but no durable replacement is
known. Transition to IN_DOUBT.

A local exception after REMOTE_STARTED is IN_DOUBT unless non-mutation is proven.

## Public success

```text
READY
ROTATED
ROTATED_CLEANUP_REQUIRED
```

No secret/reference/provider account/token fields.

## Public failure

```text
CONNECTION_UNAVAILABLE
ROTATOR_UNAVAILABLE
AUTHENTICATION_REQUIRED
AUTHORIZATION_DENIED
RATE_LIMITED
REMOTE_TEMPORARY
REMOTE_PERMANENT
REMOTE_DATA_INVALID
BUDGET_EXCEEDED
CANCELLED
ROTATION_IN_PROGRESS
ROTATION_IN_DOUBT
CREDENTIAL_VERSION_CHANGED
INTERNAL
```

Only RATE_LIMITED, REMOTE_TEMPORARY, and ROTATION_IN_PROGRESS may carry bounded
retry hints.

Unexpected exceptions before remote start map INTERNAL. Uncertainty after remote
start maps ROTATION_IN_DOUBT.

## V020

Authorize exactly one additive migration:

```text
V020__create_credential_rotation_execution.sql
```

Create:

```text
integration_credential_rotation_execution
```

Minimum fields:

```text
organization_id
connection_id
binding_version
execution_id
state
claimed_at
lease_expires_at
remote_started_at nullable
retry_not_before nullable
terminal_at nullable
updated_at
```

Constraints:

- primary identity organization + connection + binding version;
- binding version positive;
- five frozen states only;
- composite FK to existing credential binding;
- execution ID cannot be accidentally reused across fences;
- no secret, secret reference, token, provider payload, or fingerprint column;
- `timestamptz` timestamps;
- fail-closed state-shape checks where practical.

No prior migration is modified.

## Postgres concurrency

Claim must atomically validate:

- active organization;
- active connection;
- expected current binding version;
- current claim state.

Concurrent first claims converge to one owner.

Expired CLAIMED can be reclaimed.

REMOTE_STARTED cannot be automatically reclaimed and returns/settles IN_DOUBT
after abandoned ownership.

RETRYABLE waits until retry_not_before.

Every query/update is organization scoped.

## Secret safety

Forbidden from V020, outcomes, errors, logs, metrics, traces, audit detail, and
test artifacts:

- credential bytes;
- access/refresh token;
- client secret;
- authorization code;
- secret reference;
- provider body;
- token fingerprint.

## Required tests

1. module forbidden-dependency gate;
2. malformed invocation/deadline fails closed;
3. unknown rotator fails before secret resolution;
4. static provider has no implicit rotator;
5. USABLE creates no claim;
6. AUTHENTICATION_REQUIRED creates no claim;
7. REFRESH_REQUIRED claims current binding version;
8. concurrent same-version execution makes at most one provider refresh attempt;
9. expired CLAIMED is reclaimable;
10. abandoned REMOTE_STARTED is not replayable and yields ROTATION_IN_DOUBT;
11. RETRYABLE respects retry_not_before;
12. retryable failure does not advance binding;
13. terminal auth failure never loops;
14. indeterminate result persists IN_DOUBT;
15. replacement increments exactly one Control Plane binding version;
16. old secret is revoked after successful rotation;
17. cleanup-required preserves new active binding;
18. stale replacement never overwrites newer binding;
19. local failure after remote start does not blind-retry;
20. cancellation/deadline before remote start prevents provider work;
21. organization isolation;
22. restart reproduces durable state semantics;
23. V020 contains no credential/secret-reference marker;
24. public outcomes expose no supplied secret/provider marker;
25. Integration Control Plane regression green;
26. Connector Runtime regression green without production change;
27. TASK-0149 provider regression green;
28. full build green.

## Exact authorized implementation paths

TASK-0150 may modify/create exactly these twelve paths:

1. MODIFY `settings.gradle.kts`

2. MODIFY
   `applications/integration-control-plane/src/main/kotlin/io/flooow/integration/control/IntegrationControlPlane.kt`

3. CREATE `applications/credential-rotation-execution/build.gradle.kts`

4. CREATE
   `applications/credential-rotation-execution/src/main/kotlin/io/flooow/integration/credential/CredentialRotationContracts.kt`

5. CREATE
   `applications/credential-rotation-execution/src/main/kotlin/io/flooow/integration/credential/CredentialRotationExecutor.kt`

6. CREATE
   `applications/credential-rotation-execution/src/test/kotlin/io/flooow/integration/credential/CredentialRotationExecutorTest.kt`

7. MODIFY
   `applications/marketplace-operations-persistence-postgres/build.gradle.kts`

8. CREATE
   `applications/marketplace-operations-persistence-postgres/src/main/resources/db/migration/V020__create_credential_rotation_execution.sql`

9. CREATE
   `applications/marketplace-operations-persistence-postgres/src/main/kotlin/io/flooow/marketplace/persistence/postgres/PostgresCredentialRotationExecutionStore.kt`

10. CREATE
    `applications/marketplace-operations-persistence-postgres/src/test/kotlin/io/flooow/marketplace/persistence/postgres/PostgresCredentialRotationExecutionStoreTest.kt`

11. MODIFY only for implementation evidence
    `docs/evidence/TASK-0150-provider-neutral-credential-rotation-execution-bridge.md`

12. APPEND exactly one implementation entry
    `docs/journal/MGI-EXECUTIVE-JOURNAL.md`

No thirteenth implementation path is authorized.

## Frozen

No TASK-0150 production change to:

- Connector Runtime;
- provider ingestion module;
- Mercado Livre or Omie provider code;
- OAuth authorization redirect/callback;
- real secrets/tokens;
- production vault implementation;
- scheduler/worker;
- API/UI;
- Economic Truth;
- Sales Intelligence;
- association/promotion;
- webhooks;
- provider writes;
- Kernel.

If a frozen path is required, implementation stops for governance amendment.

## Gates

```text
./gradlew :applications:integration-control-plane:test --no-daemon --console=plain
./gradlew :applications:credential-rotation-execution:compileKotlin --no-daemon --console=plain
./gradlew :applications:credential-rotation-execution:compileTestKotlin --no-daemon --console=plain
./gradlew :applications:credential-rotation-execution:test --no-daemon --console=plain
./gradlew :applications:marketplace-operations-persistence-postgres:compileKotlin --no-daemon --console=plain
./gradlew :applications:marketplace-operations-persistence-postgres:compileTestKotlin --no-daemon --console=plain
./gradlew :applications:marketplace-operations-persistence-postgres:test --no-daemon --console=plain
./gradlew :applications:connector-runtime:test --no-daemon --console=plain
./gradlew :applications:marketplace-economic-provider-ingestion:test --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
```

Repository CI must pass.

## Completion

TASK-0150 completes only after exact scope, concurrency, crash, privacy, tests, CI,
review, and merge gates pass.

Next: govern the Mercado Livre OAuth credential envelope and real refresh adapter,
then live read-only Mercado Livre economic ingestion.