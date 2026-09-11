# EXP-0001 Independent Replication 001

## Replication identity

- Date: 2026-08-04
- Executor: OpenAI Codex independent replicator
- Baseline commit: `effa2c1ed44d8ca70468eda04f6e09184d19c9d6`
- Initial working tree: clean (`git status --porcelain=v1` produced no output)
- Host: `TS-PRODUCTS`
- OS: Windows 11, `10.0 amd64` (`System.Environment.OSVersion`: `Microsoft Windows NT 10.0.26200.0`)
- Shell: Windows PowerShell `5.1.26100.8521`
- Java: Microsoft OpenJDK `21.0.12+8-LTS`, 64-bit Server VM
- Gradle wrapper: Gradle `9.4.0` (revision `b631911858264c0b6e4d6603d677ff5218766cee`)
- Gradle-reported Kotlin/Groovy/Ant: Kotlin `2.3.0`; Groovy `4.0.29`; Ant `1.10.15`
- Time zone: America/Sao_Paulo (`-03:00`)

The documented Unix-style wrapper invocation was executed through the repository's Windows wrapper, `gradlew.bat`, with identical Gradle task paths, test filters, and `--no-daemon` option. For each Gradle invocation, PowerShell set `GRADLE_USER_HOME` to the repository-local `.gradle` directory because the wrapper otherwise attempted to use the unwritable path `C:\.gradle`.

## Track A reproduction

Controlled input and expected snapshot were used through the documented `Exp0001ReplicationTest` reproduction test.

Exact command:

```powershell
$env:GRADLE_USER_HOME=(Resolve-Path -LiteralPath '.gradle').Path; .\gradlew.bat :applications:marketplace-operations:test --tests "*Exp0001ReplicationTest" --no-daemon
```

Result:

- Process exit code: `0`
- Gradle result: `BUILD SUCCESSFUL in 37s`
- Test result: 1 executed, 1 passed, 0 skipped, 0 failures, 0 errors
- Task result: 16 actionable tasks; 1 executed and 15 up-to-date
- Semantic divergence: none observed; the canonical input produced the committed semantic snapshot.

## Track B reproduction

Controlled workflow input and expected snapshot were used through the documented `Exp0001TrackBReplicationTest` reproduction test.

Exact command:

```powershell
$env:GRADLE_USER_HOME=(Resolve-Path -LiteralPath '.gradle').Path; .\gradlew.bat :applications:marketplace-operations:test --tests "*Exp0001TrackBReplicationTest" --no-daemon
```

Result:

- Process exit code: `0`
- Gradle result: `BUILD SUCCESSFUL in 23s`
- Test result recorded by Gradle/JUnit XML: 1 passed, 0 skipped, 0 failures, 0 errors
- Task result: 7 actionable tasks; 1 restored from cache and 6 up-to-date
- Semantic divergence: none reported by the cached canonical comparison.
- Execution-provenance note: the test task was `FROM-CACHE`; Gradle restored the passing test result rather than executing the test body during this invocation. This is an environment/build-cache observation, not an observed semantic mismatch.

## Complete build

Exact command:

```powershell
$env:GRADLE_USER_HOME=(Resolve-Path -LiteralPath '.gradle').Path; .\gradlew.bat clean build --no-daemon
```

Result:

- Process exit code: `0`
- Gradle result: `BUILD SUCCESSFUL in 24s`
- Aggregate JUnit XML result: 71 passed, 0 skipped, 0 failures, 0 errors
- Module counts: `marketplace-operations` 20 passed; `kernel` 51 passed
- Task result: 12 actionable tasks; 6 executed and 6 restored from cache
- Semantic divergence: none reported.
- Execution-provenance note: both module test tasks were `FROM-CACHE`; compilation also used cached outputs where shown by Gradle.

## Fresh-execution follow-up

This follow-up preserves the cached-run observations above and adds `--rerun-tasks` to force task execution instead of accepting cached test results.

### Track B forced fresh execution

Exact command:

```powershell
$env:GRADLE_USER_HOME=(Resolve-Path -LiteralPath '.gradle').Path; .\gradlew.bat :applications:marketplace-operations:test --tests "*Exp0001TrackBReplicationTest" --no-daemon --rerun-tasks
```

Result:

- Process exit code: `0`
- Gradle result: `BUILD SUCCESSFUL in 52s`
- Fresh JUnit result: 1 executed, 1 passed, 0 skipped, 0 failures, 0 errors
- JUnit timestamp: `2026-08-04T16:53:37.386Z`
- Task provenance: 7 actionable tasks; all 7 executed, none restored from cache
- Semantic divergence: none observed; the freshly executed canonical Track B comparison matched the committed snapshot.

### Complete build forced fresh execution

Exact command:

```powershell
$env:GRADLE_USER_HOME=(Resolve-Path -LiteralPath '.gradle').Path; .\gradlew.bat clean build --no-daemon --rerun-tasks
```

Result:

- Process exit code: `0`
- Gradle result: `BUILD SUCCESSFUL in 56s`
- Fresh aggregate JUnit result: 71 executed, 71 passed, 0 skipped, 0 failures, 0 errors
- Module counts: `marketplace-operations` 20 passed; `kernel` 51 passed
- JUnit timestamp ranges: `marketplace-operations` `2026-08-04T16:54:40.375Z` through `2026-08-04T16:54:40.547Z`; `kernel` `2026-08-04T16:54:32.791Z` through `2026-08-04T16:54:32.978Z`
- Task provenance: 12 actionable tasks; 11 executed and 1 up-to-date, none restored from cache
- Semantic divergence: none observed.

### Fresh-execution environment warnings

During forced recompilation, Kotlin compilation repeatedly could not create daemon client marker files under `C:\Users\xmz_r\AppData\Local\kotlin\daemon` because of `AccessDeniedException`. The Kotlin Gradle plugin explicitly fell back to compilation without the Kotlin daemon; compilation, tests, and both builds then succeeded. This is an environment-only warning and did not produce a semantic divergence. The forced executions also reused Gradle's configuration cache, which does not alter the task provenance above: the Track B test task and both full-build test tasks executed rather than reporting `FROM-CACHE`.

## Divergences and warnings

No semantic divergence was observed in Track A, Track B, or the complete build.

Environment-only observations:

1. A preliminary `gradlew.bat --version` attempt without the repository-local `GRADLE_USER_HOME` override failed before Gradle started because it could not create `C:\.gradle\wrapper\dists\...\gradle-9.4.0-bin.zip.lck`. All reproduction and build commands used the repository-local `.gradle` directory and completed successfully.
2. The Windows wrapper `gradlew.bat` was used instead of the procedure's Unix notation `./gradlew`.
3. Track B's test task, and both test tasks in the complete build, were restored from the local Gradle build cache. This limits fresh-execution provenance but produced no failing comparison or other semantic divergence.
4. Gradle printed the normal informational message that a single-use daemon was forked to honor JVM settings and stopped after each `--no-daemon` build.
5. Forced recompilation could not use the Kotlin compile daemon because its client marker directory under the user profile was inaccessible. Gradle's documented fallback compilation path succeeded.

The working tree remained free of production-code, test, fixture, snapshot, and pre-existing experiment-record changes. This report is the only newly created source-controlled file.
