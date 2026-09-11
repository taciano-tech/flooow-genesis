# SPEC-0015: Canonical Inventory Source Acceptance

**Status:** Proposed

**Date:** 2026-08-12

**Source decision:** ADR-0015

## Objective

Persist an explicit, immutable, organization-scoped accepted head for one exact
inventory source-mapping lineage without ranking sources, aggregating quantities,
or declaring business stock.

## Authorized next implementation

Acceptance authorizes TASK-0080 only:

1. add a pure `applications:inventory-source-acceptance` Kotlin module;
2. define acceptance IDs, correlations, reasons, decisions, head, outcomes,
   repository, service, and history contracts;
3. add additive PostgreSQL migration V010 for acceptance revisions and retirement
   audit;
4. implement transactional initial acceptance, replacement, withdrawal, exact
   head read, and ordered history;
5. prove succession, stale fencing, mapping reinterpretation, concurrency,
   lifecycle, isolation, immutability, and privacy;
6. leave production startup, HTTP, connectors, projection, Marketplace
   Operations, assessments, events, and external traffic unchanged.

No provider adapter, credential use, API call, automatic acceptance, bulk scan,
scheduler, worker, cross-connection authority, staleness duration, source health,
aggregation, reconciliation, rounding, measure selection, business availability,
inventory command, assessment, event, outbox message, or public endpoint is
authorized.

## Module boundary

The pure module may depend only on:

```text
platform:foundation:organization-context
applications:integration-control-plane
applications:inventory-identity-mapping
applications:inventory-canonical-observation
```

It has no Kernel, Marketplace Operations, database, HTTP, serialization,
framework, provider, scheduler, filesystem, environment, or cryptography access.

## Canonical values

The module exports UUID-backed values:

```text
CanonicalInventoryAcceptanceId
CanonicalInventoryAcceptanceCorrelationId
```

Parsing accepts canonical lowercase UUID text. `toString` is `[INTERNAL]` and
explicit UUID access exists only for persistence adapters.

The mapping lineage is identified by the root `InventoryMappingDecisionId` whose
revision is 1 and whose predecessor is null. A new public source-stream identity
is not introduced. The root ID remains internal and redacted.

## Accepted observation reference

```text
AcceptedCanonicalInventoryObservation(
  observationId,
  sourcePointer,
  projectionRevision,
  mappingDecisionId,
  mappingRevision,
  target
)
```

The reference copies no quantity, source text, provider timestamp, principal,
or payload. Persistence validates every copied field against V008. Quantities
remain available only by scoped observation lookup.

## Acceptance decision

```text
CanonicalInventoryAcceptance(
  id,
  organizationId,
  connectionId,
  capability = inventory.source-balance.read,
  lineageRootDecisionId,
  revision,
  state,
  acceptedObservation,
  principalReference,
  reason,
  correlationId,
  acceptedAt,
  retiredAt?,
  supersedesAcceptanceId?
)
```

Revisions start at 1. Revision 1 has reason `INITIAL_ACCEPTANCE` and no
predecessor. A replacement increments exactly one revision, cites the current
acceptance, and uses one of:

```text
NEW_SOURCE_EVIDENCE
MAPPING_REINTERPRETATION
OPERATOR_CORRECTION
```

Withdrawal uses a controlled reason:

```text
SOURCE_REVOKED
EVIDENCE_INVALIDATED
OPERATOR_WITHDRAWAL
```

Free-text reasons are forbidden. `InventoryAcceptancePrincipalReference` follows
the V007 trusted-principal rules: NFC normalized, already trimmed, no ISO control
characters, 1..128 UTF-8 bytes, and redacted rendering.

State is `ACTIVE` or `RETIRED`. Decision content is immutable. Replacement or
withdrawal retires the prior head and appends a separate retirement audit in the
same transaction.

## Lineage validation

The candidate observation must cite a mapping decision whose predecessor chain:

- remains inside the authorized organization and connection;
- retains the exact capability and nullable source selector;
- has contiguous mapping revisions;
- terminates at `lineageRootDecisionId` with revision 1;
- contains the candidate decision as its active leaf;
- has active target identities at acceptance time.

The candidate V006 source row must exactly supply that leaf decision's selector.
The root mapping evidence pointer need not equal the candidate observation's
source pointer; V009 explicitly permits later matching evidence.

No lineage is inferred from target equality, source SKU, GTIN, title, quantity,
provider timestamp, or UUID coincidence.

## Succession

Let `current` be the active accepted observation and `candidate` the requested
replacement.

```text
candidate progress > current progress
  => NEW_SOURCE_EVIDENCE may advance

candidate pointer == current pointer
and candidate projection revision > current projection revision
and candidate mapping revision > current mapping revision
  => MAPPING_REINTERPRETATION may advance

candidate observation == current observation
  => ALREADY_ACCEPTED

candidate progress < current progress
or candidate projection/mapping revision regresses
  => STALE

candidate progress == current progress
and candidate ordinal != current ordinal
  => CONFLICT
```

`progress` means `inputProgressVersion`. Record ordinal is identity within a page,
not a temporal tie breaker.

`OPERATOR_CORRECTION` may select a later source progress or later reinterpretation
but cannot bypass lineage, exact selector, monotonicity, or lifecycle rules.

Provider timestamps, source commit time, projection time, mapping decision time,
and acceptance time never establish succession. Missing provider time remains
missing and does not block acceptance.

## Operations and controlled results

The service supports:

```text
accept initial candidate for exact lineage
replace expected active acceptance with candidate
withdraw expected active acceptance
read active head for exact scoped lineage
read ordered acceptance history for exact scoped lineage
```

Controlled results are:

```text
Accepted(acceptanceId, revision)
AlreadyAccepted(acceptanceId, revision)
Withdrawn(revision)
Unaccepted
CandidateUnavailable
LineageUnavailable
TargetUnavailable
Stale
Conflict
IntegrityFailure
```

Results expose no organization, connection, lineage, mapping, target,
observation, correlation, source pointer, quantity, time, principal, or source
value except the redacted acceptance ID wrapper returned for successful writes.

Foreign and missing candidate observations are both `CandidateUnavailable`.
Foreign, missing, retired, or divergent mapping lineages are
`LineageUnavailable`. Impossible duplicate-active or divergent replay states are
`IntegrityFailure`.

## Lifecycle

New acceptance requires:

- active organization;
- same-organization connection in `ACTIVE` or `SUSPENDED` state;
- one existing candidate V008 observation;
- one exact active V007 mapping leaf and valid lineage root;
- active same-organization target identities;
- expected active acceptance ID and revision for replacement or withdrawal.

Draft, revoked, unknown, and foreign connections fail closed. Historical head
and history reads require trusted organization scope but remain available after
connection, mapping, target, or organization suspension/retirement. A withdrawn
lineage has no active head and does not fall back to its previous acceptance.

No credential, vault, protector, provider, connector, or external system is
opened by an acceptance operation.

## PostgreSQL migration V010

`integration_inventory_source_acceptance` contains:

```text
organization_id uuid
acceptance_id uuid
connection_id uuid
capability text
lineage_root_decision_id uuid
revision integer
state text
observation_id uuid
source_progress_version bigint
source_record_ordinal integer
projection_revision integer
mapping_decision_id uuid
mapping_revision integer
target_item_id uuid
target_location_id uuid nullable
target_unit_id uuid
principal_ref text
reason text
correlation_id uuid
accepted_at timestamptz
retired_at timestamptz nullable
supersedes_acceptance_id uuid nullable
```

Required structure:

- primary key `(organization_id, acceptance_id)`;
- same-organization foreign keys to connection, lineage root mapping, candidate
  observation, candidate mapping, target identities, and predecessor acceptance;
- unique lineage plus revision;
- one partial unique active row per exact lineage;
- positive contiguous revisions and valid initial/replacement shapes;
- source position and projected fields equal the referenced V008 observation;
- lineage root is revision 1 with no mapping predecessor;
- candidate mapping descends contiguously from that root and exactly selects its
  V006 source row;
- reason agrees with initial, evidence-advance, or reinterpretation shape;
- active/retired time shape is valid;
- timestamp precision is PostgreSQL microseconds.

`integration_inventory_source_acceptance_retirement` contains organization,
acceptance ID, trusted principal, withdrawal or replacement reason, correlation,
and retirement time. It has one row per retired acceptance and no free text.

Triggers reject changes to acceptance content. The only allowed update is the
atomic `ACTIVE` to `RETIRED` lifecycle transition with a matching retirement
audit. Direct delete is always rejected.

V010 stores no source item, location, SKU, unit text, source version, quantity,
factor, provider timestamp, raw payload, credential, secret, Genesis `SkuRef`,
business availability, assessment, event, or destination.

## Concurrency and atomicity

- two initial acceptances for one lineage create one active revision;
- identical replay returns `AlreadyAccepted` only when complete stored content
  agrees;
- replacement and withdrawal use expected acceptance ID and revision;
- two competing replacements produce one successor and one `Conflict`;
- a source-page advance racing a mapping reinterpretation produces one complete
  accepted decision or a controlled retry/conflict, never mixed provenance;
- lineage, observation, mapping, target, and head rows are locked before write;
- forced constraint, trigger, audit, or commit failure rolls back all changes;
- SQL and injected marker text never leaves the repository boundary.

## Privacy and observability

Allowed telemetry dimensions are operation, controlled result, controlled
reason, and duration. Aggregate counts contain no record dimensions.

Forbidden in logs, errors, metrics, spans, and public outcomes:

- all source text and quantities;
- organization, connection, acceptance, observation, mapping, target, and
  correlation IDs;
- lineage root, source pointer, revisions, and timestamps;
- principal, provider, factor, raw payload, and database error text.

## Test plan

1. module dependencies are limited to the four authorized projects;
2. identifiers parse canonical lowercase UUIDs and redact text;
3. trusted principal normalization and limits reproduce V007 safety;
4. V010 applies after V001 through V009;
5. first acceptance creates revision 1 with no predecessor;
6. identical replay appends nothing;
7. a later source progress version advances with `NEW_SOURCE_EVIDENCE`;
8. a lower progress version is stale;
9. different ordinals in one progress version conflict;
10. provider time cannot override source progress order;
11. null provider time remains acceptable;
12. same pointer with later mapping/projection revisions advances as
    `MAPPING_REINTERPRETATION`;
13. same pointer without both greater revisions is stale or integrity failure;
14. different selector, lineage, connection, or organization cannot replace;
15. target equality alone never combines lineages;
16. retired or foreign target blocks a new acceptance;
17. active and suspended connections permit deliberate acceptance;
18. draft and revoked connections fail closed;
19. concurrent initial acceptance creates one head;
20. concurrent replacements create one successor;
21. stale expected head cannot retire the current row;
22. replacement retirement audit is atomic;
23. withdrawal leaves no head and preserves ordered history;
24. withdrawal replay is controlled and never restores an older head;
25. history remains readable after lifecycle retirement;
26. direct SQL content update and delete are rejected;
27. injected failure rolls back head, decision, and audit;
28. source, quantity, principal, IDs, and injected markers leak nowhere;
29. V006 through V009 content remains unchanged;
30. production startup and public OpenAPI remain unchanged;
31. no source contains provider adapters, HTTP, OAuth, bulk scan, scheduler,
    worker, aggregation, reconciliation, assessment, event, or mutation;
32. complete repository build and persistent runtime package remain green.

## Provider and storage constraints

- Mercado Livre stock is independently managed by location and logistics owner;
- Mercado Livre `x-version` fences writes to one stock resource and stale writes
  return conflict; it is not canonical source authority;
- Omie stock position is parameterized by position date, product, and stock
  location and exposes physical, reserved, pending, and balance values;
- provider timestamps and versions are not universally comparable;
- PostgreSQL default `READ COMMITTED` can expose different snapshots across
  statements, so acceptance requires explicit row locks and database constraints;
- serializable execution may still require complete transaction retry, so
  deterministic conflict classification remains an application responsibility.

## References

- Mercado Livre distributed inventory:
  https://developers.mercadolivre.com.br/pt_br/lojas-oficiais/estoque-distribuido
- Mercado Livre multi-origin inventory:
  https://developers.mercadolivre.com.br/pt_br/produto-consulta-de-usuarios/estoque-multi-origem
- Omie inventory query API:
  https://app.omie.com.br/api/v1/estoque/consulta/
- PostgreSQL concurrency control:
  https://www.postgresql.org/docs/current/mvcc.html
- PostgreSQL transaction isolation:
  https://www.postgresql.org/docs/current/transaction-iso.html

## Remaining boundary

Provider adapters, production encryption and scheduling, automatic projection,
acceptance workflow and roles, provider-specific sequence validation, source
health, wall-clock staleness, cross-connection authority, location ownership,
measure selection, source reconciliation, aggregation, rounding, display units,
business availability, conversion to `InventorySnapshot` or
`InventoryRiskInput`, inventory mutation, assessments, events, and outbound
stock writes require later accepted specifications.

## Acceptance

Merging ADR-0015 and SPEC-0015 authorizes TASK-0080 only. It does not authorize a
real provider, public administration, automatic head selection, source ranking,
staleness threshold, aggregation, reconciliation, business stock, mutation,
assessment, event, worker, scheduler, or external request.
