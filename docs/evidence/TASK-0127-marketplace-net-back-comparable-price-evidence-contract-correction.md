# TASK-0127: Comparable Price Evidence Contract Correction

Status: Contract corrected; no implementation delivered

Date: 2026-08-20

## Repository finding

Pre-implementation inspection of ADR-0036 and SPEC-0036 found that their first
controlled-result model distinguished source and derived currency and
price-quantum failures.

Those four public states are not all reachable from a valid
`NetBackCostBasisFloorDelta` and same-fact observation pair:

```text
source profile currency      = derived profile currency
source profile price quantum = derived profile price quantum
source observation price     = derived observation price
```

The first two equalities are guaranteed by
`MarketplaceNetBackCostBasisScenarioApplication`, which constructs the derived
profile with the source currency and source price quantum. The third is required
by the accepted same-fact invariant.

Consequently, after source evaluation succeeds, a distinct derived currency or
price-quantum mismatch cannot occur. Keeping separate public result types would
create semantics that tests could reach only by violating accepted aggregate
invariants.

## Correction

ADR-0036 and SPEC-0036 now define:

```text
EvidenceMismatch
SourceOwnershipMismatch
DerivedOwnershipMismatch
CurrencyMismatch
PriceQuantumMismatch
```

Ownership remains side-specific because callers can explicitly provide a wrong
source or derived scenario while preserving all same-fact fields. Currency and
quantum remain shared diagnostics because both floors and observations share
those properties by construction.

The projection still delegates to `MarketplaceEconomicPricePosition`. Both
generic currency branches map to the one controlled `CurrencyMismatch`, and
both generic quantum branches map to the one controlled
`PriceQuantumMismatch`. No formula, validation rule, or evaluator behavior is
changed.

## Scope preserved

The correction changes no runtime code and does not broaden the accepted
boundary. It still authorizes only:

- one pure dual-assessment projection;
- explicit same-fact evidence;
- exact evaluator delegation;
- complete lineage and invariant retention;
- deterministic fail-closed results;
- redacted rendering and focused tests.

Transition classification, percentage, materiality, preferred cost basis,
recommendation, authority, action, persistence, API, AI, and Kernel changes
remain excluded.

## Authorization boundary

Acceptance of this correction authorizes TASK-0128, not TASK-0127, to implement
the corrected ADR-0036 / SPEC-0036 boundary. No implementation is included in
this task.
