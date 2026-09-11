# ADR-0058: Sales Intelligence API + Controlled Refresh

Status: Accepted
Date: 2026-09-07

Expose authenticated Sales Intelligence through the existing
marketplace-operations-api.

Exactly:

POST /v1/sales-intelligence/refresh
GET /v1/sales-intelligence/orders
GET /v1/sales-intelligence/orders/{marketplaceOrderId}

HTTP remains adapter-only.

Refresh delegates to TASK-0155 once per request with a bounded deadline.
List/detail delegate only to the durable projection.

Organization is authenticated/server-owned. The first MVP Mercado Livre
connection is server-owned configuration.

No migration, background execution, scheduler, direct provider call, inline
OAuth refresh or HTTP-layer economic calculation is introduced.