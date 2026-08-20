# ADR-0037: Marketplace Net-Back Price Position Transition Boundary

Status: Proposed

Date: 2026-08-20

## Context

TASK-0128 retains two exact assessments of the same observed-price fact: one
against the source Product Cost floor and one against the selected-cost derived
floor. Callers can inspect both positions, but the repository has no canonical
vocabulary for their exact source-to-derived pair.

The next smallest useful question is:

> Which exact economic price-position region did the same observed-price fact
> occupy before and after the accepted Product Cost floor change?

This is a deterministic classification over two already-accepted diagnostic
facts. It must not turn the transition into an improvement, deterioration,
material change, preference, or pricing instruction.

## Decision

Introduce a pure, production-inactive position-transition projection in the
existing Marketplace pricing package.

It accepts one complete `NetBackComparablePriceEvidence` and retains that exact
aggregate beside one canonical transition value.

The transition is derived only from:

```text
evidence.sourceAssessment.position
evidence.derivedAssessment.position
```

No price, gap, floor, Product Cost, quality, source, time, or policy is
recalculated.

## Exact transition taxonomy

The existing four positions produce exactly sixteen ordered pairs:

```text
BELOW_ABSOLUTE_TO_BELOW_ABSOLUTE
BELOW_ABSOLUTE_TO_BELOW_ECONOMIC
BELOW_ABSOLUTE_TO_AT_ECONOMIC
BELOW_ABSOLUTE_TO_ABOVE_ECONOMIC

BELOW_ECONOMIC_TO_BELOW_ABSOLUTE
BELOW_ECONOMIC_TO_BELOW_ECONOMIC
BELOW_ECONOMIC_TO_AT_ECONOMIC
BELOW_ECONOMIC_TO_ABOVE_ECONOMIC

AT_ECONOMIC_TO_BELOW_ABSOLUTE
AT_ECONOMIC_TO_BELOW_ECONOMIC
AT_ECONOMIC_TO_AT_ECONOMIC
AT_ECONOMIC_TO_ABOVE_ECONOMIC

ABOVE_ECONOMIC_TO_BELOW_ABSOLUTE
ABOVE_ECONOMIC_TO_BELOW_ECONOMIC
ABOVE_ECONOMIC_TO_AT_ECONOMIC
ABOVE_ECONOMIC_TO_ABOVE_ECONOMIC
```

The names encode source first and derived second. They contain no ordinal
score, favorable/unfavorable label, or implied direction of action.

Classification uses exhaustive branches over both accepted position enums. It
does not use enum ordinals, strings, maps supplied by callers, fallback values,
or a default branch that could hide a future position.

## Controlled aggregate

```text
NetBackCostBasisPricePositionTransition
  evidence
  transition
```

Internal construction reproduces the transition from the retained assessments.
The aggregate and projection render `[REDACTED]`.

The projection has no controlled failure because its only input is a complete,
internally valid comparable-price evidence aggregate. It reads neither partial
assessments nor raw observations.

## Diagnostic fact, not judgment

For the accepted `143.20 -> 48.00` floor fixture at observed price `100.00`, the
transition is:

```text
BELOW_ABSOLUTE_TO_ABOVE_ECONOMIC
```

This means only that the same price fact is below the source absolute floor and
above the derived economic floor. It does not prove that the selected Product
Cost is available, preferable, realized, market competitive, or authorized.

The projection derives no:

- changed/unchanged severity or materiality;
- ordinal distance or number of regions crossed;
- percentage, ratio, score, confidence, or probability;
- preferred Product Cost basis or economic objective;
- recommended price, price change, approval, decision, authority, or action.

## No infrastructure activation

The projection adds no persistence, migration, API, serialization, event,
connector, worker, scheduler, UI, alert, experiment, AI, agent, external price
mutation, rollback, or Kernel change.

## Consequences

### Positive

- all exact source-to-derived position pairs receive one canonical vocabulary;
- later policy can consume a stable fact without reclassifying positions;
- exhaustive classification makes future enum growth fail visibly;
- complete observation, floor, and Product Cost lineage remains retained;
- no recommendation or authority leaks into the diagnostic layer.

### Negative

- sixteen explicit values are intentionally verbose;
- the taxonomy does not rank or summarize transitions;
- callers must not infer that `TO_ABOVE_ECONOMIC` is automatically preferable;
- materiality and objective fitness remain unavailable.

## Alternatives considered

Returning only `changed` or `unchanged` was rejected because it discards the
exact pair already available. Numeric ranks and signed distances were rejected
because they introduce ordinal semantics not yet accepted. Names such as
`IMPROVED` and `DETERIORATED` were rejected because they make a business
judgment without objective or policy. Recomputing either position was rejected
because TASK-0128 already retains the accepted assessments. Adding a
recommendation was rejected because market, inventory, simulation, policy, and
authority evidence are absent. Kernel promotion was rejected because this
remains Marketplace pricing vocabulary.

## Authorization

This ADR alone authorizes no implementation. SPEC-0037 may authorize only the
pure exact-transition taxonomy, exhaustive classifier, invariant retention,
redaction, and focused tests for TASK-0130.

No ordinal rank, percentage, materiality, objective, preferred basis,
recommendation, authority, action, persistence, API, AI, or Kernel modification
is authorized.
