# ADR-0007: Integration Event and Outbox Boundary

Status: Proposed

Date: 2026-08-10

## Context

Marketplace Operations now records authenticated inventory-risk assessments as
durable facts. Future connectors, notifications, analytics, and workflows need a
stable way to observe those facts without reading application tables or importing
application and Kernel types.

The Kernel ontology has deliberately not accepted a universal `Event` primitive.
CloudEvents describes interoperable event data and context; it does not decide
the business meaning of an occurrence. Treating its envelope as a Kernel concept
would reverse the established dependency direction.

Publishing directly after a database commit would create a dual-write gap: the
assessment could persist while its event is lost, or the event could be emitted
for a transaction that later fails.

## Decision

Define application-owned integration events at the Marketplace Operations
boundary and represent them externally as CloudEvents 1.0 structured JSON.

The first event is:

```text
io.flooow.marketplace.inventory-risk-assessment.recorded.v1
```

It represents the already committed fact that one inventory-risk assessment was
recorded. It does not represent a command, recommendation approval, workflow
instruction, universal Kernel event, or database-change notification.

The assessment row and its outbox event are inserted in one PostgreSQL
transaction. A future dispatcher may deliver committed outbox records at least
once. Consumers must deduplicate by CloudEvents `source` plus `id`.

## Boundary

- Marketplace Operations owns the occurrence semantics and payload vocabulary;
- an application adapter owns CloudEvents serialization and outbox persistence;
- PostgreSQL supplies atomicity;
- no broker, connector, webhook, workflow engine, or notification provider is
  selected by this decision;
- no CloudEvents type or SDK enters the Kernel;
- database table names and internal Kotlin representations are not contracts.

## Alternatives considered

### Add a universal Event primitive to the Kernel

Rejected. The current ontology explicitly leaves that question unresolved, and
an integration envelope is not evidence that every organizational event shares
one universal semantic model.

### Publish from the HTTP route after persistence

Rejected because it introduces an unrecoverable dual-write window and couples
request success to an unavailable external system.

### Database change capture as the public contract

Rejected because row layouts are persistence details, not intentional business
contracts. CDC may later transport outbox records without defining their meaning.

### Introduce a broker now

Deferred. Atomic production, stable serialization, privacy, and idempotency must
be proven before choosing delivery infrastructure.

### Emit the complete API or domain representation

Rejected. Integration consumers receive the minimum stable fact they need, not
reasoning traces, internal judgments, or every persisted field.

## Consequences

### Positive

- connectors can consume intentional facts without importing Genesis internals;
- assessment and event cannot diverge at commit time;
- delivery infrastructure remains replaceable;
- event contracts can be versioned independently from database and HTTP models;
- the Kernel remains infrastructure-agnostic.

### Negative

- PostgreSQL gains an outbox table and retention responsibility;
- at-least-once delivery requires consumer idempotency;
- schema evolution requires compatibility governance;
- a later dispatcher, retry policy, dead-letter policy, and operational control
  surface still need separate specifications.

## Authorization

This ADR does not authorize implementation alone. SPEC-0007 must freeze the
CloudEvents envelope, payload, atomicity, uniqueness, ordering, retention,
privacy, compatibility, and test contracts before production changes.
