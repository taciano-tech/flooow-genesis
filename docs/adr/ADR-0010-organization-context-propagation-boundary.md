# ADR-0010: Organization Context Propagation Boundary

Status: Proposed

Date: 2026-08-11

## Context

TASK-0067 introduced organization-scoped integration connections, credentials,
destinations, and audit records. The existing Marketplace Operations data plane
is still global: its bearer principal has no organization authority, assessment
journal lookups use only `assessment_id`, CloudEvents contain no organization,
and outbox delivery accepts an unscoped destination string.

Connecting those two halves now would allow a caller or routing component to
select another company's destination without proving authority over that
company. A request header or body field is not authority; it is untrusted input.

The MVP still has one configured machine client and no human identity, tenant
membership, role, scope, identity provider, or administration API.

## Decision

Introduce one canonical `OrganizationId` in a small pure foundation module that
is independent from the Kernel. The integration control plane and Marketplace
Operations use that value without moving organization concepts into Flooow's
reasoning ontology.

Bind the current service bearer to exactly one organization at startup through
trusted configuration. Successful authentication creates an internal
`ServicePrincipal(organizationId)`. Clients cannot select or override an
organization through a header, path, query, or request body.

Propagate that authenticated context through every data-plane boundary:

```text
service configuration
  -> authenticated principal
  -> recorder and scoped lookup
  -> assessment journal
  -> CloudEvent v2
  -> outbox
  -> delivery registration and claim
  -> control-plane destination
```

All new writes are organization-scoped. Reads, updates, enqueue, claim, lease,
and settlement require organization ID even when another identifier is globally
unique. Database composite keys and foreign keys enforce agreement.

Existing unscoped rows are legacy records. The expansion migration preserves
them with a null organization, but production APIs and delivery coordination do
not return, route, mutate, or dispatch them. No migration guesses their owner.
Assigning or deleting legacy records requires a later explicit operator-owned
data migration.

The immutable v1 event contract remains unchanged. Organization-scoped writes
emit a v2 event with an organization extension and schema. Existing v1 fixtures
remain historical compatibility evidence and cannot enter scoped delivery.

## Consequences

### Positive

- company authority originates from authenticated server configuration;
- cross-company reads and routing become structurally impossible through public
  and repository contracts;
- existing data is preserved without silently assigning it to the wrong owner;
- the Kernel remains independent from tenancy and identity;
- real provider credentials and APIs can remain deferred.

### Negative

- the current runtime still supports only one technical client and one company;
- legacy rows require an explicit future ownership migration;
- CloudEvent consumers must adopt v2 before scoped delivery is activated;
- multi-company token inventories, users, roles, and delegated access remain
  unresolved.

## Alternatives considered

### Accept an organization header

Rejected because possession of the current coarse bearer would authorize any
organization value supplied by the caller.

### Put organization ID in request bodies

Rejected for the same reason and because identity context is not business input.

### Assign all historical rows to the configured organization

Rejected because deployment configuration is not evidence of historical data
ownership.

### Add organization directly to the Kernel

Rejected because tenancy is an application/platform concern, not an atomic unit
of organizational reasoning.

### Implement users, JWTs, roles, or OAuth first

Deferred. These require separate product and security decisions and are not
needed to make the single-service MVP structurally company-safe.

## Authorization

This ADR does not authorize implementation alone. SPEC-0010 freezes the exact
configuration, contracts, migration, event version, delivery isolation, and
compatibility tests for TASK-0069.
