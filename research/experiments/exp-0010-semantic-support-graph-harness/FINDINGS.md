# EXP-0010 — Semantic Support Graph Findings

## Hypothesis

Reference integrity does not imply semantic entailment.

A claim can cite a real evidence reference and still assert something the evidence does not support.

## Gate

Flooow must evaluate support using typed propositions and explicit registered derivation rules.

Free-form narrative is advisory only and must not influence support decisions.

## Success criteria

- unrelated claim + valid evidence reference -> UNSUPPORTED;
- direct fact match -> DIRECTLY_SUPPORTED;
- registered derivation rule + matching governed evidence -> DERIVED_SUPPORTED;
- unknown evidence reference -> deterministic failure;
- unknown derivation rule -> deterministic failure;
- changing narrative text cannot upgrade epistemic support;
- every accepted claim exposes a support graph;
- accepted intelligence remains non-canonical and non-executable.

## Architectural consequence

The safe path is:

governed evidence
-> typed proposition
-> explicit support declaration
-> deterministic validator
-> support graph
-> governed intelligence proposal

This experiment does not claim to solve unrestricted natural-language entailment.
It deliberately narrows trusted reasoning to deterministic Flooow-owned semantics.
