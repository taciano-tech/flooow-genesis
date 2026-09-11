# EXP-0002 — Conflicting Evidence Evaluation

**Status:** Completed — Null Hypothesis Supported

**Protocol Version:** 1.0

## 1. Objective

Determine whether the current executable reasoning Kernel can represent and
evaluate evidence that supports, contradicts, or leaves a hypothesis unresolved
without application-defined reasoning semantics or a new Kernel concept.

## 2. Motivation

EXP-0001 validated deterministic reasoning and workflow execution using
controlled evidence with fixed `CERTAIN` confidence. It did not exercise
contradictory observations, variable confidence, or an unsupported hypothesis.

The current `Evidence` documentation says that evidence may support or weaken a
hypothesis. Its executable structure records observation references,
confidence, and time, but no explicit relationship between evidence and a
hypothesis. The deterministic aggregator averages confidence values, and the
current evaluator always produces the conclusion `Evidence supports the
hypothesis.`

This experiment must characterize that behavior before any change is proposed.

## 3. Research Question

Can the existing Kernel distinguish supporting evidence from contradicting
evidence and produce a deterministic supported, contradicted, or unresolved
judgment using only its current executable contracts?

## 4. Hypothesis

The current executable Kernel can faithfully evaluate supporting and
contradicting evidence for the same hypothesis without adding application-owned
polarity rules or modifying the Kernel.

## 5. Null Hypothesis

The current executable Kernel cannot faithfully distinguish the effect of
supporting and contradicting evidence for the same hypothesis without an
additional relationship, interpretation, or evaluation mechanism.

## 6. Experimental Scope

The experiment will use a Marketplace Operations replenishment hypothesis:

```text
Replenishment will arrive before the projected stockout.
```

Controlled observations will describe mutually incompatible arrival evidence.
The experiment evaluates Kernel representation and reasoning only. It does not
test live carrier integrations, probabilistic forecasting, source authority,
machine learning, persistence, or autonomous action.

## 7. Frozen Kernel Baseline

The initial execution must use the current implementations of:

- `Observation`;
- `Evidence`;
- `EvidenceSet`;
- `DeterministicEvidenceAggregator`;
- `WeightedConfidencePolicy`;
- `DeterministicHypothesisEvaluator`;
- `Judgment`.

No Kernel source may change during baseline characterization. A failing result
must be recorded before any RFC or implementation proposal is opened.

## 8. Controlled Scenarios

All scenarios evaluate the same hypothesis and fixed clock.

### Scenario A: Supporting evidence only

A confirmed carrier update places replenishment before projected stockout.

Expected semantic direction: `SUPPORTED`.

### Scenario B: Contradicting evidence only

A confirmed carrier update places replenishment after projected stockout.

Expected semantic direction: `CONTRADICTED`.

### Scenario C: Conflicting evidence

One source places replenishment before stockout and another places it after
stockout, with equal confidence.

Expected semantic direction: `UNRESOLVED`.

### Scenario D: Unequal conflicting evidence

Supporting and contradicting evidence use different confidence values.

Expected behavior: the judgment reflects both direction and declared confidence
without discarding either evidence item.

### Scenario E: Permutation

The same evidence set is constructed in multiple insertion orders.

Expected behavior: identical judgment and trace.

## 9. Independent Variables

- semantic direction of each observation;
- evidence-set composition;
- evidence confidence;
- hypothesis confidence;
- evidence insertion order;
- confidence-policy weights.

## 10. Dependent Variables

- judgment direction;
- judgment conclusion;
- judgment confidence;
- evaluated evidence retained in the result;
- deterministic equality across repeated and permuted executions;
- ability to explain the contribution of supporting and contradicting evidence.

## 11. Controlled Variables

- Kernel and application versions;
- hypothesis identity and statement;
- observation timestamps;
- evaluation clock;
- confidence policy;
- test dataset;
- execution environment.

## 12. Experimental Method

1. Encode each scenario using only existing Kernel contracts.
2. Execute the current deterministic evaluator without application-side text
   parsing, polarity flags, or alternate judgment logic.
3. Capture the complete input evidence and resulting judgment.
4. Compare observed judgment direction with the scenario's expected semantic
   direction.
5. Repeat identical inputs and permute evidence insertion order.
6. Record representation gaps and behavioral divergences exactly as observed.
7. Do not reinterpret a constant supportive conclusion as success for
   contradicting or unresolved scenarios.

## 13. Success Criteria

The hypothesis is supported only if all conditions hold:

- supporting-only evidence produces a supported judgment;
- contradicting-only evidence produces a contradicted judgment;
- equal conflicting evidence produces an explicitly unresolved judgment;
- unequal conflicting evidence retains both contributions;
- confidence changes are deterministic and explainable;
- evidence order does not change the result;
- no semantic polarity is inferred by parsing free-text descriptions;
- no application abstraction duplicates universal reasoning responsibility;
- another engineer can reproduce the committed evidence.

## 14. Failure Criteria

The null hypothesis gains support if any condition occurs:

- opposite evidence sets produce the same semantic judgment;
- contradicting evidence is reported as supporting;
- conflict cannot be represented without application-owned polarity;
- an unresolved result cannot be expressed;
- confidence magnitude is treated as semantic direction;
- evidence is discarded or hidden;
- insertion order changes the result;
- the judgment cannot explain how conflict affected it;
- independent replication contradicts the recorded result.

Failure must be preserved as evidence. It must not be fixed inside the baseline
characterization task.

## 15. Evidence Collection

The experiment must commit:

- controlled input fixtures for every scenario;
- observed semantic snapshots from the frozen Kernel;
- expected semantic direction declared independently of the implementation;
- automated characterization tests;
- repeated and permutation results;
- source commit and environment metadata;
- build and test results;
- every representation limitation and divergence.

## 16. Threats to Validity

- observation prose may accidentally become an undocumented reasoning API;
- confidence may be confused with support direction;
- application code may hide a Kernel limitation;
- equal confidence may not imply equal source authority;
- one marketplace scenario cannot establish universality;
- characterization tests may encode current behavior as desired behavior;
- a proposed fix may bias interpretation of baseline evidence;
- cached tests may weaken execution provenance.

## 17. Decision Matrix

| Observation | Decision |
|---|---|
| All semantic directions are representable and reproducible | Preserve the Kernel |
| Direction is expressible by an existing concept used incorrectly | Correct the consumer and add regression evidence |
| A universal reasoning relationship is demonstrably missing | Open a separate RFC |
| The need is specific to replenishment or marketplaces | Keep it in the application |
| Evidence is incomplete or replication is not fresh | Continue investigation |

## 18. Architectural Constraints

- baseline execution must not modify the Kernel;
- confidence must not be used as a hidden polarity flag;
- observation descriptions must not be parsed to determine direction;
- a failing scenario must not be weakened or deleted;
- no missing concept may be promoted without RFC validation;
- no architectural decision may precede recorded baseline evidence;
- independent replication must use fresh task execution.

## 19. Replication Requirement

An independent engineer must run the committed characterization suite and full
build from the recorded commit with task outputs forced fresh. The replicator
must report supporting, contradicting, unresolved, unequal, and permutation
scenarios separately and record every divergence.

## 20. Initial Disposition

**Ready for baseline characterization.**

No conclusion has been reached. The current implementation suggests a testable
representation risk, not a predetermined experiment result.

## 21. Baseline Execution Record

The frozen baseline was executed on 2026-08-04 without a Kernel change. The
controlled scenarios, observed snapshot, and assessment are documented in
`EXP-0002-BASELINE-RESULT.md`.

The characterization observed deterministic output but semantic divergence for
contradicting and unresolved evidence. The experiment hypothesis was not
supported, and the null hypothesis gained baseline support. Independent fresh
replication is required before a final decision or RFC.

## 22. Independent Replication Record

Independent replication was completed on 2026-08-04 from frozen commit
`7f159edf588f664ea061adc7b28b525238c66f6c`. The replicator forced fresh task
execution for the isolated characterization test and the complete build.

- isolated characterization: 1 test passed;
- complete clean build: 72 tests passed;
- committed snapshot: reproduced completely;
- five controlled scenarios: reproduced without semantic divergence from the
  baseline record;
- Kernel or production changes: none.

The complete provenance and environmental notes are recorded in
`EXP-0002-INDEPENDENT-REPLICATION-001.md`.

## 23. Final Assessment

The experiment hypothesis is **not supported**. The current executable Kernel
cannot faithfully distinguish supporting, contradicting, and unresolved
evidence using its existing contracts.

The null hypothesis is **supported** by reproduced evidence. Determinism and
permutation stability are present, but they do not compensate for the missing
semantic relationship: opposite and conflicting evidence produce the same
supportive conclusion, and confidence magnitude cannot encode direction.

## 24. Final Decision

Per the decision matrix, a universal reasoning relationship is demonstrably
missing. The authorized next step is to **open a separate RFC** that evaluates
how evidence relates to a hypothesis and how judgments express supported,
contradicted, or unresolved direction.

This experiment does not authorize a Kernel implementation. Any proposed
concept must satisfy the RFC process and the Kernel promotion rules before code
is changed.
