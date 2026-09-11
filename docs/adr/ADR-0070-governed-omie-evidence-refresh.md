# ADR-0070 — Governed Omie Evidence Refresh

## Status

Accepted for TASK-0165D.

## Decision

The first production Omie `ListarPedidos` read is exposed only through the
authenticated `POST /v1/commerce-identity/omie/refresh` seam. The server
selects `FLOOOW_OMIE_CONNECTION_ID`; the browser cannot supply an organization
or connection. The request principal supplies the organization boundary.

The seam validates that the configured connection is active, belongs to the
principal organization, uses provider `omie`, and has
`STATIC_API_CREDENTIAL`. It invokes the existing `ConnectorRuntime` with
`OmieTransactionEvidenceCapability` and the existing PostgreSQL committer,
within a bounded deadline and page budget. Replay is delegated to the runtime
progress/commit protocol and is idempotent.

## Boundaries

Omie transaction evidence remains source evidence, not Economic Truth,
identity confirmation, recovery authority, or a financial mutation. The seam
does not call write endpoints, confirm mappings, or execute recovery. Failures
are fail-closed and expose operational metadata only.

Blank optional connection environment values are absent. Non-blank values must
be canonical UUIDs. Mercado Livre behavior is unchanged apart from this generic
optional parsing correction.
