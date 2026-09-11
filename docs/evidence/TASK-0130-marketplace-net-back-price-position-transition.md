# TASK-0130: Marketplace Net-Back Price Position Transition

## Result

Implemented ADR-0037 / SPEC-0037 as one pure, production-inactive transition
projection in the existing Marketplace pricing package.

No existing production type, Kernel type, persistence adapter, API, connector,
runtime, recommendation, decision, authority, or price-execution behavior was
changed.

## Exact transition taxonomy

The implementation defines the sixteen exact ordered pairs produced by the four
accepted economic price positions. Source position is always encoded first and
selected-cost derived position second.

Classification uses nested exhaustive branches over
`EconomicPricePosition`. Production source contains no caller mapping, enum-name
parsing, default branch, fallback, explicit ordinal access, rank, or signed
distance.

The successful aggregate retains only:

```text
complete comparable-price evidence
exact transition
```

Internal construction reproduces the transition from the retained source and
derived assessments. Aggregate and projection rendering is `[REDACTED]`.

## Accepted fixture

For source Product Cost `143.20`, selected Product Cost `48.00`, equal absolute
and economic floors, and observed price `100.00`:

```text
source position  = BELOW_ABSOLUTE_FLOOR
derived position = ABOVE_ECONOMIC_FLOOR
transition       = BELOW_ABSOLUTE_TO_ABOVE_ECONOMIC
floor delta      = -95.20
```

The transition is a diagnostic fact only. It carries no favorable/unfavorable
meaning and authorizes no Product Cost or price decision.

## Exhaustive validation

The focused suite constructs valid Product Cost selections, derived profiles,
Net-Back floors, floor deltas, same-fact observations, and paired assessments
for every one of the sixteen position combinations.

No assessment or aggregate is mocked to reach a transition.

Focused suite:

```text
MarketplaceNetBackCostBasisPricePositionTransitionTest
7 tests
0 failures
0 errors
0 skipped
all 16 ordered position pairs covered
```

Complete Marketplace module:

```text
23 suites
207 tests
0 failures
0 errors
0 skipped
```

Broad repository build, excluding only the local PostgreSQL/Testcontainers
test task:

```text
BUILD SUCCESSFUL in 2m 54s
78 actionable tasks
```

The GitHub CI remains the authority for the complete build and persistent
runtime package with Docker.

Additional checks prove:

- transition bytecode contains no Kernel or price-evaluator reference;
- the only public projection input is complete comparable-price evidence;
- the enum contains exactly the sixteen accepted values;
- the accepted fixture preserves evidence and exact floor deltas;
- internal construction rejects an inconsistent transition;
- value-equal inputs are deterministic and immutable;
- output retains the same evidence instance;
- aggregate fields are exactly evidence and transition;
- no materiality, preference, recommendation, authority, or action field is
  introduced.

## Deliberately absent

- transition rank, distance, changed/unchanged grouping, or severity;
- percentage, ratio, tolerance, score, or materiality;
- preferred Product Cost basis or economic objective;
- market competitiveness, simulation, or optimal price;
- recommendation, approval, decision, authority, or action;
- persistence, API, UI, event, connector, worker, agent, or AI;
- Kernel vocabulary or behavior.

## Boundary conclusion

Marketplace Pricing Intelligence now has an exhaustive, canonical vocabulary
for the diagnostic movement of one price fact across source and selected-cost
Net-Back floors. Any judgment over that transition remains a later contract.
