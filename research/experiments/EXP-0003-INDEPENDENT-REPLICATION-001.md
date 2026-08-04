# EXP-0003 Independent Replication 001

**Date:** 2026-08-04

**Replicator role:** context-independent replication

**Branch:** `agent/task-0045-exp-0003-independent-replication`

**Frozen replication commit:** `29974ca59e6c9e03acdf702e666a16477798c53b`

**Fixture protocol provenance:** fixture version `1.0`, protocol source commit `2adf94cdb0e9120e259378cd41283e2877839b14`

**Primary characterization provenance:** source commit `5d5b850af18065638b4388e93de934a4063c441c`

**Disposition:** Reproduced — no semantic divergence

## Independence and repository state

The replication used only the committed EXP-0003 protocol, primary result,
primary observed snapshot, frozen `fixtures/exp-0003`, and isolated
`exp-0003-harness` module. Before execution, `git status --short` was empty,
the branch was the required branch, and `HEAD` exactly matched the frozen
replication commit above. No fixture, harness, existing document, application,
or production Kernel source was changed.

## Environment

- OS reported by Gradle: Windows 11 10.0 amd64; runtime OS version
  `Microsoft Windows NT 10.0.26200.0`.
- Architecture: x64 OS and process.
- Java: Microsoft OpenJDK 21.0.12+8-LTS, 64-bit Server VM.
- Gradle: 9.4.0, revision `b631911858264c0b6e4d6603d677ff5218766cee`.
- Gradle embedded Kotlin: 2.3.0.
- PowerShell: 5.1.26100.8521.
- Local safe `GRADLE_USER_HOME`:
  `<repository>/.gradle-user-home-exp0003-repl001`.

## Exact commands and execution results

The isolated command was:

```powershell
$env:GRADLE_USER_HOME=(Join-Path (Resolve-Path '.').Path '.gradle-user-home-exp0003-repl001'); .\gradlew.bat :research:experiments:exp-0003-harness:test --rerun-tasks --no-daemon
```

The first sandboxed invocation could not download
`https://services.gradle.org/distributions/gradle-9.4.0-bin.zip` and exited 1
after 8,612 ms with `java.net.SocketException: Permission denied: getsockopt`.
An authorized retry began the download but the command host timed out after
124.1 seconds. Keeping the same local cache, the completed fresh retry exited
0 in 27,089 ms: `BUILD SUCCESSFUL in 26s`, with all 6 actionable tasks
executed.

The complete repository command was then:

```powershell
$env:GRADLE_USER_HOME=(Join-Path (Resolve-Path '.').Path '.gradle-user-home-exp0003-repl001'); .\gradlew.bat clean build --rerun-tasks --no-daemon
```

It exited 0 in 99.8 seconds of command-host wall time. Fresh JUnit XML contained
27 suites and 73 tests, with 0 failures, 0 errors, and 0 skipped tests. The
EXP-0003 suite contributed 1 aggregate test with 0 failures, 0 errors, and 0
skips. These counts reproduce the primary counts (1 harness, 21 Marketplace
Operations, and 51 Kernel tests).

## Snapshot comparison

The generated snapshot was
`research/experiments/exp-0003-harness/build/exp-0003/complete-observed.snapshot`;
this is the harness test working directory's `build/exp-0003` output. It was
compared with committed
`research/experiments/EXP-0003-PRIMARY-OBSERVED.snapshot` after only converting
CRLF/CR to LF and ensuring exactly one trailing newline.

- Complete normalized snapshot: equal, 101 of 101 lines.
- Frozen core keys: 24 compared, 24 equal, 0 missing, 0 divergent.
- Frozen integrity keys: 46 compared, 46 equal, 0 missing, 0 divergent.
- Frozen core plus integrity: 70 compared, 70 equal, 0 missing, 0 divergent.
- Raw SHA-256 differs only because of line endings: primary
  `3384D98ECD7DD963F8077EA801A720F70C3ECF8424271B041CCD4DE60E3794B3`;
  generated `593899C70F5A7B011C6A41AEDC7D9E279066367E86FE79B115B2C8C2BA823A5B`.

All 24 core traces therefore reproduce both domains, C1–C6, and P1/P2. All 46
integrity observations reproduce I1–I12, including deterministic repetition,
permutation, prose independence, validation, canonical Evidence preservation,
and the zero-confidence cases.

## Reduction, ablation, and structural observations

- Neutral reduction oracle: `FAIL`, reproducing all 3 primary collisions.
- Remove relationship direction: `FAIL_REQUIRED_SEMANTICS`.
- Remove judgment direction: `FAIL_REQUIRED_SEMANTICS`.
- Remove retained relationships: `FAIL_INVARIANT`.
- Duplicate relationship identifier: rejected with
  `StructuralInputViolation` and message
  `Relationship IDs must be unique: [M-R-STRUCT-01]`.

These results reproduce the primary evidence that existing Kernel contracts do
not express every required direction and complete trace, and that relationship
direction, structured judgment direction, and retained relationships are each
necessary under the frozen experimental conditions.

## Divergences

**Semantic divergences:** none. Snapshot content, all 70 frozen keys,
reduction, ablations, structural validation, and test counts match the primary
evidence.

**Environmental divergences:** the replication commit and local cache path
differ from the primary characterization. The sandbox initially denied the
Gradle distribution download and one authorized download attempt exceeded its
command-host timeout; neither reached experimental execution and the later
fresh run passed. During the full build, Windows denied Kotlin daemon marker
creation under `AppData`; Gradle used its fallback compiler and completed
successfully. The primary result records the same Kotlin-daemon fallback, so
that item is reproduced environmental behavior rather than a contradiction.

## Assessment

The independent replication reproduces the complete primary evidence. It does
not contradict the EXP-0003 hypothesis assessment: under the declared frozen
conditions, the contextual relationship and explicit judgment direction are
sufficient across both policies and fixture domains, the proposed dimensions
are necessary under their predeclared ablations, and the existing contracts
fail the neutral reduction oracle. This remains bounded experimental evidence;
it is not proof of universality and does not authorize an ADR, primitive
promotion, or production Kernel implementation by itself.
