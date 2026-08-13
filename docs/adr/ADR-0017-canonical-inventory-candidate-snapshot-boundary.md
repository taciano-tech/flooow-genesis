# ADR-0017: Canonical Inventory Candidate Snapshot Boundary

Status: Proposed

Date: 2026-08-13

## Context

V011 can resolve one exact selected source candidate for one mapping lineage. The
candidate preserves organization, connection, source pointer, acceptance,
observation, mapping, canonical target, selected measure, and exact signed
rational quantity.

Multiple lineages may resolve to the same exact canonical item, nullable
location, and unit. An ERP, marketplace, fulfillment service, warehouse, or
operator system may each expose a legitimate candidate. V011 deliberately does
not say that those candidates are mutually comparable, fresh, additive, or
authoritative.

The next unsafe shortcut would be to let a reconciliation consumer query
whatever happens to be active while it evaluates a policy. Acceptances and
measure selections can advance independently between reads. A result could then
refer to a combination of candidates that never existed as one reviewed input.
It would be impossible to reproduce which exact evidence set a later authority,
aggregation, risk, or economic decision considered.

Genesis therefore needs one smaller boundary before reconciliation:

> capture an explicit, immutable set of currently resolved V011 candidates for
> one exact canonical target without ranking, comparing, combining, or choosing
> any candidate.

## Decision

Introduce a production-inactive Canonical Inventory Candidate Snapshot.

A snapshot is an organization-owned evidence bundle. It contains one exact
canonical target and one or more explicitly requested mapping lineages. For each
lineage, the repository resolves the active V011 selection against the active
V010 acceptance and freezes the resulting provenance references in one
transaction.

```text
explicit lineage roots
  + V011 active measure selections
  + V010 active accepted heads
  + V008 immutable exact observations
  -> one immutable same-target candidate snapshot
  -> later authority, freshness, reconciliation, aggregation, and business stock
```

The snapshot does not create a current inventory row. It does not establish
source authority, priority, confidence, freshness, equality, conflict,
ownership, additivity, or business availability.

## Explicit membership

The caller supplies only:

- trusted organization scope;
- a canonical target item, nullable location, and unit;
- a non-empty set of exact mapping lineage root IDs;
- a request ID, trusted principal reference, and correlation ID.

The caller cannot supply a connection, measure, quantity, selection,
acceptance, observation, mapping leaf, source pointer, revision, or source time.
Those values are loaded from the controlled V011 resolver.

Lineage membership is a set. Input order carries no priority or semantic
meaning. A lineage may appear at most once. Broad organization scans, implicit
discovery, “all current sources,” provider-name filters, and location wildcards
are excluded.

## Exact target agreement

Every resolved member must have the same organization and the exact target named
by the request:

```text
itemId
locationId? using null-safe equality
unitId
```

Cross-item, cross-location, and cross-unit capture fails closed. A null location
means the exact location-less canonical target; it is not a wildcard.

Candidates from different connections are permitted only when their canonical
target agrees exactly. This permission groups evidence; it does not make the
sources comparable or authoritative.

## Frozen provenance

Each member freezes:

- connection and capability;
- mapping lineage root;
- selection ID and revision;
- acceptance ID and revision;
- observation ID and source pointer;
- projection revision;
- mapping leaf ID and revision;
- canonical target;
- selected measure.

V012 stores no quantity. The exact signed rational remains in the immutable V008
observation. A snapshot reader reconstructs each quantity by joining the frozen
observation and selected measure and validates all copied provenance before
returning a member.

Later replacement or withdrawal of a mapping, acceptance, or selection does not
rewrite a snapshot. Historical snapshot reads remain reproducible from the
frozen immutable rows.

## No reconciliation semantics

Member order is deterministic for representation only and never means rank.
The snapshot performs no arithmetic and emits no comparison result.

In particular, it defines no:

- source priority or authority;
- wall-clock or provider-clock freshness threshold;
- tolerance, equality, divergence, or conflict rule;
- sum, minimum, maximum, average, fallback, or voting rule;
- location or channel aggregation;
- unit conversion or rounding;
- non-negative clamp;
- sellable, reservable, purchasable, or publishable quantity;
- inventory, purchase, pricing, promotion, supplier, or capital action.

Those are separate policies requiring their own evidence and accepted
specifications.

## Idempotency and concurrency

`CanonicalInventoryCandidateSnapshotRequestId` is unique inside one
organization. Repeating the same request ID with the same target and lineage set
returns the existing snapshot. Reusing it with different content conflicts.

For a new request, capture locks the requested lineage roots in canonical
unsigned 16-byte UUID order, then resolves every member inside one PostgreSQL
transaction. The Kotlin comparator and PostgreSQL ordering must implement the
same order. Any unavailable or divergent candidate changes nothing. Concurrent
acceptance or selection replacement is serialized by the same lineage locks;
the snapshot cannot contain a torn mix of old and new provenance for one
lineage.

A completed identical request may be replayed after later lifecycle retirement
because replay performs no write and returns the already immutable snapshot.

Snapshots have no mutable active head, revision chain, replacement, retirement,
or delete workflow. New evidence produces a new request and a new immutable
snapshot.

## Consequences

### Positive

- later decisions can cite the exact candidate set they evaluated;
- independently advancing source lineages cannot produce a torn evidence set;
- grouping across connections does not silently introduce authority;
- exact null, zero, negative, and rational semantics remain in V008;
- later economic and operational outcomes can be traced back to frozen source
  evidence without changing the Kernel.

### Negative

- a snapshot is not useful business stock by itself;
- membership must be selected explicitly by a trusted application workflow;
- no candidate is preferred and no disagreement is classified;
- later reconciliation still requires authority, freshness, and conflict
  policies;
- V008 observations and their provenance ledgers must remain retained.

## Alternatives considered

### Query all active candidates during reconciliation

Rejected because independently advancing heads could make a decision
irreproducible and because an organization-wide scan would silently define
membership.

### Copy quantities into the snapshot

Rejected because V008 is already the immutable exact quantity ledger. Copying
quantities creates another fact store and a divergence risk.

### Rank or reconcile while capturing

Rejected because membership, source authority, freshness, conflict handling,
and arithmetic are different decisions.

### Make the snapshot a Kernel primitive

Rejected because this is an inventory integration application boundary.
Marketplace and inventory evidence bundles are not universal organizational
primitives.

### Treat null location as all locations

Rejected because it would introduce implicit aggregation and make identity
matching ambiguous.

## Authorization

This ADR alone authorizes no implementation. SPEC-0017 freezes TASK-0084's pure
candidate-snapshot contract, additive V012 reference ledger, transactional
capture, frozen read, idempotency, isolation, and deterministic tests.

It authorizes no Kernel change, provider adapter, credential access, public
route, automatic scan, scheduler, source authority, ranking, staleness policy,
comparison, reconciliation result, aggregation, formula, rounding, business
stock, assessment, economic metric, recommendation, action, event, or external
request.
