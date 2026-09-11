# SPEC-0049: Live Omie Economic Evidence Ingestion - Slice A

Status: Accepted

Date: 2026-09-06

Governing ADR: ADR-0050

Implementation task: TASK-0149

## Objective

Implement the smallest production-capable, read-only Omie connector slice that
can acquire explicit Omie product-cost data and durably preserve it as
provider-level source observation without inventing marketplace order identity,
currency authority, or canonical economic truth.

Connector Runtime, organization/credential authority, and the existing
marketplace economic evidence contract remain unchanged.

## Capability

Freeze one capability:

```text
marketplace-economic.product-cost
```

Provider key:

```text
omie
```

The capability processes at most one bounded Omie page per Connector Runtime
invocation.

## Credential contract

Slice A uses the existing Integration Control Plane credential custody.

The connector receives opaque credential bytes from Connector Runtime.

Inside the provider module, those bytes decode to a versioned Omie credential
envelope containing exactly:

```text
schemaVersion = 1
appKey
appSecret
```

Rules:

- blank values are invalid;
- values are never included in `toString`, logs, exceptions, telemetry, records,
  progress, or evidence;
- owned temporary byte arrays must be cleared when practical;
- the connector does not persist credentials;
- the connector does not rotate credentials;
- repository source contains no real credential.

## HTTP contract

Use HTTPS only.

Default Omie endpoint for Slice A:

```text
https://app.omie.com.br/api/v1/estoque/consulta/
```

Method:

```text
ListarPosEstoque
```

The request uses the documented numbered-page contract:

```text
nPagina
nRegPorPagina
dDataPosicao
cExibeTodos
codigo_local_estoque
```

Slice A uses `cExibeTodos = "S"` so provider-level product-cost observations are
not silently restricted to products with movement.

`dDataPosicao` is explicit adapter input/configuration derived from the
invocation-time clock and formatted as the Omie-required calendar date. It is not
stored in opaque progress.

Endpoint override is allowed only through explicit constructor/configuration
injection for deterministic tests and controlled environments; it is not
accepted from provider payload or progress.

One invocation performs one provider request/page.

No internal automatic retry loop.

Connector Runtime deadlines, record budget, response-byte budget, and
cancellation remain authoritative.

The adapter consumes only documented read-only response fields needed by Slice A:

```text
nPagina
nTotPaginas
nRegistros
nTotRegistros
dDataPosicao
produtos[].nCodProd
produtos[].cCodInt
produtos[].cCodigo
produtos[].cDescricao
produtos[].nSaldo
produtos[].nCMC
produtos[].codigo_local_estoque
produtos[].reservado
produtos[].fisico
```

Raw response bodies are not persisted.

## Pagination

Progress is opaque to Connector Runtime.

The Omie adapter encodes only the next `nPagina`.

The first invocation requests page 1.

After each response:

- `nPagina` must equal the requested page;
- `nTotPaginas` must be positive;
- `nPagina` must not exceed `nTotPaginas`;
- if `nPagina < nTotPaginas`, next progress is `nPagina + 1`;
- if `nPagina == nTotPaginas`, the page is exhausted and next progress is null.

The adapter validates progress before network use.

Progress must not contain:

- credentials;
- source payload;
- economic amounts;
- organization identity;
- internal order identity;
- inventory location values;
- provider date strings.

A page cannot exceed ConnectorBudget.maxRecords.

A response cannot exceed ConnectorBudget.maxResponseBytes.

## Connector record

Define a closed Slice A record representing one explicit Omie inventory-position
product-cost source observation.

Minimum fields:

```text
Omie product reference (nCodProd)
Omie integration reference when present (cCodInt)
Omie displayed product/SKU code when present (cCodigo)
Omie location reference (codigo_local_estoque)
unit CMC from nCMC
stock balance from nSaldo when present
physical stock from fisico when present
reserved stock from reservado when present
provider position date from dDataPosicao
connector observedAt
```

The record contains no `MarketplaceEconomicEvidenceSubject`.

`nCMC` is preserved exactly as provider decimal source evidence.

A provider `nCMC = 0` is preserved as an observed zero; it is not automatically
interpreted as a known zero canonical product cost.

Currency remains absent in Slice A because `ListarPosEstoque` does not establish
currency authority.

A connector record is not canonical economic truth.

## Association rule

Slice A performs no Omie-product-to-marketplace-order association.

Title similarity, SKU guessing, product-name matching, price matching, and fuzzy
identity are forbidden.

An exact SKU/EAN/source identity may be retained as source evidence, but it does
not become canonical identity by retention alone.

Historical MGI identity-resolution knowledge may be used later as candidate
evidence, never as implicit canonical association in this slice.

Promotion from provider-level cost to order-level `PRODUCT_COST` is a later
separately governed stage requiring explicit subject, identity, allocation
semantics where applicable, and currency authority.

## Committer and durable progress

Create one PostgreSQL-backed `ConnectorPageCommitter` for this capability.

The provider module owns no durable progress.

The persistence layer may introduce one reusable Postgres connector-progress
store backed by the existing:

```text
integration_connector_progress
integration_connector_page_commit
```

schema.

Slice A also introduces one additive normalized provider-observation table for
Omie product-cost records. It is not an economic-truth or marketplace-evidence
table.

The Omie product-cost committer must:

1. validate organization, capability, and record type;
2. load/use the existing durable connector progress contract;
3. durably write normalized provider-level product-cost records for the page;
4. retain no raw provider response;
5. preserve explicit missing currency rather than invent one;
6. reject conflicting replay for the same page/source identity;
7. advance connector progress only after all normalized records for the page are
   durable;
8. preserve idempotent page replay;
9. never call or mutate the marketplace independent economic evidence repository;
10. never create order-level `PRODUCT_COST` evidence.

The generic Postgres progress store is infrastructure reuse only. It must not
interpret provider records or economic meaning.

## Economic mapping

For an explicit Omie unit CMC, Slice A preserves the exact source decimal as a
provider-level product-cost observation.

Binary floating-point canonicalization is forbidden.

The observation does not claim `EconomicComponentType.PRODUCT_COST`, coverage,
order currency, order quantity allocation, or canonical economic meaning.

Missing/ambiguous values generate no invented amount or currency.

## Deterministic identifiers

Provider-level source observation identity must be deterministic from stable
source/page identity and record ordinal/source reference, or otherwise satisfy
replay idempotency through the durable committer contract.

Repeated delivery of the same provider page must converge without duplicate
source meaning.

A changed provider meaning under an already-committed page/source identity must
fail closed.

Slice A does not invent automatic source correction and does not emit
marketplace economic evidence identifiers.

## Provider failure translation

At minimum:

- invalid/missing credential -> AUTHENTICATION_REQUIRED;
- provider authorization denial -> AUTHORIZATION_DENIED;
- HTTP/provider rate limit -> RATE_LIMITED with bounded Retry-After when present;
- provider 5xx/transient transport -> REMOTE_TEMPORARY;
- permanent unsupported request -> REMOTE_PERMANENT;
- malformed economic/provider payload -> REMOTE_DATA_INVALID;
- budget overflow -> BUDGET_EXCEEDED;
- cancellation -> CANCELLED.

No adapter exception, response body, secret, endpoint credential material, or
raw provider payload crosses the public Connector Runtime result boundary.

## Required tests

Module tests must prove at minimum:

- valid credential decoding without secret leakage;
- malformed credential fails before network use;
- HTTPS enforcement;
- one invocation performs at most one page request;
- numbered pagination/progress round trip;
- malformed progress fails before network use;
- record budget enforced;
- response byte budget enforced;
- cancellation before/during work;
- explicit unit_cmc parses exactly;
- absent unit_cmc does not synthesize zero;
- malformed monetary value fails closed;
- missing currency remains missing;
- no fuzzy identity association;
- no marketplace economic subject is invented;
- deterministic replay of the same provider page;
- duplicate provider delivery does not duplicate source meaning;
- conflicting replay blocks progress advancement;
- source-observation persistence failure blocks progress advancement;
- all source-observation durability precedes page progress commit;
- organization isolation;
- connector failure taxonomy mapping;
- secrets absent from public strings/errors;
- raw provider JSON is not persisted.

Existing regression gates must remain green.

## Performance/safety characterization

The test suite must include a representative bounded page near the accepted
record maximum for this capability and prove linear page handling without
unbounded accumulation beyond ConnectorBudget.

No load-test framework or partitioning is authorized.

## Exact authorized implementation paths

TASK-0149 may modify/create exactly these thirteen paths:

1. MODIFY
   `settings.gradle.kts`

2. CREATE
   `applications/marketplace-economic-provider-ingestion/build.gradle.kts`

3. CREATE
   `applications/marketplace-economic-provider-ingestion/src/main/kotlin/io/flooow/marketplace/operations/economics/provider/ProviderEconomicEvidenceRecords.kt`

4. CREATE
   `applications/marketplace-economic-provider-ingestion/src/main/kotlin/io/flooow/marketplace/operations/economics/provider/omie/OmieEconomicEvidenceConnector.kt`

5. CREATE
   `applications/marketplace-economic-provider-ingestion/src/test/kotlin/io/flooow/marketplace/operations/economics/provider/ProviderEconomicEvidenceRecordsTest.kt`

6. CREATE
   `applications/marketplace-economic-provider-ingestion/src/test/kotlin/io/flooow/marketplace/operations/economics/provider/omie/OmieEconomicEvidenceConnectorTest.kt`

7. MODIFY
   `applications/marketplace-operations-persistence-postgres/build.gradle.kts`

8. CREATE
   `applications/marketplace-operations-persistence-postgres/src/main/resources/db/migration/V019__create_omie_product_cost_source_observation.sql`

9. CREATE
   `applications/marketplace-operations-persistence-postgres/src/main/kotlin/io/flooow/marketplace/persistence/postgres/PostgresConnectorProgressStore.kt`

10. CREATE
    `applications/marketplace-operations-persistence-postgres/src/main/kotlin/io/flooow/marketplace/persistence/postgres/PostgresOmieProductCostCommitter.kt`

11. CREATE
    `applications/marketplace-operations-persistence-postgres/src/test/kotlin/io/flooow/marketplace/persistence/postgres/PostgresOmieProductCostCommitterTest.kt`

12. MODIFY only as TASK-0149 implementation evidence
    `docs/evidence/TASK-0149-live-omie-economic-evidence-ingestion.md`

13. APPEND exactly one TASK-0149 implementation entry
    `docs/journal/MGI-EXECUTIVE-JOURNAL.md`

No fourteenth implementation path is authorized.

## Frozen outside Slice A

- `applications:connector-runtime` production code;
- `applications:integration-control-plane` production code;
- PostgreSQL migrations or schema changes other than V019 and the explicitly
  authorized persistence paths;
- Marketplace Economic Evidence domain semantics;
- Economic Truth assembler/calculator;
- Sales Intelligence projection;
- Mercado Livre adapter;
- OAuth;
- provider credential rotation execution bridge;
- API/UI;
- scheduler/worker;
- retries/backpressure/circuit breaker;
- identity adjudication;
- promotion of provider product cost into order-level PRODUCT_COST evidence;
- MGI domain model port;
- Kernel.

If implementation requires any frozen path, TASK-0149 stops and governance must
be amended before code continues.