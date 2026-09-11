# SPEC-0053: Marketplace Order Identity and OrderOccurrence Promotion

Status: Accepted

Date: 2026-09-06

Governing ADR: ADR-0054

Implementation task: TASK-0153

## Objective

Promote durable Mercado Livre V021 order source observations into:

1. exactly one stable Genesis `MarketplaceOrderId` per exact external marketplace
   order identity; and
2. source-backed independent `OrderOccurrence` evidence.

No financial component is promoted.

## New module

Create:

```text
applications:marketplace-order-source-promotion
```

Allowed production dependencies:

```text
applications:marketplace-operations
applications:integration-control-plane
platform:foundation:organization-context
```

Forbidden production dependencies:

- Connector Runtime;
- marketplace economic provider ingestion;
- marketplace provider authentication;
- Postgres/JDBC/Flyway;
- API/server;
- scheduler;
- Kernel direct dependency.

## Source key

A durable promotion candidate is identified by:

```text
organizationId
connectionId
capability = marketplace-economic.order-source
inputProgressVersion
recordOrdinal
```

It carries only normalized values needed by this task:

```text
marketplace = mercado-livre
externalOrderId
currency
dateCreated
observedAt
```

It does not carry item/payment money.

## Canonical order identity key

Exactly:

```text
organizationId
marketplace
externalOrderId
```

`connectionId` must not participate in canonical identity uniqueness.

## Identity registry

V022 creates an additive registry equivalent to:

```text
marketplace_order_identity_registry
```

Required fields:

```text
organization_id
marketplace_key
external_order_id
marketplace_order_id
currency
allocated_at
first_source_connection_id
first_source_capability
first_source_input_progress_version
first_source_record_ordinal
```

Constraints:

- primary/unique internal order identity is organization-scoped;
- unique `(organization_id, marketplace_key, external_order_id)`;
- immutable row after allocation;
- first-source FK references the exact V021 parent order observation;
- marketplace is bounded canonical text;
- currency is exactly 3 uppercase letters;
- no PII/raw JSON/provider credential.

The implementation must use insert-if-absent plus winner read, not last-write-wins.

## Identity resolution

Public result is equivalent to:

```text
Resolved(orderId, allocatedNow)
Conflict(existingOrderId)
Unavailable
```

Rules:

- exact same external key + same currency -> same stored orderId;
- exact same external key + different currency -> Conflict;
- concurrent allocators converge to one stored orderId;
- proposed losing UUID is discarded;
- no identity replacement in TASK-0153.

## Promotion ledger

V022 also creates an additive task-specific ledger equivalent to:

```text
marketplace_order_occurrence_source_promotion
```

Key:

```text
organization_id
source_connection_id
source_capability
source_input_progress_version
source_record_ordinal
```

Terminal outcomes exactly:

```text
PROMOTED
DUPLICATE
IDENTITY_CONFLICT
EVIDENCE_CONFLICT
```

Store:

```text
marketplace_order_id
outcome
promoted_at
```

The row references both the V021 source row and the identity registry.

Exact replay is harmless. Different terminal material for the same source key
fails closed.

## Pending work-set

Repository exposes a bounded pending-source query for one
organization/connection.

It selects V021 parent source observations with no terminal TASK-0153 promotion
row.

Ordering:

```text
input_progress_version ASC
record_ordinal ASC
```

Limit is bounded to 1..1000.

No second global checkpoint is introduced.

## Evidence subject

Build exactly:

```text
MarketplaceEconomicEvidenceSubject(
  organizationId = candidate.organizationId,
  orderId = resolved internal order id,
  marketplace = mercado-livre,
  externalOrderId = candidate external order id,
  currency = candidate source currency
)
```

The identity registry has already frozen that external identity/currency pair.

## OrderOccurrence source

Build:

```text
EconomicSource(
  kind = MARKETPLACE,
  systemKey = br.com.mercadolivre,
  externalReference = candidate external order id
)
```

and:

```text
MarketplaceEconomicOrderOccurrenceObservation(
  subject = subject,
  source = source,
  occurredAt = candidate.dateCreated,
  observedAt = candidate.observedAt
)
```

No promotion-clock timestamp replaces source `observedAt`.

## Observation ID

Use an injected identifier factory with random UUID default.

A new identifier on retry is safe because the existing evidence merger treats
same marketplace source key + same canonical OrderOccurrence meaning as
Duplicate.

No provider-derived deterministic UUID is authorized.

## Evidence apply

Use only existing:

```text
MarketplaceIndependentEconomicEvidenceRepository.find
MarketplaceIndependentEconomicEvidenceRepository.apply
```

Algorithm:

```text
read current evidence
  -> ZERO if not found
  -> current version if found
apply ObserveFact(OrderOccurrence)
```

Bound stale-version retry to at most 3 re-read/apply cycles.

Mappings:

```text
Applied -> PROMOTED
Duplicate -> DUPLICATE
SourceFactConflict -> EVIDENCE_CONFLICT
StaleVersion -> bounded retry
OrganizationUnavailable -> blocked/unavailable, no terminal marker
IntegrityFailure -> blocked/integrity, no terminal marker
unexpected evidence conflict -> blocked/integrity, no terminal marker
```

## Batch application service

Expose one bounded operation equivalent to:

```text
promotePending(organizationId, connectionId, limit)
```

It:

- reads at most `limit` pending candidates;
- processes them in repository order;
- continues after terminal identity/evidence conflicts because each is durably
  visible;
- stops and returns Blocked on infrastructure/integrity failure;
- performs no sleep;
- performs no provider call;
- performs no scheduling.

## Exact semantic boundary

TASK-0153 may make canonical `occurredAt` resolvable through existing Economic
Truth assembly.

It must not create any `EconomicComponent`.

No source amount from TASK-0152 is even present in the promotion candidate
contract.

## Required tests

1. new module dependency guard;
2. exact canonical identity key excludes connection;
3. same external identity across two source connections resolves same orderId;
4. concurrent first allocation converges to one orderId;
5. different currency for same external identity -> IDENTITY_CONFLICT;
6. proposed UUID is not derived from provider order id;
7. pending query excludes already terminally processed source rows;
8. pending query preserves version/ordinal order;
9. exact promotion ledger replay is harmless;
10. conflicting terminal replay fails closed;
11. first source FK is exact V021 parent;
12. service builds marketplace key `mercado-livre`;
13. service builds source system key `br.com.mercadolivre`;
14. occurredAt equals source `date_created`;
15. observedAt equals durable source observedAt;
16. first evidence promotion -> PROMOTED;
17. same later source occurrence -> DUPLICATE;
18. changed date_created under same source identity -> EVIDENCE_CONFLICT;
19. no automatic correction is emitted;
20. stale evidence version retries boundedly;
21. evidence integrity failure creates no terminal promotion marker;
22. organization unavailable creates no terminal marker;
23. crash-equivalent retry after evidence success can finish as DUPLICATE;
24. no financial component type exists in promotion candidate/service;
25. V022 contains no raw JSON/token/secret/PII columns;
26. V022 applies additively after V021;
27. source rows remain immutable;
28. identity survives restart;
29. promotion ledger survives restart;
30. organization isolation;
31. TASK-0152 regression green;
32. independent evidence repository regression green;
33. Economic Truth assembler regression green;
34. Sales Intelligence regression green;
35. full build green.

## Exact authorized implementation paths

TASK-0153 may modify/create exactly these ten paths:

1. MODIFY `settings.gradle.kts`

2. CREATE
   `applications/marketplace-order-source-promotion/build.gradle.kts`

3. CREATE
   `applications/marketplace-order-source-promotion/src/main/kotlin/io/flooow/marketplace/operations/economics/promotion/MarketplaceOrderSourcePromotion.kt`

4. CREATE
   `applications/marketplace-order-source-promotion/src/test/kotlin/io/flooow/marketplace/operations/economics/promotion/MarketplaceOrderSourcePromotionTest.kt`

5. MODIFY
   `applications/marketplace-operations-persistence-postgres/build.gradle.kts`

6. CREATE
   `applications/marketplace-operations-persistence-postgres/src/main/resources/db/migration/V022__create_marketplace_order_identity_and_occurrence_promotion.sql`

7. CREATE
   `applications/marketplace-operations-persistence-postgres/src/main/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceOrderSourcePromotionRepository.kt`

8. CREATE
   `applications/marketplace-operations-persistence-postgres/src/test/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceOrderSourcePromotionRepositoryTest.kt`

9. MODIFY only for implementation evidence
   `docs/evidence/TASK-0153-marketplace-order-identity-and-occurrence-promotion.md`

10. APPEND exactly one TASK-0153 implementation entry
    `docs/journal/MGI-EXECUTIVE-JOURNAL.md`

No eleventh implementation path is authorized.

## Frozen

No TASK-0153 production change to:

- TASK-0152 provider connector/record contracts;
- authentication/OAuth;
- Connector Runtime;
- Integration Control Plane;
- credential rotation;
- Marketplace Operations economic domain/evidence contracts;
- existing V001-V021 migrations;
- Sales Intelligence;
- Economic Truth assembler;
- Omie ingestion;
- API/UI;
- scheduler/worker;
- Kernel.

If an existing frozen contract is insufficient, stop for governance amendment.

## Gates

```text
./gradlew :applications:marketplace-order-source-promotion:compileKotlin --no-daemon --console=plain
./gradlew :applications:marketplace-order-source-promotion:compileTestKotlin --no-daemon --console=plain
./gradlew :applications:marketplace-order-source-promotion:test --no-daemon --console=plain
./gradlew :applications:marketplace-operations-persistence-postgres:test --no-daemon --console=plain
./gradlew :applications:marketplace-economic-provider-ingestion:test --no-daemon --console=plain
./gradlew :applications:marketplace-operations:test --no-daemon --console=plain
./gradlew :applications:connector-runtime:test --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
```

Repository CI must pass.

## Completion

TASK-0153 completes after exact scope, identity-concurrency, evidence-promotion,
terminal-conflict, persistence, regression, CI, review, and merge gates pass.

Next: separately research and govern marketplace financial-component promotion
from TASK-0152 source amounts, then product/order-cost association.