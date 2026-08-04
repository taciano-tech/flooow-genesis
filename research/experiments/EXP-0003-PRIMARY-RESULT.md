# EXP-0003 Primary Characterization Result

**Date:** 2026-08-04

**Source commit:** `5d5b850af18065638b4388e93de934a4063c441c`

**Kernel change:** None

**Disposition:** Continue Investigation — Independent Replication Required

## Execution

The isolated EXP-0003 harness was executed fresh with:

```powershell
$env:GRADLE_USER_HOME=(Resolve-Path -LiteralPath '.gradle').Path; .\gradlew.bat :research:experiments:exp-0003-harness:test --rerun-tasks --no-daemon --console=plain
```

The execution passed one aggregate JUnit test with zero failures, errors, or
skipped tests. The aggregate test executes the complete protocol before making
assertions so that observed evidence is persisted even when a comparison fails.

The complete repository build was then executed fresh with:

```powershell
$env:GRADLE_USER_HOME=(Resolve-Path -LiteralPath '.gradle').Path; .\gradlew.bat clean build --rerun-tasks --no-daemon --console=plain
```

Fresh JUnit XML from 27 suites recorded 73 tests with zero failures, errors, or
skipped tests: 1 harness test, 21 Marketplace Operations tests, and 51 Kernel
tests.

The primary observed snapshot is committed at
`research/experiments/EXP-0003-PRIMARY-OBSERVED.snapshot`.

## Core Results

All 24 complete core traces matched the frozen semantic snapshot:

- two fixture domains: Marketplace Replenishment and Service Reliability;
- six core scenarios per domain;
- two policies per scenario;
- complete hypothesis, direction, reason, totals, relationships, confidence,
  and evaluation instant.

P1 produced conservative `UNRESOLVED/CONFLICT` whenever both relationship
directions were present. P2 produced positive, negative, balanced, or
insufficient-weight results from exact decimal totals. Both policies consumed
the same relationship contract and returned the same judgment-result shape.

## Integrity Results

I1–I12 matched every frozen expectation:

- one canonical evidence item related differently to distinct hypotheses;
- missing evidence, hypothesis mismatch, empty relationships, identical
  duplicates, and contradictory duplicates were rejected explicitly;
- confidence zero did not become hidden polarity;
- observation prose did not change executable results;
- all 120 C6 permutations were identical;
- repeated valid and invalid evaluations were structurally identical;
- canonical Evidence fields remained unchanged and no default relationship was
  created.

A separate structural check rejected duplicate relationship identifiers before
policy evaluation.

## Reduction Result

The neutral reduction oracle returned **FAIL** from observed behavior rather
than a predetermined flag.

The current Kernel produced three collisions in which the same public Judgment
projection corresponded to different required directions:

1. P1 C1 `SUPPORTED` and C2 `CONTRADICTED`;
2. P2 C1 `SUPPORTED` and C2 `CONTRADICTED`;
3. P2 C4 `SUPPORTED` and C5 `CONTRADICTED`.

The current Judgment projection exposes no evaluated relationship collection.
The existing contracts therefore did not express every required direction and
complete trace without equivalent new semantics.

## Ablation Results

All three predeclared ablations produced their expected failure:

- removing relationship direction left multiple executable directions for every
  evaluated C1–C5 policy assignment;
- removing judgment direction made C1 and C2 direction
  `NOT_REPRESENTABLE` to a typed consumer without interpreting another field;
- removing retained relationships preserved zero of two C4 contributions and
  failed the auditability invariant.

These results provide primary evidence that relationship direction, structured
judgment direction, and relationship retention are each necessary under the
declared experimental conditions.

## Hypothesis Assessment

The EXP-0003 hypothesis gains **primary support**:

- the proposed semantics were sufficient for every frozen core and integrity
  scenario;
- both policies used the same contracts;
- both fixture domains required no semantic contract change;
- each proposed dimension failed its predeclared ablation;
- the existing Kernel failed the neutral reduction oracle.

This is initial portability evidence across two fixture domains, not proof of
universal primitive status. It does not establish that either policy is
universal.

## Integrity and Environment Notes

- expected fixtures were not changed or overwritten;
- observed output was promoted deliberately from ignored build output;
- no production Kernel, application, or fixture source changed;
- Kotlin daemon marker creation under Windows AppData was denied; Gradle
  fallback compilation completed successfully;
- task execution was forced fresh with `--rerun-tasks`.

## Required Next Step

A context-independent engineer must execute the isolated harness and full build
from the committed evidence baseline, compare the complete snapshot, and report
every semantic, test-count, and environmental divergence. Until that fresh
replication is complete, no ADR or Kernel implementation is authorized.
