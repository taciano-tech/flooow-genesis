# SPEC-0009: Integration Control Plane

**Status:** Proposed

**Date:** 2026-08-10

**Source decision:** ADR-0009

## Objective

Create a production-inactive control-plane library that proves organization
isolation, provider connection lifecycle, destination registration, secret
references, rotation, and auditability without connecting to an external API.

## Authorized next implementation

Acceptance authorizes TASK-0067 only:

1. add a pure Kotlin `applications:integration-control-plane` module with no
   Kernel, HTTP, database, OAuth SDK, or provider dependency;
2. add additive PostgreSQL control-plane tables and repository adapters;
3. define a transport-neutral `SecretVault` port and deterministic fake vault;
4. implement organization, connection, destination, credential-binding, rotation,
   suspension, revocation, and append-only audit operations;
5. require organization scope on every repository operation;
6. validate isolation, lifecycle, rotation failures, privacy, and compatibility;
7. keep all production startup and public routes unchanged.

No human user, role, permission, login, OAuth redirect, provider token exchange,
real secret manager, connector, polling, webhook, scheduler, public control API,
or production delivery is authorized.

## Identifiers

All primary identifiers are canonical random UUIDs created by injectable factories:

```text
IntegrationOrganizationId
IntegrationConnectionId
IntegrationAuditEntryId
```

`ProviderKey` and `DestinationId` use lowercase ASCII:

```text
ProviderKey:  [a-z0-9][a-z0-9.-]{0,99}
Destination:  [a-z0-9][a-z0-9._-]{0,99}
```

Examples are `br.com.mercadolivre`, `br.com.omie`, and
`connection.773afbc1-6e04-41ef-9f30-0974d7b31a90`. Examples are not a provider
registry and do not authorize an adapter.

## Application contracts

The new module owns immutable values and ports for:

```text
IntegrationOrganization
IntegrationConnection
IntegrationDestination
CredentialBinding
IntegrationAuditEntry
IntegrationControlPlaneRepository
SecretVault
```

The module does not depend on Marketplace Operations. Marketplace Operations,
the API, and the Kernel do not depend on the new module in TASK-0067.

## Organization lifecycle

Organization status is `ACTIVE` or `SUSPENDED`.

- creation starts `ACTIVE`;
- suspension blocks connection activation, secret use, destination enqueue, and
  future sync work;
- resumption returns only the organization to `ACTIVE`; it does not reactivate
  suspended or revoked connections automatically;
- organization deletion is not authorized.

The record contains only ID, status, created time, and updated time. Legal name,
tax ID, address, billing, plan, locale, and user membership are out of scope.

## Connection lifecycle

Each connection belongs to exactly one organization and one immutable provider
key. Credential kind is either `OAUTH2_AUTHORIZATION_CODE` or
`STATIC_API_CREDENTIAL`; this describes custody, not provider protocol behavior.

Statuses:

```text
DRAFT -> ACTIVE -> SUSPENDED -> ACTIVE
  |         |           |
  +---------+-----------+-> REVOKED
```

- `DRAFT` has no active credential binding and cannot operate;
- activation requires an active organization and a current credential binding;
- `SUSPENDED` preserves the binding but blocks all use;
- `REVOKED` is terminal, clears the active binding after vault revocation, and
  cannot be reactivated;
- transient provider or network failures do not change lifecycle status.

Provider key, organization ID, credential kind, and connection ID are immutable.
Remote account identifiers, scopes, expiry, health, rate limits, sync cursors,
and provider settings remain provider-adapter concerns.

## Destination registration

One destination belongs to the same organization and connection. Its default ID
is `connection.<connection UUID>`, but an explicit valid ID is accepted for future
independent streams.

Destination status is `ACTIVE` or `SUSPENDED`. It may become active only when its
organization and connection are active. Suspension prevents new enqueue but does
not delete, settle, or rewrite existing delivery history.

Registration does not authorize enqueue. Existing assessments, outbox events,
and delivery rows do not yet carry organization scope, so TASK-0067 must not join
or add a foreign key from the control plane to that data plane. A later accepted
specification must propagate organization identity through the HTTP contract,
assessment journal, CloudEvent, outbox, and delivery coordination atomically
before any registered destination can receive real data.

## SecretVault contract

Secret material is an owned `ByteArray`, never a `String`, data class property,
exception message, log field, metric attribute, span attribute, database value,
event, or test artifact.

The port supports:

```text
store(organizationId, connectionId, credentialBytes) -> SecretReference
withSecret(secretReference, operation: (ByteArray) -> T) -> T
revoke(secretReference)
```

Rules:

- `SecretReference` is opaque, nonblank, at most 512 characters, and sensitive;
- the vault validates organization and connection ownership on every operation;
- `store` copies input bytes and zeroes the supplied input buffer in `finally`;
- `withSecret` supplies a fresh copy and zeroes it in `finally`, including when
  the operation throws;
- callers must not retain the byte array;
- `revoke` is idempotent and revoked material cannot be resolved;
- fake-vault references contain random IDs, never secret hashes or prefixes;
- production vault selection and IAM are not authorized.

## Credential binding and rotation

PostgreSQL stores one current opaque secret reference per connection plus binding
version, bound time, and revoked time. It stores no token metadata or secret
fingerprint.

Initial binding:

1. validate active organization and `DRAFT` connection;
2. store secret in vault;
3. transactionally create binding version 1 and activate connection;
4. if database commit fails, revoke the newly stored secret;
5. append an audit entry without the reference value.

Rotation:

1. store the new secret first;
2. transactionally increment binding version and swap the reference;
3. if commit fails, revoke the new secret and retain the old binding;
4. after commit, revoke the old secret;
5. if old-secret revocation fails, keep the new binding active, emit a controlled
   cleanup-required result, and never roll back to old credentials;
6. append an audit entry containing versions but no references.

Only one rotation may commit for a connection version. Optimistic version checks
reject concurrent stale rotations.

## PostgreSQL schema

Migration `V004` creates, at minimum:

```text
integration_organization
integration_connection
integration_credential_binding
integration_destination
integration_control_audit
```

Every child table carries `organization_id`. Composite unique keys and foreign
keys enforce that a connection, binding, destination, and audit entry cannot
reference a resource from another organization.

Repository queries require organization ID in their predicates even when a
globally unique resource UUID is known. No unscoped `findById`, update, rotate,
suspend, revoke, or list operation is allowed.

Audit entries are append-only and contain:

```text
audit_id, organization_id, connection_id nullable,
action, occurred_at, correlation_id
```

Allowed actions are controlled enums for organization, connection, destination,
binding, rotation, suspension, resumption, and revocation. Actor identity is not
invented before a user/service identity specification.

## OAuth preparation boundary

Future OAuth adapters must follow RFC 9700:

- Authorization Code flow with PKCE `S256`, including confidential clients;
- exact pre-registered redirect URI matching;
- one-time transaction-bound state and PKCE verifier;
- TLS for authorization, token, and resource endpoints;
- minimum provider scopes and audience restriction when supported;
- refresh token rotation or sender constraint when required by provider/client;
- authorization codes, state, verifier, tokens, and client secrets held only in
  short-lived secure state or the vault, never control-plane tables.

TASK-0067 does not implement any OAuth endpoint or transaction state.

## Privacy and observability

Allowed operational attributes are provider key, lifecycle status, action, and a
low-cardinality outcome. Organization, connection, destination, correlation, and
audit identifiers may appear in traces but not metric labels.

Secret references, remote account identifiers, credentials, authorization URLs,
redirect query strings, tokens, request/response bodies, and vault errors are
forbidden in logs, metrics, traces, events, API problems, snapshots, and audit
details.

## Test plan

1. the pure module has no forbidden dependencies;
2. V004 applies after V001 through V003 and creates composite isolation keys;
3. organization creation is deterministic and duplicate IDs fail;
4. every connection and destination operation requires organization scope;
5. cross-organization reads, updates, bindings, and destination references fail;
6. lifecycle accepts only the frozen transitions;
7. suspended organizations and connections cannot activate or enqueue;
8. revoked connections are terminal;
9. initial binding activates a draft connection atomically;
10. database binding failure revokes the newly stored secret;
11. successful rotation increments exactly one version and revokes the old secret;
12. failed rotation commit preserves old binding and revokes the new secret;
13. concurrent stale rotation is rejected;
14. failed old-secret cleanup produces only a controlled cleanup result;
15. fake vault copies and zeroes input and callback bytes on success and failure;
16. revoked secrets cannot be resolved and double revocation is safe;
17. PostgreSQL contains references but no supplied secret bytes or hashes;
18. audit order and controlled actions reproduce exactly without secret refs;
19. preexisting delivery state, retries, and immutable events remain unchanged;
20. no control-plane record can enqueue or receive an existing global event;
21. public API, OpenAPI, Marketplace Operations, and Kernel remain unchanged;
22. production startup makes no control-plane or external API call;
23. repository build remains green.

## References

- OAuth 2.0 Security Best Current Practice, RFC 9700:
  https://www.rfc-editor.org/rfc/rfc9700.html
- Proof Key for Code Exchange, RFC 7636:
  https://www.rfc-editor.org/rfc/rfc7636.html
- OAuth 2.0 Authorization Framework, RFC 6749:
  https://www.rfc-editor.org/rfc/rfc6749.html

## Acceptance

Merging ADR-0009 and SPEC-0009 authorizes TASK-0067 only. Human identity, public
administration, a production vault, OAuth execution, provider configuration,
organization propagation into business data, remote APIs, sync, webhooks,
mapping, and automatic routing require new accepted specifications.
