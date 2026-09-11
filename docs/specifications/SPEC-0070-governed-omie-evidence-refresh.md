# SPEC-0070 — Governed Omie Evidence Refresh

## Endpoint

`POST /v1/commerce-identity/omie/refresh`

The endpoint requires the service bearer principal, accepts no query
parameters and no request body, and derives organization exclusively from
`ServicePrincipal.organizationId`. The configured server-side
`FLOOOW_OMIE_CONNECTION_ID` is the only connection source.

## Execution contract

The endpoint validates the active organization-scoped control-plane context,
then invokes `ConnectorRuntime` with capability
`marketplace-economic.omie-transaction-evidence`. At most ten bounded pages
are attempted, with a two-minute deadline, 100 records per page, and a 2 MiB
response budget. The existing `PostgresOmieTransactionEvidenceCommitter`
persists pages and progress; retries/replays therefore produce committed or
already-committed outcomes without duplicating evidence.

Success returns only `status`, `connectionStatus`, invocation/page counters,
and record count. Provider payloads, credentials, secret references and raw
economic data are never returned. Provider failures return a safe 503.

## Non-goals

No Omie write-back, Mercado Livre mutation, Economic Truth write, identity
confirmation, recovery proposal, authority decision, or execution is part of
this contract. Existing commerce identity evaluation remains the sole matching
authority; ingestion supplies evidence only.
