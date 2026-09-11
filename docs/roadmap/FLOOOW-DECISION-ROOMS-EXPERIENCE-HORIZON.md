# Flooow Decision Rooms Experience Horizon

Status: STRATEGIC HORIZON - NOT AUTHORIZED FOR IMPLEMENTATION

Captured: 2026-09-06

## Purpose

Preserve the product/experience concepts explored through external mockups and
discussion without turning them into implementation scope before the underlying
truth, provider, reconciliation, authority, and intelligence contracts are ready.

The material is a future product-design input, not a frontend specification.

## Core product thesis

Flooow should not become a collection of dashboards by department.

The stronger concept is:

> Flooow is a network of governed decision rooms over one institutional reality.

Different roles see different decision surfaces, but they consume the same
governed evidence, canonical truth, contradiction, authority, action, outcome,
and learning primitives.

The role surface changes. Institutional reality does not.

## Experience invariant

Every decision room should be able to answer, at the appropriate level:

1. What is known to be true?
2. What evidence supports it and how fresh is that evidence?
3. What is contradictory, missing, partial, or unresolved?
4. What is economically or operationally at risk?
5. What hypothesis, scenario, or recommendation is being proposed?
6. What assumptions and uncertainty belong to that proposal?
7. Who has authority to decide or act?
8. What action is permitted, blocked, or requires approval?
9. What actually happened after action?
10. What did the system learn from prediction versus outcome?

## Visual semantic contract to preserve

The future UI should visibly distinguish:

- OBSERVED / source evidence;
- CANONICAL / assembled institutional truth;
- RECONCILED / confirmed actual outcome;
- MISSING / PARTIAL / CONFLICTING / NOT READY;
- INFERRED / model output;
- SIMULATED / scenario;
- RECOMMENDED / proposal;
- AUTHORIZED / permitted action;
- EXECUTED / action taken;
- OUTCOME / measured result.

A prediction, score, confidence value, or recommendation must never visually
masquerade as observed truth.

## Confidence rule

Avoid a single unexplained "confidence score".

Where confidence is useful, expose its drivers, such as:

- evidence coverage;
- freshness;
- source agreement/contradiction;
- provenance quality;
- model uncertainty;
- reconciliation status;
- policy readiness.

Confidence is decision context, not authority.

## Decision-room concepts captured

### Economic Decision Room / CEO

Strong fit with Genesis.

Focus:

- contribution and margin truth;
- economic leakage;
- qualified situations;
- decision readiness;
- evidence freshness;
- high-impact decisions requiring attention.

This is a role-level lens over economic truth, reconciliation, situations, and
governance, not a separate economic model.

### Decision Governance

Very strong fit and likely a cross-cutting platform capability.

The sequence shown in the concept is aligned with Flooow's architecture:

```text
evidence
  -> evaluation
  -> recommendation
  -> authority
  -> decision
  -> action
  -> outcome
```

A recommendation does not grant authority.

"Apply best scenario" or equivalent actions must be gated by policy, authority,
freshness, safety envelope, and rollback semantics.

### Observatorio de Contradicoes

Very strong concept.

Contradiction should be considered a cross-domain primitive, not merely an
operations dashboard.

Potential contradiction families include:

- ERP versus canonical inventory;
- canonical inventory versus Safe ATP;
- Safe ATP versus published stock;
- economic evidence versus reconciled actual;
- provider identity conflicts;
- fiscal or payment-state conflicts;
- stale source versus newer source;
- policy expectation versus executable reality.

The product should expose the authority responsible for resolving each
contradiction and the effect of leaving it unresolved.

### Economic Reconciliation & Recovery

Strong fit with existing financial trace, reconciliation, evidence, and economic
truth foundations.

Likely one of the earliest high-value future surfaces after provider ingestion
and reconciliation contracts are operational.

The key product distinction is not merely reporting leakage. It is preserving a
trace from expected value to observed divergence to recovery action to actual
recovered/prevented value.

### Growth & Retail Media Intelligence

Strong future fit after Ads identity/economic evidence is mature.

Prefer:

- contribution after Ads;
- incremental/marginal contribution;
- iROAS or equivalent causal/incremental measures when evidence supports them;
- inventory-safe budget constraints;
- waste and saturation evidence;
- governed allocation proposals.

ROAS alone must not become the optimization objective.

### Market Laboratory / Commercial

Strong future decision surface.

Useful concepts:

- price/competitiveness hypotheses;
- buybox position;
- conversion;
- scenario comparison;
- temporary controlled tests;
- decision envelope.

It should be powered by a Hypothesis/Experiment/Outcome layer rather than by
unguarded sliders that imply causality.

### Digital Shelf & Catalog Intelligence

Makes sense as content-to-outcome intelligence.

Potential future chain:

```text
catalog/content evidence
  -> compatibility/quality hypothesis
  -> governed experiment
  -> conversion / return / support outcome
  -> learned content policy
```

Do not reduce catalog quality to a vanity score. Preserve component evidence and
outcome causality.

### Customer Outcome & Reputation Intelligence

Strong concept if modeled as outcome learning rather than customer-service
dashboarding.

Returns, complaints, reviews, support, delivery incidents, and content issues can
be linked to hypotheses and product/content/fulfillment decisions.

The valuable primitive is the cause-to-outcome learning loop.

### Supply & Capital Intelligence

Strategically attractive but later.

Capital allocation recommendations require mature:

- canonical inventory;
- Safe ATP;
- demand evidence;
- supplier reliability;
- lead-time evidence;
- landed cost;
- economic contribution;
- uncertainty/risk models.

Metrics such as RCAI may be explored later but require an explicit formula,
version, assumptions, and outcome validation before becoming decision authority.

### Launch Readiness Room

Promising as a generic governed gate surface.

Instead of creating a launch-specific parallel engine, model readiness as
versioned gates consuming existing evidence and policies.

Example gates may include economics, inventory, content, compatibility, media,
3PL capacity, fiscal eligibility, returns, and support.

WAIT/GO/NO-GO must be the result of explicit policy, not a UI label.

### Foresight & Opportunity Intelligence

Highly aligned with the long-term opportunity-intelligence thesis, but should
remain downstream of canonical truth.

Potential future inputs include:

- demand/search trend;
- competition saturation;
- sourcing/landed cost;
- lead time;
- return risk;
- capital requirement;
- evidence quality;
- Value of Information.

Opportunity scores must remain explainable evaluation, not authority.

### 3PL Control Tower

Strong fit when Flooow governs commitments rather than attempts to replace every
WMS.

Useful primitives:

- reported versus physically confirmed inventory;
- freshness;
- custody;
- fulfillment responsibility;
- capacity;
- SLA commitment;
- handoff integrity;
- fallback;
- operator contradiction.

The 3PL is a participant in the operating network with explicit commitments and
authority boundaries.

### Own Fulfillment Console

Potentially useful, but strategically different from the other rooms.

Picking, scanning, quantity confirmation, label printing, damage reporting, and
handoff can push Flooow toward WMS execution.

Do not assume this should be native product scope.

Preferred future decision:

- Flooow may orchestrate and govern an existing WMS/warehouse execution system;
- a lightweight native execution console is considered only if it materially
  improves the target customer workflow and does not dilute the operating-system
  thesis.

## Product architecture principle

Do not build thirteen independent products.

Prefer:

```text
shared institutional primitives
  -> evidence / truth / contradiction
  -> situation
  -> hypothesis / scenario
  -> decision readiness
  -> authority / policy
  -> action
  -> outcome / reconciliation
  -> learning

                         |
                         +-> CEO lens
                         +-> Commercial lens
                         +-> Growth lens
                         +-> Operations lens
                         +-> Finance lens
                         +-> Supply lens
                         +-> Catalog lens
                         +-> Customer lens
                         +-> 3PL lens
                         +-> future opportunity lens
```

A decision room is a projection/lens over shared institutional primitives, not a
new source of truth.

## Relative roadmap placement

### Nearer horizon after current P0/P1 gates

Candidate first product surfaces:

1. controlled Sales Intelligence / Economic Decision Room;
2. Economic Reconciliation & Recovery;
3. Contradiction Observatory / integration and data-quality operations;
4. Decision Governance primitives surfaced consistently across the above.

These are closest to contracts already being built.

### Later decision-intelligence horizon

- Growth & Retail Media Intelligence;
- Commercial Market Laboratory;
- Digital Shelf & Catalog Intelligence;
- Customer Outcome & Reputation Intelligence;
- 3PL commitment/control surface.

### Strategic horizon

- Supply & Capital Intelligence;
- Launch Readiness Room;
- Foresight & Opportunity Intelligence;
- native Own Fulfillment Console if separately justified.

## UX quality bar

Avoid:

- dashboard-first implementation;
- KPI card proliferation;
- arbitrary neon/gamification;
- invented confidence;
- black-box scores;
- "AI recommendation" without evidence;
- direct action buttons that bypass authority;
- visually mixing observed and simulated values;
- separate domain truth created by each room.

Prefer:

- situations before dashboards;
- exception and impact prioritization;
- traceable evidence;
- explicit freshness;
- contradiction visibility;
- assumptions visible with scenarios;
- contextual action;
- policy/authority envelope;
- outcome measurement;
- longitudinal learning.

## Revisit triggers

Bring this material back to active design when the relevant gates are closed,
especially:

1. read-only provider activation is production-capable;
2. operational API/read model is ready for a real consumer;
3. reconciliation and decision-readiness contracts are accepted;
4. the first high-value user decision flow is selected;
5. before any frontend/design-system implementation is authorized;
6. before Growth/Opportunity intelligence implementation is authorized.

At that point, use these concepts as inputs to product discovery and contract
design. Do not treat the mockups as accepted screens or data contracts.