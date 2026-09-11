# SPEC-0024: Marketplace Economic Price Position

Status: Proposed

Date: 2026-08-13

Source decision: ADR-0024

## Objective

Compare one exact source-observed gross price with one complete Net-Back
absolute/economic floor and produce an explainable diagnostic position without
recommending or changing a price.

## Authorized next implementation

Acceptance authorizes TASK-0098 only:

1. extend the pure Marketplace pricing package with observed-price evidence and
   a deterministic position evaluator;
2. reuse organization, scenario, marketplace, currency, money, source,
   evidence-quality, floor, and policy values without weakening them;
3. add caller-supplied observation identity, exact ownership/currency/quantum
   validation, source occurrence time, and redacted rendering;
4. calculate signed gaps to absolute and economic floors;
5. classify the accepted four economic positions with exact precedence;
6. propagate confirmed/estimated quality from floor and observation;
7. prove boundary, equality, quantum, gap, quality, determinism, privacy, and
   immutability behavior with pure tests;
8. leave persistence, startup, APIs, connectors, and all other module behavior
   unchanged.

No migration, repository, HTTP, JSON, event, worker, clock, random source,
competitor, elasticity, inventory, recommendation, decision, action, AI, LLM,
ML, or Kernel change is authorized.

## Identity and evidence

```text
EconomicPriceObservationId
```

The identity wraps a caller-supplied UUID, accepts canonical lowercase UUID
text, has value equality, an internal persistence accessor for future adapters,
and `[INTERNAL]` rendering. No random identity is generated.

```text
ObservedMarketplacePrice(
  organizationId,
  scenarioId,
  observationId,
  grossPrice,
  source,
  occurredAt,
  evidenceQuality
)
```

Construction requires non-negative `MarketplaceMoney` and a source time at
microsecond precision. Source provenance and quality reuse MKT-001 exactly.
Aggregate rendering is `[REDACTED]`.

The observation is immutable and has value equality. It contains no floor,
position, recommendation, SKU, listing, competitor, or execution state.

## Evaluation preconditions

```text
MarketplaceEconomicPricePosition.evaluate(floor, observation)
```

Evaluation requires:

1. exact organization ownership;
2. exact scenario ownership;
3. gross-price currency equal to floor currency;
4. gross price exactly aligned to the floor price quantum.

Ownership disagreement returns controlled `OwnershipMismatch`; currency
disagreement returns `CurrencyMismatch`; quantum disagreement returns
`PriceQuantumMismatch`. No partial assessment or input value is disclosed.

Quantum alignment is exact decimal remainder equality to zero. No rounding or
tolerance is used.

## Position

```text
EconomicPricePosition
  BELOW_ABSOLUTE_FLOOR
  BELOW_ECONOMIC_FLOOR
  AT_ECONOMIC_FLOOR
  ABOVE_ECONOMIC_FLOOR
```

Classification is numeric and exact:

```text
grossPrice < absoluteFloor -> BELOW_ABSOLUTE_FLOOR
grossPrice < economicFloor -> BELOW_ECONOMIC_FLOOR
grossPrice = economicFloor -> AT_ECONOMIC_FLOOR
otherwise                  -> ABOVE_ECONOMIC_FLOOR
```

The accepted Net-Back invariant guarantees economic floor is not below absolute
floor.

## Exact gaps

```text
absoluteFloorGap = grossPrice - absoluteFloor
economicFloorGap = grossPrice - economicFloor
```

Both use `MarketplaceMoney`, retain floor currency, and may be negative. No
absolute value, percentage, margin, severity, or rounded display value is
created.

## Assessment

```text
EconomicPricePositionAssessment(
  organizationId,
  scenarioId,
  observationId,
  marketplace,
  currency,
  observedGrossPrice,
  absoluteFloor,
  economicFloor,
  absoluteFloorGap,
  economicFloorGap,
  position,
  quality,
  floorNormalizationPolicyVersion,
  floorCalculationPolicyVersion,
  source,
  observedAt
)
```

Quality is `CONFIRMED` only when floor truth quality and observation evidence
quality are both confirmed; otherwise it is `ESTIMATED`.

Construction is internal, immutable, value-equal, and validates that gaps,
position, ownership, currency, and quality are consistent with its evidence.
Aggregate rendering is `[REDACTED]`.

## Controlled result

```text
EconomicPricePositionResult
  Assessed(assessment)
  OwnershipMismatch
  CurrencyMismatch
  PriceQuantumMismatch
```

Every aggregate result renders `[REDACTED]`. Errors expose no organization,
scenario, observation, marketplace, currency, price, source, time, or floor.

## Determinism and side effects

Value-equal floor and observation inputs return value-equal results. The
evaluator reads no clock, environment, database, file, network, random source,
global organization context, or provider configuration.

The evaluator does not mutate either input and generates no assessment ID,
event, recommendation, decision, action, or outcome.

## Acceptance fixtures

For absolute floor `235.09` and economic floor `299.90`:

```text
observed 220.00 -> BELOW_ABSOLUTE_FLOOR
  absolute gap = -15.09
  economic gap = -79.90

observed 235.09 -> BELOW_ECONOMIC_FLOOR
  absolute gap = 0
  economic gap = -64.81

observed 299.90 -> AT_ECONOMIC_FLOOR
  economic gap = 0

observed 310.00 -> ABOVE_ECONOMIC_FLOOR
  economic gap = 10.10
```

When both floors are `125.00`, observed `125.00` is `AT_ECONOMIC_FLOOR`.

## Test plan

TASK-0098 proves at least:

1. pricing-position bytecode references no Kernel type;
2. canonical observation identity, non-negative price, microsecond source time,
   safe rendering, and caller-supplied identity behavior;
3. source provenance rules remain unchanged;
4. organization and scenario mismatches are controlled;
5. currency mismatch is controlled;
6. exact quantum misalignment is controlled and never rounded;
7. explicit zero observed price is accepted;
8. below absolute classification and both negative gaps;
9. equality with absolute but below economic classification;
10. equality with economic classification and zero economic gap;
11. above economic classification and positive gaps;
12. equal absolute/economic floor classifies equality as at economic floor;
13. confirmed quality requires confirmed floor and observation;
14. either estimated input produces estimated assessment;
15. assessment validates exact gaps, classification, ownership, currency, and
    quality consistency;
16. value-equal input returns value-equal output;
17. inputs remain unchanged;
18. aggregate rendering and errors disclose no sensitive value;
19. no percentage, recommendation, competitor, inventory, action, API,
    persistence, event, connector, worker, or runtime behavior is added;
20. no file under `platform/foundation/kernel` changes;
21. `git diff --check` and the complete repository build remain green.

## Remaining boundary

Observation persistence/history, current-price selection, listing/SKU identity,
product matching, competitor observations, price index, market position,
elasticity, KVI, inventory-aware pricing, promotions, experiments, severity,
recommendations, policies, approvals, execution, rollback, API/UI, alerts,
outcomes, and learning require later accepted specifications.

## Acceptance

Merging ADR-0024 and SPEC-0024 authorizes TASK-0098 only. It changes no runtime
behavior and authorizes no live price ingestion, market comparison,
recommendation, decision, external price mutation, AI, or Kernel modification.
