# ADR-0009: Integration Control Plane Boundary

Status: Proposed

Date: 2026-08-10

## Context

Genesis can produce and coordinate integration events, but it has no accepted
concept of a customer company, provider connection, destination registration, or
credential custody. Enabling a Mercado Livre or ERP adapter before those
boundaries would make global credentials and cross-company data leakage likely.

The current service bearer token authenticates one technical API client. It does
not identify a company, user, role, consent grant, or external account and cannot
be reused as a tenant authorization model.

## Decision

Introduce an application-owned Integration Control Plane with four responsibilities:

1. define an opaque organization scope for data isolation;
2. register provider connections owned by exactly one organization;
3. register logical delivery destinations bound to those connections;
4. bind connections to opaque secret-manager references without storing secret
   material in PostgreSQL.

These are product and infrastructure concepts, not universal organizational
semantics. They do not enter the Kernel. The control plane describes who owns a
connection and whether it may operate; provider adapters remain responsible for
protocol-specific behavior.

## Separation of planes

- **Control plane:** organization scope, connection metadata, lifecycle,
  destination registration, credential reference, and audit history;
- **Data plane:** polling, webhooks, API calls, rate limits, sync cursors, mapping,
  event delivery, retries, and business data;
- **Secret plane:** token and key custody, encryption, access policy, rotation,
  and deletion inside an external vault adapter.

No plane may infer business authority from a valid provider credential. A token
permits protocol access only; Genesis rules still govern inventory, price, order,
and financial actions.

## Organization scope

`IntegrationOrganizationId` is an opaque UUID tenancy boundary for integrations.
It does not claim to model the universal meaning of an organization, legal
entity, billing account, user group, or Kernel `Entity`.

Every connection, destination, audit entry, and future sync cursor must carry the
organization ID. Repository operations require both organization and resource ID,
and PostgreSQL relationships use composite keys to prevent cross-organization
references.

## Credential custody

PostgreSQL stores only an opaque `secret_ref`. It never stores access tokens,
refresh tokens, API keys, client secrets, passwords, private keys, authorization
codes, PKCE verifiers, or decrypted secret JSON.

A `SecretVault` port exposes scoped use of mutable bytes and zeroes the caller's
buffer after use. Concrete secret storage, IAM, encryption keys, and production
rotation remain deployment decisions.

## Alternatives considered

### Store encrypted tokens in the application database

Rejected for the first control plane. Application-level encryption would still
place ciphertext, key-management responsibility, backups, and accidental
decryption paths in the operational database.

### Put company or connection concepts in the Kernel

Rejected. Integration tenancy and provider authorization are application
boundaries, not proven universal primitives of organizational computation.

### Use the current bearer token as the company identity

Rejected. It is one deployment credential with no tenant, subject, scope,
revocation inventory, or delegated-consent semantics.

### Implement Mercado Livre OAuth immediately

Deferred. Provider behavior must depend on an accepted connection and secret
boundary, not define those foundations accidentally.

### Build a universal connector DSL now

Deferred. The first real adapters must reveal stable variation points before a
mapping language or connector SDK is standardized.

## Consequences

- company isolation becomes explicit before real data arrives;
- secrets remain outside business and event storage;
- destinations can be validated before delivery rows are enqueued;
- OAuth and static credentials can share custody without sharing protocol logic;
- provider APIs, user identity, consent UI, and production vault selection still
  require later specifications.

## Authorization

SPEC-0009 freezes the minimum control-plane contracts. This ADR alone authorizes
no database, endpoint, OAuth redirect, credential, or provider call.
