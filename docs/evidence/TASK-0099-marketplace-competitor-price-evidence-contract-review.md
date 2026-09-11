# TASK-0099 - Marketplace Competitor Price Evidence contract review

## Decision

ADR-0025 and SPEC-0025 define the first implementable market-evidence slice
after Economic Price Position. The slice compares one economically diagnosed
own price with current available competitor prices whose product matching was
already established by upstream evidence.

## Boundary choices

- Economic-price assessment now retains its validated price quantum.
- Competitor evidence has caller identity, seller key, product-match identity,
  provenance, source time, exact gross price, and separate price/match quality.
- Product matching and offer availability are asserted upstream, never inferred
  by the comparator.
- A versioned maximum-age policy and caller evaluation time make freshness
  deterministic and auditable.
- Stale, future, cross-owner, cross-scenario, cross-marketplace, cross-currency,
  misaligned, or duplicate evidence fails closed.
- The result retains exact lowest price, signed gap, and every lowest tie.
- An empty set produces no market position.
- Economic safety and competitive position remain independent diagnostics.

## Accepted positions

```text
BELOW_LOWEST_COMPETITOR
TIED_LOWEST_COMPETITOR
ABOVE_LOWEST_COMPETITOR
```

For own price `299.90`, competitor sets with minima `305.00`, `299.90`, and
`280.00` produce exact gaps `-5.10`, `0`, and `19.90` respectively.

## Deliberate deferral

The slice does not ingest prices, persist history, select a current offer,
discover or match products, model unavailable offers, normalize freight or
fulfillment, calculate price index or elasticity, consider inventory, recommend
a price, request approval, mutate a listing, or measure an outcome.

It adds no migration, API, connector, scraper, event, worker, AI, or Kernel
vocabulary.

## Essence preserved

- Genesis remains the Organizational Computing platform.
- Marketplace Intelligence remains a vertical consumer.
- Economic truth precedes floors.
- Floors precede economic price diagnosis.
- Economic diagnosis precedes competitor evidence.
- Competitor evidence precedes governed recommendation.
- Recommendation precedes authority and action.
- Every fact retains provenance, time, and evidence quality.

## Sequence preserved

```text
economic truth -> financial evidence -> reconciliation
  -> net-back floors -> observed economic position
  -> matched competitor price evidence -> competitive position
  -> later simulation and governed recommendation
  -> later approval, execution, reconciliation, and learning
```

## Authorization

Acceptance authorizes TASK-0100 only: pure quantum carry-forward, competitor
evidence values, freshness policy, diagnostic evaluator, assessment, and
focused tests in the existing Marketplace pricing package. It authorizes no
persistence, runtime, ingestion, product matching, API, recommendation,
decision, price action, AI, or Kernel change.
