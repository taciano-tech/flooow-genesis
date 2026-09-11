# ADR-0019: Canonical Inventory Candidate Adjudication Boundary

Status: Proposed

Date: 2026-08-13

## Context

V012 freezes an explicit candidate set and TASK-0086 classifies its structural
relationship without selecting a member. Exact agreement still needs one cited
provenance path if a later workflow is to continue. A single candidate must be
confirmed deliberately. Measure mismatch and exact divergence require a trusted
review outside the comparator.

The unsafe shortcut would be to make the comparator choose the first member, a
minimum, maximum, newest, provider-named, or otherwise preferred source. That
would silently turn representation order or an unapproved heuristic into source
authority.

Genesis therefore needs an explicit decision boundary:

> a trusted application workflow may cite exactly one member of one immutable
> snapshot after review, while the platform records who decided and why, without
> inventing an automatic winner or business-stock balance.

## Decision

Introduce an immutable Canonical Inventory Candidate Adjudication.

The caller supplies a trusted organization, request ID, snapshot ID, one exact
lineage root already frozen in that snapshot, a controlled reason, principal,
and correlation. The repository loads the snapshot and comparison. The caller
cannot supply the chosen member's connection, measure, quantity, provenance,
target, comparison class, or timestamp.

The decision records only the frozen snapshot and chosen lineage reference. Its
read model reconstructs the exact chosen V012 member and the deterministic
TASK-0086 comparison.

A new adjudication requires the owning organization to be active. No current
connection, target, mapping, acceptance, selection, or source lifecycle state is
consulted because the decision is over the already immutable snapshot.

## Controlled reasons

```text
SINGLE_CANDIDATE_CONFIRMATION
EXACT_AGREEMENT_CONFIRMATION
MEASURE_POLICY_REVIEW
EVIDENCE_QUALITY_REVIEW
CONTROLLED_EXCEPTION
```

The reason must agree with the comparison shape:

- `SingleCandidate` permits only `SINGLE_CANDIDATE_CONFIRMATION`;
- `ExactAgreement` permits only `EXACT_AGREEMENT_CONFIRMATION`;
- `MeasureMismatch` permits `MEASURE_POLICY_REVIEW` or `CONTROLLED_EXCEPTION`;
- `ExactDivergence` permits `EVIDENCE_QUALITY_REVIEW` or
  `CONTROLLED_EXCEPTION`.

Reasons explain the decision category. They do not establish global source
priority, trust score, ownership, freshness, or future authority.

## Snapshot finality

One snapshot may have at most one adjudication. A different conclusion requires
a new evidence snapshot and a new decision; V013 does not rewrite history or
provide correction, retirement, or deletion.

`(organizationId, requestId)` is unique. Same request and same snapshot/member/
reason replay the existing decision. Any disagreement conflicts. A concurrent
different request for the same snapshot creates one adjudication and one
conflict.

Historical reads and identical replay survive later organization, connection,
target, mapping, acceptance, selection, or source lifecycle changes because the
decision cites immutable historical evidence.

## No business-stock semantics

An adjudicated member is evidence chosen for a later policy. It is not a current
inventory balance and creates no operational authority outside this one snapshot.

The boundary performs no:

- automatic selection, ranking, scoring, voting, or fallback;
- source-health or freshness evaluation;
- tolerance, severity, materiality, or percentage computation;
- aggregation, conversion, rounding, clamp, or arithmetic;
- reservation, availability, replenishment, pricing, purchase, or publication;
- recommendation, approval workflow, event, notification, or external action.

## Persistence

V013 stores one immutable adjudication header with organization, adjudication ID,
request ID, snapshot ID, chosen lineage root, controlled reason, trusted
principal, correlation, and decision time. It stores no quantity, measure,
connection, target, copied provenance, comparison result, source text, rank,
score, timestamp from a provider, or business value.

The chosen member is protected by an organization-scoped foreign key to the V012
snapshot member. Deferred validation independently checks that the reason agrees
with the deterministic comparison of the complete immutable snapshot.

## Consequences

### Positive

- no member is selected implicitly;
- every continuation cites exact frozen provenance and a trusted decision;
- agreement, incomparability, and divergence require appropriate reasons;
- concurrent decisions cannot produce two winners for one snapshot;
- V008 remains the only quantity ledger and the Kernel remains unchanged.

### Negative

- a trusted workflow must supply the decision;
- no reusable source-authority policy is created;
- a correction needs a new snapshot rather than mutation;
- adjudication still does not create operational stock.

## Alternatives considered

### Automatically accept exact agreement

Rejected because a deliberate audit boundary is still required before a chosen
provenance path can feed later decisions.

### Choose the first member

Rejected because canonical order is representation, not authority.

### Store the chosen quantity

Rejected because V008 already stores the immutable exact fact and V012 freezes
its provenance.

### Create a reusable source rank now

Rejected because authority scope, ownership, lifecycle, administration, and
health have not yet been specified.

## Authorization

This ADR alone authorizes no implementation. SPEC-0019 may authorize only a pure
adjudication contract, additive V013 immutable reference ledger, transactional
explicit decision/replay/read behavior, and tests. It authorizes no Kernel,
runtime, API, provider, automatic choice, global authority, business stock,
assessment, recommendation, event, or action.
