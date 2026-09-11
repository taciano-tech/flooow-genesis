# ADR-0056: Live Marketplace Economic Pipeline Orchestration

Status: Accepted

Date: 2026-09-06

## Context

TASK-0152 through TASK-0154 provide durable live Mercado Livre source ingestion,
canonical order identity, OrderOccurrence, REVENUE evidence and durable Sales
Intelligence projection infrastructure.

The remaining MVP gap is composition.

## Decision

Create:

```text
applications:marketplace-live-pipeline
```

as a stateless bounded application coordinator.

The coordinator composes existing authorities in this exact order:

```text
1. ConnectorRuntime source pull
2. MarketplaceOrderSourcePromotionService
3. MarketplaceOrderRevenuePromotionService
4. MarketplaceSalesIntelligenceProjectionProcessor
```

## Caller-owned identity

The caller supplies one exact:

```text
organizationId
connectionId
```

TASK-0155 does not enumerate organizations or discover connections.

## Provider/capability boundary

The accepted live slice is exactly:

```text
provider = br.com.mercadolivre
capability = marketplace-economic.order-source
```

The coordinator must invoke only this capability.

It must fail closed if a source outcome identifies a different provider.

## Global run boundary

Every run has:

```text
deadline
cancellation
bounded source pages
bounded promotion batches
bounded projection batches
```

Maximum run duration is five minutes.

No unbounded loop is authorized.

## Source failure rule

A source failure other than explicit cancellation does not by itself prevent
processing already-durable downstream work.

The source outcome must remain visible in the run summary.

The coordinator must not reinterpret generic REMOTE_TEMPORARY as "caught up".

## Downstream fail-closed rule

If OrderOccurrence promotion blocks:

```text
stop before REVENUE
```

If REVENUE promotion blocks:

```text
stop before Sales Intelligence projection
```

If Sales Intelligence returns:

```text
CheckpointConflict
IntegrityFailure
```

the cycle blocks.

## Existing truth owners remain sovereign

TASK-0155 creates no new persistence and no migration.

Durable positions remain owned by:

```text
ConnectorRuntime progress
V022 occurrence promotion
V023 revenue promotion
Sales Intelligence change-feed checkpoint
```

No pipeline checkpoint, execution ledger or distributed transaction is added.

## Retry semantics

A bounded cycle is safe to retry because every composed stage is already
idempotent or conflict-explicit.

TASK-0155 does not claim exactly-once execution across stages.

## Scheduling

TASK-0155 is not a scheduler.

It exposes one callable cycle suitable for:

- controlled API refresh;
- CLI/manual execution;
- future scheduled invocation.

Fleet scheduling is separately governed.

## Credential behavior

No OAuth refresh occurs inside the pipeline.

Authentication/authorization failures remain source outcomes from
ConnectorRuntime.

## Consequences

The MVP gains one deterministic orchestration seam without mixing provider,
evidence, economic truth or projection responsibilities.

The next critical path becomes:

```text
TASK-0156 Sales Intelligence API + controlled refresh
TASK-0157 MVP UI
```