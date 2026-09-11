# TASK-0129: Marketplace Net-Back Price Position Transition Contract Review

Status: Contract proposed; no implementation delivered

Date: 2026-08-20

## Repository finding

Inspection of current `main` at `7a38e36` confirms that TASK-0128 retains exact
source and selected-cost derived price-position assessments for one explicit
observed-price fact under complete Product Cost and floor-delta lineage.

No boundary currently gives their ordered pair one canonical transition value.
The smallest safe dependency is an exhaustive factual taxonomy over the two
already-accepted `EconomicPricePosition` values.

## Decision material

ADR-0037 and SPEC-0037 define:

- one complete `NetBackComparablePriceEvidence` as the only input;
- sixteen exact source-to-derived position pairs;
- nested exhaustive classification without ordinals, strings, maps, or
  fallback;
- retention of the exact evidence instance and complete lineage;
- internal reproduction of the transition invariant;
- no new ID, time, source, quality, version, or policy;
- no rank, distance, percentage, materiality, preference, or recommendation;
- no infrastructure, AI, or Kernel change.

## Why sixteen explicit values

A changed/unchanged flag would discard which diagnostic regions are involved.
A numeric rank or terms such as improved/deteriorated would add interpretation
before the repository has an economic objective or policy.

The explicit ordered pairs preserve the complete fact without declaring any
pair desirable. They also make future growth of `EconomicPricePosition` fail
at compilation until the taxonomy is deliberately extended.

## Current target-direction compatibility

This remains deterministic Pricing Intelligence built on Marketplace Economic
Truth, Financial Trace/Reconciliation, and explicit Product Cost lineage. It
does not interrupt the protected Trust / Operational Fulfillment roadmap and
does not activate agents, simulation, policy authority, or autonomous commerce.

The Kernel remains unaware of Marketplace, Product Cost, price-position, and
transition vocabulary.

## Authorization boundary

Acceptance authorizes TASK-0130 only: add the pure transition enum, exhaustive
classifier, controlled aggregate, focused tests, and implementation evidence in
the existing Marketplace pricing package.

Ranking, distance, percentage, materiality, objective, preferred Product Cost
basis, recommendation, authority, execution, persistence, API, UI, AI, and
Kernel changes remain outside the authorized scope.
