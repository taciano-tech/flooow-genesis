# TASK-0078 Reusable Inventory Mapping Projection

**Date:** 2026-08-12

## Result

**IMPLEMENTED - pending CI validation.**

V009 corrects one overly restrictive V008 validation so an accepted exact
inventory mapping can project later immutable V006 evidence that has the same
organization, connection, capability, source item, nullable location, and
nullable unit selector.

## Root cause

SPEC-0014 requires the projector to construct the exact selector from the source
row and resolve the one active V007 mapping for that selector. V008 additionally
required the projected source pointer to equal the historical evidence pointer
used to justify creation of the mapping decision. That coupled a reusable
identity decision to one page record and prevented the same mapping from
interpreting the next inventory reading.

The V007 evidence pointer remains immutable proof for why the mapping was
accepted. It is not the lifetime scope of that mapping. The exact selector is
the mapping scope.

## Implemented scope

- additive Flyway V009; V008 remains immutable in migration history;
- replaces only the V008 validation function;
- preserves exact organization, connection, capability, item, nullable
  location, and nullable unit agreement;
- preserves active mapping, active target, factor, revision, source content,
  time, predecessor, immutability, and redaction checks;
- adds a PostgreSQL test with two V006 page records under the same selector;
- proves both observations cite the one accepted mapping and retain their own
  source pointers and exact quantities.

## Safety boundary

No automatic mapping, bulk projection, scheduler, worker, current-state
selection, aggregation, reconciliation, business availability, inventory
mutation, assessment, event, provider call, public route, or runtime wiring is
introduced.

## Validation

Local validation compiles the complete PostgreSQL test source and keeps the pure
canonical observation tests green. GitHub CI must run all PostgreSQL
Testcontainers tests, Flyway V001 through V009, the complete repository build,
and the persistent runtime package before review.
