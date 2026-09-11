# ADR-0055: Mercado Livre Order Revenue Promotion Boundary

Status: Accepted

Date: 2026-09-06

## Context

TASK-0152 supplies durable normalized Mercado Livre order source observations.

TASK-0153 supplies canonical internal marketplace-order identity and source-backed
OrderOccurrence evidence.

The MVP now needs one financial fact that can be promoted without confusing:

```text
order
payment
billing
settlement
shipping
tax
product cost
```

## Decision

Authorize one bounded financial promotion slice:

```text
Mercado Livre durable order total_amount
    -> independent REVENUE evidence
```

No other financial component is promoted by TASK-0154.

## Existing application boundary

Reuse:

```text
applications:marketplace-order-source-promotion
```

Do not create another provider-specific financial application module for this
slice.

The module remains provider-source-to-domain promotion logic and must not call
the provider remotely.

## Canonical subject authority

TASK-0154 must use the identity established by TASK-0153:

```text
organizationId
marketplace = mercado-livre
externalOrderId
-> stored MarketplaceOrderId + immutable currency
```

No new order identity allocation mechanism is introduced.

## Revenue semantics

Create exactly:

```text
EconomicComponentType.REVENUE
EconomicDirection.ADDITION
```

with magnitude:

```text
source total_amount
```

and:

```text
family = MARKETPLACE_ORDER
quality = CONFIRMED
coverageClaim = PARTIAL
```

`PARTIAL` is mandatory.

TASK-0154 does not claim complete revenue/economic coverage from the Orders API.

## Temporal semantics

A source row is eligible only when `date_closed` exists.

Use:

```text
component.occurredAt = source date_closed
observation.observedAt = durable source observedAt
```

No ingestion/promotion clock replaces either source time.

## Source semantics

Use:

```text
kind = MARKETPLACE
systemKey = br.com.mercadolivre
externalReference = externalOrderId
```

Existing independent evidence source-fact semantics remain sovereign.

## Re-observation

Same source identity and same REVENUE canonical meaning:

```text
DUPLICATE
```

Same source identity with changed amount, currency, occurrence time, direction,
type, source or other canonical component meaning:

```text
EVIDENCE_CONFLICT
```

No automatic correction is authorized.

## Payment is not revenue

TASK-0154 must not use:

- `paid_amount`;
- payment `transaction_amount`;
- payment status;
- payment release date;

as REVENUE substitutes or settlement truth.

## Sale fee is deferred

TASK-0154 must not promote current `sale_fee` into:

- MARKETPLACE_COMMISSION;
- MARKETPLACE_FEE;
- FINANCIAL_COST;
- OTHER_ADJUSTMENT.

Official provider documentation exposes additional sale-fee composition and
billing-time discount/rebate semantics.

That authority must be separately governed.

## Shipping is deferred

TASK-0154 does not promote shipping.

Seller shipping cost requires the dedicated shipment-cost source.

## Tax is deferred

TASK-0154 does not promote tax.

Fiscal liability, tax paid by marketplace, DIFAL/barrier-fiscal payment,
recovery/ressarcimento and settlement remain separate evidence authorities.

## Persistence

Add one TASK-0154 terminal promotion ledger in V023.

Do not alter V001-V022.

No second global checkpoint.

## Crash/retry model

The sequence is:

```text
read eligible durable source
resolve existing canonical identity
apply existing independent evidence
mark TASK-0154 source terminal
```

A crash can leave evidence applied but terminal marker absent.

Retry is safe because the existing evidence merger returns Duplicate for the
same source fact/canonical meaning.

No distributed exactly-once claim is made.

## Consequences

After TASK-0154, the MVP can display:

- live order identity;
- source-backed OrderOccurrence;
- source-backed REVENUE;
- explicit incomplete Economic Truth.

Fees, shipping, cost, tax and settlement remain visibly missing rather than
invented.

## Not authorized

- provider HTTP changes;
- OAuth changes;
- Connector Runtime changes;
- Control Plane changes;
- TASK-0152 source mutation;
- TASK-0153 identity semantics change;
- automatic correction;
- fee/commission promotion;
- shipping promotion;
- payment/settlement promotion;
- tax promotion;
- product-cost association;
- scheduler/orchestration;
- API/UI;
- Kernel change.