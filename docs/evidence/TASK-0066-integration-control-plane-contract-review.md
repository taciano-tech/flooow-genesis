# TASK-0066 Integration Control Plane Contract Review

**Date:** 2026-08-10

## Result

**PROPOSED - ready for tenancy, security, and integration review.**

ADR-0009 and SPEC-0009 define the minimum company-isolated control plane before
any real ERP or marketplace authorization is attempted.

## Repository evidence

- no organization, tenant, connection, provider registry, or credential model
  exists in production code;
- the current bearer authenticates one technical client and conveys no company
  or delegated authority;
- delivery destination IDs are validated but intentionally opaque and unregistered;
- assessments, CloudEvents, outbox records, and delivery rows currently have no
  organization scope and therefore cannot be routed to a company safely;
- no token, API key, OAuth SDK, vault client, or provider adapter exists;
- the Kernel contains no accepted integration tenancy concept.

## Research evidence

- RFC 9700 requires CSRF protection, exact redirect matching, TLS, minimum
  privilege, and modern Authorization Code protections;
- PKCE is recommended for confidential clients as well as required for public
  clients, with `S256` as the non-leaking challenge method;
- refresh tokens require confidentiality in transit and storage and may require
  rotation or sender constraint;
- provider credentials express protocol access, not Genesis business authority.

## Decision evidence

- organization scope is application-owned and absent from the Kernel;
- all child relationships and repository operations are organization-scoped;
- PostgreSQL stores only opaque secret references;
- secret bytes live behind a vault port and are zeroed after scoped use;
- rotation uses store, commit swap, then old-secret revocation;
- destinations become registered control-plane resources but remain disconnected
  from global events until organization identity is propagated end to end;
- TASK-0067 remains production-inactive and provider-neutral.

## Repository validation

```text
./gradlew build --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 3m 44s
37 actionable tasks: 37 executed
120 tests, 0 failures, 0 errors, 0 skipped
```

The proposal changes documentation only. Existing API, persistence, delivery,
tracing, Marketplace Operations, research, and Kernel behavior remain green.

## Authorization boundary

Merging this proposal authorizes only the isolated control-plane library,
PostgreSQL adapter, fake vault, and tests. It does not authorize real credentials,
OAuth flows, users, administration endpoints, or provider network access.
