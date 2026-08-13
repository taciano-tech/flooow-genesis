# TASK-0096 - Marketplace Net-Back Economic Floor

## Outcome

Implemented the first production-inactive MKT-004 Net-Back calculator authorized
by ADR-0023 and SPEC-0023.

The Marketplace vertical can now derive an exact break-even floor and an exact
target-contribution floor from complete normalized unit economics without
producing a recommendation or changing an external price.

## Implemented boundary

- Pure package `io.flooow.marketplace.operations.economics.pricing`.
- Caller-supplied canonical scenario and component identities.
- Exact fixed money and exact scale-eight revenue rates.
- Organization, scenario, currency, source, duplicate, quality, and coverage
  invariants.
- Absolute-amount and margin-rate contribution targets.
- Signed fixed-cost and variable-rate netting with explicit subsidies.
- Strict denominator validation and controlled out-of-range results.
- Conservative integer-quantum ceiling with no intermediate money rounding.
- Canonical provenance, deterministic equality, immutable collections, redacted
  rendering, and Kernel bytecode boundary verification.

## Reproduced economics

```text
MKT-001 reverse:
235.09 fixed cost + 64.81 target -> 299.90 economic floor

variable economics:
100.00 fixed / 20% cost rate -> 125.00 absolute floor
100.00 fixed / 20% cost rate / 10% margin -> 142.86 economic floor
100.00 fixed / 20% cost rate / 20.00 target -> 150.00 economic floor
```

## Architectural evidence

- No file under `platform/foundation/kernel` changed.
- No pricing source imports `io.flooow.kernel`.
- No migration, repository, API, event, connector, scheduler, worker, or startup
  wiring was added.
- No competitor, elasticity, inventory, promotion, recommended price,
  authority, action, AI, model, expert, or agent was introduced.
- No `Float` or `Double` participates in a rate or monetary calculation.

## Remaining boundary

Provider fee rules, tiered taxes, shipping bands, quantity/kit economics,
profile persistence, current-price evaluation, competitor matching, price
index, elasticity, inventory-aware pricing, promotions, experiments,
recommendations, approvals, execution, rollback, API/UI, outcomes, and learning
remain explicitly deferred.
