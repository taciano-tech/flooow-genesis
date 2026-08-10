# TASK-0052 Compatibility Review

**Date:** 2026-08-10
**Baseline:** `origin/main` at `b65e6d2`
**Scope:** inventory and compatibility decision only; no runtime change

## Result

**PASS — continued parallel operation is required.**

The repository still has one production application on the legacy reasoning
pipeline and one controlled regression consumer on the directional pipeline.
No truthful, lossless projection exists between the two judgment contracts.
This review therefore authorizes neither removal nor deprecation. A production
migration requires a new accepted specification.

## Consumer inventory

| Consumer | Current contract | Decision | Reason |
| --- | --- | --- | --- |
| `InventoryRiskEvaluator` | `ReasoningModule.deterministic`, `EvaluationRequest`, `EvaluationResult`, `Judgment` | Continue legacy operation | It is the only production consumer. Its assessment, decision context, and reasoning trace expose legacy result and judgment types. Migration changes observable application behavior and requires a new specification. |
| `Exp0002BaselineCharacterizationTest` | `DeterministicHypothesisEvaluator`, `EvidenceSet`, `Judgment` | Retain frozen legacy characterization | It is evidence of the direction-loss defect, not a migration target. Changing it would destroy the baseline oracle. |
| `Exp0003HarnessTest` reduction characterization | Legacy evaluator plus experimental directional model | Retain parallel research path | The legacy reduction and experimental engine exist to compare semantics. They must not be collapsed into one implementation. |
| `ProductionDirectionalRegressionConsumerTest` | `DirectionalEvaluationRequest`, explicit `EvidenceRelationship`, named policy, `StructuredJudgment` | Keep directional | TASK-0051 migrated this consumer without projection and reproduced all committed EXP-0003 traces. |
| Kernel unit tests for legacy contracts | Legacy constructors and interfaces | Retain as contract tests | They protect the behavior kept available during parallel operation; they are not external migration candidates. |
| Kernel unit tests for directional contracts | Directional request, validator, policies, evaluator, and structured judgment | Keep directional | They protect the additive API and deterministic semantics accepted by SPEC-0001. |

No additional production or test consumer was found outside these categories.

## Legacy public contract and constructor inventory

The following public production contracts form the legacy path and remain
available without deprecation:

- `EvaluationRequest(Hypothesis, EvidenceSet)`;
- `EvaluationResult(Judgment, EvidenceSet, Timestamp)`;
- `ReasoningEngine.evaluate(EvaluationRequest)`;
- `ReasoningModule.deterministic(ReasoningConfiguration)`;
- `DefaultReasoningEngine(List<EvaluationStrategy>)`;
- `EvaluationStrategy` and `EvaluatorBasedStrategy`;
- `HypothesisEvaluator.evaluate(Hypothesis, EvidenceSet)`;
- `DeterministicHypothesisEvaluator(EvidenceAggregator, ConfidencePolicy, Clock)`;
- `Judgment(Identifier, Identifier, String, Confidence, Timestamp)`;
- `DecisionContext(Hypothesis, EvidenceSet, Judgment)`;
- supporting `EvidenceSet`, `AggregatedEvidence`, `EvidenceAggregator`, and
  confidence-policy contracts.

`Hypothesis` and `EvidenceSet` are shared input concepts, not deprecated legacy
concepts. The incompatibility lies in the absence of explicit relationships in
the legacy request and in the directionless legacy judgment/result.

The directional contracts remain additive and parallel:

- `EvidenceRelationship` and `EvaluatedEvidenceRelationship`;
- `DirectionalEvaluationRequest` and its validated internal form;
- `DirectionalEvaluationResult`;
- explicit `DirectionalEvaluationPolicy` implementations;
- `DirectionalHypothesisEvaluator`;
- `StructuredJudgment`.

## Projection and deprecation decision

### Directional to legacy

Do not publish an adapter. A projection from `StructuredJudgment` to `Judgment`
would discard direction, reason, policy identity, aggregate totals, and retained
evaluated relationships. No current consumer requires that lossy conversion.

### Legacy to directional

Do not publish an adapter. A legacy `EvaluationRequest` contains no relationship
direction, so an adapter would have to invent SUPPORTS, CONTRADICTS, or another
semantic default. SPEC-0001 explicitly rejects a supportive default.

### Deprecation

Do not deprecate the legacy API yet. Deprecation before the only production
consumer has an accepted migration design would create pressure to perform an
unauthorized semantic conversion. Revisit deprecation only after a production
consumer runs directionally and has regression evidence.

## Snapshot inventory

| Snapshot | Semantic role | Decision |
| --- | --- | --- |
| `exp-0001/red-moto-expected.snapshot` | Production vertical-slice output using the legacy reasoning result | Freeze; migrate only under a new production specification and record any intentional output difference. |
| `exp-0001/track-b-workflow-expected.snapshot` | Domain workflow transition output, independent of the reasoning migration | Freeze; no compatibility action. |
| `exp-0002/conflicting-evidence-observed.snapshot` | Observed legacy direction-loss baseline | Freeze permanently as experimental evidence. |
| `fixtures/exp-0003/evidence-relationship-expected.snapshot` | Directional semantic oracle | Freeze and retain as the primary directional regression oracle. |
| `EXP-0003-PRIMARY-OBSERVED.snapshot` | Primary experimental observation | Freeze; it is retained research evidence, not a production serialization contract. |

Generated copies under `build/` are outputs, not repository compatibility
contracts.

## Public documentation inventory

- `applications/marketplace-operations/README.md` correctly describes a local,
  deterministic vertical slice and does not promise directional production
  semantics.
- `ADR-0002`, `RFC-0004`, and `SPEC-0001` correctly describe additive parallel
  operation and prohibit removal before this review.
- EXP-0002 documentation must continue describing the legacy defect as observed;
  it must not be rewritten as current directional behavior.
- EXP-0003 and TASK-0051 documentation correctly distinguish experimental,
  controlled production, and general production consumers.
- `ReasoningModule` KDoc calls the exposed legacy contracts stable. That remains
  accurate for the current parallel-operation stage and is not changed here.

No public document currently instructs general consumers to migrate or claims
that Marketplace Operations uses the directional evaluator.

## Decision for the next specification

A later production migration specification must define, at minimum:

1. how Marketplace observations become explicit evidence relationships;
2. which named policy applies and why;
3. how `InventoryRiskAssessment` and `DecisionContext` represent a structured
   judgment without a lossy projection;
4. expected differences in traces and snapshots;
5. source, binary, and serialization compatibility expectations;
6. staged rollout, rollback, and independent replication.

Until that specification is accepted, both pipelines remain operational and no
legacy symbol, constructor, test, or snapshot may be removed.

## Validation criteria

- inventory covers production, test, and research consumers;
- legacy public constructors and contracts are explicitly classified;
- committed and generated snapshots are distinguished;
- projection, deprecation, and parallel-operation decisions are explicit;
- no production source or runtime behavior is changed;
- repository build passes unchanged.

## Repository validation

The required validation was executed with a repository-local Gradle user home:

```text
./gradlew clean build --rerun-tasks --no-daemon --no-configuration-cache
BUILD SUCCESSFUL
```

No production source, test, fixture, or frozen snapshot was modified by this
review.
