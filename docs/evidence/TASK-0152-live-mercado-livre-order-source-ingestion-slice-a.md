# TASK-0152: Live Mercado Livre Order Source Ingestion Slice A

Status: Authorized

Date: 2026-09-06

Governing ADR: ADR-0053

Specification: SPEC-0052

## Objective

Activate the first real read-only Mercado Livre economic-data acquisition after
TASK-0151 while preserving provider source observations below canonical economic
truth.

## Acceptance

TASK-0152 is accepted only when:

- current OAuth envelope is reused, not reparsed independently;
- exactly one bounded seller-order GET occurs per connector read;
- progress is UTC-hour aligned because the provider discards sub-hour date-filter
  precision;
- finishing one source hour never terminally exhausts the live capability;
- caught-up non-closed hours perform no HTTP call and return bounded
  `REMOTE_TEMPORARY`;
- no inline refresh exists;
- provider order/item/payment source values are strict and exact;
- no buyer/seller PII is modeled or persisted;
- no raw provider JSON is persisted;
- no internal `MarketplaceOrderId` is invented;
- no independent economic evidence is written;
- durable source rows and connector progress commit atomically;
- duplicate replay is harmless and conflicting replay fails closed;
- all SPEC-0052 regression gates pass;
- exactly eleven implementation paths change;
- CI/review/merge complete.

## Explicit non-claims

TASK-0152 does not prove:

- complete order history;
- canonical order identity;
- canonical revenue;
- canonical marketplace commission;
- payment settlement;
- seller shipping cost;
- fiscal tax;
- Economic Truth readiness.

## Completion evidence

Implementation evidence is appended here only after code exists and all local
gates pass.
## Implementation evidence

Implementation branch:

```text
feat/task-0152-live-mercado-livre-order-source-ingestion
```

Implemented source path:

```text
TASK-0151 current OAuth envelope
  -> scoped seller/access-token read
  -> Mercado Livre seller /orders/search
  -> fully closed UTC-hour source window
  -> strict order/item/payment source records
  -> V021 normalized source observations
  -> existing atomic connector page/progress commit
```

Implementation preserves the frozen non-claims:

- no `MarketplaceOrderId` is created;
- no independent economic evidence is written;
- no amount is promoted to an EconomicComponent;
- no source-hour completion becomes canonical completeness;
- no live page uses terminal connector exhaustion;
- caught-up current-hour progress returns bounded REMOTE_TEMPORARY with zero HTTP;
- no buyer/seller PII or raw provider JSON is modeled/persisted;
- no inline OAuth refresh exists;
- no provider write exists.

Local SPEC-0052 gates are run before commit and push.

Repository CI, review, and merge remain required.