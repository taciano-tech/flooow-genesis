# TASK-0097 - Marketplace Economic Price Position contract review

## Decision

ADR-0024 and SPEC-0024 define the first implementable slice after the Net-Back
floor. The slice compares one source-observed price to the absolute and economic
floors and returns a diagnostic position with exact monetary gaps.

## Boundary choices

- Observed price is evidence with caller identity, provenance, source time, and
  confirmed/estimated quality.
- Organization, scenario, currency, and price quantum must match the floor.
- The observed value is never rounded or silently normalized.
- Absolute and economic gaps remain exact signed money.
- Break-even shortfall is distinct from target-contribution shortfall.
- Assessment quality combines floor and observation evidence quality.
- The result is a diagnostic position, not a recommendation or decision.

## Accepted positions

```text
BELOW_ABSOLUTE_FLOOR
BELOW_ECONOMIC_FLOOR
AT_ECONOMIC_FLOOR
ABOVE_ECONOMIC_FLOOR
```

For the accepted `235.09 / 299.90` fixture, observations `220.00`, `235.09`,
`299.90`, and `310.00` reproduce those four states respectively.

## Deliberate deferral

The slice does not persist price history, choose a current observation, model a
listing/SKU, match competitors, calculate price index or elasticity, consider
inventory, recommend a price, request approval, mutate a listing, or measure an
outcome.

It adds no API, connector, event, worker, AI, or Kernel vocabulary.

## Essence preserved

- Genesis remains the Organizational Computing platform.
- Marketplace Intelligence remains a vertical consumer.
- Economic floors precede price diagnosis.
- Price diagnosis precedes market recommendation.
- Recommendation precedes authority and action.
- Every current fact retains source provenance and quality.

## Sequence preserved

```text
economic truth -> financial evidence -> reconciliation
  -> net-back floors -> observed economic position
  -> competitor/market evidence -> governed recommendation
  -> later approval and execution
```

## Authorization

Acceptance authorizes TASK-0098 only: pure observed-price evidence, evaluator,
assessment, and focused tests in the existing Marketplace pricing package. It
authorizes no persistence, runtime, connector, API, market recommendation,
decision, price action, AI, or Kernel change.
