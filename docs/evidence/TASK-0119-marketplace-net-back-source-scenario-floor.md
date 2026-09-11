# TASK-0119: Marketplace Net-Back Source Scenario Floor

Status: Implemented and locally verified

Date: 2026-08-15

Contract: ADR-0033 / SPEC-0033

## Delivered boundary

The Marketplace pricing vertical now calculates the accepted Net-Back result
for the exact source profile retained by one complete applied-scenario floor.

The projection:

- accepts only `NetBackAppliedScenarioFloor`;
- invokes `MarketplaceNetBackEconomicFloor` against its retained source
  profile;
- maps Complete, Incomplete, and Unachievable without loss;
- retains the complete derived-scenario floor in every result family;
- internally reproduces source calculation invariants;
- renders all new results as `[REDACTED]`.

No delta, ratio, direction, comparison, objective, feasibility, recommendation,
decision, authority, action, persistence, API, connector, AI, runtime
activation, or Kernel change was added.

## Acceptance evidence

The focused suite proves 9 scenarios with zero failures:

- no Kernel reference and only the complete derived floor is accepted;
- the source `143.20` fixture produces source floors of `143.20`;
- the retained derived floor remains exactly `48.00`;
- source ownership, unit, quantum, policies, target, components, and quality
  equal the generic source calculation;
- source and selected Product Cost evidence remain distinct and unchanged;
- the successful aggregate has only source and derived calculation lineage,
  with no delta, comparison, or recommendation field;
- an out-of-range source can return Unachievable while the lower-cost derived
  floor remains complete;
- mismatched complete, incomplete, and unachievable internal construction is
  rejected;
- permutations are deterministic and inputs remain unchanged;
- calculated and unachievable renderings are redacted.

## Verification

Focused boundary:

```text
./gradlew.bat :applications:marketplace-operations:test \
  --tests io.flooow.marketplace.operations.economics.pricing.MarketplaceNetBackSourceScenarioFloorTest \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process

BUILD SUCCESSFUL
9 tests, 0 failures, 0 errors, 0 skipped
```

Complete Marketplace module:

```text
./gradlew.bat :applications:marketplace-operations:test \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process

BUILD SUCCESSFUL
172 tests, 19 suites, 0 failures, 0 errors, 0 skipped
```

Repository build, excluding only the local PostgreSQL Testcontainers suite:

```text
./gradlew.bat build \
  -x :applications:marketplace-operations-persistence-postgres:test \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process

BUILD SUCCESSFUL in 2m 10s
69 actionable tasks: 7 executed, 62 up-to-date
```

`git diff --check` passed. No file under `platform/foundation/kernel` changed.

## Remaining boundary

Exact source/derived floor deltas, comparison classification, percentage,
economic objective, feasibility, simulation, preferred basis, recommendation,
authority, execution, outcome, persistence, API/UI, quantity/kit conversion,
and multiple Product Cost allocation remain outside this task.
