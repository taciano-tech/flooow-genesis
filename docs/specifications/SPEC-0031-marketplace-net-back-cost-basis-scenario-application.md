# SPEC-0031: Marketplace Net-Back Cost Basis Scenario Application

Status: Proposed

Date: 2026-08-14

Source decision: ADR-0031

## Objective

Construct a new Net-Back scenario from one source profile and one explicit
Product Cost Basis selection while preserving all lineage and producing no
floor or price recommendation.

## Authorized next implementation

Acceptance authorizes TASK-0114 only:

1. add pure application policy/version values in the Marketplace pricing
   package;
2. accept a source profile, completed cost selection, distinct target scenario
   ID, policy, and caller application time;
3. validate ownership, source scenario, marketplace, currency, and unit;
4. validate application age and reproduce selection validity at application;
5. require exactly one complete fixed-deduction Product Cost component;
6. clone every component to the target scenario and substitute only the
   selected Product Cost value, source, and quality;
7. retain source profile, selection, original/applied component, derived
   profile, policy, target scenario, and time;
8. return typed redacted failures and prove behavior with pure tests.

No floor calculation, comparison, objective, recommendation, decision, action,
persistence, API, connector, AI, or Kernel change is authorized.

## Policy

```text
NetBackCostBasisApplicationPolicyVersion
NetBackCostBasisApplicationPolicy(
  version,
  maximumSelectionAge
)
```

The version uses canonical bounded policy text and renders `[REDACTED]`.
Maximum selection age is positive, microsecond-precise, and at most 31 days.

## Evaluation

```text
MarketplaceNetBackCostBasisScenarioApplication.apply(
  sourceProfile,
  costSelection,
  targetScenarioId,
  policy,
  appliedAt
)
```

`appliedAt` uses microsecond precision. No clock, random source, database,
network, or framework is read.

## Validation precedence

Failures are evaluated in this deterministic order:

1. target scenario reuses source scenario;
2. organization mismatch;
3. selected source scenario mismatch;
4. marketplace mismatch;
5. currency mismatch;
6. normalized unit mismatch;
7. application window violation;
8. selection no longer applicable;
9. unsupported Product Cost shape.

This precedence prevents lower-level shape or time details from obscuring a
fundamental ownership or compatibility failure.

## Compatibility

The selection source assessment must match the profile:

```text
sourceProfile.organizationId == assessment.organizationId
sourceProfile.scenarioId     == assessment.scenarioId
sourceProfile.marketplace    == assessment.marketplace
sourceProfile.currency       == assessment.currency
sourceProfile.unitKey        == assessment.unitKey
```

No conversion or mapping is performed.

## Application window

Both boundaries are inclusive:

```text
costSelection.selectedAt <= appliedAt
appliedAt <= costSelection.selectedAt + policy.maximumSelectionAge
```

Before-selection time, expired age, or overflow returns
`SelectionOutsideApplicationWindow`.

## Selection reproduction

At `appliedAt`, reconstruct:

```text
PricingCostBasisSelectionPolicy(
  costSelection.selectionPolicyVersion,
  costSelection.selectedBasis,
  costSelection.maximumAssessmentAge
)
```

Invoke the accepted TASK-0110 selector against
`costSelection.sourceAssessment` and `appliedAt`.

The reproduced result must be `Selected`, use the same basis and selected
evidence, and retain the same source assessment. It may have a later
`selectedAt` because it proves validity at application time.

Any controlled selector failure or lineage mismatch returns
`SelectionNoLongerApplicable`.

## Supported source shape

The source profile must satisfy:

```text
coverage[PRODUCT_COST] == COMPLETE
count(PRODUCT_COST components) == 1
direction == DEDUCTION
value is FixedAmount
```

Otherwise return `UnsupportedProductCostShape` with no partial profile.

## Derived components

For every non-Product-Cost component:

```text
targetComponent = sourceComponent.copy(scenarioId = targetScenarioId)
```

For the single Product Cost component:

```text
targetComponent = sourceComponent.copy(
  scenarioId = targetScenarioId,
  value = FixedAmount(costSelection.selectedEvidence.unitCost),
  source = costSelection.selectedEvidence.source,
  evidenceQuality = costSelection.selectedEvidenceQuality
)
```

IDs are retained. The `NetBackPricingProfile` constructor canonicalizes order,
copies collections, and revalidates ownership, currency, source facts, and
coverage.

## Derived profile

```text
NetBackPricingProfile(
  organizationId = source.organizationId,
  scenarioId = targetScenarioId,
  marketplace = source.marketplace,
  currency = source.currency,
  unitKey = source.unitKey,
  priceQuantum = source.priceQuantum,
  normalizationPolicyVersion = source.normalizationPolicyVersion,
  components = derivedComponents,
  coverage = source.coverage,
  target = source.target
)
```

No profile field other than scenario ownership and Product Cost evidence is
changed.

## Successful aggregate

```text
NetBackCostBasisAppliedScenario(
  sourceProfile,
  costSelection,
  originalProductCostComponent,
  appliedProductCostComponent,
  targetScenarioId,
  derivedProfile,
  applicationPolicyVersion,
  maximumSelectionAge,
  appliedAt
)
```

Construction is internal and exactly reproduces all compatibility, timing,
shape, component, and profile invariants. Rendering is `[REDACTED]`.

## Controlled result

```text
NetBackCostBasisScenarioApplicationResult
  Applied(appliedScenario)
  TargetScenarioReusesSource
  OwnershipMismatch
  SourceScenarioMismatch
  MarketplaceMismatch
  CurrencyMismatch
  UnitMismatch
  SelectionOutsideApplicationWindow
  SelectionNoLongerApplicable
  UnsupportedProductCostShape
```

All aggregate variants render `[REDACTED]`.

## Accepted fixture

Given a source profile with Product Cost `143.20`, target scenario `...0002`,
and a current replacement selection of `48.00`:

```text
source profile remains Product Cost 143.20
derived profile Product Cost becomes 48.00
derived Product Cost source/quality come from selected evidence
all other components remain value-equal except target scenario ownership
target and normalization policy remain unchanged
no floor is calculated
```

Explicit zero selected cost remains a fixed Product Cost component.

## Test plan

TASK-0114 proves at least:

1. application bytecode references no Kernel type;
2. policy version, duration, time precision, and redaction;
3. target scenario must differ and no random ID is generated;
4. organization and source scenario mismatch precedence;
5. marketplace, currency, and unit mismatch failures;
6. inclusive application-age boundaries;
7. before-selection, expired, and overflow application failure;
8. stale current and elapsed forward selection failure;
9. selection reproduction retains exact evidence and assessment;
10. missing, partial, not-applicable, multiple, rate, or addition Product Cost
    shape fails closed;
11. original Product Cost component remains unchanged;
12. derived Product Cost uses exact selected cost, source, and quality;
13. every component retains ID and changes only scenario ownership unless it is
    the selected Product Cost;
14. derived profile retains marketplace, currency, unit, quantum,
    normalization policy, coverage, and target;
15. explicit zero selected cost remains evidence;
16. permutations produce value-equal output and inputs remain unchanged;
17. aggregate and failures render `[REDACTED]`;
18. no floor calculator, recommendation, API, persistence, or runtime change;
19. no file under `platform/foundation/kernel` changes;
20. `git diff --check` and complete repository build remain green.

## Remaining boundary

Calculating the derived floor, comparing baseline and replacement-cost floors,
economic objective, price feasibility, simulation, recommendation, authority,
execution, outcome, persistence, API/UI, quantity/kit conversion, and multiple
Product Cost allocation require later accepted specifications.

## Acceptance

Merging ADR-0031 and SPEC-0031 authorizes TASK-0114 only. It changes no runtime
behavior and authorizes no floor calculation, recommendation, decision, action,
AI, or Kernel modification.
