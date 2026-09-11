# EXP-0010 — Semantic Support Graph

Purpose: close the gap left by EXP-0009.

EXP-0009 proves reference integrity: a claim cannot cite unknown evidence and an observation must match its referenced fact.

It does not prove that a valid reference semantically supports an inference.

EXP-0010 therefore moves the decision unit from "claim + reference" to:

typed proposition
+ explicit support declaration
+ registered deterministic derivation rule
+ support graph

## Core invariant

A valid evidence reference is necessary but not sufficient for semantic support.

Narrative text has no authority.

Only typed propositions and Flooow-owned rules participate in support validation.

## Support modes

- DIRECT — proposition exactly matches governed evidence.
- DERIVED — proposition is produced by a registered deterministic rule over governed evidence.
- HYPOTHETICAL — explicitly exploratory, not evidence-backed.
- UNSUPPORTED — no valid semantic support path exists.

## CI-safe test

```powershell
.\gradlew.bat -p research\experiments\exp-0010-semantic-support-graph-harness test
```

Research-only. No production integration.
