# Mercado Livre Order Revenue Promotion Research

Date: 2026-09-06

Status: Concluded for TASK-0154 bounded slice

## Question

What is the smallest Mercado Livre financial source fact that Genesis can promote
after TASK-0153 without inventing fee, settlement, shipping, refund, discount, or
tax semantics?

## Repository facts already established

TASK-0152 durably stores normalized Mercado Livre order observations with:

```text
external order reference
provider status
date_created
date_last_updated
date_closed nullable
currency
total_amount
paid_amount nullable
```

and item/payment child observations including:

```text
quantity
unit_price
sale_fee nullable
gross_price nullable
payment reference/status/amount/timestamps
```

TASK-0153 establishes exactly one canonical internal MarketplaceOrderId for:

```text
organizationId + marketplace=mercado-livre + externalOrderId
```

and promotes only OrderOccurrence.

## Current official Mercado Livre semantics

Official Orders documentation describes:

- `total_amount` as the order total amount;
- `order_items.quantity` as purchased quantity;
- `order_items.unit_price` as the unit price with applicable sale price/discount
  already reflected;
- `order_items.sale_fee` as the sale commission;
- `gross_price` as the original amount the buyer would have paid without
  discounts;
- `date_closed` as the order confirmation date, established when the order first
  changes to confirmed/paid.

Official Billing documentation adds an important distinction:

- order `sale_fee`/`marketplace_fee` is useful for an operational expected-net
  view;
- billing details expose later charge composition, including sale-fee gross/net,
  discount and rebate;
- billing is a post-sale/reconciliation authority.

Official selling-cost documentation further shows that a sale fee can have
internal composition such as percentage, fixed fee and financing-related cost.
Therefore TASK-0154 must not infer Genesis'
`MARKETPLACE_COMMISSION` versus `MARKETPLACE_FEE` from the currently persisted
single order-item `sale_fee` value.

Official references reviewed:

- https://developers.mercadolivre.com.br/pt_br/gerenciamento-de-vendas
- https://developers.mercadolivre.com.br/pt_ar/provisoes
- https://developers.mercadolivre.com.br/pt_br/comissao-por-vender

## Decision

The first financial promotion slice is REVENUE only.

Use the durable order source:

```text
source total_amount
```

as one confirmed marketplace observation of:

```text
EconomicComponentType.REVENUE
EconomicDirection.ADDITION
```

only after canonical TASK-0153 identity is available.

This is not a claim that all seller economics are complete.

The evidence claim is intentionally:

```text
coverageClaim = PARTIAL
quality = CONFIRMED
```

The value is directly observed from the provider, but the order source alone
does not establish complete economic coverage across:

- marketplace-funded or seller-funded discount attribution;
- rebates;
- refunds;
- chargebacks;
- shipping;
- sale-fee composition;
- financing costs;
- tax;
- settlement/release;
- product cost.

## Revenue occurrence time

Use:

```text
occurredAt = source date_closed
```

`date_closed` is the provider confirmation boundary for the order.

Rows with no `date_closed` are not eligible for TASK-0154 promotion.

A later durable source row can become eligible when the provider supplies
`date_closed`.

Do not substitute:

- source `date_created`;
- source `date_last_updated`;
- ingestion time;
- promotion clock.

## Observation time

Use the durable V021 source observation's:

```text
observedAt
```

No promotion clock replaces provider-source observation time.

## Source identity

Use:

```text
EconomicSource(
  kind = MARKETPLACE,
  systemKey = br.com.mercadolivre,
  externalReference = externalOrderId
)
```

The existing economic evidence merger therefore remains the authority for
re-observation semantics.

Same external order REVENUE source + same canonical meaning:

```text
Duplicate
```

Same external order REVENUE source + changed canonical meaning:

```text
SourceFactConflict
```

TASK-0154 does not invent an automatic correction.

## Why `paid_amount` is not REVENUE

`paid_amount` and payment transaction amounts describe payment state/amount.

They are not silently equivalent to economic REVENUE because later work must
separate:

```text
sale
payment
billing
settlement/release
withdrawal
bank receipt
refund
chargeback
```

## Why `sale_fee` is deferred

The current order source preserves `sale_fee`, but Genesis already distinguishes:

```text
MARKETPLACE_COMMISSION
MARKETPLACE_FEE
FINANCIAL_COST
OTHER_ADJUSTMENT
```

The provider's aggregate/operational sale-fee representation is insufficient to
split those Genesis meanings without a governed composition policy.

Billing and settlement research must remain separate authority work.

## Why shipping is deferred

Seller shipping cost has a dedicated shipment-cost authority.

No order/payment shipping shortcut is promoted in TASK-0154.

## Why tax is deferred

Order/payment tax-shaped fields do not establish complete fiscal liability,
payment, recoverability, DIFAL/barrier-fiscal treatment, or later reimbursement.

Tax remains separately governed fiscal evidence.

## Exact safe bridge

```text
V021 Mercado Livre order observation
        +
V022 canonical order identity
        |
        v
eligible when date_closed exists
        |
        v
REVENUE
  direction = ADDITION
  magnitude = total_amount
  currency = source/identity currency
  family = MARKETPLACE_ORDER
  quality = CONFIRMED
  coverage = PARTIAL
  occurredAt = date_closed
  observedAt = source observedAt
  source = MARKETPLACE / br.com.mercadolivre / externalOrderId
        |
        v
existing Independent Economic Evidence
        |
        v
existing Economic Truth assembly
```

## MVP effect

After TASK-0154, live orders can show a source-backed revenue component while
remaining explicitly incomplete for fees, shipping, product cost, tax and
settlement.

This is sufficient to proceed to:

```text
TASK-0155 live pipeline orchestration
TASK-0156 Sales Intelligence API
TASK-0157 MVP UI
```

without waiting for full financial reconciliation or fiscal intelligence.

## Separately governed future authorities

Later slices must independently govern:

1. Mercado Livre billing charge composition;
2. shipment seller cost;
3. discounts/campaign funding;
4. refunds and chargebacks;
5. Mercado Pago release/settlement reports;
6. bank/ERP receipt reconciliation;
7. fiscal documents, taxes and recoverables;
8. Omie/product cost association.

Provider informs; evidence records; domain judges; intelligence derives.