# TASK-0109 - Marketplace Pricing Product Cost Basis Selection contract review

## Decision

ADR-0029 and SPEC-0029 define the smallest missing dependency after TASK-0108:
an explicit, caller-owned selection of one cost basis without changing
Net-Back or recommending a price.

## Why this precedes another floor

TASK-0108 intentionally preserves three simultaneous cost truths. A later
pricing scenario cannot safely consume one until the choice is explicit,
versioned, temporally valid, and lineage-preserving.

Moving directly to a new floor would either hide the choice in technical code
or reduce the selected evidence to an untyped money value.

## Boundary choices

- policy names exactly one historical, current, or forward basis;
- policy version records the upstream organizational rule;
- assessment reuse has an explicit bounded age;
- current evidence is rechecked for freshness at selection time;
- forward evidence must still be future at selection time;
- stale evidence fails without automatic fallback;
- successful output retains the complete three-basis assessment;
- selected evidence quality and complete assessment quality remain separate;
- selection states no objective, optimality, authority, or action.

## Deliberate deferral

The contract does not:

- derive basis choice from objective, inventory, demand, promotion, or Ads;
- rebuild or mutate a `NetBackPricingProfile`;
- replace a `PRODUCT_COST` component;
- calculate or compare a new floor;
- recommend, approve, or execute a price;
- persist or expose a selection through API/UI;
- add AI, agents, infrastructure, or Kernel language.

## Sequence preserved

```text
economic truth and reconciled finance
  -> deterministic Net-Back floors
  -> market evidence and diagnostic position
  -> historical/current/forward cost-basis evidence
  -> explicit cost-basis selection
  -> later lineage-preserving scenario application
  -> later objective, simulation, recommendation, authority, and outcome
```

## Authorization

Acceptance authorizes TASK-0110 only: pure selection policy, caller selection
time, temporal revalidation, exact lineage-preserving selection, controlled
results, and focused tests inside the Marketplace pricing package.

It authorizes no Net-Back change, recommendation, decision, action,
infrastructure, AI, or Kernel modification.
