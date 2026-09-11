# TASK-0156B: Sales Intelligence API + Controlled Refresh

Status: Authorized
Date: 2026-09-07
Governing ADR: ADR-0058
Specification: SPEC-0057

Objective: make the durable live economic backend safely consumable by the first
Flooow MVP UI.

Exactly seven implementation paths. No migration.

Next critical path: TASK-0157 MVP UI.

## Implementation evidence

The existing authenticated API now exposes exactly the three authorized Sales
Intelligence endpoints. List and detail read only the durable organization-scoped
projection. The HTTP adapter renders the closed unresolved, calculated-complete,
and calculated-incomplete states without reconstructing or mutating Economic
Truth and without exposing provider source identity.

Pagination preserves the existing bounded keyset contract (default 50, range
1..200). Its continuation token is versioned, HMAC-authenticated, canonically
encoded, and bound to the authenticated organization. Its signing key is
domain-separated from the server-owned runtime master material and is never
derived from the caller-visible bearer credential. Projection results that
violate organization, page-size, subject, or continuation invariants fail closed.

Refresh invokes exactly one existing TASK-0155 pipeline run with the server-owned
Mercado Livre connection and a two-minute deadline. Its response contains only
sanitized stage status and counts. Production composition reuses the accepted
Control Plane, encrypted secret/progress runtime, Connector Runtime, Mercado
Livre source adapter, PostgreSQL promotion/evidence/feed/projection adapters,
and live pipeline coordinator. No provider call, OAuth refresh, economic
calculation, scheduler, background execution, or canonical write was added to
the HTTP layer. Production startup requires `FLOOOW_SECRET_VAULT_PATH` for the
already-governed encrypted file vault; it does not silently choose a persistence
location.

The deterministic network-free API tests cover authentication ordering,
server-owned organization and connection scope, cursor tamper and organization
binding, bounded limits, all projection states, missing-versus-zero rendering,
read fail-closed behavior, cross-organization adapter corruption, one-run refresh
semantics, the two-minute deadline, sanitized summaries, stable errors, and
strict runtime connection configuration.

Local acceptance completed successfully:

- `:applications:marketplace-operations-api:compileKotlin`;
- `:applications:marketplace-operations-api:compileTestKotlin`;
- `:applications:marketplace-operations-api:test` (28 tests);
- `:applications:marketplace-live-pipeline:test` (13 tests);
- `:applications:marketplace-operations:test` (284 tests);
- `:applications:marketplace-operations-persistence-postgres:test` (124 tests);
- `build`;
- committed OpenAPI JSON parse;
- `git diff --check`;
- exactly seven authorized implementation paths and no migration.
