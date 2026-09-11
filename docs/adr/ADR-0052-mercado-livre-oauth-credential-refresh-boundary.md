# ADR-0052: Mercado Livre OAuth Credential Refresh Boundary

Status: Accepted
Date: 2026-09-06

## Decision

Create `applications:marketplace-provider-authentication`.

TASK-0151 implements only the Mercado Livre OAuth credential envelope and real
one-attempt refresh adapter.

Descriptor:

```text
providerKey = br.com.mercadolivre
credentialKind = OAUTH2_AUTHORIZATION_CODE
```

Integration Control Plane remains sole authority for organization/connection
lifecycle, SecretVault custody, binding version, final replacement, old-secret
revoke, and audit.

Credential Rotation Execution remains authority for the pre-remote fence,
REMOTE_STARTED, retry-not-before, IN_DOUBT, and public rotation outcome.

The provider adapter owns only envelope validation, local expiry assessment,
token endpoint mechanics, response validation, failure interpretation, and
replacement opaque credential bytes.

No second vault, token table, credential binding, lease, or retry loop exists.

## Envelope V1

```text
schemaVersion
clientId
clientSecret
authorizedUserId
accessToken
refreshToken
accessTokenExpiresAt
```

Do not hard-code access-token lifetime; derive expiry from provider `expires_in`.

## Failure policy

- valid 2xx -> REPLACEMENT;
- invalid_grant/401 -> terminal AUTHENTICATION_REQUIRED;
- 403 -> terminal AUTHORIZATION_DENIED;
- explicit 429 -> retryable RATE_LIMITED;
- other definitive 4xx -> terminal REMOTE_PERMANENT;
- timeout/I/O/5xx -> INDETERMINATE;
- malformed or semantically invalid 2xx -> INDETERMINATE.

Replacement `user_id` must equal stored `authorizedUserId`.

## HTTP

Default endpoint:

```text
https://api.mercadolibre.com/oauth/token
```

HTTPS POST, form-urlencoded, redirect NEVER, bounded timeout/response, no
automatic retry, injectable transport, no request/response body logging.

## Not authorized

OAuth redirect/callback, authorization-code exchange, PKCE persistence, real
credential bootstrap, provider data reads/writes, scheduler, migration, API/UI,
Economic Truth, Sales Intelligence, or Kernel changes.