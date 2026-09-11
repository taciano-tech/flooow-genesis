# TASK-0150: Provider-Neutral Credential Rotation Execution Bridge

Status: Authorized for implementation

Date: 2026-09-06

## Authority

Governed by:

- Knowledge Governance;
- ADR-0009 / SPEC-0009 Integration Control Plane;
- ADR-0011 / SPEC-0011 Connector Runtime;
- ADR-0050 / SPEC-0049 live provider economic evidence;
- ADR-0051 Provider-Neutral Credential Rotation Execution Boundary;
- SPEC-0050 Provider-Neutral Credential Rotation Execution Bridge;
- completed TASK-0149.

SPEC-0050 is normative.

## Why next

TASK-0149 proved the first real provider data path with static Omie credentials.

Mercado Livre requires rotating single-use OAuth refresh credentials. Existing
Control Plane binding CAS is necessary but cannot prevent duplicate remote refresh
attempts before the local replacement exists.

The missing capability is a durable pre-remote execution fence, not another token
store and not a PullConnector change.

## Objective

Implement:

```text
local credential assessment
-> durable binding-version claim
-> REMOTE_STARTED fence
-> deterministic fake refresh outcome
-> existing Control Plane versioned replacement
-> durable execution outcome
```

No real provider request.

## Critical invariant

For one:

```text
organization + connection + binding version
```

at most one execution may cross REMOTE_STARTED.

Abandoned REMOTE_STARTED work must never cause blind same-version replay.

Uncertainty is represented as IN_DOUBT.

## Scope

Exactly twelve implementation paths frozen by SPEC-0050.

Exactly one additive migration:

```text
V020__create_credential_rotation_execution.sql
```

No previous migration edit.

## Provider boundary

TASK-0150 uses deterministic fake rotators only.

No Mercado Livre endpoint, client ID, client secret, access token, refresh token,
authorization code, or real account value.

## MGI reuse

Historical MGI proves that Mercado Livre OAuth refresh worked operationally.

Do not port `.mgi` token files, local JSON token persistence, secrets, or old
provider-specific orchestration.

Genesis retains Control Plane custody and adds durable execution fencing.

## Gates

Run all SPEC-0050 gates plus exact 12-path validation and `git diff --check`.

## Completion

Complete only after local gates, repository CI, clean review, and merge.

Next: Mercado Livre OAuth credential envelope + real refresh adapter, then live
read-only Mercado Livre economic ingestion.
## Implementation evidence

Implementation branch:

```text
feat/task-0150-credential-rotation-execution
```

Implemented flow:

```text
active credential metadata
-> provider/kind rotator resolution before secret use
-> scoped local assessment
-> durable binding-version claim
-> durable REMOTE_STARTED fence
-> one bounded fake refresh result
-> existing Integration Control Plane rotateCredential
-> durable COMPLETED / RETRYABLE / IN_DOUBT
```

The implementation keeps Connector Runtime unchanged, makes no real provider HTTP
request, creates no second vault/binding, stores no secret/reference in V020,
allows lease recovery only before remote start, prevents stale versions from
crossing REMOTE_STARTED, and converts abandoned remote-started work to IN_DOUBT.

Local gates run before commit/push. Repository CI and merge remain completion
gates.