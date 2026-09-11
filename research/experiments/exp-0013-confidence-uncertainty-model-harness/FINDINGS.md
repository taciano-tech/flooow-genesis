# EXP-0013 â€” Findings

## Hypothesis

Confidence is not a model-generated self-score.

It must be computed from Flooow-owned, inspectable inputs and rules.

## Success criteria

- confidence is deterministic for equal inputs;
- higher derivation depth reduces confidence;
- contradiction reduces confidence;
- explicit unknowns reduce confidence;
- insufficient information cannot be promoted by narrative;
- confidence never changes canonicalTruth=false;
- confidence never changes executable=false.

## Architectural consequence

The confidence model belongs to governed intelligence infrastructure, not to the LLM.

The model may propose claims, but Flooow owns the confidence computation.
