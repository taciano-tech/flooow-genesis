# EXP-0014 â€” Contradiction Detection

Purpose: detect conflicting governed intelligence claims without silently choosing a winner.

## Core invariant

contradiction detected != truth resolved

A contradiction detector may establish that two claims cannot both hold in the same governed scope and validity interval.

It must not decide which claim is true merely because one sounds more plausible, has higher confidence, or was generated later.

## Decisions

- CONSISTENT
- CONTRADICTED
- INCOMPARABLE

## Rules

Claims are comparable only when they share:

- organization;
- subject;
- fact key;
- governed scope;
- overlapping validity windows.

Typed values are compared deterministically.

Narrative prose has zero authority.

All outputs remain non-canonical and non-executable.

Research-only. No production integration.
