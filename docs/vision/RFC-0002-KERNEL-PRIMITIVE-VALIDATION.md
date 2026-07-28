# RFC-0002

## Kernel Primitive Validation

**Version:** 0.1  
**Status:** Draft

---

# Objective

This RFC defines the validation criteria required before promoting any candidate concept to a Kernel Primitive.

The objective is to ensure that the Flooow Kernel remains minimal, universal and internally consistent.

---

# Motivation

Primitive concepts are extremely expensive architectural decisions.

Every primitive becomes a permanent dependency of the computational model.

Incorrect primitives create unnecessary complexity throughout the system.

Therefore, promotion requires explicit validation.

---

# Validation Criteria

Every candidate primitive must satisfy all of the following criteria.

## 1. Universality

The concept must exist in every organizational domain.

Examples:

✔ Entity

✔ State

✘ Customer

✘ Invoice

---

## 2. Irreducibility

The concept cannot be expressed as another primitive.

If it can be derived from another primitive, it does not belong in the Kernel.

---

## 3. Necessity

Removing the concept must reduce the expressive power of the computational model.

If the model continues to function without it, the concept is not primitive.

---

## 4. Independence

The definition of the concept cannot depend on concepts that are themselves derived.

Primitive concepts define other concepts.

They are not defined by them.

---

## 5. Technology Independence

The concept cannot assume databases, APIs, programming languages, storage strategies or implementation technologies.

---

## 6. Domain Independence

The concept cannot depend on business vocabulary.

Examples of domain concepts include:

- Customer
- Product
- Order
- Invoice
- Shipment

These concepts belong to domain models rather than the computational kernel.

---

## 7. Conceptual Stability

The concept must remain valid for decades.

Temporary implementation trends must not influence the ontology.

---

# Candidate Evaluation

| Concept | Universal | Irreducible | Necessary | Candidate |
|----------|-----------|-------------|-----------|-----------|
| Entity | ✓ | ✓ | ✓ | YES |
| State | ✓ | ✓ | ✓ | YES |
| Observation | ? | ? | ? | REVIEW |
| Observer | ? | ? | ? | REVIEW |
| Evidence | ? | ? | ? | REVIEW |
| Knowledge | ? | ? | ? | REVIEW |
| Goal | ? | ? | ? | REVIEW |
| Constraint | ? | ? | ? | REVIEW |
| Relationship | ? | ? | ? | REVIEW |
| Decision | ? | ? | ? | REVIEW |
| Action | ? | ? | ? | REVIEW |
| Outcome | ? | ? | ? | REVIEW |

---

# Expected Outcome

After this RFC, every concept shall receive one of the following classifications:

- Kernel Primitive
- Candidate Primitive
- Derived Concept
- Domain Concept

---

# Architectural Rule

No new primitive may be introduced without satisfying every validation criterion defined in this RFC.

Architectural consistency takes precedence over implementation convenience.

---

# Future Work

This RFC will be used as the evaluation framework for all future candidate primitives.

Every proposal for a new Kernel concept shall explicitly demonstrate compliance with the validation criteria defined in this document before architectural acceptance.

---

# Status

◯ Draft

This RFC is subject to review and refinement before formal adoption.