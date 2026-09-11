# EXP-0001 Independent Replication

## Purpose

This procedure allows an independent replicator to execute the canonical
Marketplace Operations scenario and compare its semantic result with committed
evidence. Preparing this package does not constitute independent replication.

## Controlled Evidence

- Input: `applications/marketplace-operations/src/test/resources/exp-0001/red-moto-input.properties`
- Expected result: `applications/marketplace-operations/src/test/resources/exp-0001/red-moto-expected.snapshot`
- Reproduction test: `Exp0001ReplicationTest`
- Java toolchain: 21
- Gradle wrapper: 9.4.0

## Procedure

1. Check out the commit selected for replication.
2. Confirm the working tree is clean.
3. Run:

   ```text
   ./gradlew :applications:marketplace-operations:test --tests "*Exp0001ReplicationTest" --no-daemon
   ```

4. Run the complete repository validation:

   ```text
   ./gradlew clean build --no-daemon
   ```

5. Record the commit, date, executor, environment, command results, and every
   observed divergence in the replication record of `EXP-0001`.

## Interpretation

- A passing reproduction test means the canonical input produced the committed
  semantic snapshot in that environment.
- A failing comparison is a divergence and must be recorded rather than hidden.
- A passing test does not prove universality or complete EXP-0001.
- The original implementer must not fill the independent replication record.

## Track B Package

The separately controlled workflow sequence and its independent procedure are
documented in `EXP-0001-TRACK-B-REPLICATION.md`. Track A and Track B comparisons
must be reported separately so that a passing result in one track cannot hide a
divergence in the other.
