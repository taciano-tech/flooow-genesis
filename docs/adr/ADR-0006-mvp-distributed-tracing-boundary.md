# ADR-0006: MVP Distributed Tracing Boundary

Status: Accepted and implemented by TASK-0061

Date: 2026-08-10

## Context

The persistent and authenticated Marketplace Operations MVP can be reproduced,
but an operator cannot follow one request across the Ktor server and PostgreSQL.
HTTP status and persisted output prove behavior after the fact; they do not show
where latency or failure occurred while the request was executing.

Adding telemetry types to Marketplace Operations or the Kernel would mix an
operational concern into deterministic business contracts. Committing directly
to one monitoring vendor would also make the runtime package less portable.

## Decision

Establish the first observability boundary with OpenTelemetry distributed traces.

The API process is instrumented with the pinned OpenTelemetry Java agent. It
produces automatic HTTP server and JDBC spans and propagates W3C `traceparent`
context. The process exports OTLP to an OpenTelemetry Collector, which is the
only telemetry endpoint known by the application runtime.

The reproducible local package includes a pinned Collector and a pinned Jaeger
backend. Jaeger is a local verification and exploration tool, not an application
dependency or production vendor decision.

The first increment intentionally covers traces only. Metrics and log export
require later specifications so that names, cardinality, retention, and cost are
accepted deliberately.

## Data boundary

Telemetry must not contain:

- `Authorization` or any service-token value or fragment;
- HTTP request or response bodies;
- database credentials, connection URLs containing credentials, or bind values;
- raw SQL containing business values;
- complete inventory-risk inputs, outputs, traces, or recommendation text;
- environment variables or process command lines.

Only standard low-cardinality HTTP, network, service, error, and sanitized
database attributes are authorized. Route templates may be recorded; concrete
assessment identifiers and SKU values may not be attributes or span names.

## Failure boundary

Telemetry is operational evidence, not a prerequisite for business execution.
Collector or backend unavailability must not make liveness, readiness,
authentication, assessment creation, or retrieval fail. Export is asynchronous
and bounded. The application does not retry telemetry indefinitely or persist a
local telemetry queue.

## Alternatives considered

### Application-only manual spans

Deferred. Manual spans can add business meaning later, but beginning there would
miss consistent HTTP and JDBC coverage and introduce instrumentation code before
the operational baseline is proven.

### Ktor library instrumentation in production code

Deferred in favor of the Java agent for the first baseline. Ktor's maintained
telemetry plugin is a valid future option if agent coverage proves insufficient.

### Direct export to Jaeger or another vendor

Rejected. The Collector preserves an OTLP boundary and keeps backend selection
outside the API process.

### Traces, metrics, and logs in one task

Rejected because it would combine different schemas, cardinality risks, and
operational acceptance criteria in one change.

## Consequences

### Positive

- operators can correlate HTTP and JDBC work for one request;
- no telemetry dependency enters the domain or Kernel;
- backend replacement does not reconfigure application code;
- incoming trace context can continue across service boundaries;
- local reproduction can prove exported spans independently.

### Negative

- the Docker image gains a pinned agent artifact;
- Compose gains Collector and Jaeger services;
- traces add bounded CPU, memory, network, and storage cost;
- automatic spans expose operational structure and therefore require explicit
  data-leak tests and sampling configuration.

## Authorization

This ADR does not authorize implementation alone. SPEC-0006 must freeze agent,
resource, propagation, export, sampling, privacy, reproduction, and failure
semantics before runtime changes.
