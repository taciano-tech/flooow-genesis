# Mercado Livre OAuth Credential Refresh Research

Status: Research complete for TASK-0151 governance
Date: 2026-09-06

## Repository baseline

TASK-0150 is merged. Integration Control Plane still owns SecretVault custody and
versioned credential replacement. Credential Rotation Execution owns the durable
pre-remote binding-version fence, REMOTE_STARTED, RETRYABLE, COMPLETED, and
IN_DOUBT. Connector Runtime remains provider-neutral.

## Official contract rechecked

Primary documentation:

```text
https://developers.mercadolivre.com.br/autenticacao-e-autorizacao
https://developers.mercadolivre.com.br/en_us/authentication-and-authorization
https://developers.mercadolivre.com.br/controle-de-acesso-e-autorizacao
```

Refresh is:

```text
POST https://api.mercadolibre.com/oauth/token
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token
client_id
client_secret
refresh_token
```

Current official semantics:
- refresh token is single-use;
- only the latest generated refresh token is valid;
- it is bound to client_id;
- success returns a new access token and a new refresh token;
- invalid_grant can mean expired, revoked, deleted, or already-used credential;
- refresh-token expiration is documented as six months;
- account/security/authorization events may invalidate tokens early.

## Access-token lifetime

Official examples/prose currently contain both `expires_in=10800`,
`expires_in=21600`, and prose saying six hours. Genesis therefore must not
hard-code three or six hours. The provider `expires_in` response is used to
derive `accessTokenExpiresAt`.

## Envelope

One opaque Control Plane credential value contains:

```text
schemaVersion
clientId
clientSecret
authorizedUserId
accessToken
refreshToken
accessTokenExpiresAt
```

It does not contain authorization code, redirect URI, PKCE material, advertiser
ID, economic subject, connector progress, or raw token response.

Provider identity is exactly:

```text
br.com.mercadolivre
OAUTH2_AUTHORIZATION_CODE
```

## Local assessment

```text
valid envelope + now < expiry  -> USABLE
valid envelope + now >= expiry -> REFRESH_REQUIRED
malformed/incomplete envelope  -> AUTHENTICATION_REQUIRED
```

No network call occurs during assessment.

## Remote safety mapping

After TASK-0150 has persisted REMOTE_STARTED:

```text
valid 2xx replacement                 -> REPLACEMENT
400 invalid_grant / 401              -> terminal AUTHENTICATION_REQUIRED
403                                  -> terminal AUTHORIZATION_DENIED
explicit 429                         -> retryable RATE_LIMITED
other definitive 4xx                 -> terminal REMOTE_PERMANENT
timeout / I/O / 5xx                  -> INDETERMINATE
malformed 2xx                        -> INDETERMINATE
missing replacement refresh token    -> INDETERMINATE
mismatched authorized user           -> INDETERMINATE
```

A malformed success is not ordinary REMOTE_DATA_INVALID: the old single-use
refresh token may already have been consumed.

Once REMOTE_STARTED is durable, cooperative cancellation must not voluntarily
abandon the one bounded token request; the deadline remains the bound.

## Module

Create a narrow provider-edge module:

```text
applications:marketplace-provider-authentication
```

Allowed dependencies:
- credential-rotation-execution;
- integration-control-plane;
- kotlinx-serialization-json.

No Connector Runtime, Marketplace Operations, provider economic ingestion,
Postgres/JDBC, server framework, scheduler, or Kernel dependency.

## Conclusion

TASK-0151 can implement a real Mercado Livre refresh adapter without changing
Connector Runtime or creating a second credential authority. Live provider data
ingestion remains the next separately governed task.