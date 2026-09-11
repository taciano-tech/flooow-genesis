# Mercado Livre Live Order Source Ingestion Research

Status: Research complete for TASK-0152 governance

Date: 2026-09-06

## Question

What is the narrowest safe first live read-only Mercado Livre economic-data slice
after TASK-0151, given that Connector Runtime has provider credential/progress
context but does not carry a canonical `MarketplaceOrderId` or a complete
`MarketplaceEconomicEvidenceSubject`?

## Repository baseline

TASK-0151 provides:

```text
br.com.mercadolivre
  -> SecretVault-held OAuth envelope
  -> access-token readiness assessment
  -> single-use refresh fencing through TASK-0150
  -> real one-attempt refresh adapter
```

Existing provider ingestion already proves the safe Omie pattern:

```text
provider page
  -> typed provider-level source record
  -> durable normalized source observation
  -> durable connector progress
  -> later separately governed association/promotion
```

Connector Runtime remains provider-neutral.

## Official Mercado Livre order source

Current official documentation confirms seller order search:

```text
GET https://api.mercadolibre.com/orders/search?seller={SELLER_ID}
Authorization: Bearer {ACCESS_TOKEN}
```

Primary references:

```text
https://developers.mercadolivre.com.br/pt_br/gerenciamento-de-vendas
https://developers.mercadolivre.com.br/pt_br/pedidos-e-opinioes
```

The current source exposes, among other fields:

```text
id
status
date_created
date_closed
date_last_updated
currency_id
total_amount
paid_amount
pack_id
order_items[]
payments[]
shipping.id
```

Current docs also expose filters including:

```text
order.date_last_updated.from
order.date_last_updated.to
order.date_created.from
order.date_created.to
order.date_closed.from
order.date_closed.to
```

Search responses use paging with `offset` and `limit`.

The seller order resource is documented as retaining orders for up to 12 months.

## Provider search is not a canonical completeness proof

The order search is a mutable provider view.

Current docs describe seller ordering primarily by `date_closed`, while
`date_last_updated` is available as a filter. No current official order-search
contract was found that provides an immutable snapshot cursor or a search-after
cursor tied to `date_last_updated`.

Therefore:

```text
orders/search progress
  !=
proof that Genesis has every order/update
```

A bounded offset/date-window retrieval can provide durable source observations,
but it must not set canonical evidence coverage to COMPLETE merely because a page
or window was exhausted.

Later orchestration may add bounded overlap, reconciliation, notifications, or
another provider-supported change source. TASK-0152 does none of those.

## Critical identity boundary

A Mercado Livre order response explicitly identifies the provider order:

```text
external order id
currency
provider timestamps
provider item/payment/shipping identities
```

It does not contain Genesis' internal canonical UUID:

```text
MarketplaceOrderId
```

The current Connector Runtime invocation does not supply that UUID and does not
supply a complete economic evidence subject.

Therefore TASK-0152 must not:

- invent a `MarketplaceOrderId`;
- derive a UUID from the provider order id;
- create a canonical economic subject;
- write directly to `MarketplaceIndependentEconomicEvidence`;
- mark Economic Truth ready;
- treat source currency as a cross-system currency adjudication.

The correct first live Mercado Livre slice is provider-level source ingestion.

## Slice A

```text
Mercado Livre seller order search
  -> typed MercadoLivreOrderSourceRecord
  -> durable normalized provider order observation
  -> durable connector progress
```

A later separately governed marketplace-order identity promotion stage may:

```text
provider order source observation
  -> explicit internal order identity allocation/association
  -> MarketplaceEconomicEvidenceSubject
  -> order occurrence / order component / payment identity promotion
```

## Source fields retained

TASK-0152 should retain only bounded non-PII source economics and identities
needed by later promotion.

Order level:

```text
externalOrderReference
providerStatus
dateCreated
dateLastUpdated
dateClosed nullable
currency
totalAmount
paidAmount nullable
packReference nullable
shippingReference nullable
observedAt
```

Order item level:

```text
itemReference
variationReference nullable
quantity
unitPrice
currency
saleFee nullable
grossPrice nullable
```

Payment level:

```text
paymentReference
providerStatus
transactionAmount
currency
dateCreated nullable
dateLastModified nullable
```

No buyer/seller name, email, address, phone, document, comments, item title,
payment reason, card data, or raw provider JSON is retained.

`shippingReference` is identity only. The order response's shipping-related
amounts are not accepted as canonical seller shipping cost in Slice A.

`totalAmount`, `paidAmount`, and `saleFee` remain source observations, not
canonical REVENUE, settlement, or commission components.

## Credential read boundary

TASK-0151's envelope codec is the authority for Mercado Livre credential shape.

TASK-0152 may add one narrow scoped read helper to that existing envelope codec so
the provider adapter can obtain:

```text
authorizedUserId
accessToken
```

for one provider request.

Rules:

- no second credential parser;
- no duplicate envelope schema;
- no refresh logic in provider ingestion;
- no token persistence outside SecretVault;
- no token in progress, source observation, exception, log, or test output;
- helper rendering remains redacted;
- the adapter must not retain credential material after the request scope.

## Capability

Freeze provider-level capability:

```text
marketplace-economic.order-source
```

This capability names source acquisition only. It does not assert canonical
`MARKETPLACE_ORDER` evidence.

## Pagination/progress

V1 progress is opaque to Connector Runtime and provider-specific.

Current Mercado Livre documentation states that order date filters use only hour
granularity and discard minutes, seconds, and milliseconds. The source cursor
must therefore never claim sub-hour retrieval precision.

The Connector Runtime also treats `exhausted=true` as terminal: once durable
progress is exhausted, the adapter is never invoked again for that
connection/capability.

TASK-0152 is a live continuous source capability, so progress must represent
closed UTC source hours rather than a terminal finite scan.

Equivalent V1 state:

```text
windowFromHour
offset
```

with:

```text
windowToHour = windowFromHour + 1 hour
```

Rules:

- UTC boundaries are exactly aligned to `HH:00:00Z`;
- first invocation seeds the immediately preceding fully closed UTC hour;
- historical backfill is not part of Slice A;
- each invocation performs at most one remote request;
- provider page limit is bounded by ConnectorBudget and a conservative provider
  maximum;
- all pages in one source hour keep the same hour and advance only `offset`;
- final page of one closed hour advances progress to the next hour at offset 0;
- normal TASK-0152 pages never set `ConnectorPage.exhausted=true`;
- if progress catches up to an hour that is not yet fully closed, the adapter
  performs no remote call and returns bounded `REMOTE_TEMPORARY`;
- hour-boundary duplication is acceptable source observation behavior and is not
  silently promoted into canonical truth;
- progress never contains access token, seller credential, provider body, internal
  order UUID, or economic decision;
- completing a source hour never means canonical evidence completeness.

The seller order search remains a mutable offset view. No hourly progress state
is an exactly-once or completeness proof.

## HTTP/failure boundary

Read-only GET only.

Use current access token through the TASK-0151 credential helper.

Map provider/transport failures to existing Connector Runtime failure taxonomy.

A 401 from a data call maps `AUTHENTICATION_REQUIRED`; the provider data adapter
does not refresh inline. CredentialRotationExecutor remains the refresh workflow.

No internal retry, sleep, scheduler, or loop.

## Persistence

Authorize one additive migration:

```text
V021__create_mercado_livre_order_source_observation.sql
```

It may create normalized parent/child source-observation tables for:

```text
order
order_item
payment
```

Every row remains scoped by the ConnectorPageCommitKey identity:

```text
organization_id
connection_id
capability
input_progress_version
record_ordinal
```

Child ordinals are bounded.

The source observation references the existing durable connector page commit and
is not a second progress store.

Raw provider JSON is forbidden.

## MGI archaeology

Historical MGI proved Mercado Livre OAuth and order/provider reads operationally
useful. Reuse endpoint and failure learnings only when they match the current
official contract.

Do not copy:

- `.mgi` files;
- old tokens/secrets;
- local SQLite authority;
- old universal connector abstractions;
- old economic calculations;
- implicit order identity;
- provider payload dumps.

## Conclusion

TASK-0152 should activate real Mercado Livre seller-order acquisition without
pretending that a provider order has already become Genesis economic truth.

The safe sequence is:

```text
TASK-0151 OAuth refresh
  -> TASK-0152 durable ML order source observations
  -> later explicit order identity/promotion
  -> independent economic evidence
  -> Economic Truth / Sales Intelligence
```