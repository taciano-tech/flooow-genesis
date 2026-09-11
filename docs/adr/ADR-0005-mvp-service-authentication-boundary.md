# ADR-0005: MVP Service Authentication Boundary

Status: Proposed

Date: 2026-08-10

## Context

The persistent Marketplace Operations API currently accepts business requests
from any network client that can reach it. The MVP has no end-user accounts,
tenant model, identity provider, role model, or authorization server.

Introducing user authentication, self-issued JWTs, or a login endpoint would
invent identity concepts the product does not yet own. Leaving business routes
anonymous would make inventory assessments and their durable identifiers
available without even a client boundary.

## Decision

Introduce one machine-to-machine service authentication boundary using the HTTP
Bearer scheme and Ktor's maintained authentication plugin.

The credential authenticates an API client, not a human user. It grants access
to the complete current MVP business surface and carries no user identity,
tenant, role, scope, or business authority claim.

Protected routes:

```text
POST /v1/marketplace-operations/inventory-risk-assessments
GET  /v1/marketplace-operations/inventory-risk-assessments/{assessmentId}
GET  /openapi.json
```

Public routes:

```text
GET /health/live
GET /health/ready
```

The service token is supplied at startup through `FLOOOW_SERVICE_TOKEN`. The
application does not mint, return, persist, hash to the database, or log tokens.

## Transport boundary

Bearer credentials are permitted over plaintext HTTP only in isolated local
development. Every shared, staging, or production deployment must terminate TLS
before traffic reaches the API. TLS provisioning remains an infrastructure
responsibility and is not simulated inside Ktor in this task.

## Alternatives considered

### Anonymous MVP

Rejected because persistence raised the impact of unauthorized reads and
writes. Network isolation alone is not an application authentication boundary.

### API key presented in a custom header

Rejected in favor of the standard Bearer request header and challenge semantics.
The credential still represents only an API client.

### Self-issued JWT

Rejected because a JWT without an external issuer, rotation policy, audience,
expiry, and key lifecycle would add complexity without adding trustworthy
identity.

### OAuth 2.0 or OpenID Connect now

Deferred until Flooow has users, tenants, delegated authorization, or an
accepted identity provider. That later integration replaces the verifier behind
the boundary without changing protected business routes.

## Consequences

### Positive

- anonymous business access is closed;
- the protocol uses standard HTTP Bearer semantics;
- health probes remain operational without credentials;
- the boundary can later accept externally issued access tokens;
- no authentication concept enters Marketplace Operations or the Kernel.

### Negative

- one static service token has coarse-grained access;
- rotation requires coordinated secret replacement and process restart;
- the application cannot identify individual humans or distinguish clients;
- rate limiting, revocation, audit identity, TLS, and secret-manager injection
  remain operational follow-up work.

## Authorization

This ADR does not authorize implementation alone. SPEC-0005 must define token
requirements, response semantics, configuration, tests, and rollout before the
runtime changes.
