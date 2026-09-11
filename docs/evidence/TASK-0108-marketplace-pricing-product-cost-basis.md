# TASK-0108 - Marketplace Pricing Product Cost Basis

## Outcome

Implemented the pure Product Cost Basis projection authorized by ADR-0028 and
SPEC-0028 inside the Marketplace pricing package.

The projection preserves three independent temporal facts:

```text
HISTORICAL_ACQUISITION
CURRENT_REPLACEMENT
FORWARD_REPLACEMENT
```

No fact overwrites another and no fact changes Net-Back pricing.

## Delivered boundary

- canonical caller evidence identity;
- normalized commercial-unit identity;
- explicit assumption-set version per evidence;
- non-negative exact unit cost with currency;
- source provenance, source occurrence, and economic applicability;
- confirmed or estimated evidence quality;
- versioned current-replacement age and forward horizon;
- exact typed missing-basis evidence;
- controlled duplicate, ownership, marketplace, currency, unit, source-time,
  and applicability failures;
- exact signed historical-to-current, current-to-forward, and
  historical-to-forward cost deltas;
- deterministic, immutable, redacted complete assessment.

## Temporal guarantees

- the evaluator reads no clock;
- every timestamp uses microsecond precision;
- source facts cannot occur after evaluation;
- current source occurrence and applicability must be inside the inclusive
  versioned age window;
- historical applicability cannot follow current applicability;
- forward applicability must be strictly after evaluation and inside the
  inclusive versioned horizon;
- arithmetic overflow in a time window fails closed.

## Verification

The focused test suite proves:

- no compiled Product Cost Basis class references the Kernel;
- value, UUID, money, time, policy, and redaction constraints;
- every missing-basis combination and explicit zero evidence;
- duplicate basis, identity, and source-fact handling;
- organization/scenario, marketplace, currency, and unit isolation;
- inclusive current-time and forward-horizon boundaries;
- future, stale, misordered, and overflow time failures;
- the accepted `41.00 -> 48.00 -> 52.00` fixture and exact deltas;
- positive, zero, and negative trajectories;
- confirmed/estimated quality propagation;
- deterministic permutations, input preservation, and immutable absence;
- redacted aggregate and failure rendering.

Focused local result:

```text
MarketplacePricingProductCostBasisTest
tests: 14
failures: 0
errors: 0
```

Complete Marketplace module result:

```text
15 suites
121 tests
0 failures
0 errors
```

Broad local repository result:

```text
./gradlew build -x :applications:marketplace-operations-persistence-postgres:test
BUILD SUCCESSFUL
78 actionable tasks
```

The Postgres Testcontainers suite remains delegated to Docker-backed GitHub CI.

## Scope confirmation

The implementation adds no persistence, API, connector, ingestion, FX or
landed-cost formula, source selection, Net-Back mutation, price recommendation,
decision, action, AI, or Kernel vocabulary.

## Next boundary

Any policy that selects one of these cost bases for a pricing objective still
requires a separate accepted contract. TASK-0108 itself authorizes no such
selection or recommendation.
