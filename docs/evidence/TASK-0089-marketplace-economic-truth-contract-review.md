# TASK-0089 - Marketplace Economic Truth contract review

## Decision

ADR-0020 and SPEC-0020 adapt EPIC-MKT-001 Engineering Specification v1.0 to the
current repository as the v1.1 contract.

The epic is ready to implement only after contract acceptance. The existing
`applications:marketplace-operations` module owns the domain; no new module or
Kernel concept is needed.

## Required upgrades incorporated

- organization ownership and cross-tenant rejection;
- open marketplace key for connector growth;
- explicit economic direction with non-negative component magnitudes;
- complete/not-applicable/partial/missing coverage instead of implicit zero;
- stable source-system provenance and source-fact duplicate protection;
- confirmed versus estimated truth without confidence scoring;
- exact currency-safe decimal rules and typed non-positive-revenue margin;
- calculation policy version and deterministic canonical component order;
- bounded redacted values following current repository conventions.

## Repository corrections

The execution package's repository review paths are updated conceptually:

- `docs/vision/FLOOOW-THESIS.md` replaces absent root `VISION.md`;
- `research/experiments/EXPERIMENT-PROTOCOL.md` replaces absent
  `RESEARCH-METHODOLOGY.md`;
- `applications:marketplace-operations` replaces absent
  `applications/experiments/marketplace`;
- experiments remain under `research/experiments` and marketplace test fixtures.

## Essence preserved

- Genesis remains the Organizational Computing platform.
- Marketplace Intelligence remains a vertical consumer.
- No marketplace, order, fee, currency, or commission concept enters the
  Kernel.
- Economic truth is deterministic, traceable, explainable, and precedes AI.
- Incomplete data blocks authoritative economic truth.

## Authorization

Acceptance authorizes TASK-0090 only: the pure Marketplace Economic Truth domain
and focused tests inside the existing marketplace application. It authorizes no
Kernel, persistence, API, connector, dynamic rule, recommendation, or AI work.
