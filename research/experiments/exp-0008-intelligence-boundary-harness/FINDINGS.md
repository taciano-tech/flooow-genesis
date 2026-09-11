# EXP-0008 — Intelligence Boundary Findings

## Status

Technical integration gate: PASS.

Semantic grounding gate: NOT YET PASSED.

## What was proven

The isolated JVM harness successfully executed the complete local inference path:

Flooow-owned contract
→ GovernedIntelligenceOrchestrator
→ read-only governed tool
→ LangChain4j adapter
→ Ollama local runtime
→ llama3.2:1b
→ IntelligenceProposal

The proposal remained explicitly non-canonical and non-executable.

CI-safe tests also passed without requiring a live model.

## Important experimental finding

During the live Ollama test, the governed observation supplied to the model was limited to a contribution margin value and its provenance.

The model nevertheless introduced unsupported assertions about:

- an "industry average";
- "labor costs";
- a supposed relationship between labor costs and contribution margin.

Those assertions were not supplied by the governed projection or read-only tool.

This demonstrates that transport-level grounding is not equivalent to semantic grounding.

A model can correctly receive governed evidence while still inventing explanatory context.

## Architectural consequence

The following invariant is now experimentally justified:

AGENT != TRUTH
AGENT != AUTHORITY

LLM output must remain a hypothesis/proposal until independently supported by governed evidence.

Prompt instructions alone are not an acceptable truth boundary.

## Required next gate

Before production adoption, the intelligence boundary must support evidence-bound claims.

A future proposal contract should distinguish at minimum:

- evidence-backed observations;
- hypotheses;
- assumptions;
- unsupported claims;
- evidence references;
- confidence or epistemic status.

Unknown evidence references must be rejected deterministically.

A proposal must never become Economic Truth merely because it was generated from governed context.

## Adoption status

LangChain4j:

PROVISIONAL ADOPT
Mode: WRAP
Phase: NOW for research

The dependency must remain behind Flooow-owned contracts.

Ollama:

LAB / LOCAL MODEL RUNTIME

No production commitment is implied by this experiment.

## Decision

EXP-0008 proves that a JVM-native local intelligence boundary is viable.

It does not prove semantic grounding.

The next experiment must harden claim-level provenance before any intelligence adapter is connected to production Economic Truth.