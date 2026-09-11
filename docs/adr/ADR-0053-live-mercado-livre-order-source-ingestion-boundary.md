# ADR-0053: Live Mercado Livre Order Source Ingestion Boundary

Status: Accepted

Date: 2026-09-06

## Context

TASK-0151 enables safe current Mercado Livre OAuth credential use and refresh.

Genesis now needs its first live Mercado Livre economic-data read.

The current Connector Runtime does not carry a canonical marketplace order UUID
or a complete economic evidence subject. Mercado Livre order search supplies a
provider order identity, not Genesis' internal `MarketplaceOrderId`.

Direct provider-to-canonical evidence would therefore require inventing an
internal subject during transport ingestion.

## Decision

TASK-0152 activates only provider-level Mercado Livre seller-order source
ingestion.

```text
Mercado Livre /orders/search
  -> typed provider order source records
  -> durable normalized source observations
  -> existing durable connector progress
```

It does not directly create independent marketplace economic evidence.

## Authority boundary

Provider adapter may know:

- Mercado Livre order endpoint;
- seller user ID from the current OAuth envelope;
- bearer access token;
- provider query/filter/pagination format;
- provider order/item/payment/shipping source schema;
- provider HTTP failure semantics.

Provider adapter may not own:

- `MarketplaceOrderId`;
- canonical economic subject creation;
- revenue/commission/shipping/tax interpretation;
- payment-to-settlement interpretation;
- product-cost association;
- Economic Truth;
- Sales Intelligence;
- retry scheduling;
- OAuth refresh execution.

## Credential access

The TASK-0151 envelope codec remains the single credential-shape authority.

A narrowly scoped read helper may expose the authorized seller ID and current
access token only inside one caller operation.

No second JSON credential parser is authorized.

Data adapters never write a replacement credential and never invoke the token
endpoint.

A data-call 401 returns `AUTHENTICATION_REQUIRED`; refresh remains a separate
credential-rotation workflow.

## Capability

Freeze:

```text
marketplace-economic.order-source
```

The word `source` is intentional. It does not mean canonical
`MARKETPLACE_ORDER` evidence.

## Source observation semantics

Persist source-level facts only.

Order amount, paid amount, item sale fee, payment amount, provider status, and
source identities remain provider observations.

No amount becomes an `EconomicComponent` in TASK-0152.

No coverage becomes COMPLETE from order-search exhaustion.

No provider shipping amount becomes canonical seller shipping cost.

## Privacy

Do not retain:

- buyer/seller PII;
- address;
- phone;
- email;
- document identifiers;
- comments;
- item title;
- payment reason/card data;
- raw JSON.

Retain only bounded economic values, timestamps, source statuses, and stable
provider identities required for later governed promotion.

## Mutable search and live-progress caveat

Current order search uses offset/limit and is a mutable source view. Current
provider documentation also states that order date filters discard sub-hour
precision.

TASK-0152 therefore uses only fully closed, UTC hour-aligned
`date_last_updated` source windows.

Connector Runtime treats `exhausted=true` as permanently terminal. Because this
capability is a continuous live source, finishing one source hour advances to the
next hour with non-terminal progress; it never exhausts the capability.

When the cursor reaches an hour that has not fully closed, the adapter performs no
provider request and returns bounded `REMOTE_TEMPORARY` until a closed source hour
is available.

A completed hour plus offset paging is still not an exactly-once or completeness
claim. Future notification/reconciliation/overlap coordination is separately
governed.

## Persistence

One additive migration may create normalized Mercado Livre order source tables
under the existing connector page-commit identity.

No second progress store.

No raw provider payload.

No modification of V001-V020.

## Consequences

Genesis can make real read-only Mercado Livre order calls after safe credential
readiness without weakening Economic Truth authority.

The next required semantic step is explicit internal marketplace-order identity
allocation/association and promotion from provider source observations into
independent economic evidence.

## Not authorized

- direct economic evidence promotion;
- internal order UUID invention;
- provider writes;
- shipment-cost endpoint;
- shipping detail endpoint;
- Ads endpoint;
- billing/fiscal endpoint;
- product-cost association;
- notifications/webhooks;
- scheduler;
- automatic retry loop;
- API/UI;
- Economic Truth semantic changes;
- Sales Intelligence semantic changes;
- Kernel changes.