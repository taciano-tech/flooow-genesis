# TASK-0151: Mercado Livre OAuth Credential Envelope and Refresh Adapter

Status: Authorized
Date: 2026-09-06
Governing ADR: ADR-0052
Specification: SPEC-0051

## Objective

Implement the real Mercado Livre OAuth envelope and one-attempt refresh adapter
over TASK-0150 without changing Connector Runtime or creating a second credential
authority.

## Acceptance

- exact `br.com.mercadolivre` descriptor;
- strict bounded redacted envelope;
- expiry from provider `expires_in`, never hard-coded;
- single-use refresh uncertainty -> INDETERMINATE;
- malformed successful response never blind-retries;
- invalid_grant -> authentication-required;
- no real token/secret committed;
- no live provider call in tests;
- all SPEC-0051 gates pass;
- exactly eight implementation paths change;
- CI/review/merge complete.

No callback, provider data read, migration, scheduler, UI, or provider write is
part of TASK-0151.
## Implementation evidence

Implementation branch:

```text
feat/task-0151-mercado-livre-oauth-refresh
```

Implemented boundary:

```text
existing Control Plane credential envelope
  -> TASK-0150 local assessment
  -> durable REMOTE_STARTED fence
  -> one Mercado Livre token refresh HTTP request
  -> strict response validation
  -> opaque replacement credential
  -> existing Control Plane versioned rotation
```

Implementation preserves the frozen authority model:

- provider descriptor is `br.com.mercadolivre` +
  `OAUTH2_AUTHORIZATION_CODE`;
- credential envelope V1 is strict, bounded, and redacted;
- provider `expires_in` derives the new access-token expiry;
- refresh tokens are treated as single-use;
- malformed/uncertain successful responses become `INDETERMINATE`;
- timeout, I/O, 408/425, and 5xx after remote start become `INDETERMINATE`;
- `invalid_grant` and 401 become terminal authentication-required;
- 403 becomes terminal authorization-denied;
- explicit 429 is the only provider rate-limit retry path;
- no automatic retry loop exists;
- default JDK transport follows no redirects;
- no test contacts the real Mercado Livre endpoint;
- no Connector Runtime, Postgres, provider-ingestion, Economic Truth, or Sales
  Intelligence production code changes.
- organization-context is present only as a test-fixture dependency for the required Control Plane integration test; it is not a production dependency.

Local SPEC-0051 gates are run before commit and push.

Repository CI, review, and merge remain required completion gates.