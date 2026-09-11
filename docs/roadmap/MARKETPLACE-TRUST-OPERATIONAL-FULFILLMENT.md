# Marketplace Trust, Operational Truth, and Fulfillment Orchestration

Status: Approved target direction; no implementation authorized

Recorded: 2026-08-20

Source reviewed: Flooow Trust, Operational Truth & Fulfillment Orchestration
Roadmap / Architecture Specification v1.1

Trusted-commerce consolidation recorded: 2026-08-20 (TASK-0125)

## Purpose and authority

This roadmap item records the target trust boundary required before Flooow can
turn fulfillment opportunity into safe external execution. It complements the
Marketplace Intelligence Operating Model and the future OFI capability. It is
not a parallel implementation sequence, a mandate to build one large Trust
service, or authorization for a broad feature.

Before any capability below becomes an ADR, specification, task, module,
score, recommendation, or automation, the latest repository state remains
authoritative. Accepted contracts must be extended rather than duplicated.

## Architectural invariants

- Genesis remains the Organizational Computing platform.
- Marketplace trust, inventory, fiscal, supplier, logistics, seller, and
  fulfillment vocabulary remains outside the Kernel unless a separately
  validated universal primitive is accepted.
- Economic Truth, Operational Truth, Fiscal Eligibility, Fiscal Truth,
  Marketplace Eligibility, Marketplace Truth, and Financial Trace/Reconciled
  Truth remain distinct boundaries. The word `Truth` does not make any of them
  a generic Kernel primitive.
- Eligibility records what approved policy permits. Truth records what
  auditable evidence says occurred. Neither one silently implies the other.
- Financial settlement, split, or payment direction never defines commercial,
  operational, marketplace, or fiscal reality.
- Facts, evidence, features, assessment, score, eligibility, recommendation,
  policy, authority, decision, action, outcome, and learning do not collapse
  into one type or agent.
- Missing or critically stale evidence never becomes an optimistic default.
- Recommendation never grants execution authority.
- Scores are decomposable, versioned assessments rather than decisions.
- Material actions retain evidence, policy, authority, responsible party, and
  outcome lineage.

## Flooow Trust Layer target

The long-term execution trust path is:

```text
Economic Truth
  + Operational Truth
  + Fiscal Eligibility
  + Marketplace Eligibility
  + Supplier Reliability
  + Seller Reputation Protection
  -> Decision Intelligence
  -> Policy and Authority
  -> Trusted Execution
  -> Fiscal Truth + Marketplace Truth + Financial Trace
  -> Reconciliation and Outcome
  -> Learning
```

`Operational Truth` is a target umbrella for auditable physical and
operational state. It is not approved as one generic Kernel abstraction or one
premature service. Concrete vertical evidence must demonstrate each contract.

### Distinct truth and eligibility boundaries

The target model separates permission from occurrence:

```text
Fiscal Policy
  -> Fiscal Eligibility
  -> Authorized Execution
  -> Fiscal Documents and Events
  -> Fiscal Truth
```

Economic Truth explains the deterministic economics of the commercial event.
Operational Truth explains auditable physical and responsibility facts.
Marketplace Truth records what the marketplace observed, charged, refunded,
or reported. Financial Trace and Reconciliation explain expected versus actual
money movement. Reconciled Truth is a qualified projection over those retained
facts, never a replacement for their source evidence.

## Repository dependency assessment

Inspection of `main` at `59fae87` found substantial reusable foundations.

### Already delivered

- typed immutable inventory source balances with source identity, location,
  unit, version, source time, available-to-sell, on-hand, reserved, pending
  inbound, and pending outbound measures;
- exact source-to-canonical identity mapping and rational quantity projection;
- immutable canonical observations with source pointer, provenance, source
  update time, source commit time, and projection time;
- explicit source acceptance and measure selection;
- frozen candidate snapshots, exact comparison, and explicit human
  adjudication without automatic authority;
- connector runtime and integration control-plane boundaries;
- Marketplace Economic Truth, Financial Ledger, Reconciliation, Net-Back,
  Product Cost Basis, and deterministic price diagnostics.

These foundations must be reused. Source-declared `availableToSell`, `reserved`,
or pending measures remain source evidence; they are not yet Flooow business
availability, Operational Truth, Inventory Confidence, or Safe ATP.

### Missing dependencies

- reusable source authority, ownership, health, and staleness policy;
- provider succession and canonical current-state selection;
- explicit operational reservations, unconfirmed demand, order dependency,
  and responsibility evidence controlled by Flooow;
- location/channel aggregation and business-availability semantics;
- inventory reconciliation state and confidence reasons;
- versioned graceful-degradation and Safe ATP policy;
- supplier dispatch, fill-rate, cancellation, return, fiscal, catalog,
  lead-time, and integration outcome evidence;
- order SLA, marketplace deadline, warehouse, carrier, and seller-impact
  evidence;
- fiscal scenario/eligibility registry and specialist-approved policies;
- atomic title, custody, inventory-recognition, risk-bearing, sale-right,
  reservation-holder, and fiscal-seller evidence where the operating model
  requires those distinctions;
- allocation policy, Seller Entitlement, Channel ATP, and published-inventory
  lineage after Safe ATP exists;
- product fiscal profiles and versioned policy applicability evidence;
- participant payable/allocation and settlement-grouping contracts extending
  the accepted Financial Trace and Reconciliation capabilities;
- a repository-versioned OFI v1.1 contract.

The supplied Trust specification references OFI v1.1, but no OFI document or
contract exists in this repository checkpoint. This roadmap therefore records
the dependency without inventing or duplicating OFI semantics. When OFI is
versioned, it must be mapped into the existing Operating Model and this trust
boundary.

## First eligible dependency chain

The target first vertical slice remains deterministic Operational Truth,
Inventory Confidence, and Safe ATP for one controlled inventory source.
Repository inspection shows that it is not yet implementation-ready as one
slice. Its prerequisite chain is:

```text
accepted canonical inventory evidence
  -> source authority and freshness assessment
  -> explicit canonical current-state selection
  -> operational reservations and unconfirmed demand evidence
  -> deterministic inventory-confidence assessment
  -> versioned Safe ATP and graceful-degradation policy
```

The smallest future contract is expected to address source authority/health
and freshness against accepted canonical evidence. That expectation is not an
authorization or fixed task number; `main` must be reinspected when the
inventory roadmap becomes active again.

Safe ATP must return the same result for the same frozen evidence and policy
version, retain decomposable reason codes and evidence references, and fail
safe under critical uncertainty. A transient failure may progress through
policy-controlled reduced ATP, aggressive buffer, zero ATP, and listing pause;
it must not always pause an entire catalog immediately.

## Target capability boundaries

### Inventory Confidence and Dynamic ATP

Inventory Confidence remains distinct from reported quantity. Candidate
inputs include source authority, source age, reconciliation state,
reservations, unconfirmed orders, integration health, historical accuracy, and
concurrent sales velocity. Candidate output retains reported stock,
freshness, confidence, reservations, unconfirmed demand, Safe ATP, policy
version, evidence, and reasons.

Seller performance may be one later allocation feature, but it is never the
sole allocator. Channel allocation must avoid an unexamined self-reinforcing
monopoly and remain governed by explicit versioned policy.

Physical quantity, source-reported reserved quantity, Safe ATP, Seller
Entitlement, Channel ATP, and published quantity remain different facts or
projections. The target dependency direction is:

```text
Physical Inventory
  -> Operational Truth
  -> Inventory Confidence
  -> Safe ATP
  -> Allocation Policy
  -> Seller Entitlement
  -> Channel ATP
  -> Published Inventory
```

No single `economicOwner` field is accepted as sufficient legal or operational
truth. Concrete models should retain the atomic facts needed by the applicable
policy, including title/ownership status, custody, inventory-recognition
party, risk-bearing party, sale-right holder, reservation holder, and fiscal
seller. These facts may change at different events and times.

### Supplier Reliability

Supplier Reliability is a future deterministic, decomposable assessment over
observed outcomes such as inventory accuracy, dispatch SLA, fill rate,
cancellation, return quality, fiscal reliability, catalog accuracy, lead-time,
and integration health. Every assessment retains score version, policy
version, evidence, observation window, freshness, and confidence.

Predictive extensions and Reliability-Adjusted Economic Cost remain ineligible
until calibrated outcome evidence exists. Modeled failure cost stays separate
from realized accounting truth.

### Seller Reputation Protection

The target path is order evidence to SLA risk, expected seller impact,
preventive intervention, escalation, remediation, responsibility, settlement,
and outcome. Prevention is preferred to punishment.

This capability is not implementation-ready before order lifecycle,
marketplace deadline, supplier/warehouse/carrier, integration-health, and
seller-impact evidence exist. It authorizes no autonomous penalty.

### Fiscal orchestration

Flooow owns fiscal scenario, operation classification, eligibility,
jurisdiction, effective date, policy/rule version, evidence, and audit lineage.
Specialized calculation may come from replaceable Tax Provider Adapters;
provider output is evidence and never sovereign eligibility or decision
intelligence.

Brazilian fiscal logic must not become scattered application conditionals, but
this roadmap does not authorize building a generic tax engine or choosing a
vendor.

A future Fiscal Policy Registry may qualify applicability by operation model,
origin, seller and destination jurisdictions, product fiscal profile,
fulfillment mode, and effective date. Candidate output includes `ELIGIBLE`,
`CONDITIONAL`, `BLOCKED`, or `UNKNOWN`, required documents and evidence,
provider reference, policy version, and reason codes.

A future versioned Product Fiscal Profile may retain fiscal description, NCM,
CEST where applicable, origin, composition, classification evidence,
effective interval, approver, and policy version. No product classification,
CFOP, CST, or tax treatment from a proposal becomes software truth without
qualified specialist approval and applicable evidence.

`SELLER_DIRECT_DELIVERY`, venda a ordem, consignation, dedicated stock,
warehouse, and fulfillment are candidate operating/fiscal models, not inferred
rules. In particular, shared cross-dock never automatically implies venda a
ordem. An unresolved material scenario remains under specialist review and
fails closed.

### Financial allocation and settlement extension

The accepted Financial Trace Ledger and Financial Reconciliation remain the
single financial foundation. A future participant-allocation contract may add
supplier, 3PL, Flooow, seller, tax, or other payable/receivable responsibility,
allocation policy, settlement grouping, and lifecycle evidence such as
reported, settled, disputed, and adjusted. It must extend existing immutable
`EXPECTED` and `ACTUAL` evidence rather than create a second Settlement Ledger.

Payables and split execution follow explained economic events, fiscal
eligibility/documents, fulfillment evidence, marketplace settlement, and
reconciliation. Receipt of a marketplace deposit alone never proves why money
belongs to a participant.

### Responsibility and operational liability

Future responsibility policy may relate event type, responsible party, seller
impact, financial-liability policy, evidence requirements, marketplace
deadline, remediation, dispute, and settlement. Software records and applies
approved contractual/legal policy; it does not invent legal liability.

### Fiscal and operational sandbox

Before execution authority widens, one specialist-approved controlled pilot
must exercise sale, cancellation, return, partial return, loss, damage,
invoice rejection, inventory divergence, seller restriction, supplier
failure, and marketplace/integration failure. The sandbox captures ownership,
custody, inventory, fiscal, shipment, settlement, return, reconciliation,
accounting, and outcome evidence.

The first candidate pilot is deliberately narrow: one importer, one 3PL, one
seller, one SKU, one marketplace, one operating mode, one origin/destination
path, and one approved fiscal policy. Approval evidence must cover the happy
path and cancellation before invoice, cancellation after invoice, return,
damaged return, lost shipment, stock divergence, invoice rejection,
marketplace refund, and financial reconciliation before scale increases.

Operational evidence should be capable of retaining order received, pick
started, packed, invoice authorized, ready for pickup, carrier handoff, and
marketplace deadline times. These observations can later support Supplier
Reliability and Seller Reputation Protection without prematurely creating an
opaque score.

## Fulfillment opportunity and OFI integration

Future fulfillment evaluation follows:

```text
performance signal
  -> qualified opportunity
  -> Economic Truth
  -> Operational Truth
  -> Fiscal and Marketplace Eligibility
  -> scenario simulation
  -> recommendation
  -> Policy and Authority
  -> decision and action
  -> reconciled outcome
```

Fulfillment Opportunity Leakage compares current-mode and simulated-mode
incremental contribution, capital, inventory risk, fees, media effects,
returns, operational constraints, confidence, readiness, and decision
deadline. Incremental GMV alone is not value and may correctly yield a
recommendation not to migrate.

When OFI v1.1 is versioned, Supplier Reliability, Inventory Confidence, SLA
Risk, Operational Truth freshness, Fiscal Eligibility, and Marketplace
Eligibility may become qualified inputs or blockers. Score remains distinct
from eligibility and recommendation.

## Protected decisions

### Consignacao remains a hypothesis

Mercantile consignation is only a candidate model pending qualified fiscal,
accounting, and legal validation of the actual ownership, custody, right to
sell, parties, jurisdiction, regime, product classification, warehouse,
marketplace, fulfillment, invoicing, returns, and settlement flow.

Candidate states may include `UNDER_SPECIALIST_REVIEW`,
`APPROVED_WITH_CONDITIONS`, `APPROVED`, `REJECTED`, and
`EXPIRED_OR_SUPERSEDED`. Required unresolved eligibility fails closed.

### Cross-docking is not blocked by sales volume alone

High sales, seller performance, or velocity never automatically prohibits
cross-docking or forces Full/FBA/another fulfillment mode. Migration requires
economic and operational evidence, eligibility, simulation, policy, and
authority. A mandatory transition needs a separately approved safety,
contractual, or operational policy.

Shared cross-dock and dedicated marketplace fulfillment remain distinct
operational, fiscal, marketplace-eligibility, inventory, return, and
reconciliation workflows. Approval of one model never authorizes the other.

### External fiscal providers do not own decision intelligence

Flooow may delegate specialized calculation through provider-neutral adapters,
while retaining orchestration, eligibility, evidence, effective-date and
policy versioning, and decision authority. Replacing a provider must not
require rewriting marketplace workflows or disclose Flooow's proprietary
eligibility and decision logic.

## Readiness gates

A future task may proceed only when it can prove its direct evidence inputs,
ownership, freshness, policy version, deterministic/fail-closed behavior,
reason decomposition, privacy, tests, and boundary from recommendation and
authority.

Initial phases explicitly do not authorize:

- a broad Trust Layer service or generic Operational Truth Kernel primitive;
- an opaque ML reliability score;
- an autonomous stock promise or listing mutation;
- a generic Brazilian tax engine or vendor selection;
- a specific CFOP, CST, NCM, tax treatment, or venda-a-ordem rule as assumed
  truth;
- consignation as the default fiscal model;
- fulfillment coercion from sales volume;
- real escrow, split, or participant payment execution before economic,
  operational, fiscal, marketplace, and reconciliation prerequisites;
- autonomous financial penalties;
- a dashboard as proof of the domain contract.

## Current sequencing decision

This roadmap is recorded now because it materially protects future execution.
The consolidation audit inspected `main` at `54d1545`. It does not interrupt
the active deterministic Marketplace Pricing sequence and authorizes no
production code.

When the inventory roadmap is resumed, inspect the latest `main` and derive one
small contract from the first missing dependency. Do not mechanically implement
the candidate sequence or the entire vertical slice.
