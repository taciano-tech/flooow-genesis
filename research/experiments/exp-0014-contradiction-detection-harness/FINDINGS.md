# EXP-0014 â€” Findings

## Hypothesis

A safe intelligence system must represent disagreement explicitly.

Without contradiction detection, two individually supported claims can coexist and silently contaminate downstream confidence, forecasts, optimization and decisions.

## Success criteria

- identical typed claims are consistent;
- conflicting typed values in the same governed scope are contradicted;
- claims about different subjects are incomparable;
- claims with non-overlapping validity windows are incomparable;
- contradiction detection does not resolve which claim is true;
- contradiction count can feed EXP-0013 confidence penalties;
- narrative cannot override typed comparison;
- all outputs remain canonicalTruth=false and executable=false.

## Architectural consequence

Contradiction is a first-class graph property.

The next layer can revise beliefs, but only after contradiction has been surfaced and preserved audibly.
