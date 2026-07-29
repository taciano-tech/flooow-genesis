# ATOMIC UNIT OF ORGANIZATIONAL COMPUTATION

## Research Investigation

**Version:** 0.1
**Status:** Research Draft

---

# Research Question

What is the atomic unit of organizational computation?

This investigation evaluates whether the Flooow Computational Kernel should be founded primarily on:

- Entity
- Fact
- Claim
- Assertion
- Contextualized Assertion

The purpose of this document is not to modify the Kernel Ontology immediately.

Its purpose is to test competing hypotheses before any foundational concept is promoted, removed or reclassified.

---

# Motivation

The current Kernel Ontology treats Entity as a foundational candidate concept.

However, organizational systems rarely access reality directly.

They receive:

- Messages
- Measurements
- Documents
- Sensor readings
- Human reports
- System events
- External records
- Model-generated conclusions

Each of these communicates something about organizational reality.

This raises a foundational question:

> Does organizational computation operate primarily over entities, or over statements about entities?

The answer affects:

- Identity
- State representation
- Observation
- Evidence
- Knowledge
- Belief revision
- Reasoning
- Decision-making
- Persistence
- Auditability

---

# Competing Hypotheses

## Hypothesis A — Entity-First

Entity is the foundational unit.

```text
Entity
    ↓
State
    ↓
Observation
    ↓
Evidence
    ↓
Knowledge
```

Under this model:

- Entities exist as stable identifiable units.
- States describe entities over time.
- Observations report conditions concerning entities.
- Assertions depend on pre-existing entity identity.

### Strengths

- Stable identity
- Natural domain modeling
- Compatibility with object and relational models
- Simpler references and relationships
- Clear lifecycle representation

### Risks

- May assume direct access to reality
- May hide uncertainty behind stored state
- May treat disputed information as objective truth
- May make provenance secondary

---

## Hypothesis B — Assertion-First

Assertion is the foundational unit.

```text
Assertion
    ↓
Interpretation
    ↓
Entity Construction
    ↓
State Construction
    ↓
Knowledge
```

Under this model:

- The system receives statements about reality.
- Entities are identified or constructed through assertions.
- States are derived from temporally relevant assertions.
- Knowledge emerges from validated and interpreted assertions.

### Strengths

- Native provenance
- Native uncertainty
- Support for conflicting sources
- Strong auditability
- Compatibility with belief revision
- Suitable for distributed and multi-agent systems

### Risks

- Identity reconstruction may become complex
- Querying may be more expensive
- Stable entity references may still be required
- Implementation may become overly epistemic
- Not every organizational operation begins with an explicit assertion

---

## Hypothesis C — Dual Foundation

Entity and Contextualized Assertion are co-foundational.

```text
Entity ←──────────── Contextualized Assertion
   │                           │
   ↓                           ↓
Identity                    Knowledge
   │                           │
   └────────── State ──────────┘
```

Under this model:

- Entity provides stable identity.
- Contextualized Assertion provides epistemic content.
- State is constructed or revised from assertions about entities.
- Neither Entity nor Assertion is completely reducible to the other.

### Strengths

- Preserves identity
- Preserves provenance
- Supports uncertainty
- Supports reconstruction and revision
- Avoids forcing ontology and epistemology into one concept

### Risks

- Two foundational concepts increase Kernel size
- Their boundary must be defined rigorously
- Circular dependencies must be prevented
- Identity creation rules remain necessary

---

# Candidate Concepts

## Entity

An Entity is an identifiable unit of reality that can participate in organizational computation.

Entity answers:

> What is being referred to?

Entity provides continuity across changes in State.

---

## Fact

A Fact is a proposition accepted as true within a context.

Fact answers:

> What is currently treated as true?

A Fact may later be revised if its supporting basis changes.

Because truth may depend on context, authority and time, Fact may be a status assigned to a Claim or Assertion rather than an independent primitive.

---

## Claim

A Claim is a proposition communicated by an actor or source.

Claim answers:

> What does a source say is true?

A Claim may be:

- True
- False
- Unverified
- Contested
- Superseded
- Partially supported

Claim requires provenance.

---

## Assertion

An Assertion is a proposition represented within the computational system.

Assertion answers:

> What proposition has been formally registered?

An Assertion may represent:

- A human claim
- A sensor reading
- A system-generated statement
- A model inference
- An imported external record

An Assertion does not imply truth.

---

## Contextualized Assertion

A Contextualized Assertion is an Assertion accompanied by sufficient metadata to support interpretation, validation and reasoning.

A candidate structure is:

```text
ContextualizedAssertion {
    subject
    predicate
    object
    source
    observedAt
    recordedAt
    context
    confidence
    supportingEvidence
}
```

Not every field must necessarily become mandatory.

The minimum viable structure remains an open research question.

---

# Fundamental Distinctions

## Assertion is not Fact

An Assertion is represented.

A Fact is accepted as true under defined conditions.

```text
Assertion
    ↓ validation
Fact
```

---

## Claim is not Assertion

A Claim is made by an actor or source.

An Assertion is its computational representation.

```text
Claim
    ↓ registration
Assertion
```

---

## Observation is not Assertion

Observation concerns perception, measurement or recording.

Assertion concerns propositional representation.

An Observation may produce one or more Assertions.

An Assertion may also originate from:

- Reasoning
- Imports
- Rules
- Human declarations
- External systems

---

## Entity is not Assertion

Entity provides identity.

Assertion provides propositional content.

Example:

```text
Entity:
Order #48291

Assertion:
Order #48291 has status Paid at 10:31
```

The Entity persists while Assertions concerning it may accumulate, conflict or be superseded.

---

# Evaluation Criteria

Each hypothesis must be evaluated against the following criteria.

## 1. Universality

Does the concept apply across all organizational domains?

## 2. Irreducibility

Can the concept be fully expressed using another concept?

## 3. Identity

Can the model represent stable identity across time?

## 4. Provenance

Can the model represent who or what produced information?

## 5. Temporality

Can the model represent when information applied and when it was recorded?

## 6. Uncertainty

Can the model represent confidence, ambiguity and incomplete information?

## 7. Contradiction

Can the model preserve conflicting statements without corrupting State?

## 8. Revision

Can previous conclusions be revised when new information appears?

## 9. Auditability

Can decisions be traced back to their informational basis?

## 10. Operational Simplicity

Can common organizational operations be expressed without unnecessary conceptual overhead?

## 11. Technology Independence

Does the model remain valid independently of databases, languages and infrastructure?

## 12. Domain Independence

Does the model avoid concepts specific to particular industries?

---

# Test Cases

## Test Case 1 — Payment

Source A asserts:

```text
Order #48291 is Paid at 10:31
```

Source B asserts:

```text
Order #48291 is Unpaid at 10:32
```

Questions:

- Are both statements preserved?
- Which source has authority?
- What is the current State?
- Is the State known or disputed?
- Can the conclusion later be revised?

---

## Test Case 2 — Shipment

Carrier system asserts:

```text
Shipment #811 departed Port A at 08:10
```

Warehouse operator asserts:

```text
Shipment #811 remains at Port A at 08:25
```

Questions:

- Can the system preserve contradiction?
- Can evidence be ranked?
- Can temporal differences resolve the conflict?
- Must the system select one statement immediately?

---

## Test Case 3 — Machine Temperature

Sensor A records:

```text
Machine #14 temperature is 82°C at 14:32
```

Sensor B records:

```text
Machine #14 temperature is 76°C at 14:32
```

Questions:

- Is temperature a State or an Assertion?
- Is the State derived from aggregation?
- How is sensor reliability represented?
- Can the system express uncertainty instead of choosing one value?

---

## Test Case 4 — Contractual Obligation

A signed contract states:

```text
Supplier X must deliver 1,000 units by 30 September
```

Questions:

- Is this an Observation?
- Is this a Claim?
- Is it a Constraint?
- Is it a Relationship?
- Is the contractual source sufficient to establish authority?

---

## Test Case 5 — Inferred Risk

A reasoning model concludes:

```text
Supplier X has a high probability of delay
```

Questions:

- Is this an Assertion?
- Is it Knowledge?
- Is it a Belief?
- What evidence supports it?
- How is model provenance represented?
- Can a later outcome invalidate the inference?

---

# Counterexamples to Entity-First

Entity-first may be insufficient when:

- Identity is uncertain.
- Two systems refer to the same real-world object using different identifiers.
- Multiple sources disagree about State.
- Information exists before entity resolution.
- A statement refers to an unknown or anonymous actor.
- A source reports that something may exist without proving it.

---

# Counterexamples to Assertion-First

Assertion-first may be insufficient when:

- Stable identity is required for legal, financial or operational continuity.
- An entity exists without being observed.
- Actions must target a persistent referent.
- Relationships require durable participants.
- Entity identity cannot be reconstructed reliably from assertions.
- High-volume operations require direct state access.

---

# Preliminary Assessment

The current evidence does not justify replacing Entity with Assertion.

Entity and Contextualized Assertion appear to solve different problems:

- Entity solves identity.
- Contextualized Assertion solves representation, provenance and epistemic uncertainty.

The strongest current hypothesis is therefore:

> Entity and Contextualized Assertion may be co-foundational concepts with distinct responsibilities.

Under this hypothesis:

```text
Entity
    ↓ referenced by
Contextualized Assertion
    ↓ interpreted as
Evidence
    ↓ contributes to
Knowledge
    ↓ informs
Decision
```

State may be understood as a temporally scoped interpretation of Assertions concerning one or more Entities.

This remains a research hypothesis and must not yet be treated as an accepted Kernel rule.

---

# Research Questions

1. Can Entity be derived completely from Assertions?
2. Can Assertions exist without identifiable Entities?
3. Is Contextualized Assertion distinct from Observation?
4. Is Claim a domain role or a Kernel concept?
5. Is Fact a primitive or a validation status?
6. Which fields are mandatory for a Contextualized Assertion?
7. How should confidence be represented?
8. How should source authority be represented?
9. How should contradictions affect derived State?
10. How should assertions be superseded or invalidated?
11. Should State be stored, derived or both?
12. Should belief revision belong to the Kernel or the reasoning layer?

---

# Decision Boundary

This document does not authorize changes to:

- `PRIMITIVE-CONCEPTS.md`
- `KERNEL-ONTOLOGY.md`
- Kernel implementation
- Runtime contracts
- Persistence schemas

Any architectural change resulting from this investigation requires a separate RFC and explicit review.

---

# Expected Outcome

This investigation must produce one of the following conclusions:

1. Entity remains the unique foundational primitive.
2. Contextualized Assertion replaces Entity as the foundational primitive.
3. Entity and Contextualized Assertion become co-foundational.
4. Neither concept is primitive and a different foundation is required.
5. The evidence remains insufficient and further research is required.

---

# Research Status

◯ Open Investigation

No architectural decision has been accepted.