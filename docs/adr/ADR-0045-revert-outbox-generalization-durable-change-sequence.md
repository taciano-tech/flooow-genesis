# ADR-0045: Revert Outbox Generalization — Durable Change Sequence Instead

Status: Accepted

Date: 2026-09-01

## Context

ADR-0044/SPEC-0043 (merged via PR #138) corrected a real defect: SPEC-0042
required inserting an economic-evidence event into `integration_event_outbox`,
a table whose schema and delivery canonicalizer were inventory-risk-specific.
The finding was accurate and the fix as designed — nullable `assessment_id`,
type-discriminated checks, byte-compatible inventory-risk preservation,
explicit forbidden-field list, fail-closed on unknown type — is technically
sound.

However, the chosen remedy (generalize the existing outbox; extend
`OutboxDeliveryRuntime.kt`, an existing shared production file, plus its test)
reopens a boundary every prior contract in this line (ADR-0043, SPEC-0042, and
this project's own recurring review discipline) had deliberately kept closed:
no existing production file or existing migration may change under this
evidence-durability effort, precisely because `OutboxDeliveryRuntime.kt`
already serves the one delivery path currently in production use
(inventory-risk-assessment). Widening it now increases regression surface on
the only live path, at the exact moment this project is prioritizing time to
a first real seller over infrastructure generalization.

Separately, `integration_event_outbox` is an external-delivery mechanism —
leases, retries, dead-letter, destination policy. It is not designed to be
read as an internal, ordered, resumable checkpoint source. SPEC-0043 does not
specify how a future P0.3 projection would consume it incrementally and
idempotently; it only specifies delivery to an external consumer.

## Decision

Revert the outbox-generalization remedy. `integration_event_outbox`,
`OutboxDeliveryRuntime.kt`, and both existing outbox migrations (V002, V005)
remain untouched by the durable evidence work in its entirety.

TASK-0144, as currently scoped by SPEC-0043 to nine files including
`OutboxDeliveryRuntime.kt` and its test, is **not authorized to proceed**
under that scope. SPEC-0044 (accompanying this ADR) replaces it with a
seven-file scope requiring no outbox change.

The durable evidence journal instead assigns a `change_sequence`: an
organization-scoped, strictly increasing value recorded once per committed
applied update (fact, attempt, or correction), independent of and in addition
to the existing `MarketplaceEconomicEvidenceVersion` (which is per-subject and
has no cross-subject ordering). `change_sequence` is the durable, resumable
cursor a future P0.3 projection reads directly from the evidence tables —
`WHERE organization_id = ? AND change_sequence > :checkpoint ORDER BY
change_sequence` — with no dependency on outbox delivery semantics.

## What is not reverted

The technical finding in ADR-0044 remains valid and stays on record: the
outbox schema and canonicalizer are inventory-risk-specific today, and any
future attempt to route economic-evidence notifications through
`integration_event_outbox` must go through the same generalization design
ADR-0044 already specified. That design is not discarded — it is deferred.
Generalizing the outbox becomes a candidate future ADR once a concrete
external distribution consumer for economic-evidence events exists (e.g. a
downstream system outside this repository that needs CloudEvents delivery,
not merely an internal projection).

## Consequences

- `change_sequence` is added to the evidence journal schema, not to the
  outbox.
- TASK-0144's file scope shrinks back toward SPEC-0042's original seven
  files, with contents corrected per SPEC-0044.
- No existing production file or existing migration is touched by this line
  of work, restoring the invariant SPEC-0042 originally stated.
- Future outbox generalization remains available, gated on a real external
  consumer, per this project's standing discipline against building
  infrastructure ahead of demonstrated need.

## Authorization

This ADR, together with SPEC-0044, supersedes the outbox-generalization
sections of ADR-0044 and SPEC-0043 specifically. The technical finding
section of ADR-0044 (schema/canonicalizer inspection results) remains valid
documentation and is not superseded. Merging this ADR and SPEC-0044
authorizes TASK-0144 to proceed under the corrected, narrower scope.
