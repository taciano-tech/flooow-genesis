# TASK-0058 Service Authentication Specification Review

**Date:** 2026-08-10

## Result

**PROPOSED - ready for security and architectural review.**

ADR-0005 and SPEC-0005 define the first authentication boundary for the
persistent MVP without inventing users, tenants, roles, or a token issuer.

## Research evidence

- OWASP API2:2023 identifies missing or weak microservice authentication as a
  severe API risk and distinguishes API-client keys from user authentication;
- RFC 6750 defines Bearer transmission through the Authorization header and
  requires TLS to protect reusable bearer credentials;
- RFC 9110 requires a `WWW-Authenticate` challenge on 401 and reserves 403 for
  valid credentials that are insufficient;
- Ktor 3.5 documents a maintained Bearer provider with custom token validation
  and route-level protection.

## Scope evidence

- documentation only;
- no production source, Gradle dependency, OpenAPI resource, fixture, snapshot,
  Compose behavior, or CI workflow changed;
- service credential authenticates only machine clients;
- health remains public while business and OpenAPI routes are protected;
- constant-time comparison and indistinguishable failures are mandatory;
- TLS is mandatory outside isolated local development;
- identity-provider, user, tenant, role, scope, rate-limit, and TLS provisioning
  work remains explicitly deferred.

## Repository validation

```text
./gradlew clean build --rerun-tasks --no-daemon
BUILD SUCCESSFUL
```

The complete repository build passed on 2026-08-10, including the PostgreSQL
integration tests executed against the local Docker daemon.

## Authorization boundary

Merging this proposal accepts ADR-0005 and SPEC-0005 and authorizes TASK-0059
only: implementation and reproduction of the service Bearer boundary. It does
not authorize any Marketplace Operations or Kernel production change.
