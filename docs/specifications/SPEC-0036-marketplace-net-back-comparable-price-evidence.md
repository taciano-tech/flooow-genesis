# SPEC-0036: Marketplace Net-Back Comparable Price Evidence

Status: Proposed

Date: 2026-08-20

Source decision: ADR-0036

## Objective

Retain two explicit scenario-owned representations of one observed gross-price
fact and evaluate the source and selected-cost derived Net-Back floors with the
existing diagnostic authority, without rebinding evidence or producing a
preference, recommendation, decision, or action.

## Authorized next implementation

Acceptance authorizes TASK-0128 only after the TASK-0127 contract correction:

1. add one pure projection in the Marketplace pricing package;
2. accept one complete `NetBackCostBasisFloorDelta`, one source-scenario
   `ObservedMarketplacePrice`, and one derived-scenario
   `ObservedMarketplacePrice`;
3. require the observations to retain the same source fact exactly;
4. invoke the accepted evaluator once against each retained floor;
5. map source and derived failures independently without partial output;
6. retain the floor delta, both observations, and both exact assessments on
   success;
7. reproduce all successful invariants internally;
8. render every new type as `[REDACTED]`;
9. prove behavior with focused tests and complete repository verification.

No observation generation or copying, position-transition classification,
percentage, materiality, economic objective, preferred Product Cost basis,
recommendation, decision, authority, action, persistence, API, connector, AI,
or Kernel change is authorized.

## Inputs

```text
MarketplaceNetBackComparablePriceEvidence.evaluate(
  floorDelta: NetBackCostBasisFloorDelta,
  sourceObservation: ObservedMarketplacePrice,
  derivedObservation: ObservedMarketplacePrice
): NetBackComparablePriceEvidenceResult
```

The implementation reads no clock, random source, environment, database,
network, global organization context, or provider configuration.

## Same source-fact invariant

The two caller-supplied observations must have exact equality for:

```text
organizationId
id
grossPrice
source
occurredAt
evidenceQuality
```

Their `scenarioId` values must differ and must match the source and derived
floor scenarios respectively.

The equal observation ID identifies the single caller-supplied source fact; it
is not generated, replaced, or interpreted as a new assessment identity. Exact
gross price, currency, provenance, time, and quality prevent unrelated facts
from masquerading as a cost-basis-only comparison.

Any same-fact disagreement returns `EvidenceMismatch` before either floor is
evaluated. No field is normalized, rounded, tolerated, merged, or repaired.

## Exact dual evaluation

After the same-fact check, the projection invokes exactly:

```text
MarketplaceEconomicPricePosition.evaluate(
  floorDelta.sourceScenarioFloor.sourceFloor,
  sourceObservation
)

MarketplaceEconomicPricePosition.evaluate(
  floorDelta.sourceScenarioFloor.appliedScenarioFloor.floor,
  derivedObservation
)
```

Source evaluation completes before derived evaluation. A source failure returns
immediately and the derived evaluator is not represented as successful. A
derived failure returns no partial source assessment.

The projection must not calculate another floor, reconstruct a profile, replace
Product Cost evidence, recalculate floor deltas, copy either observation,
reimplement price gaps or positions, or add rounding or tolerance.

## Successful aggregate

```text
NetBackComparablePriceEvidence(
  floorDelta,
  sourceObservation,
  derivedObservation,
  sourceAssessment,
  derivedAssessment
)
```

Internal construction requires exact reproduction of:

```text
EconomicPricePositionResult.Assessed(sourceAssessment)
EconomicPricePositionResult.Assessed(derivedAssessment)
```

from the retained floors and observations. It also reproduces the same-fact
invariant. The aggregate renders `[REDACTED]`.

The complete lineage retains:

- source and selected Product Cost evidence;
- source and derived pricing profiles;
- source and derived Net-Back floors;
- exact absolute/economic floor deltas;
- the two explicit scenario-owned forms of one observed-price fact;
- both exact positions, gaps, qualities, floor policies, source provenance,
  occurrence time, marketplace, currency, and quantum.

No assessment field is translated, collapsed, or made authoritative over the
other.

## Controlled result

```text
sealed interface NetBackComparablePriceEvidenceResult

Assessed(evidence: NetBackComparablePriceEvidence)
EvidenceMismatch
SourceOwnershipMismatch
DerivedOwnershipMismatch
CurrencyMismatch
PriceQuantumMismatch
```

Mappings are exact:

```text
source OwnershipMismatch   -> SourceOwnershipMismatch
derived OwnershipMismatch  -> DerivedOwnershipMismatch
either CurrencyMismatch    -> CurrencyMismatch
either PriceQuantumMismatch -> PriceQuantumMismatch
```

The accepted scenario application copies source currency and price quantum
unchanged into the derived profile. The same-fact invariant requires both
observations to retain the same gross price, including its currency. Therefore
distinct source/derived currency and quantum failure types would describe
states that no valid input can produce. The shared controlled failures preserve
the exact diagnostic without creating unreachable public semantics.

Every result renders `[REDACTED]`. Failures contain no partial assessment and
disclose no organization, scenario, observation, price, currency, source, time,
floor, cost basis, or policy value.

## Existing semantics inherited unchanged

TASK-0128 inherits from `MarketplaceEconomicPricePosition` without
modification:

- organization and scenario ownership;
- exact currency equality;
- exact positive price-quantum alignment without rounding;
- absolute and economic gap formulas;
- four accepted economic price positions and their precedence;
- confirmed/estimated quality propagation;
- floor normalization and calculation policy lineage.

The new projection defines no duplicate position, gap, quality, money, source,
time, or policy type.

## Accepted fixture

Given:

```text
source Product Cost = 143.20
selected Product Cost = 48.00
source absolute/economic floors = 143.20
derived absolute/economic floors = 48.00
absolute/economic floor deltas = -95.20
observed gross price = 100.00
same observation ID, source, time, and confirmed quality
source observation scenario = source scenario
derived observation scenario = derived scenario
```

The exact paired assessments are:

```text
source position = BELOW_ABSOLUTE_FLOOR
source absolute/economic gaps = -43.20

derived position = ABOVE_ECONOMIC_FLOOR
derived absolute/economic gaps = 52.00

source quality = CONFIRMED
derived quality = CONFIRMED
```

The result says only that the same explicit price fact occupies those two
diagnostic positions under the retained floors. It does not classify the
transition, choose the selected Product Cost, or recommend `100.00`.

## Deterministic failure precedence

1. any same-fact field disagreement returns `EvidenceMismatch`;
2. otherwise source evaluation maps its first controlled result;
3. only after source success does derived evaluation map its controlled result;
4. only two successful assessments produce the aggregate.

This precedence is observable only through the redacted result type. No error
message or partial aggregate leaks values.

## Determinism and immutability

- value-equal inputs return value-equal output;
- no ID, time, version, source, policy, or observation is generated;
- floor delta, floors, profiles, Product Cost evidence, and observations remain
  unchanged;
- successful output retains the same input instances;
- exact evaluator outputs are retained without translation.

## Implementation scope

TASK-0128 may add only:

- `MarketplaceNetBackComparablePriceEvidence.kt` in Marketplace pricing;
- `MarketplaceNetBackComparablePriceEvidenceTest.kt`;
- TASK-0128 evidence.

No existing production type needs modification.

## Test plan

TASK-0128 proves at least:

1. projection bytecode references no Kernel type;
2. public inputs are only complete floor delta and two observed prices;
3. same organization, ID, gross price, source, time, and quality are required;
4. scenarios must remain distinct and exactly match their retained floors;
5. evidence mismatch precedes evaluator mapping;
6. accepted fixture returns both exact existing assessments;
7. output retains the same floor delta and observation instances;
8. source and derived gaps, positions, policies, time, source, and quality remain
   exact;
9. source and derived ownership failures remain distinguishable;
10. shared currency and price-quantum failures map without unreachable
    source/derived variants;
11. a derived failure returns no partial source assessment;
12. confirmed and estimated quality combinations remain evaluator-owned;
13. internal construction rejects changed evidence or either mismatched
    assessment;
14. value-equal inputs are deterministic and inputs remain unchanged;
15. all new renderings are `[REDACTED]`;
16. no transition classification, percentage, materiality, preferred basis,
    recommendation, authority, or action is produced;
17. no API, persistence, runtime, connector, event, or AI is added;
18. no file under `platform/foundation/kernel` changes;
19. `git diff --check` and complete repository build remain green.

## Remaining boundary

Source-to-derived position-transition classification, percentage or
materiality, economic objective, market competitiveness, price simulation,
preferred Product Cost basis, recommendation, authority, execution, outcome,
persistence, API/UI, quantity/kit conversion, and multiple Product Cost
allocation require later accepted specifications.

## Acceptance

Merging the TASK-0127 correction to ADR-0036 and SPEC-0036 authorizes TASK-0128
only. It changes no runtime behavior and authorizes no observation generation,
recommendation, decision, action, AI, or Kernel modification.
