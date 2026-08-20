# TASK-0123: Marketplace Net-Back Cost-Basis Price Position

Status: Implemented and locally verified

Date: 2026-08-20

Contract: ADR-0035 / SPEC-0035

## Delivered boundary

The Marketplace pricing vertical now evaluates one exact derived-scenario
observed gross price against the selected-cost Net-Back floor while retaining
the complete cost-basis and floor-delta lineage.

The projection:

- accepts only one complete `NetBackCostBasisFloorDelta` and one
  `ObservedMarketplacePrice`;
- delegates all validation, gaps, classification, and quality propagation to
  `MarketplaceEconomicPricePosition`;
- maps assessed and controlled mismatch results without fallback;
- retains the exact floor delta, observation, and assessment on success;
- internally reproduces the accepted evaluator result;
- renders every new aggregate and result as `[REDACTED]`.

No evidence rebinding, source-floor assessment, cross-scenario position
comparison, preferred cost basis, objective, recommendation, authority,
action, persistence, API, connector, AI, runtime activation, or Kernel change
was added.

## Acceptance evidence

The focused suite proves 10 scenarios with zero failures:

- production bytecode contains no Kernel reference and exposes only the two
  authorized inputs;
- the accepted fixture delegates the exact existing assessment and retains
  identical input instances;
- source floors and `-95.20` deltas remain unchanged context while the derived
  `48.00` floor is assessed;
- all four existing economic positions map without change;
- exact gaps, policies, source, occurrence time, identity, and estimated
  quality map without translation;
- organization, scenario, currency, and quantum mismatches map one for one;
- a source-scenario observation is rejected rather than rebound;
- internal construction rejects an assessment from another observation;
- value-equal inputs are deterministic and immutable;
- all renderings are redacted and no source position, preference,
  recommendation, authority, or action is introduced.

## Verification

Focused boundary:

```text
./gradlew.bat :applications:marketplace-operations:test \
  --tests io.flooow.marketplace.operations.economics.pricing.MarketplaceNetBackCostBasisPricePositionTest \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process

BUILD SUCCESSFUL in 1m 57s
10 tests, 0 failures, 0 errors, 0 skipped
```

Complete Marketplace module:

```text
./gradlew.bat :applications:marketplace-operations:test \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process

BUILD SUCCESSFUL in 38s
191 tests, 21 suites, 0 failures, 0 errors, 0 skipped
```

Repository build, excluding only the local PostgreSQL Testcontainers suite:

```text
./gradlew.bat build \
  -x :applications:marketplace-operations-persistence-postgres:test \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process

BUILD SUCCESSFUL in 1m 47s
69 actionable tasks: 7 executed, 62 up-to-date
```

`git diff --check` passed. No file under `platform/foundation/kernel` changed.

## Remaining boundary

Observation rebinding or explicit cross-scenario evidence, source/derived
position comparison, percentage or materiality, economic objective, market
competitiveness, price simulation, preferred cost basis, recommendation,
authority, execution, outcome, persistence, API/UI, quantity/kit conversion,
and multiple Product Cost allocation remain outside this task.
