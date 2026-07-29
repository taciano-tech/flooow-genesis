# EXP-0001 — Execution Plan

**Status:** In Progress

**Experiment:** EXP-0001 — Kernel Validation

**Target PR:** PR-0026 — Execution Foundation

---

## 1. Objective

Execute and validate the complete reasoning pipeline currently implemented by the Flooow Genesis Kernel:

```text
Observation
→ Evidence
→ Hypothesis
→ Judgment
→ Decision
```

The experiment must remain outside the Kernel and consume only its public APIs.

No change to `:platform:foundation:kernel` is permitted unless experimental evidence demonstrates an actual Kernel insufficiency and a separate architectural decision is accepted.

---

## 2. Execution Principles

- Each step must produce independently reviewable evidence.
- The Kernel must be consumed as a library.
- The experimental application must not duplicate Kernel responsibilities.
- Every completed step must preserve a successful build.
- Determinism must be evaluated with controlled inputs and a controlled clock.
- Experimental failures must be recorded as valid evidence.

---

## 3. Experimental Module

Planned directory: `applications/experiments/marketplace`

Planned Gradle project: `:applications:experiments:marketplace`

The marketplace scenario is only an experimental instrument. It must not introduce domain-specific concepts into the Kernel.

---

## 4. Execution Steps

### E-0001 — Bootstrap Experimental Module

Create the empty experimental application module.

Acceptance criteria: included in `settings.gradle.kts`, recognized by Gradle, compiles successfully, repository build succeeds, and no Kernel source file changes.

**Status:** Pending

### E-0002 — Establish Kernel Dependency

Validate that the experimental module can consume `:platform:foundation:kernel` through public APIs only.

**Status:** Pending

### E-0003 — Create Observation

Record a marketplace order observation using `Observation`, `Identifier`, and `Timestamp`.

**Status:** Pending

### E-0004 — Derive Evidence

Create `Evidence` referencing the observation with explicit `Confidence` and `Timestamp`.

**Status:** Pending

### E-0005 — Define Hypothesis

Create the hypothesis: `The marketplace order can be approved.`

**Status:** Pending

### E-0006 — Execute Deterministic Reasoning

Evaluate the hypothesis through `ReasoningModule.deterministic()` using an `EvaluationRequest` and obtain an `EvaluationResult`.

**Status:** Pending

### E-0007 — Validate Judgment

Validate that the returned `Judgment` references the hypothesis, preserves evaluated evidence, and follows the deterministic reasoning policy.

**Status:** Pending

### E-0008 — Produce Decision

Create the decision: `Approve the marketplace order.` The trace must connect decision, judgment, hypothesis, evidence, and observation.

**Status:** Pending

### E-0009 — Validate Determinism and Traceability

Run the pipeline twice with identical identifiers, inputs, ordering, configuration, and fixed clock. The semantic results must be identical.

Required trace:

```text
Observation
→ Evidence
→ Hypothesis
→ EvaluationRequest
→ Judgment
→ EvaluationResult
→ Decision
```

**Status:** Pending

---

## 5. Global Acceptance Criteria

PR-0026 is complete only when E-0001 through E-0009 are completed, only public Kernel APIs are consumed, no Kernel source file changes, no new universal primitive is introduced, all automated tests pass, `./gradlew build` succeeds, and the results are reproducible.

---

## 6. Explicit Non-Goals

PR-0026 will not introduce persistence, databases, HTTP APIs, user interfaces, message brokers, distributed execution, event sourcing, CQRS, production deployment, or changes to Kernel abstractions.

---

## 7. Completion Checklist

- [ ] E-0001 — Bootstrap Experimental Module
- [ ] E-0002 — Establish Kernel Dependency
- [ ] E-0003 — Create Observation
- [ ] E-0004 — Derive Evidence
- [ ] E-0005 — Define Hypothesis
- [ ] E-0006 — Execute Deterministic Reasoning
- [ ] E-0007 — Validate Judgment
- [ ] E-0008 — Produce Decision
- [ ] E-0009 — Validate Determinism and Traceability

---

## 8. Execution Record

- **Starting commit:** Pending
- **Final source commit:** Pending
- **Executor:** Pending
- **Execution date:** Pending
- **JVM version:** Pending
- **Gradle version:** Pending
- **Build result:** Pending
- **Test result:** Pending
- **Evidence location:** Pending

---

## 9. Final Result

Pending completion of E-0001 through E-0009.
