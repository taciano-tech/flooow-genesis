# ADR-0022: Marketplace Financial Reconciliation Boundary

Status: Proposed

Date: 2026-08-13

## Context

MKT-002 now preserves immutable expected and actual financial facts from a
marketplace order through settlement, payment account, and bank. It deliberately
does not decide whether those facts agree.

The next product question is narrower than returns, recovery, pricing, or a
financial workflow:

> Given one immutable financial-trace snapshot and one explicit policy, what is
> the current expected-versus-actual status of every observed financial stage?

Unsafe shortcuts would be to:

- mutate ledger entries when a source sends a correction;
- treat an absent expected or actual fact as numeric zero;
- compare binary floating-point amounts;
- hide a tolerance in application configuration or source-specific code;
- match unrelated stages merely because their amounts happen to be equal;
- call an actual-only charge reconciled because it is small;
- let a partial receipt close the whole trace;
- persist a mutable status without retaining the policy and evidence used;
- put reconciliation, order, settlement, or bank vocabulary in the Kernel;
- activate a workflow, connector, financial action, or AI with the first
  reconciliation projection.

## Decision

Introduce a pure, production-inactive Marketplace Financial Reconciliation
projection in the existing `applications:marketplace-operations` module under:

```text
io.flooow.marketplace.operations.economics.reconciliation
```

The projection consumes a validated `FinancialTrace` and an explicit immutable
policy. It performs no I/O, reads no clock, creates no identity, and persists no
state. No source in the package may import a Kernel type.

## Effective ledger facts

Ledger history remains immutable. For reconciliation only, an entry is current
when no later entry directly corrects it. In a correction chain, only the leaf
is effective; every replaced entry remains in the trace and remains auditable.

Economic reversals are not corrections. Every effective reversal remains in
the projection and contributes according to its explicit direction.

Effective entries are grouped only by the ledger's controlled financial stage
and `EXPECTED` or `ACTUAL` basis. The first contract performs no cross-stage,
cross-order, settlement-line, invoice, payout, or bank-reference matching.

## Exact signed net

For each stage and basis, the projection calculates one exact signed net:

```text
sum(ADDITION magnitudes) - sum(DEDUCTION magnitudes)
```

The input magnitudes remain non-negative. The derived net may be positive,
zero, or negative. Every calculation reuses the exact MKT-001 money and trace
currency; `Float` and `Double` are prohibited.

An observed zero remains different from no fact. Absence is represented by a
typed state and is never converted into zero.

## Explicit policy

A reconciliation policy freezes:

```text
policy version
currency
one non-negative absolute tolerance for every controlled ledger stage
```

The policy must cover the complete current stage enum. There is no default,
environment variable, marketplace override, percentage threshold, relative
tolerance, materiality band, or rounding rule.

The version is a bounded canonical value. The assessment records it so the
same trace and policy can be reproduced. Changing a tolerance requires a new
policy version and a new assessment; it never rewrites a prior ledger fact.

## Per-stage comparison

The assessment includes every stage that has at least one effective expected
or actual entry. Stages absent on both sides do not produce synthetic lines.

Each side is typed as:

```text
NOT_OBSERVED
OBSERVED(net amount, canonically ordered effective entry IDs)
```

When both sides are observed:

```text
signed difference   = actual - expected
absolute difference = abs(signed difference)
```

The line status is deterministic:

1. expected observed and actual absent -> `PENDING`;
2. expected absent and actual observed -> `DIVERGENCE`;
3. both observed and absolute difference is within tolerance ->
   `FULLY_RECONCILED`;
4. both observed, outside tolerance, with non-zero actual in the same direction
   and smaller absolute magnitude than expected -> `PARTIALLY_RECONCILED`;
5. every other observed mismatch -> `DIVERGENCE`.

An explicit actual zero against a non-zero expectation is a divergence, not
pending. Overpayment, opposite direction, unexpected charge, and amount beyond
tolerance are divergences. Tolerance never converts an actual-only fact into a
match.

## Trace status

One trace assessment has one aggregate status with this precedence:

1. any divergent line -> `DIVERGENCE`;
2. all lines fully reconciled -> `FULLY_RECONCILED`;
3. all lines pending -> `PENDING`;
4. otherwise -> `PARTIALLY_RECONCILED`.

The fourth case includes a directly partial line and a trace where some stages
are fully reconciled while others remain pending. A trace with no effective
financial facts returns a typed `NOT_ASSESSABLE` result and receives no status.

## Evidence and deterministic explanation

Every line retains the effective expected and actual entry IDs that produced
its nets, in canonical unsigned-UUID order. The assessment retains trace,
organization, order, currency, and policy ownership as structured values while
aggregate rendering remains redacted.

The projection adds no assessment timestamp. The result is a deterministic
function of immutable evidence and policy; adding a clock would make identical
inputs produce different output without adding financial truth.

## Not a reconciliation workflow

These first states describe a financial comparison only. They are not case or
recovery workflow states. The following remain outside this boundary:

```text
INVESTIGATING
RECOVERY_REQUESTED
RECOVERED
REJECTED
COMPLETED
```

The projection does not decide whether a dispute should be opened, a refund is
valid, money is recoverable, or a person may close a case.

## No infrastructure activation or intelligence

This boundary adds no:

- migration, repository, current-status table, event, outbox, API, or UI;
- Mercado Livre, ERP, payment-account, or bank adapter;
- scheduler, worker, webhook, polling, or production startup wiring;
- cross-order or systemic divergence grouping;
- returns, refund, fee reversal, recovery, dispute, or accounting workflow;
- pricing, promotion, inventory, forecasting, simulation, recommendation,
  decision, action, AI, model, expert, or agent;
- Kernel change.

## Consequences

### Positive

- expected and actual values become comparable without weakening the ledger;
- corrections are projected without erasing their audit history;
- partial settlements and unexpected charges receive distinct typed outcomes;
- tolerance is explicit, currency-safe, versioned, and reproducible;
- every line cites the exact effective entries used;
- later pricing and recovery work can distinguish pending evidence from true
  divergence;
- Marketplace vocabulary remains outside the Kernel.

### Negative

- callers must provide expected and actual facts at the same controlled stage;
- equal values at different stages are deliberately not matched;
- no durable history of assessments exists in this slice;
- the first policy supports only absolute per-stage tolerance;
- an empty trace cannot be declared reconciled;
- systemic issues and case lifecycle remain later work.

## Alternatives considered

### Reconcile sale directly to bank receipt

Rejected because fees, costs, settlement boundaries, partial payouts, and
reversals make amount equality insufficient evidence of identity.

### Treat missing actual as zero

Rejected because "not observed" and an observed zero are different financial
facts and lead to different statuses.

### Compare every ledger entry one-to-one

Rejected because one obligation may be settled by several actual facts and the
first accepted slice has no authoritative line-allocation key.

### Store status on the ledger trace

Rejected because status changes as facts arrive and policies evolve, while the
ledger is immutable evidence.

### One global hidden tolerance

Rejected because different stages can have different accepted precision and a
hidden threshold cannot be audited or reproduced.

### Add reconciliation concepts to the Kernel

Rejected because only Marketplace Intelligence has demonstrated this need.

## Authorization

This ADR alone authorizes no implementation. SPEC-0022 may authorize only the
pure deterministic projection and focused tests for TASK-0094. It authorizes no
persistence, runtime, connector, API, workflow, financial action, pricing, AI,
or Kernel modification.
