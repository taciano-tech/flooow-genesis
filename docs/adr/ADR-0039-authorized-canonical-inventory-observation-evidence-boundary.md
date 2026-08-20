# ADR-0039: Authorized Canonical Inventory Observation Evidence Boundary

Status: Proposed

Date: 2026-08-20

## Context

TASK-0133 can prove that one accepted and selected canonical inventory
candidate is authorized by exact organizational policy. The selected candidate
retains observation identity and lineage but deliberately omits the full
observation timestamps and measure set.

The next Trust dependency is freshness, but freshness cannot be assessed from
an observation identifier alone. Loading an arbitrary observation beside an
authorized candidate would be unsafe unless their organization, source,
mapping, target, selected measure, and exact quantity are proven identical.

The next smallest question is therefore factual:

> Does this full canonical observation exactly reproduce the observation and
> selected quantity retained by this authorized candidate?

This question precedes any age threshold, source health, confidence, or
current-state choice.

## Decision

Introduce a pure, production-inactive evidence linker in the existing
`inventory-source-authority` module.

It consumes:

- one valid `CanonicalInventorySourceAuthorityAssessment`;
- one complete `CanonicalInventoryObservation`.

On success it retains those exact two instances in
`AuthorizedCanonicalInventoryObservationEvidence`. On disagreement it returns
one typed mismatch and no partial evidence.

## Exact reproduction

The full observation must reproduce the authorized candidate's:

- organization;
- observation identifier;
- exact source pointer;
- projection revision;
- mapping decision and revision;
- mapping target;
- selected measure availability;
- exact selected rational quantity.

Selected quantity is read exhaustively from the existing observation measure
set according to the existing `CanonicalInventoryMeasure` enum. No new
measure, conversion, rounding, aggregation, or formula is introduced.

## Controlled results

```text
Linked(evidence)
OrganizationMismatch
ObservationIdentityMismatch
SourcePointerMismatch
ProjectionRevisionMismatch
MappingLineageMismatch
TargetMismatch
SelectedMeasureUnavailable
SelectedQuantityMismatch
```

Checks follow that exact order. There is no null result, permissive fallback,
or caller-provided precedence.

## Timestamps are retained, not interpreted

The linked observation exposes existing `sourceUpdatedAt`,
`sourceCommittedAt`, and `projectedAt` evidence to a later accepted freshness
contract. This linker performs no temporal ordering, age calculation,
wall-clock read, maximum-age policy, future-time classification, or fallback
between timestamps.

The authority assessment's evaluation time is also retained unchanged. The
linker does not claim that authority time is a freshness evaluation time.

## Diagnostic evidence, not current state

Successful linking means only that the complete observation and the already
authorized selected candidate describe the same retained evidence. It does not
mean the observation is fresh, healthy, correct, reconciled, preferred, or the
current-state winner.

The source-reported selected quantity remains evidence. It does not become
business availability, Inventory Confidence, or Safe ATP.

## No infrastructure activation

The linker adds no repository read, persistence, migration, API, event,
connector, worker, scheduler, UI, policy administration, external action, or
Kernel change.

## Consequences

### Positive

- later freshness work receives complete, lineage-proven observation evidence;
- a foreign or partially matching observation fails closed;
- exact rational quantity remains unrounded;
- authority and observation truth remain separate but connected;
- no temporal judgment leaks into evidence linking.

### Negative

- callers must supply the full observation explicitly;
- no freshness or health answer exists after this increment;
- an observation with an unavailable selected measure cannot be linked;
- current-state selection remains unresolved.

## Alternatives considered

Assessing freshness directly from the selected candidate was rejected because
it lacks timestamps. Trusting observation ID alone was rejected because the
full object could carry divergent lineage or quantity. Copying timestamps into
the authority assessment was rejected because authority and observation
evidence have different responsibilities. Combining linking, freshness,
health, confidence, and ATP was rejected as a broad Trust engine. Kernel
promotion was rejected because this remains integration-inventory evidence.

## Authorization

This ADR alone authorizes no implementation. SPEC-0039 may authorize only the
pure evidence linker, exact lineage and selected-quantity reproduction,
controlled mismatches, redaction, and focused tests for TASK-0135.

No freshness, health, priority, current-state selection, reconciliation,
business availability, Inventory Confidence, Safe ATP, persistence, runtime,
action, AI, or Kernel change is authorized.
