# ADR-0074 — Governed cross-system product identity decisions

## Decision

Cross-system Mercado Livre product to Omie provider-product identity is decided
only by explicit, append-only confirmation or rejection evidence. The relation
is scoped by organization and by both provider connections and names an exact
Mercado Livre item plus seller SKU and exact Omie provider product ID.

Corrections append a decision that supersedes the current decision for the same
relation. Changing the Omie target requires explicitly rejecting the old
relation and explicitly confirming the new relation. Current state is derived
from unsuperseded decisions. More than one current confirmation for a Mercado
Livre identity is a conflict and resolves fail closed.

Existing candidate generation and same-kind within-Omie resolution remain
separate evidence interpretations. Text equality, fuzzy/title/price evidence,
and within-provider exactness never create or imply a cross-system decision.

## Consequences

The authenticated API derives organization from its principal and both
connection IDs from server configuration. Before append, persistence verifies
active provider-scoped connections and the exact durable identities on both
sides. Client decision and correlation UUIDs provide deterministic replay.
Database mutation guards preserve historical decisions.

This authority is identity evidence only. It performs no provider write,
Economic Truth or `PRODUCT_COST` promotion, currency inference, or autonomous
execution.
