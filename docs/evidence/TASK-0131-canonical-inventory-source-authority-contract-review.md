# TASK-0131: Canonical Inventory Source Authority Contract Review

Status: Contract accepted for implementation

Date: 2026-08-20

## Repository inspection

Inspected canonical `main` at merge commit `d11ce27`, merged PRs through #126,
the Marketplace Intelligence Operating Model, the protected Trust roadmap,
and inventory ADRs/specifications through the candidate-adjudication boundary.

The active Pricing sequence now retains:

- selected Product Cost evidence;
- source and derived Net-Back scenarios and floors;
- exact monetary floor deltas;
- both assessments of one observed-price fact;
- a canonical sixteen-value position-transition taxonomy.

That path has reached a complete diagnostic fact. Advancing directly to
improvement, materiality, preference, or recommendation would require an
economic objective and additional market, inventory, policy, and authority
evidence.

The Trust roadmap already identifies a prerequisite chain before Inventory
Confidence and Safe ATP. Repository inspection confirms that the first missing
fact remains reusable source authority.

## Reuse audit

The new contract reuses `SelectedCanonicalInventoryMeasure`, which already
retains exact organization, connection, capability, lineage, acceptance,
observation, mapping, target, selected measure, and rational quantity evidence.

It does not duplicate:

- source ingestion or canonical observation;
- acceptance or measure selection;
- candidate snapshot, comparison, or adjudication;
- item, location, unit, measure, organization, or connection identities;
- Marketplace Economic Truth, pricing, or Kernel vocabulary.

## Boundary decision

ADR-0038 and SPEC-0038 define one pure, production-inactive assessment:

```text
selected canonical measure
  + exact versioned source-authority policy
  + explicit evaluation time
  -> authorized assessment or typed scope/time mismatch
```

The policy is exact over organization, connection, source-balance capability,
canonical target, selected measure, and bounded effective interval.

Authority means eligibility to contribute evidence only. It does not imply
freshness, health, correctness, ownership, priority, current-state winner,
business availability, confidence, ATP, publication, or mutation authority.

## Why authority is isolated first

Combining authority with freshness or health would require timestamps and
operational signals that `SelectedCanonicalInventoryMeasure` deliberately does
not retain. Combining it with current-state selection would also require
provider succession and reconciliation policy.

The isolated contract therefore creates the smallest honest dependency:

```text
accepted and selected evidence
  -> source authority
  -> later source freshness and health
  -> later canonical current state
  -> later operational reservations and demand
  -> later Inventory Confidence
  -> later Safe ATP
```

## Explicit exclusions

- no source rank, weight, priority, fallback, or winner;
- no freshness threshold, health state, confidence score, or tolerance;
- no quantity calculation, aggregation, reservation, availability, or ATP;
- no provider-specific rule or credential-derived authority;
- no persistence, policy administration, API, runtime, event, connector, UI,
  external action, AI, or Kernel change.

## Authorization outcome

Acceptance authorizes TASK-0132 only: implement the new pure
`inventory-source-authority` module, policy and version values, exact assessor,
controlled results, redaction, focused tests, and evidence defined by
SPEC-0038.

The next task must not expand into freshness, health, current-state selection,
Inventory Confidence, Safe ATP, or inventory execution.
