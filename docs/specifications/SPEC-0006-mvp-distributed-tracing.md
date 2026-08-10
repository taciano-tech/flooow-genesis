# SPEC-0006: MVP Distributed Tracing

**Status:** Proposed

**Date:** 2026-08-10

**Source decision:** ADR-0006

## Objective

Make one authenticated assessment request traceable from its HTTP server span to
its PostgreSQL work without changing Marketplace Operations, the Kernel, or the
external business representation.

## Authorized implementation

Acceptance authorizes TASK-0061 only:

1. pin and verify an OpenTelemetry Java agent in the API image;
2. export traces through OTLP to a pinned OpenTelemetry Collector;
3. add pinned Collector and Jaeger services to the local Compose package;
4. propagate W3C trace context and record HTTP server plus JDBC spans;
5. configure bounded sampling and batch export through environment variables;
6. prove trace correlation, privacy, and telemetry-failure isolation;
7. update runtime documentation, CI, and reproduction evidence.

No Marketplace Operations or Kernel production source may change. No custom
business span or attribute is authorized.

## Runtime topology

```text
authenticated client
        |
        | HTTP + optional traceparent
        v
Marketplace Operations API + Java agent
        |
        | OTLP/gRPC
        v
OpenTelemetry Collector
        |
        | OTLP
        v
Jaeger (local verification only)
```

The API knows only the Collector OTLP endpoint. It never uses a Jaeger-specific
exporter or API.

## Artifact integrity

The Java agent, Collector image, and Jaeger image are pinned to explicit
versions. Container images use immutable digests in the accepted implementation.

The Java agent download is verified against a committed SHA-256 value during the
image build. A mismatch fails the build. The agent JAR is copied into the runtime
image but no downloader, package manager, source tree, or build cache is added to
the final stage.

TASK-0061 must record the selected versions, upstream release references, and
hashes in its evidence. Floating `latest` tags are prohibited.

## Resource contract

Every emitted span contains these resource attributes:

```text
service.name=flooow-marketplace-operations-api
service.namespace=flooow
service.version=<project version>
deployment.environment.name=<configured environment>
```

`service.name`, `service.namespace`, and `service.version` are fixed by the
package. `deployment.environment.name` comes from an explicit non-secret runtime
configuration. Hostnames, container IDs, process command lines, and environment
variables are not promoted to custom attributes.

## Trace and propagation contract

- propagator: W3C Trace Context;
- incoming valid `traceparent` continues the same trace;
- missing or invalid context starts a new trace without failing the request;
- HTTP server spans use route templates, never concrete assessment UUIDs;
- POST and GET-by-ID contain at least one child database span when persistence is
  executed;
- authentication failures contain no database span;
- health routes are excluded from tracing to avoid probe noise;
- OpenAPI may be traced but emits no business or database span;
- status and error attributes follow current OpenTelemetry HTTP and database
  semantic conventions supplied by the pinned agent.

No trace identifier is added to the frozen business response or problem body.
Trace context remains in standard propagation and telemetry channels.

## Sampling and export

Isolated local and CI reproduction use `parentbased_always_on` so assertions are
deterministic. Shared deployments use an explicitly configured
`parentbased_traceidratio` value; absence of a shared-environment sampling value
must fail deployment configuration validation outside the application.

Export uses the agent's batch span processor and OTLP/gRPC. Queue, batch, timeout,
and export interval values are finite and documented. The service never uses a
synchronous exporter on the request path.

## Privacy and cardinality

The following capture features remain disabled:

- request and response bodies;
- request or response headers, including `Authorization`;
- JDBC bind parameters;
- unsanitized database statements;
- end-user identity attributes;
- process command arguments and environment-variable values.

Span names and attributes must not contain service tokens, passwords, SKUs,
assessment UUIDs, dates, unit counts, recommendation text, expected impact, or
the domain reasoning trace. Tests use unique canary values and inspect exported
spans, Collector output, image history, and built artifacts for leakage.

Standard attributes with unbounded concrete paths are rejected. Database
statements remain sanitized using the agent default; disabling statement
sanitization is prohibited.

## Failure isolation

With Collector unavailable:

- API startup succeeds;
- public health behavior is unchanged;
- authenticated POST and GET preserve their existing status and representations;
- persistence remains durable;
- export failures do not disclose telemetry payloads or credentials;
- memory use is bounded by the configured batch queue;
- recovery of the Collector permits later spans to export without API restart.

Readiness does not depend on Collector or Jaeger. Database-aware readiness is a
separate operational specification.

## Local package and access

Compose adds Collector and Jaeger on an internal network. The Collector OTLP
receiver is not published to the host by default. Jaeger's query UI/API may bind
to a configurable loopback host port for local exploration and CI assertions.

The local package remains one-command reproducible. Stopping Compose removes
ephemeral telemetry state; PostgreSQL durability semantics remain unchanged.

## Test plan

1. image build rejects an agent hash mismatch;
2. final API image contains the pinned agent and no download tool or secret;
3. Compose configuration uses pinned Collector and Jaeger digests;
4. health routes emit no trace;
5. anonymous protected request emits an HTTP 401 span and no JDBC span;
6. authenticated POST exports one HTTP server trace with JDBC child work;
7. authenticated GET exports one HTTP server trace with JDBC child work;
8. an incoming valid `traceparent` is retained end to end;
9. invalid trace context does not alter the HTTP contract;
10. route attributes contain templates, not assessment identifiers;
11. canary token, password, SKU, UUID, body values, and recommendation text are
    absent from exported spans and Collector logs;
12. database statements contain no bind values;
13. Collector absence does not change API startup, health, POST, GET, or durable
    persistence;
14. Collector recovery exports a later trace without API restart;
15. local Jaeger query returns the expected service and correlated spans;
16. CI reproduces trace export and privacy assertions;
17. repository build and all frozen snapshots remain green;
18. Kernel and Marketplace Operations production sources remain unchanged.

## Rollout and rollback

Deploy the Collector endpoint before enabling export in a shared environment.
Start with a conservative parent-based ratio, observe CPU, memory, export error,
and backend volume, then adjust through deployment configuration.

Rollback removes the agent JVM option and OTLP configuration. It must not require
an application binary rollback, database migration, or client contract change.
Collector and backend removal occurs only after exporters are disabled.

## Out of scope

- application metrics, dashboards, alerts, SLOs, and error budgets;
- OpenTelemetry log export or log/trace correlation;
- custom domain spans, events, or business attributes;
- profiling, continuous profiling, and runtime security monitoring;
- production Jaeger deployment or observability-vendor selection;
- long-term telemetry storage, retention, backup, and cost policy;
- database-aware readiness;
- CloudEvents, asynchronous messaging, and Temporal;
- changes to authentication, business DTOs, persistence schema, or Kernel APIs.

## Acceptance

Merging ADR-0006 and SPEC-0006 authorizes TASK-0061 only. Any metrics, logs,
business telemetry, vendor-specific exporter, or domain-source change requires a
new accepted specification.
