# TASK-0142: Durable Independent Economic Evidence Contract Review

Status: Contract accepted; runtime implementation not started

Date: 2026-08-31

## Objective

Derive the smallest P0.2 architecture and implementation contract from current
canonical `main` without writing migration or repository code prematurely.

## Repository state before

- canonical `main`: `a472f55`, merge of TASK-0141 through PR #136;
- TASK-0140 pure evidence aggregate implemented and tested;
- no ADR-0043, SPEC-0042, V015, repository port, Postgres adapter, projection,
  or provider activation existed;
- existing Financial Ledger, canonical inventory, organization lifecycle,
  Postgres transaction, and outbox patterns were available for reuse.

## Inputs reviewed

- ADR-0042, SPEC-0041, TASK-0140 evidence, and implementation;
- MGI convergence P0.2 gate;
- Financial Trace Ledger ADR, specification, migration, adapter, and tests;
- canonical observation and acceptance persistence patterns;
- inventory source commit serialization;
- transactional outbox schema, journal, and delivery runtime;
- local durable-independent-evidence architecture brief as non-authoritative
  supporting material;
- permanent strategic benchmark memory, confirming that Commerce Network does
  not enter this task.

## Decision

Accept ADR-0043 and SPEC-0042 for one append-oriented relational authority with
domain-merger replay, optimistic per-subject version, duplicate-before-stale
classification, atomic outbox notification, and strict isolation from provider,
projection, Economic Truth, Ledger, Reconciliation, API, UI, and Kernel work.

## Reuse and non-duplication

- reuse existing money, source, subject, fact, attempt, correction, and merger;
- imitate Financial Ledger storage qualities without storing evidence in the
  Ledger;
- reuse existing organization lifecycle and outbox tables/runtime;
- keep provider source pages/checkpoints in Connector Runtime;
- make evidence history the replay authority and outbox delivery-only;
- do not create a mutable canonical JSON snapshot.

## Risk decisions

- every new applied update increments one version and emits one event;
- duplicate retry after lost response succeeds even with stale expected version;
- compatible stale write reloads instead of overwriting;
- correction and replacement are one transaction/version;
- malformed history fails closed instead of being repaired silently;
- event payload carries only internal order, version, and change kind;
- the application port owns a narrow redacted ID/version persistence bridge so
  the Postgres module does not require widening the TASK-0140 constructors;
- suspended organizations retain historical reads and exact duplicate replay,
  while every new mutation remains blocked;
- a rejected first update leaves no empty subject root;
- implementation stops if exact replay requires widening TASK-0140 domain API.

## Validation

- repository capability and duplication search completed;
- dependency and authority boundaries reconciled;
- 38 implementation tests and seven allowed files specified;
- no production code, schema, dependency, provider, API, UI, or Kernel change
  belongs to TASK-0142;
- local documentation checks and full build are required before publication;
- PR CI remains the canonical merge gate.

## Next objective

Implement TASK-0143 exactly within SPEC-0042. Do not advance to P0.3,
Mercado Livre, Omie, API, UI, materialization, or decision intelligence.
