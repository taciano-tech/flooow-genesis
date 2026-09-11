# SPEC-0011: Connector Runtime

**Status:** Proposed

**Date:** 2026-08-11

**Source decision:** ADR-0011

## Objective

Implement a production-inactive, provider-neutral runtime that proves one
organization-scoped connector page can be selected, bounded, consumed
idempotently, and classified safely without calling a real provider.

## Authorized next implementation

Acceptance authorizes TASK-0071 only:

1. add a pure Kotlin `applications:connector-runtime` module depending only on
   `applications:integration-control-plane` and organization context;
2. add validated capability, invocation, progress, budget, page, outcome, and
   controlled-failure values;
3. add a closed provider/capability registry that rejects unknown and duplicate
   registrations before secret use;
4. expose immutable provider identity separately from the existing scoped
   credential use on an active control-plane connection;
5. execute at most one deterministic adapter page per invocation;
6. commit typed records and the next progress position through an atomic,
   compare-and-set page-committer port;
7. provide deterministic in-memory adapters and committers for tests only;
8. prove credential, checkpoint, organization, concurrency, idempotency,
   cancellation, budget, failure, and observability guarantees;
9. keep production startup, HTTP routes, database schema, and external traffic
   unchanged.

No Mercado Livre or Omie adapter, OAuth exchange, token refresh, static real
credential, endpoint configuration, HTTP client, webhook, scheduler, production
worker, PostgreSQL progress table, provider record schema, universal mapping,
business mutation, or public connector API is authorized.

## Identifiers and values

The runtime reuses `OrganizationId`, `IntegrationConnectionId`, and `ProviderKey`.
It adds:

```text
ConnectorCapability: [a-z0-9][a-z0-9.-]{0,99}
ConnectorInvocationId: canonical UUID
ConnectorProgressVersion: non-negative long
ConnectorPageCommitKey: deterministic internal value
```

Capability examples are documentation only. TASK-0071 tests may use names under
`test.*`; it must not imply that a production provider or canonical inventory
schema is registered.

An invocation receives organization ID, connection ID, capability, invocation
ID, deadline, maximum records, and maximum response bytes. Limits are positive,
have conservative hard maxima, and cannot be disabled with sentinel values.
The clock is injected.

## Control-plane access

The control plane adds one metadata-only operation with these semantics:

```text
activeConnectionProvider(organizationId, connectionId) -> ProviderKey?
```

It verifies the active organization, active same-organization connection, and
current credential binding without resolving the secret. The provider key is
immutable connection metadata. After registry resolution, the runtime uses the
existing `withActiveCredential` operation, which revalidates lifecycle and
binding before resolving the vault secret. Credential bytes remain governed by
`SecretVault`: copied for the callback, never converted to `String`, never
retained, and zeroed in `finally` on success or failure.

Unknown, foreign, suspended, revoked, or unbound connections produce one
controlled precondition outcome. They do not reveal whether a foreign resource
exists and do not consult the connector registry or network.

## Connector contracts

The module defines transport-neutral contracts equivalent to:

```text
ConnectorDescriptor(providerKey, supportedCapabilities)
ConnectorRegistry.register(adapter)
ConnectorRegistry.resolve(providerKey, capability)

PullConnector<R>.readPage(
  capability,
  credentialBytes,
  currentProgress,
  budget
) -> ConnectorPage<R> | ConnectorFailure
```

`R` implements a marker `ConnectorRecord` contract and is a typed capability
record. It is neither JSON nor a map of arbitrary fields. The runtime treats it
opaquely and never persists, logs, hashes, or sends it to the Kernel. The
capability-owned committer validates and maps it.

Registration requires a nonempty capability set and exact provider ownership.
One provider has at most one registered adapter, and one adapter has at most one
handler for a capability. Registry construction fails on duplicates. Resolution
failure occurs before credential use.

TASK-0071 adapters are deterministic fakes with no HTTP, filesystem, database,
environment, randomness, or sleep dependency.

## Bounded page contract

One `readPage` invocation may represent no more than one remote request. A page
contains typed records, an optional next progress value, an `observedAt` instant,
and whether the source is exhausted. The following invariants apply:

- record count does not exceed the requested or hard maximum;
- declared response bytes do not exceed the requested or hard maximum;
- `observedAt` is not after the injected clock beyond a small frozen tolerance;
- exhausted pages have no next progress;
- non-exhausted successful pages have a distinct next progress;
- empty non-exhausted pages are allowed only when progress advances;
- progress is opaque, nonempty when present, and no larger than 4096 bytes;
- credential, input progress, and returned progress arrays are defensively
  copied and zeroed when their scoped use ends.

The deadline is checked before credential resolution, before adapter execution,
and before commit. No retry, sleep, page loop, or recursive call occurs.

## Progress and atomic commit

The capability-owned `ConnectorPageCommitter<R>` port supports:

```text
load(organizationId, connectionId, capability) -> VersionedProgress
commit(
  organizationId,
  connectionId,
  capability,
  expectedProgressVersion,
  pageCommitKey,
  records,
  nextProgress,
  exhausted,
  observedAt
) -> COMMITTED | ALREADY_COMMITTED | STALE_PROGRESS
```

The implementation behind this port must atomically validate/map accepted
records, record the page key, and advance progress. TASK-0071 supplies only a
deterministic in-memory implementation. Durable schema and transaction ownership
require a later specification together with the first typed capability.

The runtime derives `pageCommitKey` only from organization, connection,
capability, and loaded progress version. It contains no credential, checkpoint,
record, response, or remote identifier material.

`COMMITTED` and `ALREADY_COMMITTED` are successful outcomes. A stale compare-and-
set becomes `PROGRESS_CONFLICT`; it is never retried inside the invocation. If
commit fails, the runtime does not return the next progress as successful.

## Controlled outcomes

Success exposes only:

```text
outcome, providerKey, capability, recordCount, exhausted, observedAt
```

It omits records and progress. Expected adapter failures use:

```text
AUTHENTICATION_REQUIRED
AUTHORIZATION_DENIED
RATE_LIMITED
REMOTE_TEMPORARY
REMOTE_PERMANENT
REMOTE_DATA_INVALID
BUDGET_EXCEEDED
CANCELLED
```

Runtime failures add:

```text
CONNECTION_UNAVAILABLE
CONNECTOR_UNAVAILABLE
PROGRESS_CONFLICT
INTERNAL
```

Only `RATE_LIMITED` and `REMOTE_TEMPORARY` may carry `retryAfter`, clamped to a
frozen minimum and maximum duration. TASK-0071 does not retry or mutate
connection lifecycle. Unexpected exceptions become `INTERNAL` with no exception
class, message, stack, provider body, URL, or secret data in the result.

## Cancellation and concurrency

Cancellation is cooperative and injected through a small token checked at each
runtime boundary. It produces `CANCELLED` without commit. The runtime does not
interrupt threads or own an executor.

Concurrent invocations may read the same progress and make the same fake adapter
call. Compare-and-set allows exactly one new commit. The other invocation returns
`PROGRESS_CONFLICT` or observes `ALREADY_COMMITTED`; it never overwrites advanced
progress or duplicates accepted records.

## Privacy and observability

Allowed low-cardinality metric labels are provider key, capability, execution
outcome, and exhausted flag. Organization, connection, invocation, and page
commit IDs may appear in traces but not metric labels.

Credentials, secret references, progress bytes, progress hashes, raw records,
remote identifiers, URLs, query strings, request or response bodies, exception
messages, and adapter diagnostics are forbidden in logs, metrics, traces, audit,
events, results, snapshots, and assertion failure messages.

TASK-0071 may emit in-memory observations for tests but changes no production
telemetry wiring.

## Test plan

1. the runtime has only the two authorized project dependencies and no HTTP,
   database, serialization, framework, provider SDK, or Kernel dependency;
2. capability and invocation values reject malformed input;
3. duplicate providers or capabilities fail registry construction;
4. unknown provider/capability fails before credential resolution;
5. foreign, suspended, revoked, or unbound connections expose only the same
   controlled unavailable result;
6. active connection resolution supplies its immutable provider key;
7. credential bytes are copied and zeroed on adapter success and failure;
8. progress bytes are defensively copied, bounded, and zeroed;
9. one invocation calls the adapter at most once;
10. deadlines and cancellation before each boundary prevent later work;
11. record, response-byte, timestamp, exhausted, and progress invariants are
    validated before commit;
12. arbitrary JSON or map records cannot enter the runtime contract;
13. successful commit advances one version and returns no progress or records;
14. retry after a committed-but-unacknowledged page is idempotent;
15. concurrent stale invocations cannot double-commit or overwrite progress;
16. commit failure or stale progress never reports page success;
17. only rate-limited and remote-temporary outcomes retain bounded retry hints;
18. unexpected exceptions expose only `INTERNAL`;
19. outcome and test telemetry contain none of the supplied secret, progress,
    record, URL, body, or exception marker values;
20. production startup, API routes, OpenAPI, PostgreSQL migrations, events,
    delivery, Marketplace Operations, research, and Kernel remain unchanged;
21. no test or production source opens a provider connection;
22. the complete repository build remains green.

## Provider research constraints

The contract deliberately does not standardize OAuth, JSON request envelopes,
page numbers, offsets, scroll IDs, refresh, or webhook payloads:

- Mercado Livre access tokens are short-lived and refresh tokens rotate; rate
  limits and pagination mechanisms vary by endpoint;
- Mercado Livre notifications identify a topic and resource that must generally
  be fetched, so notification receipt is not the business record itself;
- Omie uses App Key/App Secret with method and parameters in JSON POST bodies;
- Omie list methods expose numbered pages and its rate limit is scoped by IP,
  App Key, and method.

## References

- Mercado Livre authentication and authorization:
  https://developers.mercadolivre.com.br/pt_br/api-docs-pt-br/autenticacao-e-autorizacao
- Mercado Livre notifications:
  https://developers.mercadolivre.com.br/produto-receba-notificacoes
- Mercado Livre rate limits:
  https://developers.mercadolivre.com.br/pt_br/usuarios-e-aplicativos/rate-limit-erro-429
- Omie API characteristics and protocols:
  https://ajuda.omie.com.br/pt-BR/articles/5412721-caracteristicas-e-recomendacoes-das-apis-do-omie
- Omie API consumption limits:
  https://ajuda.omie.com.br/pt-BR/articles/8112984-limites-de-consumo-da-api-do-omie
- Omie inventory API:
  https://app.omie.com.br/api/v1/estoque/consulta/

## Remaining boundary

Durable connector progress, the first canonical provider record, mapping,
Mercado Livre authorization and adapter, Omie credential onboarding and adapter,
token refresh, webhooks, missed-event recovery, scheduling, leases, automatic
retry, outbound writes, public administration, and production activation require
later accepted specifications.

## Acceptance

Merging ADR-0011 and SPEC-0011 authorizes TASK-0071 only. It does not authorize
any real credential, external request, provider schema, business mutation, or
production connector.
