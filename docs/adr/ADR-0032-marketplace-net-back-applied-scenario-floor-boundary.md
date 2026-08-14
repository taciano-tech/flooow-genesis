# ADR-0032: Marketplace Net-Back Applied Scenario Floor Boundary

Status: Proposed

Date: 2026-08-14

## Context

TASK-0114 can immutably apply one selected Product Cost Basis to a distinct,
unit-compatible Net-Back profile. It deliberately stops before calculating a
floor.

The repository can explain which source profile, Product Cost assessment,
selected evidence, policy, caller time, and original component produced the
derived profile. It cannot yet answer:

> What absolute and target-contribution floors result from this exact derived
> profile?

Calling the generic calculator directly is mathematically valid, but its result
does not retain the application aggregate. A later comparison could then
receive a floor without the Product Cost lineage that explains why it exists.

Unsafe shortcuts would be to mutate the source, introduce a second formula,
flatten incomplete or unachievable results, calculate and compare both scenarios
at once, generate identity or time, activate infrastructure, or add Marketplace
pricing language to the Kernel.

## Decision

Introduce a pure, production-inactive applied-scenario floor projection in the
existing Marketplace pricing package.

It accepts only a completed `NetBackCostBasisAppliedScenario` and delegates:

```text
MarketplaceNetBackEconomicFloor.calculate(
  appliedScenario.derivedProfile
)
```

The existing calculator remains sovereign over coverage, fixed and rate
netting, denominators, quantum rounding, money bounds, calculation policy, and
truth quality. No evidence is substituted in this step.

## Result families

The output mirrors the calculator without losing lineage:

```text
NetBackAppliedScenarioFloorResult
  Calculated(NetBackAppliedScenarioFloor)
  Incomplete(appliedScenario, calculation)
  Unachievable(appliedScenario, calculation)
```

`NetBackAppliedScenarioFloor` retains the applied scenario and complete floor.
It internally reproduces the calculation and rejects a mismatched floor.
Incomplete and Unachievable retain the application and exact generic result.
All variants render `[REDACTED]`.

The lineage remains:

```text
derived floor -> derived profile -> applied Product Cost component
  -> selected evidence -> Product Cost assessment
  -> original Product Cost component -> source profile
```

## No comparison or decision

This boundary does not calculate the source-profile floor or derive floor
deltas, percentage change, feasibility, objective fitness, preferred basis,
recommendation, authority, or action. A deterministic scenario floor is not
evidence that the organization should execute that price.

It adds no persistence, migration, API, serialization, event, connector,
worker, scheduler, UI, experiment, agent, AI, external mutation, or Kernel
change.

## Consequences

### Positive

- accepted mathematics is reused without drift;
- every outcome retains Product Cost application lineage;
- incomplete and unachievable scenarios remain explicit;
- later comparison receives a strongly explained derived result;
- decision authority remains outside deterministic calculation.

### Negative

- consumers retain a larger aggregate than a standalone floor;
- the projection adds no comparison or recommendation;
- incomplete coverage and impossible denominators still fail closed.

## Alternatives considered

Returning the generic result directly was rejected because it severs application
lineage. Adding selection fields to the generic floor was rejected because that
floor supports unrelated profiles. Calculating source and target together was
rejected because it prematurely creates a comparison boundary. Revalidating
selection was rejected because TASK-0114 already closes that step. Kernel
promotion was rejected because the need remains Marketplace-specific.

## Authorization

This ADR alone authorizes no implementation. SPEC-0032 may authorize only the
pure applied-scenario calculation projection, lineage-preserving results,
redaction, and focused tests for TASK-0117.

No comparison, recommendation, decision, authority, action, persistence, API,
AI, or Kernel modification is authorized.
