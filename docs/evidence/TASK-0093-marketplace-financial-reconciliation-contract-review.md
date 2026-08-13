# TASK-0093 - Marketplace Financial Reconciliation contract review

## Decision

ADR-0022 and SPEC-0022 define the first implementable slice of MKT-003. The
slice turns an immutable Marketplace Financial Trace into a deterministic,
versioned expected-versus-actual assessment without changing the ledger.

## Boundary choices

- Financial reconciliation remains a Marketplace Intelligence concept.
- The projection consumes only validated MKT-002 evidence and an explicit
  policy; it opens no infrastructure.
- Correction-chain leaves are effective while every historical entry remains
  auditable.
- Reversals remain economic facts and are not confused with corrections.
- Expected and actual facts net exactly within the same controlled stage.
- Absence remains typed and never becomes a synthetic zero.
- Absolute tolerance is explicit for every stage, currency-safe, inclusive,
  and versioned.
- Each line cites the effective entry IDs used in its calculation.
- Aggregate status has deterministic precedence and cannot close an empty
  trace.

## Accepted status semantics

```text
expected only                         -> PENDING
actual only                           -> DIVERGENCE
both within tolerance                 -> FULLY_RECONCILED
same-direction actual progress        -> PARTIALLY_RECONCILED
other mismatch                        -> DIVERGENCE
```

At trace level, divergence wins, all-full closes, all-pending remains pending,
and every other non-divergent mixture is partial.

## Deliberate deferral

The first MKT-003 slice does not persist a mutable status, allocate settlement
lines, match sale directly to bank, group systemic issues, open investigation
or recovery cases, handle returns, emit events, expose an API, or activate an
action.

It also does not introduce percentage tolerance, materiality, source authority,
confidence, model scoring, recommendation, pricing, AI, or Kernel vocabulary.

## Essence preserved

- Genesis remains the Organizational Computing platform.
- Marketplace Intelligence remains a vertical consumer.
- Immutable evidence remains separate from policy-derived judgment.
- Every judgment is reproducible from cited evidence and a policy version.
- Domain and tests precede storage, integrations, UI, and intelligence.
- No reconciliation result has execution authority.

## Sequence preserved

```text
MKT-001 exact economic truth
  -> MKT-002 immutable financial evidence
  -> MKT-003 deterministic reconciliation projection
  -> durable cases / systemic divergence / recovery
  -> net-back pricing and later intelligence
```

## Authorization

Acceptance authorizes TASK-0094 only: the pure reconciliation domain and
focused tests in the existing marketplace application module. It authorizes no
migration, repository, runtime, connector, API, event, workflow, financial
action, pricing, AI, or Kernel change.
