# ADR-0038: Canonical Inventory Source Authority Boundary

Status: Proposed

Date: 2026-08-20

## Context

The canonical inventory path already retains typed source balances, exact
observations, explicit acceptance, measure selection, frozen candidate
snapshots, exact comparison, and human adjudication. None of those contracts
states that a connection is authorized to supply one selected measure for one
canonical target.

Provider access, successful ingestion, mapping, acceptance, selection, and
human adjudication are evidence-processing decisions. They are not reusable
source authority. Without an explicit boundary, later current-state,
Inventory Confidence, and Safe ATP work could accidentally infer authority
from a valid credential, a recent write, candidate presence, or quantity.

The first missing Trust dependency is therefore narrower than source health or
freshness:

> Does one versioned organizational policy authorize this exact accepted and
> selected source candidate for this target and measure at the supplied time?

## Decision

Introduce a pure, production-inactive source-authority assessment in a new
inventory application module.

The assessment consumes:

- one existing `SelectedCanonicalInventoryMeasure`;
- one explicit `CanonicalInventorySourceAuthorityPolicy`;
- one caller-supplied `evaluatedAt` instant.

The policy scope is exact over:

- organization;
- integration connection;
- inventory source-balance capability;
- canonical inventory target;
- selected canonical measure;
- half-open effective interval;
- policy version.

An authorized assessment retains the exact candidate, policy, and evaluation
time. Scope or time disagreement returns a typed controlled result and no
partial assessment.

## Authority means evidence eligibility only

Authority in this contract means only that approved organizational policy
allows the connection to contribute the selected measure for the exact target
at the evaluation time.

It does not mean that:

- the provider credential is valid or sovereign;
- the source is healthy, fresh, correct, complete, or reconciled;
- its quantity is business availability or Safe ATP;
- the source owns the inventory, location, product, or organization;
- it outranks another authorized source;
- it becomes the canonical current-state winner;
- a human adjudication becomes a reusable policy;
- any inventory may be published or mutated.

Several sources may be independently authorized for the same target and
measure. Provider succession, priority, reconciliation, and winner selection
remain later boundaries.

## Policy interval and determinism

The effective interval is:

```text
effectiveFrom <= evaluatedAt < effectiveUntil
```

`effectiveUntil` is mandatory and later than `effectiveFrom`. A bounded
interval prevents an accidental permanent grant and makes policy replacement
explicit. Boundary instants are deterministic: the start is included and the
end is excluded.

The assessor reads no wall clock, environment, repository, credential store,
or provider API. The same candidate, policy, and evaluation time always
produce the same result.

## Controlled results

The result vocabulary distinguishes:

```text
Authorized(assessment)
OrganizationMismatch
ConnectionMismatch
TargetMismatch
MeasureMismatch
PolicyNotYetEffective
PolicyExpired
```

The source-balance capability is guaranteed by both existing candidate and new
policy construction, so no unreachable capability-mismatch result is defined.
Checks run from broad identity to specific scope and finally time, so one input
has one deterministic result. No fallback policy or permissive default exists.

## Existing evidence remains sovereign

The assessor does not reconstruct acceptance, measure selection, mapping, or
quantity. `SelectedCanonicalInventoryMeasure` remains the existing complete
selected-candidate evidence. Its organization, connection, capability,
target, and measure are compared exactly with policy scope.

The new contract does not change any existing inventory type, repository, or
database row.

## No infrastructure activation

This boundary adds no policy administration, persistence, migration, API,
event, connector, scheduler, worker, current-state table, UI, alert, or
external inventory action. Policies are explicit domain inputs until a later
administration and persistence contract is accepted.

## Consequences

### Positive

- provider evidence no longer needs to be mistaken for organizational
  authority;
- authority is exact, versioned, bounded, deterministic, and auditable;
- later freshness, health, current-state, confidence, and ATP contracts can
  require an authorized candidate explicitly;
- multiple sources remain possible without premature ranking;
- no Marketplace or provider-specific vocabulary enters the Kernel.

### Negative

- the policy must be supplied explicitly;
- authorization alone cannot select a current value;
- bounded policies require deliberate renewal or replacement;
- freshness and health remain unavailable after this increment.

## Alternatives considered

Treating an active connection as authoritative was rejected because
credentials and operational lifecycle do not grant business authority.
Treating acceptance, measure selection, or adjudication as authority was
rejected because those decisions have narrower evidence-processing purposes.
Embedding priority or a winner in the policy was rejected because succession
and reconciliation are separate decisions. Combining authority, health,
freshness, confidence, and ATP was rejected because it would skip observable
dependencies and create a broad Trust engine. Adding this vocabulary to the
Kernel was rejected because it remains an integration-inventory concern.

## Authorization

This ADR alone authorizes no implementation. SPEC-0038 may authorize only the
pure source-authority policy, exact assessment, controlled mismatch results,
redaction, and focused tests for TASK-0132.

No health, freshness, priority, score, reconciliation, current-state winner,
aggregation, business availability, Inventory Confidence, Safe ATP,
recommendation, authority to act, persistence, runtime, or Kernel change is
authorized.
