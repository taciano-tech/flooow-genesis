# SPEC-0051: Mercado Livre OAuth Credential Envelope and Refresh Adapter

Status: Accepted
Date: 2026-09-06
Governing ADR: ADR-0052
Implementation task: TASK-0151

## Objective

Implement a production-capable but production-inactive Mercado Livre OAuth
credential envelope and one-attempt refresh adapter over TASK-0150.

## Module

Create:

```text
applications:marketplace-provider-authentication
```

Allowed:
- applications:credential-rotation-execution
- applications:integration-control-plane
- kotlinx-serialization-json
- JDK HTTP.

Forbidden: Connector Runtime, Marketplace Operations, provider economic
ingestion, JDBC/Postgres, API/server framework, scheduler, Kernel.

## Descriptor

Exactly:

```text
ProviderKey.of("br.com.mercadolivre")
CredentialKind.OAUTH2_AUTHORIZATION_CODE
```

## Envelope V1

JSON contains exactly:

```text
schemaVersion
clientId
clientSecret
authorizedUserId
accessToken
refreshToken
accessTokenExpiresAt
```

Strict/bounded values, no unknown fields, encoded size no larger than
ReplacementCredential.MAX_BYTES, redacted rendering, no secret marker in public
errors/outcomes.

## Assessment

```text
now < accessTokenExpiresAt  -> USABLE
now >= accessTokenExpiresAt -> REFRESH_REQUIRED
malformed envelope          -> AUTHENTICATION_REQUIRED
```

No hard-coded token TTL.

## Refresh request

Exactly one POST to `https://api.mercadolibre.com/oauth/token` with form fields:

```text
grant_type=refresh_token
client_id
client_secret
refresh_token
```

No authorization-code, redirect URI, PKCE, economic, or connector-progress field.

## Successful response

Require bounded:
- access_token;
- bearer token_type;
- positive expires_in;
- positive user_id matching authorizedUserId;
- replacement refresh_token.

Replacement preserves client identity/secret and authorized user, replaces
access/refresh tokens, and sets expiry from response-received time + expires_in.

## Failure mapping

```text
2xx valid                       -> REPLACEMENT
400 invalid_grant / 401        -> TERMINAL AUTHENTICATION_REQUIRED
403                            -> TERMINAL AUTHORIZATION_DENIED
429                            -> RETRYABLE RATE_LIMITED
other definitive 4xx           -> TERMINAL REMOTE_PERMANENT
timeout / I/O / 5xx            -> INDETERMINATE
malformed/invalid 2xx           -> INDETERMINATE
user mismatch                   -> INDETERMINATE
```

No internal retry. No test contacts real Mercado Livre.

## Required tests

Envelope strictness/round-trip/redaction/size; USABLE/REFRESH_REQUIRED/AUTH
assessment; exact form request; HTTPS; valid replacement; expires_in-derived
expiry; user mismatch; missing refresh token; malformed 2xx; invalid_grant; 401;
403; 429; 5xx; timeout/I/O; response bound; no internal retry; no secret marker;
bridge integration rotating exactly one binding; TASK-0150, Control Plane,
Connector Runtime, TASK-0149 Omie, and full-build regressions.

## Exact authorized implementation paths

Exactly eight:

1. MODIFY `settings.gradle.kts`
2. CREATE `applications/marketplace-provider-authentication/build.gradle.kts`
3. CREATE `applications/marketplace-provider-authentication/src/main/kotlin/io/flooow/integration/provider/mercadolivre/MercadoLivreOAuthCredentialEnvelope.kt`
4. CREATE `applications/marketplace-provider-authentication/src/main/kotlin/io/flooow/integration/provider/mercadolivre/MercadoLivreOAuthCredentialRotator.kt`
5. CREATE `applications/marketplace-provider-authentication/src/test/kotlin/io/flooow/integration/provider/mercadolivre/MercadoLivreOAuthCredentialEnvelopeTest.kt`
6. CREATE `applications/marketplace-provider-authentication/src/test/kotlin/io/flooow/integration/provider/mercadolivre/MercadoLivreOAuthCredentialRotatorTest.kt`
7. MODIFY only for implementation evidence `docs/evidence/TASK-0151-mercado-livre-oauth-credential-envelope-and-refresh-adapter.md`
8. APPEND one TASK-0151 implementation entry `docs/journal/MGI-EXECUTIVE-JOURNAL.md`

No ninth path is authorized.

## Frozen

Connector Runtime, Integration Control Plane, credential-rotation-execution,
provider economic ingestion, Postgres/migrations, Marketplace Operations,
OAuth callback/API, scheduler, Economic Truth, Sales Intelligence,
association/promotion, provider writes, API/UI, Kernel.

## Gates

```text
./gradlew :applications:marketplace-provider-authentication:test --no-daemon --console=plain
./gradlew :applications:credential-rotation-execution:test --no-daemon --console=plain
./gradlew :applications:integration-control-plane:test --no-daemon --console=plain
./gradlew :applications:connector-runtime:test --no-daemon --console=plain
./gradlew :applications:marketplace-economic-provider-ingestion:test --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
```

Repository CI must pass.

Next: separately govern live read-only Mercado Livre economic evidence ingestion.