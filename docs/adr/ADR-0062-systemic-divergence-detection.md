# ADR-0062: Systemic Divergence Detection

Status: Accepted

Date: 2026-09-08

## Decision

Add a derived, explainable `SystemicDivergenceSignal` built only from valid
durable reconciliation cases. Grouping is restricted to organization, stage,
currency and versioned policy inside an explicit bounded time window. A signal
requires explicit minimum case count and absolute materiality thresholds; both
are policy data, never hidden constants. Identity is deterministic and
references every supporting case.

The signal is not Economic Truth, evidence, a recoverability assertion, a claim
or recovery authority. This authorizes deterministic analysis, additive
durability and authenticated read-only visibility only. No provider, scheduler,
financial or marketplace mutation, or autonomous action is allowed.
