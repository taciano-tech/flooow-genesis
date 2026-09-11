# ADR-0013: Inventory Identity Mapping Boundary

Status: Proposed

Date: 2026-08-12

## Context

TASK-0073 introduced an immutable, organization-scoped ledger that preserves
source item, location, SKU, unit, timestamp, version, and separate inventory
measures. Those values are evidence from one integration connection. They are
not Genesis product, warehouse, or unit identities.

The existing Marketplace Operations `SkuRef` and `InventorySnapshot` are MVP
business types. `SkuRef` wraps an unscoped Kernel string identifier, while a
snapshot assumes one whole, non-negative `availableUnits` value. Reusing those
types as an integration catalog would silently choose company ownership,
location aggregation, unit conversion, decimal handling, and availability
semantics.

Provider models reinforce this distinction. Mercado Livre can associate one
User Product with multiple independently managed stock locations, and a
location may be seller-managed or Mercado Livre-managed. User Product, item,
inventory, store, and network-node identifiers have different roles. Omie
separates product, local de estoque, unit, physical, reserved, available, and
forecast quantities. Textual equality between any of those values is not proof
that they describe the same business subject.

Before a real adapter or automatic assessment can be accepted, Genesis needs a
small boundary where an organization deliberately states which source tuple is
the same item, location, and unit in its own canonical inventory namespace.

## Decision

Introduce a production-inactive Inventory Identity Registry and an auditable,
exact Source Mapping Registry.

The identity registry creates stable organization-owned anchors only:

```text
InventoryItemId
InventoryLocationId
InventoryUnitId
```

These UUID identities do not contain a name, source SKU, GTIN, marketplace ID,
ERP code, description, or business policy. They are not added to the Kernel and
do not replace the current MVP `SkuRef` in TASK-0075.

The mapping registry binds one exact selector:

```text
organization
connection
inventory.source-balance.read
source item reference
source location presence and value
source unit presence and value
```

to one canonical target:

```text
inventory item
optional inventory location
inventory unit
positive exact quantity factor
```

The factor is a reduced positive rational `numerator / denominator`. It records
an explicit statement such as `1 source box = 12 canonical each`. TASK-0075
stores and reproduces that decision but does not apply it to source quantities.

Every initial or replacement mapping cites one immutable source-ledger record as
evidence. The cited record must belong to the same organization, connection, and
capability and must have the exact selector values. A mapping cannot be created
from a guessed SKU, a provider response that was not committed, or another
organization's evidence.

```text
source ledger evidence
  -> exact reviewed selector
  -> immutable mapping revision
  -> canonical identity anchors
  -> later mapped observation contract
  -> later aggregation and business inventory policy
```

## Exact matching

Resolution uses case-sensitive exact source values and exact null presence. It
has no wildcard, prefix, regular expression, fallback location, default unit,
name similarity, SKU equality, GTIN heuristic, cross-connection reuse, or
provider-specific branch.

An omitted source location is not the same as every location. An omitted source
unit may be mapped only through a deliberate mapping revision that declares the
canonical unit and factor; the resolver never invents `EA`, `UN`, or `1:1`.

A source location, when present, must map to a canonical location. When it is
absent the target location is also absent. Aggregating an unspecified location
into a known warehouse remains a later policy decision.

## Revision and authority

Mapping decision content is immutable. One exact selector has at most one active
revision. Replacement atomically changes only the current revision's lifecycle
state, appends its retirement audit, and appends the next revision using
compare-and-set fencing. Retirement never deletes or rewrites selector, target,
evidence, authority, reason, or history.

Each decision records a UUID decision ID, revision, trusted principal reference,
controlled reason code, correlation ID, decision time, evidence pointer, and
optional superseded decision. Principal authority is supplied by an internal
trusted application boundary; request bodies and source records cannot choose
it. TASK-0075 uses deterministic principals in tests and exposes no public
administration route.

Canonical identities can be retired but not deleted or reassigned to another
organization. A retired identity remains valid for historical mapping reads but
cannot receive a new active mapping.

## Resolution result

Resolution returns one of these controlled results:

```text
RESOLVED
UNMAPPED
AMBIGUOUS_INTEGRITY_FAILURE
```

`RESOLVED` returns canonical IDs, the exact factor, mapping decision ID, and
revision. It returns no source text. `UNMAPPED` reveals no cross-organization or
cross-connection existence. Multiple active matches are a database-integrity
failure, never an arbitrary first match.

Resolution does not choose one of the five source measures, multiply a quantity,
round a decimal, clamp negative stock, aggregate locations, create an
`InventorySnapshot`, alter inventory, run an assessment, or emit an event.

## Consequences

### Positive

- canonical identity is organization-owned instead of inferred from provider text;
- mappings are evidence-backed, reviewable, reversible, and historically reproducible;
- exact connection scope supports the same source identifier in different accounts;
- locations and units cannot disappear through convenient defaults;
- replacement races cannot create two active meanings;
- future provider adapters remain independent from Genesis catalog identity;
- later aggregation can cite the exact mapping revision it used.

### Negative

- data remains staged and cannot yet drive the MVP assessment automatically;
- organizations must review mappings before automation can consume a source;
- canonical identities initially have no friendly catalog metadata;
- unit factors are stored before a later observation contract applies them;
- source identifiers remain retained in mapping history and need privacy governance;
- human identity, roles, and an administration interface remain unresolved.

## Alternatives considered

### Treat source SKU as `SkuRef`

Rejected because SKU uniqueness is organization- and system-specific, values can
change, and the current `SkuRef` does not carry organization or connection scope.

### Match automatically by SKU, GTIN, title, or description

Rejected because equality and similarity are useful review suggestions, not
identity proof. False positives would combine unrelated stock.

### Share mappings globally by provider

Rejected because source identifiers and warehouse codes are account-scoped and
different organizations may deliberately model the same provider data differently.

### Put provider identifiers in canonical identities

Rejected because it would make Genesis identity depend on one provider and
complicate migration, multi-channel association, and provider retirement.

### Convert and aggregate quantities during mapping

Rejected because identity, unit conversion, measure selection, aggregation,
rounding, and negative-stock policy are separate decisions with different evidence.

### Edit one mutable mapping row

Rejected because past observations could no longer reproduce which mapping was
effective, and concurrent corrections could silently overwrite each other.

## Authorization

This ADR alone authorizes no implementation. SPEC-0013 freezes TASK-0075's pure
identity and mapping contracts, additive V007 registry, immutable revisions,
exact resolver, deterministic tests, and production-inactive wiring. It
authorizes no provider adapter, public mapping API, automatic match, quantity
conversion, canonical inventory observation, aggregation, inventory mutation,
assessment, or external request.
