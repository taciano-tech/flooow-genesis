# ADR-0065: Durable Recovery Hypothesis and Recoverability Validation MVP

Status: Accepted  
Date: 2026-09-09

## Decision

Authorize the derived, fail-closed chain:

```text
SystemicDivergenceSignal → RecoveryHypothesis → RecoverabilityValidation
→ ValidatedRecoveryCandidate → STOP
```

Only `EXACT_CONFIRMED` Commerce Identity from ADR-0064 may produce a validated
candidate. `CANDIDATE`, `AMBIGUOUS`, `CONFLICT` and `UNRESOLVED` hypotheses are
blocked. Potential and validated amounts require explicit versioned policy and
evidence; no amount is copied from observed divergence and missing is never
zero.

This slice authorizes deterministic domain evaluation and read-only visibility
contracts only. No authority, proposal approval, execution, provider call,
claim, refund, dispute, payment, settlement, marketplace mutation or
autonomous recovery is authorized.
