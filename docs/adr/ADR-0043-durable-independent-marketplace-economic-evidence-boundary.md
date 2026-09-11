# ADR-0043: Durable Independent Marketplace Economic Evidence Boundary

Status: Accepted

Date: 2026-08-31

## Context

ADR-0042 and SPEC-0041 established a pure, provider-neutral aggregate for
independently arriving marketplace economic facts, external identities,
collection attempts, and explicit corrections. TASK-0140 implemented that
aggregate and proved deterministic duplicate, conflict, no-regression,
correction, ordering, precision, and redaction behavior.

The aggregate currently exists only in memory. Process restart loses its
history. Two concurrent writers have no durable serialization point. A later
Sales Intelligence projection cannot safely distinguish committed evidence
from transient process state, and no atomic notification exists for rebuilding
downstream projections.

Genesis already contains reusable Postgres patterns for organization isolation,
immutable append, request replay, source-fact uniqueness, optimistic
serialization, correction lineage, transaction rollback, and transactional
outbox delivery. A new mutable JSON order snapshot or a second Financial Ledger
would duplicate weaker versions of those foundations.

## Decision

Introduce one application-owned persistence port beside the existing evidence
domain and one Postgres adapter in the existing marketplace persistence module.

The durable authority is an organization-scoped, relational, append-oriented
history for one `MarketplaceEconomicEvidenceSubject`. A root records subject
identity and a monotonically increasing version. Applied updates append facts,
attempts, and corrections; they never update or delete historical evidence.

The adapter reconstructs the aggregate by replaying retained updates through
`MarketplaceIndependentEconomicEvidenceMerger`. It does not duplicate domain
merge logic in SQL or silently repair malformed state.

## Aggregate identity and version

One root is uniquely identified by:

```text
organization_id + marketplace_order_id
```

The root also retains marketplace, external marketplace order, and currency.
An existing root with different subject attributes is an integrity conflict.
The same marketplace external order may exist in another organization.

Version `0` represents an empty, not-yet-persisted aggregate. Every successful
domain `Applied` update increments the durable version exactly once. Duplicate,
conflict, stale, unavailable, and integrity results do not advance it.

## Repository semantics

The application port supports:

- reading by complete subject identity;
- applying one domain update against an expected version;
- returning the reconstructed aggregate and durable version for applied and
  duplicate outcomes;
- controlled stale, organization-unavailable, not-found, domain-conflict, and
  integrity results.

The port file also owns the narrow persistence encoding bridge required by an
adapter in another Gradle module. It may expose a public, redacted
`valueForPersistence()` extension for the existing observation identifier and
the version raw value. It must reconstruct identifiers through their existing
canonical parser. This bridge exists only because the identifier's raw UUID is
module-internal; it does not widen domain construction or logging authority.

The repository uses this classification order inside one transaction:

1. lock an existing subject root and validate subject consistency;
2. reconstruct current evidence and apply the domain merger;
3. return an exact durable duplicate without a write, including after
   organization suspension;
4. require an active organization before any new root or mutation;
5. return a domain conflict without a write when applicable;
6. for a new compatible update, compare expected and current version;
7. return stale version when they differ;
8. safely establish a version-zero root only when the first update can apply;
9. append relational history, advance the root once, and append one outbox
   notification;
10. commit atomically.

Classifying duplicate before stale preserves harmless retry after a caller
loses the prior response. A compatible new update with stale expected version
must reload and reapply. Last-write-wins is forbidden.

Historical reads remain available after organization suspension, following the
existing Financial Ledger retention boundary. Suspension blocks new evidence,
not audit access to evidence already committed. A rejected first update must
not leave an empty root behind.

## Durable history

The storage model retains enough typed columns to reconstruct every accepted
domain update exactly:

- subject root and version;
- component facts and their existing exact economic values and provenance;
- external identity facts;
- collection attempts;
- corrections, replacement fact, superseded fact, reason, and observation
  time;
- the aggregate version at which each update was committed.

Correction replacement and correction record share one committed aggregate
version and transaction. No mutable JSON snapshot is canonical authority. A
derived head or cache may be added only under a later accepted contract and
must be rebuildable from history.

Database constraints protect immutability, organization ownership, unique
subject identity, globally unique evidence identifiers within a subject,
one update per aggregate version, valid subtype shape, and correction
references. Domain replay remains the final semantic integrity check.

## Transactional outbox

Every newly applied fact, attempt, or correction appends exactly one generic
evidence-change notification to the existing `integration_event_outbox` in the
same transaction. Duplicate and rejected updates emit nothing.

The event type is versioned and provider-neutral. Its payload contains only the
internal marketplace order identifier, committed evidence version, and change
kind. Organization remains in the existing outbox organization column. Amounts,
external provider references, external order identifiers, source-system keys,
and correction reason text are excluded.

The outbox is a delivery notification, not evidence authority. Full rebuild and
recovery replay the evidence history, not delivered-event history. Delivery
failure never rolls back already committed evidence.

## Concurrency and failure

One locked root serializes writers for a subject. Concurrent compatible facts
may both succeed after the stale caller reloads. Exact duplicate delivery
converges without a second row or event. Conflicting facts remain explicit.
Concurrent corrections of one active fact admit at most one successor.

A transaction failure cannot leave any of these partial states:

- evidence row without root version advancement;
- correction without replacement;
- version advancement without history;
- applied history without its outbox notification.

## Boundaries preserved

- Provider source records remain distinct from accepted canonical evidence.
- Economic Evidence remains distinct from Economic Truth.
- Economic Evidence remains distinct from Financial Ledger and Reconciliation.
- The repository performs no provider access, economic calculation,
  materialization, projection, recommendation, or action.
- No marketplace concept enters the Kernel.
- The persistence module depends inward on the marketplace application port;
  the domain has no JDBC, SQL, Flyway, JSON, or Postgres dependency.

## Privacy and precision

Exact `MarketplaceMoney` decimal magnitude and whole-microsecond timestamps
must round-trip without conversion or inference. Controlled results and errors
remain redacted. SQL failures, constraint names, values, identifiers, amounts,
timestamps, provider references, and payloads do not escape through public
results or rendering.

## Alternatives rejected

A mutable order snapshot was rejected because absence or failure could erase
known evidence and history could diverge from the current row. Persisting the
whole aggregate as canonical JSON was rejected because constraints, correction
lineage, querying, and deterministic migration would be weaker. Reusing the
Financial Ledger as evidence storage was rejected because identity and
collection-attempt evidence are not financial movements. Updating a projection
inside this task was rejected because P0.3 owns read semantics. Publishing an
in-memory event after commit was rejected because a crash could lose the only
projection notification. Provider pagination and retry state were rejected
because Connector Runtime owns those mechanics.

## Consequences

Evidence survives restart, concurrent delivery, retries, and provider failure.
Downstream projections receive atomic change notification and can always rebuild
from canonical history. The cost is normalized persistence code and integration
tests for replay, constraints, concurrency, rollback, and outbox atomicity.

## Authorization

SPEC-0042 authorizes one bounded implementation task for the application port,
Postgres migration and adapter, focused domain contract tests, Postgres
integration tests, evidence report, and journal update. It authorizes no API,
UI, provider adapter, Economic Truth materializer, Ledger materializer,
reconciliation change, decision readiness, or Kernel change.
