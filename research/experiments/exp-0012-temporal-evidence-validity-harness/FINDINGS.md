# EXP-0012 â€” Temporal Evidence Validity Findings

## Hypothesis

Evidence validity has at least three independent dimensions:

1. reference integrity;
2. semantic support;
3. temporal validity.

A support graph is unsafe for operational decisions if time semantics are absent.

## Success criteria

- evidence observed inside the decision window -> TEMPORALLY_VALID;
- evidence older than configured freshness -> STALE;
- future-dated evidence -> FUTURE_EVIDENCE;
- evidence outside the claim window -> OUTSIDE_WINDOW;
- premises with mutually incompatible windows -> TEMPORAL_CONFLICT;
- temporal status cannot upgrade a semantically unsupported claim;
- all accepted intelligence remains non-canonical and non-executable.
