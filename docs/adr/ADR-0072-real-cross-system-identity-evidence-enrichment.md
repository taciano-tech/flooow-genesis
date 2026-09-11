# ADR-0072 — Real Cross-System Identity Evidence Enrichment

## Decision

TASK-0165F corrects provider-shape extraction without relaxing
`CommerceIdentityBridge`. Omie `ListarPedidos` fields are read from their
documented nested paths (`informacoes_adicionais`, `det[].produto`, and
`total_pedido`). Mercado Livre `item.seller_sku` is preserved as typed,
durable product evidence distinct from `item_id`.

Seller SKU evidence is added through additive migration V027. Existing V021
columns are not reinterpreted, and existing durable evidence is not rewritten.
No provider field is treated as a Mercado Livre transaction identity unless
the provider explicitly declares that relation.

## Boundaries

This is read-only evidence enrichment. It does not change matching thresholds,
confirm mappings, Economic Truth, recovery, authority, or provider writes.
Missing values remain missing and replay remains organization/connection scoped
and deterministic.
