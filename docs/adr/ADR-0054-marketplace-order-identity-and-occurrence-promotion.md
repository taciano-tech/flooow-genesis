# ADR-0054: Marketplace Order Identity and OrderOccurrence Promotion

Status: Accepted

Date: 2026-09-06

## Context

TASK-0152 now provides durable Mercado Livre order source observations.

The Connector Runtime/provider adapter intentionally does not carry or invent
Genesis' canonical `MarketplaceOrderId`.

Independent economic evidence requires that internal identity before
OrderOccurrence or financial facts can be attached to an economic subject.

## Decision

Introduce a separate application boundary:

```text
applications:marketplace-order-source-promotion
```

It promotes durable provider order source observations into canonical internal
order identity and OrderOccurrence evidence.

## Identity authority

For Mercado Livre source promotion, exact external business identity is:

```text
organizationId
marketplace = mercado-livre
externalOrderId
```

`connectionId` is source provenance and is not part of canonical business
identity.

The internal `MarketplaceOrderId` is an opaque random UUID allocated exactly once
behind durable uniqueness of the external identity tuple.

Do not derive an internal UUID from provider text.

## Currency

The first source observation fixes subject currency in the identity registry.

A later source observation for the same canonical external order with another
currency is `IDENTITY_CONFLICT`.

No automatic currency replacement is authorized.

## First promoted fact

TASK-0153 promotes only:

```text
MarketplaceIndependentEconomicFact.OrderOccurrence
```

with:

```text
occurredAt = source date_created
source.kind = MARKETPLACE
source.systemKey = br.com.mercadolivre
source.externalReference = externalOrderId
observedAt = durable source observedAt
```

Existing economic-evidence duplicate/conflict/correction semantics remain the
only authority.

## Re-observation

Same source identity + same occurrence meaning:

```text
DUPLICATE
```

Same source identity + different occurrence meaning:

```text
EVIDENCE_CONFLICT
```

TASK-0153 does not automatically create a correction.

## Terminal promotion ledger

Persist one task-specific terminal result per V021 order source row:

```text
PROMOTED
DUPLICATE
IDENTITY_CONFLICT
EVIDENCE_CONFLICT
```

Infrastructure, stale-version exhaustion, organization unavailability, or
integrity failure do not become terminal source outcomes.

They remain retryable/blocked application results.

## Transaction boundary

Identity allocation is committed before independent evidence promotion.

Evidence promotion uses the existing economic-evidence repository transaction.

Terminal promotion is marked after `Applied`, `Duplicate`, or explicit conflict.

Therefore a process crash can leave:

```text
identity allocated
evidence already applied
promotion marker absent
```

This is safe. Retry resolves the same identity and existing evidence returns
Duplicate.

No distributed exactly-once claim is made.

## No financial promotion

TASK-0153 must not promote:

- `total_amount`;
- `paid_amount`;
- `sale_fee`;
- payment amount;
- shipping cost;
- tax;
- product cost.

## Consequences

After TASK-0153, a live Mercado Livre order can possess a stable internal
`MarketplaceOrderId` and source-backed canonical OrderOccurrence.

This unlocks later separately governed financial-component promotion and
product/order-cost association without weakening Economic Truth.

## Not authorized

- provider HTTP changes;
- OAuth changes;
- Connector Runtime changes;
- TASK-0152 source mutation;
- automatic evidence corrections;
- revenue/fee/shipping/payment/tax promotion;
- Omie product-cost association;
- product/SKU fuzzy mapping;
- scheduler/worker;
- API/UI;
- Sales Intelligence semantic change;
- Kernel change.