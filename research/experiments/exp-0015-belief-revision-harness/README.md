# EXP-0015 â€” Governed Belief Revision

Purpose: validate how Flooow can revise an intelligence belief when new governed evidence or contradiction arrives, while preserving history and never mutating canonical Economic Truth.

## Core invariants

belief != truth

new evidence != automatic overwrite

revision != deletion

higher confidence != canonical truth

AGENT != TRUTH
AGENT != AUTHORITY

## Revision states

- ESTABLISHED
- REINFORCED
- SUPERSEDED
- CONTESTED
- SUSPENDED
- INSUFFICIENT_INFORMATION

## Design

A belief is an intelligence-layer object with:

- stable belief id;
- version;
- typed proposition;
- semantic-support state;
- temporal-validity state;
- confidence band;
- contradiction state;
- supporting claim ids;
- opposing claim ids;
- revision lineage;
- recorded reason codes;
- explicit canonicalTruth=false;
- explicit executable=false.

Revision is deterministic and policy-owned.

A new candidate never overwrites history. It produces a new version that references the prior version.

## Policy highlights

1. Unsupported or temporally invalid candidates cannot supersede an established belief.
2. A directly supported, temporally valid candidate that agrees with the current belief reinforces it.
3. A supported, valid candidate that conflicts with the current belief makes the belief CONTESTED unless a governed supersession policy is satisfied.
4. Supersession requires:
   - comparable proposition scope;
   - semantic support;
   - temporal validity;
   - no unresolved contradiction in the candidate;
   - candidate confidence strictly stronger than current confidence;
   - candidate must be newer by governed observation time;
   - candidate must meet minimum confidence policy.
5. If essential information is missing, revision is SUSPENDED or INSUFFICIENT_INFORMATION.
6. Narrative prose has zero revision authority.

Research-only. No production integration.
