# TASK-0134: Authorized Canonical Inventory Observation Evidence Contract Review

Status: Contract accepted for implementation

Date: 2026-08-20

## Repository inspection

Inspected canonical `main` at merge commit `cf1f398`, the accepted canonical
observation, acceptance, measure-selection, candidate, and source-authority
contracts, plus the protected Trust dependency chain.

TASK-0133 proves source authority against `SelectedCanonicalInventoryMeasure`.
That type retains exact observation identity and lineage but intentionally does
not copy `sourceUpdatedAt`, `sourceCommittedAt`, `projectedAt`, or the complete
measure set.

The repository therefore cannot honestly assess source freshness yet. The
smallest missing dependency is a strict evidence link to the complete existing
observation.

## Reuse decision

ADR-0039 / SPEC-0039 reuse:

- `CanonicalInventorySourceAuthorityAssessment` as the accepted authority;
- `CanonicalInventoryObservation` as the complete observation evidence;
- the existing five-value `CanonicalInventoryMeasure` vocabulary;
- existing exact rational quantities, identities, source pointer, mapping
  lineage, and target.

No parallel timestamp, measure, quantity, source, target, or identity type is
introduced.

## Exact boundary

```text
authorized selected candidate
  + complete canonical observation
  -> exact linked evidence or typed mismatch
```

The linker verifies organization, observation identity, source pointer,
projection revision, mapping lineage, target, selected-measure availability,
and exact selected quantity in deterministic order.

All five existing measures are handled exhaustively. No measure fallback,
conversion, rounding, or arithmetic exists.

## Why freshness remains later

Linking exposes all three retained observation timestamps to a later contract
without deciding:

- which timestamp is authoritative for which source;
- whether missing source time may fall back to commit time;
- acceptable source, commit, or projection ages;
- future-time and temporal-order failure semantics;
- how connection health changes the result.

Those are policy decisions and require their own accepted boundary.

## Explicit exclusions

- no timestamp interpretation, duration, age, freshness, or health;
- no source rank, priority, succession, reconciliation, or winner;
- no aggregation, reservation, business availability, confidence, or ATP;
- no persistence, API, event, runtime, connector, UI, action, AI, or Kernel
  change.

## Authorization outcome

Acceptance authorizes TASK-0135 only: extend the existing pure authority module
with the exact evidence linker, controlled mismatches, exhaustive selected
measure extraction, redaction, focused tests, and evidence specified by
SPEC-0039.
