# TASK-0062 Canonical Event Contract Review

**Date:** 2026-08-10

## Result

**PROPOSED - ready for semantic, integration, and persistence review.**

ADR-0007 and SPEC-0007 define the first application integration event without
promoting `Event` to a Kernel primitive or selecting delivery infrastructure.

## Repository evidence

- the persisted assessment is the first durable fact suitable for integration;
- PostgreSQL already commits the assessment in one Exposed transaction;
- there is no outbox, broker, dispatcher, webhook, connector, event SDK, or
  public event endpoint;
- the Kernel ontology explicitly leaves a universal Event primitive unresolved;
- prior architecture rejected database rows and infrastructure types as public
  semantic contracts.

## Research evidence

- CloudEvents 1.0.2 is the current stable core and JSON event-format release;
- CloudEvents requires `id`, `source`, `specversion`, and `type` and defines
  `source` plus `id` as the duplicate identity;
- reverse-DNS event types and explicit incompatible type versions are the
  recommended interoperability model;
- `subject` identifies the resource within a source while `data` contains the
  occurrence-specific fact;
- structured JSON separates interoperable context from application-owned data;
- CloudEvents describes an event envelope and does not define Genesis ontology,
  transactional production, delivery guarantees, or consumer authority.

## Scope evidence

- documentation only;
- one application-owned fact and one versioned contract;
- transactional outbox before delivery infrastructure;
- no broker, connector, workflow, webhook, notification, BI, or tenant model;
- minimum stable payload without reasoning internals or credentials;
- no Marketplace Operations or Kernel production source change.

## Repository validation

Executed on 2026-08-10:

```text
./gradlew build --rerun-tasks --no-daemon --console=plain
exit code: 0
```

The complete repository build and test suite passed after the documentation-only
change. No cached task result was accepted for this validation.

## Authorization boundary

Merging this proposal accepts ADR-0007 and SPEC-0007 and authorizes TASK-0063
only: atomically persist the frozen CloudEvent with its assessment. It does not
authorize publishing or consuming the event.
