# ADR-0066 — Live Omie Transaction Evidence

Status: Accepted

TASK-0164 promotes the MGI-proven Omie `POST /api/v1/produtos/pedido/` `ListarPedidos` read contract into a Genesis connector. The adapter emits typed, organization-scoped source evidence only. Omie evidence is not Economic Truth, a sales projection, reconciliation case, recovery hypothesis, authority, or a write-back instruction.

Stable order references are kept distinct from product references. The connector is read-only, credential-safe, bounded, replay-safe and does not perform matching or confirmation automatically. TASK-0165 owns cross-system health evaluation.
