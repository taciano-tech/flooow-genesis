# TASK-0091 - Marketplace Financial Trace and Economic Ledger contract review

## Decision

ADR-0021 and SPEC-0021 define the first implementable slice of EPIC-MKT-002.
The slice turns normalized financial facts into an immutable organization-owned
trace from order through settlement, payment account, and bank.

## Boundary choices

- Financial trace and ledger remain Marketplace Intelligence concepts.
- MKT-001 exact money, direction, source, marketplace, and order values are
  reused rather than duplicated or promoted to the Kernel.
- Expected and actual facts are separate append-only records.
- External source facts and internal append requests provide independent replay
  protection.
- Corrections append replacements and preserve originals; economic reversals
  remain separate facts.
- PostgreSQL records source time and transaction recording time independently.
- One present external source fact cannot be attached to two orders in the same
  organization.

## Deliberate deferral

MKT-002 does not compute differences, matching, tolerance, partial/full status,
systemic divergence, returns, recovery, or pricing. Those are judgments over
the ledger and begin only with MKT-003.

This separation preserves the project sequence:

```text
MKT-001 exact economic truth
  -> MKT-002 immutable financial evidence
  -> MKT-003 reconciliation and divergence
  -> pricing and later intelligence
```

## Essence preserved

- Genesis remains the Organizational Computing platform.
- Marketplace Intelligence remains a vertical consumer.
- The Kernel receives no marketplace or financial vocabulary.
- Domain and persistence precede live integrations and intelligence.
- No financial action can be produced by the first ledger.

## Authorization

Acceptance authorizes TASK-0092 only: the production-inactive ledger domain,
PostgreSQL V014 repository, and focused tests. It authorizes no API, connector,
worker, reconciliation, pricing, AI, or Kernel change.
