# TASK-0121: Marketplace Net-Back Cost-Basis Floor Delta

Status: Implemented and locally verified

Date: 2026-08-15

Contract: ADR-0034 / SPEC-0034

## Delivered boundary

The Marketplace pricing vertical now derives the exact signed monetary change
between accepted source and selected-cost Net-Back floors.

The projection:

- accepts only one complete `NetBackSourceScenarioFloor`;
- subtracts source from derived for each matching floor;
- retains the complete source-to-derived Product Cost lineage;
- preserves exact signed `MarketplaceMoney` values;
- internally reproduces both subtraction invariants;
- renders the aggregate and projection object as `[REDACTED]`.

No percentage, ratio, direction type, classification, tolerance, materiality,
objective, feasibility, preference, recommendation, decision, authority,
action, persistence, API, connector, AI, runtime activation, or Kernel change
was added.

## Acceptance evidence

The focused suite proves 9 scenarios with zero failures:

- production bytecode contains no Kernel reference and accepts only a complete
  source-scenario floor;
- the accepted `143.20` source and `48.00` derived fixture returns exact
  `-95.20` absolute and economic deltas;
- equal source and selected Product Cost returns exact zero;
- a higher selected Product Cost returns exact positive deltas;
- variable rate plus a margin target produces independently calculated
  absolute `-65.00` and economic `-74.28` deltas;
- source and derived floors, evidence, units, currency, and policies remain
  exact and unchanged;
- mismatched internal delta construction is rejected;
- component permutations are deterministic and inputs remain unchanged;
- the aggregate exposes facts only and all new renderings are redacted.

## Verification

Focused boundary:

```text
./gradlew.bat :applications:marketplace-operations:test \
  --tests io.flooow.marketplace.operations.economics.pricing.MarketplaceNetBackCostBasisFloorDeltaTest \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process

BUILD SUCCESSFUL in 1m 25s
9 tests, 0 failures, 0 errors, 0 skipped
```

Complete Marketplace module:

```text
./gradlew.bat :applications:marketplace-operations:test \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process

BUILD SUCCESSFUL in 45s
181 tests, 20 suites, 0 failures, 0 errors, 0 skipped
```

Repository build, excluding only the local PostgreSQL Testcontainers suite:

```text
./gradlew.bat build \
  -x :applications:marketplace-operations-persistence-postgres:test \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process

BUILD SUCCESSFUL in 2m 12s
69 actionable tasks: 7 executed, 62 up-to-date
```

`git diff --check` passed. No file under `platform/foundation/kernel` changed.

## Remaining boundary

Direction classification, relative or percentage change, materiality,
tolerance, economic objective, price feasibility, simulation, preferred cost
basis, recommendation, authority, execution, outcome, persistence, API/UI,
quantity/kit conversion, and multiple Product Cost allocation remain outside
this task.
