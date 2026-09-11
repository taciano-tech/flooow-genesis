# SPEC-0063: Governed Recovery Boundary

Status: Accepted

Source decision: ADR-0063

## 1. Recovery hypothesis contract

`RecoveryHypothesis` is a derived proposal input with:

- organization-scoped subject and currency;
- one or more reconciliation-case and/or systemic-signal identifiers;
- complete evidence references for each supporting assertion;
- explicit policy/version and hypothesis revision;
- observed amount and optional potentially recoverable amount as separate
  fields;
- no lifecycle state implying entitlement, debt, receivable or authority.

An empty reference set is invalid. A hypothesis cannot be constructed directly
from raw provider payloads or from a number alone.

## 2. Recoverability validation contract

Future validation must explicitly evaluate: eligibility; marketplace/provider
rule and version; claim/time window; evidence completeness; already refunded,
reversed or recovered state; duplicate-recovery identity; currency; stage and
category; and required operator, legal or accounting review. Any confidence or
completeness field must have defined semantics and policy/version. No numeric
threshold is implicit.

Validation output is neither authority nor execution. A failed, incomplete or
expired validation yields missing or ineligible semantics, never zero or a copied
amount.

## 3. Authority and execution

The only permitted future order is:

```text
RecoveryHypothesis -> RecoveryValidation -> RecoveryProposal
-> RecoveryAuthorityDecision -> RecoveryExecution -> RecoveryOutcome
```

`recommendation != authority`, `validation != authority`,
`recoverable != authorized`, `authorized != executed`, and
`executed != recovered`. The Authority Decision must be explicit, human or
otherwise governed, organization-scoped, policy-bound, auditable, expirable,
and idempotency-bound before any external effect.

## 4. Identity and outcome

Future duplicate prevention must use a deterministic identity over organization,
subject/case set, policy/version and action scope. A retry of the same execution
must reuse that identity and an explicit attempt/version; timeout is unknown,
not permission to repeat blindly. A revised hypothesis is a new revision of
institutional memory, not a new financial obligation.

Recovery is not complete when an action is sent or accepted. Completion requires
execution attempt, provider response, independent financial evidence, an actual
recovered outcome and reconciliation back to the canonical ledger/evidence.

## 5. Conceptual future model

The next implementation may introduce `RecoveryHypothesis`,
`RecoveryValidation`, `RecoveryProposal`, `RecoveryAuthorityDecision`,
`RecoveryExecution` and `RecoveryOutcome`. This specification intentionally
does not authorize tables, migrations, endpoints, provider adapters or writes.

## 6. Decision Room semantics

The future read surface may show `Observed impact`, `Potential recovery`,
`Validated recovery`, `Authorized`, `In execution`, `Recovered` and
`Unresolved`. Missing values stay missing. Potential recovery must be labelled
as a hypothesis and never displayed as recovered or recoverable cash.

TASK-0161 authorizes the boundary, semantics, validation contract, authority
gate and conceptual lifecycle only. It authorizes no external or financial
action.
