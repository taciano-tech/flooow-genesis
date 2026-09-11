# TASK-0095 - Marketplace Net-Back Economic Floor contract review

## Decision

ADR-0023 and SPEC-0023 define the first implementable slice of MKT-004. The
slice inverts complete normalized unit economics into an exact absolute floor
and an exact target-contribution economic floor.

## Boundary choices

- Net-back pricing remains a Marketplace Intelligence concept.
- Fixed money and revenue-linked rates are distinct controlled values.
- Every cost type has explicit complete, not-applicable, partial, or missing
  coverage; incomplete economics produces no floor.
- Contribution target is an exact non-negative amount or margin rate.
- Price precision is an explicit positive quantum rather than an inferred
  currency registry.
- Floors round upward by integer quantum units and never silently violate the
  target.
- Components retain source provenance and confirmed/estimated evidence quality.
- The result is a lower economic boundary, not a recommended or optimal price.

## Accepted equations

```text
F = fixed deductions - fixed additions
V = rate deductions - rate additions

absolute floor = F / (1 - V)
amount target  = (F + T) / (1 - V)
margin target  = F / (1 - V - M)
```

Negative solved prices clamp to zero. Non-positive denominators and values
outside the money bound return controlled unachievable reasons. Every result is
ceiled to the supplied price quantum.

## Reproduction target

The first implementation must reverse the accepted MKT-001 fixture:

```text
fixed costs              235.09
target contribution       64.81
absolute floor           235.09
economic floor           299.90
```

It must also prove fixed cost `100`, variable rate `20%`, and margin target
`10%` produce absolute floor `125.00` and economic floor `142.86`.

## Deliberate deferral

The slice does not build provider fee rules, tiered taxes, shipping bands,
competitor matching, elasticity, inventory-aware pricing, dynamic pricing,
promotion logic, price recommendation, approval, mutation, or rollback.

It adds no persistence, API, connector, event, worker, AI, or Kernel vocabulary.

## Essence preserved

- Genesis remains the Organizational Computing platform.
- Marketplace Intelligence remains a vertical consumer.
- Economic evidence and reconciliation precede pricing boundaries.
- Incomplete evidence cannot become a price decision.
- Calculation remains separate from policy authority and execution.
- Domain and deterministic tests precede integrations and intelligence.

## Sequence preserved

```text
MKT-001 exact economic truth
  -> MKT-002 immutable financial evidence
  -> MKT-003 deterministic reconciliation
  -> MKT-004 net-back absolute/economic floor
  -> market/competitive pricing intelligence
  -> governed recommendation and later execution
```

## Authorization

Acceptance authorizes TASK-0096 only: the pure net-back pricing domain and
focused tests in the existing marketplace application module. It authorizes no
migration, repository, runtime, connector, API, event, recommendation,
decision, price action, AI, or Kernel change.
