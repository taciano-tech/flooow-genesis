# ADR-0069 — Governed Omie Static-Credential Bootstrap

## Status

Accepted for TASK-0165C.

## Decision

Genesis exposes one authenticated, organization-scoped provisioning endpoint for
an Omie static credential. The endpoint creates a draft control-plane connection,
serializes the versioned Omie credential envelope, and binds it through the
existing `IntegrationControlPlaneService` and secure vault. The serialized
`ByteArray` is zeroized after the bind attempt.

The request is the only place the credential is accepted. Organization scope is
read exclusively from the authenticated `ServicePrincipal`; provider, connection
and secret-reference fields are never client supplied or returned.

## Boundaries

This is credential provisioning only. It does not call `ListarPedidos`, perform
identity matching, write Economic Truth, create recovery authority, or mutate
Omie business data. A failed bind is fail-closed and cannot produce `READY`.
The existing vault/control-plane and Omie connector remain the sole persistence
and read boundaries.

## Rejected

- SQL or manual vault writes.
- Environment variables as a permanent credential store.
- Credential listing or read-back endpoints.
- Provider reads or business mutations during bootstrap.
