# ADR-0033: Marketplace Net-Back Source Scenario Floor Boundary

Status: Proposed

Date: 2026-08-15

## Context

TASK-0117 calculates the Net-Back floor of a Product-Cost-applied derived
scenario and retains the full application lineage. A complete result exposes
the derived floor, source profile, selected Product Cost evidence, original and
applied components, unit identity, policies, and caller times.

It intentionally does not calculate the source profile. The repository can now
answer what floor follows from the selected cost, but cannot place beside it
the deterministic floor that follows from the original profile.

The next smallest question is:

> What accepted Net-Back result follows from the exact source profile retained
> by this completed derived-scenario calculation?

Calculating deltas in the same step would combine source calculation with a new
comparison contract. It would also encourage consumers to interpret a lower or
higher floor before an economic objective, feasibility, or policy exists.

## Decision

Introduce a pure, production-inactive source-scenario floor projection in the
existing Marketplace pricing package.

It accepts only a complete `NetBackAppliedScenarioFloor` and invokes:

```text
MarketplaceNetBackEconomicFloor.calculate(
  appliedScenarioFloor.appliedScenario.sourceProfile
)
```

It retains the complete applied-scenario floor with every source calculation
family. It does not accept an arbitrary source profile or recalculate the
derived profile.

## Existing calculator remains sovereign

The projection adds no formula or policy. Coverage, cost netting, denominators,
quantum rounding, money bounds, calculation version, and truth quality remain
owned by `MarketplaceNetBackEconomicFloor`.

The source profile is used exactly as frozen by TASK-0114. No Product Cost
evidence, source fact, coverage, target, unit, or policy is replaced.

## Result families

```text
NetBackSourceScenarioFloorResult
  Calculated(NetBackSourceScenarioFloor)
  Incomplete(appliedScenarioFloor, calculation)
  Unachievable(appliedScenarioFloor, calculation)
```

The complete aggregate retains:

```text
appliedScenarioFloor
sourceFloor
```

All constructors reproduce their exact generic source-profile calculation and
all new renderings are `[REDACTED]`.

Current TASK-0114 invariants copy coverage unchanged, so a complete derived
floor normally implies complete source coverage. The Incomplete family remains
an explicit fail-closed mapping of the calculator contract rather than an
unchecked assumption that could become unsafe if upstream contracts evolve.

## No comparison yet

Although a successful aggregate retains both source and derived floors, it
derives no:

- absolute-floor delta;
- economic-floor delta;
- percentage or ratio;
- increase/decrease classification;
- preferred cost basis;
- price feasibility;
- objective fitness;
- recommendation;
- decision or action.

The two floors are deterministic scenario facts. Their difference and meaning
require a separate accepted boundary.

## No infrastructure activation

The projection adds no ID, time, clock, persistence, migration, API,
serialization, event, connector, worker, scheduler, UI, notification,
experiment, agent, AI, external mutation, or Kernel change.

## Consequences

### Positive

- source and derived floors retain one continuous evidence lineage;
- the generic calculator remains the only mathematical authority;
- source unachievability remains explicit rather than disappearing;
- the later comparison boundary can consume two accepted calculations;
- no diagnostic difference is mistaken for a recommendation.

### Negative

- the output deliberately adds no delta or interpretation;
- consumers retain another aggregate layer;
- source calculation may be unachievable even when the lower-cost derived
  scenario is calculable.

## Alternatives considered

Calculating the source profile independently was rejected because it loses the
link to the derived calculation. Adding source floor fields to TASK-0117 was
rejected because that retroactively broadens an accepted type. Calculating
deltas immediately was rejected because source calculation and comparison are
separate responsibilities. Reusing the derived floor for the source was
rejected because the profiles contain different Product Cost evidence. Kernel
promotion was rejected because this remains Marketplace pricing vocabulary.

## Authorization

This ADR alone authorizes no implementation. SPEC-0033 may authorize only the
pure source-scenario floor projection, result-family preservation, invariant
reproduction, redaction, and focused tests for TASK-0119.

No delta, comparison, objective, recommendation, authority, action,
persistence, API, AI, or Kernel modification is authorized.
