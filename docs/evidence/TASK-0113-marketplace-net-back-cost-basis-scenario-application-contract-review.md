# TASK-0113 - Marketplace Net-Back Cost Basis scenario application contract review

## Decision

ADR-0031 and SPEC-0031 define the first safe application of a selected Product
Cost to Net-Back as a new immutable scenario, not a mutation or floor
calculation.

## Preconditions now available

- Product Cost Basis preserves historical/current/forward evidence;
- selection names one basis explicitly and revalidates time;
- Net-Back profiles and results now name their normalized commercial unit.

These allow compatibility to fail closed across organization, scenario,
marketplace, currency, and unit.

## Boundary choices

- caller supplies a distinct target scenario ID;
- source profile and selection remain immutable;
- exactly one complete fixed-deduction Product Cost is supported;
- component IDs remain stable within the new scenario;
- selected cost, source, and quality replace only Product Cost evidence;
- every other component changes only scenario ownership;
- selection is reproduced at application time;
- full profile, selection, original/applied component, policy, and time lineage
  remains in one redacted aggregate;
- no floor is calculated.

## Deliberate deferral

The contract does not:

- allocate one selected cost across multiple Product Cost components;
- convert currency or commercial units;
- infer a target scenario identity;
- calculate or compare a derived floor;
- select an economic objective;
- recommend, approve, or execute a price;
- persist or expose the scenario through API/UI;
- add AI, agents, infrastructure, or Kernel language.

## Sequence preserved

```text
economic truth and baseline Net-Back
  -> market diagnostics
  -> explicit cost evidence and selection
  -> explicit Net-Back unit identity
  -> immutable unit-safe cost-basis scenario application
  -> later derived floor and baseline comparison
  -> later objective, simulation, recommendation, authority, and outcome
```

## Authorization

Acceptance authorizes TASK-0114 only: pure application policy, compatibility
and timing validation, one supported Product Cost shape, deterministic derived
profile construction, complete lineage, controlled results, and focused tests.

It authorizes no floor calculation, comparison, recommendation, runtime,
infrastructure, AI, or Kernel modification.
