# ADR-0011: Connector Execution Boundary

Status: Proposed

Date: 2026-08-11

## Context

TASK-0069 completed organization propagation from the authenticated service
principal through Marketplace Operations, immutable events, outbox, and delivery
coordination. The Integration Control Plane can now identify an active,
organization-owned provider connection and expose its credential only through a
scoped callback. Genesis still has no contract for executing provider reads.

The first intended sources, Mercado Livre and Omie, do not share one protocol.
Mercado Livre uses OAuth access and rotating refresh tokens, endpoint-dependent
offset or cursor pagination, webhooks that identify resources to fetch, and
client/endpoint rate limits. Omie uses App Key and App Secret in JSON POST
requests, numbered pages, method-specific response shapes, and limits scoped by
IP, App Key, and method. Treating either shape as universal would couple every
future connector to the first provider implemented.

Genesis also cannot allow a connector to write arbitrary JSON directly into the
Kernel or operational tables. Provider transport, canonical mapping, durable
ingestion, and business authority are different responsibilities.

## Decision

Introduce a provider-neutral, pull-only connector execution boundary between the
Integration Control Plane and future provider adapters. Its first implementation
executes at most one bounded remote page per invocation and remains inactive in
production.

The boundary has four roles:

1. the control plane proves that the organization and connection are active,
   identifies the immutable provider key, and lends credential bytes briefly;
2. a registry selects exactly one adapter for that provider and capability;
3. the adapter translates one provider request and response into typed records
   without persisting or authorizing business changes;
4. a capability-owned page committer atomically maps or stores accepted records
   and advances progress with compare-and-set semantics.

```text
authenticated organization + connection
  -> active connection provider
  -> provider/capability registry
  -> scoped credential
  -> one bounded adapter page
  -> capability-owned atomic page commit
  -> controlled execution outcome
```

Provider adapters own authentication headers or bodies, URLs, pagination syntax,
remote identifiers, response parsing, and provider error interpretation. The
runtime owns organization scope, capability selection, budgets, idempotent commit
coordination, controlled failure classes, and secret-safe observability.

## Capability boundary

A connector advertises explicit, stable capability keys such as
`inventory.snapshot.read`. Capability keys describe data access, not provider
endpoints or Genesis business commands. Registration is closed by default:
unknown providers, unknown capabilities, and duplicate registrations fail before
credential resolution or network work.

TASK-0071 uses only deterministic fake adapters and records. It does not register
Mercado Livre, Omie, or any production capability. Real capability schemas must
be accepted separately after the provider fields and product mapping rules are
known.

## One-page execution

One invocation may make at most one provider request and return at most one page.
The caller cannot request an unbounded loop. Record count, deadline, and payload
limits are mandatory, and the adapter must stop before exceeding them.

Progress is an opaque, bounded byte value owned by the adapter. It may represent
an offset, page number, cursor, scroll token, timestamp, or compound position.
The runtime never parses it, converts it to text, or includes it in telemetry.
The page committer loads a versioned position and atomically commits accepted
records plus the next position. Compare-and-set rejects concurrent stale work.

The idempotency key is derived from organization, connection, capability, and
the loaded progress version, never from secret or response bytes. A crash after
commit can therefore reload the advanced version; two workers may perform the
same remote read, but only one can commit that page.

## Failure and retry boundary

Adapters translate expected provider failures into a small controlled taxonomy:

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

The runtime adds `PROGRESS_CONFLICT` and `INTERNAL`. Only `RATE_LIMITED` and
`REMOTE_TEMPORARY` may carry a bounded retry hint. TASK-0071 performs no automatic
retry, lifecycle transition, credential refresh, or connection revocation.
Those actions require policy outside one adapter call. Raw status bodies,
exceptions, URLs, credentials, checkpoints, and remote records never appear in
the public outcome.

## Credential and data safety

Credential bytes are available only inside the existing control-plane callback
and are zeroed afterward. Adapters must not retain them. Progress bytes follow
the same copy-and-zero discipline in the deterministic implementation.

The runtime never stores provider payloads and never passes arbitrary JSON to the
Kernel. A typed capability consumer owns validation and mapping before commit.
Provider data is evidence; it is not authority to change price, inventory,
orders, finance, or any other protected business state.

## Explicitly separate boundaries

- OAuth authorization and refresh are credential-lifecycle workflows, not pull
  execution;
- inbound webhooks need authenticity, replay, deduplication, and missed-event
  recovery contracts of their own;
- outbound writes need business authorization, approval, and idempotency rules;
- scheduling, durable leases, automatic retries, and production activation are
  orchestration concerns;
- field mapping and a user-authored connector DSL require evidence from real
  adapters and are not standardized now.

## Consequences

### Positive

- the first provider cannot dictate authentication or pagination for all others;
- organization and active-connection checks precede credential or network use;
- bounded pages prevent an adapter from becoming an uninterruptible sync loop;
- optimistic progress and deterministic page identity prepare safe ingestion;
- provider payloads remain outside the Kernel and generic persistence;
- real APIs remain disabled while the execution mechanics become testable.

### Negative

- TASK-0071 will not yet import live data;
- two concurrent workers can duplicate a remote read even though commit is fenced;
- durable progress storage, provider mapping, and scheduling remain unfinished;
- each real capability still needs a typed record and consumer specification.

## Alternatives considered

### Implement Mercado Livre or Omie directly in Marketplace Operations

Rejected because authentication, pagination, failure, and mapping details would
leak into the current API and business application.

### Use a universal JSON request/response connector

Rejected because it would make unvalidated remote payloads a de facto internal
schema and create secret, privacy, and compatibility risks.

### Build a workflow or mapping DSL first

Deferred because two unimplemented providers are insufficient evidence for a
stable language. A DSL would also mix transport with business authority.

### Run a complete synchronization loop inside one adapter call

Rejected because it defeats bounded execution, cancellation, fair scheduling,
checkpoint fencing, and rate-limit control.

### Treat webhooks and polling as the same interface

Rejected because accepting an inbound notification has different trust,
availability, replay, and acknowledgement semantics from a provider pull.

## Authorization

This ADR alone authorizes no implementation or external call. SPEC-0011 freezes
the production-inactive connector runtime and deterministic tests for TASK-0071.
