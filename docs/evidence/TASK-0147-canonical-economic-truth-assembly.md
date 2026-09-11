# TASK-0147: Canonical Economic Truth Assembly

Status: Implementation complete; PR/CI pending

Date: 2026-09-05

## Authority

This implementation task is governed by:

- SPEC-0047, Accepted;
- ADR-0048, Accepted;
- SPEC-0020 and ADR-0020 for Marketplace Economic Truth;
- SPEC-0041 and ADR-0042 for Independent Marketplace Economic Evidence;
- SPEC-0042 and ADR-0043 for durable independent economic evidence;
- EXP-0008 and its concluded Reject decision;
- accepted SPEC-0046 only as a downstream consumer contract.

SPEC-0047 is normative. This TASK narrows execution to the smallest canonical
Economic Truth Assembly implementation slice and must not reinterpret, widen,
or bypass the accepted semantic contract.

## Repository checkpoint

- base HEAD at task drafting:
  `dfade8717e11f41dee7f643f687a1ca284806460`;
- assigned task: `TASK-0147`;
- prior Sales Intelligence implementation task: `TASK-0146`;
- TASK-0146 remains paused;
- no migration is assigned to TASK-0147;
- no provider implementation is assigned to TASK-0147.

## Objective

Implement the smallest provider-neutral production slice that converts current
canonical MarketplaceIndependentEconomicEvidence into either:

```text
MarketplaceEconomicTruthAssemblyResult.Ready(MarketplaceOrder)
```

or:

```text
MarketplaceEconomicTruthAssemblyResult.NotReady
```

without inventing order occurrence time, monetary values, coverage,
applicability, provider meaning, or persistence semantics.

The implemented path is:

```text
MarketplaceIndependentEconomicEvidence
  -> activeFacts
  -> MarketplaceEconomicTruthAssembler
      -> Ready(MarketplaceOrder)
      -> NotReady(reasons)
```

MarketplaceEconomicTruthCalculator remains downstream and unchanged.

## Closed implementation scope

Relevant implementation work is limited to exactly these eight paths:

1. MODIFY
   `applications/marketplace-operations/src/main/kotlin/io/flooow/marketplace/operations/economics/evidence/MarketplaceIndependentEconomicEvidence.kt`
2. MODIFY
   `applications/marketplace-operations/src/test/kotlin/io/flooow/marketplace/operations/economics/evidence/MarketplaceIndependentEconomicEvidenceTest.kt`
3. CREATE
   `applications/marketplace-operations/src/main/kotlin/io/flooow/marketplace/operations/economics/MarketplaceEconomicTruthAssembler.kt`
4. CREATE
   `applications/marketplace-operations/src/test/kotlin/io/flooow/marketplace/operations/economics/MarketplaceEconomicTruthAssemblerTest.kt`
5. MODIFY
   `applications/marketplace-operations-persistence-postgres/src/main/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceIndependentEconomicEvidenceRepository.kt`
6. MODIFY
   `applications/marketplace-operations-persistence-postgres/src/test/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceIndependentEconomicEvidenceRepositoryTest.kt`
7. CREATE, then MODIFY only as the task evidence record
   `docs/evidence/TASK-0147-canonical-economic-truth-assembly.md`
8. MODIFY by appending exactly one TASK-0147 execution entry
   `docs/journal/MGI-EXECUTIVE-JOURNAL.md`

No ninth path is authorized.

File 5 exists only because MarketplaceIndependentEconomicFact is sealed and
adding OrderOccurrence requires the existing PostgreSQL adapter to remain
compile-safe and explicitly fail closed until durable persistence is separately
authorized.

File 6 may test only that unsupported durable OrderOccurrence persistence fails
closed without corrupting, fabricating, or partially persisting evidence.

Neither file 5 nor file 6 authorizes actual OrderOccurrence persistence.

Discovery that implementation requires another production file, build file,
migration, schema change, helper, provider adapter, scheduler, projection file,
API, or UI file is a stop condition requiring TASK/SPEC review.

## Explicit OrderOccurrence observation

Introduce inside the existing evidence boundary:

```text
MarketplaceEconomicOrderOccurrenceObservation(
  id,
  subject,
  source,
  occurredAt,
  observedAt
)
```

It represents exactly the occurrence time of the marketplace order identified
by subject.

It must reuse the existing:

- MarketplaceEconomicEvidenceObservationId;
- MarketplaceEconomicEvidenceSubject;
- EconomicSource;
- Instant.

It must introduce no alternate organization, marketplace, order identity,
source, or timestamp abstraction.

The observation must:

- belong to exactly one subject;
- use whole-microsecond precision for occurredAt and observedAt;
- preserve source provenance exactly;
- apply the accepted MANUAL/CALCULATED ordering rule;
- infer no source-clock ordering for external providers;
- create no identifier or timestamp internally;
- render exactly `[REDACTED]`.

For MANUAL and CALCULATED sources:

```text
observedAt >= occurredAt
```

For provider-originated clocks, no additional chronological inference is
permitted.

## Fact extension

Extend MarketplaceIndependentEconomicFact with exactly one new fact subtype:

```text
OrderOccurrence(observation)
```

Its family is structurally fixed to:

```text
MARKETPLACE_ORDER
```

Callers must not be able to select another family.

OrderOccurrence participates in the existing aggregate semantics for:

- subject validation;
- global observation-id uniqueness;
- canonical fact ordering;
- historicalFacts;
- activeFacts;
- duplicate handling;
- conflict handling;
- explicit correction and supersession;
- immutable aggregate equality;
- redacted rendering.

No parallel order-time repository or mutable order-time field is authorized.

## Source-fact identity

For MARKETPLACE and ERP sources with a present external reference, the
OrderOccurrence source-fact identity is exactly:

```text
source.kind
+ source.systemKey
+ source.externalReference
+ ORDER_OCCURRENCE
```

ORDER_OCCURRENCE is a semantic discriminator only and must not become an
EconomicComponentType.

For MANUAL or CALCULATED sources with no external reference, observation id
remains the uniqueness authority. No provider identity may be invented.

For an equal OrderOccurrence source-fact key:

- equal occurredAt and equal semantic provenance -> Duplicate;
- different occurredAt -> conflict requiring explicit correction.

A conflicting timestamp must never silently replace an active timestamp.

## Correction semantics

MarketplaceEconomicEvidenceCorrection applies unchanged.

A corrected OrderOccurrence must preserve:

```text
old fact
  -> historicalFacts

correction
  -> explicit supersession

replacement fact
  -> activeFacts
```

The assembler must consume activeFacts only.

A superseded OrderOccurrence must never re-enter current assembly.

## Assembly production contract

Create the production assembly boundary containing exactly the accepted
semantic concepts required by SPEC-0047:

- MarketplaceEconomicTruthAssemblyPolicyVersion;
- MarketplaceEconomicTruthAssemblyResult;
- MarketplaceEconomicTruthAssemblyNotReadyReason;
- MarketplaceEconomicTruthAssembler.

No new Gradle module is authorized.

### Policy version

Version 1 is exactly:

```text
marketplace-economic-truth-assembly/1
```

It is independent from calculator policy:

```text
marketplace-economic-truth/1
```

Neither policy version may be inferred from the other.

### Result

The result surface is exactly:

```text
Ready(
  order,
  assemblyPolicyVersion
)

NotReady(
  reasons,
  assemblyPolicyVersion
)
```

Version 1 NotReady reasons are exactly:

```text
ORDER_OCCURRED_AT_UNRESOLVED
ORDER_OCCURRED_AT_CONFLICT
INCONSISTENT_ACTIVE_FACTS
```

Controlled rendering must expose no organization id, order id, marketplace,
currency, source reference, monetary value, or timestamp.

NotReady must contain no partial MarketplaceOrder.

## Order-occurrence resolution

The assembler reads active OrderOccurrence facts only.

Let:

```text
times = distinct occurredAt values from active OrderOccurrence facts
```

Then:

```text
no values
  -> NotReady(ORDER_OCCURRED_AT_UNRESOLVED)

exactly one value
  -> use it as MarketplaceOrder.occurredAt

more than one distinct value
  -> NotReady(ORDER_OCCURRED_AT_CONFLICT)
```

Multiple independent observations agreeing on the same occurredAt are valid.

Source agreement is not otherwise required.

Timestamp disagreement must fail closed.

## Version 1 coverage

For every EconomicComponentType:

```text
active = active Component facts whose component.type == type
```

The exact policy is:

```text
active.isEmpty()
  -> MISSING

active.isNotEmpty()
  -> PARTIAL
```

Version 1 never derives COMPLETE.

Version 1 never derives NOT_APPLICABLE automatically.

Observation-level COMPLETE remains evidence metadata only.

Absence, unsupported ingestion capability, NO_EVIDENCE, AMBIGUOUS, and
TEMPORARY_FAILURE must not become COMPLETE, NOT_APPLICABLE, or zero.

FINANCIAL_COST and OTHER_ADJUSTMENT remain MISSING when no active component
exists.

## Component preservation

For every active Component fact, the assembler supplies the exact existing
EconomicComponent value to MarketplaceOrder.

It must not alter:

- component id;
- organization id;
- order id;
- type;
- direction;
- magnitude;
- currency;
- source;
- external reference;
- component occurredAt;
- quality.

The assembler performs no monetary calculation.

MarketplaceOrder retains authority for its existing ownership, currency,
duplicate, and coverage consistency invariants.

## Collection attempts

MarketplaceEconomicEvidenceCollectionAttempt remains audit evidence only.

Assembly must not use attempts to create:

- EconomicComponent;
- monetary zero;
- COMPLETE;
- NOT_APPLICABLE;
- order occurrence time.

With value-equal activeFacts, changing attempts alone must not change assembly.

## INCONSISTENT_ACTIVE_FACTS

INCONSISTENT_ACTIVE_FACTS is a final fail-closed envelope only for canonical
input that cannot satisfy MarketplaceOrder invariants.

It must not expose exception text or offending values.

It must not swallow arbitrary programming errors unrelated to canonical input
integrity.

Implementation must distinguish expected domain construction failure from
unexpected runtime/programming failure.

## Assembler purity

Expose:

```text
MarketplaceEconomicTruthAssembler.assemble(
  evidence: MarketplaceIndependentEconomicEvidence
): MarketplaceEconomicTruthAssemblyResult
```

The assembler is pure and stateless.

It may read only:

- evidence.subject;
- evidence.activeFacts;
- accepted immutable economic domain values.

It must read no:

- clock;
- repository;
- PostgreSQL;
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

Equivalent legal insertion orders must produce the same semantic result.

Canonical ordering may stabilize processing but must never create authority.

No first-wins, last-wins, minimum-time, maximum-time, or source-priority
adjudication is permitted.

## Relationship to calculator

The assembler must never calculate revenue, costs, contribution, contribution
margin, or truth quality.

Usage remains:

```text
assembly = MarketplaceEconomicTruthAssembler.assemble(evidence)

Ready
  -> MarketplaceEconomicTruthCalculator.calculate(order)

NotReady
  -> do not invoke calculator
```

Only MarketplaceEconomicTruthCalculator determines Complete versus Incomplete.

## PostgreSQL compatibility boundary

TASK-0147 does not implement durable OrderOccurrence persistence.

The existing PostgreSQL repository currently has physical support only for the
pre-existing durable fact kinds.

Because MarketplaceIndependentEconomicFact is sealed, the adapter must be made
compile-safe after OrderOccurrence is introduced.

The authorized PostgreSQL change is therefore limited to explicit fail-closed
handling for OrderOccurrence until a later persistence task is accepted.

The adapter must not:

- map OrderOccurrence to COMPONENT;
- map OrderOccurrence to EXTERNAL_IDENTITY;
- serialize it into an unrelated field;
- reuse another occurred_at column;
- infer it from Component occurrence;
- infer it from provider payload;
- silently drop it while reporting Applied;
- partially commit an update containing it.

An attempt to durably apply an unsupported OrderOccurrence fact or correction
whose replacement is OrderOccurrence must fail through an existing sanitized
persistence failure surface and leave durable state unchanged.

The exact durable encoding is intentionally unresolved in TASK-0147.

## Mandatory persistence follow-up

After TASK-0147, a separately governed persistence slice is mandatory before
OrderOccurrence can participate in real durable production evidence.

That later work must decide and test, at minimum:

- migration number;
- durable fact discriminator;
- physical timestamp representation;
- source provenance representation;
- insert semantics;
- replay semantics;
- correction replacement semantics;
- duplicate/conflict behavior across restart;
- rollback and atomicity;
- real PostgreSQL/Testcontainers evidence.

No existing durable record may be reinterpreted as OrderOccurrence.

No historical timestamp may be backfilled or fabricated.

## Tests

### Evidence-domain tests

Tests must prove at minimum:

- valid OrderOccurrence construction;
- whole-microsecond enforcement;
- MANUAL/CALCULATED observedAt ordering;
- provider clocks do not gain invented ordering;
- family is structurally MARKETPLACE_ORDER;
- redacted rendering;
- duplicate same source-fact/same occurredAt;
- conflict same source-fact/different occurredAt;
- global observation-id uniqueness;
- correction supersedes OrderOccurrence;
- historicalFacts retains superseded fact;
- activeFacts exposes only replacement;
- aggregate equality remains deterministic.

### Assembler tests

Tests must prove at minimum:

- no OrderOccurrence -> ORDER_OCCURRED_AT_UNRESOLVED;
- one authoritative distinct time -> Ready;
- multiple active facts with equal time -> Ready;
- multiple distinct active times -> ORDER_OCCURRED_AT_CONFLICT;
- corrected old occurrence does not re-enter assembly;
- empty component set for a type -> MISSING;
- non-empty component set for a type -> PARTIAL;
- observation COMPLETE still reduces to PARTIAL;
- no V1 COMPLETE;
- no automatic NOT_APPLICABLE;
- FINANCIAL_COST absent -> MISSING;
- OTHER_ADJUSTMENT absent -> MISSING;
- attempts do not affect assembly;
- exact component values are preserved;
- Ready may still yield calculator Incomplete;
- assembler never substitutes for calculator;
- equivalent legal insertion orders produce equal assembly results;
- repeated assembly produces equal results;
- expected MarketplaceOrder invariant failure maps to
  INCONSISTENT_ACTIVE_FACTS without leaking values;
- unrelated unexpected programming failure is not swallowed.

### PostgreSQL compatibility tests

Tests must prove at minimum:

- the existing repository remains operational for supported durable facts;
- unsupported OrderOccurrence persistence fails closed;
- unsupported OrderOccurrence correction persistence fails closed;
- no durable evidence version advances on that failure;
- no change_sequence is committed on that failure;
- no partial fact or correction row is committed;
- subsequent supported persistence remains usable.

These PostgreSQL tests validate only the temporary fail-closed compatibility
boundary. They do not count as OrderOccurrence persistence support.

## Verification

Implementation evidence must include:

- focused marketplace-operations tests;
- focused PostgreSQL/Testcontainers compatibility tests;
- repository build/test gate required by CI;
- `git diff --check`;
- mechanically enumerated changed paths;
- proof that no migration was added;
- proof that no ninth path was changed;
- proof that TASK-0146 remained untouched.

## Explicitly out of scope

TASK-0147 does not authorize:

- PostgreSQL schema or migration changes;
- durable OrderOccurrence encoding;
- data migration or backfill;
- provider adapters;
- Mercado Livre integration changes;
- Omie integration changes;
- Sales Intelligence projection implementation;
- TASK-0146 resumption;
- API or UI work;
- synchronous page-load assembly;
- MGI opportunity intelligence;
- VOI;
- Hypothesis Ledger;
- Bayesian belief;
- Opportunity-to-Outcome;
- new Gradle modules;
- new economic component types;
- new applicability or completeness semantics.

## Stop conditions

Stop implementation and return to governance if any of the following becomes
necessary:

1. a ninth changed path;
2. a migration or schema change;
3. a new repository method;
4. a new evidence family;
5. a new EconomicComponentType;
6. a fourth NotReady reason;
7. an alternate assembly policy version;
8. provider-specific assembly logic;
9. automatic COMPLETE or NOT_APPLICABLE semantics;
10. a fallback/inferred order occurrence timestamp;
11. swallowing arbitrary runtime/programming failures as
    INCONSISTENT_ACTIVE_FACTS;
12. durable persistence support beyond explicit fail-closed compatibility;
13. any modification to TASK-0146 implementation scope;
14. any API/UI, projection, MGI, VOI, Bayesian, or opportunity-intelligence work.

## Completion gate

TASK-0147 is complete only when:

1. all authorized domain and assembler semantics are implemented;
2. all required tests pass;
3. the PostgreSQL adapter remains compile-safe and fails closed for unsupported
   durable OrderOccurrence operations;
4. no migration or durable OrderOccurrence representation exists;
5. the complete diff contains no path outside the eight-path closed scope;
6. execution evidence is appended to this TASK document;
7. exactly one TASK-0147 entry is appended to the executive journal;
8. CI is green;
9. the mandatory durable OrderOccurrence persistence gap remains explicit;
10. TASK-0146 remains paused.

Completion of TASK-0147 does not by itself authorize TASK-0146.

The next gate after TASK-0147 is a separately authorized durable
OrderOccurrence persistence slice.

## Execution evidence

Implementation completed locally on 2026-09-05 against the TASK-0147 closed scope.

### Implemented

- introduced explicit MarketplaceEconomicOrderOccurrenceObservation inside the existing independent evidence boundary;
- extended MarketplaceIndependentEconomicFact with OrderOccurrence;
- preserved correction, supersession, canonical ordering, uniqueness, equality, provenance, precision, and redaction semantics;
- implemented MarketplaceEconomicTruthAssembler with policy marketplace-economic-truth-assembly/1;
- implemented fail-closed occurredAt resolution and Version 1 MISSING/PARTIAL coverage policy;
- preserved exact active EconomicComponent values;
- kept MarketplaceEconomicTruthCalculator unchanged and downstream;
- kept the PostgreSQL adapter compile-safe while explicitly rejecting unsupported durable OrderOccurrence persistence;
- added real PostgreSQL/Testcontainers compatibility evidence for unsupported observation and correction replacement.

### PostgreSQL compatibility result

Unsupported OrderOccurrence durable persistence returns the existing sanitized IntegrityFailure surface before durable mutation.

The tests prove:

- unsupported OrderOccurrence observation does not create a subject, journal row, identifier row, fact row, version advance, or committed change_sequence;
- unsupported OrderOccurrence correction replacement leaves the existing subject version unchanged;
- no journal, identifier, fact, or correction row is partially committed;
- no new change_sequence is committed;
- a subsequent supported durable fact remains persistable after the rejection.

This is compatibility-only fail-closed behavior. TASK-0147 does not implement durable OrderOccurrence encoding or replay.

### Local verification

Focused marketplace-operations gate:

```text
.\gradlew.bat :applications:marketplace-operations:test
BUILD SUCCESSFUL
```

Focused unsupported OrderOccurrence PostgreSQL/Testcontainers gate:

```text
.\gradlew.bat :applications:marketplace-operations-persistence-postgres:test --tests "*unsupported order occurrence*"
BUILD SUCCESSFUL
```

Complete marketplace-operations-persistence-postgres gate:

```text
.\gradlew.bat :applications:marketplace-operations-persistence-postgres:test
BUILD SUCCESSFUL
```

Complete repository gate:

```text
.\gradlew.bat clean build --rerun-tasks --no-daemon --console=plain
BUILD SUCCESSFUL in 4m 24s
94 actionable tasks: 94 executed
```

Diff hygiene:

```text
git diff --check
exit code: 0
```

### Closed-scope proof before documentation update

Mechanical working-tree enumeration contained exactly these six implementation/test paths:

```text
applications/marketplace-operations/src/main/kotlin/io/flooow/marketplace/operations/economics/evidence/MarketplaceIndependentEconomicEvidence.kt
applications/marketplace-operations/src/main/kotlin/io/flooow/marketplace/operations/economics/MarketplaceEconomicTruthAssembler.kt
applications/marketplace-operations/src/test/kotlin/io/flooow/marketplace/operations/economics/evidence/MarketplaceIndependentEconomicEvidenceTest.kt
applications/marketplace-operations/src/test/kotlin/io/flooow/marketplace/operations/economics/MarketplaceEconomicTruthAssemblerTest.kt
applications/marketplace-operations-persistence-postgres/src/main/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceIndependentEconomicEvidenceRepository.kt
applications/marketplace-operations-persistence-postgres/src/test/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceIndependentEconomicEvidenceRepositoryTest.kt
```

No migration path was present.

TASK-0146 was absent from the working tree and remains paused.

After this evidence record and the single executive-journal entry are added, the complete TASK-0147 diff is expected to contain exactly the eight authorized paths.

### Mandatory remaining gap

Durable OrderOccurrence persistence remains intentionally unimplemented and requires a separately governed persistence task before OrderOccurrence can participate in real durable production evidence.

That future gate must decide and test the migration, durable discriminator, timestamp/source representation, insert, replay, correction replacement, restart behavior, duplicate/conflict behavior, rollback, and Testcontainers evidence.

No existing durable row has been reinterpreted or backfilled as OrderOccurrence.

### CI status

Local implementation and repository gates are green.

GitHub PR CI is pending because TASK-0147 has not yet been committed or pushed.
