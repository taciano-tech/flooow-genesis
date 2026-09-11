# ADR-0048: Canonical Economic Truth Assembly Semantics

Status: Accepted

Date: 2026-09-05

## Context

The canonical marketplace economic path currently contains two existing semantic
boundaries that cannot yet be connected without inventing meaning:

```text
MarketplaceIndependentEconomicEvidence
  -> ?
  -> MarketplaceOrder
  -> MarketplaceEconomicTruthCalculator
```

ADR-0042 defines independent economic evidence as append-only accepted facts,
collection attempts, explicit corrections, and active-fact current state.

It deliberately did not define complete Economic Truth materialization policy.

EXP-0008 tested whether the current evidence contract already contained enough
information and accepted semantics to construct MarketplaceOrder.

The experiment rejected that hypothesis.

Three missing semantic boundaries were demonstrated:

1. no accepted fact is authoritative for MarketplaceOrder.occurredAt;
2. observation-level COMPLETE/PARTIAL claims do not define canonical order-level
   coverage;
3. FINANCIAL_COST and OTHER_ADJUSTMENT cannot currently be represented by an
   independent evidence family, and their absence cannot mean NOT_APPLICABLE.

The missing boundary must be resolved upstream of the Sales Intelligence
projection. A projection, API, UI, provider adapter, or calculator must not
invent these semantics.

## Decision

Introduce a canonical Economic Truth Assembly semantic boundary between current
independent economic evidence and MarketplaceOrder.

Conceptually:

```text
durable independent economic evidence
  -> current active facts
  -> canonical Economic Truth Assembly
  -> MarketplaceOrder
  -> MarketplaceEconomicTruthCalculator
```

The assembly boundary is deterministic, fail-closed, provider-neutral, and
versioned where policy semantics affect the result.

It does not become a second evidence store and does not replace
MarketplaceEconomicTruthCalculator.

## Assembly authority

The assembler may consume only canonical current state and accepted assembly
policy.

It must not:

- read external providers;
- recover meaning from raw provider payloads;
- use projection state as economic authority;
- scan historical corrections when canonical activeFacts are already available;
- manufacture EconomicComponent values;
- manufacture monetary zero;
- reinterpret collection failure as economic fact;
- infer NOT_APPLICABLE from software capability;
- choose an economic timestamp merely because it is deterministic.

Corrections are consumed through canonical activeFacts only.

Historical superseded facts remain audit evidence and do not re-enter current
Economic Truth assembly.

## Authoritative order occurrence time

MarketplaceOrder.occurredAt requires explicit semantic authority.

The independent evidence boundary must therefore be capable of retaining an
explicit order-occurrence observation for the marketplace-order subject.

That observation is an economic-order fact, not a field inferred from another
financial component or external identity.

Conceptually it retains:

```text
subject
orderOccurredAt
source provenance
observedAt
```

The precise production type name belongs to the subsequent SPEC.

The order occurrence observation must remain independently ingestible. It must
not be added as a mandatory field of MarketplaceEconomicEvidenceSubject because
doing so would recreate an ordering dependency between otherwise-independent
evidence families.

The following timestamps are not permitted substitutes:

- revenue component occurredAt;
- earliest component occurredAt;
- latest component occurredAt;
- payment identity occurredAt;
- ERP identity occurredAt;
- invoice identity occurredAt;
- Genesis observedAt;
- projection projectedAt.

If no authoritative active order-occurrence observation exists, assembly returns
NotReady.

If multiple active observations establish different order occurrence values and
canonical correction semantics have not resolved the conflict, assembly returns
NotReady.

Equivalent observations may converge without changing economic meaning, subject
to the exact duplicate/conflict rules defined by the subsequent SPEC.

## Canonical order-level coverage

MarketplaceOrder requires exactly one EconomicComponentCoverage value for every
EconomicComponentType.

Order-level coverage is a derived canonical conclusion.

It is not copied mechanically from one MarketplaceEconomicComponentObservation
and it is not determined by list order.

Assembly must apply one explicit, versioned coverage policy over current active
facts.

The policy must preserve these meanings:

```text
COMPLETE
  canonical policy has sufficient authoritative active evidence to establish
  complete coverage for that EconomicComponentType

PARTIAL
  one or more active economic components exist, but canonical completeness has
  not been established

MISSING
  canonical coverage cannot currently establish an economic component for a
  type that has not been explicitly concluded NOT_APPLICABLE

NOT_APPLICABLE
  an explicit accepted applicability policy concludes that the economic type
  does not apply to this subject
```

`MISSING` is a completeness state.

It does not mean monetary zero, provider-confirmed absence, deletion, or
NOT_APPLICABLE.

This distinction permits assembly to remain conservative without inventing
economic values.

## Observation coverage claims

Existing observation-level COMPLETE and PARTIAL claims remain evidence metadata.

They participate in the versioned coverage policy but are not, by themselves,
order-level coverage authority.

A later SPEC must define the smallest deterministic policy contract needed to
establish when active evidence is authoritative enough for COMPLETE.

That contract must remain provider-neutral at the canonical boundary.

Provider-specific payload interpretation belongs in adapters before canonical
evidence is accepted.

## Collection attempts

NO_EVIDENCE, AMBIGUOUS, and TEMPORARY_FAILURE remain collection-attempt results,
not economic components.

They must never directly create:

- monetary zero;
- NOT_APPLICABLE;
- an EconomicComponent;
- an order occurrence timestamp.

Coverage policy may observe that canonical evidence is insufficient, but must
not reinterpret an attempt outcome as an economic amount.

## Economic types without a current evidence family

The absence of a current evidence family for FINANCIAL_COST or
OTHER_ADJUSTMENT does not imply NOT_APPLICABLE.

This ADR does not add those component types to an arbitrary existing family and
does not move financial-settlement authority out of the ledger/reconciliation
boundaries.

Until canonical evidence or an explicit applicability policy establishes
stronger meaning, their order-level coverage is MISSING.

Therefore:

```text
unsupported capability
  != NOT_APPLICABLE
  != zero
```

This conservative classification permits a legitimate MarketplaceOrder to exist
while MarketplaceEconomicTruthCalculator may correctly return Incomplete.

Future evidence support for these types may be added only through its own
accepted semantic contract.

## Assembly result boundary

The assembly boundary must distinguish structural assembly failure from
economic incompleteness.

Conceptually:

```text
EconomicTruthAssemblyResult

Ready(MarketplaceOrder)

NotReady(reasons)
```

NotReady means a legitimate MarketplaceOrder cannot be constructed.

Examples include:

- unresolved authoritative order occurrence time;
- conflicting active subject-level semantic facts;
- inconsistent canonical ownership or currency;
- another structural condition explicitly defined by the subsequent SPEC.

Ready does not mean economically complete.

A Ready MarketplaceOrder may contain PARTIAL or MISSING coverage and therefore
produce MarketplaceEconomicTruthCalculationResult.Incomplete.

The calculator remains the final authority for Complete versus Incomplete
Economic Truth.

## Determinism

Equivalent canonical current evidence plus the same assembly-policy version must
produce the same assembly result independent of legal insertion order.

Determinism must not be achieved by choosing arbitrary values.

Canonical ordering may stabilize processing, but ordering itself provides no
economic authority.

## Policy versioning

Any rule that changes canonical order-level coverage or applicability semantics
must be versioned.

The exact policy value type and version identifier belong to the subsequent
SPEC.

Changing policy semantics must not silently rewrite historical evidence.

Projection state remains disposable and may be rebuilt under the accepted
current policy according to its own projection contract.

## Relationship to Sales Intelligence Slice B

This decision preserves the planned path:

```text
durable independent economic evidence
  -> incremental change feed
  -> canonical Economic Truth Assembly
  -> MarketplaceEconomicTruthCalculator
  -> durable Sales Intelligence projection
  -> fast local API/UI
```

The Sales Intelligence processor may materialize only semantic output accepted
by this upstream chain.

It must not reproduce assembly logic independently.

It must not bypass assembly by interpreting raw evidence or provider payloads.

TASK-0146 remains paused until the downstream SPEC is reconciled with this
decision and the assembly contract is separately specified.

## Out of scope

This ADR does not define or authorize:

- production Kotlin;
- final class or interface names;
- repository changes;
- database schema changes;
- a migration;
- V017 or any later migration;
- provider adapters;
- Mercado Livre mapping;
- Omie mapping;
- ledger or reconciliation redesign;
- projection persistence;
- API/UI contracts;
- synchronous evidence reconstruction for page loads;
- scheduler or worker behavior;
- a TASK;
- implementation.

## Alternatives considered

### Use revenue occurredAt as order time - rejected

Revenue is an EconomicComponent fact. Its occurrence timestamp is not already
defined as marketplace-order occurrence authority.

Using it would convert a convenient timestamp into new economic meaning.

### Use earliest or latest evidence timestamp - rejected

Deterministic ordering does not create semantic authority.

### Add orderOccurredAt to MarketplaceEconomicEvidenceSubject - rejected

The subject is the common identity required by independent evidence families.

Making order occurrence mandatory on the subject would force otherwise-valid
evidence to wait for that information and would reintroduce sequencing
coupling.

### Copy observation coverage directly to order coverage - rejected

Observation coverage is local evidence metadata. Multiple active observations
of the same type may coexist and no caller list order is authoritative.

### Treat unsupported component types as NOT_APPLICABLE - rejected

Software capability is not economic applicability.

### Treat unsupported component types as zero - rejected

Absence of evidence is not an economic amount.

### Block MarketplaceOrder creation until every type is COMPLETE - rejected

MarketplaceOrder and MarketplaceEconomicTruthCalculator already model
incomplete coverage explicitly.

Assembly readiness and economic completeness are different boundaries.

## Consequences

Positive consequences:

- order occurrence gains explicit semantic provenance;
- independent evidence collection remains independent;
- active correction semantics remain authoritative;
- coverage becomes explicit, deterministic, and versionable;
- unsupported types fail closed as MISSING rather than fabricated zero or
  NOT_APPLICABLE;
- MarketplaceOrder can legitimately represent incomplete economic state;
- MarketplaceEconomicTruthCalculator remains sovereign;
- Sales Intelligence does not need provider or evidence-history work on the
  synchronous read path.

Costs and limitations:

- the evidence contract requires one new subject-level semantic observation;
- a bounded coverage-policy contract must be specified;
- some orders may remain economically Incomplete until more evidence or
  applicability authority exists;
- FINANCIAL_COST and OTHER_ADJUSTMENT remain unsupported as independent
  financial facts in this decision;
- downstream SPEC-0046 must be reconciled before TASK-0146 resumes.

## Evidence and references

- ADR-0020, Marketplace Economic Truth Boundary;
- SPEC-0020, Marketplace Economic Truth;
- ADR-0042, Independent Marketplace Economic Evidence Boundary;
- ADR-0043, Durable Independent Marketplace Economic Evidence Boundary;
- ADR-0046, Durable Marketplace Economic Evidence Incremental Change Feed;
- ADR-0047, Durable/Fast Sales Intelligence Projection Boundary;
- SPEC-0046, Durable/Fast Sales Intelligence Projection - Slice B;
- EXP-0008, Economic Evidence -> Economic Truth Assembly;
- PR #147 / merge commit 2910629.

## Authorization

If accepted after human review, this ADR authorizes drafting a separate bounded
SPEC for canonical Economic Truth Assembly semantics.

That SPEC may define only the minimum contracts necessary for:

- explicit order-occurrence evidence;
- versioned order-level coverage policy;
- deterministic Ready/NotReady assembly;
- preservation of existing Economic Truth calculator semantics;
- focused semantic acceptance tests.

This ADR does not authorize production implementation, migration, provider
behavior, projection implementation, API/UI work, or resumption of TASK-0146.
