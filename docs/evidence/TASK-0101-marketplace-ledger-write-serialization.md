# TASK-0101 - Marketplace Ledger write serialization

## Trigger

The same pre-existing concurrency test failed on the first CI execution of two
consecutive, unrelated pull requests:

```text
PostgresMarketplaceFinancialLedgerRepositoryTest
  > concurrent open append and direct correction accept exactly one write
```

Both reruns passed. Neither pull request changed the Ledger or PostgreSQL
adapter, so changing either product diff would have hidden the actual debt.

## Root cause

`PostgresMarketplaceFinancialLedgerRepository.append` is a write operation over
the ordered fact set owned by one financial trace. It previously selected the
trace root with:

```sql
FOR SHARE
```

Shared row locks are mutually compatible. Two append transactions could
therefore both pass the trace boundary and race on append-request or source-fact
uniqueness. The losing transaction then depended on exception recovery to
reconstruct the replay result. Under CI timing, that path intermittently
returned a result other than the required `AlreadyAppended`.

## Correction

The append boundary now locks the trace root with:

```sql
FOR UPDATE
```

The helper is named `lockTraceForWrite` to make the required lock semantics
explicit.

Concurrent appends for the same trace are now serialized before source-fact,
entry-identity, and correction validation. Appends for different trace rows
remain independent.

No table, constraint, migration, public domain result, transaction isolation
level, API, connector, or Kernel behavior changed.

## Test strengthening

The existing concurrent open/append/correction test now uses:

```text
two worker threads
ready latch for both workers
shared start latch
bounded worker/result waits
```

This forces both operations to reach the concurrency boundary before either is
released. The test no longer relies on incidental executor scheduling.

It continues to prove:

- concurrent trace open yields one `Opened` and one `AlreadyOpen`;
- identical concurrent append yields one `Appended` and one
  `AlreadyAppended`;
- concurrent direct corrections yield one `Appended` and one `Conflict`;
- the final immutable trace contains exactly the original fact and one
  correction.

## Architectural boundary

This is a persistence correctness repair for MKT-002 Financial Trace & Ledger.
It introduces no marketplace intelligence, pricing recommendation, authority,
execution, AI, or Kernel vocabulary.

## Validation

Required before merge:

```text
focused PostgreSQL concurrency test
complete marketplace-operations-persistence-postgres test suite
complete repository CI
git diff --check
```
