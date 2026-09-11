# TASK-0145: Durable Marketplace Economic Evidence Incremental Change Feed — Slice A

Status: Implementation not started

Date: 2026-09-02

## Authority

This implementation task is governed by:

- SPEC-0045, Accepted at commit
  `67ac06a9d76cb0977256682ab06900aa44054de6`;
- ADR-0046, Accepted at commit `efe0679`;
- RFC-0007 v0.3;
- EXP-0006 at commit `1898629`;
- both committed EXP-0006 evidence records;
- SPEC-0044, ADR-0045, TASK-0144, and V015 as the existing P0.2
  foundation.

The accepted SPEC is normative. This TASK narrows execution to its closed
scope and does not reinterpret or widen it.

## Repository checkpoint

- base HEAD at task drafting:
  `67ac06a9d76cb0977256682ab06900aa44054de6`;
- assigned task: `TASK-0145`;
- highest prior task: `TASK-0144` at
  `docs/evidence/TASK-0144-durable-independent-marketplace-economic-evidence.md`;
- assigned migration: `V016`;
- highest prior migration: `V015__create_independent_marketplace_economic_evidence.sql`;
- V015 blob at task drafting:
  `b8fe5815232c908a47c525be41b75433fc3cb32f`;
- task-number duplicates: none;
- migration-number duplicates: none.

## Objective

Implement P0.3 Slice A exactly as accepted in SPEC-0045:

- a separate inward-facing marketplace economic-evidence change-feed port;
- durable organization/projection checkpoints;
- a PostgreSQL adapter for incremental reads and checkpoint compare-and-set;
- canonical Query B pending-organization discovery;
- structural checkpoint destination validation;
- a composite durable checkpoint-to-journal foreign key;
- fail-closed infrastructure and privacy boundaries;
- focused application and real PostgreSQL/Testcontainers tests;
- a mechanically complete evidence record;
- one append-only executive journal entry.

This task implements no Slice B behavior or materialized Sales Intelligence
projection.

## Closed implementation scope

Relative to the canonical base, TASK-0145 may create or modify exactly these
seven files:

1. CREATE
   `applications/marketplace-operations/src/main/kotlin/io/flooow/marketplace/operations/economics/evidence/MarketplaceEconomicEvidenceChangeFeed.kt`
2. CREATE
   `applications/marketplace-operations/src/test/kotlin/io/flooow/marketplace/operations/economics/evidence/MarketplaceEconomicEvidenceChangeFeedTest.kt`
3. CREATE
   `applications/marketplace-operations-persistence-postgres/src/main/resources/db/migration/V016__create_marketplace_economic_evidence_projection_checkpoint.sql`
4. CREATE
   `applications/marketplace-operations-persistence-postgres/src/main/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceEconomicEvidenceChangeFeed.kt`
5. CREATE
   `applications/marketplace-operations-persistence-postgres/src/test/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt`
6. CREATE
   `docs/evidence/TASK-0145-durable-marketplace-economic-evidence-incremental-change-feed.md`
7. MODIFY by appending exactly one TASK-0145 entry
   `docs/journal/MGI-EXECUTIVE-JOURNAL.md`

File 6 has one physical path and two lifecycle states:

- relative to base `67ac06a9d76cb0977256682ab06900aa44054de6`, it is
  `CREATE` and remains one of the seven TASK-owned files in the total TASK
  diff;
- after the documentation-only TASK commit, implementation treats this same
  path as `MODIFY` by appending execution evidence to the already-versioned
  TASK document.

This task document is the single TASK-0145 evidence record. No second
TASK/evidence file may be created, the seven TASK-owned paths remain unchanged,
and no eighth path is authorized.

No eighth file is authorized. Discovery that implementation requires a build
file, existing production file, V015, the P0.2 repository, another migration,
helper file, scheduler file, or any other path is a stop condition requiring
SPEC/TASK review.

## Application contract

The first authorized Kotlin file contains all and only the required new
application types:

- `ChangeSequenceCheckpoint`;
- `ProjectionName`;
- `MarketplaceEconomicEvidenceChange`;
- `MarketplaceEconomicEvidenceChangeKind`;
- `MarketplaceEconomicEvidenceChangeFeedResult`;
- `CheckpointAdvanceResult`;
- `MarketplaceEconomicEvidenceChangeFeed`.

No additional Kotlin file may split these types.

### Port

`MarketplaceEconomicEvidenceChangeFeed` is separate from
`MarketplaceIndependentEconomicEvidenceRepository` and exposes exactly:

```text
changesSince
organizationsWithPendingChanges
currentCheckpoint
advanceCheckpoint
```

No fifth method is permitted. The P0.2 repository remains unchanged and
continues to expose exactly `find` and `apply`.

### Closed result surface

The outer `MarketplaceEconomicEvidenceChangeFeedResult<T>` contains exactly:

```text
Success(value)
IntegrityFailure
```

`CheckpointAdvanceResult` contains exactly:

```text
Advanced(checkpoint)
Stale(currentCheckpoint)
Regression
```

There is no third envelope variant and no fourth CAS result.

## Value rules

### `ChangeSequenceCheckpoint`

- regular immutable class, not `@JvmInline`;
- one private `Long` whose value is greater than or equal to zero;
- `Comparable`;
- explicit value equality and `hashCode`;
- `NONE` and `ZERO` both represent zero and are value-equal;
- exactly one public raw accessor: `valueForPersistence()`;
- `toString()` is exactly `[INTERNAL]`;
- no generated public raw `Long` accessor, unbox surface, public field,
  component, copy, or alternate raw conversion.

### `ProjectionName`

- regular immutable class;
- length from 1 through 100 characters inclusive;
- matches exactly `^[a-z0-9][a-z0-9-]*$`;
- exactly one public raw accessor: `valueForPersistence()`;
- `toString()` is exactly `[INTERNAL]`.

### Limits

Both limits are frozen to:

```text
1..1000 inclusive
```

An invalid limit fails through a sanitized caller precondition before any
persistence access.

## Change and feed semantics

`MarketplaceEconomicEvidenceChange` contains exactly:

- complete `MarketplaceEconomicEvidenceSubject`;
- `MarketplaceEconomicEvidenceVersion`;
- `ChangeSequenceCheckpoint`;
- `MarketplaceEconomicEvidenceChangeKind`.

The closed change kinds are exactly `FACT`, `ATTEMPT`, and `CORRECTION`.
The change contains no economic, fact, attempt, correction, component, SQL,
or commit-timestamp payload.

The feed is an invalidation feed, not a historical snapshot, exact timeline,
or aggregate-at-version replay contract.

## `changesSince`

Each call reads exactly one organization using an exclusive checkpoint:

```text
change_sequence > checkpoint
ORDER BY change_sequence ASC
LIMIT validated-limit
```

The operation provides deterministic stable pagination for stable durable
state, accepts physical sequence gaps, performs no checkpoint mutation, and
makes no global cross-organization ordering claim.

An unknown organization returns `Success(emptyList())` and creates no row.

The adapter must perform one SQL read joining:

```text
marketplace_economic_evidence_update
+
marketplace_economic_evidence_subject
```

to reconstruct the complete subject. No second consumer subject lookup is
required.

## Checkpoint migration

V016 creates exactly:

```text
marketplace_economic_evidence_projection_checkpoint
```

with:

```text
organization_id UUID NOT NULL
projection_name TEXT NOT NULL
last_change_sequence BIGINT NOT NULL
updated_at TIMESTAMPTZ(6) NOT NULL
```

V016 must enforce:

- primary key `(organization_id, projection_name)`;
- foreign key `organization_id` to
  `integration_organization(organization_id)`;
- composite foreign key `(organization_id, last_change_sequence)` to
  `marketplace_economic_evidence_update(organization_id, change_sequence)`;
- the ProjectionName regular expression and maximum length;
- `last_change_sequence >= 0`;
- database-authoritative `updated_at` through a migration-owned
  `BEFORE INSERT OR UPDATE` trigger assigning `transaction_timestamp()`.

No persisted row represents `NONE`; absence of a row represents `NONE` and
`ZERO`. V015 must remain byte-unchanged. V016 must add no journal index.

Adapter relational destination validation and the composite database foreign
key are complementary. The adapter supplies caller-contract classification;
the FK protects durable state against direct SQL, bypass, bugs, and future
code paths.

## Checkpoint reads and advancement

`currentCheckpoint` returns:

- missing known organization: `Success(NONE)`;
- unknown organization: `Success(NONE)`;
- valid durable row: `Success(checkpoint)`;
- malformed, inconsistent, mapping, or infrastructure failure:
  `IntegrityFailure`.

It performs no mutation and need not redundantly read the journal when the
composite FK already guarantees structural membership.

`advanceCheckpoint` uses this exact conceptual order:

1. validate scalar/domain arguments;
2. when `next <= expected`, return `Success(Regression)` with zero write and
   no relational destination validation;
3. when `next > expected`, validate that the requested organization exists;
4. validate that the same organization owns a committed
   `change_sequence == next`;
5. when the organization exists but destination is invalid, raise a sanitized
   caller-contract violation with zero checkpoint mutation;
6. perform durable compare-and-set;
7. when durable equals expected, return `Success(Advanced(next))`;
8. when durable differs from expected, return
   `Success(Stale(currentCheckpoint))` without mutation.

An unknown organization with `next > expected` returns `IntegrityFailure`
and creates no row. A failure while performing relational validation is
`IntegrityFailure`, not invalid caller input. No SQL or infrastructure detail
may escape.

## Consumer checkpoint responsibility

Slice A proves only that `next` is a structurally valid committed destination
for the requested organization. It does not prove that the consumer processed
every earlier change.

A consumer must advance only after successful handling. The normal flow is:

```text
currentCheckpoint
-> changesSince
-> process returned changes
-> determine last successfully handled changeSequence
-> advanceCheckpoint(next = that sequence)
```

No acknowledgement token, batch token, fifth method, lease, scheduler state,
or Slice B transaction semantic may be added.

## Checkpoint concurrency

Real PostgreSQL/Testcontainers tests must prove:

### First row

Two concurrent writers using the same organization, projection, expected
`NONE`, and same valid committed `next` produce exactly:

```text
1 Success(Advanced)
1 Success(Stale)
```

### Existing row

Two concurrent writers using the same durable expected value and same valid
committed destination produce exactly:

```text
1 Success(Advanced)
1 Success(Stale)
```

No `SQLException`, SQLSTATE, constraint, or database detail may escape.
Bounded retry may be introduced inside the adapter only after a failing real
concurrency test proves it necessary, and only for appropriate retryable
PostgreSQL conditions. Retry logic must not be copied preemptively.

## Pending-organization discovery

The production adapter uses Query B with this semantic shape:

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

Query A using full-journal `GROUP BY`/`MAX` is forbidden. Query C is not the
production strategy.

The controlled representative-volume test must execute Query B, capture the
complete `EXPLAIN (ANALYZE, BUFFERS)`, demonstrate the expected indexed
`EXISTS` access path using the existing V015
`(organization_id, change_sequence)` key under that fixture, and preserve the
raw plan in this evidence file. Planner behavior outside that fixture is not
a public contract.

## Known starvation limitation

```text
ORDER BY organization_id ASC
LIMIT N
```

may starve later organizations while earlier organizations remain pending.
TASK-0145 must test and document that limitation and must not fix it.

The implementation must add no lease, claim, scheduler, `SKIP LOCKED`,
round-robin, fairness cursor, retry-after, failure counter, or last-served
state.

## Fail-closed and privacy boundary

The inward-facing surface must never expose:

- `SQLException` or driver exception;
- SQLSTATE;
- constraint, table, schema, column, trigger, or function names;
- SQL text or bound database values;
- organization, order, projection, sequence, or other sensitive values.

Mapping, malformed durable state, transaction, database, and unexpected
adapter failures return `IntegrityFailure`. Invalid caller preconditions use
sanitized programmer-contract violations.

## Mandatory 76-case requirement map

Every accepted SPEC-0045 requirement is mandatory. The planned test method
names below may be refined only without losing one-to-one traceability. No
requirement may become `N/A` without returning for SPEC review.

| Requirement | Test method | Test file | Evidence/status |
|---:|---|---|---|
| 1 | `checkpoint rejects negative value` | `MarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 2 | `checkpoint NONE and ZERO represent equal zero` | `MarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 3 | `checkpoint ordering equality and hash are deterministic` | `MarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 4 | `checkpoint JVM surface exposes only persistence raw accessor` | `MarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 5 | `projection name accepts valid value and is value equal` | `MarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 6 | `projection name rejects blank` | `MarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 7 | `projection name rejects more than one hundred characters` | `MarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 8 | `projection name rejects values outside canonical regex` | `MarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 9 | `all change feed contract types render redacted or internal` | `MarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 10 | `unknown organization and known organization without changes return empty without writes` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 11 | `NONE returns all bounded committed changes for organization` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 12 | `partial exclusive checkpoint returns only later changes` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 13 | `checkpoint at current maximum returns empty` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 14 | `feed limits reject zero negative and over maximum and accept boundaries` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 15 | `bounded pagination is deterministic without repetition or omission` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 16 | `physical sequence gaps are accepted` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 17 | `change includes complete value equal subject` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 18 | `journal kinds map exactly to closed change kinds` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 19 | `change contract exposes no economic payload` | `MarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 20 | `changesSince isolates organizations` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 21 | `repeated feed reads never create or advance checkpoints` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 22 | `real P0_2 writer changes are visible through feed` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 23 | `concurrent P0_2 writer and feed reader preserve increasing per organization order` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 24 | `change feed contract makes no global ordering assertion` | `MarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 25 | `missing checkpoint for known organization returns NONE` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 26 | `checkpoint read for unknown organization returns NONE without writes` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 27 | `first advance to same organization committed sequence creates one row` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 28 | `missing row with non NONE expected returns stale NONE without insert` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 29 | `equal next is regression before relational validation` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 30 | `lower next is regression before relational validation` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 31 | `same organization committed next is eligible for CAS` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 32 | `next committed only for another organization is rejected before mutation` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 33 | `future or invented next is rejected before mutation` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 34 | `physical gap cannot be stored as checkpoint` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 35 | `failure during destination validation returns integrity failure without mutation` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 36 | `invalid relational next leaks no infrastructure or input detail` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 37 | `different durable value returns stale current without mutation` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 38 | `matching durable expected advances exactly once` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 39 | `concurrent first checkpoint writers yield one advanced and one stale` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 40 | `concurrent existing checkpoint writers yield one advanced and one stale` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 41 | `checkpoint state is independent by projection name` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 42 | `checkpoint state is independent by organization` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 43 | `all read operations leave checkpoint state unchanged` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 44 | `advance for unknown organization returns integrity failure without rows` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 45 | `port has no acknowledgement token batch token or fifth operation` | `MarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 46 | `organization without checkpoint and with changes is pending` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 47 | `organization with current checkpoint is not pending` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 48 | `mixed discovery returns only pending organizations` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 49 | `pending discovery is independent by projection name` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 50 | `pending discovery validates and enforces bounded limit` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 51 | `pending organizations are deterministically ordered ascending` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 52 | `pending discovery cannot return nonexistent organization` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 53 | `adapter SQL has canonical Query B semantic shape` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 54 | `controlled Query B explain records expected indexed EXISTS path` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 55 | `adapter contains no full journal group by max discovery` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 56 | `bounded fixed ordering documents starvation without fairness` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 57 | `Flyway applies through V016 without last migration assumption` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 58 | `direct checkpoint insert for unknown organization violates FK` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 59 | `checkpoint primary key permits one organization projection row` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 60 | `database rejects invalid blank and overlength projection names` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 61 | `database rejects negative checkpoint sequence` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 62 | `database authoritatively stamps insert and update time` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 63 | `composite FK rejects nonexistent journal sequence` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 64 | `composite FK rejects sequence owned by another organization` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 65 | `composite FK accepts committed same organization sequence` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 66 | `database metadata proves exact composite journal foreign key` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 67 | `referenced V015 organization sequence key is byte unchanged` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 68 | `V016 introduces no V015 or journal index modification` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 69 | `all migrations through V015 remain byte unchanged` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 70 | `connection query mapping transaction and malformed failures are fail closed` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 71 | `failure surfaces leak no SQLSTATE SQL or sensitive value` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 72 | `checkpoint survives adapter reconstruction` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 73 | `compiled contract contains no forbidden dependency or numeric type` | `MarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 74 | `change feed has four methods and P0_2 repository remains find apply` | `MarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 75 | `V016 creates only checkpoint table and owned timestamp function trigger` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |
| 76 | `checkpoint operations create no outbox projection scheduler or provider side effect` | `PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt` | PENDING |

## Real PostgreSQL and migration gates

Persistence behavior must be tested against real PostgreSQL via Testcontainers
with Flyway applying V001 through V016. H2, fake JDBC, mocked transactions, or
disabled Testcontainers are not accepted substitutes.

Migration tests must prove:

- unknown organization FK rejection;
- organization/projection primary-key uniqueness;
- projection-name database constraint;
- non-negative checkpoint constraint;
- database-authoritative timestamp;
- nonexistent journal sequence composite-FK rejection;
- other-organization sequence composite-FK rejection;
- same-organization committed sequence acceptance;
- exact composite-FK metadata;
- byte-unchanged V015;
- no new journal index.

## Architectural scans

Mechanical source and bytecode inspection must prove that the application
contract contains no reference to:

- `java.sql` or `javax.sql`;
- JDBC, PostgreSQL, or Flyway;
- JSON or serialization frameworks;
- API or UI;
- provider or connector;
- outbox or delivery runtime;
- `MarketplaceFinancialLedger`;
- reconciliation;
- `Float` or `Double`;
- Kernel.

Reflection and bytecode inspection must additionally prove:

- the change-feed port exposes exactly four methods;
- the P0.2 repository still exposes exactly `find` and `apply`;
- `ChangeSequenceCheckpoint` exposes exactly the accepted raw `Long` surface.

## Mandatory execution order

### Gate A — application contract

Create the contract and focused contract test only. Run the focused
`MarketplaceEconomicEvidenceChangeFeedTest` before continuing.

### Gate B — persistence

Create V016, the Postgres adapter, and its focused test. Run
`PostgresMarketplaceEconomicEvidenceChangeFeedTest` against real
PostgreSQL/Testcontainers. Diagnose failures before correction and do not add
preemptive retry.

### Gate C — application regression

Run every `applications:marketplace-operations` test.

### Gate D — persistence regression

Run every `applications:marketplace-operations-persistence-postgres` test.

### Gate E — full build

Run the full applicable build only after focused and module gates are green.

### Gate F — mechanical closure

Run:

- `git diff --check`;
- exact seven-file scope verification;
- forbidden dependency and reference scans;
- privacy and rendering scans;
- migration-number and migration-integrity checks;
- V015 byte-unchanged verification;
- no-new-journal-index verification;
- the complete 76-case mapping audit.

## Evidence requirements

Preserve all normative and task-plan content above. During implementation, do
not rewrite the task plan to insert runtime narrative throughout it. Append one
`## Execution Evidence` section to this same TASK document.

Within `## Execution Evidence`, primary measured outputs must appear before
narrative interpretation. They do not appear before or in place of the approved
TASK definition. The appended section must record at least:

- base commit;
- TASK number;
- migration number;
- exact seven-file scope;
- final Git diff/stat and names;
- focused application test totals and output;
- focused Postgres test totals and output;
- all marketplace-operations test totals and output;
- all persistence-postgres test totals and output;
- full build result;
- actual PostgreSQL/Testcontainers versions;
- Flyway V001–V016 result;
- both checkpoint concurrency outcomes;
- complete raw Query B `EXPLAIN (ANALYZE, BUFFERS)`;
- all 76 requirements with test/evidence/status;
- privacy and fail-closed checks;
- forbidden-dependency scans;
- V015 unchanged proof;
- absence of a new journal index;
- final `git status`;
- commit and push state.

Narrative-only evidence is insufficient.

## Executive journal update

Append exactly one TASK-0145 entry to
`docs/journal/MGI-EXECUTIVE-JOURNAL.md`. Do not rewrite historical entries.
The entry records task identity, purpose, scope, measured evidence, and final
status. Before the implementation commit, its commit field is `pending`.

## Stop conditions

Stop implementation and return for review if any of these occurs:

- an eighth file is required;
- V015 must change;
- the P0.2 repository must change;
- a build file must change;
- another migration or helper file appears necessary;
- an architecture conflict with SPEC-0045 appears;
- the checkpoint FK requires another journal index;
- a fifth port method appears necessary;
- fairness, leasing, claiming, or scheduler state appears necessary;
- any of the 76 cases cannot be satisfied within the seven-file scope;
- SQL would weaken existing integrity.

Do not silently widen scope.

## Explicitly excluded

- P0.3 Slice B;
- materialized Sales Intelligence;
- exact evidence timeline or historical aggregate-at-version reconstruction;
- scheduler, lease, claim, ownership, fairness, or `SKIP LOCKED`;
- API or UI;
- Mercado Livre or Omie work;
- provider or connector activation;
- outbox changes;
- Financial Ledger or reconciliation;
- V015 or existing migration changes;
- P0.2 repository widening;
- Kernel changes;
- deployment or data backfill.

## Commit and push policy

This draft authorizes no implementation commit. During a future authorized
execution, no commit may occur until all gates pass. No push may occur without
separate explicit authorization.

Before the implementation commit:

- TASK execution-evidence commit state is `pending`;
- executive-journal commit field is `pending`;
- push state is `not performed`.

A Git commit cannot contain its own final hash as content because changing that
content changes the hash. After an authorized implementation commit exists,
report its actual hash in the agent's post-commit review output. Do not modify
the TASK evidence or executive journal merely to backfill that same hash, and
do not create a second documentation commit unless separately authorized. If a
later repository convention explicitly requires hash backfill, it is a
separately authorized follow-up documentation action. TASK-0145 has no
self-referential commit-hash requirement.

## Completion condition

TASK-0145 implementation gates are complete before the eventual implementation
commit only when:

- all 76 accepted requirements pass with measured evidence;
- all focused, module, full-build, and mechanical gates pass;
- the worktree and total TASK diff contain exactly the seven authorized
  TASK-owned files;
- V015 remains byte-unchanged and no new journal index exists;
- this document contains complete `## Execution Evidence` with commit state
  `pending` and push state `not performed`;
- the executive-journal entry is complete with commit `pending`.

Only then may human review authorize the implementation commit. The post-commit
hash is an external verification result and does not require mutation of the
committed evidence or executive journal.

## Execution Evidence

### Primary measured outputs

#### Gate A — focused application contract

    MarketplaceEconomicEvidenceChangeFeedTest
    TOTAL   16
    PASSED  16
    FAILED  0
    SKIPPED 0

#### Gate B — focused PostgreSQL

    PostgresMarketplaceEconomicEvidenceChangeFeedTest
    TOTAL   22
    PASSED  22
    FAILED  0
    SKIPPED 0

Real PostgreSQL/Testcontainers execution:

    PostgreSQL image: postgres:18.4
    Testcontainers: 2.0.5
    Flyway: 13.2.0
    Flyway result: Successfully validated and applied V001-V016

#### Gate C — complete application regression

    applications:marketplace-operations
    TOTAL   254
    PASSED  254
    FAILED  0
    SKIPPED 0

#### Gate D — complete PostgreSQL persistence regression

    applications:marketplace-operations-persistence-postgres
    XML SUITES 9
    TOTAL      86
    PASSED     86
    FAILED     0
    SKIPPED    0

    PostgresMarketplaceIndependentEconomicEvidenceRepositoryTest
    15/15

    PostgresMarketplaceEconomicEvidenceChangeFeedTest
    22/22

#### Gate E — full applicable build

Exact command:

    $env:GRADLE_USER_HOME = 'C:\Users\xmz_r\.gradle'
    .\gradlew.bat build --no-daemon

Measured result:

    BUILD SUCCESSFUL in 54s
    85 actionable tasks: 29 executed, 56 up-to-date
    Configuration cache entry stored.
    test bypass = none
    failed Gradle tasks = 0

#### Concurrency

    PASS — concurrent first checkpoint writers yield one Advanced and one Stale
    PASS — concurrent existing checkpoint writers yield one Advanced and one Stale
    PASS — concurrent P0.2 writer and feed reader preserve increasing per-organization order
    retry added = no
    automatic rerun to obtain green = no

#### Controlled Query B fixture

    organizations = 80
    updates per organization = 40
    journal rows = 3200
    projection name = explain-projection
    limit = 1000
    PostgreSQL = 18.4

Complete raw EXPLAIN (ANALYZE, BUFFERS):

    TASK-0145 QUERY B EXPLAIN BEGIN
    Limit  (cost=68.84..68.88 rows=19 width=16) (actual time=0.387..0.395 rows=80.00 loops=1)
      Buffers: shared hit=192
      ->  Sort  (cost=68.84..68.88 rows=19 width=16) (actual time=0.385..0.389 rows=80.00 loops=1)
            Sort Key: o.organization_id
            Sort Method: quicksort  Memory: 25kB
            Buffers: shared hit=192
            ->  Nested Loop Semi Join  (cost=13.34..68.43 rows=19 width=16) (actual time=0.086..0.355 rows=80.00 loops=1)
                  Buffers: shared hit=189
                  ->  Hash Right Join  (cost=13.06..21.53 rows=58 width=24) (actual time=0.065..0.074 rows=80.00 loops=1)
                        Hash Cond: (c.organization_id = o.organization_id)
                        Buffers: shared hit=5
                        ->  Bitmap Heap Scan on marketplace_economic_evidence_projection_checkpoint c  (cost=10.75..19.21 rows=4 width=24) (actual time=0.039..0.039 rows=0.00 loops=1)
                              Recheck Cond: (projection_name = 'explain-projection'::text)
                              Buffers: shared hit=4
                              ->  Bitmap Index Scan on marketplace_economic_evidence_projection_checkpoint_pkey  (cost=0.00..10.75 rows=4 width=0) (actual time=0.035..0.035 rows=0.00 loops=1)
                                    Index Cond: (projection_name = 'explain-projection'::text)
                                    Index Searches: 1
                                    Buffers: shared hit=4
                        ->  Hash  (cost=1.58..1.58 rows=58 width=16) (actual time=0.018..0.018 rows=80.00 loops=1)
                              Buckets: 1024  Batches: 1  Memory Usage: 12kB
                              Buffers: shared hit=1
                              ->  Seq Scan on integration_organization o  (cost=0.00..1.58 rows=58 width=16) (actual time=0.005..0.009 rows=80.00 loops=1)
                                    Buffers: shared hit=1
                  ->  Index Only Scan using marketplace_economic_evidence_organization_id_change_sequen_key on marketplace_economic_evidence_update u  (cost=0.28..11.01 rows=18 width=24) (actual time=0.003..0.003 rows=1.00 loops=80)
                        Index Cond: ((organization_id = o.organization_id) AND (change_sequence > COALESCE(c.last_change_sequence, '0'::bigint)))
                        Heap Fetches: 23
                        Index Searches: 80
                        Buffers: shared hit=184
    Planning:
      Buffers: shared hit=281 read=2
    Planning Time: 0.696 ms
    Execution Time: 0.441 ms
    TASK-0145 QUERY B EXPLAIN END

### Authority and execution bases

    Normative SPEC / task-drafting base:
    67ac06a9d76cb0977256682ab06900aa44054de6

    TASK authority commit:
    ef2d4260c6101c24760f5de50ff9b3b8a3e36a5e

    Current execution base:
    1f0a4472736ace3cf15ae81018834ff8b338f892

Independent maintenance commits:

    b61a97915392e7ff3e60623e7af8e9ef53028e6a
    test: relax extension-fragile V015 migration assertions

    1f0a4472736ace3cf15ae81018834ff8b338f892
    docs: align SPEC-0045 acceptance status

These maintenance changes are recorded separately and are excluded from TASK
ownership. No claim is made that the complete repository diff from
67ac06a9d76cb0977256682ab06900aa44054de6 contains only seven paths.

### TASK-owned change set

    TASK = TASK-0145
    migration = V016
    TASK-0145 owned change set = exactly 7 paths
    independent maintenance changes = excluded from TASK ownership and recorded separately

The seven TASK-owned paths are:

1. applications/marketplace-operations/src/main/kotlin/io/flooow/marketplace/operations/economics/evidence/MarketplaceEconomicEvidenceChangeFeed.kt
2. applications/marketplace-operations/src/test/kotlin/io/flooow/marketplace/operations/economics/evidence/MarketplaceEconomicEvidenceChangeFeedTest.kt
3. applications/marketplace-operations-persistence-postgres/src/main/resources/db/migration/V016__create_marketplace_economic_evidence_projection_checkpoint.sql
4. applications/marketplace-operations-persistence-postgres/src/main/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceEconomicEvidenceChangeFeed.kt
5. applications/marketplace-operations-persistence-postgres/src/test/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceEconomicEvidenceChangeFeedTest.kt
6. docs/evidence/TASK-0145-durable-marketplace-economic-evidence-incremental-change-feed.md
7. docs/journal/MGI-EXECUTIVE-JOURNAL.md

### Migration and immutability evidence

Mechanical enumeration:

    highest prior migration = V015__create_independent_marketplace_economic_evidence.sql
    TASK migration = V016__create_marketplace_economic_evidence_projection_checkpoint.sql
    V016 duplicate count = 1
    competing V016 = none

V016 creates only:

    marketplace_economic_evidence_projection_checkpoint table
    stamp_marketplace_economic_evidence_projection_checkpoint function
    stamp_marketplace_economic_evidence_projection_checkpoint_before_write trigger

It creates no unrelated table, performs no journal mutation, makes no outbox
change, and adds no second migration.

V015 proof:

    accepted Git blob = b8fe5815232c908a47c525be41b75433fc3cb32f
    HEAD Git blob = b8fe5815232c908a47c525be41b75433fc3cb32f
    worktree Git blob = b8fe5815232c908a47c525be41b75433fc3cb32f
    SHA-256 = 671bd0625241e0682d749818a4bbcd56c64e059fa8fb34defa3b605b12f910c2
    diff from normative base = empty
    V015 unchanged = PASS

No-new-journal-index source scan:

    searched V016 and TASK production adapter for:
    CREATE INDEX
    CREATE UNIQUE INDEX
    ALTER TABLE marketplace_economic_evidence_update
    result = NO MATCH

Real-PostgreSQL index assertion passed with exactly:

    marketplace_economic_evidence_update_pkey
    marketplace_economic_evidence_organization_id_marketplace__key1
    marketplace_economic_evidence_organization_id_change_sequen_key

### Architecture, privacy, and fail-closed evidence

Application contract forbidden-reference scan searched for:

    java.sql, javax.sql, jdbc, postgres, flyway, json, serialization,
    api, ui, provider, connector, outbox, MarketplaceFinancialLedger,
    reconciliation, Float, Double, Kernel

Result:

    NO MATCH

Production contract, PostgreSQL adapter, and V016 forbidden-reference scan
searched for:

    outbox, scheduler, lease, claim, SKIP LOCKED, fairness cursor,
    round-robin, provider call, connector call, MarketplaceFinancialLedger,
    reconciliation, materialized Sales Intelligence

Result:

    NO MATCH

The compiled/reflection contract tests passed and proved:

    MarketplaceEconomicEvidenceChangeFeed port methods = exactly 4
    methods = changesSince, organizationsWithPendingChanges,
              currentCheckpoint, advanceCheckpoint
    MarketplaceIndependentEconomicEvidenceRepository methods = exactly find, apply
    IntegrityFailure rendering = [REDACTED]
    internal value rendering = [INTERNAL]

Measured fail-closed fixtures:

    PASS — connection/infrastructure failure
    PASS — destination validation query failure
    PASS — malformed mapping failure
    PASS — deferred COMMIT failure
    PASS — invalid relational destination

Public/rendered surfaces exposed no SQL, SQLSTATE, JDBC URL, constraint name,
table/schema name, organization ID, projection name, sequence value, economic
value, credential, or configuration detail.

### Complete SPEC-0045 requirement audit

| ID | Accepted requirement | Exact test method / mechanical evidence | Evidence type | Status |
|---:|---|---|---|---|
| 1 | checkpoint rejects negative value | checkpoint rejects negative value | application focused test | PASS |
| 2 | checkpoint NONE and ZERO represent equal zero | checkpoint NONE and ZERO represent equal zero | application focused test | PASS |
| 3 | checkpoint ordering equality and hash are deterministic | checkpoint ordering equality and hash are deterministic | application focused test | PASS |
| 4 | checkpoint JVM surface exposes only persistence raw accessor | checkpoint JVM surface exposes only persistence raw accessor | bytecode/reflection test | PASS |
| 5 | projection name accepts valid value and is value equal | projection name accepts valid value and is value equal | application focused test | PASS |
| 6 | projection name rejects blank | projection name rejects blank | application focused test | PASS |
| 7 | projection name rejects more than one hundred characters | projection name rejects more than one hundred characters | application focused test | PASS |
| 8 | projection name rejects values outside canonical regex | projection name rejects values outside canonical regex | application focused test | PASS |
| 9 | all change feed contract types render redacted or internal | all change feed contract types render redacted or internal | application focused test | PASS |
| 10 | unknown organization and known organization without changes return empty without writes | unknown organization and known organization without changes return empty without writes | PostgreSQL/Testcontainers test | PASS |
| 11 | NONE returns all bounded committed changes for organization | exclusive bounded feed reconstructs subject deterministically and accepts physical gaps | PostgreSQL/Testcontainers test | PASS |
| 12 | partial exclusive checkpoint returns only later changes | exclusive bounded feed reconstructs subject deterministically and accepts physical gaps | PostgreSQL/Testcontainers test | PASS |
| 13 | checkpoint at current maximum returns empty | exclusive bounded feed reconstructs subject deterministically and accepts physical gaps | PostgreSQL/Testcontainers test | PASS |
| 14 | feed limits reject zero negative and over maximum and accept boundaries | feed limits reject invalid values before persistence and accept both boundaries; feed limit contract accepts one and one thousand and rejects all outside values | PostgreSQL/Testcontainers and application focused tests | PASS |
| 15 | bounded pagination is deterministic without repetition or omission | exclusive bounded feed reconstructs subject deterministically and accepts physical gaps | PostgreSQL/Testcontainers test | PASS |
| 16 | physical sequence gaps are accepted | exclusive bounded feed reconstructs subject deterministically and accepts physical gaps | PostgreSQL/Testcontainers test | PASS |
| 17 | change includes complete value equal subject | exclusive bounded feed reconstructs subject deterministically and accepts physical gaps | PostgreSQL/Testcontainers test | PASS |
| 18 | journal kinds map exactly to closed change kinds | journal kinds map exactly to closed change kinds | PostgreSQL/Testcontainers test | PASS |
| 19 | change contract exposes no economic payload | change contract exposes exactly four fields and no economic payload | application focused test | PASS |
| 20 | changesSince isolates organizations | exclusive bounded feed reconstructs subject deterministically and accepts physical gaps | PostgreSQL/Testcontainers test | PASS |
| 21 | repeated feed reads never create or advance checkpoints | checkpoint state is isolated by organization and projection and reads do not mutate | PostgreSQL/Testcontainers test | PASS |
| 22 | real P0.2 writer changes are visible through feed | real P0_2 writer changes are visible through feed | PostgreSQL/Testcontainers test | PASS |
| 23 | concurrent P0.2 writer and feed reader preserve increasing per organization order | concurrent P0_2 writer and feed reader preserve increasing per organization order | PostgreSQL/Testcontainers concurrency test | PASS |
| 24 | change feed contract makes no global ordering assertion | port exposes exactly four organization scoped methods and no global ordering assertion | application reflection test | PASS |
| 25 | missing checkpoint for known organization returns NONE | current checkpoint handles missing known unknown durable and reconstructed adapter states | PostgreSQL/Testcontainers test | PASS |
| 26 | checkpoint read for unknown organization returns NONE without writes | current checkpoint handles missing known unknown durable and reconstructed adapter states | PostgreSQL/Testcontainers test | PASS |
| 27 | first advance to same organization committed sequence creates one row | advance validates order regression stale valid and relational destination semantics | PostgreSQL/Testcontainers test | PASS |
| 28 | missing row with non-NONE expected returns stale NONE without insert | advance validates order regression stale valid and relational destination semantics | PostgreSQL/Testcontainers test | PASS |
| 29 | equal next is regression before relational validation | advance validates order regression stale valid and relational destination semantics uses unreachable database with next equal to expected and returns Regression | PostgreSQL/Testcontainers boundary test | PASS |
| 30 | lower next is regression before relational validation | advance validates order regression stale valid and relational destination semantics uses unreachable database with next lower than expected and returns Regression | PostgreSQL/Testcontainers boundary test | PASS |
| 31 | same organization committed next is eligible for CAS | advance validates order regression stale valid and relational destination semantics | PostgreSQL/Testcontainers test | PASS |
| 32 | next committed only for another organization is rejected before mutation | advance validates order regression stale valid and relational destination semantics | PostgreSQL/Testcontainers test | PASS |
| 33 | future or invented next is rejected before mutation | advance validates order regression stale valid and relational destination semantics | PostgreSQL/Testcontainers test | PASS |
| 34 | physical gap cannot be stored as checkpoint | advance validates order regression stale valid and relational destination semantics | PostgreSQL/Testcontainers test | PASS |
| 35 | failure during destination validation returns integrity failure without mutation | destination validation query failure is fail closed and rolls back without checkpoint mutation | PostgreSQL/Testcontainers failure fixture | PASS |
| 36 | invalid relational next leaks no infrastructure or input detail | advance validates order regression stale valid and relational destination semantics; sanitized Checkpoint destination is invalid assertion | PostgreSQL/Testcontainers privacy test | PASS |
| 37 | different durable value returns stale current without mutation | advance validates order regression stale valid and relational destination semantics | PostgreSQL/Testcontainers test | PASS |
| 38 | matching durable expected advances exactly once | advance validates order regression stale valid and relational destination semantics | PostgreSQL/Testcontainers test | PASS |
| 39 | concurrent first checkpoint writers yield one advanced and one stale | concurrent first checkpoint writers yield one advanced and one stale | PostgreSQL/Testcontainers concurrency test | PASS |
| 40 | concurrent existing checkpoint writers yield one advanced and one stale | concurrent existing checkpoint writers yield one advanced and one stale | PostgreSQL/Testcontainers concurrency test | PASS |
| 41 | checkpoint state is independent by projection name | checkpoint state is isolated by organization and projection and reads do not mutate | PostgreSQL/Testcontainers isolation test | PASS |
| 42 | checkpoint state is independent by organization | checkpoint state is isolated by organization and projection and reads do not mutate | PostgreSQL/Testcontainers isolation test | PASS |
| 43 | all read operations leave checkpoint state unchanged | checkpoint state is isolated by organization and projection and reads do not mutate | PostgreSQL/Testcontainers test | PASS |
| 44 | advance for unknown organization returns integrity failure without rows | advance for unknown organization and infrastructure validation failure fail closed without rows | PostgreSQL/Testcontainers failure test | PASS |
| 45 | port has no acknowledgement token batch token or fifth operation | port has no acknowledgement token batch token or fifth operation | application reflection test | PASS |
| 46 | organization without checkpoint and with changes is pending | pending discovery is projection isolated mixed bounded deterministic and cannot invent organizations | PostgreSQL/Testcontainers test | PASS |
| 47 | organization with current checkpoint is not pending | pending discovery is projection isolated mixed bounded deterministic and cannot invent organizations | PostgreSQL/Testcontainers test | PASS |
| 48 | mixed discovery returns only pending organizations | pending discovery is projection isolated mixed bounded deterministic and cannot invent organizations | PostgreSQL/Testcontainers test | PASS |
| 49 | pending discovery is independent by projection name | pending discovery is projection isolated mixed bounded deterministic and cannot invent organizations | PostgreSQL/Testcontainers test | PASS |
| 50 | pending discovery validates and enforces bounded limit | feed limits reject invalid values before persistence and accept both boundaries; pending discovery is projection isolated mixed bounded deterministic and cannot invent organizations | PostgreSQL/Testcontainers tests | PASS |
| 51 | pending organizations are deterministically ordered ascending | pending discovery is projection isolated mixed bounded deterministic and cannot invent organizations | PostgreSQL/Testcontainers test | PASS |
| 52 | pending discovery cannot return nonexistent organization | pending discovery is projection isolated mixed bounded deterministic and cannot invent organizations | PostgreSQL/Testcontainers test | PASS |
| 53 | adapter SQL has canonical Query B semantic shape | adapter SQL has canonical Query B indexed EXISTS shape without forbidden alternatives | source/static SQL inspection test | PASS |
| 54 | controlled Query B explain records expected indexed EXISTS path | controlled Query B explain records expected indexed EXISTS path; complete raw plan recorded above | raw EXPLAIN and PostgreSQL/Testcontainers test | PASS |
| 55 | adapter contains no full journal group-by/max discovery | adapter SQL has canonical Query B indexed EXISTS shape without forbidden alternatives | source/static SQL inspection test | PASS |
| 56 | bounded fixed ordering documents starvation without fairness | bounded fixed ordering demonstrates known starvation without fairness state | PostgreSQL/Testcontainers test | PASS |
| 57 | Flyway applies through V016 without last-migration assumption | V016 migration enforces identity checks foreign keys timestamp and exact metadata; Flyway XML reports 16 migrations applied through v016 | Flyway/PostgreSQL test | PASS |
| 58 | direct checkpoint insert for unknown organization violates FK | V016 migration enforces identity checks foreign keys timestamp and exact metadata; advance for unknown organization and infrastructure validation failure fail closed without rows | migration and fail-closed tests | PASS |
| 59 | checkpoint primary key permits one organization/projection row | V016 migration enforces identity checks foreign keys timestamp and exact metadata | PostgreSQL constraint test | PASS |
| 60 | database rejects invalid blank and overlength projection names | V016 migration enforces identity checks foreign keys timestamp and exact metadata | PostgreSQL constraint test | PASS |
| 61 | database rejects negative checkpoint sequence | V016 migration enforces identity checks foreign keys timestamp and exact metadata | PostgreSQL constraint test | PASS |
| 62 | database authoritatively stamps insert and update time | V016 migration enforces identity checks foreign keys timestamp and exact metadata | PostgreSQL trigger test | PASS |
| 63 | composite FK rejects nonexistent journal sequence | V016 migration enforces identity checks foreign keys timestamp and exact metadata | PostgreSQL FK test | PASS |
| 64 | composite FK rejects sequence owned by another organization | V016 migration enforces identity checks foreign keys timestamp and exact metadata | PostgreSQL FK test | PASS |
| 65 | composite FK accepts committed same-organization sequence | V016 migration enforces identity checks foreign keys timestamp and exact metadata | PostgreSQL FK test | PASS |
| 66 | database metadata proves exact composite journal foreign key | V016 migration enforces identity checks foreign keys timestamp and exact metadata | PostgreSQL metadata test | PASS |
| 67 | referenced V015 organization/sequence key is byte unchanged | V015 and all prior migrations are byte unchanged and V016 adds no journal index; HEAD/worktree Git blob b8fe5815232c908a47c525be41b75433fc3cb32f and SHA-256 671bd0625241e0682d749818a4bbcd56c64e059fa8fb34defa3b605b12f910c2 | PostgreSQL index assertion and Git blob/hash | PASS |
| 68 | V016 introduces no V015 or journal-index modification | V015 and all prior migrations are byte unchanged and V016 adds no journal index; Gate F source scan returned NO MATCH | migration/static source inspection | PASS |
| 69 | all migrations through V015 remain byte unchanged | V015 and all prior migrations are byte unchanged and V016 adds no journal index; expected migration SHA-256 map | migration hash regression and Git diff | PASS |
| 70 | connection query mapping transaction and malformed failures are fail closed | connection query mapping transaction and malformed failures are fail closed without leakage; destination validation query failure is fail closed and rolls back without checkpoint mutation; deferred commit failure is fail closed and rolls back checkpoint write | PostgreSQL/Testcontainers failure fixtures | PASS |
| 71 | failure surfaces leak no SQLSTATE, SQL, or sensitive value | connection query mapping transaction and malformed failures are fail closed without leakage; destination-validation and deferred-COMMIT sanitized assertions | PostgreSQL/Testcontainers privacy tests | PASS |
| 72 | checkpoint survives adapter reconstruction | current checkpoint handles missing known unknown durable and reconstructed adapter states | PostgreSQL/Testcontainers durability test | PASS |
| 73 | compiled contract contains no forbidden dependency or numeric type | compiled contract contains no forbidden dependency or numeric type; Gate F source scan returned NO MATCH | bytecode/reflection and source inspection | PASS |
| 74 | change feed has four methods and P0.2 repository remains find/apply | port exposes exactly four organization scoped methods and no global ordering assertion; P0_2 repository still exposes exactly find and apply | reflection tests and source inspection | PASS |
| 75 | V016 creates only checkpoint table and owned timestamp function/trigger | V015 and all prior migrations are byte unchanged and V016 adds no journal index; V016 static object scan found one table, one function, and one trigger | migration metadata/static inspection | PASS |
| 76 | checkpoint operations create no outbox, projection scheduler, or provider side effect | checkpoint operations create no outbox projection scheduler or provider side effect | PostgreSQL/Testcontainers side-effect test | PASS |

    Requirements PASS = 76/76
    Requirements failed = 0
    Requirements N/A = 0

### Final Gate F state

    Gate A = PASS
    Gate B = PASS
    Gate C = PASS
    Gate D = PASS
    Gate E = PASS
    Gate F mechanical closure = PASS
    V015 unchanged = PASS
    no new journal index = PASS
    no forbidden application dependency = PASS
    port methods = exactly four
    P0.2 repository = exactly find/apply
    tracked TASK modifications = 2
    untracked TASK creations = 5
    total TASK-owned paths = 7
    unexpected paths = 0
    implementation commit = pending
    push = not performed
