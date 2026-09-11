# ADR-0061: Governed Live Reconciliation Case Orchestration

Status: Accepted

Date: 2026-09-08

## Context

ADR-0060 established durable, derived reconciliation cases but deliberately
left live processor wiring out. TASK-0159 supplies that missing application
seam without expanding financial authority.

## Decision

Introduce an explicit `AcceptedFinancialReconciliationAssessment` marker and a
`GovernedReconciliationCaseOrchestrator`. The seam validates organization scope,
uses the existing deterministic processor and durable repository, and persists
only accepted `DIVERGENCE` observations. Identical observations are unchanged;
material observations advance revision. Persistence failure is fail-closed and
safe to retry.

An assessment is not Economic Truth, financial evidence, a reconciliation case,
or recovery authority. `WITHIN_TOLERANCE`/fully reconciled and non-divergent
assessments never create cases. The seam cannot write ledger/evidence, call a
provider, or execute recovery, refund, claim, settlement, payment or
marketplace mutation. Organization comes from the trusted composition boundary,
never an HTTP request.

## Consequences

The runtime composes the seam beside the existing read API. A future producer
must explicitly accept the assessment before invoking it; no synthetic
assessment or scheduler is introduced where the current live pipeline has no
accepted reconciliation output.
