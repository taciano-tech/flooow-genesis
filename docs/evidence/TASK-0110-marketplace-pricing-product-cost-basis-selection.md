# TASK-0110 - Marketplace Pricing Product Cost Basis Selection

## Outcome

Implemented the pure Product Cost Basis Selection projection authorized by
ADR-0029 and SPEC-0029 inside the Marketplace pricing package.

The implementation applies one explicit caller policy to one complete
TASK-0108 assessment. It does not infer which basis is best.

## Delivered boundary

- canonical selection-policy version;
- explicit historical, current, or forward selected basis;
- positive microsecond-precise assessment age capped at 31 days;
- caller-supplied microsecond selection time and no clock;
- inclusive assessment selection window;
- current source-occurrence and applicability freshness revalidation;
- strict forward futurity revalidation;
- historical selection without a false current/future claim;
- complete source-assessment and exact selected-evidence lineage;
- separate selected-evidence and complete-assessment qualities;
- controlled assessment-window and evidence-applicability failures;
- deterministic and redacted successful selection.

## No fallback

If current evidence became stale or forward evidence is no longer future, the
projection returns `SelectedEvidenceOutsideApplicability`. It never substitutes
historical, current, or forward evidence automatically.

## Verification

The focused suite proves:

- selection bytecode contains no Kernel reference;
- policy version, bounded duration, microsecond precision, and redaction;
- exact mapping of all three bases and preservation of the complete assessment;
- inclusive lower and upper assessment-age boundaries;
- before-assessment, expired-assessment, and overflow failures;
- inclusive current source/applicability freshness boundaries;
- independent stale-source and stale-applicability failures;
- strict forward-before-selection behavior and elapsed-forward failure;
- historical selection remains historical;
- explicit zero remains selected evidence;
- selected and complete-snapshot qualities remain separate;
- value equality, determinism, and redacted aggregate rendering.

Focused local result:

```text
MarketplacePricingProductCostBasisSelectionTest
tests: 14
failures: 0
errors: 0
```

Complete Marketplace module result:

```text
16 suites
135 tests
0 failures
0 errors
```

Broad local repository result:

```text
./gradlew build -x :applications:marketplace-operations-persistence-postgres:test
BUILD SUCCESSFUL
69 actionable tasks
```

The Postgres Testcontainers suite remains delegated to Docker-backed GitHub CI.

## Scope confirmation

The implementation adds no `NetBackCostComponent`, profile mutation, floor,
economic objective, recommendation, decision, persistence, API, connector,
event, worker, AI, agent, or Kernel vocabulary.

## Next boundary

Applying selected evidence to a reproducible Net-Back scenario without losing
basis and source-assessment lineage requires a separate contract. TASK-0110
authorizes no such application or price conclusion.
