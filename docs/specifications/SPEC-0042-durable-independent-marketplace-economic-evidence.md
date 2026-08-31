# SPEC-0042: Durable Independent Marketplace Economic Evidence

Status: Accepted

Date: 2026-08-31

## Objective

Persist the TASK-0140 independent evidence aggregate as organization-scoped,
append-only Postgres history with deterministic replay, optimistic
serialization, cross-restart idempotency, and atomic outbox notification.

## Dependencies

This specification depends on:

- ADR-0042 and SPEC-0041;
- TASK-0140 implementation;
- ADR-0043;
- existing organization lifecycle persistence;
- existing Postgres/Flyway and transactional-outbox foundations.

It reuses the current evidence model without changing its accepted meaning.

## New application contract

TASK-0143 adds one production file beside the evidence aggregate. It defines:

### `MarketplaceEconomicEvidenceVersion`

- non-negative signed `Long`;
- `ZERO` for an empty, unpersisted subject;
- `next()` fails closed at overflow;
- comparable and value-equal;
- renders `[INTERNAL]`;
- raw value is available only to the persistence adapter.

### `VersionedMarketplaceIndependentEconomicEvidence`

Contains exactly:

- one `MarketplaceIndependentEconomicEvidence`;
- one `MarketplaceEconomicEvidenceVersion`.

It validates subject ownership through the aggregate and renders `[REDACTED]`.

### Read result

`MarketplaceIndependentEconomicEvidenceReadResult` contains:

```text
Found(versioned evidence)
NotFound
IntegrityFailure
```

Every result has bounded redacted rendering.

### Persist result

`MarketplaceIndependentEconomicEvidencePersistResult` contains:

```text
Applied(versioned evidence)
Duplicate(versioned evidence)
StaleVersion(current version)
OrganizationUnavailable
SubjectMismatch
IdentifierConflict
SourceFactConflict
SupersededFactNotFound
SupersededTargetNotFact
FactAlreadySuperseded
ReplacementIdentifierConflict
ReplacementSourceFactConflict
IntegrityFailure
```

Domain conflict names map one-to-one to the existing merger. Persistence must
not collapse them into a generic conflict. Every result renders redacted or
internal and exposes no sensitive value through exceptions.

### Repository port

`MarketplaceIndependentEconomicEvidenceRepository` exposes exactly:

```text
find(subject) -> read result

apply(expectedVersion, update) -> persist result
```

The port has no JDBC, SQL, transaction, Postgres, Flyway, JSON, provider,
connector, event-delivery, clock, logger, API, or UI type.

The same file provides the minimum cross-module encoding bridge:

- a public `MarketplaceEconomicEvidenceObservationId.valueForPersistence()`
  extension that returns its UUID for JDBC binding;
- observation ID reconstruction only through the existing canonical `parse`;
- the version raw `Long` through an explicitly persistence-named accessor.

All of those types continue to render redacted/internal. The bridge must not
expose a new public constructor, generic serialization model, JSON DTO, or
logging representation.

## Domain contract behavior

Focused tests prove:

- version zero, ordering, next, overflow, and redaction;
- versioned aggregate equality and redaction;
- all read and persist result renderings;
- the port production bytecode introduces no infrastructure dependency;
- existing TASK-0140 behavior remains unchanged.

## Postgres schema

Migration `V015__create_independent_marketplace_economic_evidence.sql` creates
an append-oriented model. Exact names may follow repository conventions, but
the following logical structures are mandatory.

### Subject root

Retains:

- organization ID;
- internal marketplace order ID;
- marketplace key;
- external marketplace order ID;
- currency;
- current evidence version.

Primary key is organization plus internal order. Subject attributes are
immutable. Version starts at zero and never decreases.

### Applied update journal

Retains one row per committed aggregate version:

- organization and internal order;
- version;
- update identifier;
- change kind: fact, attempt, or correction;
- committed timestamp supplied by the database for audit only.

Organization/order/version and organization/order/update identifier are unique.
The committed timestamp does not replace domain occurrence, observation, or
attempt time.

### Component fact

Retains the complete existing component observation:

- observation ID, family, component ID and type;
- direction, exact non-negative magnitude, currency;
- source kind, system key, present external reference or explicit internal
  absence reason;
- occurrence time, evidence quality, coverage claim, observation time;
- committed aggregate version.

Constraints preserve permitted null shape, currency consistency, microsecond
precision, and component/family compatibility.

### External identity fact

Retains observation ID, family, identity kind, anchor reference, linked system
and reference, complete source provenance, occurrence and observation time, and
committed version. It contains no amount or financial allocation.

### Collection attempt

Retains observation ID, family, source-system key, bounded attempt outcome,
attempt time, and committed version. It contains no amount, coverage claim, or
accepted-fact semantics.

### Correction

Retains correction ID, replacement fact ID, superseded fact ID, reason,
correction observation time, and committed version. Replacement fact and
correction are inserted together at one aggregate version. Foreign keys prevent
cross-subject correction and correction of an attempt or correction record.

### Immutability

Application-role update and delete of subject history, facts, attempts, and
corrections are rejected. Root version may advance only through the repository
transaction. Existing repository migration conventions determine trigger or
privilege mechanics.

## Durable application algorithm

### Find

Within one read transaction:

1. find the exact root by organization and internal order;
2. return `NotFound` when absent;
3. compare all supplied subject attributes to the root and return
   `IntegrityFailure` on mismatch;
4. load journal and subtype rows in committed version order;
5. start with `MarketplaceIndependentEconomicEvidence.empty(subject)`;
6. reconstruct each update and apply it through the existing merger;
7. require every replay step to produce `Applied` and every journal version to
   be contiguous from one through root version;
8. return `IntegrityFailure` for malformed, missing, duplicate, non-contiguous,
   incompatible, or non-replayable persisted state;
9. return `Found` with value-equal aggregate and version.

The adapter performs no provider call, clock decision, freshness assessment,
or materialization during read. Historical reads remain available when the
owning organization is suspended; organization scope is still mandatory.

### Apply

Within one database transaction:

1. lock the existing root for the organization and internal order, when any;
2. validate the complete subject and reconstruct current evidence under lock;
3. apply the existing domain merger;
4. map `Duplicate` to durable `Duplicate` using current version, regardless of
   a stale expected version;
5. require and lock an active organization before any non-duplicate result can
   write or establish a root;
6. map every domain conflict without writing;
7. for domain `Applied`, compare expected and current version;
8. return `StaleVersion` without writing when they differ;
9. establish a version-zero root only after the first update is known to be
   compatible and eligible to apply;
10. append the update journal and subtype rows at `current.next()`;
11. update root version exactly once;
12. insert exactly one outbox notification;
13. commit;
14. return `Applied` with the domain result and new version.

Any exception rolls back every row and returns `IntegrityFailure` without
leaking database details.

## Idempotency and concurrency

- Same identifier and equal payload after repository recreation returns
  `Duplicate` with unchanged version.
- Same identifier with different payload returns `IdentifierConflict`.
- Equal financial source fact under a new observation identifier returns
  `Duplicate`; different meaning returns `SourceFactConflict`.
- A compatible update against a stale version returns `StaleVersion`.
- One retry after stale reload may apply normally.
- Concurrent first writers establish one root.
- Concurrent exact duplicates create one update and one event.
- Concurrent compatible facts never overwrite; one may return stale and retry.
- Concurrent conflicting source facts never both become active.
- Concurrent corrections of the same fact admit one successor.

## Outbox contract

Each durable `Applied` update inserts one row into the existing
`integration_event_outbox`:

```text
event_type: marketplace.economic-evidence.changed.v1
content_type: application/json
organization_id: update subject organization
occurred_at: fact observedAt, attempt attemptedAt, or correction observedAt
```

Payload schema version 1 contains only:

```json
{
  "marketplaceOrderId": "internal canonical UUID",
  "evidenceVersion": 1,
  "changeKind": "FACT|ATTEMPT|CORRECTION"
}
```

Serialization is canonical and deterministic. No amount, marketplace external
order, provider reference, linked identity, source-system key, tax/invoice/Ads
identity, or correction reason appears. Event ID may use the existing injected
UUID event-identifier pattern; only one committed event exists per applied
version.

Duplicate, stale, unavailable, conflict, and integrity results emit no event.
The evidence journal is replay authority. The outbox is delivery only.

## Organization lifecycle

New roots and mutations require an active organization. Historical reads remain
available after suspension, matching the existing Financial Ledger audit
boundary. Exact replay of a previously committed update while suspended returns
`Duplicate` and never mutates state. A new or conflicting update while suspended
returns `OrganizationUnavailable`. Historical retention is never deleted by
suspension.

## Precision and privacy

- money persists as exact decimal and round-trips through `MarketplaceMoney`;
- no `Float` or `Double` enters new code or schema mapping;
- all domain timestamps retain whole-microsecond precision exactly;
- database commit time never replaces source or Genesis observation time;
- controlled results and public types render `[REDACTED]` or `[INTERNAL]`;
- errors expose no organization, order, marketplace, external reference,
  amount, timestamp, source, family, identity kind, correction reason, SQL, or
  constraint name.

## Required Postgres tests

TASK-0143 proves at least:

1. V015 is the latest successful Flyway migration;
2. inactive organization rejects a first update with no root or history;
3. first fact establishes root and version one;
4. fresh find returns value-equal evidence;
5. exact replay after new repository instance is duplicate;
6. identifier conflict survives restart;
7. source-fact duplicate and conflict survive restart;
8. authoritative zero shipping round-trips exactly;
9. missing attempt contains no amount and does not regress known shipping;
10. COGS without ERP order identity round-trips;
11. invoice identity and tax remain separate;
12. Ads identity creates no Ads allocation;
13. provider temporary failure after known fact does not regress it;
14. correction preserves original, replacement, and active history;
15. second correction of the superseded fact is rejected;
16. correction replacement and correction roll back together on failure;
17. active and historical facts are value-equal after replay;
18. canonical ordering is independent of ingestion order;
19. exact microsecond precision round-trips;
20. provider clock order remains uninferred;
21. exact decimal precision and currency round-trip;
22. same external order can exist in another organization;
23. cross-organization reads and correction references fail closed;
24. compatible stale write returns current version and applies after reload;
25. concurrent first create yields one root;
26. concurrent exact duplicate yields one row and one event;
27. concurrent compatible facts converge after retry without overwrite;
28. concurrent source conflict admits no silent replacement;
29. concurrent correction race admits one successor;
30. every applied fact, attempt, and correction emits one atomic outbox row;
31. duplicate and rejected operations emit none;
32. outbox payload contains only the allowed locator/version/change fields;
33. injected failure before commit leaves root, history, version, and outbox
    unchanged;
34. database history update/delete is rejected;
35. malformed or non-contiguous persisted state returns `IntegrityFailure`;
36. every new type and result is redacted;
37. no provider, API, UI, Economic Truth, Ledger, Reconciliation, pricing,
    decision, action, AI, or Kernel behavior changes;
38. focused domain tests, complete marketplace tests, complete Postgres tests,
    full build, and `git diff --check` pass.

The suite must additionally prove, as part of the cases above, that historical
read and exact duplicate replay remain available after suspension, a new update
after suspension is unavailable, and a rejected first correction leaves no
empty subject root.

## Implementation file scope

TASK-0143 may alter only:

1. production application contract:
   `applications/marketplace-operations/src/main/kotlin/io/flooow/marketplace/operations/economics/evidence/MarketplaceIndependentEconomicEvidencePersistence.kt`;
2. focused application contract test:
   `applications/marketplace-operations/src/test/kotlin/io/flooow/marketplace/operations/economics/evidence/MarketplaceIndependentEconomicEvidencePersistenceTest.kt`;
3. Flyway migration:
   `applications/marketplace-operations-persistence-postgres/src/main/resources/db/migration/V015__create_independent_marketplace_economic_evidence.sql`;
4. Postgres adapter:
   `applications/marketplace-operations-persistence-postgres/src/main/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceIndependentEconomicEvidenceRepository.kt`;
5. Postgres adapter test:
   `applications/marketplace-operations-persistence-postgres/src/test/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceIndependentEconomicEvidenceRepositoryTest.kt`;
6. `docs/evidence/TASK-0143-durable-independent-marketplace-economic-evidence.md`;
7. one TASK-0143 entry in `docs/journal/MGI-EXECUTIVE-JOURNAL.md`.

No dependency, API wiring, runtime activation, existing production file,
existing migration, or TASK-0140 domain file may change. If implementation
proves the existing public domain API insufficient for exact replay, TASK-0143
must stop and return to ADR/SPEC correction rather than widening scope.

## Quality gates

Before commit and push:

- focused persistence-contract tests;
- focused Postgres repository tests with Testcontainers;
- all `applications:marketplace-operations` tests;
- all `applications:marketplace-operations-persistence-postgres` tests;
- full build;
- mechanical forbidden-dependency and forbidden-reference scan;
- exact seven-file scope check;
- `git diff --check`.

PR CI must pass without bypass. No merge is authorized while checks fail or the
branch conflicts with current `main`.

## Explicitly excluded

- Mercado Livre or Omie adapters and credentials;
- connector pagination, retry, leases, or provider checkpoints;
- Sales Intelligence projection, API, UI, list, or detail;
- Economic Truth, Financial Ledger, or Reconciliation materialization;
- Ads allocation policy;
- decision readiness, recommendation, authority, action, or AI;
- settlement, returns, Commerce Network, or fulfillment implementation;
- Kernel changes;
- production deployment or data backfill.

## Acceptance

Merging ADR-0043 and this specification authorizes TASK-0143 only. P0.3 and
provider work remain blocked until TASK-0143 is implemented, tested, merged,
and reinspected from canonical `main`.
