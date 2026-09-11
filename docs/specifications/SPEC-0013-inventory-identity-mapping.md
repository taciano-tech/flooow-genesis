# SPEC-0013: Inventory Identity Mapping

**Status:** Proposed

**Date:** 2026-08-12

**Source decision:** ADR-0013

## Objective

Create organization-owned inventory identity anchors and persist immutable,
evidence-backed mappings from an exact source-ledger selector to those anchors,
without interpreting or mutating inventory.

## Authorized next implementation

Acceptance authorizes TASK-0075 only:

1. add a pure `applications:inventory-identity-mapping` Kotlin module;
2. define canonical UUID item, location, unit, mapping, and correlation values;
3. define exact source selector, canonical target, rational factor, decision,
   lifecycle, repository, service, and resolver contracts;
4. add additive PostgreSQL migration `V007` for canonical identity anchors and
   immutable mapping revisions;
5. implement the repository in the existing schema-owning persistence module;
6. prove evidence agreement, exact resolution, lifecycle fencing, isolation,
   history, and privacy with deterministic tests;
7. leave production startup, HTTP routes, source ingestion, existing business
   inventory, assessments, events, and external traffic unchanged.

No provider adapter, OAuth flow, API call, automatic match, fuzzy suggestion,
public catalog or mapping endpoint, product metadata, name, GTIN, canonical SKU,
quantity conversion execution, source-measure selection, aggregation, rounding,
clamping, inventory snapshot, command, assessment, event, worker, or scheduler is
authorized.

## Module boundary

The pure module may depend only on:

```text
platform:foundation:organization-context
applications:integration-control-plane
applications:inventory-source-ingestion
```

It has no Kernel, Marketplace Operations, database, HTTP, serialization,
framework, provider, scheduler, filesystem, environment, or cryptography access.

## Canonical identities

The module exports UUID-backed values:

```text
InventoryItemId
InventoryLocationId
InventoryUnitId
InventoryMappingDecisionId
InventoryMappingCorrelationId
```

Parsing accepts only canonical lowercase UUID text. `toString` for item,
location, unit, decision, and correlation values is `[INTERNAL]`; explicit
persistence accessors expose UUID values only to repository adapters.

An identity anchor contains organization, identity UUID, state, creation time,
and optional retirement time. States are `ACTIVE` and `RETIRED`. Creation is
allowed only for an active organization. Retirement is one-way, idempotent only
for the same expected state, and preserves all referencing mappings.

TASK-0075 adds no catalog code, display name, description, source identifier,
unit symbol, conversion family, or cross-organization identity.

## Exact source selector

```text
InventorySourceSelector(
  connectionId,
  capability = inventory.source-balance.read,
  sourceItemReference,
  sourceLocationReference?,
  sourceUnitCode?
)
```

Organization is service authority and is never accepted inside the selector.
Provider key is obtained from the same-organization connection and is not
duplicated. Capability is fixed to the one accepted inventory source capability.

The selector uses the existing opaque source value types unchanged. Null is an
exact value. No normalization beyond those types and no match by source SKU or
source version is allowed.

Source SKU and version are observation metadata and may change independently.
They cannot select a mapping. Source timestamp and quantities are never mapping
keys.

## Canonical target and quantity factor

```text
InventoryMappingTarget(
  itemId,
  locationId?,
  unitId,
  quantityFactor
)

QuantityFactor(numerator, denominator)
canonicalQuantity = sourceQuantity * numerator / denominator
```

Numerator and denominator are positive integers in `1..1_000_000_000`, reduced
by greatest common divisor at construction. Equality and persistence use the
reduced form. No floating point value exists.

When the selector has a source location, target location is required. When it
has none, target location must be null. The target item, unit, and any target
location must be active and belong to the authorized organization when a mapping
is activated or replaced.

The factor is metadata in TASK-0075. No source quantity is multiplied, divided,
rounded, truncated, clamped, or converted.

## Ledger evidence

```text
InventoryMappingEvidence(
  connectionId,
  capability,
  inputProgressVersion,
  recordOrdinal
)
```

Version is non-negative and ordinal is `0..999`. The referenced
`integration_inventory_source_balance` row must exist under the same organization,
connection, and capability. Its item, location, and unit values must equal the
selector exactly, including null presence.

Source SKU, source version, timestamps, and quantities need not equal any prior
mapping revision because they do not participate in identity selection.

## Mapping decision

```text
InventoryMappingDecision(
  id,
  organizationId,
  selector,
  target,
  evidence,
  revision,
  state,
  principalReference,
  reason,
  correlationId,
  decidedAt,
  retiredAt?,
  supersedesDecisionId?
)
```

Revision starts at 1. A replacement increments by exactly one and cites the
active decision it supersedes. A retirement retains the same immutable decision
row and appends a retirement audit entry; it does not rewrite target, selector,
evidence, principal, reason, or decision time.

`InventoryMappingPrincipalReference` is NFC-normalized, already trimmed, contains
no ISO control character, and is 1..128 UTF-8 bytes. It originates only from a
trusted internal authority. It is forbidden in logs, metrics, spans, public
outcomes, and exception messages.

Controlled reasons are:

```text
INITIAL_ASSIGNMENT
IDENTITY_CORRECTION
LOCATION_CORRECTION
UNIT_CORRECTION
CATALOG_REPLACEMENT
SOURCE_MODEL_CHANGE
```

Free-text reasons, source values, and provider payloads are not stored in audit
details.

## Lifecycle operations

The service supports:

```text
create item/location/unit identity
retire identity with expected ACTIVE state
activate initial mapping for an unmapped selector
replace active mapping with expected decision ID and revision
retire active mapping with expected decision ID and revision
resolve exact selector
read decision history for exact selector
```

Every write is one PostgreSQL transaction and revalidates organization,
connection, identity, evidence, and expected revision inside that transaction.

Mapping administration requires an active organization and a same-organization
connection that is `ACTIVE` or `SUSPENDED`. Suspension may deliberately pause
sync while an operator corrects mappings. It does not open credentials or
require external network access. A draft, revoked, unknown, or foreign
connection cannot receive a new mapping.
Historical reads remain available for authorized organization scope even after a
connection or identity is retired.

## Resolution

Resolution receives trusted organization authority plus an exact selector.

```text
Resolved(target, quantityFactor, decisionId, revision)
Unmapped
IntegrityFailure
```

Only an active mapping with active target identities resolves. A retired mapping
or retired target yields `Unmapped` for future processing while remaining in
history. Any impossible duplicate-active state yields `IntegrityFailure`.

The result contains no source item, location, SKU, unit, quantities, principal,
reason, evidence ordinal, organization, connection, or provider value. It cannot
be directly converted to `SkuRef`, `InventorySnapshot`, or `InventoryRiskInput`.

## PostgreSQL migration V007

`inventory_item_identity`, `inventory_location_identity`, and
`inventory_unit_identity` contain:

```text
organization_id uuid
identity_id uuid
state text
created_at timestamptz
retired_at timestamptz nullable
```

Each primary key is `(organization_id, identity_id)` and each organization is a
foreign key to `integration_organization`. Checks enforce active/retired time
shape.

`integration_inventory_source_mapping` contains the complete immutable decision:

```text
organization_id uuid
connection_id uuid
capability text
source_item_ref text
source_location_ref text nullable
source_unit_code text nullable
decision_id uuid
revision integer
state text
target_item_id uuid
target_location_id uuid nullable
target_unit_id uuid
factor_numerator bigint
factor_denominator bigint
evidence_progress_version bigint
evidence_record_ordinal integer
principal_ref text
reason text
correlation_id uuid
decided_at timestamptz
retired_at timestamptz nullable
supersedes_decision_id uuid nullable
```

Composite foreign keys enforce same-organization connection, evidence, item,
location, and unit ownership. Checks mirror source UTF-8 limits, capability,
factor, location-presence, revision, lifecycle, and initial/superseding shapes.

Decision ID is unique within the organization. One partial unique index permits
at most one active row for an exact selector, using PostgreSQL
`NULLS NOT DISTINCT` semantics. Another nulls-not-distinct unique index permits
one revision per exact selector. A deferred constraint trigger requires a
superseded decision to belong to the same organization, connection, capability,
exact nullable selector, and immediately previous revision. A digest may be used
only as an index accelerator; exact stored values must still agree and a digest
collision fails closed.

`integration_inventory_source_mapping_retirement` is append-only and contains
decision scope, trusted principal, controlled reason, correlation ID, and time.
It has one row per retired decision and no free text.

No product name, title, description, source SKU, source version, quantity,
provider payload, raw JSON, credential, secret reference, Genesis `SkuRef`,
assessment, event, or destination is stored by V007.

## Concurrency and atomicity

- two initial activations for one selector accept exactly one revision 1;
- replay of the same decision ID returns `ALREADY_APPLIED` only when every
  stored decision field agrees; disagreement is an integrity failure;
- replace uses expected decision ID and revision and cannot lose an update;
- replacement retires old, appends retirement audit, and activates new in one transaction;
- retirement is fenced by expected decision ID and revision;
- identity retirement and mapping activation lock the relevant identity rows;
- evidence and connection lifecycle are rechecked after locks;
- a failed validation, constraint, audit insert, or commit changes nothing;
- history ordering is by revision, not wall-clock tie breaking.

## Privacy and observability

Allowed telemetry is operation, controlled result, reason enum, and duration.
Counts may be recorded without selector dimensions.

Source values, target UUIDs, mapping UUIDs, principal, correlation, organization,
connection, evidence pointer, and factor are forbidden metric labels and trace
attributes. They are also forbidden in exception messages and public outcomes.
Repository and constraint failures become controlled redacted errors.

## Test plan

1. the module has only its three accepted project dependencies;
2. canonical UUID parsing rejects noncanonical text and redacts output;
3. principal normalization, controls, whitespace, and UTF-8 limits reproduce;
4. rational factors reject zero/negative/oversized values and reduce exactly;
5. source selector retains exact case and null presence;
6. source SKU, source version, timestamp, and quantities cannot enter a selector;
7. location presence agreement is enforced;
8. V007 applies after V001 through V006;
9. identity rows are organization-scoped and lifecycle constrained;
10. foreign or retired target identities cannot receive a mapping;
11. evidence must exist and selector values must agree exactly;
12. foreign connection or evidence is indistinguishable from unavailable;
13. source identifiers may be reused across organizations and connections;
14. one exact selector permits only one active mapping;
15. two concurrent initial activations accept exactly one revision;
16. identical replay is controlled and does not append another decision;
17. replacement increments exactly one revision and links its predecessor;
18. stale replacement cannot retire or overwrite the active decision;
19. replacement writes decision and retirement audit atomically;
20. retirement removes future resolution but preserves history;
21. retired identity prevents future resolution while history remains reproducible;
22. null location and null unit never act as wildcards;
23. exact resolution returns target, factor, decision ID, and revision only;
24. unmapped resolution reveals no foreign existence;
25. injected database failure rolls back identities, decisions, and audit effects;
26. source and internal markers appear in no diagnostics or telemetry;
27. production startup and public OpenAPI remain unchanged;
28. no source contains automatic matching, quantity application, provider code,
    HTTP, OAuth, scheduler, worker, assessment, event, or inventory mutation;
29. existing source ledger rows and V001 through V006 behavior remain unchanged;
30. the complete repository build remains green.

## Provider research constraints

- Mercado Livre distinguishes User Product, item, inventory, store, network node,
  and stock-location type; none is interchangeable by string convention;
- one User Product can have multiple stock locations with independent quantities;
- `meli_facility`, `selling_address`, and `seller_warehouse` have different
  ownership and API editability;
- the Mercado Livre `x-version` header is concurrency state, not product identity;
- Omie exposes physical, reserved, and available stock separately by product and
  stock location;
- Omie distinguishes physical stock from availability that considers expected
  inbound and outbound movements;
- Omie units and kit/component quantities make unit conversion item-specific;
- operational source stock can be negative, so mapping cannot rewrite quantity.

## References

- Mercado Livre distributed inventory:
  https://developers.mercadolivre.com.br/pt_br/lojas-oficiais/estoque-distribuido
- Mercado Livre multi-origin inventory:
  https://developers.mercadolivre.com.br/pt_br/produto-consulta-de-usuarios/estoque-multi-origem
- Mercado Livre User Products and variation model:
  https://developers.mercadolivre.com.br/pt_br/guia-para-produtos/preco-variacao
- Omie inventory analysis:
  https://ajuda.omie.com.br/pt-BR/articles/1592576-analisando-o-estoque-dos-produtos
- Omie physical versus available stock:
  https://ajuda.omie.com.br/pt-BR/articles/10156625-configurando-o-omie-pre-venda
- Omie negative stock:
  https://ajuda.omie.com.br/pt-BR/articles/12540308-entendendo-o-estoque-negativo-e-seus-impactos-nos-registros-contabeis

## Remaining boundary

Friendly catalog metadata, human users and roles, mapping UI/API, automatic
candidate suggestions, provider-specific record adapters, production progress
encryption, sync scheduling, application of unit factors, measure selection,
canonical observations, aggregation, reconciliation, retention, business
inventory mutation, assessment triggering, and outbound stock writes require
later accepted specifications.

## Acceptance

Merging ADR-0013 and SPEC-0013 authorizes TASK-0075 only. It does not authorize
a real provider, automatic mapping, public administration, quantity conversion,
canonical inventory state, inventory mutation, assessment, event, worker,
scheduler, or external request.
