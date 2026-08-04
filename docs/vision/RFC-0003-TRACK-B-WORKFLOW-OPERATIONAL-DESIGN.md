# RFC-0003: Track B Workflow Operational Design

**Version:** 0.1

**Status:** Accepted Experimental Baseline

**Accepted:** 2026-08-04 through PR #28

## Objective

Define the smallest operational design that can test identity, temporal state,
occurrence, and workflow execution in EXP-0001 without assuming that `Entity`,
`State`, `Event`, or `Process` is already a Kernel primitive.

This RFC is an experimental design proposal. It does not authorize code changes
or promote any concept into the Kernel.

## Context

EXP-0001 Protocol Amendment 001 established that the four workflow concepts in
the original protocol are absent from the executable Kernel baseline. The
current Kernel provides reusable language and reasoning contracts, including
`Identifier`, `Timestamp`, `Observation`, `Evidence`, `Decision`, `Action`, and
`Outcome`.

The next experiment must distinguish two questions:

1. Can Marketplace Operations express the workflow by composing existing
   Kernel contracts with domain-owned representations?
2. Does a concrete insufficiency demonstrate the need for a new universal
   Kernel abstraction?

The first question must be tested before the second can justify architecture.

## Proposed Classification

| Operational need | Proposed experimental representation | Classification for this experiment | Kernel implication |
|---|---|---|---|
| Stable identity | A domain-specific SKU reference backed by `Identifier` | Application concept using Kernel language | `Entity` remains a candidate; identity reuse alone does not prove it is a primitive. |
| Temporal state | An immutable inventory snapshot with a SKU reference and `Timestamp` | Application projection | `State` remains a candidate; the experiment tests whether a domain projection is sufficient. |
| Occurrence | An immutable inventory occurrence describing a domain change at a `Timestamp` | Application record that can produce an `Observation` | Do not introduce a universal `Event`; occurrence and observation remain distinct. |
| Workflow execution | A deterministic application workflow that evaluates a command against a snapshot and returns a transition result | Derived application behavior | Do not introduce a universal `Process`; orchestration remains outside the Kernel unless insufficiency is demonstrated. |
| Intervention | A domain implementation of the existing `Action` contract | Application specialization of a Kernel concept | Exercises the existing contract without expanding it. |
| Observed consequence | A domain implementation of the existing `Outcome` contract | Application specialization of a Kernel concept | Separates intended action from observed result. |

These classifications are hypotheses for Track B. Passing tests would show
that this particular workflow can be composed without new Kernel primitives;
they would not prove that the candidate ontology is universally sufficient.

## Minimal Operational Vocabulary

The proposed implementation should use domain names rather than shadow Kernel
names:

- `SkuRef`: stable identity for one marketplace SKU;
- `InventorySnapshot`: available units for a SKU at an effective timestamp;
- `InventoryOccurrence`: a dated receipt, reservation, sale, or adjustment;
- `InventoryCommand`: a requested domain intervention;
- `InventoryTransition`: the accepted transition from one snapshot to another,
  including the occurrence that explains the change;
- `RejectedInventoryTransition`: an unchanged snapshot plus a rejection reason;
- `InventoryIntervention`: the selected action;
- `InventoryOutcome`: the observed consequence after an intervention.

Names may be refined during implementation, but they must remain explicit
domain concepts. Types named only `Entity`, `State`, `Event`, or `Process` are
out of scope.

## Operational Semantics

### Identity

Two records refer to the same SKU only when their `SkuRef` values are equal.
The reference remains stable across inventory changes. The experiment does not
attempt entity resolution across conflicting external identifiers.

### Temporal State

An `InventorySnapshot` describes one SKU at one effective timestamp. A
transition never mutates the prior snapshot; it produces a later snapshot.
Snapshots are operational projections, not claims of direct access to reality.

### Occurrence

An `InventoryOccurrence` records what the workflow accepted as changing the
inventory projection. It must identify the SKU, occurrence kind, quantity, and
effective timestamp. It is not automatically evidence or truth. An
`Observation` may be created when the occurrence is perceived or recorded.

### Workflow Execution

The workflow is a deterministic application function:

```text
current snapshot + command -> accepted transition | rejected transition
```

An accepted transition returns the prior snapshot, resulting snapshot,
occurrence, and trace. A rejected transition returns the unchanged snapshot,
reason, and trace. No marketplace API, persistence, clock lookup, or autonomous
external action occurs inside the function.

### Action and Outcome

Selecting an `InventoryIntervention` does not imply execution. An
`InventoryOutcome` is recorded separately and may confirm, contradict, or be
unrelated to the expected impact. This preserves the Kernel distinction between
`Action` and `Outcome`.

## Required Invariants

1. A transition cannot change the SKU reference.
2. A resulting snapshot must be later than its prior snapshot.
3. Available units cannot be negative.
4. A positive receipt or adjustment increases available units by its declared
   quantity.
5. A sale or reservation cannot consume more units than are available.
6. A rejected command leaves the snapshot unchanged and emits no accepted
   occurrence.
7. Identical snapshot and command inputs produce identical transition results.
8. Every accepted result contains a trace linking input, rule, occurrence, and
   resulting snapshot.
9. An action, its expected impact, and an observed outcome remain separate
   records.
10. Application types do not claim universal Kernel status.

## Experimental Scenarios

The minimum executable suite must cover:

| Scenario | Expected observation |
|---|---|
| Receive replenishment for the same SKU at a later timestamp | Transition accepted; units increase; occurrence and trace are retained. |
| Consume available inventory for a sale | Transition accepted; units decrease without becoming negative. |
| Consume more inventory than available | Transition rejected; snapshot unchanged; no accepted occurrence. |
| Apply a command to a different SKU | Transition rejected; identity preserved. |
| Apply a command at the same or an earlier timestamp | Transition rejected; temporal ordering preserved. |
| Repeat an accepted and a rejected execution with identical inputs | Results are structurally identical. |
| Record an outcome after an intervention | Outcome remains distinguishable from the action and expected impact. |

## Evidence to Collect

- immutable input snapshots and commands;
- accepted occurrences;
- resulting snapshots;
- rejected-transition records;
- complete deterministic traces;
- repeated-execution comparisons;
- action, expected-impact, and observed-outcome records;
- full build and test results;
- source commit and environment metadata.

## Falsification and Escalation

A failing domain rule is not automatically a Kernel limitation. A Kernel RFC
may be opened only when the implementation records all of the following:

1. a concrete workflow requirement that cannot be represented faithfully;
2. the exact existing Kernel contracts evaluated;
3. a failing or insufficient executable scenario;
4. why an application representation would duplicate a universal
   responsibility or break a required invariant;
5. the smallest domain-independent concept that could remove the limitation;
6. evidence against reducing that concept to an existing abstraction.

Until those conditions are met, the limitation stays in the application or the
experiment continues investigating.

## Alternatives Considered

### Add Entity, State, Event, and Process to the Kernel first

Rejected for this experiment because it assumes the conclusion and bypasses
primitive validation.

### Define application types with the same universal names

Rejected because it would hide the Kernel gap and create ambiguous ownership.

### Treat observations as events and snapshots as objective state

Rejected because an observation is a recorded perception, while an occurrence
describes a domain change and a snapshot is a temporal projection.

### End Track B because the concepts are absent

Rejected because absence is a useful experimental condition: composition with
existing contracts can test whether additional universal abstractions are
actually necessary.

## Decision

This operational design is accepted as the baseline for the Track B application
experiment. Acceptance authorizes only that application experiment. It does
not authorize a Kernel change, accept the EXP-0001 hypothesis, or complete
Track B.
