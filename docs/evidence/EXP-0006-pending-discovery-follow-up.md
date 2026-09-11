# EXP-0006 — Pending-Organization Discovery Follow-up

Date: 2026-09-02

Status: READY FOR REVIEW

## Scope

This follow-up compares three pending-organization discovery query hypotheses against PostgreSQL 18.4/Testcontainers. It does not change production code, V015, the P0.2 repository, migrations, or the original EXP-0006 evidence.

No planner setting was forced. Existing constraints and triggers remained enabled. Flyway applied V001 through V015 before fixture creation.

The raw plans and factual comparison below are from the final focused comparison run, after `ANALYZE` on the journal, checkpoint, and organization tables.

## Queries

### Query A — full journal aggregation

```sql
SELECT h.organization_id
FROM (
    SELECT organization_id,
           max(change_sequence) AS maximum_change_sequence
    FROM marketplace_economic_evidence_update
    GROUP BY organization_id
) h
LEFT JOIN projection_checkpoint c
  ON c.organization_id = h.organization_id
 AND c.projection_name = ?
WHERE h.maximum_change_sequence >
      COALESCE(c.last_change_sequence, 0)
ORDER BY h.organization_id ASC
LIMIT ?
```

### Query B — exists per organization

```sql
SELECT o.organization_id
FROM integration_organization o
LEFT JOIN projection_checkpoint c
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

### Query C — latest sequence index lookup

```sql
SELECT o.organization_id
FROM integration_organization o
LEFT JOIN projection_checkpoint c
  ON c.organization_id = o.organization_id
 AND c.projection_name = ?
WHERE (
    SELECT u.change_sequence
    FROM marketplace_economic_evidence_update u
    WHERE u.organization_id = o.organization_id
    ORDER BY u.change_sequence DESC
    LIMIT 1
) > COALESCE(c.last_change_sequence, 0)
ORDER BY o.organization_id ASC
LIMIT ?
```

## Functional equivalence

The harness compared exact ordered `organization_id` lists programmatically with `A == B` and `A == C` for:

- no checkpoint;
- partial checkpoint;
- checkpoint at maximum sequence;
- mixed pending/non-pending organizations;
- two independent projection names;
- limit 7 while pending organizations exceeded the limit.

All comparisons passed at all three cumulative scales.

## Fixtures

Each journal row belongs to a valid, independently versioned evidence aggregate. Fixture loading inserted subject, update, identifier, and collection-attempt rows and then advanced the subject root to version 1. Existing V015 constraints and deferred triggers remained enabled.

| Scale | Added organizations | Added changes per organization | Cumulative journal rows | Cumulative organizations | Total checkpoint rows | Projection checkpoint rows | Pending | Non-pending | Changes min / avg / max | EXPLAIN limit |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---|---:|
| 1 | 40 | 25–64 | 1,780 | 40 | 34 | 20 | 30 | 10 | 25 / 44.500000 / 64 | 1,000 |
| 2 | 80 | 800–879 | 68,940 | 120 | 134 | 60 | 90 | 30 | 25 / 574.500000 / 879 | 1,000 |
| 3 | 120 | 2,000–2,119 | 316,080 | 240 | 334 | 120 | 180 | 60 | 25 / 1317.000000 / 2,119 | 1,000 |

The `checkpoint rows` column is the total across all experimental projection names accumulated at that scale. `Projection checkpoint rows` is the count for the projection used by that scale's measured queries.

## Factual comparison

| Scale | Query | Journal rows | Organizations | Pending | Node strategy | Journal examination observable in plan | Existing index used | Buffer hits | Buffer reads | Planning ms | Execution ms |
|---|---|---:|---:|---:|---|---|---|---:|---:|---:|---:|
| 1 | A | 1,780 | 40 | 30 | Seq Scan → HashAggregate → Hash Right Join → Sort | Seq Scan rows=1,780 | No journal index | 28 | 0 | 0.528 | 0.536 |
| 1 | B | 1,780 | 40 | 30 | Hash Left Join → Nested Loop Semi Join → Index Only Scan → Sort | 40 index searches; 30 heap fetches | `marketplace_economic_evidence_organization_id_change_sequen_key` | 115 | 0 | 0.602 | 0.363 |
| 1 | C | 1,780 | 40 | 30 | Hash Right Join → backward Index Only Scan subplan → Sort | 40 index searches; 40 heap fetches | `marketplace_economic_evidence_organization_id_change_sequen_key` | 125 | 0 | 0.456 | 0.269 |
| 2 | A | 68,940 | 120 | 90 | Seq Scan → HashAggregate → Sort → Merge Left Join | Seq Scan rows=68,940 | No journal index | 922 | 0 | 0.586 | 12.035 |
| 2 | B | 68,940 | 120 | 90 | Hash Right Join → Nested Loop Semi Join → Index Only Scan → Sort | 120 index searches; 90 heap fetches | `marketplace_economic_evidence_organization_id_change_sequen_key` | 456 | 0 | 0.744 | 0.866 |
| 2 | C | 68,940 | 120 | 90 | Hash Right Join → backward Index Only Scan subplan → Sort | 120 index searches; 120 heap fetches | `marketplace_economic_evidence_organization_id_change_sequen_key` | 486 | 0 | 0.484 | 1.170 |
| 3 | A | 316,080 | 240 | 180 | Parallel Seq Scan → Partial HashAggregate → Gather Merge → Finalize GroupAggregate → Merge Left Join | 105,360 rows × 3 loops = 316,080 | No journal index | 4,233 | 0 | 0.563 | 23.191 |
| 3 | B | 316,080 | 240 | 180 | Hash Right Join → Nested Loop Semi Join → Index Only Scan → Sort | 240 index searches; 180 heap fetches | `marketplace_economic_evidence_organization_id_change_sequen_key` | 909 | 0 | 0.715 | 1.409 |
| 3 | C | 316,080 | 240 | 180 | Hash Right Join → backward Index Only Scan subplan → Sort | 240 index searches; 240 heap fetches | `marketplace_economic_evidence_organization_id_change_sequen_key` | 969 | 0 | 0.581 | 1.639 |

All recorded plans used warm shared buffers; no buffer reads were reported in the measured runs.

## Raw EXPLAIN (ANALYZE, BUFFERS)

### Scale 1 — Query A

```text
Limit  (cost=53.32..53.35 rows=13 width=16) (actual time=0.473..0.478 rows=30.00 loops=1)
  Buffers: shared hit=28
  ->  Sort  (cost=53.32..53.35 rows=13 width=16) (actual time=0.472..0.475 rows=30.00 loops=1)
        Sort Key: marketplace_economic_evidence_update.organization_id
        Sort Method: quicksort  Memory: 25kB
        Buffers: shared hit=28
        ->  Hash Right Join  (cost=51.60..53.08 rows=13 width=16) (actual time=0.443..0.452 rows=30.00 loops=1)
              Hash Cond: (c.organization_id = marketplace_economic_evidence_update.organization_id)
              Filter: ((max(marketplace_economic_evidence_update.change_sequence)) > COALESCE(c.last_change_sequence, '0'::bigint))
              Rows Removed by Filter: 10
              Buffers: shared hit=25
              ->  Seq Scan on projection_checkpoint c  (cost=0.00..1.43 rows=20 width=24) (actual time=0.005..0.008 rows=20.00 loops=1)
                    Filter: (projection_name = 'comparison-scale-1'::text)
                    Rows Removed by Filter: 14
                    Buffers: shared hit=1
              ->  Hash  (cost=51.10..51.10 rows=40 width=24) (actual time=0.426..0.427 rows=40.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 11kB
                    Buffers: shared hit=24
                    ->  HashAggregate  (cost=50.70..51.10 rows=40 width=24) (actual time=0.415..0.419 rows=40.00 loops=1)
                          Group Key: marketplace_economic_evidence_update.organization_id
                          Batches: 1  Memory Usage: 32kB
                          Buffers: shared hit=24
                          ->  Seq Scan on marketplace_economic_evidence_update  (cost=0.00..41.80 rows=1780 width=24) (actual time=0.008..0.212 rows=1780.00 loops=1)
                                Buffers: shared hit=24
Planning:
  Buffers: shared hit=252
Planning Time: 0.528 ms
Execution Time: 0.536 ms
```

### Scale 1 — Query B

```text
Limit  (cost=32.37..32.41 rows=13 width=16) (actual time=0.254..0.258 rows=30.00 loops=1)
  Buffers: shared hit=115
  ->  Sort  (cost=32.37..32.41 rows=13 width=16) (actual time=0.253..0.255 rows=30.00 loops=1)
        Sort Key: o.organization_id
        Sort Method: quicksort  Memory: 25kB
        Buffers: shared hit=115
        ->  Nested Loop Semi Join  (cost=1.95..32.13 rows=13 width=16) (actual time=0.060..0.222 rows=30.00 loops=1)
              Buffers: shared hit=112
              ->  Hash Left Join  (cost=1.68..3.19 rows=40 width=24) (actual time=0.030..0.040 rows=40.00 loops=1)
                    Hash Cond: (o.organization_id = c.organization_id)
                    Buffers: shared hit=2
                    ->  Seq Scan on integration_organization o  (cost=0.00..1.40 rows=40 width=16) (actual time=0.006..0.009 rows=40.00 loops=1)
                          Buffers: shared hit=1
                    ->  Hash  (cost=1.43..1.43 rows=20 width=24) (actual time=0.013..0.013 rows=20.00 loops=1)
                          Buckets: 1024  Batches: 1  Memory Usage: 10kB
                          Buffers: shared hit=1
                          ->  Seq Scan on projection_checkpoint c  (cost=0.00..1.43 rows=20 width=24) (actual time=0.004..0.007 rows=20.00 loops=1)
                                Filter: (projection_name = 'comparison-scale-1'::text)
                                Rows Removed by Filter: 14
                                Buffers: shared hit=1
              ->  Index Only Scan using marketplace_economic_evidence_organization_id_change_sequen_key on marketplace_economic_evidence_update u  (cost=0.28..7.77 rows=15 width=24) (actual time=0.004..0.004 rows=0.75 loops=40)
                    Index Cond: ((organization_id = o.organization_id) AND (change_sequence > COALESCE(c.last_change_sequence, '0'::bigint)))
                    Heap Fetches: 30
                    Index Searches: 40
                    Buffers: shared hit=110
Planning:
  Buffers: shared hit=295
Planning Time: 0.602 ms
Execution Time: 0.363 ms
```

### Scale 1 — Query C

```text
Limit  (cost=3.62..3.65 rows=13 width=16) (actual time=0.235..0.239 rows=30.00 loops=1)
  Buffers: shared hit=125
  ->  Sort  (cost=3.62..3.65 rows=13 width=16) (actual time=0.234..0.236 rows=30.00 loops=1)
        Sort Key: o.organization_id
        Sort Method: quicksort  Memory: 25kB
        Buffers: shared hit=125
        ->  Hash Right Join  (cost=1.90..3.38 rows=13 width=16) (actual time=0.056..0.216 rows=30.00 loops=1)
              Hash Cond: (c.organization_id = o.organization_id)
              Filter: ((SubPlan 1) > COALESCE(c.last_change_sequence, '0'::bigint))
              Rows Removed by Filter: 10
              Buffers: shared hit=122
              ->  Seq Scan on projection_checkpoint c  (cost=0.00..1.43 rows=20 width=24) (actual time=0.003..0.005 rows=20.00 loops=1)
                    Filter: (projection_name = 'comparison-scale-1'::text)
                    Rows Removed by Filter: 14
                    Buffers: shared hit=1
              ->  Hash  (cost=1.40..1.40 rows=40 width=16) (actual time=0.012..0.012 rows=40.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 10kB
                    Buffers: shared hit=1
                    ->  Seq Scan on integration_organization o  (cost=0.00..1.40 rows=40 width=16) (actual time=0.005..0.007 rows=40.00 loops=1)
                          Buffers: shared hit=1
              SubPlan 1
                ->  Limit  (cost=0.28..2.46 rows=1 width=8) (actual time=0.004..0.004 rows=1.00 loops=40)
                      Buffers: shared hit=120
                      ->  Index Only Scan Backward using marketplace_economic_evidence_organization_id_change_sequen_key on marketplace_economic_evidence_update u  (cost=0.28..96.46 rows=44 width=8) (actual time=0.004..0.004 rows=1.00 loops=40)
                            Index Cond: (organization_id = o.organization_id)
                            Heap Fetches: 40
                            Index Searches: 40
                            Buffers: shared hit=120
Planning:
  Buffers: shared hit=218
Planning Time: 0.456 ms
Execution Time: 0.269 ms
```

### Scale 2 — Query A

```text
Limit  (cost=1964.89..1966.54 rows=40 width=16) (actual time=11.942..11.977 rows=90.00 loops=1)
  Buffers: shared hit=922
  ->  Merge Left Join  (cost=1964.89..1966.54 rows=40 width=16) (actual time=11.941..11.970 rows=90.00 loops=1)
        Merge Cond: (marketplace_economic_evidence_update.organization_id = c.organization_id)
        Filter: ((max(marketplace_economic_evidence_update.change_sequence)) > COALESCE(c.last_change_sequence, '0'::bigint))
        Rows Removed by Filter: 30
        Buffers: shared hit=922
        ->  Sort  (cost=1959.44..1959.74 rows=120 width=24) (actual time=11.865..11.870 rows=120.00 loops=1)
              Sort Key: marketplace_economic_evidence_update.organization_id
              Sort Method: quicksort  Memory: 29kB
              Buffers: shared hit=920
              ->  HashAggregate  (cost=1954.10..1955.30 rows=120 width=24) (actual time=11.824..11.835 rows=120.00 loops=1)
                    Group Key: marketplace_economic_evidence_update.organization_id
                    Batches: 1  Memory Usage: 32kB
                    Buffers: shared hit=920
                    ->  Seq Scan on marketplace_economic_evidence_update  (cost=0.00..1609.40 rows=68940 width=24) (actual time=0.007..5.165 rows=68940.00 loops=1)
                          Buffers: shared hit=920
        ->  Sort  (cost=5.45..5.60 rows=60 width=24) (actual time=0.070..0.073 rows=60.00 loops=1)
              Sort Key: c.organization_id
              Sort Method: quicksort  Memory: 27kB
              Buffers: shared hit=2
              ->  Seq Scan on projection_checkpoint c  (cost=0.00..3.67 rows=60 width=24) (actual time=0.019..0.026 rows=60.00 loops=1)
                    Filter: (projection_name = 'comparison-scale-2'::text)
                    Rows Removed by Filter: 74
                    Buffers: shared hit=2
Planning:
  Buffers: shared hit=282
Planning Time: 0.586 ms
Execution Time: 12.035 ms
```

### Scale 2 — Query B

```text
Limit  (cost=99.13..99.23 rows=40 width=16) (actual time=0.823..0.831 rows=90.00 loops=1)
  Buffers: shared hit=456
  ->  Sort  (cost=99.13..99.23 rows=40 width=16) (actual time=0.822..0.826 rows=90.00 loops=1)
        Sort Key: o.organization_id
        Sort Method: quicksort  Memory: 25kB
        Buffers: shared hit=456
        ->  Nested Loop Semi Join  (cost=4.12..98.06 rows=40 width=16) (actual time=0.056..0.791 rows=90.00 loops=1)
              Buffers: shared hit=453
              ->  Hash Right Join  (cost=3.70..7.53 rows=120 width=24) (actual time=0.035..0.059 rows=120.00 loops=1)
                    Hash Cond: (c.organization_id = o.organization_id)
                    Buffers: shared hit=3
                    ->  Seq Scan on projection_checkpoint c  (cost=0.00..3.67 rows=60 width=24) (actual time=0.005..0.013 rows=60.00 loops=1)
                          Filter: (projection_name = 'comparison-scale-2'::text)
                          Rows Removed by Filter: 74
                          Buffers: shared hit=2
                    ->  Hash  (cost=2.20..2.20 rows=120 width=16) (actual time=0.023..0.023 rows=120.00 loops=1)
                          Buckets: 1024  Batches: 1  Memory Usage: 14kB
                          Buffers: shared hit=1
                          ->  Seq Scan on integration_organization o  (cost=0.00..2.20 rows=120 width=16) (actual time=0.007..0.011 rows=120.00 loops=1)
                                Buffers: shared hit=1
              ->  Index Only Scan using marketplace_economic_evidence_organization_id_change_sequen_key on marketplace_economic_evidence_update u  (cost=0.42..72.42 rows=191 width=24) (actual time=0.006..0.006 rows=0.75 loops=120)
                    Index Cond: ((organization_id = o.organization_id) AND (change_sequence > COALESCE(c.last_change_sequence, '0'::bigint)))
                    Heap Fetches: 90
                    Index Searches: 120
                    Buffers: shared hit=450
Planning:
  Buffers: shared hit=333
Planning Time: 0.744 ms
Execution Time: 0.866 ms
```

### Scale 2 — Query C

```text
Limit  (cost=8.60..8.70 rows=40 width=16) (actual time=1.125..1.134 rows=90.00 loops=1)
  Buffers: shared hit=486
  ->  Sort  (cost=8.60..8.70 rows=40 width=16) (actual time=1.124..1.129 rows=90.00 loops=1)
        Sort Key: o.organization_id
        Sort Method: quicksort  Memory: 25kB
        Buffers: shared hit=486
        ->  Hash Right Join  (cost=3.70..7.53 rows=40 width=16) (actual time=0.078..1.094 rows=90.00 loops=1)
              Hash Cond: (c.organization_id = o.organization_id)
              Filter: ((SubPlan 1) > COALESCE(c.last_change_sequence, '0'::bigint))
              Rows Removed by Filter: 30
              Buffers: shared hit=483
              ->  Seq Scan on projection_checkpoint c  (cost=0.00..3.67 rows=60 width=24) (actual time=0.005..0.013 rows=60.00 loops=1)
                    Filter: (projection_name = 'comparison-scale-2'::text)
                    Rows Removed by Filter: 74
                    Buffers: shared hit=2
              ->  Hash  (cost=2.20..2.20 rows=120 width=16) (actual time=0.021..0.021 rows=120.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 14kB
                    Buffers: shared hit=1
                    ->  Seq Scan on integration_organization o  (cost=0.00..2.20 rows=120 width=16) (actual time=0.006..0.010 rows=120.00 loops=1)
                          Buffers: shared hit=1
              SubPlan 1
                ->  Limit  (cost=0.42..3.50 rows=1 width=8) (actual time=0.008..0.008 rows=1.00 loops=120)
                      Buffers: shared hit=480
                      ->  Index Only Scan Backward using marketplace_economic_evidence_organization_id_change_sequen_key on marketplace_economic_evidence_update u  (cost=0.42..1767.97 rows=574 width=8) (actual time=0.008..0.008 rows=1.00 loops=120)
                            Index Cond: (organization_id = o.organization_id)
                            Heap Fetches: 120
                            Index Searches: 120
                            Buffers: shared hit=480
Planning:
  Buffers: shared hit=248
Planning Time: 0.484 ms
Execution Time: 1.170 ms
```

### Scale 3 — Query A

```text
Limit  (cost=7214.62..7289.05 rows=79 width=16) (actual time=20.787..23.114 rows=180.00 loops=1)
  Buffers: shared hit=4233
  ->  Merge Left Join  (cost=7214.62..7289.05 rows=79 width=16) (actual time=20.785..23.101 rows=180.00 loops=1)
        Merge Cond: (marketplace_economic_evidence_update.organization_id = c.organization_id)
        Filter: ((max(marketplace_economic_evidence_update.change_sequence)) > COALESCE(c.last_change_sequence, '0'::bigint))
        Rows Removed by Filter: 60
        Buffers: shared hit=4233
        ->  Finalize GroupAggregate  (cost=7202.30..7274.04 rows=238 width=24) (actual time=20.680..22.951 rows=240.00 loops=1)
              Group Key: marketplace_economic_evidence_update.organization_id
              Buffers: shared hit=4229
              ->  Gather Merge  (cost=7202.30..7268.80 rows=571 width=24) (actual time=20.674..22.885 rows=601.00 loops=1)
                    Workers Planned: 2
                    Workers Launched: 2
                    Buffers: shared hit=4229
                    ->  Sort  (cost=6202.27..6202.87 rows=238 width=24) (actual time=18.623..18.632 rows=200.33 loops=3)
                          Sort Key: marketplace_economic_evidence_update.organization_id
                          Sort Method: quicksort  Memory: 34kB
                          Buffers: shared hit=4229
                          Worker 0:  Sort Method: quicksort  Memory: 32kB
                          Worker 1:  Sort Method: quicksort  Memory: 32kB
                          ->  Partial HashAggregate  (cost=6190.50..6192.88 rows=238 width=24) (actual time=18.552..18.571 rows=200.33 loops=3)
                                Group Key: marketplace_economic_evidence_update.organization_id
                                Batches: 1  Memory Usage: 56kB
                                Buffers: shared hit=4215
                                Worker 0:  Batches: 1  Memory Usage: 56kB
                                Worker 1:  Batches: 1  Memory Usage: 56kB
                                ->  Parallel Seq Scan on marketplace_economic_evidence_update  (cost=0.00..5532.00 rows=131700 width=24) (actual time=0.007..7.425 rows=105360.00 loops=3)
                                      Buffers: shared hit=4215
        ->  Sort  (cost=12.32..12.62 rows=120 width=24) (actual time=0.102..0.107 rows=120.00 loops=1)
              Sort Key: c.organization_id
              Sort Method: quicksort  Memory: 29kB
              Buffers: shared hit=4
              ->  Seq Scan on projection_checkpoint c  (cost=0.00..8.18 rows=120 width=24) (actual time=0.032..0.049 rows=120.00 loops=1)
                    Filter: (projection_name = 'comparison-scale-3'::text)
                    Rows Removed by Filter: 214
                    Buffers: shared hit=4
Planning:
  Buffers: shared hit=284
Planning Time: 0.563 ms
Execution Time: 23.191 ms
```

### Scale 3 — Query B

```text
Limit  (cost=199.26..199.45 rows=79 width=16) (actual time=1.346..1.363 rows=180.00 loops=1)
  Buffers: shared hit=909
  ->  Sort  (cost=199.26..199.45 rows=79 width=16) (actual time=1.345..1.352 rows=180.00 loops=1)
        Sort Key: o.organization_id
        Sort Method: quicksort  Memory: 25kB
        Buffers: shared hit=909
        ->  Nested Loop Semi Join  (cost=7.82..196.77 rows=79 width=16) (actual time=0.090..1.298 rows=180.00 loops=1)
              Buffers: shared hit=906
              ->  Hash Right Join  (cost=7.40..15.89 rows=240 width=24) (actual time=0.059..0.108 rows=240.00 loops=1)
                    Hash Cond: (c.organization_id = o.organization_id)
                    Buffers: shared hit=6
                    ->  Seq Scan on projection_checkpoint c  (cost=0.00..8.18 rows=120 width=24) (actual time=0.010..0.024 rows=120.00 loops=1)
                          Filter: (projection_name = 'comparison-scale-3'::text)
                          Rows Removed by Filter: 214
                          Buffers: shared hit=4
                    ->  Hash  (cost=4.40..4.40 rows=240 width=16) (actual time=0.039..0.040 rows=240.00 loops=1)
                          Buckets: 1024  Batches: 1  Memory Usage: 20kB
                          Buffers: shared hit=2
                          ->  Seq Scan on integration_organization o  (cost=0.00..4.40 rows=240 width=16) (actual time=0.006..0.018 rows=240.00 loops=1)
                                Buffers: shared hit=2
              ->  Index Only Scan using marketplace_economic_evidence_organization_id_change_sequen_key on marketplace_economic_evidence_update u  (cost=0.42..164.11 rows=443 width=24) (actual time=0.005..0.005 rows=0.75 loops=240)
                    Index Cond: ((organization_id = o.organization_id) AND (change_sequence > COALESCE(c.last_change_sequence, '0'::bigint)))
                    Heap Fetches: 180
                    Index Searches: 240
                    Buffers: shared hit=900
Planning:
  Buffers: shared hit=337
Planning Time: 0.715 ms
Execution Time: 1.409 ms
```

### Scale 3 — Query C

```text
Limit  (cost=18.42..18.62 rows=80 width=16) (actual time=1.589..1.604 rows=180.00 loops=1)
  Buffers: shared hit=969
  ->  Sort  (cost=18.42..18.62 rows=80 width=16) (actual time=1.588..1.594 rows=180.00 loops=1)
        Sort Key: o.organization_id
        Sort Method: quicksort  Memory: 25kB
        Buffers: shared hit=969
        ->  Hash Right Join  (cost=7.40..15.89 rows=80 width=16) (actual time=0.094..1.544 rows=180.00 loops=1)
              Hash Cond: (c.organization_id = o.organization_id)
              Filter: ((SubPlan 1) > COALESCE(c.last_change_sequence, '0'::bigint))
              Rows Removed by Filter: 60
              Buffers: shared hit=966
              ->  Seq Scan on projection_checkpoint c  (cost=0.00..8.18 rows=120 width=24) (actual time=0.009..0.025 rows=120.00 loops=1)
                    Filter: (projection_name = 'comparison-scale-3'::text)
                    Rows Removed by Filter: 214
                    Buffers: shared hit=4
              ->  Hash  (cost=4.40..4.40 rows=240 width=16) (actual time=0.037..0.037 rows=240.00 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 20kB
                    Buffers: shared hit=2
                    ->  Seq Scan on integration_organization o  (cost=0.00..4.40 rows=240 width=16) (actual time=0.006..0.016 rows=240.00 loops=1)
                          Buffers: shared hit=2
              SubPlan 1
                ->  Limit  (cost=0.42..3.92 rows=1 width=8) (actual time=0.006..0.006 rows=1.00 loops=240)
                      Buffers: shared hit=960
                      ->  Index Only Scan Backward using marketplace_economic_evidence_organization_id_change_sequen_key on marketplace_economic_evidence_update u  (cost=0.42..4648.12 rows=1328 width=8) (actual time=0.006..0.006 rows=1.00 loops=240)
                            Index Cond: (organization_id = o.organization_id)
                            Heap Fetches: 240
                            Index Searches: 240
                            Buffers: shared hit=960
Planning:
  Buffers: shared hit=252
Planning Time: 0.581 ms
Execution Time: 1.639 ms
```

## Ordering diagnostic

For each scale, the measured projection had more pending organizations than the limit of 5. Query A was executed four times without advancing any checkpoint. Every execution returned the same ordered first batch.

STARVATION OBSERVED

## Test result

- PostgreSQL: 18.4 via Testcontainers
- Flyway: V001–V015 applied successfully
- Focused comparison test: 1 passed / 0 failed / 0 skipped
- Complete EXP-0006 harness: 8 passed / 0 failed / 0 skipped
- Assertions reached: yes
- A/B/C exact ordered equivalence: passed at all scales and checkpoint states

## Limitations

- Measurements are single observed executions, not a benchmark distribution.
- All measured buffers were warm shared-buffer hits.
- The largest fixture contains 316,080 journal rows, satisfying the required hundreds-of-thousands scale but not approaching one million.
- Organization counts are 40, 120, and 240 cumulative; different organization-to-history ratios may produce different planner choices.
- Fixture facts are structurally valid single-version collection attempts; they do not model every business evidence subtype.
- `ANALYZE` was executed after installing the measured checkpoint fixtures, but no `VACUUM` was run; index-only plans therefore reported heap fetches.
- The comparison records PostgreSQL's natural plans on this fixture and environment only.
- The fairness diagnostic observes repeated first batches without checkpoint advancement; it does not evaluate alternative ordering or leasing designs.
- No query is selected and no production architecture is recommended by this evidence.
