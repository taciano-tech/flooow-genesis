# TASK-0068 Organization Context Contract Review

**Date:** 2026-08-11

## Result

**PROPOSED - ready for architecture, security, and data-migration review.**

ADR-0010 and SPEC-0010 define the minimum safe bridge between the organization-
scoped integration control plane and the currently global Marketplace Operations
data plane.

## Repository evidence

- the current service bearer authenticates one technical client but carries no
  organization authority;
- POST and GET callbacks receive no principal or organization context;
- assessment journal append and lookup are globally keyed by assessment ID;
- the v1 CloudEvent, outbox, and delivery rows contain no organization ID;
- delivery enqueue accepts an opaque destination without consulting the control
  plane;
- TASK-0067 destinations and credentials are correctly isolated but deliberately
  disconnected from those global events;
- no existing record contains trustworthy evidence from which its company can
  be inferred.

## Decision evidence

- the current bearer is bound server-side to exactly one configured organization;
- organization cannot be selected through untrusted request input;
- one canonical foundation value is shared without entering the Kernel;
- organization scope becomes mandatory from recorder through delivery fencing;
- immutable v1 events remain unchanged and scoped writes use v2;
- legacy global rows are preserved but quarantined rather than guessed;
- real integrations and production routing remain disabled.

## Validation

```text
./gradlew build --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 11m 32s
41 actionable tasks: 41 executed
125 tests, 0 failures, 0 errors, 0 skipped
```

This task changes documentation only. Existing API, control plane, persistence,
delivery, Marketplace Operations, research, and Kernel behavior remain green.

## Authorization boundary

Acceptance authorizes only TASK-0069 organization propagation and isolation.
Users, roles, multiple credentials, OAuth, real providers, automatic sync,
webhooks, and production delivery remain outside the accepted boundary.
