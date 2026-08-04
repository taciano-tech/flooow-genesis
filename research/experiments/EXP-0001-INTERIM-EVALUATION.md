# EXP-0001 Final Evaluation

**Evaluation date:** 2026-08-04

**Experiment status:** Completed

**Final disposition:** Preserve the Kernel

## Purpose

This report evaluates the combined Track A reasoning slice and Track B workflow
evidence against the success criteria of EXP-0001. It does not replace
independent replication, complete the experiment, or authorize a Kernel change.

## Evidence Baseline

The evaluated baseline contains:

- the inventory-risk vertical slice and its complete reasoning trace;
- the controlled input fixture and expected semantic snapshot;
- the independent replication procedure;
- boundary scenarios for zero inventory, partial-day coverage, replenishment
  timing, commercial-period limits, and already-achieved goals;
- the accepted RFC-0003 Track B operational baseline;
- executable accepted and rejected inventory transitions;
- immutable snapshots, occurrences, transition traces, interventions, and
  outcomes;
- controlled Track B input and expected semantic snapshot;
- 71 passing repository tests on the recorded execution environment;
- successful repository builds and GitHub Actions runs for the evidence changes;
- no modification to the Kernel while producing the vertical slice.

Track A exercises the Kernel reasoning chain `Observation -> Evidence ->
Hypothesis -> Judgment -> Decision`. Track B exercises the amended operational
workflow as domain-owned SKU identity, temporal inventory snapshots,
occurrences, accepted and rejected transitions, actions, and outcomes.

Repository inspection further established that those four workflow concepts
are absent from the executable Kernel baseline. Their effect on the protocol
and the separation of future evidence tracks are recorded in
`EXP-0001-PROTOCOL-AMENDMENT-001.md`.

## Success-Criteria Assessment

| Success criterion | Status | Current evidence | Remaining evidence |
|---|---|---|---|
| The workflow is completely represented | Supported for the accepted baseline | Track A retains the complete reasoning cycle; Track B represents identity, temporal projections, occurrences, accepted and rejected transitions, action, expected impact, and outcome. | Broader operational completeness remains outside the declared experimental scope. |
| No additional Kernel primitive is required | Supported for this experiment | Both tracks were implemented without modifying the Kernel or adding shadow universal types. | This does not prove sufficiency across other workflows or domains. |
| Valid transitions produce expected states | Supported for the accepted baseline | Receipt and consumption produce immutable later snapshots with the expected quantities and occurrences. | Independent replication must confirm the controlled sequence externally. |
| Invalid transitions are rejected | Supported for the accepted baseline | SKU mismatch, non-forward time, and over-consumption return typed rejections, preserve the prior snapshot, and emit no accepted occurrence. | Additional domain rules may expose limitations in future experiments. |
| Identical inputs produce identical outputs | Supported | Repeated executions are structurally equal, and both tracks reproduce committed semantic snapshots. | Independent replication must confirm the results in another execution context. |
| The complete execution is traceable | Supported for the accepted baseline | Reasoning traces connect evidence to decisions; transition traces connect commands and rules to occurrences, snapshots, or rejections. | External action execution and live-system effects remain outside scope. |
| Decisions can be explained from recorded evidence | Supported for the accepted baseline | Decision context, evaluated evidence, judgment, alternatives, recommendation, action, expected impact, and observed outcome remain distinguishable. | Ambiguous, conflicting, or variable-confidence evidence remains untested. |
| Kernel invariants remain valid | Supported within exercised APIs | All 71 tests pass; the application consumes existing Kernel APIs without modifying them, and domain invariants are enforced outside the Kernel. | The result is bounded to the APIs and scenarios exercised. |
| Another engineer can reproduce the result | Supported | Independent Track A and fresh Track B comparisons passed from commit `effa2c1`; the fresh complete build passed 71 of 71 tests with no semantic divergence. | Replication is bounded to the recorded environment and baseline. |

`Supported` means that the current evidence directly supports the criterion only
within the stated scope. No scoped status is a claim of universality.

## Limitations and Threats to Validity

- The domain slice may be too simple to expose missing Kernel concepts.
- The evaluator is deterministic and uses controlled local inputs; it does not
  exercise external data, concurrency, persistence, or distributed execution.
- Evidence confidence is fixed at `CERTAIN`, so ambiguous or contradictory
  evidence is not represented.
- The evaluator constructs a hypothesis that its own evidence supports; it does
  not compare competing hypotheses or exercise a negative judgment path.
- Track B records an intervention and outcome but does not execute an external
  marketplace action or establish causal attribution.
- Track B uses application-owned identity, snapshots, occurrences, and workflow
  behavior; it does not validate `Entity`, `State`, `Event`, or `Process` as
  universal Kernel primitives.
- The transition scenarios are deliberately small and do not exercise
  concurrency, idempotency, partial failure, or conflicting command sources.
- Implementation-time reproduction was not independent; the separately
  recorded replication 001 later executed both tracks, including a forced
  fresh Track B run and complete build.
- Passing tests demonstrate consistency of the implemented behavior, not the
  universality of the Kernel ontology.

## Final Conclusion

The combined evidence supports deterministic, traceable, and explainable
reasoning and workflow transitions for the accepted Marketplace Operations
baseline without a Kernel change. All nine success criteria are supported
within their declared scope. Independent reproduction matched the committed
Track A and Track B semantic evidence, and the fresh complete build passed all
71 tests without semantic divergence.

According to the experiment decision matrix, the final disposition is
**Preserve the Kernel**.

## Future Evidence Boundary

Future experiments should evaluate broader domains, conflicting evidence,
variable confidence, concurrent commands, partial failures, idempotency, and
live action outcomes. Those are new evidence boundaries rather than unfinished
EXP-0001 criteria.

EXP-0001 is **Completed**. Its result preserves the current Kernel and does not
authorize new primitives or promote draft ontology candidates.
