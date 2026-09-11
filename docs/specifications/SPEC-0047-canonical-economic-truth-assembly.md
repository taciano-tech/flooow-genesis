# SPEC-0047: Canonical Economic Truth Assembly

Status: Accepted

Date: 2026-09-05

Source decision: ADR-0048

## Objective

Define the smallest provider-neutral production contract required to transform
current independent marketplace economic evidence into a legitimate
MarketplaceOrder without inventing order occurrence time, monetary values,
coverage, applicability, or provider meaning.

This specification closes only the semantic gap proven by EXP-0008 and accepted
by ADR-0048.

The governed path becomes:

```text
MarketplaceIndependentEconomicEvidence
  -> active canonical facts
  -> MarketplaceEconomicTruthAssembler
  -> MarketplaceOrder
  -> MarketplaceEconomicTruthCalculator
```

This specification does not authorize implementation, persistence changes, a
migration, provider behavior, Sales Intelligence projection work, API/UI work,
or resumption of TASK-0146.

## Authority and dependencies

This specification must preserve:

- ADR-0020 and SPEC-0020, Marketplace Economic Truth;
- ADR-0042 and SPEC-0041, Independent Marketplace Economic Evidence;
- ADR-0043 and SPEC-0042, durable independent economic evidence;
- ADR-0048, Canonical Economic Truth Assembly Semantics;
- EXP-0008, Economic Evidence -> Economic Truth Assembly.

MarketplaceIndependentEconomicEvidence remains canonical evidence.

MarketplaceOrder remains the canonical normalized input to
MarketplaceEconomicTruthCalculator.

MarketplaceEconomicTruthCalculator remains the only authority for final
economic calculation and Complete versus Incomplete calculation results.

The assembler creates neither a second economic model nor a second calculator.

## Production boundary

The future implementation remains inside the existing marketplace operations
economics vertical.

Conceptually:

```text
io.flooow.marketplace.operations.economics.evidence
  MarketplaceEconomicOrderOccurrenceObservation
  MarketplaceIndependentEconomicFact.OrderOccurrence

io.flooow.marketplace.operations.economics
  MarketplaceEconomicTruthAssemblyPolicyVersion
  MarketplaceEconomicTruthAssemblyResult
  MarketplaceEconomicTruthAssemblyNotReadyReason
  MarketplaceEconomicTruthAssembler
```

The exact production file grouping is not an architectural concern.

No new Gradle module is required or authorized by this SPEC.

## Explicit order-occurrence evidence

The independent economic evidence boundary gains one explicit subject-level
economic fact:

```text
MarketplaceEconomicOrderOccurrenceObservation(
  id,
  subject,
  source,
  occurredAt,
  observedAt
)
```

It represents exactly:

```text
the occurrence time of the marketplace order identified by subject
```

It is not:

- revenue occurrence time;
- payment occurrence time;
- shipment occurrence time;
- ERP order occurrence time;
- invoice occurrence time;
- earliest evidence time;
- latest evidence time;
- Genesis observation time;
- projection materialization time.

The observation reuses:

- MarketplaceEconomicEvidenceObservationId;
- MarketplaceEconomicEvidenceSubject;
- EconomicSource;
- Instant.

No second timestamp, source, marketplace, organization, or order identity type is
introduced.

## Order-occurrence observation invariants

The observation must:

1. belong to exactly one MarketplaceEconomicEvidenceSubject;
2. use whole-microsecond precision for occurredAt and observedAt;
3. preserve source provenance exactly;
4. apply the existing MANUAL/CALCULATED source-time ordering rule;
5. apply no ordering inference to external provider clocks;
6. render [REDACTED];
7. create no identifier or timestamp internally.

For MANUAL and CALCULATED sources:

```text
observedAt >= occurredAt
```

For provider-originated clocks, no chronological relationship between source
occurrence and Genesis observation is inferred beyond retained provenance.

## Evidence-family relationship

Order occurrence belongs semantically to MARKETPLACE_ORDER.

The observation does not expose a caller-selectable family field.

Its fact wrapper reports:

```text
family = MARKETPLACE_ORDER
```

This prevents invalid family/order-occurrence combinations by construction.

## Extension of accepted fact

MarketplaceIndependentEconomicFact becomes conceptually:

```text
sealed interface MarketplaceIndependentEconomicFact
  Component(observation)
  ExternalIdentity(observation)
  OrderOccurrence(observation)
```

OrderOccurrence participates in the same canonical evidence aggregate as every
other accepted fact.

It must participate in:

- subject validation;
- global observation-identifier uniqueness;
- canonical fact ordering;
- historicalFacts;
- activeFacts;
- explicit correction and supersession;
- duplicate/conflict handling;
- immutable aggregate equality;
- redacted rendering.

No parallel order-time repository, mutable field, side table, or projection
authority is introduced at the domain boundary.

## Order-occurrence source-fact identity

For MARKETPLACE and ERP sources with a present external reference, the
order-occurrence source-fact key is:

```text
source.kind
+ source.systemKey
+ source.externalReference
+ ORDER_OCCURRENCE
```

ORDER_OCCURRENCE is a semantic discriminator only. It is not a new
EconomicComponentType.

For MANUAL or CALCULATED sources with absent external reference, no external
provider identity is invented and observation identifier remains the uniqueness
authority.

For the same order-occurrence source-fact key:

- equal occurredAt and equal semantic provenance is duplicate evidence;
- different occurredAt is a conflict requiring explicit correction.

A conflicting timestamp must never silently replace an active timestamp.

## Correction semantics

Existing MarketplaceEconomicEvidenceCorrection applies unchanged to
OrderOccurrence facts.

A valid correction:

```text
old OrderOccurrence fact
  -> retained in historicalFacts

correction
  -> explicit supersession relationship

replacement OrderOccurrence fact
  -> visible through activeFacts
```

The assembler consumes activeFacts only.

A superseded order-occurrence timestamp must never re-enter current assembly.

## Assembly policy version

Coverage derivation semantics are versioned independently from the Economic Truth
calculator policy.

Introduce:

```text
MarketplaceEconomicTruthAssemblyPolicyVersion
```

The value follows the same controlled version-string principles used by existing
economic policy values.

Version 1 is exactly:

```text
marketplace-economic-truth-assembly/1
```

This value is distinct from:

```text
marketplace-economic-truth/1
```

The assembly-policy version identifies how evidence becomes MarketplaceOrder.
The calculator-policy version identifies how MarketplaceOrder becomes Economic
Truth.

Neither version may be inferred from the other.

## Version 1 order-level coverage policy

Version 1 derives exactly one EconomicComponentCoverage for every
EconomicComponentType using current active component observations only.

For one type, let:

```text
active = all active Component observations whose component.type == type
```

The Version 1 rule is:

```text
active is empty
  -> MISSING

active is non-empty
  -> PARTIAL
```

Version 1 does not derive order-level COMPLETE from observation-level
coverage claims alone.

Observation-level COMPLETE remains evidence metadata, but the current accepted
contracts do not yet provide an independent canonical completeness authority
sufficient to conclude order-level COMPLETE.

A later assembly-policy version may derive COMPLETE only after a separately
accepted contract defines that authority explicitly.

Input list order has no effect.

No first-wins, last-wins, minimum-time, maximum-time, or source-priority rule is
permitted.

## COMPLETE semantics

Version 1 never derives order-level COMPLETE.

An observation-level COMPLETE claim states only that one accepted observation
claims complete coverage within its own evidence semantics.

It is not sufficient canonical authority to conclude that the complete set of
facts for the EconomicComponentType is known at order level.

Therefore Version 1 reduces every non-empty active component set to PARTIAL,
regardless of whether its observation-level claims are COMPLETE, PARTIAL, or a
mixture of both.

```text
{ COMPLETE }
  -> PARTIAL

{ COMPLETE, COMPLETE }
  -> PARTIAL

{ PARTIAL }
  -> PARTIAL

{ COMPLETE, PARTIAL }
  -> PARTIAL
```

Order-level COMPLETE requires a separately accepted canonical completeness
authority and therefore belongs to a future versioned assembly policy.

## MISSING semantics

When no active component exists for a type, Version 1 emits MISSING.

MISSING means only:

```text
canonical current evidence does not establish a component for this type
```

It does not mean:

- monetary zero;
- provider-confirmed zero;
- provider-confirmed absence;
- NOT_APPLICABLE;
- deletion;
- collection failure;
- unsupported marketplace;
- unsupported provider.

## NOT_APPLICABLE semantics

Version 1 never emits NOT_APPLICABLE automatically.

No current canonical evidence contract contains sufficient accepted subject-level
applicability authority for the assembler to prove that a component type does
not apply.

Therefore:

```text
absence != NOT_APPLICABLE
unsupported capability != NOT_APPLICABLE
NO_EVIDENCE != NOT_APPLICABLE
```

A future policy version may emit NOT_APPLICABLE only after a separately accepted
applicability contract establishes explicit semantic authority.

That future change requires governance and a new assembly-policy version.

## FINANCIAL_COST and OTHER_ADJUSTMENT

Current independent evidence families cannot represent FINANCIAL_COST or
OTHER_ADJUSTMENT component facts.

Version 1 therefore derives:

```text
FINANCIAL_COST
  -> MISSING when no active component exists

OTHER_ADJUSTMENT
  -> MISSING when no active component exists
```

They are not converted to zero or NOT_APPLICABLE.

This may cause MarketplaceEconomicTruthCalculator to return Incomplete.

That result is correct and intentional.

The assembler must not make the result complete merely because the software does
not yet ingest those component types.

## Collection attempts

MarketplaceEconomicEvidenceCollectionAttempt remains audit evidence only.

The assembler must not use:

```text
NO_EVIDENCE
AMBIGUOUS
TEMPORARY_FAILURE
```

to create:

- an EconomicComponent;
- zero;
- COMPLETE;
- NOT_APPLICABLE;
- orderOccurredAt.

Attempts do not participate directly in Version 1 coverage reduction.

Their presence or absence cannot change the assembled MarketplaceOrder when
active canonical facts are otherwise value-equal.

## Component preservation

The assembler copies no economic amount and performs no monetary calculation.

For every active Component fact, the exact existing EconomicComponent instance
is supplied to MarketplaceOrder.

The assembler must not change:

- component id;
- organization;
- order id;
- type;
- direction;
- magnitude;
- currency;
- source;
- external reference;
- occurredAt;
- quality.

The MarketplaceOrder constructor remains responsible for its existing ownership,
currency, duplicate, and coverage consistency invariants.

## Order-occurrence resolution

The assembler evaluates active OrderOccurrence facts after correction semantics
have produced activeFacts.

Let:

```text
times = distinct occurredAt values from active OrderOccurrence facts
```

Resolution is:

```text
times is empty
  -> NotReady(ORDER_OCCURRED_AT_UNRESOLVED)

times contains exactly one value
  -> use that value as MarketplaceOrder.occurredAt

times contains more than one value
  -> NotReady(ORDER_OCCURRED_AT_CONFLICT)
```

Multiple independent active observations with the same occurredAt value are
permitted.

Agreement between multiple sources is not required for readiness.

Disagreement must fail closed until explicit correction or later governed
adjudication resolves it.

## Assembly result

Introduce conceptually:

```text
sealed interface MarketplaceEconomicTruthAssemblyResult

Ready(
  order: MarketplaceOrder,
  assemblyPolicyVersion: MarketplaceEconomicTruthAssemblyPolicyVersion
)

NotReady(
  reasons: Set<MarketplaceEconomicTruthAssemblyNotReadyReason>,
  assemblyPolicyVersion: MarketplaceEconomicTruthAssemblyPolicyVersion
)
```

The Version 1 reasons are exactly:

```text
ORDER_OCCURRED_AT_UNRESOLVED
ORDER_OCCURRED_AT_CONFLICT
INCONSISTENT_ACTIVE_FACTS
```

No monetary value, source reference, organization id, order id, marketplace,
currency, or timestamp appears in controlled rendering.

Reasons are structural controlled enum values only.

## Ready semantics

Ready means:

```text
a legitimate MarketplaceOrder was constructed
```

It does not mean:

```text
economic truth is complete
```

Under Version 1, a Ready order may legitimately contain:

- PARTIAL coverage;
- MISSING coverage.

Order-level COMPLETE is reserved for a future assembly-policy version with
separately accepted canonical completeness authority.

The caller may then pass that MarketplaceOrder to
MarketplaceEconomicTruthCalculator.

Only the calculator determines Complete versus Incomplete.

## NotReady semantics

NotReady means MarketplaceOrder cannot legitimately be constructed from current
canonical evidence under the selected assembly policy.

NotReady must not contain a partial MarketplaceOrder.

NotReady must not invoke MarketplaceEconomicTruthCalculator.

NotReady must not manufacture fallback time, zero values, applicability, or
coverage merely to force calculator execution.

## INCONSISTENT_ACTIVE_FACTS

Current evidence construction already rejects most ownership, currency,
identifier, correction, and source-fact inconsistencies.

INCONSISTENT_ACTIVE_FACTS is a final fail-closed assembly envelope for a state
that cannot satisfy MarketplaceOrder invariants despite being presented as
current canonical evidence.

It must not expose the underlying value or exception text.

It must not be used to swallow programming errors unrelated to canonical input
integrity.

The future implementation must distinguish expected domain construction failure
from arbitrary unexpected runtime failure.

## Assembler

Introduce one pure stateless assembler:

```text
MarketplaceEconomicTruthAssembler.assemble(
  evidence: MarketplaceIndependentEconomicEvidence
): MarketplaceEconomicTruthAssemblyResult
```

Version 1 uses the fixed policy:

```text
marketplace-economic-truth-assembly/1
```

The assembler:

1. reads evidence.subject;
2. reads evidence.activeFacts;
3. resolves authoritative order occurrence;
4. collects exact active EconomicComponent values;
5. derives Version 1 coverage for every EconomicComponentType;
6. constructs MarketplaceOrder;
7. returns Ready or controlled NotReady.

It reads no:

- clock;
- repository;
- database;
- provider;
- filesystem;
- environment variable;
- HTTP client;
- projection;
- checkpoint;
- external configuration.

## Determinism

For value-equal canonical evidence:

```text
assemble(evidence) == assemble(evidence)
```

across independent invocations.

Equivalent evidence with different legal insertion order must produce the same
assembly result.

Canonical ordering is permitted for stable processing but must not create
semantic authority.

## Relationship to calculator

The assembler never calculates:

- gross revenue;
- marketplace fees;
- shipping total;
- advertising total;
- taxes;
- product cost total;
- financial cost total;
- other adjustments total;
- contribution;
- contribution margin;
- truth quality.

Those remain exclusively owned by MarketplaceEconomicTruthCalculator.

The intended usage is:

```text
when (val assembly = MarketplaceEconomicTruthAssembler.assemble(evidence)) {
  Ready -> MarketplaceEconomicTruthCalculator.calculate(assembly.order)
  NotReady -> do not calculate
}
```

## Relationship to Slice B

The governed downstream path remains:

```text
durable independent economic evidence
  -> MarketplaceEconomicEvidenceChangeFeed
  -> current evidence refetch
  -> MarketplaceEconomicTruthAssembler
  -> MarketplaceEconomicTruthCalculator
  -> durable Sales Intelligence projection
```

The Sales Intelligence processor must not duplicate this assembly logic.

The Sales Intelligence projection must not reinterpret raw evidence.

The synchronous future API/UI path must still read durable materialized
projection state rather than performing assembly on page load.

TASK-0146 remains paused.

After this SPEC is accepted, SPEC-0046 must be reconciled once against this
canonical assembly/calculation chain before TASK-0146 may resume.

## Persistence implications

OrderOccurrence becomes a canonical evidence fact and therefore future durable
evidence persistence must be able to retain and reconstruct it exactly.

This SPEC intentionally does not choose:

- database columns;
- discriminator encoding;
- JSON encoding;
- migration number;
- index changes;
- repository SQL;
- backfill behavior.

Those physical decisions belong to a later authorized TASK or bounded
persistence specification after the semantic contract is accepted.

No existing durable record may be silently interpreted as containing an
order-occurrence fact.

## Backward compatibility

Existing evidence aggregates without an OrderOccurrence fact remain legitimate
evidence.

Assembly of those aggregates returns:

```text
NotReady(ORDER_OCCURRED_AT_UNRESOLVED)
```

No migration-time fabricated timestamp or historical inference is permitted.

Existing Component, ExternalIdentity, CollectionAttempt, and Correction semantics
remain unchanged except that Correction may also supersede OrderOccurrence facts.

## Privacy and rendering

All new aggregate and result rendering remains redacted.

No toString output may expose:

- organization;
- order identity;
- marketplace;
- external order identity;
- currency;
- source;
- external reference;
- component;
- amount;
- occurrence time;
- observation time;
- assembly policy version.

Controlled NotReady reason enum names may be inspected explicitly by callers and
tests, consistent with existing controlled domain-result patterns.

## Acceptance tests

A future implementation task must prove at least:

1. existing evidence semantics remain unchanged for Component and ExternalIdentity;
2. OrderOccurrence belongs to exactly one subject and MARKETPLACE_ORDER family;
3. OrderOccurrence timestamp precision is enforced;
4. MANUAL/CALCULATED observation ordering is enforced;
5. provider clocks receive no invented ordering;
6. OrderOccurrence rendering is redacted;
7. exact duplicate order-occurrence evidence is idempotent;
8. same source-fact key with a different occurredAt conflicts;
9. explicit correction preserves historical occurrence and activates replacement;
10. superseded occurrence never participates in assembly;
11. no active occurrence yields ORDER_OCCURRED_AT_UNRESOLVED;
12. multiple equal active occurrence values yield one Ready order timestamp;
13. distinct active occurrence values yield ORDER_OCCURRED_AT_CONFLICT;
14. revenue/component/external-identity timestamps never substitute for order time;
15. active EconomicComponent instances are preserved exactly;
16. zero active components of one type derives MISSING;
17. all-COMPLETE active claims still derive PARTIAL under Version 1;
18. PARTIAL active claims derive PARTIAL;
19. mixed COMPLETE/PARTIAL derives PARTIAL independent of insertion order;
20. Version 1 never derives NOT_APPLICABLE from absence;
21. NO_EVIDENCE does not alter coverage;
22. AMBIGUOUS does not alter coverage;
23. TEMPORARY_FAILURE does not alter coverage;
24. FINANCIAL_COST without an active component is MISSING;
25. OTHER_ADJUSTMENT without an active component is MISSING;
26. unsupported types never become zero;
27. Ready MarketplaceOrder may contain MISSING and PARTIAL coverage;
28. calculator returns Incomplete for a legitimately assembled incomplete order;
29. assembler never calculates economic totals or margins;
30. assembly policy version is exactly marketplace-economic-truth-assembly/1;
31. calculator policy remains marketplace-economic-truth/1;
32. assembly policy and calculator policy are not conflated;
33. value-equal evidence produces value-equal assembly result;
34. legal evidence insertion order does not change the result;
35. malformed current state fails closed without leaking values;
36. no provider, database, HTTP, filesystem, environment, clock, random, AI,
    projection, or Kernel dependency is introduced by the pure assembly boundary;
37. full relevant module tests and repository validation remain green;
38. git diff --check remains green.

## Out of scope

This specification does not define or authorize:

- a TASK;
- production implementation;
- migration V017 or any migration;
- database schema changes;
- persistence serialization shape;
- provider adapters;
- Mercado Livre mapping;
- Omie mapping;
- financial settlement semantics;
- ledger redesign;
- reconciliation redesign;
- explicit applicability evidence;
- a Version 2 assembly policy;
- automatic NOT_APPLICABLE inference;
- source ranking or source trust scoring;
- timestamp adjudication policy;
- confidence scoring;
- projection implementation;
- scheduler/fairness;
- API/UI endpoints;
- synchronous page-load assembly;
- external action;
- AI behavior.

## Consequences

Positive consequences:

- order time becomes explicit canonical evidence;
- order time participates in existing correction semantics;
- coverage derivation becomes deterministic and versioned;
- active evidence is preserved without monetary transformation;
- absence remains distinct from zero and NOT_APPLICABLE;
- unsupported economic capabilities fail closed as MISSING;
- legitimate incomplete MarketplaceOrder values can reach the existing calculator;
- structural NotReady remains distinct from economic Incomplete;
- downstream projection logic receives one canonical assembly boundary.

Costs and limitations:

- the evidence fact model must eventually gain OrderOccurrence;
- durable persistence must eventually support the new fact shape;
- Version 1 intentionally cannot produce NOT_APPLICABLE from absence;
- some or many current orders may therefore calculate as Incomplete;
- FINANCIAL_COST and OTHER_ADJUSTMENT remain unsupported by current evidence
  families;
- applicability semantics require a separate future governed contract if needed;
- TASK-0146 remains paused until SPEC-0046 is reconciled.

## Acceptance

Acceptance of this SPEC authorizes drafting a separate implementation TASK for
the smallest canonical Economic Truth Assembly slice only.

That future TASK may implement only the semantic contracts accepted here and
must separately identify any durable-persistence work required for
OrderOccurrence.

Acceptance of this SPEC does not itself authorize implementation, migration,
provider behavior, projection implementation, API/UI work, or resumption of
TASK-0146.
