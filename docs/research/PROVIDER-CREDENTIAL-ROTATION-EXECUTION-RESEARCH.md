# Provider Credential Rotation Execution Research

Status: Validated research input for ADR-0051 / SPEC-0050

Date: 2026-09-06

## Question

What is the smallest provider-neutral execution boundary required to use rotating
OAuth credentials safely without moving OAuth lifecycle into Connector Runtime
or creating another secret authority?

## Repository evidence

Genesis already has:

- organization-scoped Integration Control Plane ownership;
- `SecretVault` custody with no secret bytes in PostgreSQL;
- versioned credential bindings;
- optimistic `rotateCredential(expectedVersion, credentialBytes)`;
- provider-neutral Connector Runtime with scoped credential access;
- durable provider page/progress commits;
- live read-only Omie product-cost source observation from TASK-0149.

The current Control Plane safely performs local replacement:

```text
store new secret
-> expected binding version CAS
-> swap binding
-> revoke old secret
```

ADR-0011 explicitly keeps OAuth authorization and refresh outside pull execution.

## Current Mercado Livre evidence

Current official Mercado Livre authentication documentation states that:

- access tokens are short-lived;
- refresh tokens are used to obtain new access tokens;
- a refresh token is single-use;
- every successful refresh returns a new refresh token;
- only the latest generated refresh token is valid for the next exchange;
- `invalid_grant` can mean a refresh token is expired, revoked, nonexistent, or
  already used.

Sources:

```text
https://developers.mercadolivre.com.br/autenticacao-e-autorizacao
https://developers.mercadolivre.com.br/en_us/authentication-and-authorization
```

Mercado Livre application governance also changed in 2026 so Mercado Livre and
Mercado Pago application scopes must be separated. That is a later provider
activation check, not a generic bridge concern.

```text
https://developers.mercadolivre.com.br/pt_br/gerencie-seu-aplicativo
```

RFC 9700 / BCP 240 treats refresh tokens as high-value credentials and describes
refresh-token rotation as a replay-detection mechanism.

```text
https://www.rfc-editor.org/rfc/rfc9700
```

## Finding 1 - post-refresh CAS is too late

A naive sequence can race:

```text
worker A reads binding version 7
worker B reads binding version 7

A refreshes remote token 7
B refreshes remote token 7

A CASes local binding 7 -> 8
B loses local CAS
```

Local CAS prevented two durable bindings but did not prevent two remote uses of a
single-use refresh token.

Correctness therefore requires a durable fence before the remote refresh request.

## Finding 2 - fence identity

The authority key is:

```text
organization_id
+ connection_id
+ binding_version
```

Only one execution may own the right to cross the remote side-effect boundary for
that credential version.

A process-local mutex is insufficient because it does not survive restart or
coordinate multiple workers.

## Finding 3 - exactly-once is impossible across provider + vault + database

No local transaction can atomically include an external OAuth token endpoint,
secret vault, and PostgreSQL.

Safe states are:

```text
CLAIMED
REMOTE_STARTED
RETRYABLE
COMPLETED
IN_DOUBT
```

`CLAIMED` may be reclaimed after lease expiry because no remote side effect has
started.

An abandoned `REMOTE_STARTED` must never be blindly replayed for the same binding
version. The provider may already have consumed the one-time token.

`IN_DOUBT` is therefore a first-class safety state, not an error hidden by retry.

## Finding 4 - retry must be proven

A provider-specific rotator may mark an attempt retryable with the same credential
only when it can prove no replacement was issued/consumed.

Network timeout, reset after transmission, malformed/lost success response,
uncertain 5xx, or process crash after remote start are not automatically
retryable.

`invalid_grant` is terminal for the current credential version and requires a new
credential authority or reauthorization.

## Finding 5 - separation

The correct composition is:

```text
Credential Rotation Executor
-> credential READY

then

Connector Runtime
-> provider data read
```

Credential version and connector progress are separate concurrency domains.

Static Omie credentials remain unchanged.

## Secret custody

The execution store must never contain:

- access token;
- refresh token;
- client secret;
- secret reference;
- authorization code;
- PKCE verifier;
- provider response/body;
- credential fingerprint.

Replacement bytes go directly through the existing Control Plane / `SecretVault`
rotation path.

## Result

Introduce a separate provider-neutral Credential Rotation Execution Bridge before
Mercado Livre live activation.

TASK-0150 should prove durable pre-remote fencing and explicit in-doubt semantics
using deterministic fake rotators only.