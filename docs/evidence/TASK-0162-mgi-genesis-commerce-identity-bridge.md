# TASK-0162 — MGI → Genesis Commerce Identity Bridge

## Evidence

Archaeology inspected the local MGI v0.7.6 archive, including `MGI_V0611_GUIDE`,
the v0.5.4 commerce identity contract, v0.5.9 evidence-weighted sales
reconciliation, `sales_reconciliation.py`, `transaction_attribution_resolution.py`
and MGI architecture/data-model guides. The decisive behavior was exact
external-reference matching, exact seller-SKU-required candidates, explicit
ambiguity/conflict, and no automatic persistence.

Genesis promotes that knowledge through ADR-0064/SPEC-0064 and the typed,
deterministic `CommerceIdentityBridge` assessment seam. It preserves the
transaction/product distinction and organization isolation.

This slice intentionally adds no provider adapter, migration, API route, UI
write action, credential handling or write-back because Genesis has no
authorized Omie sales-order evidence committer yet. No real-data evaluation was
performed; no credentials or historical private payloads were read.

TASK-0162 authorizes identity evidence, candidate assessment and governed
confirmation semantics only. It does not authorize Economic Truth mutation,
financial evidence mutation, recovery hypothesis, claim, payment/settlement or
marketplace mutation, provider writes or autonomous action.

Next critical path: TASK-0163 — Durable Recovery Hypothesis + Recoverability
Validation MVP.
