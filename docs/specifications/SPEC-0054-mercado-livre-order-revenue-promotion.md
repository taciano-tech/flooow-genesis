# SPEC-0054: Mercado Livre Order Revenue Promotion

Status: Accepted

Date: 2026-09-06

Governing ADR: ADR-0055

Implementation task: TASK-0154

## Objective

Promote the smallest safe financial fact from durable Mercado Livre order source
observations into existing independent economic evidence:

```text
REVENUE
```

No other economic component is authorized.

## Existing module

Extend:

```text
applications:marketplace-order-source-promotion
```

Allowed production dependencies remain those already accepted for that module.

No provider-ingestion, authentication, Connector Runtime, API, scheduler or
Kernel dependency may be added.

## Source candidate

A TASK-0154 candidate is identified by the exact V021 parent source key:

```text
organizationId
connectionId
capability = marketplace-economic.order-source
inputProgressVersion
recordOrdinal
```

and carries only:

```text
externalOrderId
currency
totalAmount
dateClosed
observedAt
resolved MarketplaceOrderId / identity state
```

It must not carry or promote:

```text
paidAmount
saleFee
grossPrice
payment amount
shipping
tax
product cost
```

## Eligibility

A V021 source row is eligible only when:

1. `date_closed` is non-null;
2. the canonical V022 order identity exists for the same
   `(organization, mercado-livre, externalOrderId)`;
3. source currency can be checked against the immutable identity currency.

Rows without `date_closed` are not terminally marked by TASK-0154.

A later V021 source observation can become eligible when the provider supplies
`date_closed`.

## Canonical subject

Build from the stored TASK-0153 identity:

```text
MarketplaceEconomicEvidenceSubject(
  organizationId,
  marketplaceOrderId,
  marketplace = mercado-livre,
  externalOrderId,
  currency = identity currency
)
```

If source currency differs from identity currency:

```text
IDENTITY_CONFLICT
```

No economic evidence is applied for that source row.

## Revenue component

Build exactly:

```text
EconomicComponent(
  organizationId = subject.organizationId,
  id = fresh random EconomicComponentId,
  orderId = subject.orderId,
  type = REVENUE,
  direction = ADDITION,
  magnitude = MarketplaceMoney(source currency, source totalAmount),
  source = marketplace source,
  occurredAt = source dateClosed,
  quality = CONFIRMED
)
```

Provider decimal text/BigDecimal semantics must remain exact.

Binary floating-point conversion is forbidden.

## Revenue observation

Wrap the component exactly as:

```text
MarketplaceEconomicComponentObservation(
  id = fresh random observation id,
  subject = subject,
  family = MARKETPLACE_ORDER,
  component = revenue,
  coverageClaim = PARTIAL,
  observedAt = source observedAt
)
```

`COMPLETE` is forbidden in TASK-0154.

## Source

Exactly:

```text
EconomicSource(
  kind = MARKETPLACE,
  systemKey = br.com.mercadolivre,
  externalReference = externalOrderId
)
```

## Identifier factories

Component and evidence observation identifiers are fresh opaque random UUIDs
through injected identifier factories.

No identifier is derived from provider order text.

## Evidence apply

Use only existing:

```text
MarketplaceIndependentEconomicEvidenceRepository.find
MarketplaceIndependentEconomicEvidenceRepository.apply
```

with:

```text
ObserveFact(Component(revenueObservation))
```

Bound stale-version retries to at most 3 re-read/apply cycles.

Mappings:

```text
Applied -> PROMOTED
Duplicate -> DUPLICATE
SourceFactConflict -> EVIDENCE_CONFLICT
StaleVersion -> bounded retry
OrganizationUnavailable -> blocked, no terminal marker
IntegrityFailure -> blocked, no terminal marker
unexpected evidence conflict -> blocked/integrity, no terminal marker
```

No Correct update is emitted.

## Terminal ledger

V023 creates:

```text
marketplace_order_revenue_source_promotion
```

Key exactly:

```text
organization_id
source_connection_id
source_capability
source_input_progress_version
source_record_ordinal
```

Store:

```text
marketplace_order_id
outcome
promoted_at
```

Terminal outcomes exactly:

```text
PROMOTED
DUPLICATE
IDENTITY_CONFLICT
EVIDENCE_CONFLICT
```

The row references:

- the exact V021 source order row;
- the canonical V022 order identity registry.

Rows are append-only/immutable.

Exact terminal replay is harmless.

Different terminal material for the same source key fails closed.

## Pending work set

Expose a bounded pending-revenue query for one organization/connection.

Ordering:

```text
input_progress_version ASC
record_ordinal ASC
```

Limit:

```text
1..1000
```

No second global checkpoint.

Rows with existing TASK-0154 terminal marker are excluded.

Rows without `date_closed` are excluded without terminalizing them.

## Crash semantics

If the process crashes after evidence apply and before terminal marking:

```text
retry
 -> same canonical identity
 -> same source fact
 -> Duplicate
 -> terminal DUPLICATE marker
```

No exactly-once fiction.

## Financial boundary

TASK-0154 must not promote or derive:

- `MARKETPLACE_COMMISSION`;
- `MARKETPLACE_FEE`;
- `SHIPPING`;
- `ADVERTISING`;
- `TAX`;
- `PRODUCT_COST`;
- `FINANCIAL_COST`;
- `OTHER_ADJUSTMENT`.

It must not create payment/settlement identity or reconciliation truth.

## Required tests

1. existing module dependency boundary remains valid;
2. rows without `date_closed` are not eligible;
3. eligible candidate resolves V022 canonical identity;
4. source currency mismatch -> IDENTITY_CONFLICT;
5. totalAmount uses exact decimal semantics;
6. REVENUE type exactly;
7. ADDITION direction exactly;
8. MARKETPLACE_ORDER family exactly;
9. CONFIRMED quality exactly;
10. PARTIAL coverage exactly;
11. COMPLETE coverage is never emitted;
12. occurredAt equals source `date_closed`;
13. observedAt equals durable source observedAt;
14. source kind MARKETPLACE;
15. source system key `br.com.mercadolivre`;
16. source external reference equals external order id;
17. no `paid_amount` is read as revenue;
18. no `sale_fee` is promoted;
19. no shipping/payment/tax/product-cost component is promoted;
20. first promotion -> PROMOTED;
21. same later source revenue -> DUPLICATE;
22. changed total amount under same source identity -> EVIDENCE_CONFLICT;
23. changed dateClosed under same source identity -> EVIDENCE_CONFLICT;
24. no automatic correction;
25. stale evidence version retries boundedly;
26. evidence integrity failure creates no terminal marker;
27. organization unavailable creates no terminal marker;
28. crash-equivalent retry can converge through Duplicate;
29. terminal exact replay harmless;
30. conflicting terminal replay fails closed;
31. V023 is additive after V022;
32. V001-V022 byte content remains unchanged;
33. source rows remain immutable;
34. organization isolation;
35. TASK-0153 regression green;
36. independent economic evidence regression green;
37. Economic Truth assembly regression green;
38. Sales Intelligence regression green;
39. TASK-0152 provider regression green;
40. full build green.

## Exact authorized implementation paths

TASK-0154 may modify/create exactly these seven paths:

1. MODIFY
   `applications/marketplace-order-source-promotion/src/main/kotlin/io/flooow/marketplace/operations/economics/promotion/MarketplaceOrderSourcePromotion.kt`

2. MODIFY
   `applications/marketplace-order-source-promotion/src/test/kotlin/io/flooow/marketplace/operations/economics/promotion/MarketplaceOrderSourcePromotionTest.kt`

3. CREATE
   `applications/marketplace-operations-persistence-postgres/src/main/resources/db/migration/V023__create_marketplace_order_revenue_source_promotion.sql`

4. MODIFY
   `applications/marketplace-operations-persistence-postgres/src/main/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceOrderSourcePromotionRepository.kt`

5. MODIFY
   `applications/marketplace-operations-persistence-postgres/src/test/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceOrderSourcePromotionRepositoryTest.kt`

6. MODIFY only for implementation evidence
   `docs/evidence/TASK-0154-mercado-livre-order-revenue-promotion.md`

7. APPEND exactly one TASK-0154 implementation entry
   `docs/journal/MGI-EXECUTIVE-JOURNAL.md`

No eighth implementation path is authorized.

No build file or settings change is authorized.

## Frozen

No TASK-0154 implementation change to:

- TASK-0152 provider record/connector contracts;
- authentication/OAuth;
- Connector Runtime;
- Integration Control Plane;
- credential rotation;
- Marketplace Operations economic/evidence domain contracts;
- migrations V001-V022;
- Sales Intelligence production code;
- Economic Truth assembler production code;
- Omie ingestion;
- API/UI;
- scheduler/worker;
- Kernel.

If an existing frozen contract is insufficient, implementation stops for a
governance amendment.

## Gates

```text
./gradlew :applications:marketplace-order-source-promotion:test --no-daemon --console=plain
./gradlew :applications:marketplace-operations-persistence-postgres:test --no-daemon --console=plain
./gradlew :applications:marketplace-economic-provider-ingestion:test --no-daemon --console=plain
./gradlew :applications:marketplace-operations:test --no-daemon --console=plain
./gradlew :applications:connector-runtime:test --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
```

Repository CI must pass.

## Completion

TASK-0154 completes after exact scope, source-time, revenue-semantics,
duplicate/conflict, persistence, regression, CI, review and merge gates pass.

Next critical path:

```text
TASK-0155 live pipeline orchestration
-> TASK-0156 Sales Intelligence API
-> TASK-0157 MVP UI
```

Billing/fees, settlement/reconciliation, fiscal intelligence and product cost
remain parallel separately governed work.