# TASK-0114: Marketplace Net-Back Cost Basis Scenario Application

Status: Implemented and locally verified

Date: 2026-08-14

Contract: ADR-0031 / SPEC-0031

## Delivered boundary

The Marketplace pricing vertical now has a pure, deterministic boundary that
applies one completed Product Cost Basis selection to a distinct derived
Net-Back scenario.

The implementation:

- validates organization, source scenario, marketplace, currency, and
  normalized unit compatibility in the specified precedence;
- validates the caller-supplied application window without reading a clock;
- reproduces the accepted Product Cost selection at application time;
- requires one complete fixed-deduction Product Cost component;
- retains component identifiers and clones ownership to the target scenario;
- replaces only Product Cost value, source, and evidence quality;
- retains the complete source profile, selection, original/applied components,
  derived profile, policy, and application time;
- fails closed through typed redacted results.

No floor is calculated. No comparison, recommendation, decision, action,
persistence, API, connector, AI, or runtime activation was added.

## Acceptance evidence

The focused test class proves 17 scenarios with zero failures, including:

- no Kernel or economic-floor-calculator reference in application bytecode;
- canonical policy and microsecond time boundaries;
- exact accepted `143.20 -> 48.00` Product Cost substitution;
- unchanged source profile and deterministic target ownership;
- validation precedence and all compatibility failures;
- inclusive age boundaries, before/expired times, and overflow;
- stale current and elapsed forward selection rejection;
- missing, not-applicable, partial, multiple, rate, and addition Product Cost
  shapes failing closed;
- explicit zero evidence preservation;
- permutation determinism, input immutability, lineage, and redaction.

## Verification

Focused boundary:

```text
./gradlew.bat :applications:marketplace-operations:test \
  --tests io.flooow.marketplace.operations.economics.pricing.MarketplaceNetBackCostBasisScenarioApplicationTest \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process

BUILD SUCCESSFUL
17 tests, 0 failures, 0 errors, 0 skipped
```

Complete Marketplace module:

```text
./gradlew.bat :applications:marketplace-operations:test \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process

BUILD SUCCESSFUL
153 tests, 17 suites, 0 failures, 0 errors, 0 skipped
```

Repository build, excluding only the local PostgreSQL Testcontainers suite:

```text
./gradlew.bat build \
  -x :applications:marketplace-operations-persistence-postgres:test \
  --no-daemon -Pkotlin.compiler.execution.strategy=in-process

BUILD SUCCESSFUL in 2m 15s
69 actionable tasks: 8 executed, 61 up-to-date
```

`git diff --check` passed. No file under `platform/foundation/kernel` changed.

## Remaining boundary

Derived-floor calculation, baseline comparison, objective, price feasibility,
simulation, recommendation, authority, execution, outcome, persistence, API,
UI, quantity/kit conversion, and multiple Product Cost allocation remain
outside this task and require a later accepted specification.
