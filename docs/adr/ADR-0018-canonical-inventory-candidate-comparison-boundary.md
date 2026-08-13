# ADR-0018: Canonical Inventory Candidate Comparison Boundary

Status: Proposed

Date: 2026-08-13

## Context

V012 freezes an explicit, immutable set of V011 candidates for one exact target.
It deliberately does not compare those candidates. A later reconciliation policy
must not infer agreement from matching source names, timestamps, decimal display,
or iteration order, and must not compare different inventory measures as though
they represented the same fact.

Before source authority, freshness, tolerance, or reconciliation can be defined,
Genesis needs a smaller descriptive boundary:

> compare the already frozen candidates using only their canonical measure and
> exact reduced rational quantity, without choosing or modifying anything.

## Decision

Introduce a production-inactive Canonical Inventory Candidate Comparison as a
pure, deterministic application function over one validated V012 snapshot view.

The comparison produces exactly one structural classification:

- `SingleCandidate` when the snapshot contains one member;
- `MeasureMismatch` when multiple members select different canonical measures;
- `ExactAgreement` when multiple members have the same measure and identical
  reduced rational quantity;
- `ExactDivergence` when multiple members have the same measure but at least two
  distinct exact rational quantities;
- `IntegrityFailure` when the supplied view violates snapshot invariants.

No timestamp, connection, provider, insertion order, source pointer, or lineage
identifier influences the classification.

## Exact semantics

Comparison uses `ExactInventoryQuantity` equality. It performs no decimal
conversion. Equivalent fractions are already reduced at the V008 boundary.
Negative values and zero are compared exactly and are not clamped or rejected.

`SingleCandidate` is not called agreement because no second independent member
was compared. `MeasureMismatch` is not divergence because `ON_HAND` and
`AVAILABLE_TO_SELL`, for example, are different controlled facts.

## No reconciliation semantics

The comparison does not define or infer:

- source authority, ownership, priority, trust, confidence, or rank;
- freshness, staleness, provider time, or wall-clock policy;
- tolerance, percentage difference, materiality, severity, or risk;
- a winner, fallback, preferred candidate, or canonical business balance;
- addition, subtraction, minimum, maximum, average, voting, conversion, or rounding;
- sellable, reservable, purchasable, publishable, or financially recognized stock;
- an incident, recommendation, approval, mutation, event, or external action.

Member order remains deterministic representation only.

## Persistence

No migration or comparison ledger is introduced. The result is a derived internal
read model and can be reproduced from the immutable V012 snapshot and V008 facts.
Persisting an assessment, decision, or policy outcome requires a later contract.

## Consequences

### Positive

- later policy can distinguish incomparability from exact disagreement;
- exact rational semantics remain intact;
- the step is reproducible and independent of current source lifecycle;
- no authority or business-stock decision leaks into the evidence layer;
- the Kernel remains unchanged.

### Negative

- exact divergence is not prioritized or resolved;
- operationally insignificant differences are still divergent;
- mixed measures remain incomparable until explicit measure policy exists;
- no result is persisted or exposed publicly.

## Alternatives considered

### Reconcile immediately

Rejected because authority, freshness, tolerance, and business meaning are
separate policies that have not been accepted.

### Compare displayed decimals

Rejected because formatting and rounding can create false agreement.

### Treat a single candidate as agreement

Rejected because agreement requires comparison.

### Persist every comparison

Rejected because the deterministic classification is reproducible and no audit
or decision lifecycle has yet been specified.

## Authorization

This ADR alone authorizes no implementation. SPEC-0018 may authorize only a pure
comparison module and deterministic tests. It authorizes no Kernel change,
migration, repository, API, runtime wiring, source policy, reconciliation,
aggregation, business stock, assessment, recommendation, event, or action.
