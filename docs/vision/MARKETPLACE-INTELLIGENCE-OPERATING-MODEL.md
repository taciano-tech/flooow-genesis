# Marketplace Intelligence - Executive Operating Model

Status: Approved target direction

Recorded: 2026-08-14

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
