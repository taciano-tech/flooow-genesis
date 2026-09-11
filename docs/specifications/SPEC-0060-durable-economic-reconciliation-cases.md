# SPEC-0060: Durable Economic Reconciliation Cases

Status: Accepted

Source decision: ADR-0060

## Authorized slice

1. Add immutable case contracts and a deterministic processor under the
   existing Marketplace reconciliation package.
2. Persist cases in PostgreSQL with additive migration V024, organization
   isolation, unique `(organization, trace, policy)` identity, revision guard,
   operator indexes and restart-safe reconstruction.
3. Expose authenticated organization-scoped list/detail reads at
   `/v1/reconciliation/cases` and `/v1/reconciliation/cases/{caseId}` with
   opaque HMAC-bound keyset cursors.
4. Extend the Economic Decision Room with truthful case status and stage
   evidence references.

## Contract

Only `DIVERGENCE` assessments are eligible. A case contains case/organization,
order/trace and policy identities, status, microsecond timestamps, currency,
absolute difference summary, stage details and immutable evidence-entry
references. `OPEN -> ACKNOWLEDGED -> RESOLVED` is the only lifecycle exposed by
this slice; no recovery or financial side effect exists.

Missing sides remain missing and are not stored as zero. Economic Truth and
financial ledger rows remain canonical and immutable. Requests never provide an
organization or connection identifier; the authenticated principal supplies
organization scope.

## Non-goals

No live ingestion wiring, scheduler, provider call, recovery, refund, claim,
payment mutation, marketplace mutation, AI, recommendation or autonomous
action is included.
