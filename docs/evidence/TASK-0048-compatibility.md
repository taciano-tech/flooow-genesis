# TASK-0048 Compatibility Evidence

**Date:** 2026-08-04

**Baseline:** `origin/main` at `6d5f6d5`

## Public source and JVM descriptor preservation

TASK-0048 adds new Kotlin files only. For every Kotlin production source that
already existed under `platform/foundation/kernel/src/main/kotlin` on the
baseline, its Git blob was compared with the working-tree blob:

```text
PREEXISTING_SOURCE_FILES=38
MISMATCHES=0
```

Because all 38 preexisting sources are byte-identical and use the same locked
Gradle/Kotlin toolchain, they were compiled once from the baseline worktree and
once from the TASK-0048 worktree. `javap -public -s` was then run over every
class present in the baseline JAR, in sorted class-name order. The complete
inventories include public JVM names and descriptors and produced:

```text
BASELINE_CLASSES=42
BASELINE_INVENTORY_SHA256=d98e26343b0351faf3ec091c6076820289454b3bed6aae39e0447e6b845eaaa1
CURRENT_INVENTORY_SHA256=d98e26343b0351faf3ec091c6076820289454b3bed6aae39e0447e6b845eaaa1
SIGNATURE_DIFF=0
```

The comparison command fails if `javap` fails for any class and compares the
full inventory text, not only its digest. The equal digest is retained here as
a reproducible record. Existing JVM names and descriptors are unchanged; new
directional contracts introduce additive descriptors only.

## Consumer and repository validation

The required clean build was executed after adding the contracts and validator:

```text
./gradlew clean build --rerun-tasks --no-daemon --no-configuration-cache
BUILD SUCCESSFUL
```

This recompiles and tests the kernel, the Marketplace Operations consumer, and
the experimental harnesses. No existing constructor, consumer, snapshot, or
legacy reasoning behavior was changed.
