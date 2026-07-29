# Experimental Protocol

**Version:** 1.0

**Status:** Active

---

# Purpose

This document defines the official experimental protocol of the Flooow Genesis Project.

Every experiment conducted within Flooow Genesis must follow this protocol to guarantee reproducibility, scientific rigor, architectural consistency, and objective evaluation.

No Kernel concept may be promoted without completing this protocol.

---

# Experimental Lifecycle

Every experiment shall follow the sequence below.

Research Question

↓

Hypothesis

↓

Null Hypothesis

↓

Experimental Design

↓

Execution

↓

Evidence Collection

↓

Evaluation

↓

Replication

↓

Architectural Decision

---

# 1. Research Question

Every experiment starts with a question.

The question must be objective, testable and relevant to the Computational Kernel.

---

# 2. Hypothesis

A hypothesis is a statement expected to be validated.

Example:

"The current Kernel primitives are sufficient."

---

# 3. Null Hypothesis

Every experiment must define the opposite hypothesis.

Example:

"The current Kernel primitives are NOT sufficient."

The experiment exists to objectively reject or fail to reject this hypothesis.

---

# 4. Experimental Design

The experiment must describe:

- scope
- assumptions
- constraints
- implementation strategy

---

# 5. Independent Variables

List every variable intentionally modified during the experiment.

---

# 6. Dependent Variables

List every variable expected to change.

---

# 7. Controlled Variables

List every condition kept constant during the experiment.

---

# 8. Evidence Collection

Evidence must be:

- reproducible
- observable
- objective
- traceable

Opinion is not evidence.

---

# 9. Evaluation

Compare observed evidence against the expected hypothesis.

---

# 10. Replication

Another engineer must be capable of reproducing the experiment independently.

If replication fails, the experiment is inconclusive.

---

# 11. Success Criteria

An experiment succeeds only if the hypothesis is supported by objective evidence.

---

# 12. Failure Criteria

Failure is considered a valid scientific result.

Failure produces knowledge.

---

# 13. Architectural Decision

Architectural Decisions may be:

- Accept
- Reject
- Continue Investigation

Only accepted decisions may influence the Kernel.

---

# Kernel Evolution Rule

Kernel

↓

Experiment

↓

Evidence

↓

Decision

↓

Kernel Evolution

The Kernel evolves only through experimentally validated evidence.
