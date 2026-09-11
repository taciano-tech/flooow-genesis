# TASK-0149: Live Omie Economic Evidence Ingestion

Status: Authorized for implementation

Date: 2026-09-06

## Authority

Governed by:

- ADR-0009 Integration Control Plane Boundary;
- ADR-0011 Connector Execution Boundary;
- ADR-0042 Independent Marketplace Economic Evidence Boundary;
- ADR-0043 Durable Independent Marketplace Economic Evidence Boundary;
- ADR-0046 Durable Marketplace Economic Evidence Incremental Change Feed;
- ADR-0048 Canonical Economic Truth Assembly;
- ADR-0049 Durable OrderOccurrence Persistence;
- ADR-0050 Live Provider Economic Evidence Ingestion Boundary;
- SPEC-0011 Connector Runtime;
- SPEC-0042 Durable Independent Marketplace Economic Evidence;
- SPEC-0045 Durable Incremental Change Feed;
- SPEC-0047 Canonical Economic Truth Assembly;
- SPEC-0048 Durable OrderOccurrence Persistence;
- SPEC-0049 Live Omie Economic Evidence Ingestion Slice A;
- completed TASK-0146 durable Sales Intelligence projection.

SPEC-0049 is normative for this task.

## Why this task is next

The durable economic chain is closed.

The next missing capability is real provider evidence entering that chain.

Historical MGI operation already proved that:

- Mercado Livre and Omie can be queried read-only;
- Omie App Key/App Secret configuration works;
- Omie exposes product/economic cost data including unit CMC;
- unit CMC is evidence, not complete economic truth;
- Mercado Livre OAuth requires rotating refresh-token handling.

The existing Genesis Connector Runtime already supports the Omie static
credential case without a runtime change.

Therefore Omie PRODUCT_COST evidence is the smallest safe live-provider proof.

## Objective

Implement exactly one provider capability:

```text
provider = omie
capability = marketplace-economic.product-cost
mode = read-only pull
```

The connector must turn explicit Omie product-cost provider data into typed
provider-level records and the page committer must durably preserve normalized
source observations with durable progress. TASK-0149 does not promote those
records into order-level marketplace economic evidence.

## Non-negotiable invariants

- provider payload is not economic truth;
- no economic value is invented;
- no zero is substituted for missing cost;
- no identity is guessed;
- no secret leaves scoped credential handling;
- no provider raw payload becomes durable economic evidence storage;
- evidence repository remains durable write authority;
- evidence change feed remains projection invalidation authority;
- assembler remains order-assembly authority;
- calculator remains economic-calculation authority;
- Sales Intelligence remains derivative;
- Connector Runtime remains provider-neutral;
- Control Plane remains credential/lifecycle authority.

## MGI reuse policy

Use old MGI only as provider archaeology and test-vector knowledge.

Reuse is encouraged for:

- working Omie request shape;
- authentication field names;
- page semantics;
- known response fields;
- unit CMC extraction knowledge;
- provider failure quirks;
- safety lessons.

Do not port as Genesis authority:

- MGI connector registry;
- MGI Universal Commerce Model;
- MGI Profit Intelligence;
- MGI local `.mgi` persistence;
- MGI automatic identity suggestions;
- MGI API endpoint architecture;
- MGI secrets/tokens;
- historical provider payloads containing private account data.

## Exact scope

Implementation is restricted to the thirteen paths frozen by SPEC-0049.

Exactly one additive migration is authorized: V019 for normalized Omie product-cost source observations. No other schema change is authorized.

The existing persistence application may add the generic connector-progress store, V019 source-observation table, and Omie product-cost committer frozen by SPEC-0049. No Connector Runtime or Integration Control Plane production change is authorized.

## Gates

At minimum:

```text
./gradlew :applications:marketplace-economic-provider-ingestion:compileKotlin --no-daemon --console=plain
./gradlew :applications:marketplace-economic-provider-ingestion:compileTestKotlin --no-daemon --console=plain
./gradlew :applications:marketplace-economic-provider-ingestion:test --no-daemon --console=plain
./gradlew :applications:marketplace-operations-persistence-postgres:compileKotlin --no-daemon --console=plain
./gradlew :applications:marketplace-operations-persistence-postgres:compileTestKotlin --no-daemon --console=plain
./gradlew :applications:marketplace-operations-persistence-postgres:test --no-daemon --console=plain
./gradlew :applications:connector-runtime:test --no-daemon --console=plain
./gradlew :applications:marketplace-operations:test --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
```

Repository CI must pass.

## Completion gate

TASK-0149 is complete only when:

- exact authorized scope is preserved;
- tests prove provider and evidence semantics;
- no secret/provider payload leakage is present;
- repository CI is green;
- implementation PR is review-clean and merged.

After TASK-0149, the next provider step is the provider-neutral credential
rotation execution bridge required for durable Mercado Livre OAuth refresh-token
rotation. Mercado Livre live economic ingestion follows that bridge.
## Pre-implementation correction - 2026-09-06

Contract validation after authorization proved that Omie product CMC cannot
directly become order-level `PRODUCT_COST` evidence because the provider page has
no canonical marketplace economic subject and Connector Runtime intentionally
does not inject one.

TASK-0149 therefore remains a live Omie activation task but stops at durable
provider-level product-cost source observation.

The later promotion/association stage is separately governed.

This is a fail-closed correction, not a scope expansion into intelligence or
identity matching.
## Pre-implementation provider-contract correction - CMC endpoint

Current official Omie documentation was revalidated before production code.

CMC is sourced from the read-only inventory-position API:

```text
POST https://app.omie.com.br/api/v1/estoque/consulta/
call = ListarPosEstoque
```

The source field is `produtos[].nCMC`.

TASK-0149 does not source CMC from `ListarProdutos`.

Historical MGI evidence is consistent with product/location CMC observations and
is retained only as provider archaeology. Genesis does not port MGI's weighted
CMC decision logic into the provider adapter.
## Implementation evidence - Slice A

Implementation branch:

```text
feat/task-0149-live-omie-economic-evidence
```

Implemented production path:

```text
Integration Control Plane scoped static credential
-> Connector Runtime PullConnector invocation
-> Omie ListarPosEstoque read-only request
-> bounded numbered page
-> normalized product/location product-cost source record
-> PostgreSQL provider source observation
-> durable connector page/progress commit
```

The implementation preserves the corrected contract:

- provider key is `omie`;
- capability is `marketplace-economic.product-cost`;
- provider endpoint is `estoque/consulta`;
- method is `ListarPosEstoque`;
- `nCMC` is exact provider-level source evidence;
- `nCMC = 0` remains observed zero and is not promoted to canonical zero cost;
- missing `nCMC` remains missing;
- currency is not invented;
- product/location source observations remain provider evidence;
- no weighted-CMC decision logic is ported from MGI;
- no order-level `PRODUCT_COST` evidence is created;
- raw provider JSON is not persisted;
- Connector Runtime and Integration Control Plane production code are unchanged;
- progress is sealed by the existing ConnectorProgressProtector contract;
- page records persist before progress advances;
- deterministic duplicate page replay validates existing normalized rows;
- conflicting replay fails closed;
- provider source persistence failure rolls back page/progress;
- organization/connection isolation is preserved.

Local implementation gates are executed by the TASK-0149 implementation bundle
before commit/push.

Repository CI and implementation PR merge remain completion-gate requirements.