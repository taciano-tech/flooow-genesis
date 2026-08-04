# Experimental Framework

## Purpose

Define the official scientific methodology for validating and evolving the Flooow Genesis Computational Kernel.

The purpose of this framework is to ensure that every architectural evolution is supported by reproducible evidence instead of intuition or preference.

---

## Scientific Principles

- The Kernel evolves through evidence.
- Every hypothesis must be experimentally validated.
- Architectural decisions are consequences of experiments.
- Domain applications exist to validate universal concepts.
- Failed experiments are valuable research results.

---

## Experiment Lifecycle

### 1. Hypothesis

Define a clear and falsifiable hypothesis.

### 2. Vertical Slice

Implement the smallest possible application capable of validating the hypothesis.

### 3. Observation

Collect objective evidence during execution.

### 4. Evaluation

Compare the observed evidence against the expected outcome.

### 5. Decision

Accept, reject, or continue the investigation.

---

## Success Criteria

An experiment is considered successful when:

- the hypothesis is validated;
- evidence is reproducible;
- the Kernel remains internally consistent;
- the concept demonstrates universality.

---

## Failure Criteria

An experiment fails when:

- the hypothesis cannot be validated;
- contradictory evidence appears;
- the concept is domain-specific;
- Kernel invariants are violated.

A failed experiment is not considered a project failure.

---

## Kernel Promotion Rules

A concept may become part of the Kernel only if:

1. it has been experimentally validated;
2. evidence demonstrates universality;
3. no existing primitive adequately represents it;
4. the architectural decision is formally accepted.

---

## Relationship with RFCs

RFCs propose ideas.

They do not validate them.

---

## Relationship with ADRs

ADRs record architectural decisions resulting from validated experiments.

---

## Relationship with the Kernel

The Kernel contains only experimentally validated concepts.

---

## Experiment Catalog

| ID | Title | Status |
|----|-------|--------|
| EXP-0001 | Kernel Validation | Completed |
| EXP-0002 | Conflicting Evidence Evaluation | Completed — Null Hypothesis Supported |
| EXP-0003 | Evidence Relationship Validation | Planned — Protocol Review |
