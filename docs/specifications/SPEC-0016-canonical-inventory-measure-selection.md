# SPEC-0016: Canonical Inventory Measure Selection

Status: Proposed

Date: 2026-08-12

## Objective

Define the smallest implementation after V010 that explicitly selects one
present exact measure for one accepted source-mapping lineage without deriving a
formula, reconciling sources, aggregating locations, rounding, or declaring
business stock.

## Authorized next implementation

Acceptance authorizes TASK-0082 only:

1. add a pure `applications:inventory-measure-selection` Kotlin module;
2. define selection IDs, correlations, measure vocabulary, principals, reasons,
   decisions, results, selected candidates, repository, service, and history;
3. add additive PostgreSQL migration V011 for selection revisions and retirement
   audit;
4. implement transactional initial selection, replacement, withdrawal, exact
   current resolution, and ordered history;
5. test exact measure preservation, missing values, replay, correction,
   concurrency, lifecycle, isolation, immutability, and privacy;
6. leave runtime composition, connector behavior, providers, API/OpenAPI,
   Marketplace Operations, assessment, event, delivery, and deployment behavior
   unchanged.

No provider adapter, credential use, external call, automatic selection, bulk
scan, formula, fallback, source ranking, staleness threshold, reconciliation,
aggregation, rounding, display conversion, business availability, mutation,
assessment, event, worker, scheduler, or public administration is authorized.

## Module boundary

`applications:inventory-measure-selection` is a pure Kotlin/JDK module. Its only
project dependencies are:

- `platform:foundation:organization-context`;
- `applications:integration-control-plane`;
- `applications:inventory-identity-mapping`;
- `applications:inventory-canonical-observation`;
- `applications:inventory-source-acceptance`.

It has no dependency on marketplace operations, API, persistence, connector
runtime, ingestion, Kernel, HTTP, JSON, SQL, logging framework, provider,
scheduler, filesystem, environment, or cryptography.

## Canonical values

The module exports UUID-backed values:

```text
CanonicalInventoryMeasureSelectionId
CanonicalInventoryMeasureSelectionCorrelationId
```

Parsing accepts canonical lowercase UUID text. `toString` is `[INTERNAL]` and
explicit UUID access exists only for persistence adapters.

`InventoryMeasureSelectionPrincipalReference` follows the trusted-principal
rules: NFC normalized, already trimmed, no ISO control characters, 1..128 UTF-8
bytes, and redacted rendering.

## Measure vocabulary

```text
CanonicalInventoryMeasure
  AVAILABLE_TO_SELL
  ON_HAND
  RESERVED
  PENDING_INBOUND
  PENDING_OUTBOUND
```

The vocabulary is closed. Free text, provider field names, JSON paths, formulas,
expressions, constants, priorities, lists, and fallback chains are forbidden.

## Selection decision

```text
CanonicalInventoryMeasureSelection(
  id,
  organizationId,
  connectionId,
  capability = inventory.source-balance.read,
  lineageRootDecisionId,
  revision,
  state,
  measure,
  anchorAcceptanceId,
  anchorAcceptanceRevision,
  anchorObservationId,
  principalReference,
  reason,
  correlationId,
  selectedAt,
  retiredAt?,
  supersedesSelectionId?
)
```

The anchor records which active acceptance and observation demonstrated that the
field existed when the policy was selected. It is provenance, not a permanent
binding to one quantity. Current resolution applies the active policy to the
current active acceptance in the same exact lineage.

Revisions start at 1. Revision 1 has reason `INITIAL_SELECTION` and no
predecessor. A replacement increments exactly one revision, cites the current
selection, and uses one of:

```text
SOURCE_SEMANTICS_CORRECTION
OPERATOR_CORRECTION
```

Withdrawal uses one of:

```text
SOURCE_SEMANTICS_REVOKED
OPERATOR_WITHDRAWAL
```

State is `ACTIVE` or `RETIRED`. Content is immutable. Replacement or withdrawal
retires the prior selection and appends one separate retirement audit in the
same transaction.

## Selected candidate

```text
SelectedCanonicalInventoryMeasure(
  organizationId,
  connectionId,
  capability,
  lineageRootDecisionId,
  selectionId,
  selectionRevision,
  acceptanceId,
  acceptanceRevision,
  observationId,
  sourcePointer,
  projectionRevision,
  mappingDecisionId,
  mappingRevision,
  target,
  measure,
  exactQuantity
)
```

The selected candidate is an internal read model, not a stored business balance.
It copies no source item, source location text, SKU, provider unit text, provider
timestamp, source commit time, projection time, acceptance time, principal,
reason, payload, or credential.

`exactQuantity` is the unchanged reduced signed rational from V008. Resolution
performs no arithmetic. Negative, positive, and zero quantities remain exact.

## Initial selection validation

Initial selection requires, inside one transaction:

- active organization;
- same-organization connection in `ACTIVE` or `SUSPENDED` state;
- exact V007 revision-1 mapping root;
- one active V010 acceptance for that root;
- accepted observation, mapping leaf, and target agreement;
- one active V007 leaf descending contiguously from the root;
- active same-organization target identities;
- the requested measure is non-null in the accepted V008 observation;
- no prior selection history for the lineage.

The anchor acceptance ID/revision and observation ID are loaded by the repository;
callers cannot claim a different anchor.

## Replacement and withdrawal

Replacement requires the exact active selection ID and revision. The requested
measure must be present in the current accepted observation.

```text
requested measure == current measure
  => ALREADY_SELECTED

different controlled measure present in current accepted observation
  => append next selection revision

requested measure absent
  => MEASURE_UNAVAILABLE

stale expected selection ID or revision
  => CONFLICT
```

Timestamps, quantities, and provider labels never establish selection order.

Withdrawal requires the exact active selection ID and revision. It leaves no
active selection and does not fall back to a prior revision. Replay of the same
completed withdrawal is controlled and never restores a selection.

## Current resolution

Resolution is read-only and requires trusted organization scope. It joins:

1. the exact mapping lineage root;
2. its one active measure selection;
3. its one active V010 acceptance;
4. the exact V008 observation named by that acceptance;
5. the candidate active mapping leaf and copied target.

Resolution returns `Unselected` when no active selection exists and `Unaccepted`
when no active acceptance exists. If the selected field is null in the current
accepted observation, it returns `MeasureUnavailable`; it never uses the anchor
quantity and never falls back to another field.

The resolver validates copied acceptance and observation provenance before
returning a candidate. Duplicate active rows, divergent copies, invalid lineage,
or impossible state return `IntegrityFailure`.

Historical selection reads remain available after organization, connection,
mapping, target, acceptance, or selection retirement. Current resolution fails
closed when no active acceptance or selection exists.

## Controlled results

Write results are:

```text
Selected(selectionId, revision)
AlreadySelected(selectionId, revision)
Withdrawn(revision)
Unaccepted
CandidateUnavailable
LineageUnavailable
TargetUnavailable
MeasureUnavailable
Conflict
IntegrityFailure
```

Resolution results are:

```text
Resolved(selectedCandidate)
Unselected
Unaccepted
MeasureUnavailable
IntegrityFailure
```

Write results expose no organization, connection, lineage, acceptance,
observation, mapping, target, measure, quantity, correlation, time, principal, or
source value except the redacted selection ID wrapper returned on success.

## PostgreSQL migration V011

`integration_inventory_measure_selection` contains:

```text
organization_id uuid
selection_id uuid
connection_id uuid
capability text
lineage_root_decision_id uuid
revision integer
state text
measure text
anchor_acceptance_id uuid
anchor_acceptance_revision integer
anchor_observation_id uuid
principal_ref text
reason text
correlation_id uuid
selected_at timestamptz
retired_at timestamptz nullable
supersedes_selection_id uuid nullable
```

Constraints include:

- primary key `(organization_id, selection_id)`;
- unique `(organization_id, lineage_root_decision_id, revision)`;
- one partial unique active row per organization and lineage root;
- organization-scoped foreign keys to connection, lineage root, anchor
  acceptance, anchor observation, predecessor selection, and retirement audit;
- positive contiguous revisions and valid initial/replacement shapes;
- exact capability and lifecycle checks;
- active anchor acceptance and non-null anchor measure validation at insert;
- exact root/leaf/acceptance/observation/target agreement;
- content immutability and rejected delete.

`integration_inventory_measure_selection_retirement` contains organization,
selection ID, trusted principal, replacement or withdrawal reason, correlation,
and retirement time. It has exactly one immutable row per retired selection.

V011 stores no quantity, source text, provider unit text, payload, provider
timestamp, credential, formula, fallback, priority, source rank, staleness
threshold, rounded value, business availability, assessment, event, or
destination. Quantities remain only in V008.

## Concurrency and atomicity

- every mutation locks the lineage root before reading selection state;
- initial selection races create one active revision;
- replacement and withdrawal use expected selection ID and revision;
- two competing replacements produce one successor and one `Conflict`;
- selection retirement, retirement audit, and successor insert are atomic;
- injected failures roll back the entire transaction;
- database constraints independently reject direct invalid writes.

## Privacy and observability

- IDs render `[INTERNAL]`;
- principal and decision renderings are redacted;
- exact quantities never appear in write-result text, exception text, or logs;
- SQL and database messages are translated to controlled outcomes;
- organization, connection, acceptance, observation, mapping, target, and
  selection predicates are explicit;
- no credential, vault, protector, provider, connector, or external system is
  opened by selection or resolution.

## Test plan

TASK-0082 proves at least:

1. allowed dependency graph and pure module boundary;
2. canonical UUID parsing and redaction;
3. principal NFC, trimming, control, and UTF-8 limits;
4. V011 applies after V001 through V010;
5. each of the five present measures can be selected;
6. first selection creates revision 1 with exact anchor provenance;
7. exact replay is `AlreadySelected`;
8. missing requested measure is `MeasureUnavailable`;
9. missing does not become zero and does not fall back;
10. replacement creates a contiguous revision and retirement audit;
11. replacement reason category is enforced;
12. stale expected ID or revision conflicts;
13. current resolution uses the current acceptance, not the anchor quantity;
14. a later accepted observation with the selected measure resolves exactly;
15. a later accepted observation missing the selected measure fails closed;
16. negative, zero, and rational quantities resolve unchanged;
17. foreign organization, connection, acceptance, observation, mapping, target,
    and lineage fail closed;
18. retired or divergent lineage blocks new selection;
19. retired target blocks new selection;
20. active and suspended connections permit deliberate selection;
21. draft and revoked connections reject new selection;
22. concurrent initial selection creates one head;
23. concurrent replacements create one successor;
24. replacement retirement audit is atomic;
25. withdrawal leaves no active selection and preserves ordered history;
26. withdrawal replay never restores an older selection;
27. historical reads survive later lifecycle retirement;
28. direct content updates and deletes are rejected;
29. direct duplicate-active and non-contiguous revisions are rejected;
30. injected transaction failure changes nothing;
31. renderings and controlled results expose no sensitive field or quantity;
32. runtime, API/OpenAPI, connectors, assessments, events, and deployment remain
    unchanged;
33. complete repository build and persistent runtime package remain green.

## Remaining boundary

Provider adapters, production encryption and scheduling, automatic projection or
selection, selection administration workflow and roles, provider-specific
sequence validation, source health, wall-clock staleness, source authority,
cross-connection and cross-location reconciliation, aggregation, rounding,
display units, business availability, conversion to `InventorySnapshot` or
`InventoryRiskInput`, inventory mutation, assessments, events, and outbound
stock writes require later accepted specifications.

## Acceptance

Merging ADR-0016 and SPEC-0016 authorizes TASK-0082 only. It does not authorize a
real provider, public route, automatic selector, formula, fallback, source rank,
staleness threshold, reconciliation, aggregation, rounding, business stock,
mutation, assessment, event, worker, scheduler, or external request.
