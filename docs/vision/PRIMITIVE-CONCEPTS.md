# PRIMITIVE CONCEPTS

## The Primitive Concepts of Organizational Computation

**Version:** 0.1
**Status:** Draft

---

# Purpose

This document defines the primitive concepts of the Flooow Computational Model.

A primitive concept is a concept that cannot be derived from another concept inside the model.

Every capability, service, API and application built on Flooow must ultimately be expressible using these primitives.

This document intentionally avoids domain-specific concepts such as Order, Product, Customer or Invoice.

Only computationally universal concepts belong here.

---

# Candidate Primitive Concepts

| Concept | Status |
|----------|--------|
| Entity | Candidate |
| State | Candidate |
| Observation | Candidate |
| Observer | Candidate |
| Evidence | Candidate |
| Knowledge | Candidate |
| Goal | Candidate |
| Decision | Candidate |
| Action | Candidate |
| Outcome | Candidate |
| Constraint | Candidate |
| Relationship | Candidate |

---

> Every candidate must pass the Universality Test before becoming part of the Flooow Kernel.

# Entity

## Definition

An Entity is an identifiable unit of reality that can participate in organizational computation.

An Entity may represent a physical object, a digital artifact, a person, an organization, a process, an event, an AI agent, or any other identifiable construct that participates in reality.

Every observation, state, relationship, decision and action is ultimately associated with one or more entities.

---

## Universality

The concept of Entity is universal.

Every organization, regardless of industry, operates over entities.

Examples include:

- Customer
- Product
- Employee
- Machine
- Warehouse
- Supplier
- Invoice
- Shipment
- Bank Account
- AI Agent

Although these examples differ across domains, they are all specializations of the same primitive concept: Entity.

---

## What Entity is NOT

Entity is not a business concept.

It is not synonymous with Customer, Product or Order.

Those are domain-specific entities.

Entity is the computational abstraction that allows every domain object to be represented consistently.

---

## Dependencies

Entity has no dependency on any other primitive concept.

It is considered one of the foundational concepts of the Flooow Computational Model.

---


## Kernel Status

◯ Candidate Kernel Primitive
---

# State

## Definition

A State is a temporally bounded representation of the conditions attributable to one or more entities within a defined context.

State answers the question:

> How is an entity at a specific instant or during a specific interval?

A State may describe properties, conditions, classifications and relationships that are considered applicable to an entity at a given temporal reference.

---

## Identity and Change

Entity and State are distinct concepts.

An Entity preserves identity across time.

A State represents how that Entity is situated at a particular moment or interval.

For example:

- `Order #48291` is an Entity.
- `Pending Payment` is one of its possible States.
- `Paid` is another possible State.
- The Order remains the same Entity while its State changes.

A change in State does not necessarily create a new Entity.

---

## State and Observation

State is not the same as Observation.

A State represents a condition attributed to an Entity.

An Observation represents a recorded claim, measurement or perception about that condition.

For example:

- State: `Machine temperature = 82°C`
- Observation: `Sensor A reported 82°C at 14:32`

Multiple observations may support, contradict or refine the same State.

State may therefore be reconstructed, inferred or updated from observations without becoming identical to them.

---

## Temporal Reference

Every State requires a temporal reference.

A temporal reference may represent:

- An instant
- An interval
- The current moment
- A historical period
- A projected future period

Without temporal context, two apparently contradictory States may both be valid.

For example:

- `Order #48291 was Pending at 10:30`
- `Order #48291 was Paid at 10:31`

These States are not contradictory because they apply to different temporal references.

---

## Universality

The concept of State is universal.

Every organizational domain reasons about conditions that vary over time.

Examples include:

- The payment status of an Invoice
- The location of a Shipment
- The availability of a Product
- The balance of a Bank Account
- The operational condition of a Machine
- The employment status of a Person
- The confidence level of a Hypothesis
- The progress of a Goal

Although the properties differ across domains, all represent conditions attributable to entities within a temporal context.

---

## What State is NOT

State is not an event.

An event represents something that happened.

State represents a condition that applies during a temporal reference.

State is not an observation.

An observation records or communicates information about a State.

State is not necessarily stored directly.

It may be persisted, reconstructed from events, inferred from observations or projected by a model.

The Flooow Computational Model defines State independently from any persistence strategy.

---

## Dependencies

State depends on:

- Entity
- Temporal Reference

State may be established or revised through Observation, but Observation is not required for State to exist conceptually.

---

## Kernel Status

◯ Candidate Kernel Primitive
