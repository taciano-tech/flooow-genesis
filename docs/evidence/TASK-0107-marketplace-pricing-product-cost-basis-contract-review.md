# TASK-0107 - Marketplace Pricing Product Cost Basis contract review

## Decision

ADR-0028 and SPEC-0028 define the first explicit cost-basis slice before any
pricing recommendation.

Historical acquisition, current replacement, and forward replacement cost are
separate evidence. None overwrites another or automatically changes Net-Back.

## Boundary choices

- Every cost fact retains organization/scenario ownership, marketplace,
  normalized unit, currency, provenance, source time, applicability time,
  quality, and assumption version.
- A complete assessment requires exactly one selected fact per basis.
- Missing bases remain typed absence; explicit zero remains evidence.
- Current replacement freshness and forward horizon are versioned.
- Historical, current, and forward applicability is ordered explicitly.
- Exact signed deltas describe cost trajectory without percentage or rounding.
- Cross-currency, unit conversion, landed-cost formulas, and source selection
  remain upstream.

## Deliberate deferral

The slice does not calculate FX, freight, duties, MOQ, supplier choice,
probability, Net-Back floors, recommended price, capital allocation, authority,
or action.

It adds no persistence, API, connector, worker, AI, agent, or Kernel behavior.

## Sequence preserved

```text
economic truth -> market diagnostics
  -> explicit historical/current/forward cost basis
  -> later cost-basis policy and economic objective
  -> later simulation and governed recommendation
```

## Authorization

Acceptance authorizes TASK-0108 only: pure cost evidence, temporal policy,
complete/missing assessment, exact deltas, controlled results, and focused tests
inside the Marketplace pricing package.

It authorizes no persistence, Net-Back mutation, recommendation, decision,
price action, AI, or Kernel change.
