# ADR-0012: Inventory Source Ledger Boundary

Status: Proposed

Date: 2026-08-12

## Context

TASK-0071 introduced a provider-neutral runtime that can read and atomically
commit one typed connector page, but its progress and committers are currently
in-memory test fakes. A real marketplace or ERP adapter still has nowhere safe
to preserve source inventory records or resume after a restart.

The existing Marketplace Operations `InventorySnapshot` and
`InventoryRiskInput` are not ingestion schemas. They are business projections:
they use a Genesis SKU, whole non-negative units, a chosen effective date, sales
velocity, goals, and replenishment assumptions. Passing provider responses into
those types would silently make mapping and semantic decisions that have not
been accepted.

The intended providers expose materially different inventory models. Mercado
Livre can separate stock by User Product, stock location, and warehouse type,
including seller-managed and marketplace-managed inventory. Omie distinguishes
physical, reserved, pending, and balance values by product and stock location,
and its operational model can contain negative balances. Their identifiers,
locations, quantities, and units are not interchangeable.

Connector progress can also contain an offset, page number, cursor, scroll
token, compound position, or future provider-sensitive material. Storing its
plaintext in PostgreSQL would weaken the secret-safe runtime boundary.

## Decision

Introduce a production-inactive Inventory Source Ledger for one explicit
capability, `inventory.source-balance.read`. It is an immutable staging boundary
between provider adapters and future product/stock mapping.

The ledger preserves what a source stated without claiming that:

- a source item is a Genesis product or SKU;
- two source locations are the same warehouse;
- one provider's balance has another provider's availability semantics;
- a provider unit is a canonical Genesis unit of measure;
- negative source stock is invalid or should be clamped;
- a source record is authorized to mutate operational inventory.

```text
provider adapter
  -> typed source-balance page
  -> atomic source ledger + sealed progress
  -> later explicit identity/measure/unit mapping
  -> later canonical inventory observation
  -> later business assessment or authorized command
```

No existing `InventorySnapshot`, `InventoryRiskInput`, assessment journal,
CloudEvent, outbox, or Kernel type is changed by the first ledger implementation.

## Source-balance record

One record identifies a source item and optional source location and SKU. It can
carry any proven subset of these separate decimal measures:

```text
availableToSell
onHand
reserved
pendingInbound
pendingOutbound
```

At least one measure is required. A missing measure means unknown, not zero.
Values are signed fixed decimals so the ledger can preserve negative ERP data.
Adapters may populate a field only when the provider documentation and endpoint
semantics support that interpretation. They must not derive `availableToSell`
by subtracting other fields unless a later provider specification explicitly
accepts that formula.

The provider's unit code and source-updated timestamp are optional and preserved
as source metadata. The connector page observation time records when Genesis
retrieved the page. Provider identifiers and source SKUs are data, not metric
labels, logging fields, or Genesis identity.

## Durable progress

Progress is stored in the same PostgreSQL transaction that appends a page's
source records. PostgreSQL stores only a sealed envelope, never plaintext
progress.

A transport-neutral `ConnectorProgressProtector` port seals and opens progress
using context that binds organization, connection, capability, and progress
version. A production implementation must use authenticated encryption with a
fresh nonce and externally managed keys. TASK-0073 supplies only a deterministic
test protector and does not activate production ingestion.

Sealing rather than storing a vault reference keeps record insertion and
checkpoint advancement within one database transaction and avoids an orphaned
external secret when a transaction rolls back. Production key selection,
rotation, re-encryption, and disaster recovery still require deployment review.

## Atomicity and idempotency

For progress version `n`, the capability committer:

1. validates the active same-organization connection and typed records;
2. seals the next progress for version `n + 1`, when present;
3. starts one database transaction;
4. inserts one immutable page-commit row keyed by the runtime commit key;
5. inserts source records in their stable page order;
6. compare-and-set advances progress from `n` to `n + 1` or marks it exhausted;
7. commits all three effects together.

The same page key returns `ALREADY_COMMITTED` without inserting or advancing
again. A different page racing on a stale version returns `STALE_PROGRESS`.
Provider reads may be duplicated, but accepted records and progress cannot be.

## Identity boundary

The ledger uses connection-scoped `SourceItemReference`, optional
`SourceLocationReference`, and optional `SourceSku`. These values may help a
future mapping workflow but never become a `SkuRef` or API request SKU by
convention. Product identity needs an explicit, auditable mapping owned by the
organization.

The page ordinal plus page commit identifies one immutable source observation.
It is not a global product identity. Repeated snapshots over time are retained
as separate observations after progress is intentionally reset by a future sync
run contract.

## Consequences

### Positive

- provider data can be preserved without contaminating current business types;
- differences in locations and balance semantics remain visible;
- decimal and negative ERP quantities are not silently truncated or clamped;
- records and progress commit atomically and idempotently;
- plaintext cursors do not enter PostgreSQL;
- future mapping can be reviewed independently from transport and collection.

### Negative

- source records still cannot drive the MVP assessment automatically;
- the ledger stores provider identifiers that require retention and privacy
  governance even though they are not credentials;
- a production progress protector and key lifecycle remain unresolved;
- resetting an exhausted stream and scheduling sync runs remain future work;
- each provider still needs an accepted field-to-measure mapping.

## Alternatives considered

### Map provider records directly to `InventorySnapshot`

Rejected because it would equate source identifiers with Genesis SKUs, discard
decimal and negative quantities, and hide location and measure differences.

### Keep one universal `availableUnits` field

Rejected because marketplace sellable quantity, ERP physical stock, reservation,
and multi-location balances have different semantics.

### Store raw provider JSON

Rejected because arbitrary payloads create an undocumented internal schema,
increase secret and personal-data risk, and postpone validation indefinitely.

### Store connector progress in plaintext

Rejected because future cursors may contain bearer-like or URL material and
database snapshots would expose it.

### Store progress in an external vault by reference

Deferred. It protects plaintext but makes atomic page commit span PostgreSQL and
an external system, creating orphan and cleanup states. A sealed envelope keeps
the first durable proof transaction-local.

### Aggregate all provider locations during ingestion

Rejected because fulfillment, seller warehouses, and ERP stock locations may
have different ownership, editability, reservation, and availability semantics.

## Authorization

This ADR alone authorizes no implementation. SPEC-0012 freezes the typed source
record, sealed progress, PostgreSQL ledger, atomic committer, and deterministic
tests for TASK-0073. No real provider, mapping, assessment, or production worker
is authorized.
