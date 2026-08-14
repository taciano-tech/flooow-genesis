# ADR-0031: Marketplace Net-Back Cost Basis Scenario Application Boundary

Status: Proposed

Date: 2026-08-14

## Context

The Marketplace pricing package now has:

- a complete historical/current/forward Product Cost Basis assessment;
- an explicit, temporally valid selection of one basis;
- an explicit normalized commercial-unit identity on Net-Back profiles.

The next missing dependency is applying the selected unit cost to a Net-Back
scenario without mutating the source profile, losing lineage, reusing the same
scenario identity, or silently combining different units.

## Decision

Introduce a pure, production-inactive Cost Basis Scenario Application
projection in the Marketplace pricing package.

It accepts one source `NetBackPricingProfile`, one completed
`PricingProductCostBasisSelection`, one caller-supplied target scenario ID, one
versioned application policy, and caller-supplied `appliedAt`.

It returns either a complete derived profile with full source lineage or a
typed controlled failure. It does not calculate a floor.

## Derived scenario identity

The target scenario ID must differ from the source profile scenario ID. The
projection never mutates or overwrites the source profile.

Every source component is copied into the target scenario. Component IDs are
retained because they are scoped by scenario and preserve component-role
lineage. No random identity is generated.

## Compatibility boundary

The source profile and cost selection must match exactly on:

```text
organization
source scenario
marketplace
currency
normalized commercial-unit key
```

Any mismatch fails before a derived profile is constructed. No currency or
unit conversion is attempted.

## Supported Product Cost shape

The first application slice requires the source profile to contain exactly one
`PRODUCT_COST` component with:

```text
coverage COMPLETE
direction DEDUCTION
value FixedAmount
```

Zero or multiple components, partial/missing/not-applicable coverage, an
addition, or a revenue-rate Product Cost returns a typed unsupported-shape
failure.

This deliberately avoids inventing allocation across multiple product-cost
facts.

## Exact substitution

The derived Product Cost component retains the source component ID and type,
changes ownership to the target scenario, and uses exactly:

```text
value   = FixedAmount(selection.selectedEvidence.unitCost)
source  = selection.selectedEvidence.source
quality = selection.selectedEvidenceQuality
```

All other components retain ID, type, direction, value, source, and quality,
changing only their scenario ownership to the target scenario.

The derived profile retains organization, marketplace, currency, unit,
quantum, normalization policy, coverage, and contribution target exactly.

## Application time and continued validity

The application policy freezes:

```text
application policy version
maximum selection age
```

The caller supplies microsecond-precision `appliedAt`; no clock is read.
Application requires:

```text
selection.selectedAt <= appliedAt
appliedAt <= selection.selectedAt + maximumSelectionAge
```

The projection also reproduces the selection at `appliedAt` using the original
selected basis and selection-policy age. If the source assessment is now too
old, current evidence is stale, or forward evidence is no longer future, the
application fails. No alternative basis is selected.

## Lineage-preserving output

A successful result retains:

```text
source profile
complete Product Cost Basis selection
original Product Cost component
applied Product Cost component
target scenario ID
derived Net-Back profile
application policy version and maximum age
appliedAt
```

The derived profile alone is not the complete audit record; the application
aggregate is.

## Controlled results

Failures remain typed and redacted:

```text
TargetScenarioReusesSource
OwnershipMismatch
SourceScenarioMismatch
MarketplaceMismatch
CurrencyMismatch
UnitMismatch
SelectionOutsideApplicationWindow
SelectionNoLongerApplicable
UnsupportedProductCostShape
```

No partial derived profile is returned.

## No floor or recommendation

The projection does not invoke `MarketplaceNetBackEconomicFloor`, compare old
and new floors, infer a price objective, recommend a price, approve an action,
or mutate an external system.

## No infrastructure or Kernel change

The boundary adds no persistence, migration, API, connector, event, worker,
scheduler, UI, AI, model, agent, or Kernel vocabulary.

## Consequences

### Positive

- selected cost can enter a Net-Back scenario without losing its evidence;
- source economics remain immutable and reproducible;
- unit, currency, marketplace, ownership, and scenario mismatches fail closed;
- stale current or elapsed forward selection cannot be applied;
- component-role lineage remains deterministic across scenarios.

### Negative

- the first slice supports only one fixed deduction Product Cost component;
- callers must supply a distinct target scenario identity and application time;
- no floor comparison or business recommendation is produced.

## Alternatives considered

### Mutate the source profile

Rejected because historical and baseline economics would be destroyed.

### Reuse the source scenario ID

Rejected because two different cost assumptions would become indistinguishable.

### Replace every Product Cost component with the same value

Rejected because multiple components may represent allocations that require an
independent rule.

### Apply the money value without retaining the selection

Rejected because basis, assumptions, provenance, applicability, and complete
assessment lineage would be lost.

### Calculate the new floor in the same operation

Rejected because scenario construction and economic calculation are separate
auditable stages.

### Put derived-scenario vocabulary in the Kernel

Rejected because the demonstrated semantics remain Marketplace Pricing
vocabulary.

## Authorization

This ADR alone authorizes no implementation. SPEC-0031 may authorize only pure
application-policy values, compatibility and time validation, supported-shape
checking, deterministic derived-profile construction, lineage-preserving
output, controlled results, and focused tests for TASK-0114.

It authorizes no floor calculation, comparison, recommendation,
infrastructure, AI, or Kernel modification.
