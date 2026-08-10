# ADR-0003: MVP External API Boundary

Status: Proposed

Date: 2026-08-10

## Context

Marketplace Operations is currently a deterministic in-process application.
It validates a real inventory-risk capability but cannot yet be called by an
external system. ADR-0001 deliberately excluded HTTP until the application
model existed. That model and its regression evidence now exist.

The Kernel also has parallel legacy and directional reasoning APIs. TASK-0052
requires continued parallel operation and forbids an invented or lossy
projection between them. An external API must therefore expose business
capabilities rather than make Kernel contracts public transport contracts.

## Decision

Introduce a separate JVM application module:

```text
applications:marketplace-operations-api
                |
                v
applications:marketplace-operations
                |
                v
platform:foundation:kernel
```

The API module will use Ktor 3.5.1 and kotlinx.serialization. It may depend on
Marketplace Operations but must not declare a direct dependency on the Kernel.

The first public capability is a synchronous inventory-risk assessment:

```text
POST /v1/marketplace-operations/inventory-risk-assessments
```

The request and response are API-owned DTOs. They represent business input and
business output and do not serialize Kernel classes, identifiers, judgments, or
evaluation results directly.

The first implementation remains stateless and deterministic. Persistence,
marketplace connectors, authentication, autonomous execution, and asynchronous
workflow orchestration remain outside this decision.

## Boundary rules

1. Transport DTOs live only in the API module.
2. Route handlers perform transport work and delegate business evaluation.
3. Domain validation remains owned by Marketplace Operations.
4. HTTP error mapping must not expose stack traces or internal class names.
5. Kernel types must not appear in the OpenAPI document.
6. A fixed input must produce the same business response independent of server
   wall-clock time.
7. The API does not migrate Marketplace Operations to the directional reasoning
   path; that requires a separate accepted specification.
8. Health routes must not invoke business evaluation.

## Alternatives considered

### Expose a generic `/evaluations` Kernel endpoint

Rejected. It would turn internal reasoning contracts into a public protocol,
couple clients to the legacy/directional compatibility problem, and expose a
platform mechanism instead of a business capability.

### Add HTTP routes to `marketplace-operations`

Rejected. It would mix transport infrastructure with the deterministic business
application and make in-process tests depend on a server framework.

### Introduce persistence with the first route

Rejected. The synchronous stateless route is sufficient to validate the
external contract. Persistence should follow with its own audit and failure
semantics.

### Start with an event broker

Rejected. No demonstrated throughput or asynchronous coordination requirement
justifies a broker in the first external slice.

## Consequences

### Positive

- external clients can use the validated business capability;
- the Kernel remains independent of HTTP and serialization;
- API evolution can be versioned separately from domain evolution;
- route tests can verify the real JSON contract without changing domain tests;
- later persistence and telemetry adapters have an explicit outer boundary.

### Negative

- DTO mapping introduces deliberate duplication;
- the first response is not durable and cannot be retrieved later;
- authentication and rate limiting are deferred;
- a later directional production migration may intentionally change parts of
  the response and will require versioned compatibility analysis.

## Acceptance

Acceptance of this ADR and SPEC-0002 authorizes only TASK-0054, the stateless
HTTP adapter implementation. It does not authorize persistence, a production
reasoning migration, or any Kernel modification.
