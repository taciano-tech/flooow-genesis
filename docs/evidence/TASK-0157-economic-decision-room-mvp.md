# TASK-0157: Economic Decision Room MVP

Status: Implemented
Date: 2026-09-08
Governing ADR: ADR-0059
Specification: SPEC-0058

## Delivery

The first Flooow web application is available under
`applications/economic-decision-room-web`. It is a replaceable Vite/React
decision surface with a premium dark institutional shell, organization-safe
typed API client, keyset pagination, order list/detail, governed refresh,
explicit state vocabulary and an architecture trust strip.

The UI consumes only the three accepted TASK-0156B endpoints. Cursors remain
opaque, the browser sends no organizationId or connectionId, and the bearer is
read only from deployment-owned `window.__FLOOOW_CONFIG__` at runtime. No
provider credential is bundled or persisted. Demo mode is deterministic,
visibly labelled and opt-in through `VITE_DEMO_MODE=true`; it is not the live
architecture.

Economic Truth was not modified. Sales Intelligence remains a derived read
model. Missing values render as `Não disponível`, incomplete and unresolved
states remain distinct, and no recommendation or UI action grants execution
authority. The controlled refresh button invokes only the existing governed
backend refresh.

The static production container uses pinned Node/Nginx version tags, a
non-domain nginx runtime, security headers, a healthcheck and no credentials.
Compose exposes it as `economic-decision-room-web` on the local-only web port.
CI adds deterministic `npm ci`, lint, strict typecheck, tests and production
build without weakening the existing Gradle/runtime job.

## Validation

- `npm ci`;
- `npm run typecheck`;
- `npm run lint`;
- `npm test -- --run` — 15 tests;
- `npm run build`;
- `npm audit --omit=dev --audit-level=high` — 0 runtime vulnerabilities;
- `docker compose config --quiet`;
- static web container smoke — HTTP 200, shell asset and security headers;
- production bundle scan — no bearer token, provider credential,
  organizationId or connectionId strings;
- `./gradlew clean build --no-daemon --stacktrace` — BUILD SUCCESSFUL;
- `git diff --check`.

The requested visual browser inspection was attempted against the production
preview, but the available browser runtime had no connected browser. HTTP and
container checks passed; this is the only external validation limitation.

## Architecture chain

```text
Economic Truth -> Sales Intelligence -> Decision Surface
              -> later governed Intelligence -> Authority -> Execution
              -> Reconciliation -> Learning
```

TASK-0157 stops at the Decision Surface. It does not claim closed-loop
autonomous decisioning.
