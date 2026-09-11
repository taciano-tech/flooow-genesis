# TASK-0059 Service Bearer Authentication Reproduction

**Date:** 2026-08-10

## Result

**PASS - the accepted service authentication boundary is implemented and
reproduced through the persistent Docker package.**

## Build evidence

```text
./gradlew build --rerun-tasks --no-daemon
BUILD SUCCESSFUL
36 actionable tasks: 36 executed
```

The build includes 18 API tests plus the PostgreSQL integration suite.

## Authentication evidence

- health readiness remained public and returned `{"status":"UP"}`;
- missing and invalid credentials returned `401`;
- the challenge was
  `WWW-Authenticate: Bearer realm=flooow-marketplace-operations`;
- authentication failures included `Cache-Control: no-store` and the frozen
  `AUTHENTICATION_REQUIRED` problem;
- missing, Basic, wrong, case-changed, truncated, and duplicated credentials
  were indistinguishable;
- unauthenticated POST and GET requests did not execute business evaluation or
  persistence lookup;
- OpenAPI required a valid token and exposed the `serviceBearer` scheme.

## Persistent reproduction

Compose ran with a non-placeholder reproduction token on host port `18081`.
An authenticated POST created assessment
`773afbc1-6e04-41ef-9f30-0974d7b31a90`; an authenticated GET retrieved the
same persisted identifier.

```text
ready=UP
anonymousStatus=401
openApiScheme=bearer
persistedMatch=true
```

The reproduction token was absent from built API artifacts and the Docker image
history. Containers, network, and PostgreSQL volume were removed after the run.

## Boundary evidence

- no Kernel production source changed;
- no user, tenant, role, scope, issuer, JWT, or login endpoint was introduced;
- the credential represents a machine API client only;
- TLS remains mandatory outside isolated local development and is terminated by
  deployment infrastructure.
