# EXP-0001 — Kernel Validation

**Status:** In Progress

**Protocol Version:** 1.0

---

## 1. Objective

Validate whether the current Flooow Genesis Computational Kernel is sufficient to model a real organizational process without introducing additional Kernel primitives.

---

## 2. Motivation

The Kernel must evolve only when experimental evidence demonstrates that its existing primitives are insufficient.

This experiment establishes the first evidence-driven validation cycle of the Flooow Genesis Kernel.

---

## 3. Research Question

Can the current Kernel Ontology represent a real organizational workflow while preserving determinism, traceability, explainability, and consistent state transitions?

---

## 4. Hypothesis

The current Kernel primitives are sufficient to represent a real organizational workflow without requiring additional universal primitives.

---

## 5. Null Hypothesis

The current Kernel primitives are not sufficient to represent a real organizational workflow without requiring additional universal primitives.

---

## 6. Experimental Scope

The experiment will implement the smallest executable vertical slice capable of representing a real organizational operation.

The vertical slice must remain outside the Kernel and use only the existing Kernel abstractions.

The experiment will not attempt to validate:

- production scalability;
- distributed execution;
- persistence technology;
- user-interface design;
- marketplace-specific business completeness.

---

## 7. Candidate Vertical Slice

The candidate vertical slice is Marketplace Operations.

Marketplace Operations was selected because it provides a realistic organizational workflow while remaining external to the Kernel.

The application is an experimental instrument, not a source of Kernel concepts.

---

## 8. Kernel Concepts Under Validation

The experiment must exercise the Kernel concepts currently identified as necessary for the workflow:

- Entity
- Event
- State
- Process

No additional primitive may be introduced during the experiment without first recording evidence of insufficiency.

---

## 9. Independent Variables

The independent variables are:

- workflow scenario;
- sequence of accepted events;
- initial entity state;
- process execution path;
- valid and invalid transition attempts.

---

## 10. Dependent Variables

The dependent variables are:

- resulting entity state;
- generated events;
- process outcome;
- execution trace;
- invariant validation result;
- determinism of repeated executions.

---

## 11. Controlled Variables

The following conditions must remain constant:

- Kernel version;
- application version;
- workflow definition;
- initial state;
- event sequence;
- execution environment;
- test dataset.

---

## 12. Experimental Design

The experiment shall:

1. define one minimal organizational workflow;
2. represent its actors and objects as entities;
3. express relevant occurrences as events;
4. model valid state transitions;
5. execute the workflow through a process;
6. reject invalid transitions;
7. record the complete execution trace;
8. repeat the same execution using the same inputs;
9. compare all resulting states, events, and decisions.

The application must not contain alternative abstractions that duplicate Kernel responsibilities.

---

## 13. Expected Evidence

The experiment should produce evidence of:

- deterministic execution;
- complete traceability;
- explainable decisions;
- consistent state transitions;
- invariant enforcement;
- reproducible outcomes;
- clear separation between Kernel and application concerns.

---

## 14. Evidence Collection

Evidence shall include:

- executable automated tests;
- input fixtures;
- expected outputs;
- resulting state snapshots;
- emitted event records;
- execution traces;
- invariant violation records;
- build and test results.

Every evidence item must be associated with the experiment version and the source commit.

---

## 15. Success Criteria

The experiment succeeds when all of the following conditions are satisfied:

- the workflow is completely represented;
- no additional Kernel primitive is required;
- valid transitions produce the expected states;
- invalid transitions are rejected;
- identical inputs produce identical outputs;
- the complete execution is traceable;
- decisions can be explained from recorded evidence;
- Kernel invariants remain valid;
- another engineer can reproduce the result.

---

## 16. Failure Criteria

The experiment fails when any of the following conditions occurs:

- an essential concept cannot be represented;
- identical inputs produce different outputs;
- the execution cannot be completely traced;
- a state transition cannot be explained;
- a Kernel invariant is violated;
- application concerns must be embedded in the Kernel;
- independent replication produces a contradictory result.

Failure is valid scientific evidence and must not be hidden or reclassified as success.

---

## 17. Threats to Validity

Potential threats include:

- selecting a workflow too simple to expose Kernel limitations;
- encoding domain behavior directly into test fixtures;
- confusing application concepts with universal concepts;
- changing multiple variables during the same execution;
- interpreting test success as proof of universality;
- promoting abstractions before independent replication;
- allowing implementation details to influence the ontology.

Each identified threat must be recorded during execution.

---

## 18. Replication Procedure

An independent engineer must be able to reproduce the experiment by:

1. checking out the recorded source commit;
2. using the documented JVM and Gradle versions;
3. executing the documented build command;
4. running the experiment test suite;
5. using the committed input fixtures;
6. comparing generated evidence with the expected evidence;
7. recording any divergence.

Replication is successful only when the observed results match the documented results.

---

## 19. Decision Matrix

| Observation | Decision |
|---|---|
| Kernel is sufficient and evidence is reproducible | Preserve the Kernel |
| A missing universal concept is supported by evidence | Open an RFC |
| An architectural contradiction is identified | Review the Kernel Ontology |
| The limitation is domain-specific | Resolve it in the application |
| Evidence is incomplete or replication fails | Continue investigation |

---

## 20. Architectural Constraints

During the experiment:

- no domain-specific primitive may enter the Kernel;
- no Kernel change may be made solely to simplify the application;
- no architectural conclusion may be based only on opinion;
- no failed result may be removed from the evidence record;
- no Kernel evolution may occur without a separate accepted decision.

---

## 21. Execution Record

To be completed when the experiment begins.

- **Source commit:** The commit containing this execution record
- **Execution date:** 2026-08-04
- **Executor:** Codex, supervised by the repository owner
- **Environment:** Windows, JVM toolchain 21, Gradle 9.4.0
- **Build result:** Success (`gradlew.bat build --no-daemon`)
- **Test result:** 58 tests passed; 0 failed; 0 skipped
- **Evidence location:** `applications/marketplace-operations/src/test` and
  `applications/marketplace-operations/src/test/resources/exp-0001`

### Initial Vertical Slice Observation

The Marketplace Operations application represented the Red Moto inventory-risk
scenario without modifying the Kernel. The execution produced explicit
observations, evidence, a deterministic judgment, calculated inventory
projections, intervention alternatives, a recommendation, expected impact, and
a calculation trace.

Six application tests demonstrated:

- explicit stock coverage, stockout date, shortage duration, and goal exposure;
- traceability from observations and evidence to the recommendation;
- preservation of the complete Observation → Evidence → Hypothesis → Judgment
  → Decision chain through `EvaluationResult` and `DecisionContext`;
- identical results for identical inputs;
- no intervention when replenishment precedes the projected stockout;
- rejection of invalid operational inputs.

This is initial evidence only. Independent replication and the remaining
experiment evaluation are required before an architectural conclusion.

The controlled replication package and independent procedure are documented in
`research/experiments/EXP-0001-REPLICATION.md`. Their preparation does not fill
or satisfy the independent replication record below.

---

## 22. Replication Record

To be completed after independent replication.

- **Replication commit:** Pending
- **Replication date:** Pending
- **Replicator:** Pending
- **Result:** Pending
- **Observed divergences:** Pending

---

## 23. Final Result

Pending experimental execution.

---

## 24. Architectural Decision

Pending evidence collection and independent replication.
