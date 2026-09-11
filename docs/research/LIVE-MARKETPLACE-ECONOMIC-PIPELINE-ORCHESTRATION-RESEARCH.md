# Live Marketplace Economic Pipeline Orchestration Research

Date: 2026-09-06

Status: Concluded for TASK-0155 bounded MVP slice

## Question

What is the smallest safe orchestration layer that turns the already-governed
live Mercado Livre source, canonical economic evidence and durable Sales
Intelligence projection into one callable MVP pipeline without inventing a
scheduler, a second checkpoint, or distributed transaction semantics?

## Existing durable stages

The repository already has the required independent authorities:

```text
ConnectorRuntime
  -> one bounded provider page
  -> durable V021 Mercado Livre order source

MarketplaceOrderSourcePromotionService
  -> canonical V022 order identity
  -> OrderOccurrence evidence
  -> durable occurrence terminal ledger

MarketplaceOrderRevenuePromotionService
  -> V021 total_amount + V022 identity
  -> REVENUE / ADDITION
  -> CONFIRMED / PARTIAL evidence
  -> durable V023 revenue terminal ledger

MarketplaceSalesIntelligenceProjectionProcessor
  -> durable economic evidence change feed
  -> Economic Truth assembly/calculation
  -> durable disposable Sales Intelligence projection
  -> durable projection checkpoint
```

Each stage is already independently retry-safe.

## Missing capability

There is no production application service that composes those four existing
stages into one bounded invocation.

Tests currently instantiate ConnectorRuntime directly. Sales Intelligence has a
processor but no live application coordinator.

The MVP therefore has all the organs but no single heartbeat.

## Decision

Create a stateless application orchestration module:

```text
applications:marketplace-live-pipeline
```

The caller supplies:

```text
organizationId
connectionId
deadline
bounded limits
cancellation
```

The service performs, in order:

```text
bounded source pull
  -> bounded OrderOccurrence drain
  -> bounded REVENUE drain
  -> bounded Sales Intelligence projection drain
```

## Why the orchestrator is stateless

Do not create:

```text
pipeline checkpoint
pipeline execution table
scheduler lease
distributed lock
global cursor
```

The existing stages already own their durable positions:

```text
Connector progress       -> integration_connector_progress
Order occurrence source  -> V022 terminal ledger
Revenue source           -> V023 terminal ledger
Sales projection         -> change-feed projection checkpoint
```

A fifth durable position would create conflicting truth.

## Source phase semantics

ConnectorRuntime remains the only authority allowed to:

- resolve the active provider;
- access the active credential;
- invoke the provider connector;
- enforce page budgets;
- commit connector progress and source observations.

TASK-0155 does not call Mercado Livre HTTP directly.

The source phase may invoke ConnectorRuntime repeatedly, but only within an
explicit page cap and a global deadline.

Each page receives a fresh ConnectorInvocationId.

The live Mercado Livre connector is not expected to report exhausted=true in
normal operation because it advances closed UTC-hour windows. Reaching the
current open hour may surface as a retryable remote condition.

TASK-0155 therefore does not claim that a retryable source stop means either
"fully caught up" or "remote outage". It reports the exact runtime failure class
and retry hint without inventing meaning.

## Durable-backlog rule

A non-cancellation source failure does not erase or suppress already-durable
work.

Example:

```text
Mercado Livre temporarily unavailable
        |
        v
source pull stops
        |
        +--> V021 rows already committed still promote
        +--> evidence already durable still projects
```

This lets the system continue converting durable truth even while the remote
provider is temporarily unavailable.

Cancellation or global deadline stops the pipeline.

## Provider identity gate

The current promotion contracts are explicitly Mercado Livre:

```text
provider = br.com.mercadolivre
marketplace = mercado-livre
capability = marketplace-economic.order-source
```

A successful source execution for another provider is an integrity failure.

A runtime failure that explicitly identifies another provider also fails
closed.

## Downstream block semantics

Occurrence, revenue and projection are ordered because:

```text
REVENUE requires canonical V022 identity
Sales Intelligence should observe all evidence already promoted in the cycle
```

If occurrence promotion blocks, revenue and projection do not continue.

If revenue promotion blocks, projection does not continue.

Projection checkpoint conflict or integrity failure blocks the cycle.

This preserves fail-closed downstream semantics.

## Bounded drain semantics

Each downstream stage can execute multiple batches, bounded by:

```text
max batches
batch size
global deadline
cancellation
```

A stage reaching its batch cap is not corruption.

It returns:

```text
drained = false
```

and the next invocation can continue.

## Concurrency

TASK-0155 does not introduce a global mutex.

Existing authorities already handle concurrency:

```text
ConnectorRuntime       -> stale progress / page commit idempotency
V022 identity          -> unique external identity convergence
economic evidence      -> optimistic version / duplicate / source conflict
V023 terminal ledger   -> immutable terminal replay
Sales projection       -> monotonic change sequence / checkpoint conflict
```

Concurrent pipeline runs may produce explicit conflicts or no-ops, but must not
silently corrupt state.

## No automatic credential refresh

TASK-0155 does not refresh OAuth credentials inline.

Authentication-required remains an explicit ConnectorRuntime source outcome.

Credential rotation remains the separately governed execution bridge.

## No scheduler yet

TASK-0155 is a callable bounded cycle, not a daemon.

It does not decide:

- recurrence;
- cron frequency;
- fairness across organizations;
- distributed worker leases;
- fleet scheduling;
- backoff persistence.

TASK-0156 can expose a controlled refresh command alongside Sales Intelligence
read APIs, and production scheduling can be governed separately later.

## MVP consequence

After TASK-0155 the backend will have one callable path:

```text
Mercado Livre
  -> V021
  -> MarketplaceOrderId
  -> OrderOccurrence
  -> REVENUE
  -> Economic Truth
  -> Sales Intelligence projection
```

TASK-0156 then only needs to wire concrete adapters and expose safe HTTP
commands/reads.

Next:

```text
TASK-0156 Sales Intelligence API + controlled refresh
-> TASK-0157 MVP UI
```