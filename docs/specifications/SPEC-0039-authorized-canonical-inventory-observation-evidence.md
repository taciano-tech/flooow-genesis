# SPEC-0039: Authorized Canonical Inventory Observation Evidence

Status: Proposed

Date: 2026-08-20

Source decision: ADR-0039

## Objective

Link one complete canonical inventory observation to one authorized selected
candidate only when all retained identity, lineage, target, selected-measure,
and exact-quantity facts agree, without evaluating freshness, health, current
state, or business availability.

## Authorized next implementation

Acceptance authorizes TASK-0135 only:

1. extend the pure `inventory-source-authority` module with one evidence file;
2. move `inventory-canonical-observation` from the module's test-only allow-list
   to its production allow-list;
3. accept one authority assessment and one complete observation;
4. reproduce exact evidence in deterministic mismatch order;
5. extract the selected observation measure with exhaustive branches;
6. retain the same assessment and observation instances on success;
7. reproduce successful invariants internally and redact every new rendering;
8. prove every mismatch, all five measures, signed rational equality,
   determinism, privacy, and scope with focused tests and complete repository
   verification.

No timestamp interpretation, duration, freshness, health, source rank,
reconciliation, current-state selection, business quantity, confidence, ATP,
persistence, API, runtime, AI, or Kernel change is authorized.

## Module dependency correction

`CanonicalInventoryObservation` becomes a production input of the existing
module. The exact production allow-list becomes:

```text
platform:foundation:organization-context
applications:integration-control-plane
applications:inventory-identity-mapping
applications:inventory-canonical-observation
applications:inventory-measure-selection
```

The test-only allow-list retains:

```text
applications:inventory-source-acceptance
```

No other dependency is authorized.

## Link API

```text
AuthorizedCanonicalInventoryObservationLinker.link(
  authority: CanonicalInventorySourceAuthorityAssessment,
  observation: CanonicalInventoryObservation
): AuthorizedCanonicalInventoryObservationResult
```

The linker reads no repository, clock, environment, provider API, or mutable
state.

## Deterministic validation order

Let `candidate = authority.candidate`. Checks occur in this exact order:

1. `observation.organizationId == candidate.organizationId`;
2. `observation.id == candidate.observationId`;
3. `observation.sourcePointer == candidate.sourcePointer`;
4. `observation.projectionRevision == candidate.projectionRevision`;
5. both mapping decision ID and mapping revision agree;
6. `observation.target == candidate.target`;
7. the selected observation measure exists;
8. the selected exact quantity equals `candidate.exactQuantity`.

The first disagreement returns its controlled result.

## Existing measure extraction

The linker exhaustively maps:

```text
AVAILABLE_TO_SELL -> observation.measures.availableToSell
ON_HAND           -> observation.measures.onHand
RESERVED          -> observation.measures.reserved
PENDING_INBOUND   -> observation.measures.pendingInbound
PENDING_OUTBOUND  -> observation.measures.pendingOutbound
```

There is no `else`, enum ordinal, enum-name parsing, reflection, map supplied
by callers, fallback to another measure, conversion, rounding, or arithmetic.
A future measure addition must fail compilation until a new contract is
accepted.

## Controlled result

```text
sealed interface AuthorizedCanonicalInventoryObservationResult {
  Linked(evidence)
  OrganizationMismatch
  ObservationIdentityMismatch
  SourcePointerMismatch
  ProjectionRevisionMismatch
  MappingLineageMismatch
  TargetMismatch
  SelectedMeasureUnavailable
  SelectedQuantityMismatch
}
```

Every result renders `[REDACTED]`. A failure retains no partial evidence or
copied observation fields.

## Successful evidence

```text
AuthorizedCanonicalInventoryObservationEvidence(
  authority,
  observation
)
```

Internal construction reproduces every exact invariant, including selected
measure availability and exact quantity equality. The aggregate retains the
same authority and observation instances and has exactly those two fields.

It adds no ID, timestamp, duration, policy, source, quality, score, status,
reason text, copied quantity, or derived value. Rendering is `[REDACTED]`.

## Accepted fixture

Given an authorized `ON_HAND` candidate retaining observation `OBS-1`, source
pointer `P`, projection revision `2`, mapping `M/3`, target `T`, and exact
quantity `-5/6`, a complete observation with the same facts and
`onHand = -5/6` links successfully.

Changing another measure does not affect linking. Missing `onHand` returns
`SelectedMeasureUnavailable`; `onHand = -4/6` returns
`SelectedQuantityMismatch` without rounding.

## Timestamps and privacy

- existing observation timestamps remain inside the retained observation;
- the linker does not compare, select, copy, or render them;
- authority evaluation time is retained only through the authority aggregate;
- output reveals no organization, connection, target, measure, quantity,
  policy, principal, or time;
- no raw evidence appears in exceptions or result rendering.

## Implementation scope

TASK-0135 may modify or add only:

- the source-authority module build allow-list;
- `AuthorizedCanonicalInventoryObservationEvidence.kt`;
- `AuthorizedCanonicalInventoryObservationEvidenceTest.kt`;
- TASK-0135 evidence.

No existing production domain type requires modification.

## Test plan

TASK-0135 proves at least:

1. the corrected production/test dependency allow-lists are exact;
2. production bytecode references no Kernel or Marketplace type;
3. exact evidence links and retains the same two instances;
4. mismatch precedence follows the accepted order;
5. organization, observation identity, source pointer, projection revision,
   mapping lineage, and target each fail closed;
6. all five selected measures are extracted exhaustively;
7. an unavailable selected measure fails without fallback;
8. negative, zero, positive, and reduced rational quantities compare exactly;
9. changing an unselected measure does not change the result;
10. inconsistent internal construction is rejected;
11. value-equal inputs are deterministic and immutable;
12. the successful aggregate has exactly authority and observation fields;
13. aggregate, linker, and every result render `[REDACTED]`;
14. no timestamp calculation, duration, freshness, health, rank, current-state,
    availability, confidence, ATP, recommendation, or action is introduced;
15. no persistence, API, event, connector, runtime, UI, AI, or Kernel change is
    introduced;
16. `git diff --check` and the complete repository build remain green.

## Remaining boundary

Timestamp ordering and provenance, source/commit/projection freshness policy,
connection health, provider succession, priority, canonical current-state
selection, tolerance, reconciliation, aggregation, reservations, unconfirmed
demand, business availability, Inventory Confidence, Safe ATP, Seller
Entitlement, publication, mutation, recommendation, authority to act, outcome,
and learning require later accepted specifications.

## Acceptance

Merging ADR-0039 and SPEC-0039 authorizes TASK-0135 only. It changes no runtime
behavior and authorizes no freshness conclusion, current-state winner,
business-stock decision, external action, AI, or Kernel modification.
