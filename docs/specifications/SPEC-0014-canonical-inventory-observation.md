# SPEC-0014: Canonical Inventory Observation Projection

**Status:** Proposed

**Date:** 2026-08-12

**Source decision:** ADR-0014

## Objective

Project one immutable source-ledger balance through one exact active mapping into
an immutable, organization-scoped canonical inventory observation without
rounding, aggregation, measure selection, or inventory mutation.

## Authorized next implementation

Acceptance authorizes TASK-0077 only:

1. add a pure `applications:inventory-canonical-observation` Kotlin module;
2. define canonical observation IDs, exact rational quantities, provenance,
   measures, outcomes, repository, service, and history contracts;
3. add additive PostgreSQL migration `V008` for immutable observation revisions;
4. implement a transactional projector in the existing schema-owning
   persistence module;
5. prove exact conversion, evidence and mapping agreement, idempotency,
   reprojection, concurrency, lifecycle fencing, isolation, and privacy;
6. leave production startup, HTTP routes, source ingestion, mappings, existing
   business inventory, assessments, events, and external traffic unchanged.

No provider adapter, credential use, API call, automatic match, bulk scan,
scheduler, worker, current-state table, aggregation, reconciliation, measure
selection, rounding, display conversion, stock command, assessment, event,
outbox message, or public endpoint is authorized.

## Module boundary

The pure module may depend only on:

```text
platform:foundation:organization-context
applications:integration-control-plane
applications:inventory-source-ingestion
applications:inventory-identity-mapping
```

It has no Kernel, Marketplace Operations, database, HTTP, serialization,
framework, provider, scheduler, filesystem, environment, or cryptography access.

## Canonical values

The module exports:

```text
CanonicalInventoryObservationId
CanonicalInventoryObservationCorrelationId
ExactInventoryQuantity
CanonicalInventoryMeasures
CanonicalInventoryObservation
CanonicalInventorySourcePointer
CanonicalInventoryProjectionResult
```

Observation and correlation IDs wrap UUIDs. Parsing accepts only canonical
lowercase UUID text. Their `toString` is `[INTERNAL]`; explicit persistence
accessors are available only to adapters.

## Exact rational quantity

```text
ExactInventoryQuantity(
  numerator: BigInteger,
  denominator: Long
)
```

Invariants:

- denominator is in `1..1_000_000_000_000_000`;
- numerator magnitude is less than `10^34`;
- numerator and denominator are reduced by greatest common divisor;
- denominator is always positive;
- every zero becomes exactly `0/1`;
- equality and persistence use the reduced pair;
- text rendering is `[REDACTED]`.

Construction from a `SourceQuantity` and `QuantityFactor` is deterministic:

```text
source = unscaled / 10^scale
factor = factorNumerator / factorDenominator
result = reduce(
  unscaled * factorNumerator,
  10^scale * factorDenominator
)
```

The source contract guarantees scale `0..6`, magnitude below `10^18`, and
factor components up to `10^9`; therefore an individual projected numerator is
strictly below `10^33` before reduction and the raw denominator is at most
`10^15`.

Implementation uses `BigInteger` multiplication and GCD. It must not call
`BigDecimal.divide`, `Double`, `Float`, `MathContext`, `setScale`, `round`,
`toInt`, `toLong`, or any clamping operation.

## Measures

```text
CanonicalInventoryMeasures(
  availableToSell: ExactInventoryQuantity?,
  onHand: ExactInventoryQuantity?,
  reserved: ExactInventoryQuantity?,
  pendingInbound: ExactInventoryQuantity?,
  pendingOutbound: ExactInventoryQuantity?
)
```

At least one measure is present. Each source measure is converted independently
using the same mapping factor. Null remains null; present zero becomes `0/1`;
negative sign is retained in the numerator.

The projector never computes or asserts an equation among measures. In
particular it does not assume:

```text
available = onHand - reserved + pendingInbound - pendingOutbound
```

Measure names preserve source-declared semantics; they do not state that a
measure is authoritative for selling, purchasing, accounting, fulfillment, or
the existing risk evaluator.

## Source pointer

```text
CanonicalInventorySourcePointer(
  connectionId,
  capability = inventory.source-balance.read,
  inputProgressVersion,
  recordOrdinal
)
```

Organization is trusted service authority and is not accepted inside the
pointer. Version is non-negative and ordinal is `0..999`. The pointer must
identify exactly one V006 source row under the authorized organization.

The source row supplies the exact selector:

```text
connection
capability
source item reference
source location presence and value
source unit presence and value
```

Source SKU, source version, source timestamp, and quantities are not mapping
keys. No wildcard, fallback, name, SKU, GTIN, prefix, fuzzy, or cross-connection
match is allowed.

## Canonical observation

```text
CanonicalInventoryObservation(
  id,
  organizationId,
  sourcePointer,
  projectionRevision,
  mappingDecisionId,
  mappingRevision,
  target: InventoryMappingTarget,
  measures,
  sourceUpdatedAt?,
  sourceCommittedAt,
  projectedAt,
  correlationId,
  supersedesObservationId?
)
```

Invariants:

- projection revision starts at 1;
- revision 1 has no predecessor;
- later revisions cite the immediately previous observation revision;
- a later revision uses a strictly greater mapping revision;
- target identities and its quantity factor exactly equal the cited mapping decision;
- source pointer exactly supplies that mapping decision's selector;
- source-updated time equals the nullable V006 source value;
- source-commit time equals its V006 page commit time;
- projection time is PostgreSQL transaction time truncated to microseconds;
- projection time is not ordered against a nullable provider timestamp;
- all converted measures exactly reproduce source times factor.

`toString` is `CanonicalInventoryObservation([REDACTED])` and must not expose
quantity, identity, evidence, mapping, organization, connection, correlation,
or time values.

## Projection operation

The service supports:

```text
project(organization authority, exact source pointer, correlation ID?)
read observation by scoped ID
read projection history for exact scoped source pointer
```

Projection executes one transaction in this order:

1. require active organization;
2. load and lock the exact V006 source row and its V006 page commit;
3. require its same-organization connection to be `ACTIVE` or `SUSPENDED`;
4. construct its exact selector;
5. load and lock exactly one active V007 mapping for that selector;
6. require all target identities active and same-organization;
7. validate source pointer, selector, target, factor, decision, and revision;
8. convert each present quantity with the pure exact algorithm;
9. inspect prior projection history under the same source pointer;
10. return exact replay when the same mapping decision and all content agree;
11. otherwise append the next immutable observation revision;
12. commit or roll back all effects.

No credential is opened and no adapter, protector, provider, network, filesystem,
assessment, event, or existing inventory service is called.

## Controlled results

```text
Projected(observationId, projectionRevision)
AlreadyProjected(observationId, projectionRevision)
Unmapped
SourceUnavailable
TargetUnavailable
Conflict
IntegrityFailure
```

Internal IDs remain redacted values. No result contains source text, quantities,
factor, mapping principal, mapping reason, organization, connection, evidence
ordinal, provider key, or credential information.

`Unmapped` does not distinguish missing, retired, foreign, or ambiguous mapping.
`SourceUnavailable` does not distinguish missing from foreign source or
connection. Impossible duplicate-active or divergent replay states return
`IntegrityFailure`. Expected concurrent replacement or revision movement returns
`Conflict` without internal detail.

## Idempotency and reprojection

One source pointer and mapping decision permit at most one observation. Repeating
that projection returns `AlreadyProjected` only if observation ID-independent
content agrees exactly.

A later active mapping decision may project the same source evidence again. It
appends `projectionRevision + 1`, cites the prior observation, and requires its
mapping revision to be greater than the prior projected mapping revision.

The observation ledger has no mutable active flag. A later contract must decide
which projection revision is accepted for aggregation or current state. Reading
history always orders by projection revision, never wall-clock tie breaking.

No automatic bulk reprojection is part of TASK-0077.

## PostgreSQL migration V008

`integration_inventory_canonical_observation` contains:

```text
organization_id uuid
observation_id uuid
connection_id uuid
capability text
input_progress_version bigint
record_ordinal integer
projection_revision integer
mapping_decision_id uuid
mapping_revision integer
target_item_id uuid
target_location_id uuid nullable
target_unit_id uuid
factor_numerator bigint
factor_denominator bigint
available_to_sell_numerator numeric(40,0) nullable
available_to_sell_denominator bigint nullable
on_hand_numerator numeric(40,0) nullable
on_hand_denominator bigint nullable
reserved_numerator numeric(40,0) nullable
reserved_denominator bigint nullable
pending_inbound_numerator numeric(40,0) nullable
pending_inbound_denominator bigint nullable
pending_outbound_numerator numeric(40,0) nullable
pending_outbound_denominator bigint nullable
source_updated_at timestamptz nullable
source_committed_at timestamptz
projected_at timestamptz
correlation_id uuid
supersedes_observation_id uuid nullable
```

Required keys and constraints:

- primary key `(organization_id, observation_id)`;
- same-organization foreign keys to connection, V006 source record, V007 mapping
  decision, item, optional location, and unit;
- unique source pointer plus mapping decision;
- unique source pointer plus projection revision;
- self foreign key for same-organization predecessor observation;
- capability fixed to `inventory.source-balance.read`;
- non-negative source version, ordinal `0..999`, positive revisions;
- factor component bounds and reduced form;
- target location presence equal to source location presence;
- numerator/denominator null pairs agree for every measure;
- at least one measure pair is present;
- each present denominator is positive and at most `10^15`;
- each present rational pair is reduced, with zero only as `0/1`;
- initial and superseding projection shapes agree with revision;
- timestamps have PostgreSQL-supported microsecond precision.

A constraint trigger validates at commit that:

- organization and connection lifecycle are eligible;
- source and page commit exist under the exact pointer;
- mapping is active and exactly selects the source row;
- copied mapping revision, target, and factor equal the mapping decision;
- target identities are active at insertion;
- each rational measure equals deterministic conversion of the V006 decimal;
- copied source times equal V006 evidence;
- predecessor has the same source pointer and immediately previous projection
  revision;
- a reprojection uses a greater mapping revision.

Triggers reject every update and delete. No source identifier, SKU, source unit,
source version, provider payload, raw JSON, credential, principal, reason,
Genesis `SkuRef`, assessment, event, or destination is stored by V008.

## Concurrency and atomicity

- projection locks the exact V006 source row before reading mapping and history;
- two first projections under one mapping produce one row and one replay;
- a mapping replacement racing projection yields either a complete old-decision
  observation or a controlled retry/conflict, never mixed provenance;
- concurrent reprojections allocate one next projection revision;
- unique violations are classified only after re-reading exact scoped content;
- a failed conversion, validation, constraint, trigger, or commit inserts no row;
- no application exception exposes SQL detail or stored values.

## Reads and lifecycle

Reads require trusted organization authority and exact scoped identifiers. They
do not require an active connection, mapping, or identity because historical
evidence remains reproducible after retirement.

Reads never search by source text, provider, SKU, target name, or quantity.
TASK-0077 exposes no public route and no general target-current query.

## Privacy and observability

Allowed telemetry dimensions are operation, controlled result, and duration.
Aggregate counts may omit all record dimensions.

Forbidden in logs, exception messages, metrics, spans, and public outcomes:

- source item, location, SKU, unit, and version;
- all source and canonical quantities;
- factor components;
- observation, mapping, target, correlation, organization, and connection IDs;
- source pointer and timestamps;
- mapping principal and reason;
- database error text and injected markers.

Repository and trigger failures become controlled redacted results. Tests use
distinct markers and assert their absence from every returned diagnostic.

## Test plan

1. the module has only its four authorized project dependencies;
2. IDs parse canonical lowercase UUIDs and redact text;
3. exact quantity rejects invalid bounds and reduces sign and GCD correctly;
4. zero canonicalizes to `0/1`;
5. `1 * 1/3` remains exactly `1/3` without decimal division;
6. maximum accepted source and factor values stay within declared bounds;
7. negative, decimal, zero, and null measures reproduce independently;
8. no relation among measures is derived or asserted;
9. V008 applies after V001 through V007;
10. exact source evidence and page commit are required;
11. foreign source and connection are indistinguishable from unavailable;
12. draft and revoked connections cannot project;
13. active and suspended connections can project without credential access;
14. absent or retired mapping yields controlled unmapped;
15. retired or foreign targets cannot receive a new observation;
16. mapping selector, target, factor, and revision must agree exactly;
17. null source location is exact and never a wildcard;
18. source timestamp remains null when absent;
19. source commit and projection clocks remain distinct;
20. first projection appends revision 1 with no predecessor;
21. identical replay appends nothing and returns already projected;
22. divergent decision replay fails integrity checks;
23. two concurrent first projections persist one observation;
24. mapping replacement permits an immutable revision 2 reprojection;
25. old and new interpretations remain ordered and reproducible;
26. stale or non-incremental predecessor state cannot append;
27. forced trigger failure rolls back the complete projection;
28. direct SQL update and delete are rejected;
29. history remains readable after mapping, target, or connection retirement;
30. source, quantity, identity, principal, and injected markers leak nowhere;
31. production startup and public OpenAPI remain unchanged;
32. no source contains provider code, HTTP, OAuth, automatic mapping, bulk scan,
    scheduler, worker, aggregation, snapshot, command, assessment, or event;
33. V006 and V007 rows remain unchanged by projection;
34. the complete repository build and persistent runtime package remain green.

## Current provider and storage constraints

- Mercado Livre manages independent stock per location and distinguishes
  fulfillment, selling-address, and seller-warehouse ownership;
- Mercado Livre uses `x-version` for stock concurrency, not canonical identity;
- multi-origin accounts use stock locations rather than one item-level quantity;
- Omie exposes decimal physical, reserved, pending, and available measures per
  product and stock location;
- Omie identifies stock locations independently and assigns operational flags;
- PostgreSQL fixed-scale `numeric(p,s)` rounds values beyond the declared scale;
- PostgreSQL floating-point types are inexact;
- PostgreSQL arbitrary-precision `numeric` and integer GCD support exact
  validation of stored rational components.

## References

- Mercado Livre distributed inventory:
  https://developers.mercadolivre.com.br/pt_br/lojas-oficiais/estoque-distribuido
- Mercado Livre multi-origin inventory:
  https://developers.mercadolivre.com.br/pt_br/produto-consulta-de-usuarios/estoque-multi-origem
- Omie inventory summary API:
  https://app.omie.com.br/api/v1/estoque/resumo/
- Omie inventory query API:
  https://app.omie.com.br/api/v1/estoque/consulta/
- Omie inventory location API:
  https://app.omie.com.br/api/v1/estoque/local/
- PostgreSQL numeric types:
  https://www.postgresql.org/docs/current/datatype-numeric.html
- PostgreSQL mathematical functions:
  https://www.postgresql.org/docs/current/functions-math.html

## Remaining boundary

Provider adapters, progress protection, sync scheduling, bulk projection,
mapping administration, canonical current-state selection, source-authority and
staleness policy, location and channel aggregation, reconciliation, rounding,
display units, business availability, conversion to the current
`InventorySnapshot` or `InventoryRiskInput`, inventory mutation, assessments,
events, and outbound stock writes require later accepted specifications.

## Acceptance

Merging ADR-0014 and SPEC-0014 authorizes TASK-0077 only. It does not authorize
a real provider, public API, automatic mapping, worker, scheduler, aggregation,
current-state winner, rounding, business inventory mutation, assessment, event,
or external request.
