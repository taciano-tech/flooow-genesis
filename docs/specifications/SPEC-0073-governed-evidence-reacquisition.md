# SPEC-0073 — Governed evidence reacquisition

The normal refresh capability and each explicit versioned reacquisition capability
are separate progress namespaces. A page committed in one namespace cannot block
a page in another. Replaying the same namespace is idempotent through the existing
page commit key and integrity checks. New observations coexist with historical
observations and retain source fingerprints and observed timestamps.

Omie typed product extraction uses
`marketplace-economic.omie-transaction-evidence.reacquisition-v2`. The durable
reader continues to include `reacquisition-v1`; advancing the generation never
rewrites or hides an older observation. A new generation is required whenever a
parser improvement must reacquire an already exhausted provider history.

Endpoints:

- `POST /v1/sales-intelligence/reacquire`
- `POST /v1/commerce-identity/omie/reacquire`

Both require the authenticated service principal, accept no body or query, and
resolve the configured connection server-side. Responses contain operational
counts and the generation identifier only.

The source failure category is bounded metadata (`AUTHENTICATION`,
`AUTHORIZATION`, `RATE_LIMIT`, provider, schema, budget, persistence, or
`UNKNOWN`). It never contains provider payloads or secrets.

## Historical Mercado Livre reacquisition bootstrap

Mercado Livre normal order-source ingestion and explicit reacquisition have
different initial temporal semantics.

- normal ingestion starts at the previous fully closed UTC hour;
- `marketplace-economic.order-source.reacquisition-v1` starts 48 fully bounded
  hours before the current UTC hour when no reacquisition progress exists;
- subsequent reads resume exclusively from durable reacquisition progress;
- API orchestration permits at most 48 source pages per reacquisition run and
  retains the existing two-minute request deadline;
- the historical window is evidence reacquisition only. It does not promote an
  identity relation, relax matching, mutate Economic Truth, or write to the
  provider.

The purpose is to reacquire older provider observations after extraction
capabilities improve, while preserving historical observations and independent
progress namespaces.
