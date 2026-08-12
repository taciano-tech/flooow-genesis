# TASK-0070 Connector Runtime Contract Review

**Date:** 2026-08-11

## Result

**PROPOSED - ready for architecture, security, data-ingestion, and integration
review.**

ADR-0011 and SPEC-0011 define the smallest safe execution boundary between the
organization-scoped Integration Control Plane and future provider adapters.

## Repository evidence

- the control plane already owns provider connection identity, lifecycle, and
  scoped secret use;
- organization authority now propagates end to end through the active MVP data
  plane;
- no connector registry, capability contract, checkpoint, page budget, provider
  failure taxonomy, or canonical ingestion boundary exists;
- current outbox delivery coordinates Genesis events going outward and is not a
  substitute for reading provider data inward;
- no provider HTTP client, SDK, OAuth execution, webhook route, sync scheduler,
  or production worker exists;
- the Kernel has no provider, transport, credential, pagination, or tenancy
  dependency and must remain unchanged.

## Provider research evidence

- Mercado Livre uses OAuth bearer access, access-token expiry, and rotating
  one-use refresh tokens;
- Mercado Livre pagination is endpoint-dependent and includes offset/limit,
  ID-based positions, and scroll flows;
- Mercado Livre returns HTTP 429 under rate pressure and recommends bounded
  backoff with jitter and controlled concurrency;
- Mercado Livre notifications carry a topic and resource pointer, after which
  the application retrieves the authoritative resource;
- Omie authenticates JSON API requests with App Key and App Secret in the request
  body rather than an OAuth bearer header;
- Omie list and stock methods use numbered pages and report total pages;
- Omie documents limits by IP, App Key, and method, including a distinct block
  after repeated invalid requests;
- these differences make provider-owned credentials, progress, request shaping,
  and failure translation mandatory variation points.

## Decision evidence

- the runtime selects adapters by immutable provider key plus explicit capability;
- one invocation performs at most one bounded provider page;
- adapter progress is opaque, bounded, copied, zeroed, and absent from telemetry;
- a capability-owned committer atomically accepts typed records and advances
  progress with compare-and-set fencing;
- runtime results expose counts and controlled outcomes, never records, bodies,
  checkpoints, URLs, exceptions, or credentials;
- automatic retry, lifecycle mutation, OAuth, webhooks, mapping DSLs, durable
  storage, and production activation remain separate;
- TASK-0071 proves the mechanics using deterministic fakes only.

## Validation

```text
./gradlew build --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 8m 20s
131 tests, 0 failures, 0 errors, 0 skipped
```

This task changes documentation only. Existing API, persistence, delivery,
integration control plane, Marketplace Operations, research, and Kernel behavior
remain unchanged.

## Authorization boundary

Acceptance authorizes only TASK-0071's production-inactive Kotlin runtime,
control-plane access adaptation, deterministic in-memory fakes, and tests. It
does not authorize real credentials, provider registration, external traffic,
provider data ingestion, public routes, database changes, or business mutation.
