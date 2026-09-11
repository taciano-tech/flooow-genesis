# TASK-0112 - Marketplace Net-Back normalized unit identity

## Outcome

Implemented the mandatory normalized commercial-unit identity authorized by
ADR-0030 and SPEC-0030.

Every `NetBackPricingProfile` now states its `PricingCostUnitKey` explicitly.
The calculator propagates that exact value through complete, incomplete, and
unachievable outcomes.

## Delivered migration

- added mandatory `unitKey` to `NetBackPricingProfile`;
- included unit identity in profile equality and hash calculation;
- propagated the key to `NetBackEconomicFloor`;
- propagated the key to incomplete coverage results;
- propagated the key to every unachievable result;
- migrated all direct profile constructors in Net-Back, Economic Position, and
  Competitive Position tests to explicit `each`;
- added focused proof that profiles differing only by unit are not equal;
- added focused proof for all result families.

## Mathematical invariance

The implementation changes no calculation. Existing regression fixtures remain:

```text
reverse MKT-001
  net fixed cost 235.09
  absolute floor 235.09
  economic floor 299.90

variable margin
  absolute floor 125.00
  economic floor 142.86

absolute target
  economic floor 150.00
```

Component ordering, coverage precedence, quantum ceiling, rates, targets,
quality, and redaction remain unchanged.

## Verification

```text
./gradlew :applications:marketplace-operations:test

16 suites
136 tests
0 failures
0 errors
```

The suite also confirms pricing bytecode remains isolated from the Kernel.

Broad local repository result:

```text
./gradlew build -x :applications:marketplace-operations-persistence-postgres:test
BUILD SUCCESSFUL
69 actionable tasks
```

The Postgres Testcontainers suite remains delegated to Docker-backed GitHub CI.

## Scope confirmation

No unit conversion, quantity model, kit expansion, Product Cost substitution,
scenario cloning, floor comparison, recommendation, persistence, API,
connector, runtime, AI, agent, or Kernel vocabulary was added.

## Next boundary

The Net-Back profile and selected Product Cost now share an explicit unit
identity. A later contract may define unit-safe scenario application while
retaining the source profile, complete cost assessment, selection policy, and
component provenance.
