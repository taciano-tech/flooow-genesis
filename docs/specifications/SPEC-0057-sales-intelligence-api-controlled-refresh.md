# SPEC-0057: Sales Intelligence API + Controlled Refresh

Status: Accepted
Date: 2026-09-07
Governing ADR: ADR-0058
Implementation task: TASK-0156B

Authenticated endpoints exactly:

POST /v1/sales-intelligence/refresh
GET /v1/sales-intelligence/orders
GET /v1/sales-intelligence/orders/{marketplaceOrderId}

Organization comes only from ServicePrincipal.

Refresh accepts no credential, organization or connection body. Server config
supplies FLOOOW_MERCADO_LIVRE_CONNECTION_ID. Deadline is no more than two
minutes. Exactly one TASK-0155 run is invoked.

List uses default limit 50 and range 1..200. Cursor is opaque, versioned and
authenticated. Malformed/tampered cursor returns 400.

States:

UNRESOLVED
CALCULATED_COMPLETE
CALCULATED_INCOMPLETE

Complete exposes safe calculated economic result. Incomplete exposes
missing/partial component types. Detail may expose normalized component summaries
but never provider source identity.

Refresh response contains only safe stage counts/status. It contains no
credentials, raw provider payload, connector progress, money or order identity.

Stable errors:

INVALID_SALES_INTELLIGENCE_CURSOR
INVALID_SALES_INTELLIGENCE_ORDER_ID
SALES_INTELLIGENCE_NOT_FOUND
SALES_INTELLIGENCE_READ_FAILURE
LIVE_REFRESH_BLOCKED
LIVE_REFRESH_UNAVAILABLE
LIVE_REFRESH_INTERNAL_FAILURE

Production main composes only already-governed adapters. Test module remains
deterministic and network-free.

No migration.

Exact implementation paths:

1. applications/marketplace-operations-api/build.gradle.kts
2. applications/marketplace-operations-api/src/main/kotlin/io/flooow/marketplace/api/Application.kt
3. applications/marketplace-operations-api/src/main/kotlin/io/flooow/marketplace/api/SalesIntelligenceApi.kt
4. applications/marketplace-operations-api/src/test/kotlin/io/flooow/marketplace/api/ApplicationTest.kt
5. applications/marketplace-operations-api/src/main/resources/openapi.json
6. docs/evidence/TASK-0156B-sales-intelligence-api-controlled-refresh.md
7. docs/journal/MGI-EXECUTIVE-JOURNAL.md

No eighth path. No migration.

Required gates:

:applications:marketplace-operations-api:compileKotlin
:applications:marketplace-operations-api:compileTestKotlin
:applications:marketplace-operations-api:test
:applications:marketplace-live-pipeline:test
:applications:marketplace-operations:test
:applications:marketplace-operations-persistence-postgres:test
build