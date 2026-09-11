# TASK-0143: Durable Evidence Outbox Contract Correction

Status: Contract corrected; runtime implementation not started

Date: 2026-08-31

## Trigger

TASK-0144 implementation preparation tested the SPEC-0042 assumptions against
the actual V002/V005 schema and `OutboxDeliveryRuntime`.

## Finding

The existing outbox was reusable operationally but not yet generic
structurally. A SPEC-0042 evidence event would fail because `assessment_id` was
mandatory, database constraints admitted only inventory-risk events, and the
delivery canonicalizer retained only inventory-risk data fields. The SPEC also
confused the stored CloudEvents content type with the inner data content type.

## Action

Production work stopped before schema or repository code was committed.
ADR-0044 and SPEC-0043 now require a type-discriminated generalization of the
single existing outbox, preserve current inventory-risk behavior, define exact
evidence CloudEvent shape, and extend the next implementation scope to the
delivery canonicalizer and its focused tests.

## DNA protection

- no second outbox or delivery engine is introduced;
- no previous migration is edited;
- existing inventory events remain compatible;
- arbitrary JSON pass-through remains forbidden;
- evidence events expose no amounts, provider identifiers, source keys, or
  correction reasons;
- Economic Evidence remains distinct from Truth, Ledger, and Reconciliation;
- no provider, API, UI, projection, action, AI, or Kernel change occurred.

## Validation required

Documentation checks, full applicable local build, and PR CI must pass. Only
after the correction merges may TASK-0144 implement the nine-file scope.
