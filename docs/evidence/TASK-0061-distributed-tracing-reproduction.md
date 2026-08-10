# TASK-0061 Distributed Tracing Reproduction

**Date:** 2026-08-10

## Result

**PASS - the persistent authenticated MVP exports correlated HTTP and JDBC
traces through a vendor-neutral Collector boundary.**

## Repository validation

```text
./gradlew build --rerun-tasks --no-daemon
BUILD SUCCESSFUL
```

The complete repository build passed, including authentication and PostgreSQL
integration tests. `docker compose config --quiet` also passed.

## Pinned supply chain

| Artifact | Version | Integrity |
| --- | --- | --- |
| OpenTelemetry Java agent | `2.30.0` | `sha256:9d6bc2ad8dd8fb7f730984988e57b8ac0a82d81c7b3b8ae795378718733a509d` |
| Collector Contrib | `0.158.0` | `sha256:c5918f78992ee73b0d6f0e599423ac5ec52dd5d9726733114d6eca53d5a32ed5` |
| Jaeger | `2.20.0` | `sha256:46a886260e04002d8f45e213fc39063fa11a50446048fdaa64786fc0840cb9f8` |

The agent hash is the digest published with the upstream release. Docker
`ADD --checksum` rejected a deliberately incorrect hash before image creation.
The final image reproduced the expected hash, ran as UID `10001`, and contained
none of `curl`, `wget`, or `git`.

## Correlation evidence

An authenticated POST continued the controlled incoming trace ID
`11111111111111111111111111111111` and exported six correlated spans:

```text
POST /(authenticate service-bearer)/v1/marketplace-operations/inventory-risk-assessments
flooow
SELECT flooow
SELECT pg_catalog.pg_get_keywords
SELECT flooow
INSERT flooow.inventory_risk_assessment_journal
```

Resource evidence:

```text
service.name=flooow-marketplace-operations-api
service.namespace=flooow
service.version=0.1.0-SNAPSHOT
deployment.environment.name=reproduction
```

The Collector OTTL filter prevented `/health/live` and `/health/ready` spans
from reaching Jaeger. An authenticated OpenAPI request produced no database span.
An unauthenticated protected request produced one HTTP 401 span and no database
span.

## Privacy evidence

The exported trace JSON, Collector logs, built image history, and artifacts were
checked with unique canaries. None contained:

- the service token;
- the PostgreSQL password;
- the request SKU;
- the created assessment UUID;
- the request's distinctive unit count.

JDBC spans used operation/table names without bind values. Header and body
capture remain disabled, metrics and log export remain `none`, and database
query sanitization remains enabled.

## Failure-isolation evidence

The Collector was stopped while the API and PostgreSQL remained running. An
authenticated POST succeeded, and GET retrieved the same durable assessment.
After the Collector restarted, a new controlled trace exported successfully
without restarting the API.

```text
isolatedPersisted=true
recoveryTrace=true
```

Collector and Jaeger are therefore outside liveness, readiness, authentication,
and business availability.

## Boundary evidence

- no Marketplace Operations or Kernel production source changed;
- no custom business span or attribute was introduced;
- the API exports only OTLP to the Collector;
- Jaeger is reachable only through its loopback-bound local query port;
- PostgreSQL durability and external HTTP representations are unchanged.

Containers, network, and the reproduction PostgreSQL volume were removed after
validation.
