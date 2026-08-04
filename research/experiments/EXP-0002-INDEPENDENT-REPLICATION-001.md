# EXP-0002 Independent Replication 001

**Date:** 2026-08-04  
**Frozen commit:** `7f159edf588f664ea061adc7b28b525238c66f6c`  
**Kernel change:** None  
**Replication result:** Reproduced

## Environment and provenance

- Host: `TS-PRODUCTS`, Windows 11 `10.0 amd64`.
- Java: Microsoft OpenJDK `21.0.12+8-LTS`, 64-bit Server VM.
- Gradle wrapper: Gradle `9.4.0` (`b631911858264c0b6e4d6603d677ff5218766cee`), Kotlin `2.3.0`, Groovy `4.0.29`, Ant `1.10.15`.
- Repository was clean and `git rev-parse HEAD` returned the frozen commit before execution.
- `GRADLE_USER_HOME` was set to the repository-local `.gradle-user-home` because the environment's default resolved to the unwritable `C:\.gradle`.
- Both successful verification commands used `--rerun-tasks`; `--no-daemon` caused Gradle to use a fresh single-use daemon, stopped after each build.
- The isolated run executed `:applications:marketplace-operations:test` and its compilation/resource dependencies. Its fresh JUnit XML recorded one test with zero failures, errors, or skipped tests; the subsequent clean build replaced that report.
- The clean build ran root and subproject `clean` tasks before compilation, packaging, checks, and both subproject `test` tasks. Its fresh JUnit XML timestamp for EXP-0002 was `2026-08-04T17:48:49.402Z`.

## Exact commands

PowerShell commands used for the successful executions were:

```powershell
$env:GRADLE_USER_HOME=(Join-Path (Get-Location) '.gradle-user-home'); .\gradlew.bat :applications:marketplace-operations:test --tests io.flooow.marketplace.operations.inventory.Exp0002BaselineCharacterizationTest --rerun-tasks --console=plain --no-daemon
```

```powershell
$env:GRADLE_USER_HOME=(Join-Path (Get-Location) '.gradle-user-home'); $env:GRADLE_OPTS='-Dkotlin.compiler.execution.strategy=in-process'; .\gradlew.bat clean build --rerun-tasks --console=plain --no-daemon
```

Environment/tool inspection used:

```powershell
git status --short
git rev-parse HEAD
git log -1 --format=fuller
java -version
$env:GRADLE_USER_HOME=(Join-Path (Get-Location) '.gradle-user-home'); .\gradlew.bat --version
```

## JUnit results

| Execution | Tests | Failures | Errors | Skipped | Result |
|---|---:|---:|---:|---:|---|
| Isolated `Exp0002BaselineCharacterizationTest` | 1 | 0 | 0 | 0 | Passed |
| Fresh clean full build | 72 | 0 | 0 | 0 | Passed |

The full-build count was independently summed from 26 fresh `TEST-*.xml` suites under the two subprojects' `build/test-results/test` directories. The EXP-0002 suite itself contained one testcase.

## Scenario-by-scenario verification

The characterization test constructs all five committed fixture scenarios, evaluates them with the frozen Kernel, serializes the results, and compares the complete serialization byte-for-byte after line-ending normalization and trailing-newline trimming with the committed observed snapshot. The passing isolated test therefore verifies every snapshot line below, not merely test completion.

| Scenario | Expected direction | Reproduced conclusion | Reproduced confidence | Semantic result |
|---|---|---|---:|---|
| Supporting-only | `SUPPORTED` | `Evidence supports the hypothesis.` | `0.8` | Match |
| Contradicting-only | `CONTRADICTED` | `Evidence supports the hypothesis.` | `0.8` | Divergence |
| Equal conflict | `UNRESOLVED` | `Evidence supports the hypothesis.` | `0.75` | Divergence |
| Unequal conflict | `SUPPORTED` | `Evidence supports the hypothesis.` | `0.6499999999999999` | Superficial match only; polarity contributions remain unrepresented |
| Equal conflict, permuted | `UNRESOLVED` | `Evidence supports the hypothesis.` | `0.75` | Divergence |

Permutation equality reproduced separately: confidence equality was `true` and conclusion equality was `true`. The snapshot also reproduced `Evidence.directionField=ABSENT`, `Judgment.directionField=ABSENT`, and `conflictResolution=ABSENT`.

## Divergences and execution notes

Semantic divergences from the protocol's expected meanings reproduced exactly: contradicting-only was reported as supporting; equal conflict and its permutation were reported as supporting rather than unresolved; and unequal conflict matched `SUPPORTED` only coincidentally because confidence magnitude was averaged without retaining semantic direction.

No divergence from the committed fixture or observed snapshot occurred. No test-count divergence from the baseline report occurred: the clean build again passed 72 tests.

Environmental divergences and failed preliminary attempts were preserved rather than reinterpreted:

1. With no local Gradle home, `gradlew --version` failed because it could not create `C:\.gradle\wrapper\dists\...\gradle-9.4.0-bin.zip.lck`.
2. The first repository-local wrapper attempt failed with `java.net.SocketException: Permission denied: getsockopt` while downloading Gradle. The same operation was rerun with approved network access; Gradle 9.4.0 downloaded successfully.
3. The combined download-and-test command then exceeded its 180-second command timeout. After the distribution was present, the isolated command above completed successfully in 85.6 seconds.
4. Kotlin daemon clients could not create marker files under `C:\Users\xmz_r\AppData\Local\kotlin\daemon` (`AccessDeniedException`). Gradle reported this and successfully used its fallback compiler strategy; tests and builds still completed.
5. A preliminary clean-build invocation appended `-Dkotlin.compiler.execution.strategy=in-process` directly to the PowerShell command. PowerShell/Gradle parsed it as task `.compiler.execution.strategy=in-process`, so that attempt failed after nine executed build-logic tasks. The successful clean-build command passed the property through `GRADLE_OPTS` as shown above; the environmental Kotlin-daemon warnings nevertheless remained and fallback compilation succeeded.

## Assessment

The frozen snapshot reproduced completely. This independent replication does **not** contradict the baseline assessment: the experiment hypothesis remains unsupported, while the null hypothesis remains supported by the frozen evidence. Determinism and permutation stability reproduced, but the Kernel still did not represent or evaluate contradictory and unresolved semantic direction.

No Kernel, production, test, fixture, snapshot, or pre-existing experiment document was changed.
