# ADR-0042: Independent Marketplace Economic Evidence Boundary

Status: Proposed

Date: 2026-08-27

## Context

MGI v0.7.6 proved that evidence needed for one marketplace order does not
arrive in a reliable universal sequence. Shipment cost can be known before an
ERP order is matched. Product COGS can be known through seller SKU and Omie
product cost. An exact invoice and tax total can be observed without ERP
sales-order identity. Marketplace item-to-Ad-Group identity can be known while
order-level Ads allocation remains unknown.

Genesis Economic Truth already accepts incomplete component coverage and
refuses to produce a complete result while required components are missing or
partial. It does not yet define how independently arriving provider facts are
accepted, retained, corrected, or protected from later empty refreshes.

Using a mutable order row for this work would allow a later provider failure or
missing response to erase accepted evidence. Using ERP order identity as a
global gate would recreate the artificial sequence removed by MGI v0.7.6.

## Decision

Introduce a pure independent marketplace economic evidence boundary inside the
existing `applications:marketplace-operations` module.

It models:

- one organization-scoped marketplace order subject;
- independently observed financial component facts;
- independently observed external identity relationships;
- bounded collection attempts that produced no accepted fact;
- explicit supersession of an accepted fact;
- deterministic append, duplicate, conflict, and correction results.

The boundary is append-only from the domain perspective. A missing, ambiguous,
or failed collection attempt is evidence about an attempt, not a command to
remove an accepted fact.

## Evidence families

The accepted families are:

```text
MARKETPLACE_ORDER
MARKETPLACE_PAYMENT
MARKETPLACE_SHIPPING
PRODUCT_COST
FISCAL_INVOICE
FISCAL_TAX
ADS_IDENTITY
ADS_ALLOCATION
```

No family depends on another family being present. In particular:

- marketplace shipping does not require ERP order identity;
- product cost does not require ERP order identity;
- invoice and tax evidence do not require ERP sales-order identity;
- Ads identity does not imply Ads allocation;
- payment observation does not establish financial settlement or bank receipt.

## Reuse

Financial observations reuse existing:

- `OrganizationId`;
- `MarketplaceOrderId`;
- `MarketplaceKey`;
- `MarketplaceExternalOrderId`;
- `MarketplaceCurrency`;
- `EconomicComponent` and its identifier, type, direction, money, source,
  occurrence time, and quality;
- `EconomicComponentCoverage` for a component-level completeness claim.

The boundary introduces no second money, currency, source, component, order,
quality, or coverage model.

## Accepted fact versus collection attempt

An accepted fact contains a value or identity with provenance. A collection
attempt contains no financial amount and cannot satisfy component coverage.

Accepted attempt outcomes are:

```text
NO_EVIDENCE
AMBIGUOUS
TEMPORARY_FAILURE
```

`NO_EVIDENCE` means only that this bounded attempt produced no accepted fact.
It is not an authoritative zero, deletion, not-applicable decision, or proof
that the fact does not exist.

An authoritative zero is an accepted financial component whose exact
`MarketplaceMoney` amount is zero and whose provenance and coverage claim are
present.

## Financial observations

Each financial observation retains one existing `EconomicComponent`, its
evidence family, Genesis observation time, and a component coverage claim.

Only `COMPLETE` and `PARTIAL` are valid observation coverage claims. `MISSING`
is represented by absence plus collection attempts. `NOT_APPLICABLE` is a
later versioned policy conclusion, not a provider fact.

Family and component type must agree:

| Family | Permitted component types |
| --- | --- |
| `MARKETPLACE_ORDER` | `REVENUE`, `MARKETPLACE_COMMISSION`, `MARKETPLACE_FEE` |
| `MARKETPLACE_SHIPPING` | `SHIPPING` |
| `PRODUCT_COST` | `PRODUCT_COST` |
| `FISCAL_TAX` | `TAX` |
| `ADS_ALLOCATION` | `ADVERTISING` |

Payment, invoice, and Ads identity families do not accept a financial
component in this contract. Financial settlement remains owned by the existing
ledger and reconciliation domains.

## External identity observations

External identities are explicit relationships, not financial components.
Accepted kinds are:

```text
MARKETPLACE_PAYMENT
ERP_ORDER
FISCAL_INVOICE
MARKETPLACE_ITEM_TO_AD_GROUP
```

Each relationship retains:

- the common order subject;
- one observation identifier;
- evidence family and identity kind;
- an anchor external reference;
- the linked external system and reference;
- source provenance;
- source occurrence time;
- Genesis observation time.

An invoice identity does not create tax evidence. A payment identity does not
create settlement evidence. An item-to-Ad-Group identity does not create an
advertising component and cannot change `ADVERTISING` coverage.

Multiple Ads relationships are allowed. Their existence still authorizes no
spend allocation formula.

## Deterministic merge

One immutable evidence set belongs to exactly one subject. Updates for another
organization, internal order, marketplace, external order, or currency fail
closed before duplicate or conflict classification.

Updates are classified in this order:

1. subject mismatch;
2. invalid family/payload relationship;
3. observation identifier already exists with an equal payload: duplicate;
4. observation identifier already exists with a different payload: conflict;
5. financial source-fact key already exists with equal economic meaning:
   duplicate source fact;
6. financial source-fact key already exists with different economic meaning:
   conflict;
7. append the new fact or attempt.

The financial source-fact key reuses the Economic Truth uniqueness identity:
source kind, source system, present external source reference, and component
type. Internal-origin sources without an external reference use observation
identity and do not gain an invented provider reference.

Ordering of retained facts and attempts is canonical and independent of input
collection order. No caller list order becomes authority.

## Correction and supersession

A conflicting fact is never silently replaced. Correction requires a separate
supersession update containing:

- its own canonical identifier;
- the replacement accepted fact;
- the exact prior observation identifier;
- one controlled reason: `SOURCE_CORRECTION`, `MAPPING_CORRECTION`, or
  `VERIFIED_MANUAL_CORRECTION`;
- correction observation time.

The prior fact must exist, belong to the same subject, not already be
superseded, and have an observation time no later than the correction. The
replacement must use a new observation identifier. Cycles and correction of a
collection attempt are invalid.

Supersession preserves the prior fact and records its replacement relationship.
Later materialization uses active facts only, while audit views retain the full
chain.

## Time and precision

Source occurrence time and Genesis observation time remain distinct. Every new
timestamp must be aligned to whole microseconds. Observation time may not
precede source occurrence time for Genesis- or manually-originated facts; no
ordering is imposed for provider clocks beyond retained provenance in this
first contract.

Freshness, clock skew policy, and provider health are later assessments. This
contract reads no clock and invents no timestamp.

## Privacy and isolation

Every new aggregate, update, result, identifier, relationship, and attempt
renders `[REDACTED]` or `[INTERNAL]` consistently with existing economics
types. Controlled failures expose no organization, order, marketplace, source,
reference, amount, timestamp, or reason text through rendering.

The module remains a marketplace vertical. No type is promoted to the Kernel.

## No infrastructure activation

This boundary contains no repository, database, migration, JSON, HTTP,
connector, provider client, credential, worker, scheduler, event, outbox, UI,
AI, or external action. Persistence and projection follow only after this pure
contract is accepted and implemented.

## Consequences

### Positive

- independent evidence progression becomes canonical and provider-neutral;
- later empty or failed refreshes cannot erase accepted facts;
- zero and missing retain different meanings;
- correction is explicit and auditable;
- Ads identity cannot leak into Ads cost;
- existing Economic Truth, ledger, and reconciliation remain sovereign.

### Cost

- provider adapters must translate their payloads into explicit facts and
  attempts;
- correction chains and source-fact conflicts require durable persistence
  later;
- complete Economic Truth still requires a separate materialization policy.

## Alternatives considered

Updating one mutable Sales Intelligence row was rejected because absence can
overwrite known evidence and projection/history can diverge. Requiring ERP
order identity first was rejected because evidence families are independent.
Treating provider `0`, null, missing field, and failed request as equivalent was
rejected because it invents financial truth. Treating Ads identity as an
advertising component was rejected because identity supplies no order-level
amount. Embedding provider payloads or connectors in the domain was rejected
because the contract must be provider-neutral. Kernel promotion was rejected
because the concepts remain marketplace-specific.

## Authorization

This ADR alone authorizes no implementation. SPEC-0041 may authorize only one
pure evidence set, accepted observation and attempt types, deterministic merge
and supersession results, redaction, and focused tests inside the existing
marketplace application.
