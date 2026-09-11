# SPEC-0045: Durable Marketplace Economic Evidence Incremental Change Feed — Slice A

Status: Accepted

Date: 2026-09-02

## Objective

Define the smallest inward-facing production contract and future Postgres
persistence boundary required to consume committed independent marketplace
economic-evidence changes incrementally and resumably per organization.

This specification covers P0.3 Slice A only:

- the incremental invalidation/change feed;
- durable projection checkpoints;
- deterministic pending-organization discovery;
- a future Postgres adapter;
- boundary, persistence, concurrency, and regression tests.

It does not define or authorize Slice B, a materialized Sales Intelligence
projection, an API, a UI, provider ingestion, or production implementation.

## Authority and dependencies

This specification depends on and must preserve:

- ADR-0046, Accepted at commit `efe0679`;
- RFC-0007 v0.3;
- EXP-0006 at experimental commit `1898629`;
- `docs/evidence/EXP-0006-durable-evidence-incremental-change-feed.md`;
- `docs/evidence/EXP-0006-pending-discovery-follow-up.md`;
- ADR-0045;
- SPEC-0044;
- P0.2/TASK-0144 and the existing V015 evidence journal.

Where this specification is silent, those accepted decisions remain
authoritative. Experimental code is evidence, not production code to copy
without review.

## Scope boundary

Slice A introduces a new port named:

```text
MarketplaceEconomicEvidenceChangeFeed
```

It is separate from and must not extend, implement, wrap into, or modify:

```text
MarketplaceIndependentEconomicEvidenceRepository
```

The P0.2 economic repository continues to expose exactly:

```text
find
apply
```

The Slice A port exposes exactly:

```text
changesSince
organizationsWithPendingChanges
currentCheckpoint
advanceCheckpoint
```

No fifth operation is permitted.

## Application contract

The future application contract belongs beside the P0.2 persistence contract
under:

```text
io.flooow.marketplace.operations.economics.evidence
```

It reuses the existing concrete types:

- `OrganizationId`;
- `MarketplaceEconomicEvidenceSubject`;
- `MarketplaceEconomicEvidenceVersion`.

It introduces only the value, result, change, and port types required by this
specification.

### Closed operation envelope

Infrastructure failures must follow the existing P0.2 fail-closed result
pattern. The public port must not throw or expose an infrastructure-specific
exception. Each of the four methods returns a closed generic envelope:

```text
MarketplaceEconomicEvidenceChangeFeedResult<T>
```

with exactly:

```text
Success(value: T)
IntegrityFailure
```

`Success` preserves the exact operation value. `IntegrityFailure` contains no
payload. Both variants render `[REDACTED]` and expose no SQL, SQLSTATE,
constraint, table, organization, projection, subject, sequence, or database
value.

Invalid caller values rejected by constructors or limit preconditions remain
programmer-contract violations, consistent with existing validated value
objects. Database, mapping, transaction, malformed-data, and unexpected
adapter failures become `IntegrityFailure`.

### Exact port signatures

The port has exactly these four methods and no inherited application
operations:

```kotlin
interface MarketplaceEconomicEvidenceChangeFeed {
    fun changesSince(
        organizationId: OrganizationId,
        checkpoint: ChangeSequenceCheckpoint,
        limit: Int
    ): MarketplaceEconomicEvidenceChangeFeedResult<List<MarketplaceEconomicEvidenceChange>>

    fun organizationsWithPendingChanges(
        projectionName: ProjectionName,
        limit: Int
    ): MarketplaceEconomicEvidenceChangeFeedResult<List<OrganizationId>>

    fun currentCheckpoint(
        organizationId: OrganizationId,
        projectionName: ProjectionName
    ): MarketplaceEconomicEvidenceChangeFeedResult<ChangeSequenceCheckpoint>

    fun advanceCheckpoint(
        organizationId: OrganizationId,
        projectionName: ProjectionName,
        expected: ChangeSequenceCheckpoint,
        next: ChangeSequenceCheckpoint
    ): MarketplaceEconomicEvidenceChangeFeedResult<CheckpointAdvanceResult>
}
```

Returned lists are bounded values of their `Success` result. The contract
must not return null, mutable adapter-owned collections, JDBC cursors, streams,
or lazy persistence handles.

## Value contracts

### `ChangeSequenceCheckpoint`

`ChangeSequenceCheckpoint` is a regular immutable class, not an
`@JvmInline value class`. It has:

- one private `Long` field;
- constructor validation requiring a non-negative value;
- `Comparable<ChangeSequenceCheckpoint>`;
- explicit value-based `equals` and `hashCode`;
- `NONE` and `ZERO`, both representing zero and value-equal;
- exactly one public raw-value accessor named `valueForPersistence()`;
- `toString()` equal to `[INTERNAL]`.

The class does not need `next()` because Slice A reads database-assigned
sequences and advances to an explicitly supplied checkpoint. If a future
accepted specification adds `next()`, it must fail closed at `Long.MAX_VALUE`.

No generated or explicit public `Long` accessor, unbox method, component
method, copy method, public field, or alternate raw conversion is permitted.
The focused contract test must inspect the compiled JVM surface mechanically.

The physical PostgreSQL sequence is global and may contain gaps. A checkpoint
is meaningful only with its organization. The type does not promise
cross-organization ordering or sequence contiguity.

### `ProjectionName`

`ProjectionName` is a regular immutable class with one private `String`
field. It validates all of the following:

- not blank;
- length from 1 through 100 characters inclusive;
- exact regular expression `^[a-z0-9][a-z0-9-]*$`.

It provides explicit value-based `equals` and `hashCode`, exactly one public
raw-value accessor named `valueForPersistence()`, and a safe `toString()`
equal to `[INTERNAL]`.

It has no SQL, PostgreSQL, JDBC, Flyway, provider, connector, API, UI, or
infrastructure dependency. Different projection names identify independent
consumers of the same durable journal.

### Concrete bounded limits

ADR-0046 and RFC-0007 require positive bounded limits. No existing production
change-feed limit convention exists, and the validated EXP-0006 harness used
`MAX_LIMIT = 1_000`. Human review approved and this specification freezes:

```text
changesSince.limit: 1..1000 inclusive
organizationsWithPendingChanges.limit: 1..1000 inclusive
```

Values below 1 or above 1000 fail before persistence access. No unbounded
`Int` reaches the adapter.

## Change contract

### `MarketplaceEconomicEvidenceChange`

The immutable change contains exactly:

```text
subject: MarketplaceEconomicEvidenceSubject
evidenceVersion: MarketplaceEconomicEvidenceVersion
changeSequence: ChangeSequenceCheckpoint
changeKind: MarketplaceEconomicEvidenceChangeKind
```

The subject is complete and therefore retains its organization ID, internal
marketplace order ID, marketplace key, external marketplace order ID, and
currency through the existing accepted type.

The change contains no:

- component, fact, attempt, or correction payload;
- amount, allocation, coverage, source, or provider payload;
- SQL or persistence metadata;
- `committed_at` or other database timestamp;
- checkpoint mutation state.

Its `toString()` is `[REDACTED]`.

### `MarketplaceEconomicEvidenceChangeKind`

`MarketplaceEconomicEvidenceChangeKind` is a closed enum with exactly the
values already persisted by V015:

```text
FACT
ATTEMPT
CORRECTION
```

The public change surface must not use a raw `String` in place of this type.
An unknown persisted value fails closed as `IntegrityFailure`.

### Invalidation semantics

The feed reports committed invalidations. It is not:

- a component-payload feed;
- an economic aggregate snapshot;
- an exact aggregate-at-version replay contract;
- an evidence timeline;
- an external delivery event.

A consumer may resolve current evidence by passing the returned complete
subject to `MarketplaceIndependentEconomicEvidenceRepository.find(subject)`.
The resulting aggregate may be newer than the `evidenceVersion` on the
change. Slice A makes no promise of reconstructing every intermediate state.

## `changesSince` semantics

Each invocation is scoped to exactly one `organizationId` and one exclusive
checkpoint. A successful read must:

1. select only committed rows whose `organization_id` equals the requested
   organization;
2. require `change_sequence > checkpoint`;
3. order by `change_sequence ASC`;
4. apply the validated positive bounded limit;
5. return an empty list when no matching committed change exists;
6. return deterministic results for stable durable state;
7. permit gaps without treating them as corruption;
8. avoid asserting global ordering across organizations;
9. leave every checkpoint unchanged.

An unknown or nonexistent organization returns `Success(emptyList())`. The
read creates no organization, subject, journal, or checkpoint row.

Repeated forward pagination uses the final returned `changeSequence` as the
next exclusive checkpoint. It must not repeat or omit a row that remains in
stable durable state.

The future Postgres implementation must reconstruct the complete subject in
the same read using an inner join between:

```text
marketplace_economic_evidence_update
marketplace_economic_evidence_subject
```

on organization and internal marketplace order. A missing, duplicated,
inconsistent, malformed, or unmappable subject row yields `IntegrityFailure`;
the consumer is not required to perform a second subject lookup.

The canonical conceptual SQL shape is:

```sql
SELECT
    u.organization_id,
    u.marketplace_order_id,
    u.evidence_version,
    u.change_sequence,
    u.change_kind,
    s.marketplace_key,
    s.external_order_id,
    s.currency
FROM marketplace_economic_evidence_update u
JOIN marketplace_economic_evidence_subject s
  ON s.organization_id = u.organization_id
 AND s.marketplace_order_id = u.marketplace_order_id
WHERE u.organization_id = ?
  AND u.change_sequence > ?
ORDER BY u.change_sequence ASC
LIMIT ?
```

Equivalent formatting and explicit column qualification are permitted;
semantic widening is not.

## Durable checkpoint semantics

A durable checkpoint is identified by exactly:

```text
organization_id + projection_name
```

No durable row means `ChangeSequenceCheckpoint.NONE`, value-equal to `ZERO`.

### `currentCheckpoint`

`currentCheckpoint` is a read-only operation:

- a missing row returns `Success(NONE)`;
- an unknown or nonexistent organization also returns `Success(NONE)` and
  creates no organization or checkpoint row;
- an existing valid row returns `Success(durable checkpoint)`;
- malformed, negative, duplicate, inconsistent, or unmappable data returns
  `IntegrityFailure`;
- database or transaction failure returns `IntegrityFailure` without leaking
  infrastructure detail;
- it never creates, updates, or deletes a row.

A checkpoint row created under the schema required by this specification is
structurally guaranteed to reference committed journal history of its own
organization. If legacy, corrupted, or inconsistent state is nevertheless
observed during mapping or migration scenarios, `currentCheckpoint` fails
closed as `IntegrityFailure`; it must never return a structurally invalid
checkpoint as `Success`.

The normal `currentCheckpoint` read does not require a redundant journal
lookup merely to reproduce the invariant already enforced by the composite
foreign key.

Historical checkpoint reading is not conditioned on organization ACTIVE
status. Organization identity and foreign-key integrity remain mandatory.

### `CheckpointAdvanceResult`

`CheckpointAdvanceResult` is a closed result family with exactly:

```text
Advanced(checkpoint: ChangeSequenceCheckpoint)
Stale(currentCheckpoint: ChangeSequenceCheckpoint)
Regression
```

There is no fourth checkpoint result. Infrastructure failure is represented
only by the outer
`MarketplaceEconomicEvidenceChangeFeedResult.IntegrityFailure`.

All three checkpoint results render `[REDACTED]`. `Advanced.checkpoint` is the
value durably stored by the successful operation. `Stale.currentCheckpoint`
is the durable value observed after the compare-and-set failed. `Regression`
has no payload because the caller already supplied `expected` and `next`, and
the adapter performs no checkpoint read or write for this case.

### Compare-and-set rules

The operation applies these rules in this exact conceptual order:

1. validate scalar/domain arguments, including value objects and accepted
   bounds;
2. when `next <= expected`, return `Success(Regression)` with zero database
   write and without relational destination validation;
3. when `next > expected`, verify that the requested organization exists and
   that `next` belongs to committed journal history for that same
   organization;
4. when the organization exists but the destination does not, fail through
   the same sanitized programmer-contract/precondition mechanism used for an
   invalid limit, with zero checkpoint write;
5. load or perform the durable checkpoint compare-and-set;
6. when durable state equals expected, advance to `next` and return
   `Success(Advanced(next))`;
7. when durable state differs from expected, return
   `Success(Stale(durable))` with zero durable mutation.

The cheap regression rule is:

```text
next <= expected
-> Success(Regression)
-> zero database write
```

For `next > expected`, destination validity is conceptually:

```sql
EXISTS (
    SELECT 1
    FROM marketplace_economic_evidence_update
    WHERE organization_id = ?
      AND change_sequence = ?
)
```

The requested organization must itself exist in `integration_organization`.
If it does not exist, the operation returns `IntegrityFailure` and creates no
checkpoint row. This unknown-organization rule applies after the mandatory
cheap `next <= expected` regression short-circuit.

When the organization exists, all of the following are invalid relational
caller input:

- a sequence committed only for another organization;
- a future or invented sequence;
- a physical gap for which no committed row of the requested organization
  has that value.

Invalid relational caller input raises a sanitized caller-contract violation,
equivalent to the invalid-limit precondition. It does not return
`IntegrityFailure`, `Stale`, or `Regression`, and it performs zero checkpoint
write. Its type and message expose no SQL, SQLSTATE, table, constraint,
organization, or sequence value.

A database, connection, query, transaction, or mapping failure while checking
organization or destination validity is not invalid caller input. It returns
the closed outer `IntegrityFailure` and leaks no infrastructure detail.

Destination validity creates no contiguity requirement. Gaps before or after
a valid committed `next` remain permitted. The normal flow is:

```text
changesSince(...)
-> consumer processes returned batch
-> last returned changeSequence
-> advanceCheckpoint(..., next = last returned changeSequence)
```

Slice A does not attempt to prove that the consumer processed the batch.

After destination validation, durable CAS follows:

```text
durable == expected
-> durable advancement to next
-> Success(Advanced(next))
```

```text
durable != expected
-> Success(Stale(durable))
-> zero durable mutation
```

A missing row is durable `NONE`:

```text
missing + expected NONE + next > NONE
-> compare-and-set creation
-> Success(Advanced(next))
```

```text
missing + expected != NONE
-> Success(Stale(NONE))
-> zero row created
```

The contract does not allocate `change_sequence`; it stores an explicitly
supplied, committed checkpoint belonging to the requested organization. Slice
A does not add checkpoint state to a P0.2 aggregate, update, read result, or
persist result.

### Consumer checkpoint responsibility

Slice A validates that `next` is a structurally valid committed destination
for the requested organization. Slice A does not prove that the consumer
successfully processed every earlier returned change.

A conforming consumer must advance its checkpoint only after successfully
handling the changes it intends to acknowledge through that destination. The
typical flow is:

```text
currentCheckpoint
-> changesSince
-> process returned changes
-> determine last successfully handled changeSequence
-> advanceCheckpoint(next = that sequence)
```

If a consumer deliberately advances from 100 directly to a valid committed
200 without handling 101 through 199, that is consumer misuse. Slice A does
not prevent that logical skip.

Explicit checkpoint advancement supports replay-tolerant and at-least-once
consumption only when the consumer advances after successful handling. The
feed does not independently guarantee processing correctness against caller
misuse.

This responsibility does not introduce an acknowledgement token, batch token,
fifth port method, lease, scheduler state, or Slice B transaction semantic.

## Checkpoint concurrency

The future adapter must implement a single durable compare-and-set transaction
safe under concurrent first-row and existing-row writers.

For two first writers using the same organization, projection name, expected
`NONE`, and the same valid committed next value:

```text
exactly one Success(Advanced)
exactly one Success(Stale)
```

Destination validation must occur before either compare-and-set mutation.
The concurrency guarantees above remain mandatory after that validation.

For two writers against an existing row using the same expected value:

```text
exactly one Success(Advanced)
exactly one Success(Stale)
```

Unique violations and serialization races are internal adapter concerns.
No `SQLException`, SQLSTATE, constraint name, SQL text, or database payload
may escape through the port. The implementation may use an atomic conditional
`UPDATE`, conflict-safe first-row `INSERT`, and bounded transaction retry only
when required by measured PostgreSQL behavior and covered by tests. It must
not acquire scheduler, worker, lease, or claim locks.

## Candidate checkpoint persistence

A future next migration may create exactly one production table named:

```text
marketplace_economic_evidence_projection_checkpoint
```

The required logical schema is:

```sql
CREATE TABLE marketplace_economic_evidence_projection_checkpoint (
    organization_id uuid NOT NULL,
    projection_name text NOT NULL,
    last_change_sequence bigint NOT NULL,
    updated_at timestamptz(6) NOT NULL,
    PRIMARY KEY (organization_id, projection_name),
    FOREIGN KEY (organization_id)
        REFERENCES integration_organization (organization_id),
    FOREIGN KEY (organization_id, last_change_sequence)
        REFERENCES marketplace_economic_evidence_update (
            organization_id,
            change_sequence
        ),
    CHECK (projection_name ~ '^[a-z0-9][a-z0-9-]{0,99}$'),
    CHECK (last_change_sequence >= 0)
);
```

Every persisted checkpoint row represents a non-`NONE` checkpoint and must
reference an existing committed journal row of the same organization.
Therefore:

- an absent checkpoint row represents `NONE`/`ZERO`;
- a persisted checkpoint cannot reference an invented or future sequence;
- it cannot reference a physical gap with no journal row;
- it cannot reference a sequence owned only by another organization;
- direct SQL bypass is also rejected by PostgreSQL;
- gaps between valid committed checkpoints remain allowed;
- no sequence-contiguity requirement exists.

The composite foreign key is the durable integrity backstop. Adapter
relational validation and the database composite foreign key are
complementary: adapter validation provides the specified caller-contract
semantics, while the foreign key protects durable state against bypass, bugs,
future code paths, and direct SQL.

The migration must also install a `BEFORE INSERT OR UPDATE` trigger backed by
a migration-owned function that always assigns:

```sql
NEW.updated_at := transaction_timestamp();
```

The timestamp is database-authoritative, uses microsecond-capable
`timestamptz(6)`, and is not caller-supplied authority. Application code may
not override it. The migration must not alter V015 or any earlier migration.

The primary key supports exact organization/projection lookup. This
specification does not require an additional checkpoint-table index without
measured evidence. The existing V015 unique index on
`(organization_id, change_sequence)` remains the journal access path.

This specification intentionally does not assign a migration number. The
future TASK must resolve the next free migration number mechanically from
canonical `main` immediately before implementation.

## Pending-organization discovery

`organizationsWithPendingChanges` discovers bounded work for one projection
name. A successful read must:

- validate the limit before database access;
- treat an absent checkpoint row as `NONE`/zero;
- include an organization when at least one journal row has
  `change_sequence` greater than that projection's checkpoint;
- exclude an organization whose checkpoint is current;
- order results by `organization_id ASC` deterministically;
- apply the positive bounded limit;
- preserve independence between projection names;
- make no scheduling-fairness guarantee;
- perform no checkpoint mutation.

Discovery starts from persisted `integration_organization` rows and therefore
cannot return an unknown or nonexistent organization.

The canonical Query B shape is:

```sql
SELECT o.organization_id
FROM integration_organization o
LEFT JOIN marketplace_economic_evidence_projection_checkpoint c
  ON c.organization_id = o.organization_id
 AND c.projection_name = ?
WHERE EXISTS (
    SELECT 1
    FROM marketplace_economic_evidence_update u
    WHERE u.organization_id = o.organization_id
      AND u.change_sequence >
          COALESCE(c.last_change_sequence, 0)
)
ORDER BY o.organization_id ASC
LIMIT ?
```

The controlled representative-volume PostgreSQL/Testcontainers regression
must execute this canonical Query B, capture and retain its complete
`EXPLAIN (ANALYZE, BUFFERS)` plan, and demonstrate the expected indexed
`EXISTS` access path through the existing V015
`(organization_id, change_sequence)` journal index under that fixture. It
must also prove that the implemented query is not Query A/full-journal
`GROUP BY`/`MAX`.

PostgreSQL planner choices outside that controlled fixture are not part of the
public change-feed contract. This qualification does not remove or weaken the
representative performance-regression test. No change to V015 is required or
permitted.

The full-journal Query A shape using `GROUP BY organization_id` and
`MAX(change_sequence)` is forbidden as this specification's implementation.
Query C using a latest-sequence lookup is not the canonical strategy.

### Known fairness limitation

Fixed:

```text
ORDER BY organization_id ASC
LIMIT N
```

can starve organizations outside the first batch while earlier organizations
remain pending and their checkpoints do not advance. This behavior is known
and accepted for Slice A.

`organizationsWithPendingChanges` identifies work; it is not a scheduler.
No test may fail Slice A solely because fairness is absent. Tests must prove
deterministic bounded discovery and document the observed starvation
limitation without adding a remedy.

Slice A must not introduce:

- a fairness cursor;
- a lease or claim;
- worker ownership;
- `SKIP LOCKED`;
- scheduler state;
- retry-after or failure counters;
- last-served state;
- round-robin ordering.

## Isolation and lifecycle

The future implementation must preserve all of the following:

- `changesSince` never returns a different organization's changes;
- checkpoint identity includes organization and projection name;
- two organizations can hold and advance checkpoints independently;
- two projection names within one organization can hold and advance
  checkpoints independently;
- pending discovery evaluates the requested projection only;
- no feed operation widens or bypasses organization identity;
- organization suspension does not delete historical changes or checkpoints;
- Slice A performs no organization lifecycle mutation.

No cross-organization ordering guarantee or shared logical checkpoint exists.

## Transaction boundaries

### `changesSince`

One read-only operation. It joins journal and subject data and performs no
checkpoint or economic-evidence mutation.

### `organizationsWithPendingChanges`

One read-only operation. It performs no claim, scheduling, checkpoint, or
economic-evidence mutation.

### `currentCheckpoint`

One read-only operation. Missing state maps to `NONE`; no row is created.

### `advanceCheckpoint`

After scalar validation and the cheap regression short-circuit, one durable
transaction validates organization and committed destination membership and
performs the compare-and-set. It may mutate only the single
organization/projection checkpoint row addressed by the call. An invalid
destination or unknown organization leaves zero checkpoint row or mutation.

Checkpoint advancement must not share a transaction with:

- materialization;
- an economic-evidence write;
- a scheduler claim;
- a provider or connector call;
- outbox delivery.

Slice B must decide materialization/checkpoint atomicity, replay, and
idempotency separately.

## Error and privacy boundary

The adapter catches and internally classifies infrastructure failures. Public
results expose only `IntegrityFailure` and never expose:

- `SQLException` or a driver exception;
- SQLSTATE;
- constraint, function, trigger, table, schema, or column names;
- SQL text or bound values;
- database host, credentials, or configuration;
- organization, order, marketplace, external reference, currency,
  projection name, version, sequence, or timestamp.

A relationally invalid checkpoint destination for an existing organization is
a sanitized caller-contract/precondition violation, not an infrastructure
failure. The public violation must use the same mechanism as an invalid limit
and contain no raw input or persistence detail. Failure to execute or map the
validation query is instead `IntegrityFailure`.

Unknown `change_kind`, negative persisted values, invalid projection names,
malformed subjects, duplicate rows where uniqueness is expected, and mapping
inconsistency fail closed. No partial collection or partially reconstructed
success may be returned after an integrity failure.

All new values, changes, envelopes, and checkpoint result types render
`[INTERNAL]` or `[REDACTED]` exactly as specified. No default data-class
rendering may expose fields.

## Architectural purity

The inward-facing application contract must not depend on or reference:

- `java.sql` or `javax.sql`;
- JDBC, PostgreSQL, or Flyway;
- JSON or serialization frameworks;
- API or UI types;
- provider or connector types;
- outbox or event-delivery types;
- `MarketplaceFinancialLedger`;
- reconciliation;
- Kernel types or behavior.

The Postgres adapter remains outside the inward-facing module. Dependencies
continue to point inward. Marketplace-specific concepts remain outside the
Kernel.

`change_sequence` appears only as `ChangeSequenceCheckpoint` on the Slice A
change-feed/checkpoint surface and as persistence state. It must never become
a field of:

- `MarketplaceIndependentEconomicEvidence`;
- `MarketplaceIndependentEconomicEvidenceUpdate`;
- `VersionedMarketplaceIndependentEconomicEvidence`;
- `MarketplaceIndependentEconomicEvidenceReadResult`;
- `MarketplaceIndependentEconomicEvidencePersistResult`.

## Mandatory test matrix

The future implementation must map every numbered case to a focused contract,
Postgres integration, migration, or regression test. Tests use real
PostgreSQL/Testcontainers where persistence semantics are asserted.

### Value contracts

1. `ChangeSequenceCheckpoint` rejects a negative value.
2. `NONE` and `ZERO` both represent zero and are value-equal.
3. Checkpoints provide deterministic ordering, equality, and hash semantics.
4. Reflection and compiled JVM-surface inspection prove that
   `valueForPersistence()` is the only public raw-`Long` accessor and no
   inline unbox accessor exists.
5. A valid `ProjectionName` is accepted and value-equal.
6. A blank `ProjectionName` is rejected.
7. A `ProjectionName` longer than 100 characters is rejected.
8. Values not matching `^[a-z0-9][a-z0-9-]*$` are rejected.
9. Checkpoint, projection name, change, operation envelope, and checkpoint
   results render redacted/internal without raw values.

### `changesSince`

10. A nonexistent organization or organization with no changes returns an
    empty successful list and creates no row.
11. `NONE` returns all committed changes for the requested organization up to
    the limit.
12. A partial exclusive checkpoint returns only later changes.
13. A checkpoint at the organization's current maximum returns empty.
14. Limit zero, negative, and 1001 are rejected; 1 and 1000 are accepted.
15. Repeated bounded pagination is deterministic and has no repetition or
    omission for stable durable state.
16. Gaps in the physical sequence are accepted and do not imply corruption.
17. Every returned change contains the complete value-equal subject.
18. `FACT`, `ATTEMPT`, and `CORRECTION` journal rows map to the exact closed
    change kind.
19. The change contract and returned instances expose no economic payload.
20. A read never returns another organization's changes.
21. Repeated reads do not create or advance checkpoint state.
22. Changes written through the real P0.2 repository are visible without
    modifying that repository.
23. A concurrent real P0.2 writer and feed reader preserve strictly increasing
    returned sequence order within the requested organization.
24. Tests make no cross-organization/global commit-order assertion.

### Checkpoint

25. Missing `currentCheckpoint` for a known organization returns
    `Success(NONE)`.
26. `currentCheckpoint` for an unknown organization returns `Success(NONE)`
    and creates no organization or checkpoint row.
27. First advancement from missing/`NONE` to a sequence committed for the same
    organization returns `Advanced(next)` and creates exactly one durable row.
28. Missing state with expected other than `NONE` and a valid committed next
    returns `Stale(NONE)` and creates no row.
29. `next == expected` returns `Regression` before destination validation and
    performs no database write.
30. `next < expected` returns `Regression` before destination validation and
    performs no database write.
31. A `next > expected` value from committed history of the same organization
    is eligible to reach durable compare-and-set.
32. A `next` value committed only for another organization is rejected as a
    caller-contract violation before checkpoint mutation.
33. A future or invented `next` absent from the journal is rejected as a
    caller-contract violation before checkpoint mutation.
34. A physical sequence gap with no committed row for the requested
    organization cannot be stored as its checkpoint.
35. Infrastructure failure while validating organization or destination
    membership returns `IntegrityFailure` with zero checkpoint mutation.
36. Invalid relational `next` rejection leaks no SQL, SQLSTATE, constraint,
    organization, or sequence value.
37. Existing durable state different from expected returns `Stale(current)`
    without mutation.
38. Existing durable state equal to expected advances once and returns
    `Advanced(next)`.
39. Concurrent first writers using the same valid committed destination
    produce exactly one `Advanced` and one `Stale` after destination
    validation.
40. Concurrent existing-row writers using the same expected and valid
    committed destination produce exactly one `Advanced` and one `Stale`.
41. Two projection names in the same organization are independent.
42. Two organizations using the same projection name are independent.
43. Every read operation leaves durable checkpoint state unchanged.
44. `advanceCheckpoint` for an unknown organization with `next > expected`
    returns `IntegrityFailure` and creates no checkpoint or organization row.

### Consumer contract

45. Structural inspection proves that the port provides no acknowledgement
    token, batch token, or fifth operation; `advanceCheckpoint` validates only
    structural destination membership, while successful processing before
    advancement remains the consumer's responsibility.

### Pending discovery

46. An organization with journal changes and no checkpoint is pending.
47. An organization whose checkpoint is current is not pending.
48. Mixed pending and non-pending organizations return only the pending set.
49. Two projection names discover independently from their own checkpoints.
50. Discovery respects the accepted positive bound and rejects invalid limits.
51. Organization ordering is deterministic ascending and stable for durable
    state.
52. Discovery cannot return an organization absent from
    `integration_organization`.
53. Adapter-source/static SQL inspection proves the canonical Query B semantic
    shape.
54. A controlled representative-volume PostgreSQL/Testcontainers test executes
    canonical Query B, captures and records complete
    `EXPLAIN (ANALYZE, BUFFERS)`, and demonstrates the expected indexed
    `EXISTS` access path through the existing V015
    `(organization_id, change_sequence)` index for that fixture. It does not
    assert that planner nodes outside the fixture are public domain behavior.
55. Adapter-source/static SQL inspection proves no full-journal
    `GROUP BY organization_id`/`MAX(change_sequence)` implementation.
56. A bounded repeated poll documents starvation under fixed ordering without
    requiring or implementing fairness.

### Persistence and integrity

57. Flyway applies every existing migration through V015 and the future
    checkpoint migration successfully without assuming it is forever the last
    migration.
58. Direct checkpoint insertion for an unknown organization is rejected by the
    foreign key and maps fail-closed without SQL leakage.
59. The primary key permits only one row per organization/projection pair.
60. Database constraints reject invalid, blank, or overlength projection
    names even when SQL bypasses the domain constructor.
61. Database constraints reject negative `last_change_sequence`.
62. Insert and update both replace caller timestamp input with
    database-authoritative `transaction_timestamp()` at microsecond-capable
    precision.
63. Direct SQL checkpoint insertion using a nonexistent journal sequence is
    rejected by the composite foreign key.
64. Direct SQL checkpoint insertion using a sequence committed only for a
    different organization is rejected by the composite foreign key.
65. Direct SQL checkpoint insertion using a committed sequence from the same
    organization succeeds.
66. Database metadata or migration inspection proves that the composite
    checkpoint foreign key references
    `marketplace_economic_evidence_update(organization_id, change_sequence)`
    in that exact column order.
67. The referenced V015 unique key on
    `(organization_id, change_sequence)` remains byte-unchanged.
68. No V015 or new journal-index modification is introduced merely to support
    the checkpoint foreign key.
69. Migration and implementation diffs prove V015 and every earlier migration
    remain byte-unchanged.
70. Connection, query, mapping, transaction, and malformed-data failures map
    to the closed `IntegrityFailure` envelope.
71. Every failure surface and rendering contains no SQLSTATE, SQL text,
    constraint name, infrastructure exception, or sensitive domain value.
72. A new adapter/repository instance reads the same durable checkpoint after
    the writer instance is discarded.

### Additional structural regressions

73. Compiled port bytecode contains no forbidden infrastructure, provider,
    connector, API, UI, outbox, Ledger, reconciliation, numeric `Float` or
    `Double`, or direct Kernel reference.
74. Reflection proves the port exposes exactly the four specified methods and
    the P0.2 repository still exposes exactly `find` and `apply`.
75. The migration creates only the checkpoint table and its owned timestamp
    function/trigger and does not mutate an evidence-journal row.
76. Independent projection/checkpoint operations create no outbox row, event,
    materialized Sales Intelligence row, scheduler state, or provider call.

## Mandatory regression and quality gates

Before any future commit or push, the implementation TASK must run, in this
order:

1. focused change-feed application contract tests;
2. focused Postgres change-feed tests against real PostgreSQL/Testcontainers;
3. all `applications:marketplace-operations` tests;
4. all `applications:marketplace-operations-persistence-postgres` tests;
5. full applicable build only after the focused gates are green;
6. `git diff --check`;
7. mechanical forbidden-dependency and forbidden-reference scans;
8. exact closed-file-scope verification;
9. byte-unchanged verification for V015 and earlier migrations.

PR CI must pass without bypass. A failing relevant test, migration, build,
scope, dependency, privacy, or integrity gate blocks merge.

## Closed future implementation file scope

Repository reconnaissance found that both required application and Postgres
modules already have the dependencies needed by this boundary. No build-file
change is authorized.

A future implementation TASK may alter or create exactly these seven logical
files:

1. application contract — CREATE:
   `applications/marketplace-operations/src/main/kotlin/io/flooow/marketplace/operations/economics/evidence/MarketplaceEconomicEvidenceChangeFeed.kt`;
2. focused application contract test — CREATE:
   `applications/marketplace-operations/src/test/kotlin/io/flooow/marketplace/operations/economics/evidence/MarketplaceEconomicEvidenceChangeFeedTest.kt`;
3. next migration resolved from canonical `main` immediately before the TASK
   — CREATE exactly one:
   `applications/marketplace-operations-persistence-postgres/src/main/resources/db/migration/V<NEXT>__create_marketplace_economic_evidence_projection_checkpoint.sql`;
4. Postgres adapter — CREATE:
   `applications/marketplace-operations-persistence-postgres/src/main/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceEconomicEvidenceChangeFeed.kt`;
5. focused Postgres adapter test — CREATE:
   `applications/marketplace-operations-persistence-postgres/src/test/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt`;
6. evidence record — CREATE:
   `docs/evidence/TASK-<ASSIGNED>-durable-marketplace-economic-evidence-incremental-change-feed.md`;
7. executive journal — MODIFY by appending exactly one TASK entry:
   `docs/journal/MGI-EXECUTIVE-JOURNAL.md`.

`<NEXT>` and `<ASSIGNED>` are placeholders, not numbers allocated by this
draft. The future TASK must resolve them mechanically from canonical `main`.
The other five paths are exact.

No eighth file is authorized. If implementation discovers a required build,
existing production, existing migration, P0.2 repository, or other file
change, it must stop and return for ADR/SPEC review rather than widen scope.

## Explicitly out of scope

- P0.3 Slice B;
- a materialized Sales Intelligence projection or read model;
- an exact evidence timeline;
- historical aggregate reconstruction at a named version;
- materialization/checkpoint atomicity or idempotency;
- scheduler, leases, claims, worker ownership, or `SKIP LOCKED`;
- fairness cursor, round-robin, retry-after, failure counters, or last-served
  state;
- API or UI;
- Mercado Livre or Omie adapters, calls, credentials, or activation;
- provider or connector work;
- outbox schema, runtime, event, or delivery changes;
- `MarketplaceFinancialLedger` or any Financial Ledger change;
- reconciliation;
- Economic Truth materialization;
- modification of V015 or any existing migration;
- widening or changing `MarketplaceIndependentEconomicEvidenceRepository`;
- changing a P0.2 aggregate, update, read result, or persist result;
- Kernel change;
- production deployment or data backfill.

## References

- `docs/adr/ADR-0046-durable-marketplace-economic-evidence-incremental-change-feed.md`,
  Accepted at commit `efe0679`;
- `docs/vision/RFC-0007-DURABLE-EVIDENCE-INCREMENTAL-CHANGE-FEED.md`, v0.3;
- experimental commit `1898629`;
- `docs/evidence/EXP-0006-durable-evidence-incremental-change-feed.md`;
- `docs/evidence/EXP-0006-pending-discovery-follow-up.md`;
- `docs/specifications/SPEC-0044-durable-independent-marketplace-economic-evidence-change-sequence-scope.md`;
- `docs/adr/ADR-0045-revert-outbox-generalization-durable-change-sequence.md`.

The conversation is not normative authority.

## Acceptance

This specification is `Accepted`. Human review approved and froze the
`1..1000` limit for both bounded operations before TASK-0145 was authorized.

Acceptance authorizes only a separately numbered P0.3 Slice A TASK constrained
to the seven-file scope above. It does not authorize Slice B, API, UI,
provider work, outbox work, Ledger, reconciliation, Kernel changes, deployment,
or data backfill.
