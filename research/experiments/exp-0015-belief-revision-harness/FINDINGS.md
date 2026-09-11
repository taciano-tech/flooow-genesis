# EXP-0015 â€” Findings

## Hypothesis

Once semantic support, temporal validity, confidence and contradiction are first-class, Flooow can implement belief revision as a deterministic governed state transition instead of allowing an LLM to "change its mind" invisibly.

## Why this matters

Without explicit revision lineage:

- stale beliefs silently survive;
- contradictory beliefs silently overwrite each other;
- downstream forecasts inherit unexplained state;
- operators cannot reconstruct why a decision changed;
- confidence becomes theater rather than governance.

## Success criteria

- every revision creates a new immutable version;
- prior versions remain reconstructable;
- agreement reinforces;
- conflict contests;
- weak candidates cannot replace stronger current beliefs;
- stronger, newer, fully eligible candidates may supersede under explicit policy;
- unsupported candidates never supersede;
- temporally invalid candidates never supersede;
- unresolved contradiction blocks supersession;
- narrative has zero authority;
- canonicalTruth=false and executable=false always.

## Architectural consequence

Belief revision becomes the bridge between:

evidence/support/time/confidence/contradiction
        â†“
governed current belief
        â†“
information gaps / Value of Information
        â†“
experiments
        â†“
decision policy
