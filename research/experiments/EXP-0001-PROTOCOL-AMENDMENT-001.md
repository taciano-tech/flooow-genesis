# EXP-0001 Protocol Amendment 001

**Date:** 2026-08-04

**Status:** Active

**Disposition:** Continue Investigation

## Reason for Amendment

The interim evaluation exposed a mismatch between the experimental protocol
and the executable repository baseline. EXP-0001 identifies `Entity`, `Event`,
`State`, and `Process` as current Kernel concepts to exercise. None of those
concepts exists in the executable Kernel API at the evaluated source baseline.

The mismatch must be recorded before extending the vertical slice. Otherwise,
application-defined substitutes could be mistaken for validation of Kernel
primitives that do not exist.

## Baseline Classification

| Concept | Executable Kernel status | Documentary status | Experimental consequence |
|---|---|---|---|
| `Entity` | Not implemented | Candidate in the draft ontology and primitive-validation RFC | Cannot currently be exercised as a Kernel abstraction. |
| `State` | Not implemented | Candidate in the draft ontology and primitive-validation RFC | State-transition criteria have no executable Kernel contract. |
| `Event` | Not implemented | Open question in the draft ontology; absent from its candidate list | Cannot be assumed to be a Kernel primitive. |
| `Process` | Not implemented | Not listed as a candidate concept in the draft ontology | Cannot be attributed to the current Kernel without a separate proposal. |

The executable Kernel currently exposes organizational-language and reasoning
contracts including `Observation`, `Evidence`, `Decision`, `Action`, `Outcome`,
`Hypothesis`, `Judgment`, `DecisionContext`, and reasoning-engine components.
This inventory describes implementation presence, not proof that each concept
is a validated universal primitive.

## Effect on the Original Protocol

The following original measures are not operationally testable against the
current Kernel API:

- initial entity state;
- sequence of accepted events;
- valid and invalid state transitions;
- process execution path and outcome;
- resulting entity state and emitted event records.

Input validation, deterministic calculations, and reasoning traces remain
valid evidence for the implemented slice. They must not be reclassified as
state-transition or process-execution evidence.

This mismatch does not prove the experiment hypothesis or null hypothesis. It
shows that part of the protocol assumed an executable baseline that was not
present.

## Amended Experimental Design

EXP-0001 proceeds through two explicitly separated evidence tracks.

### Track A: Executable Reasoning Slice

Track A evaluates only behavior that consumes existing executable Kernel APIs:

1. deterministic inventory-risk evaluation;
2. traceability from observations and evidence through judgment and decision;
3. explainability of recommendations;
4. preservation of invariants exposed by the consumed APIs;
5. independent reproduction of the controlled semantic snapshot.

Track A cannot conclude that `Entity`, `Event`, `State`, or `Process` is
sufficient, necessary, or implemented.

### Track B: Workflow Ontology Validation

Track B remains blocked from implementation until a separately reviewed design
defines:

1. an operational meaning for identity, temporal state, occurrence, and
   workflow execution;
2. whether each concept is a Kernel candidate, a derived concept, or an
   application concept;
3. executable contracts and invariants for any accepted Kernel candidate;
4. how valid and invalid transitions will be observed without duplicating
   Kernel responsibility in the application;
5. a versioned experiment baseline after any separately accepted architectural
   change.

An RFC or ADR produced for Track B is evidence collection, not automatic
authorization to change the Kernel. Any Kernel change requires its own accepted
decision and must not be folded silently into EXP-0001.

## Integrity Rules

- Do not add application types named `Entity`, `Event`, `State`, or `Process`
  merely to make the original checklist pass.
- Do not claim that input validation is state-transition validation.
- Do not promote a draft ontology candidate through experimental wording.
- Do not rewrite prior execution evidence after this amendment.
- Record Track A and Track B results separately.
- Preserve the independent-replication requirement for Track A.

## Completion Impact

EXP-0001 remains **In Progress** with the disposition **Continue
Investigation**. Its original full-workflow success criteria cannot be marked
complete until Track B has an accepted operational design and executable
evidence. Track A may still be independently replicated and assessed within its
declared scope.

This amendment changes the interpretation of future evidence. It does not alter
the original hypothesis, fill the replication record, or authorize Kernel
evolution.
