# SPEC-0071 — Durable Commerce Identity Recompute

## Read ports

`MercadoLivreIdentityEvidenceReader` and `OmieIdentityEvidenceReader` are
organization-scoped, read-only application ports. Implementations return
bounded, deterministic domain evidence and never expose raw provider JSON.

The PostgreSQL implementation reads the existing durable source tables. Omie
rows are ordered by order reference, observation time, progress version, and
ordinal; the first row per order is reconstructed with product references,
quantities, stable ERP references, amount/currency when present, transaction
time when present, and provenance fingerprint. Mercado Livre rows are selected
deterministically per external order and reconstruct order, pack, shipment,
item references, amount/currency and timestamps. Item references remain item
identity; they are not treated as seller SKU.

## Recompute endpoint

`POST /v1/commerce-identity/recompute` requires the service bearer, accepts no
query parameters or body, and derives organization solely from the principal.
It calls the existing evaluator with policy `MGI_GENESIS_IDENTITY_V1`, stores
only the derived current evaluation in memory, and returns aggregate metadata.
`GET /v1/commerce-identity/health` and relations read that evaluation.

The endpoint performs no provider call, no persistence write, no identity
confirmation, no Economic Truth mutation, and no recovery action.
