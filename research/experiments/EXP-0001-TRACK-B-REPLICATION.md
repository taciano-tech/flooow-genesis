# EXP-0001 Track B Independent Replication

## Purpose

This procedure allows an independent replicator to execute the canonical Track
B inventory workflow sequence and compare its semantic result with committed
evidence. Preparing or repeatedly running this package during implementation
does not constitute independent replication.

## Controlled Evidence

- Input: `applications/marketplace-operations/src/test/resources/exp-0001/track-b-workflow-input.properties`
- Expected result: `applications/marketplace-operations/src/test/resources/exp-0001/track-b-workflow-expected.snapshot`
- Reproduction test: `Exp0001TrackBReplicationTest`
- Java toolchain: 21
- Gradle wrapper: 9.4.0

The canonical sequence starts with 30 units, accepts a receipt of 50, accepts a
consumption of 25, rejects a consumption of 60, and rejects a receipt addressed
to another SKU. Its final accepted snapshot contains 55 units at the timestamp
of the accepted consumption.

## Procedure

1. Check out the commit selected for replication.
2. Confirm the working tree is clean.
3. Run:

   ```text
   ./gradlew :applications:marketplace-operations:test --tests "*Exp0001TrackBReplicationTest" --no-daemon
   ```

4. Run the complete repository validation:

   ```text
   ./gradlew clean build --no-daemon
   ```

5. Record the commit, date, executor, environment, command results, and every
   observed divergence in a separately reviewed Track B replication record.

## Interpretation

- A passing comparison means the canonical command sequence produced the
  committed semantic snapshot in that environment.
- A rejected transition must preserve the last accepted snapshot.
- A failing comparison is a divergence and must be recorded rather than hidden.
- A passing test supports reproducibility of this workflow only; it does not
  prove universality or promote a Kernel primitive.
- The original implementer must not claim or fill an independent replication
  result.
