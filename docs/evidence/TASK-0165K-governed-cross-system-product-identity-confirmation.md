# TASK-0165K — Governed Cross-System Product Identity Confirmation

Date: 2026-09-11

## Decision

Introduce one explicit cross-system identity decision chain inside the existing
marketplace identity boundary. Durable source evidence remains evidence;
candidate equality and within-Omie resolution remain non-authoritative inputs.
ADR-0074 and SPEC-0074 govern the new confirmation authority.

## Implementation evidence

- V029 adds organization/provider-connection-scoped, append-only decisions with
  deterministic decision IDs, correlations, provenance, revisions, and
  supersession links.
- The PostgreSQL repository validates active provider scopes and exact durable
  identities, serializes decisions for a Mercado Livre identity, rejects a
  competing current confirmation, and reads only within organization scope.
- The domain resolver returns confirmed or rejected only from current explicit
  decisions; multiple confirmations resolve as conflict.
- The narrow service-bearer API binds organization and both connections
  server-side. It exposes record and immutable decision read only.

## Safety proof

Tests cover explicit confirmation, rejection, correction/supersession,
idempotent replay and ID collision, organization/connection isolation, missing
source evidence, conflicting targets, and append-only history. Existing bridge
tests prove exact seller-SKU text remains candidate-only and same-kind
within-Omie exact resolution does not transitively confirm it.

No provider write, fuzzy/title/price inference, currency inference, Economic
Truth promotion, `PRODUCT_COST` promotion, historical candidate mutation, or
autonomous execution is introduced.

## Validation

Focused domain, authenticated API, migration/persistence Testcontainers, and
TASK-0165H/I/J regression suites passed. The full Gradle build and
`git diff --check` passed.
