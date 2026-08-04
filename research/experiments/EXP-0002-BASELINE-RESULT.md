# EXP-0002 Baseline Characterization Result

**Date:** 2026-08-04

**Kernel change:** None

**Disposition:** Open a Separate RFC

## Frozen Baseline

The characterization exercised the current `Observation`, `Evidence`,
`EvidenceSet`, `DeterministicEvidenceAggregator`, `WeightedConfidencePolicy`,
`DeterministicHypothesisEvaluator`, and `Judgment` implementations without
modifying the Kernel.

Controlled input and observed output are committed at:

- `applications/marketplace-operations/src/test/resources/exp-0002/conflicting-evidence-input.properties`;
- `applications/marketplace-operations/src/test/resources/exp-0002/conflicting-evidence-observed.snapshot`;
- `Exp0002BaselineCharacterizationTest`.

The complete repository build passed 72 tests with no failures, errors, or
skipped tests.

## Observed Results

| Scenario | Expected direction | Observed conclusion | Semantic match |
|---|---|---|---|
| Supporting evidence only | `SUPPORTED` | `Evidence supports the hypothesis.` | Yes |
| Contradicting evidence only | `CONTRADICTED` | `Evidence supports the hypothesis.` | No |
| Equal conflicting evidence | `UNRESOLVED` | `Evidence supports the hypothesis.` | No |
| Unequal conflicting evidence | `SUPPORTED` | `Evidence supports the hypothesis.` | Superficial only |
| Equal conflict, permuted | `UNRESOLVED` | `Evidence supports the hypothesis.` | No |

Permutation preserved the same confidence and conclusion. Determinism was
observed, but semantic conflict was not represented.

The unequal scenario's conclusion matches the declared direction only by
coincidence: the model averages confidence magnitude and retains no supporting
or contradicting contribution. The result therefore cannot explain how the
conflict affected the judgment.

## Representation Findings

- `Evidence` has no field describing its relationship to a hypothesis.
- `Judgment` has no explicit supported, contradicted, or unresolved direction.
- `DeterministicEvidenceAggregator` averages confidence magnitude only.
- `DeterministicHypothesisEvaluator` emits a constant supportive conclusion.
- Observation descriptions are not interpreted, and the protocol forbids using
  free text as an undocumented polarity API.
- Confidence cannot safely encode direction because its contract represents a
  normalized degree, not support versus contradiction.

## Hypothesis Assessment

The experiment hypothesis is **not supported by the frozen baseline**. Opposite
and conflicting evidence cannot be faithfully evaluated using only the current
executable contracts.

The null hypothesis is **supported by the baseline evidence**: an additional
relationship, interpretation, or evaluation mechanism is required to
distinguish supporting, contradicting, and unresolved evidence.

Independent fresh replication reproduced this assessment completely. The
decision matrix therefore authorizes a separate RFC to evaluate the missing
universal reasoning relationship. This result does not authorize a Kernel
implementation.

## Integrity Notes

- No failing semantic scenario was removed or weakened.
- The characterization test passes by reproducing observed behavior, not by
  asserting that every observed conclusion is correct.
- No application-owned polarity engine was introduced.
- No Kernel source was changed.
- The observed floating-point result `0.6499999999999999` is preserved exactly
  in the snapshot rather than normalized after execution.

## Independent Replication

Independent replication from frozen commit
`7f159edf588f664ea061adc7b28b525238c66f6c` passed the isolated
characterization test (1 test) and a fresh clean full build (72 tests). All five
scenario results and the complete committed snapshot reproduced without a
semantic or test-count divergence.

The replication provenance is recorded in
`EXP-0002-INDEPENDENT-REPLICATION-001.md`.

## Required Next Step

Open a separate RFC for the missing relationship between evidence and a
hypothesis and for explicit judgment direction. Preserve this frozen baseline
as the evidence for the proposal. Do not change the Kernel until the RFC is
evaluated and the Kernel promotion rules are satisfied.
