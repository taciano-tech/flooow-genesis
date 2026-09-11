# ADR-0071 — Durable Commerce Identity Recompute

## Status

Accepted for TASK-0165E.

## Decision

Introduce organization-scoped, read-only ports for Mercado Livre and Omie
transaction identity evidence. PostgreSQL adapters reconstruct only fields
stored in the existing V021/V026 source-evidence tables, with deterministic
ordering, bounded reads, and no writes. Exact replayed observations are
deduplicated by their persisted source fingerprint; distinct Omie identifiers
remain separate and can therefore surface ambiguity or conflict.

`POST /v1/commerce-identity/recompute` loads both durable sources and invokes
the existing `CommerceIdentityHealthEvaluator`. The result is held as a
derived in-process read model for the existing health/relations endpoints; no
mapping, Economic Truth, ledger, provider, or recovery state is mutated.

Missing source fields remain missing. In particular, Mercado Livre item IDs
are not promoted to seller SKUs, and Omie observation timestamps are never
substituted for a missing transaction timestamp.
