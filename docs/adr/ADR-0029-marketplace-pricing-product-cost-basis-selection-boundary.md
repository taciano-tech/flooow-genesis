# ADR-0029: Marketplace Pricing Product Cost Basis Selection Boundary

Status: Proposed

Date: 2026-08-14

## Context

TASK-0108 preserves historical acquisition, current replacement, and forward
replacement cost as three independent temporal facts. The complete assessment
deliberately does not decide which fact another pricing scenario should use.

Inferring that choice from collection order, newest timestamp, highest cost,
lowest cost, evidence quality, or the word `PRODUCT_COST` would hide an
organizational policy inside technical code. Feeding a selected value directly
into Net-Back would also discard basis, applicability, assumptions, and source
assessment lineage.

## Decision

Introduce a pure, production-inactive Product Cost Basis Selection projection
inside the Marketplace pricing package.

The caller supplies an explicit versioned policy that names one cost basis and
the maximum age of the source assessment. The projection returns either an
auditable selection retaining the complete assessment and exact selected
evidence or a typed temporal failure.

The projection does not decide that one basis is economically superior. It
only applies the caller's stated policy.

## Explicit policy

The policy freezes:

```text
selection policy version
selected cost basis
maximum assessment age
```

The selected basis is one of:

```text
HISTORICAL_ACQUISITION
CURRENT_REPLACEMENT
FORWARD_REPLACEMENT
```

The version identifies the organizational rule that selected the perspective.
The projection does not infer an objective, time horizon, channel strategy,
inventory posture, promotion, Ads posture, or authority from that version.

## Selection time

The caller supplies microsecond-precision `selectedAt`; the projection reads no
clock. Selection requires the assessment to exist before selection and remain
inside the inclusive policy window:

```text
assessment.evaluatedAt <= selectedAt
selectedAt <= assessment.evaluatedAt + maximumAssessmentAge
```

Overflow or a value outside that window fails closed.

## Basis applicability at selection

Historical acquisition remains a historical fact. It needs no additional
current/future applicability claim beyond the valid source assessment and
selection window.

Current replacement must still be current at `selectedAt`. Its source
occurrence and applicability must both remain inside the inclusive current-age
window already frozen by the source assessment:

```text
selectedAt - assessment.maximumCurrentReplacementAge
  <= current occurredAt <= selectedAt

selectedAt - assessment.maximumCurrentReplacementAge
  <= current applicableAt <= selectedAt
```

Forward replacement must still be future at selection:

```text
selectedAt < forward applicableAt
```

The original forward horizon remains frozen in the complete source assessment.
Selection never extends it.

## Output and lineage

A successful selection retains:

```text
complete Product Cost Basis assessment
selected basis
exact selected evidence
selected evidence quality
complete assessment quality
selection policy version and maximum age
selectedAt
```

The selected evidence must be value-equal to the exact basis slot in the source
assessment. No value is copied into an untyped amount and no source,
applicability, assumption version, unit, currency, or quality is discarded.

The selected evidence quality and complete assessment quality remain separate.
Neither is model confidence, decision confidence, authority, or forecast
accuracy.

## Controlled failure

The projection returns redacted typed failures for:

```text
ASSESSMENT_OUTSIDE_SELECTION_WINDOW
SELECTED_EVIDENCE_OUTSIDE_APPLICABILITY
```

The second result means either current evidence became stale or forward
evidence is no longer future. No fallback basis is chosen automatically.

## No Net-Back mutation

The result is not a `NetBackCostComponent`, does not rebuild a
`NetBackPricingProfile`, and does not calculate a new floor. A later contract
must define how full lineage is retained if a selected cost is applied to a
pricing scenario.

## No recommendation or authority

Selection does not state that a cost is optimal, conservative, profitable, or
approved. It does not recommend a price, choose an economic objective, allocate
capital, approve a purchase, or authorize an external action.

## No infrastructure or Kernel change

This boundary adds no persistence, migration, API, connector, event, worker,
scheduler, UI, AI, model, agent, or Kernel vocabulary.

## Consequences

### Positive

- cost-basis choice becomes explicit, versioned, and auditable;
- selection cannot silently fall back when current or forward evidence ages;
- all three cost facts and their lineage remain available after selection;
- Net-Back and recommendation remain separate downstream stages;
- policy application remains deterministic and clock-free.

### Negative

- callers must own and version the rule that names a basis;
- callers must rebuild stale source assessments rather than reuse them;
- the slice produces no new floor, price, recommendation, or business action.

## Alternatives considered

### Always select current replacement

Rejected because realized analysis and forward planning have different
economic perspectives, and the choice belongs to explicit policy.

### Select the highest cost automatically

Rejected because a conservative-looking heuristic is still an implicit
objective and can produce economically incorrect decisions.

### Pass only the selected money value downstream

Rejected because it discards basis, source, time, unit, assumptions, quality,
and the other cost truths.

### Rewrite the Net-Back profile in this slice

Rejected because product-cost shape, component identity, coverage, lineage,
target objective, and scenario reproduction need an independent contract.

### Add cost selection to the Kernel

Rejected because the demonstrated policy remains Marketplace Pricing
vocabulary.

## Authorization

This ADR alone authorizes no implementation. SPEC-0029 may authorize only pure
selection-policy values, temporal validation, lineage-preserving selection,
controlled results, and focused tests for TASK-0110.

It authorizes no Net-Back mutation, recommendation, decision, action, AI,
infrastructure, or Kernel modification.
