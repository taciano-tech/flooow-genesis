# ADR-0059: Economic Decision Room Web MVP

Status: Accepted  
Date: 2026-09-08

## Context

TASK-0156B exposes the first authenticated Sales Intelligence read model and
one bounded governed refresh. Flooow needs a replaceable decision surface that
makes the boundary between Economic Truth, derived intelligence and human
decision explicit.

## Decision

Create `applications/economic-decision-room-web` as a Vite/React/TypeScript
application. It consumes only the accepted TASK-0156B API through a small typed
Fetch client. The browser never sends organizationId or connectionId, never
reads PostgreSQL, and never receives provider credentials.

The application is a presentation adapter: it maps the three backend states,
preserves missing values as missing, treats cursors as opaque, and exposes the
existing synchronous refresh command without implying authority or execution.
Production authentication remains a runtime-injected bearer token for the
local/investor MVP; a deployment-grade browser authentication exchange is a
follow-up and is not weakened or hidden here.

Demo mode is explicit (`VITE_DEMO_MODE=true`), isolated to deterministic demo
fixtures, visibly labelled, and disabled by default. It cannot be enabled by a
production build unless the explicit build variable is set.

## Consequences

The frontend is replaceable; the institutional moat remains accumulated truth,
evidence, decisions and outcomes. No canonical economic logic, migration,
provider call, authority grant or autonomous marketplace mutation is added.
