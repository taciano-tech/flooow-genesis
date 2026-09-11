# TASK-0094 - Marketplace Financial Reconciliation

## Outcome

Implemented the first production-inactive MKT-003 reconciliation projection
authorized by ADR-0022 and SPEC-0022.

The Marketplace vertical can now derive an exact, explainable current status
from one immutable financial trace and one explicit versioned policy without
changing the ledger or activating a financial workflow.

## Implemented boundary

- Pure package:
  `io.flooow.marketplace.operations.economics.reconciliation`.
- Complete currency-safe absolute tolerance policy for every ledger stage.
- Correction-leaf projection that preserves but excludes superseded facts.
- Exact additions-minus-deductions nets for EXPECTED and ACTUAL evidence.
- Typed distinction between absent evidence and an observed zero.
- Signed `actual - expected` and absolute differences.
- Per-stage `PENDING`, `PARTIALLY_RECONCILED`, `DIVERGENCE`, and
  `FULLY_RECONCILED` classification.
- Deterministic aggregate trace status and evidence-ID ordering.
- Typed empty-trace and policy-currency outcomes.
- Redacted aggregate rendering and Kernel bytecode boundary verification.

## Reproduced scenarios

```text
exact sale                      -> FULLY_RECONCILED
65.31 expected / 50.00 actual   -> PARTIALLY_RECONCILED
65.31 expected / 65.31 actual   -> FULLY_RECONCILED
-41.99 expected / -42.49 actual -> DIVERGENCE (-0.50)
expected only                   -> PENDING
actual only                     -> DIVERGENCE
observed zero vs non-zero       -> DIVERGENCE
correction 60.00 -> 65.31       -> leaf 65.31 is effective
charge 10.00 + reversal 2.00    -> exact net -8.00
```

## Architectural evidence

- No file under `platform/foundation/kernel` changed.
- No reconciliation source imports `io.flooow.kernel`.
- No migration, repository, JDBC, API, event, connector, scheduler, worker, or
  startup wiring was added.
- No pricing, recovery, recommendation, action, model, LLM, ML, expert, or
  agent concept was added.
- No `Float` or `Double` participates in financial calculation.
- The result is a deterministic function of immutable ledger evidence and a
  policy version; it reads no clock or external state.

## Remaining boundary

Assessment persistence, lifecycle history, settlement allocation, bank-account
matching, policy registry, percentage/materiality rules, systemic divergence,
investigation, returns, recovery, accounting export, API/UI, notifications,
pricing, decisions, actions, outcomes, and learning remain explicitly deferred.
