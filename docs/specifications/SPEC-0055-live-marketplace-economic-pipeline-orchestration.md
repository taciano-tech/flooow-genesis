# SPEC-0055: Live Marketplace Economic Pipeline Orchestration

Status: Accepted

Date: 2026-09-06

Governing ADR: ADR-0056

Implementation task: TASK-0155

## Objective

Provide one stateless bounded application service that advances the live
Mercado Livre economic pipeline from provider source through durable Sales
Intelligence.

## New module

Create:

```text
applications:marketplace-live-pipeline
```

Allowed production project dependencies exactly:

```text
organization-context
integration-control-plane
connector-runtime
marketplace-order-source-promotion
marketplace-operations
```

Forbidden:

```text
marketplace-operations-persistence-postgres
marketplace-economic-provider-ingestion
marketplace-provider-authentication
credential-rotation-execution
marketplace-operations-api
kernel direct
```

## Required production adapters

The module may define thin adapters over these existing services:

```text
ConnectorRuntime
MarketplaceOrderSourcePromotionService
MarketplaceOrderRevenuePromotionService
MarketplaceSalesIntelligenceProjectionProcessor
```

Tests must be able to replace each stage through a narrow port.

## Contract constants

Exactly:

```text
provider = br.com.mercadolivre
capability = marketplace-economic.order-source
```

No other provider/capability is accepted in TASK-0155.

## Run input

One cycle receives:

```text
OrganizationId
IntegrationConnectionId
deadline
limits
ConnectorCancellation
```

No organization discovery.

No connection discovery.

## Limits

Validated limits include:

```text
maxSourcePages
sourceMaxRecordsPerPage
sourceMaxResponseBytes
maxOccurrenceBatches
maxRevenueBatches
promotionBatchSize
maxProjectionBatches
projectionBatchSize
```

Bounds:

```text
maxSourcePages             1..100
sourceMaxRecordsPerPage    1..ConnectorBudget.MAX_RECORDS
sourceMaxResponseBytes     1..ConnectorBudget.MAX_RESPONSE_BYTES
maxOccurrenceBatches       1..100
maxRevenueBatches          1..100
promotionBatchSize         1..1000
maxProjectionBatches       1..100
projectionBatchSize        1..1000
```

Global deadline must be in the future and no more than five minutes from the
coordinator clock at run start.

## Source invocation

For each source page attempt construct:

```text
ConnectorInvocation(
  organizationId,
  connectionId,
  capability = marketplace-economic.order-source,
  invocationId = fresh opaque UUID,
  budget = ConnectorBudget(
    deadline = global deadline,
    maxRecords = configured sourceMaxRecordsPerPage,
    maxResponseBytes = configured sourceMaxResponseBytes
  )
)
```

Each source attempt receives a new invocation identifier.

## Source phase

Repeat until first of:

```text
exhausted success
source failure
maxSourcePages reached
cancellation
global deadline
```

Accumulate only safe metadata:

```text
invocations
committedPages
alreadyCommittedPages
recordCount
stop kind
failure kind nullable
retryAfter nullable
```

Do not expose provider record content.

## Provider gate

For source success:

```text
providerKey must equal br.com.mercadolivre
capability must equal marketplace-economic.order-source
```

For source failure:

```text
if providerKey is present it must equal br.com.mercadolivre
capability must equal marketplace-economic.order-source
```

Mismatch -> pipeline integrity block.

## Source stop classification

Exactly:

```text
EXHAUSTED
PAGE_LIMIT
RETRYABLE_FAILURE
NON_RETRYABLE_FAILURE
```

`RATE_LIMITED` and `REMOTE_TEMPORARY` are retryable.

TASK-0155 must not infer a semantic reason beyond the ConnectorRuntime failure.

`CANCELLED` is a pipeline cancellation block, not a source summary failure.

## Durable backlog

After a non-cancellation source failure, continue to OrderOccurrence promotion.

This is mandatory.

## OrderOccurrence drain

Call:

```text
MarketplaceOrderSourcePromotionService.promotePending
```

with configured `promotionBatchSize`.

Repeat until:

```text
Completed.examined == 0
maxOccurrenceBatches
cancellation/deadline
Blocked
```

Validate for every Completed result:

```text
examined ==
  promoted +
  duplicates +
  identityConflicts +
  evidenceConflicts
```

Mismatch -> pipeline integrity block.

Blocked -> stop the entire cycle before revenue.

## Revenue drain

Only after occurrence phase does not block.

Call:

```text
MarketplaceOrderRevenuePromotionService.promotePendingRevenue
```

using the same bounded rules and consistency equation.

Blocked -> stop before projection.

## Sales Intelligence projection drain

Only after occurrence and revenue do not block.

Call:

```text
MarketplaceSalesIntelligenceProjectionProcessor.processBatch
```

until:

```text
NoChanges
maxProjectionBatches
cancellation/deadline
CheckpointConflict
IntegrityFailure
```

`Success.processedChanges` must be positive.

`NoChanges` means projection `drained = true`.

Max-batch stop is successful bounded incompleteness:

```text
drained = false
```

CheckpointConflict and IntegrityFailure block.

## Cancellation/deadline

Check between every stage invocation.

Cancellation has precedence over deadline.

No downstream stage starts after either gate closes.

## Result

Return a safe structured result:

```text
Completed
  source summary
  occurrence summary
  revenue summary
  projection summary

Blocked
  typed block detail
  summaries completed before block
```

No order IDs, external references, credentials, provider payload or money values
are included in the orchestration result.

## No new persistence

TASK-0155 creates no migration.

It creates no:

```text
pipeline checkpoint
run table
lease
lock table
retry table
```

## No scheduling

No timer, thread, coroutine loop, cron, daemon or background scheduler.

## Required tests

1. exact module dependency boundary;
2. exact provider/capability constants;
3. one source page receives one fresh invocation id;
4. source page loop respects maxSourcePages;
5. source success counts COMMITTED and ALREADY_COMMITTED separately;
6. exhausted source stops source phase;
7. RATE_LIMITED is retryable source stop;
8. REMOTE_TEMPORARY is retryable source stop;
9. non-retryable source failure still drains durable downstream work;
10. cancellation stops downstream;
11. deadline stops downstream;
12. run deadline over five minutes is rejected;
13. wrong source provider fails closed;
14. wrong source capability fails closed;
15. occurrence stage drains until zero;
16. occurrence max-batch stop returns drained=false;
17. occurrence result count mismatch fails integrity;
18. occurrence Blocked stops revenue/projection;
19. revenue runs only after occurrence;
20. revenue drains until zero;
21. revenue max-batch stop returns drained=false;
22. revenue result count mismatch fails integrity;
23. revenue Blocked stops projection;
24. projection Success loops;
25. projection NoChanges sets drained=true;
26. projection max-batch stop sets drained=false;
27. zero/negative processedChanges in Success fails integrity;
28. projection CheckpointConflict blocks;
29. projection IntegrityFailure blocks;
30. no source failure is relabeled "caught up";
31. no persistence dependency;
32. no provider-ingestion dependency;
33. no provider-authentication dependency;
34. no credential-rotation dependency;
35. no API dependency;
36. TASK-0154 regressions green;
37. ConnectorRuntime regressions green;
38. Sales Intelligence regressions green;
39. full build green.

## Exact authorized implementation paths

TASK-0155 implementation may modify/create exactly these six paths:

1. MODIFY
   `settings.gradle.kts`

2. CREATE
   `applications/marketplace-live-pipeline/build.gradle.kts`

3. CREATE
   `applications/marketplace-live-pipeline/src/main/kotlin/io/flooow/marketplace/operations/live/MarketplaceLivePipeline.kt`

4. CREATE
   `applications/marketplace-live-pipeline/src/test/kotlin/io/flooow/marketplace/operations/live/MarketplaceLivePipelineTest.kt`

5. MODIFY only for implementation evidence
   `docs/evidence/TASK-0155-live-marketplace-economic-pipeline-orchestration.md`

6. APPEND exactly one TASK-0155 implementation entry
   `docs/journal/MGI-EXECUTIVE-JOURNAL.md`

No seventh implementation path is authorized.

## Frozen

TASK-0155 must not modify production code in:

```text
connector-runtime
marketplace-economic-provider-ingestion
marketplace-order-source-promotion
marketplace-operations
marketplace-operations-persistence-postgres
integration-control-plane
marketplace-provider-authentication
credential-rotation-execution
marketplace-operations-api
```

No migration is authorized.

If an existing contract is insufficient, implementation stops for governance
amendment.

## Gates

```text
./gradlew :applications:marketplace-live-pipeline:compileKotlin --no-daemon --console=plain
./gradlew :applications:marketplace-live-pipeline:compileTestKotlin --no-daemon --console=plain
./gradlew :applications:marketplace-live-pipeline:test --no-daemon --console=plain
./gradlew :applications:connector-runtime:test --no-daemon --console=plain
./gradlew :applications:marketplace-order-source-promotion:test --no-daemon --console=plain
./gradlew :applications:marketplace-operations:test --no-daemon --console=plain
./gradlew :applications:marketplace-economic-provider-ingestion:test --no-daemon --console=plain
./gradlew :applications:marketplace-operations-persistence-postgres:test --no-daemon --console=plain
./gradlew build --no-daemon --console=plain
```

Repository CI must pass.

## Completion

TASK-0155 closes only when exact scope, bounded execution, fail-closed semantics,
local gates, CI and merge pass.

Next:

```text
TASK-0156 Sales Intelligence API + controlled refresh
-> TASK-0157 MVP UI
```