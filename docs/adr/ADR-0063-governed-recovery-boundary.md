# ADR-0063: Governed Recovery Boundary

Status: Accepted

Date: 2026-09-08

## Context

Flooow now has immutable Economic Truth and financial evidence, deterministic
reconciliation cases, and derived systemic-divergence signals. Those layers
can explain observed divergence but cannot establish that money is owed or
that recovery is possible. A future recovery capability therefore needs an
explicit institutional boundary before any persistence or external adapter is
implemented.

## Decision

Authorize only the following conceptual chain:

```text
evidence -> reconciliation -> systemic pattern -> recovery hypothesis
         -> recoverability validation -> proposal -> authority gate -> execution
         -> provider response -> financial evidence -> actual recovered outcome
         -> reconciliation
```

`RecoveryHypothesis` is derived from one or more valid reconciliation cases or
systemic signals and must retain every supporting case/evidence reference. It
is not Economic Truth, financial evidence, a reconciliation case, a systemic
signal, a claim, a receivable, a settlement, recovery authority, or actual
recovered money.

No stage before the Authority Gate may produce an external effect. Validation,
recommendation, eligibility, potentially recoverable, and authorized are
distinct states. Authority must be explicit, scoped to organization,
currency, subject, amount, action, policy/version and expiry, and auditable.

## Value semantics

The following are distinct typed concepts, never implicit conversions:

- `ObservedDivergenceAmount`: amount evidenced by reconciliation.
- `PotentiallyRecoverableAmount`: hypothesis bounded by explicit policy and
  evidence; it is not an entitlement and may be missing.
- `ValidatedRecoverableAmount`: result of a successful recoverability
  validation with complete evidence and applicable rules; never copied from
  observed or potential values.
- `AuthorizedRecoveryAmount`: amount explicitly approved by the authority
  gate; never inferred from validation.
- `ExecutedRecoveryAmount`: amount actually submitted/accepted by an execution
  boundary; never inferred from authorization.
- `ActualRecoveredAmount`: amount evidenced as received and reconciled;
  never inferred from execution or provider acknowledgement.

Missing is distinct from zero at every stage. No later value may be generated
from an earlier value without an explicit policy, evidence and validation
decision.

## Non-goals

This ADR authorizes no provider call, claim submission, refund, dispute,
settlement/payment/marketplace mutation, autonomous recovery, financial
execution, recoverable-amount assertion, scheduler or UI write action.
