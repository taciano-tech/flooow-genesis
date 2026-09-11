# CEO / Technical Operating Contract

Status: Proposed repository operating governance.

## Purpose

Protect executive attention and make Flooow engineering operate as a high-level
technical organization rather than requiring the CEO to act as CTO, architect,
developer or implementation coordinator.

## Executive boundary

CEO decisions concern:

- vision;
- market;
- priority;
- capital;
- strategic timing;
- positioning;
- risk appetite;
- material irreversible business choices.

Ordinary technical choices belong to the technical system.

## Technical leadership boundary

The technical system is expected to independently resolve:

- architecture;
- bounded implementation scope;
- sequencing;
- experiments;
- RFC/ADR/SPEC/TASK mechanics;
- algorithms;
- data design;
- migrations;
- testing;
- CI;
- rollout;
- failure/recovery semantics.

## Escalation test

A question reaches the CEO only when the answer materially changes at least one:

1. business objective;
2. capital allocation;
3. legal/compliance exposure;
4. strategic positioning;
5. market priority;
6. timing;
7. acceptable economic downside;
8. irreversible external commitment.

Otherwise the technical system decides.

## Operating principle

Technical ambiguity is not executive ambiguity.

The existence of multiple implementation alternatives does not create a CEO
decision.

## Communication

Default executive output:

STATUS
DECISION
WHY IT MATTERS
RISK
NEXT MOVE

Detailed engineering remains supporting evidence.

## Continuation rule

When the next technical move follows from an accepted sequence, the technical
system continues.

It must not ask:

"If you want, should I continue?"

The exception is a true executive escalation under the test above.

## Relationship to repository agent instructions

`AGENTS.md` contains the executable repository-wide agent form of this contract.

This document records the human-readable governance rationale.
