# SPEC-0037: Marketplace Net-Back Price Position Transition

Status: Proposed

Date: 2026-08-20

Source decision: ADR-0037

## Objective

Classify the exact ordered pair of source and selected-cost derived economic
price positions already retained by one complete comparable-price evidence
aggregate, without ranking, interpreting, recommending, or acting on it.

## Authorized next implementation

Acceptance authorizes TASK-0130 only:

1. add one pure projection in the Marketplace pricing package;
2. accept one complete `NetBackComparablePriceEvidence` as the only input;
3. introduce one sixteen-value exact transition enum;
4. classify source position first and derived position second with exhaustive
   branches;
5. retain the same evidence instance and exact transition on success;
6. reproduce the transition invariant internally;
7. render the new aggregate and projection as `[REDACTED]`;
8. prove all sixteen pairs, determinism, immutability, privacy, and scope with
   focused tests and complete repository verification.

No position recalculation, ordinal rank, percentage, materiality, economic
objective, preferred Product Cost basis, recommendation, decision, authority,
action, persistence, API, connector, AI, or Kernel change is authorized.

## Input and projection

```text
MarketplaceNetBackCostBasisPricePositionTransition.classify(
  evidence: NetBackComparablePriceEvidence
): NetBackCostBasisPricePositionTransition
```

The projection reads exactly:

```text
source = evidence.sourceAssessment.position
derived = evidence.derivedAssessment.position
```

It must not call `MarketplaceEconomicPricePosition`, recalculate a floor or
gap, reconstruct Product Cost lineage, copy an observation, or derive another
quality, source, time, or policy.

There is no generated ID, clock, random source, database, network, environment,
or framework dependency.

## Transition type

```text
enum class NetBackCostBasisPricePositionTransitionType {
  BELOW_ABSOLUTE_TO_BELOW_ABSOLUTE,
  BELOW_ABSOLUTE_TO_BELOW_ECONOMIC,
  BELOW_ABSOLUTE_TO_AT_ECONOMIC,
  BELOW_ABSOLUTE_TO_ABOVE_ECONOMIC,

  BELOW_ECONOMIC_TO_BELOW_ABSOLUTE,
  BELOW_ECONOMIC_TO_BELOW_ECONOMIC,
  BELOW_ECONOMIC_TO_AT_ECONOMIC,
  BELOW_ECONOMIC_TO_ABOVE_ECONOMIC,

  AT_ECONOMIC_TO_BELOW_ABSOLUTE,
  AT_ECONOMIC_TO_BELOW_ECONOMIC,
  AT_ECONOMIC_TO_AT_ECONOMIC,
  AT_ECONOMIC_TO_ABOVE_ECONOMIC,

  ABOVE_ECONOMIC_TO_BELOW_ABSOLUTE,
  ABOVE_ECONOMIC_TO_BELOW_ECONOMIC,
  ABOVE_ECONOMIC_TO_AT_ECONOMIC,
  ABOVE_ECONOMIC_TO_ABOVE_ECONOMIC
}
```

The enum declaration order has no business meaning. Production code must not
read `ordinal`, compare enum order, or translate the transition into a signed
number.

The enum itself carries no weight, severity, label, score, display text,
recommendation, or authority.

## Exhaustive classifier

Classification uses nested exhaustive branches:

```text
when (source) {
  BELOW_ABSOLUTE_FLOOR -> when (derived) { all four positions }
  BELOW_ECONOMIC_FLOOR -> when (derived) { all four positions }
  AT_ECONOMIC_FLOOR    -> when (derived) { all four positions }
  ABOVE_ECONOMIC_FLOOR -> when (derived) { all four positions }
}
```

There is no `else`, string construction, enum-name parsing, caller-supplied
mapping, or fallback. Any future addition to `EconomicPricePosition` must make
the classifier fail compilation until the new transition contract is accepted.

## Successful aggregate

```text
NetBackCostBasisPricePositionTransition(
  evidence,
  transition
)
```

Internal construction requires the retained transition to equal the exhaustive
classification of the retained source and derived assessments. The aggregate
renders `[REDACTED]`.

The exact input evidence remains connected to:

- source and selected Product Cost evidence;
- source and derived profiles and Net-Back floors;
- exact monetary floor deltas;
- both explicit scenario-owned representations of one observed-price fact;
- exact source and derived gaps, positions, qualities, policies, provenance,
  identity, and time.

The aggregate adds no ID, timestamp, policy, source, quality, or duplicated
position fields.

## No failure result

The only public input is a complete `NetBackComparablePriceEvidence` whose
constructor already reproduced both assessments and the same-fact invariant.
All pairs of accepted position values have an exact transition. Therefore the
projection returns the aggregate directly and defines no failure, null,
exception fallback, or partial output.

## Accepted fixture

Given:

```text
source position = BELOW_ABSOLUTE_FLOOR
derived position = ABOVE_ECONOMIC_FLOOR
```

The exact transition is:

```text
BELOW_ABSOLUTE_TO_ABOVE_ECONOMIC
```

Source position remains first even when the derived floor is numerically lower.
No inversion or preferred-direction normalization is allowed.

## Determinism and privacy

- value-equal evidence returns value-equal transition aggregates;
- the successful output retains the same evidence instance;
- the input remains unchanged;
- no ID, time, version, source, policy, or confidence is generated;
- the aggregate and projection render `[REDACTED]`;
- enum names disclose only the accepted diagnostic regions and no amounts,
  organization, scenario, observation, source, or time.

## Implementation scope

TASK-0130 may add only:

- `MarketplaceNetBackCostBasisPricePositionTransition.kt` in Marketplace
  pricing;
- `MarketplaceNetBackCostBasisPricePositionTransitionTest.kt`;
- TASK-0130 evidence.

No existing production type needs modification.

## Test plan

TASK-0130 proves at least:

1. projection bytecode references no Kernel type;
2. the only public input is complete comparable-price evidence;
3. the transition enum has exactly the sixteen accepted values;
4. every source position maps with every derived position in exact order;
5. no enum ordinal, string parsing, caller mapping, or fallback is used;
6. the accepted fixture maps to
   `BELOW_ABSOLUTE_TO_ABOVE_ECONOMIC`;
7. internal construction rejects a transition inconsistent with the retained
   assessments;
8. output retains the same evidence instance;
9. value-equal inputs are deterministic and immutable;
10. aggregate and projection rendering is `[REDACTED]`;
11. the aggregate contains only evidence and transition fields;
12. no rank, distance, percentage, severity, materiality, preference,
    recommendation, authority, or action is introduced;
13. no API, persistence, runtime, connector, event, or AI is added;
14. no file under `platform/foundation/kernel` changes;
15. `git diff --check` and complete repository build remain green.

## Remaining boundary

Transition grouping or materiality, percentage or relative floor change,
economic objective, market competitiveness, price simulation, preferred
Product Cost basis, recommendation, authority, execution, outcome, persistence,
API/UI, quantity/kit conversion, and multiple Product Cost allocation require
later accepted specifications.

## Acceptance

Merging ADR-0037 and SPEC-0037 authorizes TASK-0130 only. It changes no runtime
behavior and authorizes no recommendation, decision, action, AI, or Kernel
modification.
