# TASK-0135: Authorized Canonical Inventory Observation Evidence

## Result

Implemented ADR-0039 / SPEC-0039 as one pure evidence linker inside the
existing `inventory-source-authority` module.

The linker accepts one valid source-authority assessment and one complete
canonical observation. It links them only when organization, observation
identity, source pointer, projection revision, mapping lineage, target,
selected-measure availability, and exact rational quantity agree.

## Exact result order

```text
organization
  -> observation identity
  -> source pointer
  -> projection revision
  -> mapping lineage
  -> target
  -> selected measure availability
  -> selected exact quantity
  -> linked evidence
```

All five existing canonical measures are extracted with exhaustive branches.
No ordinal, string parsing, caller map, fallback measure, conversion, rounding,
or arithmetic is used.

## Dependency boundary

`inventory-canonical-observation` moved from the module's test-only allow-list
to the exact production allow-list because the complete observation is now a
production input. No other dependency was added.

## Deliberately absent

- timestamp interpretation, duration, age, freshness, or health;
- source priority, succession, reconciliation, or current-state selection;
- aggregation, reservation, business availability, confidence, or Safe ATP;
- persistence, API, event, connector, runtime, UI, AI, action, or Kernel
  change.

## Validation

Focused tests cover every mismatch, all five measures, unavailable and
different selected quantities, signed rational equality, unselected-measure
independence, invariant reproduction, deterministic value behavior, minimal
aggregate shape, redaction, and bytecode isolation.

Local verification on 2026-08-20:

- focused suite: 10 tests, 0 failures, 0 errors, 0 skipped;
- complete `inventory-source-authority` module: 20 tests across 2 suites,
  0 failures, 0 errors, 0 skipped;
- broad repository build: `BUILD SUCCESSFUL` in 35 seconds, 82 actionable
  tasks, with only the infrastructure-dependent PostgreSQL/Testcontainers test
  excluded;
- GitHub CI: pending PR execution.

## Boundary conclusion

Source authority is now connected to complete, lineage-proven observation
evidence. A later contract may evaluate the retained timestamps without
reloading or trusting an unrelated observation.
