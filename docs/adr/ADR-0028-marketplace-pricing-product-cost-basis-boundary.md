# ADR-0028: Marketplace Pricing Product Cost Basis Boundary

Status: Proposed

Date: 2026-08-14

## Context

Economic Truth and Net-Back already accept `PRODUCT_COST`, but that component
does not distinguish three different economic facts:

```text
historical acquisition cost
current replacement cost
forward replacement cost
```

Using one value for all three can make future pricing appear profitable only
because it is anchored to inventory purchased under past conditions. Unsafe
shortcuts would overwrite historical truth, treat a quote as an invoice, hide
FX/freight assumptions, compare different commercial units, or feed a forecast
directly into pricing authority.

## Decision

Introduce a pure, production-inactive Pricing Product Cost Basis projection in
the Marketplace pricing package. It consumes caller-selected cost evidence and
one explicit temporal policy and produces either a complete three-basis
snapshot or typed missing-basis evidence.

It performs no sourcing, landed-cost calculation, FX conversion, quote
selection, persistence, Net-Back mutation, recommendation, decision, or action.

## Explicit cost bases

```text
HISTORICAL_ACQUISITION
CURRENT_REPLACEMENT
FORWARD_REPLACEMENT
```

The bases coexist. None overwrites another.

Historical acquisition is evidence of what the normalized unit cost was in
the past. Current replacement is the accepted cost applicable to replenishment at
evaluation time. Forward replacement is an accepted estimate applicable after
evaluation time.

The projection does not claim that historical cost is accounting inventory
valuation, that a current quote is executable, or that a forward cost will
occur.

## Cost evidence

Each evidence value freezes:

```text
organization and scenario
marketplace
caller-supplied evidence ID
normalized commercial unit key
cost basis
non-negative exact unit cost
source provenance
source occurrence time
economic applicability time
evidence quality
assumption-set version
```

The assumption-set version is mandatory for every basis. For historical facts
it identifies the normalization/allocation rules used. For current and forward
facts it identifies the quote, landed-cost, FX, freight, tax, allocation, or
forecast assumptions established upstream.

The value contains no supplier name, credential, SKU, purchase order,
container, exchange rate, freight formula, or free-form assumptions.

## Common ownership and unit

A complete snapshot requires exactly one evidence item for each basis. All
three must share organization, scenario, marketplace, currency, and normalized
unit key. IDs and `(basis, source system, external reference)` facts are unique.

Cross-currency conversion and unit conversion are explicitly upstream. The
projection never normalizes or rounds evidence.

## Temporal policy

A versioned policy supplies:

```text
maximum current-replacement age
maximum forward horizon
```

The caller supplies microsecond-precision `evaluatedAt`; the projection reads
no clock.

All source occurrence times must not be in the future. Current replacement
source time and applicability time must fall inside the inclusive maximum-age
window. Applicability must satisfy:

```text
evaluatedAt - maximumCurrentReplacementAge <= current applicableAt <= evaluatedAt
historical applicableAt <= current applicableAt
evaluatedAt < forward applicableAt <= evaluatedAt + maximumForwardHorizon
```

Invalid temporal evidence fails closed. It is never silently dropped or
reclassified.

## Exact cost trajectory

A complete snapshot derives:

```text
current change from historical = current - historical
forward change from current = forward - current
forward change from historical = forward - historical
```

All deltas are exact signed money. No percentage, FX conversion, inflation
adjustment, tolerance, or rounding is calculated.

## Missing and invalid evidence

An absent basis returns `MissingCostBasis` with the exact missing basis set and
no snapshot or delta. Duplicate bases, ownership/unit mismatch, currency
mismatch, duplicate evidence, or temporal violation return typed redacted
failures.

An explicit zero is present evidence. Absence is never interpreted as zero.

## Evidence quality

A complete snapshot is confirmed only when all three cost facts are confirmed;
otherwise it is estimated. This is data quality, not forecast accuracy, model
confidence, decision confidence, or authority.

## No pricing recommendation

The result does not select which basis Net-Back should use, change a floor,
recommend a price, approve a purchase, allocate capital, or execute a supplier
or marketplace action. Those require later policy, objectives, simulations,
authority, and outcomes.

## No infrastructure or Kernel change

This boundary adds no migration, repository, API, connector, ERP/marketplace
ingestion, event, worker, scheduler, UI, AI, model, agent, or Kernel vocabulary.

## Consequences

### Positive

- past, current, and forward cost truths cannot overwrite one another;
- unit, currency, provenance, assumptions, and applicability remain explicit;
- stale current quotes and invalid forward horizons fail visibly;
- exact cost trajectory becomes available before pricing optimization;
- forecast evidence cannot masquerade as confirmed historical truth.

### Negative

- upstream callers must normalize the unit and establish landed-cost inputs;
- the first slice requires one selected fact per basis for a complete snapshot;
- no range, probability distribution, sensitivity, supplier comparison, or
  price recommendation is produced.

## Alternatives considered

### Replace PRODUCT_COST with current replacement cost

Rejected because it destroys historical economic truth.

### Put all cost types in one nullable record

Rejected because absence, zero, source, applicability, and quality would become
ambiguous.

### Calculate landed cost inside this projection

Rejected because FX, freight, duties, MOQ, allocation, and import assumptions
require an independent contract.

### Add cost-basis primitives to the Kernel

Rejected because the demonstrated vocabulary remains specific to Marketplace
Pricing and Supply Intelligence.

## Authorization

This ADR alone authorizes no implementation. SPEC-0028 may authorize only pure
cost evidence values, temporal policy, validation, exact deltas, controlled
results, and focused tests for TASK-0108.

It authorizes no persistence, Net-Back change, recommendation, decision, AI,
or Kernel modification.
