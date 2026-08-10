# TASK-0060 Distributed Tracing Specification Review

**Date:** 2026-08-10

## Result

**PROPOSED - ready for operational, security, and architectural review.**

ADR-0006 and SPEC-0006 define a vendor-neutral trace boundary for the persistent
MVP without introducing telemetry concepts into Marketplace Operations or the
Kernel.

## Repository evidence

The current runtime has observable HTTP and JDBC boundaries but no telemetry
dependency, agent, OTLP configuration, Collector, backend, trace test, metric,
or log exporter. PostgreSQL access uses Exposed JDBC, making HTTP/JDBC automatic
instrumentation the minimum useful vertical trace.

## Research evidence

- OpenTelemetry recommends beginning Java instrumentation with its Java agent;
- zero-code instrumentation covers library edges such as inbound requests and
  database calls while manual instrumentation remains available later;
- Ktor documents maintained OpenTelemetry server tracing as an alternative when
  explicit application instrumentation becomes necessary;
- OTLP through an OpenTelemetry Collector preserves a vendor-neutral application
  endpoint and supports later backend changes;
- the Java agent sanitizes database statements by default and does not capture
  JDBC bind parameters;
- Collector and backend failure must remain outside the business availability
  boundary.

## Scope evidence

- documentation only;
- traces only in the authorized next implementation;
- no production source, dependency, image, Compose service, workflow, fixture,
  or snapshot changed;
- Java agent plus Collector selected before manual or vendor-specific tracing;
- Jaeger limited to local reproduction and CI verification;
- strict secret, body, business-value, and cardinality prohibitions;
- metrics, logs, alerts, SLOs, CloudEvents, and Temporal explicitly deferred.

## Repository validation

```text
./gradlew build --rerun-tasks --no-daemon
BUILD SUCCESSFUL
36 actionable tasks: 36 executed
```

The complete build passed on 2026-08-10, including API authentication and
PostgreSQL integration tests.

## Authorization boundary

Merging this proposal accepts ADR-0006 and SPEC-0006 and authorizes TASK-0061
only: implement and reproduce the distributed-tracing baseline. It does not
authorize any Marketplace Operations or Kernel production change.
