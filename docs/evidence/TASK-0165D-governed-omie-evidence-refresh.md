# TASK-0165D Evidence — Governed Omie Evidence Refresh

TASK-0165D adds the smallest production seam for the first real Omie
`ListarPedidos` read. `POST /v1/commerce-identity/omie/refresh` is
authenticated, organization-scoped by the service principal, server-bound to
`FLOOOW_OMIE_CONNECTION_ID`, and uses the existing ConnectorRuntime and
PostgreSQL Omie evidence committer.

The connection is rejected unless it is active, belongs to the principal
organization, and is the `omie` static-credential provider. The bounded,
read-only execution reports operational counters only. Connector progress and
page commit keys preserve replay idempotency.

This task does not mutate providers, Economic Truth, identity mappings, or
recovery state. Blank optional connection variables are treated as absent and
invalid non-blank values fail closed. PostgreSQL/Testcontainers validation is
CI-authoritative when Docker is unavailable locally.
