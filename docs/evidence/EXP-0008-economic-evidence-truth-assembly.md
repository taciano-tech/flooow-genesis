# EXP-0008 — Economic Evidence → Economic Truth Assembly

Status: concluded

Decision: Reject

Scope: experimental only; no production implementation, migration, provider behavior, projection schema, API/UI behavior, or new economic semantic is authorized by this document.

## 1. Objective

Determine whether the current canonical MarketplaceIndependentEconomicEvidence contains sufficient information and accepted semantics to deterministically assemble a valid MarketplaceOrder for MarketplaceEconomicTruthCalculator without inventing:

- economic values;
- implicit zero;
- order occurrence time;
- coverage semantics;
- applicability semantics;
- provider meaning;
- correction meaning;
- collection-attempt meaning.

The experiment exists because P0.3 Slice B requires a materialized Sales Intelligence state derived from canonical economic truth, but the currently accepted boundaries do not yet prove this transformation:

MarketplaceIndependentEconomicEvidence
  -> MarketplaceOrder
  -> MarketplaceEconomicTruthCalculator

This EXP does not change the planned MVP sequence.

## 2. Research question

Can current committed independent economic evidence be transformed into MarketplaceOrder using only information and semantics already present in accepted upstream contracts?

The experiment must answer:

1. Is orderOccurredAt authoritatively derivable?
2. Can active component observations become MarketplaceOrder components without semantic transformation?
3. Can observation-level coverageClaim values be authoritatively reduced into order-level coverage?
4. Can economic types with no current evidence family be classified without treating unsupported capability as NOT_APPLICABLE?
5. Can collection attempts influence economic coverage without inventing meaning?
6. Do activeFacts fully resolve correction history for current-state assembly?
7. Can insufficient or conflicting evidence fail closed?
8. Can the resulting MarketplaceOrder be passed to the existing calculator without changing calculator semantics?

## 3. Hypothesis

H1:

The current canonical independent economic evidence model is sufficient to assemble MarketplaceOrder deterministically using only already-accepted semantics.

## 4. Null hypothesis

H0:

The current canonical independent economic evidence model is not sufficient to assemble MarketplaceOrder without introducing at least one new semantic decision.

Supporting H0 is a valid result. The experiment must identify the smallest missing boundary rather than hide it behind an assembler heuristic.

## 5. Core invariants

1. Absence must never become monetary zero implicitly.
2. Absence of an implemented evidence family does not by itself mean NOT_APPLICABLE.
3. NOT_APPLICABLE must describe the economic subject, not a limitation of the software.
4. No timestamp may become orderOccurredAt without evidence that it represents order occurrence.
5. The assembler must not manufacture EconomicComponent values.
6. Existing magnitude, currency, direction, quality, source and occurrence time must be preserved.
7. Historical facts superseded by corrections must not re-enter current economic calculation.
8. Collection attempts are not economic components.
9. NO_EVIDENCE is not automatically MISSING, NOT_APPLICABLE or zero.
10. AMBIGUOUS is not economic evidence.
11. TEMPORARY_FAILURE is not economic evidence.
12. Determinism alone is insufficient; a deterministic rule must also have semantic authority.
13. Conflicting or insufficient input must fail closed.
14. MarketplaceEconomicTruthCalculator remains the authority for final economic calculation.

## 6. Experimental result model

The harness may model:

EconomicTruthAssemblyResult

Ready(
    marketplaceOrder
)

NotReady(
    reasons
)

Initial experimental reasons may include:

ORDER_OCCURRED_AT_UNRESOLVED
COVERAGE_UNRESOLVED
CONFLICTING_COVERAGE
UNSUPPORTED_ECONOMIC_TYPE
INCONSISTENT_ACTIVE_FACTS

These names are experimental diagnostics only.

## 7. Critical boundary

Assembly.NotReady means:

A legitimate MarketplaceOrder cannot be assembled.

MarketplaceEconomicTruthCalculationResult.Incomplete means:

A legitimate MarketplaceOrder exists, but its economic coverage is insufficient for a complete economic result.

The assembler must never alter coverage merely to force execution through the calculator.

## 8. Experimental gates

Gate 0 — harness remains completely outside production code.

Gate 1 — subject identity maps one-to-one:
organizationId, orderId, marketplace, externalOrderId and currency.

Gate 2 — active EconomicComponent facts are preserved without monetary transformation.

Gate 3 — correction chains converge through canonical activeFacts; superseded facts never re-enter current calculation.

Gate 4 — determine whether any existing timestamp is semantically authoritative for orderOccurredAt.

Gate 5 — determine the exact semantic scope of coverageClaim.

Gate 6 — evaluate multiple active facts of the same EconomicComponentType without inventing a reducer.

Gate 7 — determine correct treatment of EconomicComponentType values with no active component, especially FINANCIAL_COST and OTHER_ADJUSTMENT.

Gate 8 — prove whether NO_EVIDENCE has any accepted economic coverage meaning.

Gate 9 — prove that AMBIGUOUS and TEMPORARY_FAILURE cannot manufacture economic truth.

Gate 10 — evaluate whether external identities can legitimately contribute to order identity or order occurrence time.

Gate 11 — only a semantically valid Ready result may be passed to MarketplaceEconomicTruthCalculator.

Gate 12 — use the accepted Economic Truth control fixture to prove that legitimate assembly does not alter economic meaning.

Gate 13 — equivalent canonical evidence must produce equivalent assembly regardless of legal insertion order.

Gate 14 — unresolved, conflicting or unsupported states must fail closed.

## 9. Candidate outcomes

Outcome A — current model sufficient.

Evidence
  -> deterministic assembler
  -> MarketplaceOrder
  -> MarketplaceEconomicTruthCalculator

Outcome B — current model partially sufficient.

The experiment must list only the missing boundaries.

Outcome C — current model structurally insufficient.

No heuristic production assembler may be created.

## 10. Evidence requirements

Every gate must record:

- fixture or production contract used;
- expected result;
- observed result;
- PASS, FAIL or INCONCLUSIVE;
- exact semantic conclusion.

Implementation convenience is not evidence.

## 11. Stop conditions

EXP-0008 may not:

- modify production Kotlin;
- modify accepted evidence types;
- modify MarketplaceEconomicTruthCalculator;
- add fields to MarketplaceEconomicEvidenceSubject;
- modify repository find/apply;
- modify the Slice A change feed;
- modify checkpoint semantics;
- modify V015 or V016;
- create V017;
- create the Sales Intelligence projection;
- create Mercado Livre behavior;
- create Omie behavior;
- create API/UI behavior;
- change the planned MVP sequence.

## 12. Relationship to P0.3 Slice B

TASK-0146 remains paused while EXP-0008 is unresolved.

The planned production direction remains:

durable independent economic evidence
  -> incremental change feed
  -> canonical economic truth assembly
  -> economic truth calculator
  -> durable Sales Intelligence projection
  -> fast local API/UI read path

EXP-0008 does not authorize replacing the materialized Slice B read path with synchronous evidence reconstruction.

## 13. Decision gate

EXP-0008 may conclude only as:

Accept
Reject
Continue Investigation

Until one of those outcomes is documented and reviewed, TASK-0146 production implementation remains unauthorized.


## 14. Execution

Experimental harness:

`research/experiments/exp-0008-harness`

Execution command:

`.\gradlew -p research\experiments\exp-0008-harness test`

Final observed result:

`BUILD SUCCESSFUL`

The experiment used the production marketplace economic domain contracts directly.
No production Kotlin, migration, persistence schema, provider, API, UI, or projection implementation was modified.

## 15. Gate results

| Gate | Result | Evidence conclusion |
| --- | --- | --- |
| 0 | PASS | Harness remained isolated under research/experiments. |
| 1 | PASS | Subject preserves organization, order, marketplace, external order and currency identity, but contains no authoritative order occurrence time. |
| 2 | PASS | Active EconomicComponent facts retain canonical monetary value, currency, ownership and component identity without transformation. |
| 3 | PASS | Corrections preserve history while activeFacts converges to the replacement fact. Superseded facts do not re-enter current state. |
| 4 | PASS | Current evidence may contain multiple economically meaningful timestamps, while no accepted contract identifies one as MarketplaceOrder.occurredAt. |
| 5 | PASS | Observation-level COMPLETE/PARTIAL claims can coexist and no accepted order-level coverage reducer is encoded. |
| 6 | PASS | Multiple active source facts of the same EconomicComponentType may legitimately coexist. Deterministic aggregation alone would not establish coverage semantics. |
| 7 | PASS | Current evidence families represent REVENUE, MARKETPLACE_COMMISSION, MARKETPLACE_FEE, SHIPPING, ADVERTISING, TAX and PRODUCT_COST, but cannot represent FINANCIAL_COST or OTHER_ADJUSTMENT components. |
| 8 | PASS | NO_EVIDENCE remains a collection attempt and does not manufacture a component, zero, MISSING or NOT_APPLICABLE classification. |
| 9 | PASS | AMBIGUOUS and TEMPORARY_FAILURE attempts do not alter active economic facts. |
| 10 | PASS | External identity timestamps coexist with component timestamps and do not establish an authoritative order occurrence timestamp. |
| 11 | PASS | MarketplaceOrder requires semantic information that current independent evidence cannot fully supply without new rules. |
| 12 | PASS | Accepted Economic Truth control fixture remains exact: gross revenue 299.90, contribution 64.81, contribution margin 0.21610537 and CONFIRMED truth quality. |
| 13 | PASS | MarketplaceOrder and calculator output remain deterministic under legal component insertion-order changes. |
| 14 | PASS | Missing coverage produces Incomplete and structurally invalid coverage is rejected. The boundary fails closed. |

## 16. Findings

The current model is not sufficient to assemble MarketplaceOrder using only already-accepted semantics.

Three unresolved semantic boundaries are sufficient to reject H1:

1. ORDER_OCCURRED_AT_UNRESOLVED

MarketplaceEconomicEvidenceSubject has no order occurrence timestamp.
Component and external-identity observations carry occurrence timestamps, but the accepted contracts do not establish any of them as the canonical MarketplaceOrder.occurredAt.
Choosing revenue occurrence time, earliest component time, identity time, or any other deterministic timestamp would introduce new semantic meaning.

2. COVERAGE_UNRESOLVED

MarketplaceEconomicComponentObservation carries a local COMPLETE or PARTIAL coverageClaim.
MarketplaceOrder requires exactly one coverage classification for every EconomicComponentType.
The current contracts do not define how multiple local claims become one authoritative order-level classification.

3. UNSUPPORTED_ECONOMIC_TYPE

The current evidence-family contract cannot represent FINANCIAL_COST or OTHER_ADJUSTMENT components.
Their absence therefore cannot legitimately be converted to NOT_APPLICABLE.
NOT_APPLICABLE describes the economic subject and must not be inferred from an implementation capability gap.

## 17. Hypothesis evaluation

H1: REJECTED.

The current canonical independent economic evidence model is not sufficient to assemble MarketplaceOrder deterministically using only already-accepted semantics.

H0: SUPPORTED.

At least one new upstream semantic decision is required before a production Economic Evidence -> MarketplaceOrder assembler can be authorized.

The calculator itself is not the gap.
The accepted MarketplaceEconomicTruthCalculator continues to behave deterministically and fail closed once a legitimate MarketplaceOrder exists.

## 18. Decision

Decision: Reject

EXP-0008 rejects the hypothesis that the current evidence contract is already sufficient for canonical economic truth assembly.

No heuristic production assembler is authorized.
No synchronous read-path shortcut is authorized.
TASK-0146 remains paused.

The next governed step is the smallest upstream semantic contract correction required to resolve:

- authoritative order occurrence time;
- authoritative order-level coverage derivation;
- semantic treatment of economic types that current evidence families cannot represent.

After those boundaries are accepted, the assembler can be specified and TASK-0146 can resume without changing the planned MVP sequence.
