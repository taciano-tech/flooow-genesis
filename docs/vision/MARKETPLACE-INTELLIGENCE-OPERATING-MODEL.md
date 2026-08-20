# Marketplace Intelligence - Executive Operating Model

Status: Approved target direction

Recorded: 2026-08-14

Capability enrichment recorded: 2026-08-14 (TASK-0115)

Trust and fulfillment direction recorded: 2026-08-20 (TASK-0124)

Trusted-commerce consolidation recorded: 2026-08-20 (TASK-0125)

## Nature of this document

This document is a permanent target operating model for Flooow Marketplace
Intelligence. It guides future product and architecture decisions, but does not
authorize any implementation by itself.

Accepted ADRs and specifications remain sovereign over individual increments.
The current repository roadmap must be inspected before every new task so that
approved vision never causes an older capability to be rebuilt.

The seven leadership domains below are organizational lenses. They are not a
mandate to create seven services, packages, agents, or autonomous executives.

## Invariants

- Flooow Genesis remains the Organizational Computing platform.
- Marketplace Intelligence remains a vertical built on public Genesis
  contracts.
- Marketplace-specific language does not enter the Kernel merely to accelerate
  this vertical.
- Deterministic economic truth precedes prediction, recommendation, and AI.
- Evidence retains identity, provenance, source time, quality, and applicable
  policy version.
- Insufficient or incompatible evidence fails closed where a decision could be
  unsafe.
- Monetary calculations remain exact and explicitly rounded only by accepted
  policy.
- Recommendation, authority, decision, execution, reconciliation, outcome, and
  learning remain distinct stages.
- No specialist, model, or agent has sovereign execution authority.

## Leadership domains

### Marketplace Business Director

Owns consolidated economic performance: revenue, contribution, contribution
margin, capital employed, channel mix, forecast, risks, opportunities, and
cross-domain priorities.

The Director manages economic exceptions and decisions, not individual SKUs,
Ads bids, listings, or orders.

### Economics and Pricing

Protects and expands unit economics through Economic Truth, Net-Back, economic
floors, target economics, channel economics, landed and replacement cost,
competitive position, elasticity, markdown, and margin policy.

Net-Back is deterministic mathematics. Pricing recommendation is a later
intelligence and decision layer and must not be folded into Net-Back.

### Growth and Retail Media

Buys profitable growth through ROAS, ACoS, TACoS, paid/organic attribution,
contribution after Ads, incrementality, and marginal return.

ROAS is not sovereign. Economic contribution is the final economic truth.

### Commerce Operations and Digital Shelf

Protects presence, conversion, and listing health across catalog, attributes,
content, visibility, availability, reviews, delivery promises, suppression,
conflicts, and unauthorized sellers.

Sales deterioration must not automatically cause price or Ads changes. Price,
inventory, delivery, shelf, catalog, reviews, media, and competition remain
separate hypotheses until evidence supports a cause.

### Supply, Inventory, and Capital

Allocates inventory and working capital across coverage, stockout, excess and
dead stock, reorder, suppliers, sourcing, imports, GMROI, inventory turns, cash
conversion, and capital rotation.

The long-term question is where the next unit of capital produces the best
risk-adjusted economic return.

Operational Truth, Inventory Confidence, Dynamic Available-to-Promise,
Supplier Reliability, Seller Reputation Protection, Fiscal Orchestration, and
safe fulfillment execution remain governed by the protected roadmap in
`docs/roadmap/MARKETPLACE-TRUST-OPERATIONAL-FULFILLMENT.md`.

### Returns, Reconciliation, and Recovery

Protects money after the sale across returns, refunds, disputes, fee and
shipping reversals, settlement, reconciliation, divergence, and recovery.

A return is not complete when the item comes back. The case completes only when
its operational and financial consequences are resolved.

### Foresight and Opportunity

Anticipates demand, trend, seasonality, competitor behavior, supply-demand
gaps, saturation, external events, product opportunity, import opportunity,
and decision deadlines.

The operating model must progress from explaining what happened, to estimating
what is likely, to identifying what the organization should begin before the
event occurs.

## Capability enrichment registry

This registry enriches the leadership domains above. It is not an alternative
roadmap, implementation sequence, service decomposition, or authorization to
create domain types. Repository reality remains authoritative for when each
capability becomes eligible.

Before any item below becomes an ADR, specification, task, module, agent, or
automation, the latest `main`, merged PRs, accepted decisions, specifications,
evidence, implementations, and tests must be inspected. Existing terminology
and boundaries are extended rather than duplicated.

### Retail Media and profitable growth

Growth and Retail Media must progress from isolated advertising metrics to
economic allocation. Target evidence includes:

- ROAS, ACoS, TACoS, CPC, CTR, conversion, paid sales, organic sales, and the
  paid/organic mix;
- Contribution Before Ads, Contribution After Ads, Incremental Contribution,
  Marginal ROAS, Marginal Contribution, Ad Waste, and budget efficiency;
- average performance kept distinct from the economic performance of the next
  unit of spend;
- inventory-, margin-, promotion-, supply-, and price-aware media context;
- paid-to-organic observations and experiment context without assuming
  causality from event order;
- marketplace search terms as structured analytical evidence, including
  generic, product, brand, competitor, long-tail, compatibility, and regional
  terms;
- governed Brand Defense and Conquesting hypotheses where channel policy
  permits them.

Traffic and ROAS are not final objectives. The long-term allocation question
is where the next monetary unit of media spend produces the strongest
incremental economic contribution. Marginal optimization is ineligible until
reliable historical and experimental evidence exists.

Advertising must not buy demand that the organization cannot fulfill safely.
Critical inventory, delayed replenishment, poor unit economics, elevated
returns, or organic cannibalization can invalidate an otherwise positive media
metric. Conversely, healthy margin, excess inventory, reliable demand, and
positive incrementality can support increased investment.

### Digital Shelf, Catalog, and compatibility

Commerce Operations and Digital Shelf should eventually observe listing
availability and suppression, price, delivery promise, fulfillment, title,
attributes, images, descriptions, technical content, compatibility, reviews,
ratings, visibility, offer competitiveness, catalog conflicts, and
unauthorized sellers where applicable.

Catalog content becomes structured evidence rather than undifferentiated
listing text. Target concerns include missing or contradictory attributes,
duplicates, categorization, technical specifications, claims, imagery, and
content completeness.

Fitment and compatibility are strategically material for technical products
and must not remain only free text. Future evidence may relate a product to
make, model, year, variant, and technical constraints. This evidence can later
support listing generation, search, pre-sale guidance, product matching,
return prevention, and customer support. Motorcycle parts are examples, not
Kernel vocabulary.

Returns must eventually feed Catalog Intelligence through an evidence-based
loop:

```text
return evidence -> investigated cause -> proposed catalog/content change
  -> expected outcome -> actual outcome -> learning
```

A customer-stated return reason is evidence, not automatically the proven root
cause. Investigation provenance must remain intact.

Controlled content experiments may compare title, hero image, technical or
compatibility imagery, video, descriptions, and enhanced content. They retain
hypothesis, baseline, intervention, duration, evidence, and outcome. Higher
CTR is not success when contribution, returns, customer quality, or media cost
deteriorates.

Marketplace Policy and Listing Compliance Intelligence remains governed by
the separate protected roadmap item in
`docs/roadmap/MARKETPLACE-POLICY-AND-LISTING-COMPLIANCE-INTELLIGENCE.md`.
This enrichment does not duplicate or accelerate that capability.

### Product launch and channel intelligence

A future product launch context may retain target channel, price and economic
floor, contribution objective, initial inventory, media budget, search terms,
listing and compatibility readiness, promotion plan, experiment window,
review checkpoints, and explicit scale and stop criteria.

Launch performance compares expected and actual units, revenue, reconciled
economics, media, TACoS, Contribution After Ads, returns, and inventory
consumption. Sales volume alone does not establish launch success. Any launch
state model requires a later bounded contract consistent with repository
conventions.

Marketplace Intelligence is channel-neutral at its economic boundaries and
channel-specific at adapters and evidence acquisition. Mercado Livre, Amazon,
Shopee, future marketplaces, and owned commerce may differ in Ads, ranking,
promotions, fulfillment, fees, catalog rules, APIs, and customer behavior.
Channel-specific semantics do not leak into shared truth without proof.

Future Channel Allocation Intelligence may ask where a limited available unit
is expected to produce the best economic outcome, considering contribution,
demand, conversion, media, logistics, fees, stockout risk, strategic channel
importance, and fulfillment. Equal distribution is not an assumed policy.

Cross-channel observations may generate hypotheses. A successful listing,
search, pricing, or media pattern in one channel is never automatically truth
for another.

### Opportunity, sourcing, import, and expansion

Product Expansion Radar belongs to Foresight and Product Opportunity. Candidate
evidence may include search growth, sales trend, seasonality, competitors,
supply-demand gaps, price stability, margin potential, return risk, sourcing,
lead time, and capital requirement.

The target lifecycle connects, without collapsing distinct domains:

```text
opportunity -> market validation -> sourcing -> FOB/MOQ
  -> import and landed-cost simulation -> replacement cost
  -> contribution and capital analysis -> lead-time fit
  -> governed decision -> purchase/import -> launch
  -> actual reconciled outcome -> learning
```

GO, NEGOTIATE, WAIT, or NO-GO remain decision outputs subject to evidence,
policy, and authority. No product-category example becomes a Genesis primitive.

### Economic objective and coordinated strategy

Optimization requires an explicit objective. Maximize contribution, maximize
margin, grow volume, protect market share, preserve inventory, liquidate excess
stock, improve capital return, support a launch, and defend a strategic channel
can produce different valid actions from the same evidence.

Specialists must not independently issue contradictory sovereign actions. A
future Commerce Strategy coordinates pricing, media, promotion, inventory,
competitor monitoring, and supply context with:

```text
objective
actions by domain
validity and trigger conditions
expected economic impact
required authority
expiration and rollback
```

Commerce State supplies context to that process; it is not decision authority.

### Leakage, opportunity, situations, and memory

Economic Leakage is approved as a future view of money lost or exposed through
pricing, fees and reversals, returns, media waste, stockouts, promotions,
reconciliation, logistics, or catalog failures. Potential, prevented,
recovered, and unresolved values remain distinct and cannot be calculated
before their financial truth is reliable.

Economic Opportunity is measurable potential upside across pricing, media,
inventory, recovery, sourcing, promotion, product discovery, and channel
allocation. It retains evidence and uncertainty and is not guaranteed revenue
or profit.

Management by Exception should transform high-volume events into economically
prioritized Situations, low-confidence cases, policy violations, material
opportunities, and authority requests. Correlated symptoms must be eligible for
one Systemic Situation when a common rule, fee, channel, logistics model,
category, or time boundary explains them. Alert volume is not value.

Future Decision or Organizational Memory depends on retaining:

```text
situation -> evidence -> objective -> alternatives -> simulation
  -> recommendation -> policy -> authority -> decision -> action
  -> expected outcome -> actual outcome -> difference -> learning
```

The memory is not implemented prematurely, but present designs must not destroy
the lineage required to build it later.

### Evidence and claim discipline

- correlation does not silently become causation;
- marketplace algorithm behavior is observed and versioned, not treated as a
  permanent law;
- marketing claims such as guaranteed sales, rank, Buy Box, conversion, or
  revenue are never encoded as truth;
- predictions, hypotheses, and opportunities declare confidence and evidence
  quality appropriate to their maturity;
- AI may structure, explain, or propose, but cannot manufacture deterministic
  economics or grant itself authority.

### Trust, operational truth, and fulfillment

Future fulfillment execution requires measured operational trust rather than
contractual assumption. Source-reported inventory remains evidence until
accepted authority, freshness, current-state, reservations, unconfirmed
demand, confidence, and versioned Safe ATP policy establish business
availability.

The Trust roadmap does not create a parallel epic sequence. Its protected
decisions keep consignation subject to specialist approval, prohibit automatic
cross-docking/fulfillment coercion from sales volume alone, and preserve
Flooow's fiscal orchestration and eligibility authority when external tax
providers are used.

Trusted Commerce distinguishes permission from occurrence. Fiscal Eligibility
states what an approved, versioned policy permits; Fiscal Truth retains the
documents and events that actually occurred. Economic Truth, Operational
Truth, Marketplace Truth, and Financial Trace/Reconciled Truth remain separate
evidence boundaries and do not become generic Kernel primitives by name.

Financial settlement never defines commercial or fiscal reality. Participant
payables, split, escrow, and settlement execution may be derived only after
explained economic events, eligibility, documents, fulfillment evidence,
marketplace settlement, and reconciliation. Future participant allocation must
extend the accepted Financial Trace Ledger and Reconciliation rather than
create a parallel ledger.

Future distributed inventory keeps physical quantity, reservation, Safe ATP,
Seller Entitlement, Channel ATP, and published inventory distinct. It also
prefers atomic title, custody, inventory-recognition, risk-bearing, sale-right,
reservation-holder, and fiscal-seller facts over one legally ambiguous
`economicOwner` field.

Shared cross-dock and dedicated marketplace fulfillment require separate
operational, fiscal, marketplace, inventory, return, and reconciliation
eligibility. Product fiscal profiles and policy applicability remain versioned
evidence subject to qualified approval; no proposed CFOP, CST, NCM, venda a
ordem, or consignation treatment is accepted as software truth by default.

## Cross-domain target capabilities

The following concepts are approved as future capabilities, not immediate
tasks:

- `Commerce State`: consolidated economic and operational state for one
  commercial context;
- `Situation`: correlated evidence and hypotheses presented as one actionable
  business condition;
- `Systemic Situation`: one common cause explaining many related situations;
- `Commerce Strategy`: coordinated actions with validity, triggers, expected
  impact, and rollback conditions;
- `Management by Exception`: normal analysis is automated while humans receive
  material exceptions and authority requests;
- `Economic Leakage`: money lost or exposed across commerce domains;
- `Economic Opportunity`: measurable upside available across commerce
  domains.

These concepts must not become generic abstractions before concrete vertical
evidence demonstrates their contracts.

## Human and agent operating model

Leadership accountability remains human unless explicit organizational policy
assigns otherwise. An AI capability may assist a leadership domain without
becoming the legal, financial, or strategic office holder.

### Good early agent responsibilities

- continuously observe high-volume evidence;
- detect changes and inconsistencies;
- explain evidence with citations;
- form controlled hypotheses;
- prepare simulations and recommendations;
- identify missing or low-confidence data;
- route exceptions to the appropriate human authority;
- monitor expected versus actual outcome.

### Responsibilities that remain human by default

- corporate and channel strategy;
- legal interpretation and acceptance of legal risk;
- policy ownership;
- material capital allocation;
- irreversible or high-impact commercial decisions;
- final authority for claims, safety information, imports, and disputes;
- accountability for decisions affecting customers, partners, and regulators.

### Agent output contract

Future specialists produce structured output such as:

```text
Observation
Evidence
Hypothesis
Recommendation
Confidence
Financial Impact
Risk
Applicable Policy
Required Authority
```

Genesis continues to govern the path:

```text
Evidence -> Hypothesis -> Evaluation -> Judgment
  -> Policy -> Authority -> Decision -> Action
  -> Expected Outcome -> Actual Outcome -> Learning
```

## Autonomy

Autonomy is proportional to reversibility, financial impact, confidence, and
policy. Early agents observe, explain, and recommend. Execution begins only
with explicit approval. Bounded autonomy may later be accepted for reversible,
low-impact actions with tested guardrails and rollback.

Large purchases, imports, legal claims, safety disclosures, and other material
or irreversible actions remain human-authorized.

## Executive cockpit direction

The future cockpit should answer rapidly:

- Are we making money?
- Are we likely to hit the target?
- What is the largest risk?
- What is the largest opportunity?
- Which decisions require human authority?

Target metrics include revenue, contribution, contribution margin, forecast
versus target, forecast confidence, TACoS, ROAS, leakage, opportunity, recovered
money, risk exposure, and capital exposure. Dashboard volume is not a product
outcome.

## Incremental development method

Every new task follows the repository's established method:

```text
inspect latest main and merged PRs
  -> identify the smallest missing dependency
  -> ADR when a boundary decision is required
  -> accepted specification
  -> contract-review task
  -> implementation and tests
  -> CI
  -> PR and merge
  -> inspect main again
```

A task stops for direction only when it requires a Kernel change, discovers a
major architectural conflict, has genuinely ambiguous requirements, needs a
destructive migration, or requires an irreversible external action.

## Repository checkpoint when recorded

At merge commit `f9d6960`, the repository already contains:

- Marketplace Economic Truth;
- immutable Financial Trace and Economic Ledger;
- deterministic Financial Reconciliation;
- Net-Back absolute and economic floors;
- Economic Price Position;
- matched Competitor Price Evidence and Competitive Price Position;
- serialized concurrent Ledger writes.

The next technical contract remains derived from this state. The approved
operating model does not move the repository backward or authorize a broad
Commerce State, recommendation engine, cockpit, or agent implementation.

## Repository checkpoint when enriched

At merge commit `aee4ec0`, the repository additionally contains:

- Competitive Market Reference and its controlled economic position;
- three historized Product Cost Basis evidences and exact deltas;
- explicit policy selection of one Product Cost Basis;
- mandatory normalized unit identity throughout Net-Back outcomes;
- immutable application of selected Product Cost evidence to a distinct
  derived Net-Back scenario.

The next technical boundary remains derived from this latest state. The
smallest visible dependency is controlled calculation of the already-derived
scenario before any baseline comparison, objective, recommendation, authority,
or action. This capability enrichment changes neither that conclusion nor the
accepted PR-by-PR method, and authorizes no implementation by itself.

## Repository checkpoint for Trust direction

At merge commit `59fae87`, Marketplace Pricing contains exact cost-basis floor
deltas and an accepted contract for derived-scenario price position. The
inventory path already contains typed source balances, exact canonical
observations, acceptance, measure selection, candidate comparison, and explicit
adjudication.

Source authority/health/freshness, canonical current-state selection,
operational reservations and unconfirmed demand, business availability,
Inventory Confidence, and Safe ATP remain missing. The Trust direction is
therefore recorded without interrupting Pricing or prematurely implementing
the full vertical slice. The first inventory contract must be derived from the
latest repository state when that roadmap becomes active.

## Repository checkpoint for Trusted Commerce consolidation

At merge commit `54d1545`, the protected Trust roadmap already covered
Operational Truth, Inventory Confidence, Safe ATP, graceful degradation,
Supplier Reliability, Seller Reputation Protection, fiscal-provider
sovereignty, sandbox execution, and OFI readiness. Financial Trace and
Reconciliation already provide the immutable ledger foundation.

The consolidation therefore adds only missing boundary clarity: Fiscal
Eligibility versus Fiscal Truth, atomic ownership/custody/right/risk facts,
Seller Entitlement after Safe ATP, participant allocation as an extension of
the existing ledger, versioned fiscal-policy and product-profile direction,
separate shared-cross-dock and dedicated-fulfillment eligibility, and a narrow
exception-complete pilot. It creates neither a new roadmap nor an
implementation authorization.
