# EXP-0009 — Evidence-Bound Intelligence Claims

## Initial hypothesis

Free-form model output is not an acceptable unit of trust.

The safe unit is a structured claim whose epistemic status is assigned by Flooow after deterministic validation against governed evidence.

## Gate under test

A claim may not become evidence-backed because the model says it is evidence-backed.

Flooow must verify:

- evidence reference existence;
- fact key identity;
- observed value match for observations;
- claim kind;
- confidence bounds;
- non-canonical and non-executable status.

## Specific regression from EXP-0008

The following invented concepts must not be promoted to evidence-backed observations when the only governed fact is `contribution_margin_pct=18.42`:

- industry average contribution margin;
- labor costs;
- causal relationship between labor costs and margin.

They may exist only as explicit hypotheses or assumptions unless later evidence supports them.

## Success criterion

CI-safe deterministic tests pass and a local model can draft structured claims that Flooow validates independently.
