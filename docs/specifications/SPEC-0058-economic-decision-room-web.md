# SPEC-0058: Economic Decision Room Web MVP

Status: Accepted  
Date: 2026-09-08  
Governing ADR: ADR-0059  
Implementation task: TASK-0157

## Scope

`applications/economic-decision-room-web` is a desktop-first, responsive
React application with a dark institutional visual system. It provides the
Decision Room shell, organization-scoped Sales Intelligence list/detail,
keyset pagination, controlled refresh, explicit uncertainty states and an
architecture trust strip.

## API boundary

Only these authenticated endpoints are used:

- `GET /v1/sales-intelligence/orders`
- `GET /v1/sales-intelligence/orders/{marketplaceOrderId}`
- `POST /v1/sales-intelligence/refresh`

The client sends a bearer token supplied at runtime. It never sends
`organizationId` or `connectionId`; cursors are opaque strings. Problem
responses are sanitized into actionable UI states.

## Truth and security invariants

- Economic Truth remains canonical and is not recalculated by the UI.
- Sales Intelligence remains a derived projection and is labelled as such.
- Missing values are not rendered as zero; incomplete is not complete.
- Unknown is not false, and recommendations are not authority.
- No provider credential, token, or secret is committed, bundled, persisted in
  localStorage, or logged.
- Demo fixtures are deterministic, isolated, visibly marked and opt-in.

## Validation

The app must pass strict TypeScript, lint, unit tests, production build and
bundle secret scans. Docker Compose may include the frontend as a separate
static service only when the production image remains credential-free and the
backend URL is runtime-configurable.
