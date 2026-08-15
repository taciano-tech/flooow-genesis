# ADR-0034: Marketplace Net-Back Cost-Basis Floor Delta Boundary

Status: Proposed

Date: 2026-08-15

## Context

TASK-0119 calculates the Net-Back floors of both the source Product Cost
scenario and the selected-cost derived scenario under one complete evidence
lineage. The repository can now explain the deterministic economic floor of
each scenario without independently reconstructing either profile.

It intentionally derives no difference between those accepted results. The
next smallest question is:

> By exactly how much did the absolute and economic floors change when the
> selected Product Cost was applied?

Answering this with signed money is a factual projection. Classifying the
change as beneficial, risky, material, feasible, or preferred would require an
economic objective and policy that do not yet exist at this boundary.

## Decision

Introduce a pure, production-inactive cost-basis floor delta projection in the
existing Marketplace pricing package.

It accepts only a complete `NetBackSourceScenarioFloor` and calculates:

```text
absoluteFloorDelta = derived absolute floor - source absolute floor
economicFloorDelta = derived economic floor - source economic floor
```

The projection retains the complete source-scenario floor so every delta
remains connected to the source profile, selected Product Cost evidence,
derived profile, and both accepted floor calculations.

## Exact signed money

Both outputs use `MarketplaceMoney` in the already-shared currency and retain
the exact accepted scale of the operands. A negative amount means only that
the derived numeric floor is lower than the source numeric floor. A positive
amount means only that it is higher. Zero means only equality.

No semantic direction type, percentage, ratio, threshold, tolerance, severity,
or interpretation is introduced.

## Existing calculations remain sovereign

The projection adds no economic-floor formula and recalculates neither profile.
Source and derived floors remain owned by `MarketplaceNetBackEconomicFloor`;
the selected-cost application and lineage remain owned by their accepted
boundaries.

Internal construction must reproduce both signed differences from the retained
floors. No amount may be clamped, made absolute, rounded again, normalized to a
different unit, or converted to another currency.

## Controlled aggregate

```text
NetBackCostBasisFloorDelta
  sourceScenarioFloor
  absoluteFloorDelta
  economicFloorDelta
```

The aggregate and its projection result render `[REDACTED]` and introduce no
additional ID, time, source, version, or policy.

Because the input already contains two complete accepted floor calculations,
the projection has no incomplete or unachievable branch and cannot invent a
fallback.

## No interpretation or decision

This boundary derives no:

- increase/decrease/unchanged classification;
- percentage or ratio;
- materiality or tolerance;
- preferred Product Cost basis;
- price feasibility;
- objective fitness;
- recommendation;
- authority, decision, or action.

These exact deltas are economic facts, not judgments.

## No infrastructure activation

The projection adds no persistence, migration, API, serialization, event,
connector, worker, scheduler, UI, notification, experiment, agent, AI,
external mutation, or Kernel change.

## Consequences

### Positive

- source-to-derived changes become exact and auditably reproducible;
- absolute and target-aware economic floors remain distinct;
- signed values preserve the full mathematical result;
- later comparison policy can consume facts without recalculating scenarios;
- the Kernel remains free of Marketplace pricing vocabulary.

### Negative

- consumers must not treat the sign as a recommendation;
- no relative impact or materiality is available yet;
- another immutable aggregate is retained in the lineage.

## Alternatives considered

Recalculating profiles was rejected because both accepted calculations already
exist. Returning only one delta was rejected because absolute and economic
floors answer different questions. Absolute-value differences were rejected
because they destroy direction. Percentage deltas were rejected because their
zero-denominator and interpretation rules need a separate contract. Adding a
classification immediately was rejected because facts and judgment are
separate responsibilities. Kernel promotion was rejected because this remains
Marketplace pricing vocabulary.

## Authorization

This ADR alone authorizes no implementation. SPEC-0034 may authorize only the
pure exact-delta projection, invariant reproduction, redaction, and focused
tests for TASK-0121.

No classification, percentage, objective, recommendation, authority, action,
persistence, API, AI, or Kernel modification is authorized.
