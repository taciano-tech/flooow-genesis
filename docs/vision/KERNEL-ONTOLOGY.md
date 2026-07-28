# KERNEL ONTOLOGY

## The Ontology of the Flooow Computational Kernel

**Version:** 0.1  
**Status:** Draft

---

# Purpose

This document defines the ontology of the Flooow Computational Kernel.

The ontology identifies the fundamental concepts of organizational computation, the relationships between those concepts, and the dependency rules that govern them.

While `PRIMITIVE-CONCEPTS.md` defines each candidate primitive individually, this document defines how those concepts form a coherent computational system.

The ontology must remain independent of implementation technologies, storage strategies, programming languages and business domains.

---

# Scope

This document defines:

- Candidate Kernel Concepts
- Conceptual Dependencies
- Allowed Relationships
- Reasoning Direction
- Extension Rules
- Kernel Boundaries

This document does **not** define:

- Database Schemas
- APIs
- Event Formats
- User Interfaces
- Domain Models
- Infrastructure
- Programming Languages

---

# Design Principles

The Kernel Ontology follows six fundamental principles.

## 1. Minimality

Only concepts required for universal organizational computation belong to the Kernel.

## 2. Universality

Every Kernel concept must be applicable to every organizational domain.

## 3. Independence

A primitive concept cannot be reducible to another primitive.

## 4. Non-Circularity

Primitive concepts cannot form circular definitions.

## 5. Technology Independence

The ontology must remain valid regardless of implementation technology.

## 6. Extensibility

The ontology must evolve without invalidating existing concepts.

---

# Candidate Kernel Concepts

Current candidate concepts:

- Entity
- State
- Observation
- Observer
- Evidence
- Knowledge
- Goal
- Decision
- Action
- Outcome
- Constraint
- Relationship

All concepts remain candidates until validated by the Universality Test.

---

# Conceptual Dependency Graph

```text
Entity
    │
    ├──────────────┐
    │              │
    ▼              ▼
Relationship     State
                    │
                    ▼
              Observation
                    │
                    ▼
                Evidence
                    │
                    ▼
               Knowledge
                    │
          ┌─────────┴─────────┐
          ▼                   ▼
        Goal             Constraint
          │                   │
          └─────────┬─────────┘
                    ▼
                Decision
                    │
                    ▼
                  Action
                    │
                    ▼
                 Outcome
                    │
                    ▼
              Observation
```

The graph above represents the current conceptual hypothesis of the Kernel.

Operational feedback loops are permitted.

Conceptual circular dependencies are not.

---

# Primitive Relationships

## Entity

Entity is the root concept.

Every other primitive is directly or indirectly associated with one or more entities.

---

## Relationship

Relationships connect entities.

Relationships cannot exist independently.

Depends on:

- Entity

---

## State

State represents the condition of one or more entities within a temporal context.

Depends on:

- Entity
- Temporal Reference

---

## Observation

Observation records a claim, perception or measurement.

Observation is not truth.

Depends on:

- Observer
- Temporal Reference

---

## Evidence

Evidence represents information that increases or decreases confidence in a claim.

Evidence may originate from one or more observations.

Its final status as a primitive remains under evaluation.

---

## Knowledge

Knowledge represents validated organizational understanding.

Knowledge is supported by evidence and reasoning.

---

## Goal

Goal represents a desired future condition.

Goals influence decisions.

---

## Constraint

Constraints restrict possible decisions or actions.

Examples include:

- Policies
- Regulations
- Resources
- Contracts
- Ethics
- Physical limitations

---

## Decision

Decision selects one possible course of action.

A decision does not necessarily produce an action.

---

## Action

Action represents intentional intervention.

Actions modify organizational reality.

---

## Outcome

Outcome represents the observable consequence of actions.

Outcomes may later become observations.

---

# Ontological Rules

## Rule 1

Every primitive ultimately refers to one or more entities.

## Rule 2

Every State requires temporal context.

## Rule 3

Observation does not imply truth.

## Rule 4

Evidence modifies confidence.

## Rule 5

Knowledge is supported rather than assumed.

## Rule 6

Decisions may exist without execution.

## Rule 7

Actions do not guarantee intended outcomes.

## Rule 8

Outcomes may generate new observations.

---

# Extension Rules

A new primitive may only be introduced when:

1. It is universal.
2. It cannot be derived from another primitive.
3. It is necessary.
4. It is independent.
5. It is technology-independent.
6. It is domain-independent.
7. It preserves the consistency of the ontology.

Otherwise it belongs outside the Kernel.

---

# Open Questions

The following questions remain open:

- Should Observer become a role instead of a primitive?
- Should Evidence remain independent?
- Should Knowledge become a primitive?
- Should Temporal Reference become a primitive?
- Is Goal a primitive or a desired future State?
- Is Constraint independent or derived?
- Should Event exist as a primitive?

These questions must be resolved before the ontology leaves Draft status.

---

# Kernel Status

◯ Draft Ontology

No concept becomes a Kernel Primitive solely because it appears in this document.

Promotion requires explicit architectural review and acceptance.