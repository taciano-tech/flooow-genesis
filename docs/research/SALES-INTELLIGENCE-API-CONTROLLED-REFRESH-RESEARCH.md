# Sales Intelligence API + Controlled Refresh Research

Date: 2026-09-07
Status: Concluded for TASK-0156B

TASK-0155 provides the bounded live economic pipeline. TASK-0156A provides
production-capable encrypted runtime secret/progress adapters. The durable Sales
Intelligence projection already exposes organization-scoped list/detail reads.

The first product-facing seam is:

POST /v1/sales-intelligence/refresh
GET  /v1/sales-intelligence/orders
GET  /v1/sales-intelligence/orders/{marketplaceOrderId}

HTTP remains an adapter. Refresh invokes one bounded TASK-0155 run. Reads
delegate only to the durable Sales Intelligence projection.

Authenticated organization identity is server-owned. The first Mercado Livre
connection is server configuration. No request may choose another organization,
credential or connection.

No migration, scheduler, background worker, direct provider logic in routes,
inline OAuth refresh or HTTP-layer economic calculation is authorized.

This boundary is also the first product telemetry seam. A later separate
Traction & Value Ledger may record activation, Time To Truth, economic-truth
coverage and engagement without mutating canonical Economic Truth.