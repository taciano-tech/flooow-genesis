# SPEC-0010: Organization Context Propagation

**Status:** Proposed

**Date:** 2026-08-11

**Source decision:** ADR-0010

## Objective

Propagate authenticated organization authority from the current single-service
API through assessments, immutable integration events, outbox coordination, and
registered destinations without enabling a real provider or external API.

## Authorized next implementation

Acceptance authorizes TASK-0069 only:

1. add a pure `platform:foundation:organization-context` Kotlin module containing
   the canonical `OrganizationId` value;
2. replace the control plane's private organization identifier with that value;
3. bind the configured service bearer to one configured organization;
4. require organization context for assessment recording and retrieval;
5. add an expansion-only PostgreSQL migration for organization-scoped journal,
   event, outbox-delivery, and repository operations;
6. emit a new immutable organization-scoped CloudEvent v2;
7. require active same-organization destinations before enqueue and require
   organization scope for delivery coordination;
8. quarantine legacy unscoped rows from API and delivery behavior;
9. keep external sinks, OAuth, provider adapters, polling, webhooks, and real
   credentials disabled.

No human user, role, permission, tenant administration, login, token issuance,
provider network call, production worker, or legacy ownership assignment is
authorized.

## Canonical organization value

`OrganizationId` wraps a canonical UUID. It lives outside the Kernel and outside
any individual application. Parsing rejects non-canonical UUID text. Random
creation remains the integration control plane's responsibility through its
injectable identifier factory.

The extraction is a source-compatible concept migration, not a second company
model. PostgreSQL continues using `organization_id uuid`.

## Authentication authority

Production startup additionally requires:

```text
FLOOOW_SERVICE_ORGANIZATION_ID=<canonical UUID>
```

The application validates it before binding the server. Errors name only the
configuration key and rule. Authentication produces an internal
`ServicePrincipal` containing the configured organization ID only after the
existing constant-time bearer comparison succeeds.

No `X-Organization-Id`, tenant header, organization query parameter, path
selector, cookie, or request-body property is accepted. A caller therefore
cannot select another company while using the coarse MVP credential.

Health probes remain public and create no principal. The OpenAPI request and
response shapes remain unchanged because organization context is server-owned.

## Marketplace Operations contracts

`OrganizationContext` is required explicitly at the application boundary:

```text
record(organizationId, input)
findById(organizationId, assessmentId)
append(organizationId, record)
findById(organizationId, assessmentId)
```

`RecordedInventoryRiskAssessment` contains `organizationId`, but request and
result digests remain business-content digests and do not change. The evaluator
and every Kernel contract remain byte-for-byte unchanged.

The API returns `404` for an assessment owned by another organization, exactly
as for an unknown ID. It never returns `403` or reveals that the identifier
exists elsewhere.

## PostgreSQL expansion migration

Migration `V005` adds nullable `organization_id` columns to the existing journal,
event outbox, and delivery tables so historical rows are preserved. It adds
organization-scoped indexes and composite agreement constraints.

Application code enforces non-null organization for every new row. New journal
and outbox writes occur in one transaction and carry the same organization.
New delivery rows carry the organization of both their event and registered
destination.

Legacy rows with null organization:

- are never returned by scoped assessment lookup;
- cannot be enqueued into scoped delivery;
- are excluded from claim, renewal, settlement, replay, and dispatch;
- are not mutated or assigned automatically;
- remain available only for a later explicit administrative migration.

No `DEFAULT` organization, sentinel UUID, first-row selection, or environment-
driven historical backfill is permitted.

Every scoped predicate includes `organization_id`, including lookup by globally
unique assessment or event ID. Foreign keys enforce same-organization journal to
event and connection to destination relationships. Delivery enqueue verifies an
active organization, active connection, and active destination in the control
plane in the same database transaction.

## CloudEvent v2

Existing v1 bytes, schema, type, and fixtures are immutable. New scoped writes
emit:

```text
type:       io.flooow.marketplace.inventory-risk-assessment.recorded.v2
dataschema: https://flooow.io/schemas/events/inventory-risk-assessment-recorded.v2.json
extension:  floooworganizationid=<canonical UUID>
subject:    /organizations/<organizationId>/inventory-risk-assessments/<assessmentId>
```

The v2 data object also contains `organizationId` and otherwise preserves the
accepted business fields. Database checks require the column, extension, data
field, and subject to agree exactly. Organization ID may appear in traces but
not metric labels. It is never inferred from a destination string.

## Delivery isolation

All delivery store operations require organization ID:

```text
enqueue(organizationId, eventId, destinationId, nextAttemptAt)
claim(organizationId, ...)
renew(organizationId, ...)
succeed(organizationId, ...)
retry(organizationId, ...)
deadLetter(organizationId, ...)
```

Claiming is scoped before row locks are acquired. Attempt fencing includes the
organization predicate. A worker cannot learn whether an event, destination,
lease, or attempt exists in another organization.

Registration authorizes routing metadata only. TASK-0069 retains the existing
production-disabled dispatcher and uses controlled in-memory sinks in tests; it
makes no ERP, marketplace, carrier, webhook, or broker call.

## Compatibility and rollout

TASK-0069 is a coordinated contract change for the current single service:

- Compose and CI provide a non-secret test organization UUID;
- OpenAPI business payloads remain unchanged;
- v1 event fixtures remain unchanged and v2 fixtures are added;
- startup fails closed if organization configuration is absent or invalid;
- rollback code can read only pre-TASK-0069 global rows and therefore must not be
  used after scoped writes begin unless the deployment is restored as a unit.

Production activation remains prohibited. No control-plane repository or
delivery worker is instantiated by startup solely because the schema exists.

## Test plan

1. canonical organization parsing accepts canonical UUID and rejects other text;
2. the foundation module has no Kernel, application, HTTP, or database dependency;
3. missing or invalid service organization configuration prevents startup;
4. authentication creates the configured principal only for the valid bearer;
5. headers, paths, queries, and bodies cannot override organization authority;
6. organization-scoped POST preserves the current response shape;
7. same-organization GET succeeds while cross-organization GET returns the exact
   existing 404 response;
8. evaluator, Kernel production source, and business digests remain unchanged;
9. V005 applies after V001 through V004 without assigning legacy rows;
10. new journal and event rows contain the same non-null organization;
11. v1 fixture remains byte-equal and v2 fixture reproduces exactly;
12. v2 column, extension, subject, data, and schema agreement is database-enforced;
13. legacy rows are invisible to scoped reads and cannot enter delivery;
14. enqueue rejects unknown, suspended, revoked, or cross-organization
    destinations without revealing foreign existence;
15. two organizations can reuse business SKU values without data leakage;
16. claims, leases, fencing, retry, success, and dead letter affect only the
    supplied organization;
17. concurrent workers for different organizations never claim each other's rows;
18. logs, problems, metrics, traces, events, and audit contain no credential or
    secret reference;
19. public health behavior and protected OpenAPI authentication remain unchanged;
20. no production startup or test makes an external provider call;
21. the complete repository build remains green.

## Remaining boundary

Multiple service principals, token inventory and rotation, human identity,
membership, roles, delegated authorization, tenant administration, legacy data
assignment, real vaults, OAuth, provider adapters, sync cursors, webhooks,
mapping, and production delivery require later accepted specifications.

## Acceptance

Merging ADR-0010 and SPEC-0010 authorizes TASK-0069 only. It does not authorize
real credentials, external connectivity, provider behavior, or multi-company
identity administration.
