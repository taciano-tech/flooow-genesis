# ADR-0067 — Real Commerce Identity Health

Status: Accepted

TASK-0165 authorizes deterministic, read-only evaluation of Mercado Livre and Omie source evidence through the TASK-0162 CommerceIdentityBridge. `IdentityHealth` is derived diagnostics, not Economic Truth, financial reconciliation, recovery validation, authority, or an entitlement. EXACT_CONFIRMED is only a governed relation between source records.

The evaluator is organization-scoped, policy-versioned and reproducible. Candidate, ambiguous, conflict and unresolved relations remain visible and are never auto-confirmed. Real metrics are emitted only when both secure source datasets are available; unavailable data is represented as unavailable, never as zero.
