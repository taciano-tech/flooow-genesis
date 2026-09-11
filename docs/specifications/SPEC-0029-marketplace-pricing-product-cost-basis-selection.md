# SPEC-0029: Marketplace Pricing Product Cost Basis Selection

Status: Proposed

Date: 2026-08-14

Source decision: ADR-0029

## Objective

Apply one explicit caller-owned policy to a complete Product Cost Basis
assessment and retain the exact selected evidence and complete lineage without
changing Net-Back or recommending a price.

## Authorized next implementation

Acceptance authorizes TASK-0110 only:

1. add pure selection policy/version values in the Marketplace pricing package;
2. accept one completed `PricingProductCostBasisAssessment` and caller-supplied
   microsecond selection time;
3. require the source assessment to be inside a bounded selection-age window;
4. revalidate current freshness or forward futurity at selection time;
5. select exactly the evidence named by policy with no fallback;
6. retain the complete assessment, exact evidence, separate evidence/snapshot
   quality, policy, and selection time;
7. return typed redacted temporal failures;
8. prove basis, time, quality, lineage, determinism, redaction, and boundary
   behavior with pure tests.

No Net-Back component/profile/floor change, economic objective, recommendation,
decision, action, persistence, API, connector, clock, AI, or Kernel change is
authorized.

## Values

```text
PricingCostBasisSelectionPolicyVersion
  canonical bounded policy text, [REDACTED]

PricingCostBasisSelectionPolicy(
  version,
  selectedBasis,
  maximumAssessmentAge
)
```

`selectedBasis` reuses `PricingProductCostBasis` exactly. The maximum age is
positive, microsecond-precise, and at most 31 days. Policy construction is
deterministic and renders `[REDACTED]`.

The 31-day upper bound limits reuse of an already evaluated pricing snapshot;
it does not assert that any specific cost quote is valid for 31 days. Current
cost freshness remains governed independently by the source assessment.

## Evaluation

```text
MarketplacePricingProductCostBasisSelection.select(
  assessment,
  policy,
  selectedAt
)
```

`selectedAt` uses microsecond precision and comes from the caller. No clock,
random source, network, database, or framework is read.

## Assessment selection window

Selection is permitted at both inclusive boundaries:

```text
assessment.evaluatedAt <= selectedAt
selectedAt <= assessment.evaluatedAt + policy.maximumAssessmentAge
```

Selection before assessment, after the maximum age, or an overflowing upper
bound returns:

```text
AssessmentOutsideSelectionWindow
```

No evidence is returned in that result.

## Exact basis selection

The policy maps directly to the source assessment slot:

```text
HISTORICAL_ACQUISITION -> assessment.historicalEvidence
CURRENT_REPLACEMENT    -> assessment.currentReplacementEvidence
FORWARD_REPLACEMENT    -> assessment.forwardReplacementEvidence
```

No timestamp, amount, quality, or collection order can override the selected
basis. There is no fallback.

## Applicability at selection

### Historical acquisition

No additional current/future claim is made. The valid complete assessment and
assessment-selection window are sufficient.

### Current replacement

Both source occurrence and applicability remain inside the inclusive window:

```text
selectedAt - assessment.maximumCurrentReplacementAge
  <= evidence.occurredAt <= selectedAt

selectedAt - assessment.maximumCurrentReplacementAge
  <= evidence.applicableAt <= selectedAt
```

Overflow or a value outside either window returns
`SelectedEvidenceOutsideApplicability`.

### Forward replacement

The selected evidence must remain strictly forward-looking:

```text
selectedAt < evidence.applicableAt
```

Equality or a later selection returns
`SelectedEvidenceOutsideApplicability`. The original assessment already proves
the evidence was inside its versioned forward horizon; selection cannot extend
that horizon.

## Successful selection

```text
PricingProductCostBasisSelection(
  sourceAssessment,
  selectedBasis,
  selectedEvidence,
  selectedEvidenceQuality,
  basisAssessmentQuality,
  selectionPolicyVersion,
  maximumAssessmentAge,
  selectedAt
)
```

Construction is internal and revalidates:

1. selected evidence is value-equal to the exact selected assessment slot;
2. selected evidence quality equals `selectedEvidence.quality`;
3. basis assessment quality equals source assessment quality;
4. policy duration and all selection-time rules;
5. current or forward applicability for the chosen basis.

The source assessment retains organization, scenario, marketplace, currency,
unit, all three evidence facts, deltas, assumption versions, source times,
applicability, source policy, and evaluation time. Aggregate rendering is
`[REDACTED]`.

## Controlled result

```text
PricingProductCostBasisSelectionResult
  Selected(selection)
  AssessmentOutsideSelectionWindow
  SelectedEvidenceOutsideApplicability
```

Every aggregate variant renders `[REDACTED]`.

## Quality semantics

`selectedEvidenceQuality` is the quality of the chosen fact.
`basisAssessmentQuality` is confirmed only when all three facts in the source
assessment are confirmed, as already established by TASK-0108.

They remain separate. Selection does not combine them into model confidence,
decision confidence, authority, or forecast accuracy.

## Acceptance fixtures

Given the TASK-0108 fixture evaluated at `2026-08-14T13:00:00.123456Z`:

```text
historical 41.00 CONFIRMED
current    48.00 CONFIRMED
forward    52.00 ESTIMATED, applicable 2026-11-12
```

An explicit `CURRENT_REPLACEMENT` policy selects exactly `48.00` and retains
all three source facts. An explicit `FORWARD_REPLACEMENT` policy selects exactly
`52.00`, reports selected evidence quality `ESTIMATED`, and does not label it a
recommended cost or price.

## Test plan

TASK-0110 proves at least:

1. selection bytecode references no Kernel type;
2. policy version, duration bounds, microsecond precision, and redaction;
3. caller selection time and no clock/random/framework dependency;
4. selection exactly maps each of the three enum bases;
5. source assessment and all evidence lineage remain value-equal;
6. lower and upper assessment-age boundaries are inclusive;
7. selection before assessment and after maximum age fail closed;
8. assessment-window overflow fails closed;
9. current source occurrence lower/upper boundaries are inclusive;
10. current applicability lower/upper boundaries are inclusive;
11. current source or applicability staleness fails closed;
12. forward applicability remains strictly after selection;
13. forward equality or elapsed applicability fails closed;
14. historical selection makes no current/future claim;
15. explicit zero selected cost remains evidence;
16. selected evidence and complete assessment qualities remain separate;
17. value-equal input returns value-equal output without mutation;
18. aggregate output and failures render `[REDACTED]`;
19. no Net-Back, recommendation, persistence, API, connector, or runtime change;
20. no file under `platform/foundation/kernel` changes;
21. `git diff --check` and complete repository build remain green.

## Remaining boundary

Mapping organizational objectives or horizons to a selection policy, applying
selected evidence to a Net-Back profile while preserving lineage, product-cost
component shape, floor comparison, simulation, recommendation, authority,
execution, outcomes, API/UI, persistence, and alerts require later accepted
specifications.

## Acceptance

Merging ADR-0029 and SPEC-0029 authorizes TASK-0110 only. It changes no runtime
behavior and authorizes no Net-Back mutation, optimal-cost claim, price
recommendation, decision, action, AI, or Kernel modification.
