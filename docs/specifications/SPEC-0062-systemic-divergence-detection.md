# SPEC-0062: Systemic Divergence Detection

Status: Accepted

`SystemicDivergencePolicy(version, window, minimumCases,
minimumAbsoluteDifference)` is explicit and versioned. The detector receives
only organization-scoped `DurableReconciliationCase` values and a supplied
evaluation timestamp. It emits one deterministic signal per stage/currency/
policy identity when all members are within the window and thresholds are met.

Signals persist with organization boundary, unique identity, revision guard,
case references and restart-safe timestamps. Read-only endpoints are
`GET /v1/reconciliation/systemic-divergences` and
`GET /v1/reconciliation/systemic-divergences/{signalId}`. Browser requests do
not carry organization identifiers.

Systemic divergence != individual divergence != Economic Truth != financial
evidence != recoverable amount != recovery authority.
