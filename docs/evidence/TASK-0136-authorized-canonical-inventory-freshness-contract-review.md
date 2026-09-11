# TASK-0136: Authorized Canonical Inventory Freshness Contract Review

Status: Contract accepted for implementation

Date: 2026-08-20

## Repository inspection

Inspected canonical `main` at merge commit `dabbda8`, the accepted observation,
source-acceptance, measure-selection, source-authority, linked-evidence, and
Trust roadmap contracts, plus existing temporal policy implementations.

TASK-0135 now supplies one complete, lineage-proven observation beside its
authorized selected candidate. The repository therefore has the exact three
time facts needed for a temporal assessment, but no accepted rule interprets
them.

The smallest missing dependency is a pure, source-scoped freshness policy. It
precedes connection health and canonical current-state selection.

## Reuse decision

ADR-0040 / SPEC-0040 reuse:

- `AuthorizedCanonicalInventoryObservationEvidence` as the only evidence
  input;
- its retained `CanonicalInventorySourceAuthorityAssessment.evaluatedAt` as
  the only evaluation instant;
- existing organization, connection, target, measure, authority-policy
  version, and observation timestamps;
- Java `Instant` and `Duration` without a new clock abstraction.

No parallel observation, timestamp, source, quantity, target, measure, health,
or confidence type is introduced.

## Exact boundary

```text
linked authorized observation evidence
  + versioned scoped temporal policy
  -> fresh assessment or typed temporal failure
```

Provider time is either explicitly required and assessed or explicitly not an
input. A missing provider timestamp never falls back to commit or projection
time. Commit age and projection age are always assessed independently.

## Important decisions

- freshness uses the authority assessment's exact evaluation time;
- a later answer requires reassessing authority and freshness;
- source, commit, and projection timestamps retain distinct provenance;
- policy thresholds are finite, microsecond-aligned, and versioned;
- source future-clock allowance is explicit and capped;
- commit must not be after projection;
- provider time is not ordered against Genesis timestamps;
- all accepted interval and age boundaries are explicit and inclusive where
  specified;
- freshness is not source health, correctness, confidence, or availability.

## Why health remains later

Connection health requires different evidence: execution attempts, failures,
latency, consecutive outcomes, suspension state, and observation coverage.
None of those facts belongs in a timestamp-age calculation. Combining them now
would produce an opaque score and skip the decomposable Trust chain.

## Explicit exclusions

- no connector/provider health or reliability score;
- no source rank, priority, succession, reconciliation, or winner;
- no current-state selection, aggregation, reservations, or demand;
- no business availability, Inventory Confidence, Safe ATP, or degradation;
- no persistence, migration, API, event, runtime, UI, action, AI, or Kernel
  change.

## Sequence outcome

The protected dependency chain is now:

```text
accepted canonical observation
  -> selected exact measure
  -> explicit source authority
  -> complete authorized observation evidence
  -> accepted freshness contract (this task)
  -> TASK-0137 pure freshness implementation
  -> later source health/current-state contracts
  -> later reservations and unconfirmed demand
  -> later Inventory Confidence
  -> later Safe ATP and graceful degradation
```

## Authorization outcome

Acceptance authorizes TASK-0137 only: add the pure freshness policy and
assessment, controlled results, exact temporal semantics, redaction, focused
tests, and evidence specified by SPEC-0040 inside the existing authority
module.

The implementation task must not expand into health, current-state selection,
Inventory Confidence, Safe ATP, persistence, execution, or Kernel work.
