# TASK-0165F Evidence — Real Cross-System Identity Evidence Enrichment

## Before measurement

- Mercado Livre inspected: 6
- Omie persisted: 132
- Omie identity-evaluable: 117
- Omie non-evaluable: 15
- EXACT_CONFIRMED: 0
- CANDIDATE: 0
- UNRESOLVED: 6
- coverage: 0.00%

## Root causes found

The Omie parser read `numero_pedido_cliente` only from `cabecalho`, while the
documented and MGI-proven `ListarPedidos` response places it in
`informacoes_adicionais`. It also looked for products directly under `det[]`
instead of `det[].produto`, and looked for totals in the header instead of
`total_pedido.valor_total_pedido`. These path mismatches explain the observed
zero customer references, zero products, and zero amounts. Currency is not
present in the supported ListarPedidos evidence and remains missing.

The Mercado Livre payload exposes `order_items[].item.seller_sku`; V021 did not
have a field for it. Migration V027 adds nullable typed seller-SKU evidence
without changing existing item/order/pack/shipment semantics.

`codigo_pedido_integracao` remains Omie integration evidence only and is never
interpreted as a Mercado Livre order ID. No explicit marketplace-order
reference was found in the supported provider contract, so none is populated.

The existing identity policy is unchanged. Evidence enrichment is not matching
relaxation, and no Economic Truth, recovery, authority, or provider mutation is
introduced.
