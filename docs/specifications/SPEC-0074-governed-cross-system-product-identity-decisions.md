# SPEC-0074 — Governed cross-system product identity decisions

An identity relation contains:

- organization ID;
- server-selected Mercado Livre and Omie connection IDs;
- Mercado Livre item ID and seller SKU;
- Omie provider product ID.

An immutable decision contains a caller-generated canonical decision UUID,
decision kind (`CONFIRMED` or `REJECTED`), reason, server-derived principal,
bounded provenance, correlation UUID, decision time, revision, and optional
superseded decision UUID. Initial reason must match the decision kind;
superseding decisions use `CORRECTION`.

Writes require active organization and exact active provider connections plus
an exact durable Mercado Livre item/SKU observation and exact durable Omie
product-cost observation. An identical decision-ID replay returns the stored
decision. A reused ID with different content fails integrity checks. A
supersession must target the current leaf of the same exact relation.

At most one current confirmed Omie target may exist for a scoped Mercado Livre
identity. Rejected alternatives may coexist. Conflicting confirmations return
conflict and resolve fail closed. Historical rows may neither update nor
delete.

API:

- `POST /v1/commerce-identity/product-decisions`
- `GET /v1/commerce-identity/product-decisions/{decisionId}`

Both routes require service-bearer authentication. Requests contain no
organization or connection selector. Reads are organization scoped. The API
does not accept fuzzy attributes or economic values and cannot write to either
provider.
