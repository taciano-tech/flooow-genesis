# EXP-0006 — Durable Evidence Incremental Change Feed

## Status

Ready for review. This document records experimental observations only. It
does not authorize an ADR, SPEC, TASK, migration, or production implementation.

## Baseline

- base commit: `e0ccf54fbd4885d3057d6872aa9e476eb6a7fc11`;
- normative proposal:
  `docs/vision/RFC-0007-DURABLE-EVIDENCE-INCREMENTAL-CHANGE-FEED.md`, version
  0.2;
- Docker: version 29.6.2;
- Testcontainers image: `postgres:18.4`;
- Flyway applied and validated V001 through V015 before the experimental
  schema was created.

## Experimental files

- `research/experiments/exp-0006-harness/settings.gradle.kts`;
- `research/experiments/exp-0006-harness/build.gradle.kts`;
- `research/experiments/exp-0006-harness/src/test/kotlin/io/flooow/research/exp0006/ExperimentalChangeSequenceCheckpoint.kt`;
- `research/experiments/exp-0006-harness/src/test/kotlin/io/flooow/research/exp0006/ExperimentalProjectionName.kt`;
- `research/experiments/exp-0006-harness/src/test/kotlin/io/flooow/research/exp0006/ExperimentalMarketplaceEconomicEvidenceChange.kt`;
- `research/experiments/exp-0006-harness/src/test/kotlin/io/flooow/research/exp0006/ExperimentalCheckpointAdvanceResult.kt`;
- `research/experiments/exp-0006-harness/src/test/kotlin/io/flooow/research/exp0006/ExperimentalMarketplaceEconomicEvidenceChangeFeed.kt`;
- `research/experiments/exp-0006-harness/src/test/kotlin/io/flooow/research/exp0006/ExperimentalPostgresChangeFeed.kt`;
- `research/experiments/exp-0006-harness/src/test/kotlin/io/flooow/research/exp0006/ExperimentalChangeFeedTest.kt`.

The `projection_checkpoint` table, validation constraint, timestamp trigger,
and candidate queries were created only inside the disposable Testcontainers
database. No Flyway migration was created.

## Execution

Final isolated command:

```powershell
.\gradlew.bat -p research\experiments\exp-0006-harness :test --rerun-tasks --stacktrace
```

Final result:

```text
7 tests completed
7 passed
0 failed
0 skipped
BUILD SUCCESSFUL
```

## Observed functional results

- `changesSince(NONE)` returned committed changes for the requested
  organization through the update-journal/subject join.
- Returned changes carried the complete
  `MarketplaceEconomicEvidenceSubject` and no economic payload.
- A partial exclusive checkpoint returned only later changes.
- A checkpoint equal to the observed maximum returned an empty result.
- Bounded pagination reproduced the complete ordered result without omission
  or repetition when the last returned sequence became the next checkpoint.
- Repeated reads at the same checkpoint returned the same result when no new
  write occurred.
- `changesSince` did not change durable checkpoint state.
- Explicit checkpoint advancement changed the checkpoint used by the next
  read.
- Organizations with journal changes and no checkpoint row appeared in the
  pending-organizations result as `NONE`.
- Multiple organizations retained independent checkpoint views.
- Two projection names consumed the same journal independently.
- Pending-organization ordering was stable across repeated executions of the
  exact query.

## Observed checkpoint CAS results

- missing row, `expected = NONE`, `next > NONE`: `Advanced`, one row created;
- missing row, `expected != NONE`: `Stale`, zero rows created;
- `next <= expected`: `Regression`, durable checkpoint unchanged;
- durable value different from expected: `Stale`, durable checkpoint
  unchanged;
- two concurrent first writers with the same expected/next: exactly one
  `Advanced` and one `Stale`; one durable row remained;
- two concurrent writers over an existing checkpoint with the same
  expected/next: exactly one `Advanced` and one `Stale`;
- no unique-constraint exception escaped the experimental boundary.

## P0.2 writer concurrency

The concurrency test used the production
`PostgresMarketplaceIndependentEconomicEvidenceRepository` to apply real
economic facts. The observed composition was:

```text
P0.2 repository apply
  -> V015 durable update and change_sequence
  -> experimental changesSince read
```

One initial fact and twenty additional facts were committed for the same
organization and aggregate while the experimental feed performed concurrent
reads. The final feed contained 21 changes. Every captured snapshot and the
final result were strictly increasing by `change_sequence` within that
organization. The experiment made no assertion about global commit ordering
between organizations.

## Performance fixture

The disposable database contained 6,029 rows in
`marketplace_economic_evidence_update` at plan execution. The volume fixture
contributed 6,000 valid journal updates across 30 organizations, with
checkpoint rows for ten of those organizations under
`performance-projection`. Other functional-test data remained in the same
database. The query returned 24 pending organizations because the database
also contained organizations created by earlier experimental fixtures.

## Raw EXPLAIN (ANALYZE, BUFFERS)

Exact plan captured from the query used by
`organizationsWithPendingChanges`:

```text
Limit  (cost=180.74..180.90 rows=67 width=16) (actual time=0.932..0.936 rows=24.00 loops=1)
  Buffers: shared hit=85
  ->  Sort  (cost=180.74..180.90 rows=67 width=16) (actual time=0.931..0.933 rows=24.00 loops=1)
        Sort Key: marketplace_economic_evidence_update.organization_id
        Sort Method: quicksort  Memory: 25kB
        Buffers: shared hit=85
        ->  Hash Left Join  (cost=176.16..178.70 rows=67 width=16) (actual time=0.901..0.909 rows=24.00 loops=1)
              Hash Cond: (marketplace_economic_evidence_update.organization_id = c.organization_id)
              Filter: ((max(marketplace_economic_evidence_update.change_sequence)) > COALESCE(c.last_change_sequence, '0'::bigint))
              Rows Removed by Filter: 10
              Buffers: shared hit=82
              ->  HashAggregate  (cost=155.11..157.11 rows=200 width=24) (actual time=0.879..0.883 rows=34.00 loops=1)
                    Group Key: marketplace_economic_evidence_update.organization_id
                    Batches: 1  Memory Usage: 32kB
                    Buffers: shared hit=81
                    ->  Seq Scan on marketplace_economic_evidence_update  (cost=0.00..130.41 rows=4941 width=24) (actual time=0.008..0.379 rows=6029.00 loops=1)
                          Buffers: shared hit=81
              ->  Hash  (cost=21.00..21.00 rows=4 width=24) (actual time=0.011..0.011 rows=10.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on projection_checkpoint c  (cost=0.00..21.00 rows=4 width=24) (actual time=0.007..0.008 rows=10.00 loops=1)
                          Filter: (projection_name = 'performance-projection'::text)
                          Rows Removed by Filter: 2
                          Buffers: shared hit=1
Planning:
  Buffers: shared hit=256
Planning Time: 0.502 ms
Execution Time: 0.987 ms
```

Observed node types included `Limit`, `Sort`, `Hash Left Join`,
`HashAggregate`, `Seq Scan`, and `Hash`. The observed plan used no index scan.
It examined 6,029 journal rows, removed ten joined rows through the checkpoint
filter, used shared-buffer hits only in the reported execution, and reported no
buffer reads. This paragraph records observations and makes no performance or
production-readiness decision.

## Initial experimental failures and corrections

The first harness execution produced two experimental-test failures:

1. the test compared PostgreSQL UUID ordering with Java UUID natural ordering;
   the test was corrected to prove deterministic repeated database ordering
   without substituting a different ordering implementation;
2. the initial volume fixture inserted update rows without their required V015
   identifier/attempt subtype rows and root-version advancement; PostgreSQL's
   deferred integrity trigger rejected the commit. The fixture was corrected
   to insert a structurally valid attempt history and advance every root
   version one step at a time.

No production constraint was disabled or weakened.

## Limitations observed

- Slice A is an invalidation feed and does not reconstruct the aggregate at
  each historical `evidenceVersion`.
- The pending-organizations candidate aggregated the entire observed journal
  and PostgreSQL selected a sequential scan at the tested scale.
- The performance fixture is thousands, not millions, of journal rows.
- The performance observation was executed with warm shared buffers and no
  reported physical reads.
- Pending discovery was tested without leases, multiple poller ownership, or
  failure metadata; those concepts are outside RFC-0007 v0.2.
- Materialization/checkpoint atomicity remains outside Slice A.
- The experiment supplies evidence but makes no architectural acceptance
  decision.
