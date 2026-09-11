# EXP-0003 Final Evaluation

**Date:** 2026-08-04

**Status:** Completed — Hypothesis Supported

**Final disposition:** Prepare an ADR; do not implement yet

## Evidence Basis

The final evaluation uses:

- the frozen EXP-0003 protocol and fixture version 1.0;
- the isolated test-only experimental harness;
- the 101-line primary observed snapshot;
- the primary characterization result;
- context-independent replication from commit
  `29974ca59e6c9e03acdf702e666a16477798c53b`.

The replicator reproduced the complete normalized snapshot, all 24 core traces,
all 46 integrity keys, the neutral reduction result, all three ablations,
structural validation, and the complete 73-test build without a semantic
divergence.

## Hypothesis Decision

The EXP-0003 hypothesis is **supported under the declared experimental
conditions**.

The contextual evidence-to-hypothesis relationship and explicit judgment
direction were sufficient for:

- supporting, contradicting, balanced, unequal, and multi-item evidence;
- strict-conflict and weighted-balance policies using one semantic contract;
- Marketplace Replenishment and Service Reliability fixtures without a
  domain-specific semantic branch;
- one evidence item bearing differently on distinct hypotheses;
- explicit invalid-input handling, deterministic repetition, permutation
  stability, and prose independence;
- complete contribution retention and canonical trace serialization.

The proposed dimensions were also necessary under the frozen ablations.
Removing relationship direction produced multiple possible executable
directions. Removing structured judgment direction made direction unavailable
to a typed consumer. Removing retained relationships violated auditability.

The null hypothesis is not supported by the reproduced evidence.

## Reduction Decision

The neutral reduction oracle returned `FAIL` from three observed collisions in
the current Kernel. Identical public Judgment projections corresponded to
different required directions in C1/C2 under P1 and P2 and in C4/C5 under P2.

Existing contracts therefore did not reproduce all required directions and
complete traces without equivalent new semantics.

## Candidate Classification

| Candidate | Evidence-based classification | Final experimental finding |
|---|---|---|
| Evidence-to-hypothesis relationship | Derived reasoning concept candidate | Contextual relationship is sufficient and necessary in the tested conditions; evidence does not establish a new generic Relationship primitive. |
| Relationship direction | Closed reasoning vocabulary candidate | `SUPPORTS | CONTRADICTS` is portable across the two fixture domains and independent from confidence. |
| Judgment direction | Derived evaluation-result candidate | `SUPPORTED | CONTRADICTED | UNRESOLVED` is required as structured output in the tested conditions. |
| Conflict-resolution policy | Strategy | P1 and P2 share contracts but differ legitimately; neither formula is established as universal. |
| Human-readable conclusion | Explanatory projection | Text is not an executable direction contract. |

This classification does not promote a Kernel primitive. Two fixture domains
provide initial portability evidence, not proof that any candidate is universal
or irreducible as a primitive.

## Decision Matrix Application

The reproduced semantics pass every experimental track, and the candidates are
classified as derived reasoning concepts, closed vocabulary, or strategies
rather than new primitives.

The applicable EXP-0003 decision is:

> **Prepare an ADR; do not implement yet.**

The ADR must decide whether to accept the contextual relationship and structured
judgment direction as reasoning contracts, preserve policy as a replaceable
strategy, and define a staged compatibility plan. It must explicitly reject a
default `SUPPORTS`, confidence polarity, prose parsing, and premature generic
Relationship primitive promotion.

## Governance Boundary

Completion of EXP-0003 authorizes only preparation and review of an ADR.

It does not authorize:

- production Kernel code;
- migration of existing consumers or snapshots;
- primitive promotion;
- selection of P1 or P2 as a universal policy;
- removal of the existing conclusion field;
- application or runtime integration.

Implementation may begin only if a subsequent ADR accepts an architecture and
a specification defines compatibility, migration, and regression evidence.
