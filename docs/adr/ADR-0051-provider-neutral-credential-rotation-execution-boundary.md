# ADR-0051: Provider-Neutral Credential Rotation Execution Boundary

Status: Accepted

Date: 2026-09-06

## Context

TASK-0149 completed live read-only Omie provider ingestion with static
credentials.

Mercado Livre OAuth uses short-lived access tokens and rotating single-use refresh
tokens. Genesis already has local versioned credential replacement, but a CAS
performed after a provider refresh cannot stop two workers from trying the same
one-time remote refresh token.

ADR-0011 intentionally keeps OAuth refresh outside Connector Runtime.

## Decision

Introduce a separate provider-neutral Credential Rotation Execution Boundary.

```text
active provider connection
-> local credential assessment
-> durable binding-version claim
-> REMOTE_STARTED fence
-> at most one provider credential refresh attempt
-> existing Control Plane versioned replacement
-> durable execution outcome
```

Connector Runtime remains unchanged.

## Authority split

Integration Control Plane remains authority for:

- organization/connection lifecycle;
- provider and credential kind;
- current binding version;
- scoped secret resolution;
- `SecretVault`;
- final versioned binding replacement;
- old-secret revocation;
- credential-rotation audit.

Credential Rotation Execution Bridge owns:

- durable pre-remote fencing;
- claim lease before remote start;
- one-attempt bounded execution;
- retry-not-before coordination;
- explicit `IN_DOUBT` semantics;
- controlled secret-safe outcomes.

Provider-specific rotator owns:

- credential-envelope parsing;
- local expiry/readiness assessment;
- token endpoint request;
- provider error interpretation;
- proof that a failed attempt is safe to retry with the same credential.

Connector Runtime continues to own provider data-page execution only.

## Fence identity

```text
organization_id
+ connection_id
+ binding_version
```

The binding version represents one credential authority epoch.

## State model

Freeze:

```text
CLAIMED
REMOTE_STARTED
RETRYABLE
COMPLETED
IN_DOUBT
```

### CLAIMED

No provider request has started. A bounded lease applies. An expired claim may be
reclaimed.

### REMOTE_STARTED

The external side-effect boundary has been crossed. Expiry must never authorize a
blind replay of the same credential version.

### RETRYABLE

Provider-specific evidence proved that no replacement credential was issued or
consumed and same-credential retry is safe. Retry waits until
`retry_not_before`.

### COMPLETED

Terminal for the old binding version.

### IN_DOUBT

The provider may have consumed/replaced the credential but Genesis did not
durably establish a replacement. No blind same-version retry is allowed.

Recovery requires a newer credential authority, separately proven provider
reconciliation, or reauthorization.

## No exactly-once fiction

Genesis does not claim atomicity across:

```text
OAuth token endpoint
+ secret vault
+ PostgreSQL
```

The system prevents known concurrent duplication and explicitly records
uncertainty when a crash can occur after a remote side effect.

## Retry rule

After `REMOTE_STARTED`, generic timeout/transport failure is not automatically
retryable.

Only explicit provider proof of same-credential retry safety may transition to
`RETRYABLE`.

Uncertain outcomes become `IN_DOUBT`.

A definitive invalid current credential becomes `AUTHENTICATION_REQUIRED`.

## Secret rule

The execution store contains no secret material and no secret reference.

Replacement bytes flow only:

```text
provider rotator
-> Integration Control Plane rotateCredential
-> SecretVault
```

## Static credentials

Static providers do not acquire refresh behavior implicitly. Omie remains
unchanged.

Unknown provider/credential-kind rotators fail before secret resolution or
network use.

## Public failure direction

At minimum:

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

Only explicitly retryable outcomes may carry a bounded retry hint.

## Alternatives rejected

### Refresh inside PullConnector

Rejected because credential lifecycle and provider-page progress are separate
transactional domains.

### Only CAS after remote refresh

Rejected because the provider may already have consumed a one-time token.

### Process-local mutex

Rejected because it does not survive restart or coordinate multiple processes.

### Hold a PostgreSQL transaction across HTTP

Rejected because it holds transactional resources across external latency and
still cannot create distributed atomicity.

### Retry every timeout/5xx

Rejected because the provider may already have consumed the refresh token.

## Authorization

SPEC-0050 freezes TASK-0150.

This ADR authorizes no Mercado Livre HTTP call, real OAuth credential,
authorization callback, scheduler, API/UI, provider write, or business evidence
mutation.