# SPEC-0072 — Real Cross-System Identity Evidence Enrichment

Omie normalization accepts the documented `ListarPedidos` shape:
`cabecalho.codigo_pedido_integracao`,
`informacoes_adicionais.numero_pedido_cliente`,
`det[].produto.codigo_produto_integracao` (and supported product-code aliases),
`det[].produto.quantidade`, and `total_pedido.valor_total_pedido`.
Currency remains missing unless explicitly provided by Omie.

Mercado Livre order items preserve `item.id` and `item.seller_sku` separately.
Seller SKU is product evidence, never transaction identity. No value/date/SKU
heuristic is promoted to `EXACT_CONFIRMED`; existing identity policy and
organization/connection boundaries remain unchanged.
