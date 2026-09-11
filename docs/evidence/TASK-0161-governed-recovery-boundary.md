# TASK-0161 — Governed Recovery Boundary Specification

## Evidence

- ADR-0063 and SPEC-0063 establish the chain from evidence through hypothesis,
  validation, proposal and authority to a future execution/outcome loop.
- Value semantics explicitly separate observed, potentially recoverable,
  validated, authorized, executed and actual recovered amounts.
- Evidence and case/signal references are mandatory; raw provider payloads and
  amount-only inference cannot create a hypothesis.
- Duplicate prevention, expiry, retry/unknown-timeout handling and final
  reconciliation are specified before any external action is considered.

## Explicit scope

TASK-0161 authorizes recovery-boundary governance, hypothesis semantics,
recoverability-validation contracts, authority boundaries and a conceptual
lifecycle. It does not authorize provider calls, claim submission, refunds,
disputes, settlement/payment/marketplace mutation, autonomous recovery,
financial execution, or a recoverable-amount assertion.

No production code, migration, endpoint, UI write action or external adapter
was added by design. The next implementation is TASK-0162 — Durable Recovery
Hypothesis + Recoverability Validation MVP.
