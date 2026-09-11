# ADR-0014: Canonical Inventory Observation Boundary

Status: Proposed

Date: 2026-08-12

## Context

TASK-0073 preserves immutable inventory balance evidence exactly as received
from one organization-scoped integration connection. TASK-0075 allows an
organization to map the exact source item, location, and unit tuple to stable
canonical identities with an explicit positive rational quantity factor.

Neither capability produces a canonical inventory observation. The source
ledger still contains provider identities and units, while the mapping registry
stores conversion metadata without applying it. The current Marketplace
Operations `InventorySnapshot` and `InventoryRiskInput` cannot bridge this gap:
both accept one whole, non-negative availability value and omit connection,
location, unit, source evidence, mapping revision, and the other four source
measures.

Provider contracts make an implicit bridge unsafe. Mercado Livre manages
independent quantities across fulfillment, selling-address, and seller-warehouse
locations. Omie exposes physical, reserved, pending inbound, pending outbound,
and available measures independently for a product and stock location. These
measures are not universally derivable from each other, and operational values
may be negative or decimal.

Applying a rational factor to a finite source decimal may also produce a
non-terminating decimal. For example, one source unit multiplied by `1/3` cannot
be represented exactly by any fixed decimal scale. PostgreSQL `numeric(p,s)`
rounds values whose scale exceeds `s`; floating-point types are inexact.
Choosing either representation here would silently introduce inventory policy.

Genesis therefore needs an intermediate, immutable projection that says only:

> this exact committed source record, interpreted through this exact mapping
> decision, yielded these exact quantities for these canonical identities.

## Decision

Introduce a production-inactive Canonical Inventory Observation Ledger.

One projection reads exactly one V006 source-balance record and the one active
V007 mapping that matches its exact selector. It copies no provider identifier
into the canonical observation. It applies the mapping factor independently to
each present source measure and persists an immutable observation with source,
mapping, target, timing, and projection provenance.

```text
V006 immutable source record
  + V007 active exact mapping decision
  -> deterministic exact factor application
  -> V008 immutable canonical observation revision
  -> later current-state, aggregation, reconciliation, and business policy
```

This boundary is a provenance-preserving interpretation, not an inventory
snapshot, balance authority, aggregation, reconciliation result, or mutation.

## Exact quantity representation

Canonical quantities use a reduced signed rational:

```text
ExactInventoryQuantity(numerator, denominator)
denominator > 0
gcd(abs(numerator), denominator) = 1
zero = 0 / 1
```

For a source decimal with unscaled integer `u` and non-negative scale `s`, and a
mapping factor `n/d`, projection computes:

```text
raw numerator   = u * n
raw denominator = 10^s * d
canonical value = reduce(raw numerator / raw denominator)
```

The algorithm uses integer arithmetic only. It performs no decimal division,
floating-point conversion, rounding, truncation, saturation, absolute-value
conversion, or negative-value clamp.

`availableToSell`, `onHand`, `reserved`, `pendingInbound`, and
`pendingOutbound` remain separate nullable measures. Null remains null. Zero is
present zero, not missing. Genesis does not derive one measure from another or
select which one represents business availability.

## Identity and provenance

Each observation records:

- a canonical internal observation UUID;
- organization and exact source-ledger pointer;
- mapping decision ID and mapping revision;
- canonical item, optional location, unit, and applied factor;
- five independently converted nullable exact quantities;
- nullable source-updated time copied from evidence;
- non-null source-commit and projection times;
- projection revision, correlation ID, and optional predecessor observation.

Source item, location, SKU, unit code, version, title, description, GTIN, raw
payload, and credential data are not copied. They remain available only through
the protected source evidence referenced by the observation.

Source-updated time, source-commit time, and projection time are different
facts. A missing provider timestamp remains missing; Genesis never substitutes
its commit or processing clock as provider observation time.

## Reprojection and history

An observation is never updated or deleted. Exact replay under the same mapping
decision is idempotent only when all projected content agrees.

When a later active mapping decision reinterprets the same source record,
Genesis may append another observation revision. The new row cites the prior
observation and has a greater mapping revision. The old interpretation remains
reproducible. No row is marked current or authoritative in this boundary.

This deliberate history avoids rewriting past evidence while leaving the later
consumer contract responsible for selecting accepted projection revisions.
TASK-0077 does not automatically reproject an entire ledger after a mapping
change and does not declare the highest revision to be business truth.

## Lifecycle and atomicity

Projection requires:

- an active organization;
- a same-organization connection in `ACTIVE` or `SUSPENDED` state;
- one existing exact V006 source record;
- one matching active V007 mapping decision;
- active canonical target identities;
- exact mapping/evidence selector agreement.

Suspension permits offline repair without opening credentials or contacting a
provider. Draft, revoked, unknown, or foreign connections cannot create new
observations. Historical reads remain available to authorized organization
scope after connection, mapping, or identity retirement.

The source record, mapping decision, target lifecycles, predecessor, conversion,
and insert are validated in one PostgreSQL transaction. A source-row lock
serializes competing projections. Any validation, constraint, trigger, or
commit failure changes nothing.

## Consequences

### Positive

- canonical quantities remain mathematically exact for every accepted factor;
- negative, decimal, zero, and missing measures preserve their meaning;
- provider identities do not leak into the canonical observation model;
- every quantity is reproducible from immutable source and mapping evidence;
- mapping corrections append history instead of rewriting prior interpretation;
- later aggregation can choose policy explicitly and cite exact revisions;
- the current MVP workflow remains isolated from incompatible integration data.

### Negative

- rational arithmetic and schema checks are more complex than fixed decimals;
- observations still cannot drive the MVP risk assessment directly;
- repeated interpretations consume additional storage;
- no current-state or winner selection exists yet;
- a later presentation boundary must choose rounding and display rules;
- a later policy must decide which measures and locations can be aggregated.

## Alternatives considered

### Convert directly to `InventorySnapshot`

Rejected because it would discard location, unit, decimal, negative, missing,
measure, source, and mapping provenance while choosing availability semantics.

### Store a fixed-scale decimal

Rejected because rational factors such as `1/3` cannot be represented exactly,
and PostgreSQL rounds values beyond a declared scale.

### Store floating-point quantities

Rejected because binary floating-point is inexact and equality, replay, and
audit reproduction would become unstable.

### Recompute available from physical and reserved

Rejected because provider availability may include pending and operational
policies not captured by one universal formula.

### Overwrite observations after mapping correction

Rejected because past decisions and downstream results could no longer be
reproduced.

### Aggregate connections and locations during projection

Rejected because duplicate authority, location ownership, timing, staleness,
and measure selection require a separate reconciliation policy.

## Authorization

This ADR alone authorizes no implementation. SPEC-0014 freezes TASK-0077's pure
exact-projection model, additive V008 ledger, transactional projector, history,
and deterministic tests. It authorizes no provider adapter, public endpoint,
automatic mapping, bulk worker, scheduler, aggregation, current-state winner,
rounding, business availability, inventory mutation, assessment, or event.
