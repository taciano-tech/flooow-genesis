# TASK-0087 — Canonical inventory candidate adjudication contract review

## Decision

ADR-0019 and SPEC-0019 define an explicit, immutable decision over one frozen
V012 member. The boundary records deliberate human or controlled-workflow review
without converting comparison order into authority.

## Essence preserved

- V008 remains the only exact quantity ledger.
- V012 remains the immutable candidate-set provenance boundary.
- TASK-0086 remains descriptive and never chooses a member.
- V013 stores only decision references and audit identity.
- No global source rank, automatic winner, business stock, Kernel, runtime, API,
  connector, or provider behavior is introduced.

## Authorization

Acceptance authorizes TASK-0088 only: pure adjudication contracts, V013 immutable
references, transactional explicit decision/replay/read behavior, and tests.
