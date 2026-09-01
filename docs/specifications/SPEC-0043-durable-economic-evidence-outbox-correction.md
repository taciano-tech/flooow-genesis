# SPEC-0043: Durable Economic Evidence Outbox Correction

Status: Accepted

Date: 2026-08-31

## Objective

Correct the implementation contract in SPEC-0042 after repository inspection
proved that the existing outbox schema and delivery canonicalizer are still
inventory-risk-specific.

SPEC-0042 remains authoritative except where this specification explicitly
replaces it. The bounded runtime implementation becomes TASK-0144.

## V015 outbox compatibility

In addition to the evidence tables required by SPEC-0042, V015 must:

1. make `integration_event_outbox.assessment_id` nullable;
2. preserve its inventory journal foreign key and uniqueness behavior;
3. replace inventory-only checks with type-discriminated checks;
4. require `assessment_id` for both existing inventory-risk event versions;
5. require a null `assessment_id` for economic-evidence events;
6. preserve the existing organization agreement for inventory-risk v2;
7. require organization, subject, type, schema, and internal order agreement
   for economic-evidence events;
8. retain the stored CloudEvents content type and inner JSON data content type;
9. reject every unsupported event type/schema/shape;
10. preserve existing delivery foreign keys and indexes.

The migration must not rewrite or delete existing outbox or delivery rows.

## Corrected event contract

Replace the SPEC-0042 outbox type and content-type values with:

```text
event_source: https://flooow.io/marketplace-operations
event_type: io.flooow.marketplace.economic-evidence.changed.v1
content_type: application/cloudevents+json; charset=UTF-8
datacontenttype: application/json
dataschema: https://flooow.io/schemas/events/
            marketplace-economic-evidence-changed.v1.json
```

The CloudEvent `subject` is scoped by organization and internal marketplace
order. The `data` object contains exactly:

```json
{
  "marketplaceOrderId": "internal canonical UUID",
  "evidenceVersion": 1,
  "changeKind": "FACT|ATTEMPT|CORRECTION"
}
```

The full envelope also contains the existing `floooworganizationid` extension.
No organization ID is duplicated in `data`.

## Delivery runtime

TASK-0144 may extend the existing delivery canonicalizer with explicit dispatch
by event type. It must:

- preserve current inventory-risk canonical bytes and behavior;
- canonicalize economic-evidence envelope fields in fixed order;
- canonicalize only the three admitted evidence data fields in fixed order;
- reject missing, extra, malformed, or mismatched fields;
- reject unknown event types;
- keep signing, retries, leases, destinations, and dead-letter behavior
  unchanged.

No generic pass-through of arbitrary JSON is permitted.

## Additional required tests

The SPEC-0042 test plan remains mandatory. TASK-0144 also proves:

1. pre-existing inventory-risk v2 outbox insert and delivery still pass;
2. existing inventory-risk canonical bytes are unchanged;
3. evidence outbox row uses null `assessment_id`;
4. evidence CloudEvent satisfies organization, subject, type, and schema checks;
5. evidence delivery contains exactly the allowed envelope and data fields;
6. evidence delivery canonical bytes are deterministic;
7. an evidence event with a forbidden data field is rejected;
8. an evidence event with inventory `assessment_id` is rejected;
9. an inventory event without `assessment_id` is rejected;
10. an unsupported event type fails closed;
11. migration preserves existing outbox and delivery rows;
12. duplicate, stale, conflict, unavailable, and failed transactions create no
    evidence outbox or delivery side effect.

## Corrected implementation scope

TASK-0144 may alter only these nine files:

1. production application persistence contract:
   `applications/marketplace-operations/src/main/kotlin/io/flooow/marketplace/operations/economics/evidence/MarketplaceIndependentEconomicEvidencePersistence.kt`;
2. focused application persistence contract test:
   `applications/marketplace-operations/src/test/kotlin/io/flooow/marketplace/operations/economics/evidence/MarketplaceIndependentEconomicEvidencePersistenceTest.kt`;
3. Flyway migration:
   `applications/marketplace-operations-persistence-postgres/src/main/resources/db/migration/V015__create_independent_marketplace_economic_evidence.sql`;
4. Postgres evidence adapter:
   `applications/marketplace-operations-persistence-postgres/src/main/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceIndependentEconomicEvidenceRepository.kt`;
5. Postgres evidence adapter test:
   `applications/marketplace-operations-persistence-postgres/src/test/kotlin/io/flooow/marketplace/persistence/postgres/PostgresMarketplaceIndependentEconomicEvidenceRepositoryTest.kt`;
6. existing outbox delivery runtime:
   `applications/marketplace-operations-persistence-postgres/src/main/kotlin/io/flooow/marketplace/persistence/postgres/OutboxDeliveryRuntime.kt`;
7. existing outbox delivery runtime test:
   `applications/marketplace-operations-persistence-postgres/src/test/kotlin/io/flooow/marketplace/persistence/postgres/OutboxDeliveryRuntimeTest.kt`;
8. `docs/evidence/TASK-0144-durable-independent-marketplace-economic-evidence.md`;
9. one TASK-0144 entry in `docs/journal/MGI-EXECUTIVE-JOURNAL.md`.

No dependency file, existing migration, provider, API, UI, projection,
materializer, Ledger, Reconciliation, or Kernel file may change.

## Quality gates

All SPEC-0042 gates remain mandatory, expanded to the exact nine-file scope.
Focused OutboxDeliveryRuntime tests are mandatory before the full Postgres
module suite and full build. No merge is permitted with a regression in the
existing inventory-risk event path.

## Acceptance

Merging ADR-0044 and SPEC-0043 authorizes TASK-0144 only. It does not authorize
P0.3, provider activation, API, UI, Economic Truth materialization, Ledger,
Reconciliation, decision automation, or Kernel changes.
