# ADR-0060: Durable Economic Reconciliation Cases

Status: Accepted

Date: 2026-09-08

## Context

ADR-0022 deliberately stops at a deterministic, production-inactive
reconciliation projection. Operators need durable institutional memory when a
projection contains a divergence, without changing Economic Truth or implying
that a marketplace owes money.

## Decision

Introduce an organization-owned, derived reconciliation case in the Marketplace
vertical. A case records the immutable trace/policy identity, deterministic
stage differences, evidence entry references, revision and an explicit
operational lifecycle: `OPEN`, `ACKNOWLEDGED`, `RESOLVED`.

Cases are created only by an explicit processor seam receiving an accepted
`DIVERGENCE` assessment. Within-tolerance and non-divergent assessments create
no case. The identity is deterministic for organization + trace + policy, and
repeated unchanged observations are idempotent. A changed assessment advances
the revision; ledger evidence is never rewritten.

The case is not Economic Truth, evidence, a recovery action, a claim, a refund,
or an outcome. This ADR authorizes only durable persistence, authenticated
read-only API access, deterministic tests and a Decision Room read surface. No
automatic processor wiring, provider call, recovery, financial execution or
operator write endpoint is authorized.

## Consequences

The case boundary survives restart and isolates organizations while preserving
the explicit distinction between missing and observed zero. Future recovery
work can begin from a stable, auditable case without mutating lower layers.
