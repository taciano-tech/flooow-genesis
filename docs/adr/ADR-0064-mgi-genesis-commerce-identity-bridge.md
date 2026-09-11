# ADR-0064: MGI → Genesis Commerce Identity Bridge

Status: Accepted  
Date: 2026-09-08

## Decision

Promote validated MGI identity resolution through an explicit Genesis bridge:

```text
Identity Evidence → Candidate Relation → Match Assessment
→ Governed Confirmation → Durable Commerce Identity
```

`CommerceIdentity` is derived identity evidence. It is not Economic Truth,
financial evidence, reconciliation, a recovery hypothesis, or authority.

Transaction identity and product identity remain separate. Mercado Livre
`order_id`, `pack_id`, `shipment_id` and `item_id` are transaction evidence;
`seller_sku` is product/offer evidence. Omie product identifiers and
`codigo_pedido_integracao`/`numero_pedido_cliente` are distinct ERP evidence.
SKU, item id, amount, date or title alone never proves transaction identity.

MGI exact external-reference matching is adopted. Evidence-weighted candidates
are adapted to organization-scoped typed evidence and versioned policy. MGI
endpoint architecture, SQLite persistence, automatic suggestion persistence,
secrets, tokens and fuzzy/autonomous behavior are rejected as Genesis authority.

Only one unambiguous declared external reference may be assessed as
`EXACT_CONFIRMED`; candidates remain `CANDIDATE`, competing candidates are
`AMBIGUOUS`, contradictory declared references are `CONFLICT`, and absent
evidence is `UNRESOLVED`. Confirmation is a separate governed action.

This ADR authorizes the pure deterministic bridge contract and provider-free
assessment seam only. No provider write-back, Economic Truth mutation,
recovery hypothesis, claim, payment/settlement/marketplace mutation or
autonomous action is authorized.
