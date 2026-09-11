# TASK-0165F.2 Evidence ? Historical Mercado Livre evidence reacquisition

## Production evidence gap

Real-data evaluation on 2026-09-10 showed six Mercado Livre order observations.
All six:

- existed only under `marketplace-economic.order-source`;
- contained one persisted order item;
- contained no persisted `seller_sku`;
- were originally observed on 2026-09-09;
- had `date_last_updated` between 2026-09-09T19:09:01Z and
  2026-09-09T20:19:08Z.

No `marketplace-economic.order-source.reacquisition-v1` observation existed.

The corrected connector already preserves
`order_items[].item.seller_sku`, so the residual gap is historical acquisition,
not identity-policy relaxation.

## Decision

When reacquisition has no durable progress, Mercado Livre starts from a bounded
24-hour historical lookback rather than the normal one-hour lookback.

Normal ingestion remains unchanged.

The reacquisition pipeline is bounded to 48 source pages and the existing
request deadline. New provider observations remain immutable and coexist with
historical observations.

## Safety boundary

This task does not:

- create or confirm cross-system identity;
- change `CommerceIdentityBridge`;
- introduce fuzzy matching;
- mutate Economic Truth;
- write to Mercado Livre;
- reinterpret missing evidence as zero;
- delete or rewrite historical observations.

## Real validation target

The controlled production sample is:

- 2000017953317050
- 2000018336941860
- 2000018351053412
- 2000018365191422
- 2000018365197372
- 2000018370535582

Success is measured first by historical reacquisition observations and recovered
seller-SKU evidence where the provider exposes it, not by artificially raising
identity coverage.
