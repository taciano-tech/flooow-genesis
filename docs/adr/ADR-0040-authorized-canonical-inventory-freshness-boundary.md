# ADR-0040: Authorized Canonical Inventory Freshness Boundary

Status: Proposed

Date: 2026-08-20

## Context

TASK-0135 links one complete canonical inventory observation to one source-
authority assessment only when identity, lineage, target, selected measure,
and exact quantity agree. The resulting evidence retains three different time
facts:

- nullable provider-controlled `sourceUpdatedAt`;
- non-null Genesis source-ledger `sourceCommittedAt`;
- non-null Genesis canonical `projectedAt`.

None of those timestamps currently supports a freshness conclusion. Treating
the newest timestamp as truth, silently replacing a missing provider time with
Genesis time, or reading the wall clock inside the domain would erase
provenance and make later Operational Truth irreproducible.

The next smallest question is therefore:

> At the exact time when source authority was assessed, does this linked
> observation satisfy one explicit, versioned temporal policy?

This question is narrower than connection health, current-state selection,
Inventory Confidence, or Safe ATP.

## Decision

Introduce a pure freshness assessment in the existing
`inventory-source-authority` module.

It consumes:

- one `AuthorizedCanonicalInventoryObservationEvidence`;
- one scoped `CanonicalInventoryFreshnessPolicy`.

The evaluation time is not a second caller-controlled clock. It is exactly the
`evaluatedAt` retained by the evidence's source-authority assessment. A later
consumer that needs a later answer must reassess authority and freshness at
that later time.

## Versioned and scoped policy

The freshness policy is tied to:

- its own policy version;
- the exact source-authority policy version it supplements;
- organization;
- connection;
- target;
- selected canonical measure;
- one half-open effective interval.

It independently limits source-commit age and projection age. Both thresholds
are positive, finite, microsecond-aligned durations no greater than 31 days.

## Provider timestamp modes

Provider time is optional evidence and is never silently substituted.

The policy explicitly selects one of two modes:

```text
REQUIRE_SOURCE_UPDATED
DO_NOT_ASSESS_SOURCE_UPDATED
```

`REQUIRE_SOURCE_UPDATED` requires a present provider timestamp, a positive
finite maximum source age, and a non-negative maximum future lead no greater
than five minutes. Both durations are microsecond-aligned. The accepted source
window is inclusive.

`DO_NOT_ASSESS_SOURCE_UPDATED` requires both source-time durations to be absent.
It means that this provider timestamp is not a freshness input under this
policy. It does not claim that provider occurrence time is fresh and it does
not convert commit time into provider time.

## Independent temporal facts

Source, commit, and projection ages have different meanings:

- source age describes provider-declared update time when policy requires it;
- commit age describes how recently Genesis durably accepted the source page;
- projection age describes how recently Genesis produced this canonical view.

Commit and projection must not be in the future relative to evaluation time,
and `sourceCommittedAt` must not be after `projectedAt`.

No ordering is imposed between `sourceUpdatedAt` and either Genesis timestamp.
Provider clocks can differ, and the explicit source future-lead allowance is
the only accepted treatment of that difference.

## Controlled result

The first disagreement returns one typed result:

```text
Fresh(assessment)
OrganizationMismatch
ConnectionMismatch
TargetMismatch
MeasureMismatch
AuthorityPolicyVersionMismatch
PolicyNotYetEffective
PolicyExpired
TemporalPrecisionMismatch
SourceTimestampRequired
SourceTimestampTooFarInFuture
SourceTimestampTooOld
CommitAfterProjection
CommitTimestampInFuture
CommitTimestampTooOld
ProjectionTimestampInFuture
ProjectionTimestampTooOld
```

Successful assessment retains the exact evidence and policy instances. It
copies no timestamp, duration, age, quantity, source, or status text. Every
new rendering is redacted.

## Freshness is not health or truth

`Fresh` means only that the linked, authorized evidence satisfies the accepted
temporal policy at the authority evaluation time. It does not establish:

- connector or provider health;
- source correctness, completeness, or historical reliability;
- canonical current-state winner;
- reconciliation or agreement with another source;
- Inventory Confidence;
- business availability or Safe ATP.

The source-reported exact quantity remains evidence.

## No infrastructure activation

The assessment reads no `Clock`, repository, environment, connector, provider,
database, API, event, worker, scheduler, or UI. It creates no migration and
changes no Kernel or Marketplace module.

## Consequences

### Positive

- freshness becomes reproducible from frozen evidence and versioned policy;
- provider-time absence is explicit rather than hidden by fallback;
- ingestion and projection delays remain independently visible;
- the authority decision and freshness answer share one evaluation instant;
- later health and current-state contracts receive a precise temporal fact.

### Negative

- a provider configured to require source time fails closed when it omits it;
- policies must choose and govern finite age thresholds;
- a new authority evaluation is required for a later freshness answer;
- health and current-state selection remain unresolved.

## Alternatives considered

Using the latest of the three timestamps was rejected because it destroys their
different provenance. Automatically falling back from provider time to commit
time was rejected because old provider data could appear fresh. Treating all
providers as source-time-required was rejected because some valid integrations
do not supply a trustworthy occurrence clock. Accepting a caller-supplied
second evaluation time was rejected because the retained authority assessment
could already be inactive. Combining freshness, health, current state,
confidence, and ATP was rejected as an unauditable Trust engine. Kernel
promotion was rejected because this remains integration-inventory policy.

## Authorization

This ADR alone authorizes no implementation. SPEC-0040 may authorize only one
pure, versioned freshness policy and assessment, controlled temporal results,
redaction, and focused tests for TASK-0137.

No connection health, source priority, current-state selection, reconciliation,
aggregation, reservation, unconfirmed demand, business availability,
Inventory Confidence, Safe ATP, persistence, runtime, action, AI, or Kernel
change is authorized.
