# ADR-0021: Marketplace Financial Trace and Economic Ledger Boundary

Status: Proposed

Date: 2026-08-13

## Context

TASK-0090 established exact Marketplace Economic Truth for one normalized
order. It can explain a complete economic calculation, but it does not preserve
the financial events that occur later as the marketplace settles the order,
money reaches a payment account, and the bank records the receipt.

The next product foundation must retain an auditable chain such as:

```text
order
  -> sale
  -> commission / fees / shipping / advertising / taxes / product cost
  -> settlement
  -> payment account
  -> bank
```

For later reconciliation, the chain must preserve both what was expected and
what was actually observed. It must retain exact source provenance and history
without overwriting inconvenient facts.

The unsafe shortcuts would be to:

- add financial, order, marketplace, settlement, or bank vocabulary to the
  Kernel;
- store only one mutable current amount per stage;
- calculate a difference or reconciliation status before matching rules exist;
- silently replace a corrected source fact;
- deduplicate only by an internal random ID;
- accept an external financial fact without a stable source reference;
- mix currencies inside one order trace;
- activate a connector, API, worker, or financial action with the first ledger;
- call database insertion order business chronology.

## Decision

Introduce a production-inactive Financial Trace and Economic Ledger in the
Marketplace Intelligence vertical.

The pure domain remains inside the existing
`applications:marketplace-operations` module under:

```text
io.flooow.marketplace.operations.economics.ledger
```

The durable adapter and schema remain in the existing schema-owning
`applications:marketplace-operations-persistence-postgres` module.

No new Gradle module or dependency is required. No source in the ledger package
may import a Kernel type.

## Trace root

One financial trace is owned by one organization and one normalized marketplace
order. It freezes:

```text
organization
trace ID
marketplace order ID
marketplace key
external order ID
currency
database opening time
```

An organization may open at most one trace for an order. The same external
order reference may exist in another organization or marketplace without
collision.

The trace is a root of financial evidence. It is not a workflow instance,
reconciliation case, accounting journal, general ledger account, payment
instruction, or Genesis decision.

## Ledger facts

Every ledger entry is one immutable monetary fact with:

```text
organization and trace ownership
entry and append-request identities
financial stage
EXPECTED or ACTUAL basis
ADDITION or DEDUCTION direction
non-negative exact money magnitude
source provenance
economic occurrence time
database recording time
optional correction target
```

The accepted first stages are:

```text
SALE
MARKETPLACE_COMMISSION
MARKETPLACE_FEE
SHIPPING
ADVERTISING
TAX
PRODUCT_COST
FINANCIAL_COST
OTHER_ADJUSTMENT
SETTLEMENT
PAYMENT_ACCOUNT
BANK
```

`EXPECTED` means the amount a trusted normalization process says should occur.
`ACTUAL` means the amount a trusted source says did occur. The ledger does not
infer one from the other and does not require both to exist.

Direction is separate from the non-negative magnitude. Reversals remain
explicit facts with the economically opposite direction; a reversal is not a
database correction.

## Provenance and duplicate protection

The ledger reuses the accepted MKT-001 source contract:

```text
MARKETPLACE
ERP
MANUAL
CALCULATED
```

Marketplace and ERP facts require a stable external reference. Manual and
calculated facts may explicitly declare internal origin.

Within one organization, a present external source fact is unique by:

```text
source kind
source system key
external reference
financial stage
basis
```

The trace is deliberately absent from this key so the same external financial
fact cannot be attached to two orders in one organization. Source systems must
provide a line-level stable reference when one settlement contains several
facts of the same stage and basis.

Internal append request IDs provide a second replay boundary. An exact replay
returns the original entry. Reusing a request or source-fact key with different
material fails closed.

## Corrections without mutation

An input mistake is corrected by appending a replacement entry that explicitly
references the entry it corrects. The original remains readable.

A correction target must:

- exist in the same organization and trace;
- have the same stage and basis;
- not already have a direct correction;
- already exist when the replacement append begins.

Correction chains may continue by correcting the latest replacement. They are
linear and auditable. The ledger does not calculate the effective current fact;
that projection belongs to the later reconciliation boundary.

An economic reversal does not use the correction relationship. It is another
economic fact with its own provenance, occurrence time, and opposite direction.

## Time semantics

`occurredAt` is source/business time supplied with the normalized fact.
`recordedAt` and trace `openedAt` are PostgreSQL transaction time.

The ledger preserves late-arriving and backdated facts. It does not reject an
occurrence merely because it precedes or follows another stage. Source order,
database record order, and business chronology remain distinct.

Reads use a deterministic canonical order by `recordedAt` and unsigned entry
UUID. This is a presentation of the append history, not a claim that timestamps
are unique.

## Exact money and currency

Ledger entries reuse the exact MKT-001 money and currency contracts. Magnitudes
are non-negative, bounded, scale-limited decimal values; calculated or signed
totals are not stored by this slice.

Every entry must use the trace currency. There is no FX conversion, exchange
rate, base currency, rounding, allocation, or inferred amount.

## Durable behavior

PostgreSQL stores immutable trace roots and ledger entries. Updates and deletes
are rejected by database triggers.

New traces and entries require an active organization. Historical reads remain
available to the same organization after lifecycle suspension so audit history
is not erased by operational status.

The repository supports:

```text
open trace
append one fact
find trace by trace ID
find trace by order ID
```

Opening and appending are transactional. Concurrent replay accepts one row and
returns the same controlled outcome to equivalent callers. Foreign organization
lookups return no data.

## Not reconciliation yet

The ledger preserves the evidence needed to answer separately:

- what amounts were expected;
- what amounts were observed;
- which source and reference supplied each amount;
- which facts corrected prior input.

It does not yet answer authoritatively:

- expected minus actual;
- matched versus unmatched;
- pending, partial, divergent, recovered, or fully reconciled;
- whether two entries represent the same obligation;
- whether a settlement closes an order;
- whether money reached the correct bank account.

Those require the explicit MKT-003 matching, status, tolerance, and divergence
contract. Keeping them separate prevents database storage rules from becoming
hidden financial judgment.

## No infrastructure activation or intelligence

This boundary adds no:

- Mercado Livre, ERP, payment-account, bank, Ads, tax, or freight adapter;
- HTTP route, JSON contract, UI, dashboard, webhook, scheduler, or worker;
- general-ledger debit/credit account, chart of accounts, invoice, or tax book;
- matching, difference, tolerance, reconciliation status, or systemic issue;
- refund, return, recovery, pricing, simulation, recommendation, decision,
  action, AI, model, expert, or agent;
- outbox event or production startup wiring;
- Kernel change.

## Consequences

### Positive

- every accepted financial fact remains immutable and source-traceable;
- expected and actual evidence can coexist without premature matching;
- late settlement and bank facts can join the same order trace;
- duplicate provider facts cannot be attached to multiple orders;
- corrections preserve history instead of rewriting it;
- MKT-003 receives a deterministic, organization-isolated evidence base;
- Marketplace vocabulary remains outside the Kernel.

### Negative

- the first ledger alone cannot declare an order reconciled;
- normalized callers must supply stage, basis, direction, and stable provenance;
- line-level external references may require provider-specific normalization;
- trace and entry retention/privacy policy remains future work;
- no live order reaches the ledger until an ingestion contract is accepted.

## Alternatives considered

### One mutable row per order and stage

Rejected because it erases prior observations and cannot explain corrections or
late-arriving settlement facts.

### Store expected and actual in the same row

Rejected because they can arrive from different systems at different times and
may each have multiple partial facts and corrections.

### Compute reconciliation while appending

Rejected because matching, tolerance, lifecycle, and closure rules have not yet
been accepted and must remain versioned judgment.

### Use the Economic Truth result as the ledger row

Rejected because a calculation snapshot is a derived breakdown, while a ledger
must preserve individual occurrence, source, replay, and correction history.

### Use an open stage string

Rejected for the first durable slice because uncontrolled stage semantics would
make later matching unsafe. New stages require an additive accepted contract and
migration.

### Put financial primitives in the Kernel

Rejected because only the Marketplace vertical has demonstrated this need.

### Use a new Gradle module

Rejected because the existing Marketplace application and PostgreSQL adapter
already provide the correct domain and schema ownership boundaries.

## Authorization

This ADR alone authorizes no implementation. SPEC-0021 may authorize only the
production-inactive domain, PostgreSQL V014 ledger, repository, and deterministic
tests for TASK-0092. It authorizes no reconciliation, live ingestion, API,
runtime, financial action, pricing, AI, or Kernel modification.
