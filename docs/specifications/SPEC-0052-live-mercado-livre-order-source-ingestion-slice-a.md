# SPEC-0052: Live Mercado Livre Order Source Ingestion Slice A

Status: Accepted

Date: 2026-09-06

Governing ADR: ADR-0053

Implementation task: TASK-0152

## Objective

Implement one production-capable but production-inactive read-only Mercado Livre
seller-order source connector and durable normalized source observation commit.

No canonical marketplace economic evidence is created.

## Dependencies

Existing module:

```text
applications:marketplace-economic-provider-ingestion
```

may add a production dependency on:

```text
applications:marketplace-provider-authentication
```

only for the governed Mercado Livre credential read helper.

The authentication module may be modified only to add that scoped read helper and
its test.

No provider ingestion dependency is added back into authentication.

## Capability

Exactly:

```text
marketplace-economic.order-source
```

Provider descriptor remains:

```text
br.com.mercadolivre
```

## Credential helper

Add a scoped API equivalent to:

```text
withReadAccess(
  credentialBytes,
  operation: (authorizedUserId, accessToken) -> T
)
```

Requirements:

- decode through the existing TASK-0151 envelope codec;
- fail closed on malformed envelope;
- do not persist token;
- do not render token;
- do not expose refresh token/client secret;
- caller must not retain credential material;
- no network call inside the codec/helper.

## Remote call

One `readPage` invocation performs at most one:

```text
GET https://api.mercadolibre.com/orders/search
```

with:

```text
seller={authorizedUserId}
order.date_last_updated.from={closed UTC hour start}
order.date_last_updated.to={closed UTC hour end}
offset={progress offset}
limit={bounded page size}
```

and:

```text
Authorization: Bearer {accessToken}
Accept: application/json
```

No buyer query.

No order write.

No token refresh.

## Progress and continuous live-stream semantics

Opaque progress V1 contains only retrieval state equivalent to:

```text
windowFromHour
offset
```

`windowToHour` is deterministically `windowFromHour + 1 hour`.

Current Mercado Livre documentation states that order date filters use hour
granularity and discard minutes, seconds, and milliseconds. Therefore:

- boundaries are UTC and exactly aligned to `HH:00:00Z`;
- progress contains no minute, second, or fractional-second precision;
- first invocation seeds the immediately preceding fully closed UTC hour;
- historical backfill is not part of Slice A;
- one `readPage` performs at most one remote request;
- all pages for one source hour preserve the same hour and advance only offset;
- final page of a closed source hour advances to the next UTC hour at offset 0;
- finishing a source hour does not set `ConnectorPage.exhausted=true`;
- TASK-0152 is a continuous live capability and normal pages always return
  non-null next progress with `exhausted=false`;
- when progress catches up to an hour that is not yet fully closed, the adapter
  performs zero provider calls and returns `REMOTE_TEMPORARY` with a bounded retry
  hint toward the next UTC-hour boundary;
- progress contains no credential, provider body, internal order UUID, or
  economic decision.

Connector Runtime treats durable `exhausted=true` as terminal and will not invoke
the adapter again. TASK-0152 MUST NOT use terminal exhaustion for this live
capability.

The provider's documented retention horizon is not transformed into a canonical
data-completeness guarantee.

## Record model

Create one connector record:

```text
MercadoLivreOrderSourceRecord
```

with bounded nested item/payment source values.

Required order fields:

```text
externalOrderReference
providerStatus
dateCreated
dateLastUpdated
currency
totalAmount
observedAt
```

Optional order fields:

```text
dateClosed
paidAmount
packReference
shippingReference
```

Required item fields:

```text
itemReference
quantity
unitPrice
currency
```

Optional item fields:

```text
variationReference
saleFee
grossPrice
```

Required payment fields when a payment element is present:

```text
paymentReference
providerStatus
transactionAmount
currency
```

Optional payment fields:

```text
dateCreated
dateLastModified
```

No PII fields are modeled.

## Exact decimal and time

Provider monetary source values reuse exact `ProviderSourceDecimal`.

Amounts must not be synthesized when absent.

Order/item/payment currencies are preserved independently.

Timestamp parsing must preserve the source instant and normalize to microsecond
precision only where required by existing source-value policy.

`observedAt` is Genesis clock time, not provider business occurrence time.

## Response validation

Fail closed on:

- malformed root;
- malformed paging;
- page offset/limit mismatch;
- results exceeding ConnectorBudget;
- missing required order identity/status/time/currency/amount;
- malformed decimal;
- malformed item/payment identity;
- malformed timestamp;
- body over response budget;
- unsupported credential envelope.

HTTP 206 is accepted only if the missing-content header is restricted to
documented non-modeled fields and all TASK-0152 required fields remain present.
Otherwise fail closed.

## Failure mapping

Use existing Connector Runtime failure kinds.

At minimum:

```text
401 -> AUTHENTICATION_REQUIRED
403 -> AUTHORIZATION_DENIED
429 -> RATE_LIMITED
408/425/5xx -> REMOTE_TEMPORARY
caught up to non-closed current hour -> REMOTE_TEMPORARY
other definitive 4xx -> REMOTE_PERMANENT
malformed successful response -> REMOTE_DATA_INVALID
timeout exceeding invocation deadline -> BUDGET_EXCEEDED
cancellation before request -> CANCELLED
```

No internal retry.

## Persistence

Authorize exactly one additive migration:

```text
V021__create_mercado_livre_order_source_observation.sql
```

It may create normalized tables:

```text
integration_mercado_livre_order_source_observation
integration_mercado_livre_order_item_source_observation
integration_mercado_livre_payment_source_observation
```

Every parent observation is identified by existing connector page commit plus
record ordinal.

Child observations add bounded child ordinal.

Foreign keys must preserve organization/connection/capability/input progress
scope.

Indexes may support later lookup by provider external order reference and
provider last-updated timestamp.

No raw JSON column.

No `MarketplaceOrderId`.

No canonical currency column beyond explicit source currency values.

No evidence repository write.

## Committer

Create one `ConnectorPageCommitter` implementation that atomically persists:

```text
normalized source observations
+
existing connector page/progress commit
```

Exact replay is harmless.

Conflicting replay for the same page-commit identity fails closed.

No source row may survive without its page commit, and no progress may advance
without all source rows.

## Required tests

1. authentication helper returns seller ID/access only inside scoped operation;
2. helper exposes no refresh token/client secret;
3. helper rejects malformed envelope;
4. provider module dependency guard remains narrow;
5. exact provider/capability descriptor;
6. unsupported capability fails closed;
7. no request when cancelled/deadline exhausted;
8. exactly one GET per `readPage`;
9. request uses seller from authorizedUserId;
10. bearer token not rendered by request/response wrapper;
11. progress uses exact UTC-hour boundaries and no sub-hour precision;
12. initial progress seeds the immediately preceding fully closed UTC hour;
13. finishing a source hour advances to the next hour with `exhausted=false`;
14. caught-up non-closed hour emits REMOTE_TEMPORARY with zero HTTP calls;
15. page limit respects ConnectorBudget;
16. required order fields parse;
14. optional order fields remain null when absent;
15. item/payment source values parse exact decimals;
16. no buyer/seller PII fields exist in source record;
17. malformed paging fails;
18. malformed required source value fails;
19. body budget enforced;
20. 401/403/429/5xx mappings;
21. 206 only accepted for irrelevant missing fields;
22. no internal retry;
23. V021 applies additively;
24. V021 contains no raw JSON, token, secret, MarketplaceOrderId;
25. order/item/payment rows survive restart;
26. duplicate page replay is harmless;
27. conflicting page replay fails closed;
28. progress advances atomically with all source rows;
29. organization isolation;
30. TASK-0151 auth regression green;
31. TASK-0150 rotation regression green;
32. Connector Runtime regression green;
33. TASK-0149 Omie regression green;
34. full build green.

No test calls the real Mercado Livre API.

## Exact authorized implementation paths

TASK-0152 may modify/create exactly these eleven paths:

1. MODIFY
   `applications/marketplace-provider-authentication/src/main/kotlin/io/flooow/integration/provider/mercadolivre/MercadoLivreOAuthCredentialEnvelope.kt`

2. MODIFY
   `applications/marketplace-provider-authentication/src/test/kotlin/io/flooow/integration/provider/mercadolivre/MercadoLivreOAuthCredentialEnvelopeTest.kt`

3. MODIFY
   `applications/marketplace-economic-provider-ingestion/build.gradle.kts`

4. MODIFY
   `applications/marketplace-economic-provider-ingestion/src/main/kotlin/io/flooow/marketplace/operations/economics/provider/ProviderEconomicEvidenceRecords.kt`

5. CREATE
   `applications/marketplace-economic-provider-ingestion/src/main/kotlin/io/flooow/marketplace/operations/economics/provider/mercadolivre/MercadoLivreOrderSourceConnector.kt`

6. CREATE
   `applications/marketplace-economic-provider-ingestion/src/test/kotlin/io/flooow/marketplace/operations/economics/provider/mercadolivre/MercadoLivreOrderSourceConnectorTest.kt`

7. CREATE
   `applications/marketplace-operations-persistence-postgres/src/main/resources/db/migration/V021__create_mercado_livre_order_source_observation.sql`

8. CREATE
   `applications/marketplace-operations-persistence-postgres/src/main/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMercadoLivreOrderSourceCommitter.kt`

9. CREATE
   `applications/marketplace-operations-persistence-postgres/src/test/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMercadoLivreOrderSourceCommitterTest.kt`

10. MODIFY only for implementation evidence
    `docs/evidence/TASK-0152-live-mercado-livre-order-source-ingestion-slice-a.md`

11. APPEND exactly one TASK-0152 implementation entry
    `docs/journal/MGI-EXECUTIVE-JOURNAL.md`

No twelfth implementation path is authorized.

## Frozen

No TASK-0152 production change to:

- settings/module graph beyond the allowed provider-module dependency edit;
- Integration Control Plane;
- credential-rotation-execution;
- Connector Runtime;
- Marketplace Operations domain/evidence contracts;
- existing V001-V020 migrations;
- Economic Truth;
- Sales Intelligence;
- Omie connector;
- API/UI;
- scheduler/worker;
- notifications/webhooks;
- shipment-cost endpoint;
- Ads;
- provider writes;
- Kernel.

## Gates

```text
./gradlew :applications:marketplace-provider-authentication:test --no-daemon --console=plain
./gradlew :applications:marketplace-economic-provider-ingestion:test --no-daemon --console=plain
./gradlew :applications:marketplace-operations-persistence-postgres:test --no-daemon --console=plain
./gradlew :applications:credential-rotation-execution:test --no-daemon --console=plain
./gradlew :applications:connector-runtime:test --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
```

Repository CI must pass.

## Completion

TASK-0152 completes only after exact scope, privacy, source/paging semantics,
atomic durable commit, tests, CI, review, and merge gates pass.

Next: separately govern marketplace-order identity allocation/association and
promotion from Mercado Livre source observations into independent economic
evidence.