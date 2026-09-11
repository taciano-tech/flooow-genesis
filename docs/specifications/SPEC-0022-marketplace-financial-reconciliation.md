# SPEC-0022: Marketplace Financial Reconciliation and Divergence Detection

Status: Proposed

Date: 2026-08-13

Source decision: ADR-0022

## Objective

Produce a deterministic, explainable expected-versus-actual reconciliation
assessment from one immutable Marketplace Financial Trace and one explicit
versioned tolerance policy, without changing ledger history or activating any
runtime behavior.

## Authorized next implementation

Acceptance authorizes TASK-0094 only:

1. add pure reconciliation contracts under
   `io.flooow.marketplace.operations.economics.reconciliation`;
2. reuse `FinancialTrace`, ledger stage/basis, correction relationships, exact
   MKT-001 money/currency, and existing identities without weakening them;
3. implement effective correction-leaf selection, exact stage/basis nets,
   typed side presence, typed differences, per-stage statuses, and aggregate
   trace status;
4. require a complete explicit per-stage tolerance policy and version;
5. preserve deterministic evidence entry IDs and redacted rendering;
6. prove all status, tolerance, correction, reversal, ordering, currency,
   privacy, and boundary rules with pure tests;
7. leave persistence, startup, APIs, connectors, and every other module
   behavior unchanged.

No migration, repository, JDBC, JSON, HTTP, event, worker, clock, random source,
cross-stage matching, allocation, case workflow, recovery, pricing, simulation,
recommendation, action, AI, LLM, ML, or Kernel change is authorized.

## Package boundary

```text
applications/marketplace-operations/src/main/kotlin/
  io/flooow/marketplace/operations/economics/reconciliation/
```

No source in this package may import `io.flooow.kernel`. A bytecode boundary
test must enforce the absence of Kernel references.

## Policy values

```text
FinancialReconciliationPolicyVersion
```

The version is caller-supplied canonical text matching
`[a-z0-9][a-z0-9./-]{0,99}`. It has value equality and `[REDACTED]` rendering.

```text
FinancialReconciliationPolicy(
  version,
  currency,
  tolerancesByStage
)
```

Policy construction requires:

- exactly one tolerance for every `FinancialLedgerStage`;
- no unknown, missing, or duplicate stage;
- every tolerance uses the policy currency;
- every tolerance is non-negative;
- exact `MarketplaceMoney`, never floating point.

An exact-zero policy is valid. Tolerance equality is inclusive.

## Effective entries

Given a validated `FinancialTrace`, build the set of IDs referenced by
`correctsEntryId`. An entry is effective when its ID is absent from that set.

This selects exactly the leaf of every valid correction chain while retaining
uncorrected entries. Reversals remain ordinary effective entries. The input
trace is not modified and no copied ledger history is exposed as mutable.

## Side projection

For every stage appearing among effective entries, project EXPECTED and ACTUAL
independently:

```text
FinancialReconciliationSide
  NotObserved
  Observed(netAmount, effectiveEntryIds)
```

`effectiveEntryIds` is a non-empty immutable list sorted by unsigned UUID. The
net is calculated exactly as additions minus deductions. It may be negative or
zero. `NotObserved` contains no synthetic amount or ID.

The trace currency and policy currency must be equal. A disagreement produces
a controlled `PolicyCurrencyMismatch` result and no assessment.

## Difference projection

```text
FinancialReconciliationDifference
  NotComparable
  Compared(signedDifference, absoluteDifference, tolerance)
```

`Compared` exists only when both sides are observed:

```text
signedDifference = actual.netAmount - expected.netAmount
absoluteDifference = abs(signedDifference)
```

All three values use the trace currency. The absolute difference and tolerance
are non-negative. A missing side returns `NotComparable`.

## Statuses

```text
FinancialReconciliationStatus
  PENDING
  PARTIALLY_RECONCILED
  DIVERGENCE
  FULLY_RECONCILED
```

Per-stage classification is exactly:

```text
expected observed, actual absent                 -> PENDING
expected absent, actual observed                 -> DIVERGENCE
both, abs(actual - expected) <= tolerance        -> FULLY_RECONCILED
both, outside tolerance, actual non-zero,
same sign, abs(actual) < abs(expected)            -> PARTIALLY_RECONCILED
otherwise                                        -> DIVERGENCE
```

The partial rule cannot apply when expected is zero. Sign comparison uses exact
decimal sign, not entry direction alone, because additions and deductions may
net within one stage.

## Stage result

```text
FinancialReconciliationLine(
  stage,
  expected,
  actual,
  difference,
  status
)
```

Lines are immutable and ordered by `FinancialLedgerStage` declaration order.
Each stage appears at most once. A line always has at least one observed side.
Aggregate rendering is `[REDACTED]`.

## Assessment

```text
FinancialReconciliationAssessment(
  organizationId,
  traceId,
  orderId,
  currency,
  policyVersion,
  lines,
  status
)
```

Construction requires a non-empty immutable canonical line list and derives
the aggregate status with this exact precedence:

```text
any DIVERGENCE                         -> DIVERGENCE
all FULLY_RECONCILED                   -> FULLY_RECONCILED
all PENDING                            -> PENDING
otherwise                              -> PARTIALLY_RECONCILED
```

The assessment contains no wall-clock time, mutable state, workflow authority,
or hidden input. Aggregate rendering is `[REDACTED]`.

## Evaluator and controlled result

```text
MarketplaceFinancialReconciliation.assess(trace, policy)
```

```text
FinancialReconciliationResult
  Assessed(assessment)
  NotAssessable(NO_FINANCIAL_FACTS)
  PolicyCurrencyMismatch
```

An empty trace returns `NotAssessable(NO_FINANCIAL_FACTS)`. It is not pending
and not fully reconciled. Invalid policy construction fails before evaluation
with controlled invariant messages containing no organization, order, amount,
currency, source, or entry data.

The evaluator reads no database, clock, environment, file, network, random
source, global organization context, or provider configuration.

## Determinism and identity

For value-equal traces and policies, repeated evaluation returns value-equal
assessments. Input collection order, ledger recording order among independent
facts, and map iteration order cannot change line order, entry-ID order, net,
difference, or status.

The assessment does not generate a reconciliation ID. Durable assessment
identity and historical assessment storage require a later contract.

## Privacy

Policy, side, difference, line, assessment, and result aggregate renderings
must not expose organization, trace, order, marketplace, external order,
currency, amounts, stages, entry IDs, policy version, source, references, or
timestamps.

Structured values remain available only to authorized in-process callers.
Exception messages describe invariant categories only and contain no input
values.

## Acceptance fixtures

### Exact completion

For `SALE / EXPECTED / ADDITION 299.90` and
`SALE / ACTUAL / ADDITION 299.90`, with zero SALE tolerance:

```text
difference = 0
line status = FULLY_RECONCILED
trace status = FULLY_RECONCILED
```

### Partial settlement

For `SETTLEMENT / EXPECTED / ADDITION 65.31` and actual settlement additions
of `20.00` and `30.00`, with zero tolerance:

```text
actual net = 50.00
difference = -15.31
line status = PARTIALLY_RECONCILED
```

Appending a later actual `15.31` makes the recomputed line fully reconciled.

### Divergent commission

For `MARKETPLACE_COMMISSION / EXPECTED / DEDUCTION 41.99` and
`MARKETPLACE_COMMISSION / ACTUAL / DEDUCTION 42.49`, with tolerance `0.01`:

```text
expected net = -41.99
actual net = -42.49
signed difference = -0.50
absolute difference = 0.50
line status = DIVERGENCE
```

### Correction

If an actual BANK entry `60.00` is corrected by `65.31`, only `65.31` is
effective. Both entries remain present in the input trace and the line cites
only the correction leaf.

### Unexpected actual

An actual-only fee is `DIVERGENCE` regardless of its amount or configured
tolerance. It is never silently treated as expected zero.

## Test plan

TASK-0094 proves at least:

1. reconciliation bytecode references no Kernel type;
2. no `Float`, `Double`, clock, random, network, database, or framework type is
   used;
3. policy version, equality, full stage coverage, currency, non-negative
   tolerance, safe rendering, and immutable map behavior;
4. empty trace returns typed not-assessable;
5. policy/trace currency mismatch returns the controlled result;
6. corrected ancestors are excluded and chain leaves are effective;
7. economic reversals remain included and net by direction;
8. absent side remains distinct from observed zero;
9. expected-only is pending;
10. actual-only is divergence even within tolerance;
11. exact equality and inclusive tolerance are fully reconciled;
12. same-sign underpayment is partially reconciled;
13. explicit zero, overpayment, opposite sign, and outside-tolerance mismatch
    are divergent;
14. positive and negative expected nets follow the same partial rule;
15. multiple partial actual entries sum exactly;
16. difference is `actual - expected` and absolute difference is non-negative;
17. any divergent line makes the trace divergent;
18. all fully reconciled lines make the trace fully reconciled;
19. all pending lines make the trace pending;
20. mixed full/pending or any directly partial line makes the trace partially
    reconciled when no divergence exists;
21. line and effective-entry ordering are deterministic under shuffled input;
22. value-equal inputs produce value-equal assessments;
23. aggregate rendering and invariant errors disclose no sensitive value;
24. input trace and policy are not mutated;
25. no persistence, API, event, workflow, connector, pricing, action, AI, or
    runtime behavior is introduced;
26. no file under `platform/foundation/kernel` changes;
27. `git diff --check` and the complete repository build remain green.

## Remaining boundary

Durable assessment identity/history, current-status persistence, stage or line
allocation keys, settlement grouping, bank-account matching, percentage and
materiality policies, policy registry, lifecycle timestamps, reconciliation
cases, investigation, systemic divergence, refunds, returns, recovery,
accounting export, ingestion, API/UI, alerts, pricing, simulation,
recommendations, decisions, actions, outcomes, and learning require later
accepted specifications.

## Acceptance

Merging ADR-0022 and SPEC-0022 authorizes TASK-0094 only. It changes no runtime
behavior and authorizes no persistent status, live financial source, external
action, pricing, AI, or Kernel modification.
