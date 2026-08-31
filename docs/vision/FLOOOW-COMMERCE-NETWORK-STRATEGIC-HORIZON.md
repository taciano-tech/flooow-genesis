# Flooow Commerce Network - Strategic Horizon

Status: Approved innovation memory; no implementation authorized

Recorded: 2026-08-31

Source class: strategic benchmark, operating hypothesis, and external expert
input

## Purpose and authority

This document preserves strategically valuable ideas from operating businesses,
technology benchmarks, competitor patterns, external CTO reviews, and Flooow's
own commercial experience. It exists so that future product and architecture
work can reuse validated market learning without copying another company's
architecture or losing the reasoning that made an idea relevant.

The current GitHub `main`, Constitution, accepted ADRs, and accepted
specifications remain authoritative. This document is permanent consultation
material, not a parallel roadmap, a feature backlog, or permission to write
production code.

Before a material new epic, domain boundary, external integration, execution
capability, or major roadmap change is accepted, its authors must consult this
document and other applicable vision/research material. The resulting ADR or
specification must record which ideas were reused, adapted, rejected, deferred,
or require new evidence.

External success is evidence that a problem or pattern may matter. It is not
proof that the same threshold, workflow, data model, vendor, or technology is
correct for Flooow.

Required progression:

```text
benchmark or expert input
  -> problem and evidence classification
  -> repository capability search
  -> business validation
  -> legal, fiscal, security, and payment validation where applicable
  -> architecture decision
  -> accepted specification
  -> small implementation task
  -> measured outcome
  -> organizational learning
```

## Strategic thesis

A possible future category is a **Distributed Commerce Operating Network**.
It is larger than a seller dashboard: Flooow may coordinate supply, capital,
catalog, inventory, seller access, fulfillment, fiscal flows, payments,
returns, risk, and decision intelligence across a governed network.

Potential participants include importer, manufacturer, distributor, supplier,
seller or reseller, marketplace, 3PL or logistics operator, carrier, regulated
PSP or financial institution, consumer, and Flooow as the orchestration and
decision-governance layer.

The network hypothesis is:

```text
supplier exposes catalog, commercial terms, and eligible inventory
  -> seller is evaluated under explicit policy
  -> seller receives bounded allocation or pre-reservation
  -> seller publishes through eligible channels
  -> consumer buys
  -> payment is authorized or reserved by a regulated provider
  -> fiscal and fulfillment eligibility is established
  -> supplier or 3PL ships
  -> consumer, return, dispute, and risk conditions mature
  -> participant settlement becomes eligible
  -> reconciliation verifies the actual result
  -> evidence improves future policy and decisions
```

Physical, commercial, operational, fiscal, marketplace, and financial flows
remain different even when they refer to the same order.

## Inventory and allocation hypothesis

Physical stock is not automatically seller sellable quantity. Candidate states
or projections include:

- physical stock;
- source-reported sellable stock;
- available to network;
- allocated;
- pre-reserved;
- reserved;
- committed;
- picked;
- shipped;
- delivered;
- return in transit;
- quarantined;
- restocked;
- written off.

Physical possession, title or ownership evidence, inventory recognition,
network availability, seller entitlement, channel ATP, published quantity, and
customer commitment do not collapse into one field.

A hybrid operation may combine seller-owned stock with supplier-network stock.
The accepted Trust roadmap remains authoritative for Operational Truth,
Inventory Confidence, Safe ATP, allocation policy, Seller Entitlement, and
published-inventory lineage.

## Seller trust and allocation

Seller trust must not be reduced to a credit score. Candidate evidence includes
GMV, conversion, cancellations, returns, fraud or chargebacks, claims,
marketplace reputation, account age, allocation use, reservation-to-sale
conversion, payment behavior, fiscal accuracy, operational SLA, and
concentration risk.

Possible policy-controlled consequences include:

- catalog eligibility;
- allocation limit and maximum exposure;
- reservation duration;
- prepayment or reserve requirements;
- settlement timing;
- future credit or advance eligibility.

Every future score is decomposable, historized, versioned, and evidence-backed.
It is an assessment input, never authority by itself. No fixed threshold is
accepted merely because a benchmark uses it.

## Supplier, logistics, and product risk

Candidate supplier evidence includes stock accuracy, fill rate, defect rate,
dispatch SLA, fiscal accuracy, cancellation, price stability, return causality,
and integration health.

Candidate 3PL evidence includes pick accuracy, dispatch time, damage or loss,
label and document errors, and reverse-logistics SLA. Candidate product
evidence includes returns, defect, fraud, shipping damage, and warranty claims.

Future eligibility and risk policy may combine seller, supplier, product,
fulfillment, payment, and order evidence without turning correlation into
legal liability or automatic punishment.

## Commercial and fulfillment models

`dropshipping = true` is not an adequate model. Candidate operating models
include:

1. traditional B2B: supplier sells a lot and the seller owns and fulfills it;
2. supplier dropship: seller sells while supplier stores and ships;
3. 3PL dropship: supplier retains applicable economic interests while a 3PL
   holds and ships;
4. seller-owned inventory held by a 3PL;
5. allocation or pre-reservation followed by purchase triggered by a sale;
6. hybrid seller-owned and supplier-network stock.

Each accepted model must explicitly state title, custody, inventory-recognition
party, risk-bearing party, sale-right holder, reservation holder, fiscal seller,
fulfillment responsibility, return responsibility, and settlement policy.

## Payment and settlement direction

Flooow must not commit prematurely to the legal term `escrow`. The preferred
architecture hypothesis is that Flooow explains and governs economic and
settlement policy while a regulated PSP or financial institution performs
custody and movement of funds.

Potential provider-reported states include authorized, captured, held or
reserved, settlement eligible, settled, refund pending, refunded, disputed,
chargeback, reserve held, reserve released, settlement reversed, and negative
balance.

Settlement values derive from canonical economic events, contracts, fiscal and
fulfillment evidence, marketplace settlement, and reconciliation. UI arithmetic
or receipt of one marketplace deposit never establishes participant ownership
of money.

Consumer resolution remains separate from internal liability allocation. No
universal `D+7` split rule is accepted. Applicable delivery, cooling-off,
return, dispute, fraud, warranty, product, seller, supplier, logistics, and
reserve conditions require legal and payment validation before production.

## Returns and disputes

A return is a case with lifecycle and evidence, not one order status. Candidate
facts include order and item identity, reason, consumer or policy basis,
eligibility, authorization, label, tracking, receipt, inspection, condition,
responsibility, refund, inventory disposition, fiscal adjustment, settlement
adjustment, and closure.

Future design must retain at least these exceptional scenarios:

1. cancellation before shipment;
2. cancellation after invoice and before collection;
3. refused or failed delivery;
4. lost shipment;
5. partial delivery or partial return;
6. multi-supplier or multi-location order;
7. exchange instead of refund;
8. non-resellable returned item;
9. fraudulent return or wrong item;
10. chargeback after delivery;
11. seller insolvency after settlement;
12. supplier dispute over defect responsibility;
13. repeated logistics failure;
14. seller high-return pattern;
15. high-risk product or category;
16. return after settlement;
17. settlement reversal or negative balance.

## Fiscal triangulation

A possible commercial model may contain supplier-to-seller and
seller-to-consumer fiscal legs while the physical item moves directly from a
supplier or 3PL to the consumer and returns to another controlled location.

Therefore physical, commercial, fiscal, marketplace, and financial flows are
modeled independently and linked by explicit identities and evidence.

No CFOP, CST, NCM, ICMS/ST treatment, interstate rule, venda-a-ordem
classification, consignation model, cancellation rule, or return rule becomes
software truth without qualified specialist approval and applicable versioned
evidence.

## Authority map

Future work must preserve separate authority for:

- catalog;
- inventory evidence and current state;
- allocation and entitlement;
- commercial offer;
- order acceptance;
- fulfillment;
- fiscal eligibility and fiscal evidence;
- payment and settlement;
- return and dispute;
- risk and trust policy;
- Economic Truth;
- recommendation, decision, and execution.

No model, score, provider, or agent owns all of these authorities.

## Network-effect hypothesis

Reliable suppliers may attract better sellers; more capable sellers may
increase supplier demand; more volume may improve logistics economics; more
transactions may produce better evidence; better evidence may improve risk and
allocation policy; and lower risk friction may attract more participants.

This is a hypothesis to measure. It is not guaranteed merely because another
network experienced it.

## Benchmark lessons

Patterns worth continuing to investigate include catalog management, OMS,
payout and settlement, portfolio intelligence, market and competitor
intelligence, Ads intelligence, network governance, exception management, and
recommendations expressed in the operator's language.

Do not copy benchmark fixed thresholds, technology stacks, interfaces, vendor
choices, arbitrary MVP timelines, unbounded automation, or marketing claims.
Kafka, Redis, TimescaleDB, Airflow, NestJS, or any other technology enters the
architecture only after a measured requirement demonstrates that accepted
Genesis foundations are insufficient.

## Consultation and promotion rule

This horizon is reviewed when work materially touches supplier networks,
catalog distribution, distributed inventory, allocation, reservations, seller
trust, 3PL fulfillment, fiscal orchestration, payment, settlement, returns,
disputes, risk, or network economics.

Each reviewed concept is classified as one of:

- `EXISTING`;
- `COMPOSABLE` from accepted Genesis capabilities;
- `MISSING`;
- `NEEDS_RESEARCH`;
- `NEEDS_LEGAL_FISCAL_PAYMENT_VALIDATION`;
- `DEFERRED`;
- `REJECTED_WITH_REASON`.

Promotion from this document requires a new inspection of canonical `main` and
a separately accepted ADR or specification. No code is authorized here.
