# TASK-0117: Marketplace Net-Back Applied Scenario Floor

Status: Implemented and locally verified

Date: 2026-08-14

Contract: ADR-0032 / SPEC-0032

## Delivered boundary

The Marketplace pricing vertical now calculates the accepted Net-Back result
for one exact Product-Cost-applied derived scenario while retaining complete
application lineage.

The projection:

- accepts only `NetBackCostBasisAppliedScenario`;
- invokes `MarketplaceNetBackEconomicFloor` against the exact derived profile;
- maps Complete, Incomplete, and Unachievable without loss;
- retains the application aggregate in every result family;
- internally reproduces calculation invariants;
- renders every new result as `[REDACTED]`.

No source-profile floor, comparison, delta, objective, feasibility,
recommendation, decision, authority, action, persistence, API, connector, AI,
runtime activation, or Kernel change was added.

## Acceptance evidence

The focused suite proves 10 scenarios with zero failures:

- projection bytecode has no Kernel reference;
- the public calculation input is only the applied scenario aggregate;
- source `143.20`, selected/derived `48.00` produces exact target floors of
  `48.00`;
- target ownership, unit, quantum, policies, target, components, and quality
  match the accepted generic calculator;
- the source profile and original Product Cost remain unchanged;
- output contains one target floor and no source-profile floor;
- explicit zero remains evidence and produces zero floors;
- MISSING and PARTIAL coverage retain exact Incomplete results;
- non-positive denominator retains the exact Unachievable reason;
- mismatched complete, incomplete, and unachievable internal construction is
  rejected;
- component permutations and value-equal applications are deterministic;
- all new aggregate renderings are redacted.

## Verification

Focused boundary:

```text
./gradlew.bat :applications:marketplace-operations:test \
  --tests io.flooow.marketplace.operations.economics.pricing.MarketplaceNetBackAppliedScenarioFloorTest \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process

BUILD SUCCESSFUL
10 tests, 0 failures, 0 errors, 0 skipped
```

Complete Marketplace module:

```text
./gradlew.bat :applications:marketplace-operations:test \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process

BUILD SUCCESSFUL
163 tests, 18 suites, 0 failures, 0 errors, 0 skipped
```

Repository build, excluding only the local PostgreSQL Testcontainers suite:

```text
./gradlew.bat build \
  -x :applications:marketplace-operations-persistence-postgres:test \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process

BUILD SUCCESSFUL in 1m 20s
69 actionable tasks: 7 executed, 62 up-to-date
```

`git diff --check` passed. No file under `platform/foundation/kernel` changed.

## Remaining boundary

Source-profile floor calculation, source/derived comparison, deltas, economic
objective, price feasibility, simulation, preferred basis, recommendation,
authority, execution, outcome, persistence, API/UI, quantity/kit conversion,
and multiple Product Cost allocation remain outside this task.
