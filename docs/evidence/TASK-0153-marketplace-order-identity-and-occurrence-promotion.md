# TASK-0153: Marketplace Order Identity and OrderOccurrence Promotion

Status: Authorized

Date: 2026-09-06

Governing ADR: ADR-0054

Specification: SPEC-0053

## Objective

Create the first governed bridge from durable marketplace order source
observations into canonical Genesis order identity and independent economic
evidence.

## Acceptance

TASK-0153 is accepted only when:

- exact external marketplace order identity allocates one stable internal UUID;
- connection identity is provenance, not canonical order identity;
- concurrent first allocation converges;
- source currency mismatch fails closed;
- only OrderOccurrence is promoted;
- `date_created` is preserved as occurredAt;
- source observedAt is preserved;
- existing independent evidence repository is the only evidence write authority;
- equal re-observation is Duplicate;
- changed occurrence under the same source identity is explicit conflict;
- no automatic correction occurs;
- no financial amount becomes an EconomicComponent;
- terminal promotion rows are durable/idempotent;
- infrastructure failure does not become a terminal business outcome;
- exactly ten implementation paths change;
- all SPEC-0053 gates and repository CI pass.

## Explicit non-claims

TASK-0153 does not establish:

- canonical revenue;
- canonical marketplace commission/fee;
- payment settlement;
- seller shipping cost;
- tax;
- product cost;
- SKU/product identity;
- complete order history.

## Completion evidence

Implementation evidence is appended here only after code exists and local gates
pass.
## Implementation evidence

Implementation branch:

```text
feat/task-0153-marketplace-order-identity-occurrence-promotion
```

Implemented bridge:

```text
V021 durable Mercado Livre order source
  -> bounded pending work-set
  -> exact organization + marketplace + externalOrderId identity
  -> one durable opaque MarketplaceOrderId
  -> immutable subject currency
  -> source-backed OrderOccurrence
  -> existing independent economic evidence repository
  -> terminal source promotion ledger
```

Implementation preserves the frozen boundary:

- connection identity remains provenance only;
- internal order UUID is never derived from provider text;
- concurrent first allocation converges through durable external-identity
  uniqueness;
- source currency mismatch is terminal IDENTITY_CONFLICT;
- source `date_created` becomes OrderOccurrence `occurredAt`;
- durable source `observedAt` is preserved;
- equal marketplace source occurrence is Duplicate;
- changed occurrence is EVIDENCE_CONFLICT;
- no automatic correction is issued;
- no TASK-0152 monetary value enters the promotion candidate or an
  EconomicComponent;
- infrastructure/integrity failure creates no terminal business marker.

Local SPEC-0053 gates are run before commit and push.

Repository CI, review, and merge remain required.