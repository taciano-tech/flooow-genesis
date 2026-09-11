# ADR-0050: Live Provider Economic Evidence Ingestion Boundary

Status: Accepted

Date: 2026-09-06

## Context

Genesis now has the durable chain required to consume provider evidence without
allowing provider payloads to become business truth:

```text
provider observation
  -> independent marketplace economic evidence
  -> durable evidence journal
  -> incremental change feed
  -> canonical Economic Truth Assembly
  -> canonical Economic Truth Calculator
  -> durable Sales Intelligence projection
```

TASK-0146 is complete and merged.

Genesis also already has:

- an organization-scoped Integration Control Plane with credential custody,
  versioned credential rotation, connection lifecycle, and audit;
- a provider-neutral Connector Runtime with bounded pages, cancellation,
  deadlines, record/response budgets, progress fencing, typed failures, and
  credential access scoped to one invocation;
- durable independent marketplace economic evidence and correction semantics;
- provider-neutral source provenance and external references.

Historical MGI operation proved useful provider behavior against real Mercado
Livre and Omie accounts. That history is evidence about provider mechanics, not
architecture authority.

Relevant validated MGI learnings include:

- Mercado Livre OAuth authorization-code flow, access-token expiry, refresh-token
  rotation, account discovery, advertiser discovery, Product Ads pagination and
  provider-specific filters;
- Omie App Key/App Secret authentication, numbered pages, products, inventory,
  stock movements, purchases, invoices, sales orders, and economic cost reads;
- Omie `unit_cmc` is useful product-cost evidence but is not complete margin or
  economic truth;
- identity suggestions and provider payload interpretation must remain evidence,
  never silently become canonical mapping;
- missing provider evidence must remain missing instead of being inferred.

## Decision

Provider adapters are edge translators.

They may know:

- provider authentication wire format;
- provider endpoints;
- provider pagination;
- provider response schemas;
- provider rate/failure semantics;
- provider-specific identifiers.

They must not own:

- canonical economic truth;
- economic calculation;
- canonical identity adjudication;
- correction authority beyond explicit source correction evidence;
- Sales Intelligence semantics;
- retry scheduling or backpressure policy;
- organization lifecycle;
- credential custody;
- durable evidence schema.

The Connector Runtime remains provider-neutral and infrastructure-free.

Provider-specific HTTP code must live outside `applications:connector-runtime`.

The first live activation slice is Omie read-only product-cost evidence because
its static App Key/App Secret credential model fits the existing runtime without
requiring a Connector Runtime contract change.

Mercado Livre live activation follows after a separate provider-neutral
credential-rotation execution bridge is accepted. The Integration Control Plane
already owns secure versioned credential rotation; no second secret store is
authorized.

## First live vertical

Slice A is:

```text
Omie
  -> bounded read-only product/economic-cost page
  -> typed connector records
  -> evidence committer
  -> MarketplaceIndependentEconomicEvidence PRODUCT_COST facts/attempts
  -> existing durable evidence journal
  -> existing change feed
  -> existing Economic Truth Assembly
  -> existing calculator
  -> existing Sales Intelligence projection
```

`unit_cmc` is accepted only as ERP product-cost evidence when the provider
response explicitly supplies it and an order/product association is already
explicitly available to the invocation/record.

No matching by title, fuzzy name, price similarity, or heuristic identity is
authorized.

## Module and persistence boundary

Create one marketplace-economic provider-ingestion module outside the Connector
Runtime:

```text
applications:marketplace-economic-provider-ingestion
```

The provider module may depend on:

```text
applications:connector-runtime
applications:marketplace-operations
platform:foundation:organization-context
kotlinx-serialization-json
```

It owns provider HTTP, credential-envelope decoding, pagination decoding, and
typed provider records.

Durable connector progress remains infrastructure. The provider module must not
pretend that an in-memory or fake progress implementation is production
completion.

The existing PostgreSQL application may depend on the new provider-ingestion
module and may add:

- one generic Postgres connector-progress store over the already existing
  `integration_connector_progress` and `integration_connector_page_commit`
  tables;
- one Omie economic-evidence `ConnectorPageCommitter` that composes the generic
  progress store with the existing durable independent economic evidence
  repository.

No new table or migration is authorized.

The provider module must not require:

- marketplace-operations-persistence-postgres;
- marketplace-operations-api;
- Kernel changes;
- Ktor server;
- a scheduler;
- a message broker;
- a second credential store;
- a second economic ledger.

Provider HTTP transport may use the JDK HTTP client. No provider SDK is required
for Slice A.

## Evidence authority

The adapter produces connector records.

The committer is the only Slice A component allowed to translate those records
into the existing `MarketplaceIndependentEconomicEvidenceUpdate` contract.

For product-cost facts:

```text
family = PRODUCT_COST
source.kind = ERP
source.systemKey = "omie"
external reference = explicit Omie source identity
coverage = COMPLETE or PARTIAL only when supported by the exact record
```

No zero is synthesized for missing cost.

No `NOT_APPLICABLE` is synthesized.

No provider response timestamp invents business occurrence ordering.

Collection outcomes with no usable evidence become explicit attempts where the
existing evidence contract supports them.

## Failure boundary

The adapter translates provider failures into existing Connector Runtime failure
kinds.

It must fail closed on malformed monetary values, ambiguous currency, malformed
provider identifiers, response-budget overflow, unsupported response shape, or
unsafe pagination state.

It must never return a partial record whose omitted fields alter the meaning of
a supplied economic amount.

## MGI archaeology rule

MGI code or behavior may be reused only when all of these are true:

1. the provider behavior was empirically useful;
2. the behavior still matches the provider contract used by the new adapter;
3. it does not bypass a Genesis authority boundary;
4. it can be expressed through current Genesis contracts;
5. secrets, tokens, local `.mgi` state, and historical baselines are not copied
   into the repository.

MGI domain models, Profit Intelligence, universal connector registry, endpoint
surface, local persistence model, and automatic identity behavior are not
canonical Genesis architecture.

## Consequences

Genesis can activate one real provider without weakening its economic-truth
chain.

Omie becomes the first live proof because it requires no credential-rotation
contract change.

Mercado Livre remains next and benefits from the same evidence committer and
module without forcing OAuth behavior into the Connector Runtime.

## Not authorized

This ADR does not authorize:

- Mercado Livre live traffic;
- OAuth callback routes;
- refresh-token rotation through Connector Runtime;
- provider write actions;
- campaign/budget mutation;
- scheduler or recurring worker;
- automatic retry loop;
- backpressure coordinator;
- circuit breaker;
- fuzzy Omie-to-marketplace mapping;
- API/UI;
- new economic truth types;
- new persistence tables;
- evidence-schema changes;
- Sales Intelligence semantic changes;
- Kernel changes.
## Amendment - provider product cost is not order evidence

Accepted: 2026-09-06

Pre-implementation contract validation found an authority gap that must remain
fail-closed.

`PullConnector.readPage` receives provider credential, opaque connector progress,
budget, and cancellation. It does not receive a marketplace economic subject.
That boundary is intentional and remains unchanged.

Omie product CMC is provider-level product-cost data. It does not itself identify:

- a Genesis `MarketplaceOrderId`;
- a complete `MarketplaceEconomicEvidenceSubject`;
- a marketplace order/item association;
- canonical order currency authority.

Therefore Slice A must not manufacture order-level `PRODUCT_COST` evidence from a
product page.

The first live Omie slice is corrected to:

```text
Omie product page
  -> typed provider product-cost record
  -> durable provider-level product-cost source observation
  -> durable connector progress
```

A later separately governed association stage may consume provider-level cost
observations plus explicit identity/order evidence and then produce order-level
`PRODUCT_COST` observations through the existing independent economic evidence
contract.

The association stage must remain fail-closed when product/order identity,
allocation semantics, or currency authority is unresolved.

This amendment preserves:

- live read-only Omie activation;
- exact decimal acquisition;
- provider-neutral Connector Runtime;
- durable progress and replay;
- no fuzzy identity;
- no invented economic truth.

It removes only the invalid assumption that an Omie product response can directly
name a canonical marketplace order subject.

### Durable provider observation boundary

Slice A may persist normalized provider-level product-cost observations in one
additive PostgreSQL table.

The row is source evidence, not canonical economic truth.

It may contain only bounded normalized fields such as:

- organization/connection/capability commit identity;
- record ordinal;
- stable Omie product reference;
- explicit SKU/EAN when present;
- exact source unit-CMC decimal when present;
- explicit source currency when present;
- explicit provider/source timestamp/version when present.

Raw provider JSON is not retained.

Missing currency remains missing.

No organization default currency is silently substituted.

No migration other than the single additive Slice A source-observation table is
authorized.
## Amendment - Omie CMC acquisition endpoint

Accepted: 2026-09-06

Current official Omie documentation confirms that CMC is an inventory-position
measure, not a documented field of the Products `ListarProdutos` response.

Slice A therefore acquires CMC from:

```text
POST https://app.omie.com.br/api/v1/estoque/consulta/
call = ListarPosEstoque
```

The response exposes product/location source observations including:

```text
nCodProd
cCodInt
cCodigo
cDescricao
nPrecoUnitario
nSaldo
nCMC
nPendente
codigo_local_estoque
reservado
fisico
```

Slice A preserves `nCMC` as explicit provider-level product-cost source evidence.

It does not perform the historical MGI weighted-CMC aggregation inside the
provider adapter. Each product/location observation remains source evidence.

Products `ListarProdutos` may be used by later separately governed identity or
catalog enrichment, but it is not the CMC authority for TASK-0149.

No provider write method is authorized.