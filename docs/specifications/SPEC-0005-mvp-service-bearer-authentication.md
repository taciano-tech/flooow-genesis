# SPEC-0005: MVP Service Bearer Authentication

**Status:** Proposed

**Date:** 2026-08-10

**Source decision:** ADR-0005

## Objective

Require one high-entropy service credential for every current business or
contract route while keeping health probes public and keeping authentication
outside the application domain and Kernel.

## Authorized implementation

Acceptance authorizes TASK-0059 only:

1. add Ktor 3.5.1 server authentication support to the API module;
2. load and validate one service token at process startup;
3. protect POST, GET-by-ID, and OpenAPI routes;
4. add exact challenge and problem responses;
5. update Compose, OpenAPI, documentation, and tests;
6. reproduce authenticated creation and retrieval through Compose.

No Marketplace Operations or Kernel production source may change.

## Credential configuration

Production startup requires:

```text
FLOOOW_SERVICE_TOKEN
```

The value must:

- contain at least 43 characters, sufficient to carry a base64url-encoded
  256-bit random value without padding;
- contain no leading or trailing whitespace;
- contain no ASCII control character;
- not equal the documented local-development placeholder.

Missing or invalid configuration prevents startup before the server binds its
port. Validation errors identify only the environment variable and violated
rule; they never include any token fragment, length, digest, or value.

The token is retained only in process memory. Comparison uses
`MessageDigest.isEqual` over UTF-8 bytes. Ordinary `String.equals`, prefix
matching, case folding, trimming, and partial-token acceptance are prohibited.

## Request contract

Clients send exactly one credential location:

```text
Authorization: Bearer <service-token>
```

Query parameters, cookies, request bodies, and custom headers are not accepted
as alternate credential locations. Scheme parsing follows the Ktor Bearer
provider. The token value is case-sensitive.

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

Authentication completes before request-body parsing, domain evaluation,
identifier allocation, database access, or OpenAPI resource loading.

## Failure contract

A missing, malformed, duplicated, wrong-scheme, or invalid credential returns:

```text
401 Unauthorized
WWW-Authenticate: Bearer realm="flooow-marketplace-operations"
Cache-Control: no-store
Content-Type: application/problem+json; charset=UTF-8
```

Body:

```json
{
  "type": "https://flooow.io/problems/authentication-required",
  "title": "Authentication required",
  "status": 401,
  "detail": "A valid service bearer token is required",
  "instance": "/v1/marketplace-operations/inventory-risk-assessments",
  "code": "AUTHENTICATION_REQUIRED"
}
```

Missing and invalid credentials are deliberately indistinguishable. The body
and headers contain no token, token fragment, parsing detail, expected length,
stack trace, or comparison result.

The first implementation has one privilege level. It therefore emits no 403:
a valid token can access every protected MVP route, and every other credential
is a 401 challenge. Later scopes or roles require a new specification.

## OpenAPI

The OpenAPI 3.1 document defines:

```text
components.securitySchemes.serviceBearer
type=http
scheme=bearer
```

Each protected operation references `serviceBearer` and includes 401. Health
operations explicitly use an empty security requirement. The served document
remains byte-equal to the committed resource after successful authentication.

## Compose and local development

Compose passes `FLOOOW_SERVICE_TOKEN` to the API. `.env.example` includes a
clearly labeled local-only value meeting the length rule. The Compose default
may use the same known value only to preserve one-command isolated local startup.

The README must state that the placeholder is public, provides no protection,
and must be replaced for any shared environment. CI always overrides it with a
separate CI-only value and sends that value in authenticated smoke requests.

No real secret is committed. Docker build arguments, image layers, labels, and
health checks must not contain the token.

## Transport requirement

Bearer requests outside isolated local development require HTTPS. The API does
not infer transport security from untrusted forwarding headers and does not
implement TLS termination. Deployment documentation must make the reverse-proxy
or platform TLS requirement explicit.

## Test plan

1. missing startup token is rejected without binding the server;
2. blank, short, whitespace-surrounded, control-containing, and documented
   placeholder production values are rejected;
3. valid high-entropy configuration is accepted;
4. missing Authorization returns the exact 401 challenge and problem;
5. invalid, truncated, extended, case-changed, Basic, and duplicated credentials
   return the same response;
6. unauthenticated POST never evaluates or writes business data;
7. unauthenticated GET never queries persistence;
8. authenticated POST preserves `201` and `Location`;
9. authenticated GET returns the committed representation;
10. authenticated OpenAPI equals the committed resource;
11. health routes remain public and do not execute authentication or business
    work;
12. logs, responses, OpenAPI, distributions, and image history contain no test
    token;
13. Compose authenticated POST/GET round trip succeeds under an overridden
    token;
14. existing frozen snapshots and Kernel production source remain unchanged.

## Rollout and rollback

Deployments must inject the new token before deploying the protected API.
Clients must be updated to send it in the same coordinated release. Rollback to
the anonymous API is permitted only as an explicit application rollback inside
an already isolated network; silently disabling authentication is prohibited.

Token rotation uses overlap at the deployment level: deploy a new process set
with the new token, move clients, then remove the old process set. Accepting two
tokens concurrently is outside the first implementation.

## Out of scope

- human login, passwords, sessions, account recovery, and MFA;
- JWT issuance or self-managed signing keys;
- OAuth 2.0, OpenID Connect, external identity providers, and JWKS;
- users, organizations, tenants, roles, permissions, and scopes;
- per-client credentials, token inventories, revocation, and online rotation;
- rate limiting, quotas, abuse detection, WAF, and denial-of-service controls;
- TLS certificates and reverse-proxy provisioning;
- security event export and compliance audit logs.

## Acceptance

Merging ADR-0005 and SPEC-0005 authorizes TASK-0059 only. Any user identity,
authorization role, multiple-token support, authentication endpoint, or domain
model change requires a new accepted specification.
