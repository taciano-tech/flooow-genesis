# ADR-0044: Generalized Transactional Outbox Compatibility

Status: Accepted

Date: 2026-08-31

## Context

ADR-0043 selected the existing transactional outbox for durable independent
economic-evidence notifications. Implementation inspection found that the
table and delivery serializer are not yet generic:

- `integration_event_outbox.assessment_id` is mandatory;
- database checks accept only inventory-risk event types and schemas;
- the delivery serializer reconstructs only inventory-risk data fields;
- the stored envelope content type is CloudEvents JSON, while SPEC-0042 named
  the inner data content type as the stored content type.

Inserting the event described by SPEC-0042 would therefore fail at the database
boundary or become undeliverable. Creating a second outbox would duplicate
retries, leases, destinations, observability, and operational authority.

## Decision

Generalize the existing outbox in the same V015 migration that introduces
durable economic evidence. Preserve all existing inventory-risk behavior and
add one explicitly admitted economic-evidence CloudEvent family.

The outbox row continues to store:

```text
content_type = application/cloudevents+json; charset=UTF-8
```

The CloudEvent envelope continues to declare:

```text
datacontenttype = application/json
```

`assessment_id` becomes nullable. It remains mandatory for inventory-risk
events and must be absent for economic-evidence events. Existing foreign keys,
organization isolation, unique event identity, delivery rows, leases, retries,
dead-letter behavior, and destination policy remain authoritative.

## Economic-evidence CloudEvent

The admitted event contract is:

```text
type = io.flooow.marketplace.economic-evidence.changed.v1
source = https://flooow.io/marketplace-operations
dataschema = https://flooow.io/schemas/events/
             marketplace-economic-evidence-changed.v1.json
subject = /organizations/{organizationId}/marketplace-orders/
          {marketplaceOrderId}/economic-evidence
```

The envelope contains the existing `floooworganizationid` extension. Its
`data` object contains exactly:

```text
marketplaceOrderId
evidenceVersion
changeKind
```

Organization is not duplicated inside `data`. No provider reference, external
order, amount, source key, identity, correction reason, or evidence payload is
published.

## Delivery canonicalization

The delivery runtime selects an explicit canonicalizer by admitted event type.
Inventory-risk canonicalization remains byte-compatible. Economic-evidence
canonicalization admits exactly the envelope fields above and exactly its three
data fields. Unknown event types fail closed; the runtime does not forward an
arbitrary stored JSON object.

## Consequences

Genesis retains one outbox and one delivery authority while gaining a second
bounded event family. V015 must prove existing inventory-risk events still
persist and deliver, evidence events deliver canonically, forbidden fields are
not forwarded, and unknown event types fail closed.

## Supersession

This ADR corrects only the outbox compatibility assumptions in ADR-0043.
Append-only evidence history, domain replay, optimistic versioning, lifecycle,
privacy, and all domain separations remain unchanged.
