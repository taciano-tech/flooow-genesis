# SPEC-0028: Marketplace Pricing Product Cost Basis

Status: Proposed

Date: 2026-08-14

Source decision: ADR-0028

## Objective

Represent historical acquisition, current replacement, and forward replacement
unit-cost evidence as separate temporal facts and derive an exact auditable cost
trajectory without selecting a pricing action.

## Authorized next implementation

Acceptance authorizes TASK-0108 only:

1. add pure identities, unit key, basis enum, assumption version, and cost
   evidence values in the Marketplace pricing package;
2. add a versioned current-age and forward-horizon policy;
3. require one selected evidence item per basis for a complete snapshot;
4. validate ownership, marketplace, unit, currency, identity, source fact,
   source time, applicability time, and policy windows;
5. return exact missing bases without interpreting absence as zero;
6. derive three exact signed unit-cost deltas;
7. propagate confirmed/estimated evidence quality;
8. prove boundary, time, mismatch, missing, duplicate, zero, delta, quality,
   determinism, privacy, and immutability behavior with pure tests.

No persistence, API, connector, ingestion, FX conversion, landed-cost formula,
Net-Back change, recommendation, decision, action, AI, or Kernel change is
authorized.

## Values

```text
PricingProductCostEvidenceId       canonical caller UUID, [INTERNAL]
PricingCostUnitKey                 1-100 lowercase letters/digits/dot/hyphen
PricingCostAssumptionVersion       canonical bounded policy text, [REDACTED]

PricingProductCostBasis
  HISTORICAL_ACQUISITION
  CURRENT_REPLACEMENT
  FORWARD_REPLACEMENT
```

The unit key is a normalized commercial-unit identity established upstream,
not a SKU, title, measurement formula, or conversion instruction.

## Evidence

```text
PricingProductCostEvidence(
  organizationId,
  scenarioId,
  marketplace,
  evidenceId,
  unitKey,
  basis,
  unitCost,
  source,
  occurredAt,
  applicableAt,
  quality,
  assumptionVersion
)
```

Unit cost is non-negative exact `MarketplaceMoney`. Source and applicability
times use microsecond precision. ERP sources require their existing stable
external reference rule. Aggregate rendering is `[REDACTED]`.

## Policy

```text
PricingCostBasisPolicyVersion
PricingCostBasisPolicy(
  version,
  maximumCurrentReplacementAge,
  maximumForwardHorizon
)
```

Both durations are positive, microsecond-precise, and at most 730 days. The
current maximum age may differ from the forward horizon. The evaluator reads no
clock.

## Evaluation

```text
MarketplacePricingProductCostBasis.evaluate(
  evidences,
  policy,
  evaluatedAt
)
```

`evaluatedAt` uses microsecond precision. Evaluation requires:

1. every source occurrence time `<= evaluatedAt`;
2. current source occurrence in
   `[evaluatedAt - maximumCurrentReplacementAge, evaluatedAt]`;
3. current applicability in
   `[evaluatedAt - maximumCurrentReplacementAge, evaluatedAt]`;
4. historical applicability `<=` current applicability;
5. forward applicability `> evaluatedAt` and
   `<= evaluatedAt + maximumForwardHorizon`;
6. common organization, scenario, marketplace, currency, and unit key;
7. unique evidence IDs;
8. unique basis values;
9. unique `(basis, source kind, system key, external-reference state)` facts.

No evidence is rounded, converted, dropped, or reclassified.

## Missing bases

Before complete validation, zero or more absent bases produce:

```text
MissingCostBasis(missingBases)
```

The set follows enum order, is immutable, non-empty, and `[REDACTED]`. A
duplicate present basis is not missing; it is `DuplicateCostBasis`.

## Complete snapshot

```text
PricingProductCostBasisAssessment(
  organizationId,
  scenarioId,
  marketplace,
  currency,
  unitKey,
  historicalEvidence,
  currentReplacementEvidence,
  forwardReplacementEvidence,
  currentChangeFromHistorical,
  forwardChangeFromCurrent,
  forwardChangeFromHistorical,
  quality,
  policyVersion,
  maximumCurrentReplacementAge,
  maximumForwardHorizon,
  evaluatedAt
)
```

Exact deltas are:

```text
current.unitCost - historical.unitCost
forward.unitCost - current.unitCost
forward.unitCost - historical.unitCost
```

Construction is internal and revalidates ownership, basis placement, currency,
unit, time ordering, exact deltas, quality, and policy values. Rendering is
`[REDACTED]`.

Quality is confirmed only when every evidence quality is confirmed; otherwise
it is estimated.

## Controlled result

```text
PricingProductCostBasisResult
  Assessed(assessment)
  MissingCostBasis(evidence)
  DuplicateCostBasis
  DuplicateEvidence
  OwnershipMismatch
  MarketplaceMismatch
  CurrencyMismatch
  UnitMismatch
  SourceTimeViolation
  ApplicabilityViolation
```

All aggregate variants render `[REDACTED]`.

## Acceptance fixture

```text
historical 41.00 applicable 2026-05-01 CONFIRMED
current    48.00 applicable 2026-08-14 CONFIRMED
forward    52.00 applicable 2026-11-12 ESTIMATED

current from historical  +7.00
forward from current      +4.00
forward from historical  +11.00
quality ESTIMATED
```

Explicit zero costs remain present evidence. Collection permutations produce
value-equal output.

## Test plan

TASK-0108 proves at least:

1. cost-basis bytecode references no Kernel type;
2. caller UUID, unit key, versions, non-negative exact cost, microsecond times,
   provenance, and redacted rendering;
3. positive bounded policy durations and caller evaluation time;
4. every missing-basis combination and deterministic enum order;
5. duplicate basis and evidence identity/source facts fail closed;
6. ownership, scenario, marketplace, currency, and unit mismatch;
7. future source occurrence and stale current source/applicability failure;
8. historical/current/forward applicability boundaries;
9. forward horizon boundary and overflow fail closed;
10. exact positive, zero, and negative deltas;
11. explicit zero cost remains evidence;
12. confirmed quality requires all confirmed facts;
13. any estimated fact makes the snapshot estimated;
14. permutations return value-equal output and inputs remain unchanged;
15. aggregate collections are immutable and rendering is redacted;
16. no Net-Back, recommendation, API, persistence, connector, or runtime change;
17. no file under `platform/foundation/kernel` changes;
18. `git diff --check` and complete repository build remain green.

## Remaining boundary

Source selection, history, multiple quotes, landed-cost calculation, FX and
freight sensitivity, tax/duty rules, MOQ, supplier comparison, probability
ranges, unit conversion, Net-Back cost-basis policy, economic objectives,
simulation, recommendation, authority, execution, outcome, API/UI, and alerts
require later accepted specifications.

## Acceptance

Merging ADR-0028 and SPEC-0028 authorizes TASK-0108 only. It changes no runtime
behavior and authorizes no price recommendation, cost mutation, AI, or Kernel
modification.
