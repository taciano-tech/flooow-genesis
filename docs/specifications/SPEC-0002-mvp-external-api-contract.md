# SPEC-0002: MVP External API Contract

Status: Accepted

Date: 2026-08-10

Related decision: `ADR-0003-mvp-external-api-boundary.md`

## Objective

Expose the existing Marketplace Operations inventory-risk assessment as a
small, deterministic HTTP API without exposing transport concerns to the domain
or Kernel concerns to external clients.

## Authorized implementation

TASK-0054 may add `applications:marketplace-operations-api` with:

- Ktor server 3.5.1 on JVM 21;
- kotlinx.serialization JSON;
- an application factory usable by `testApplication`;
- a production entry point using configuration for host and port;
- the routes and DTOs defined below;
- a committed OpenAPI 3.1 document matching the tested contract.

No other production module may be changed except build/module registration and
the minimum application visibility needed for delegation. The Kernel must
remain byte-for-byte unchanged.

## Media types and JSON rules

- Successful and validation responses use UTF-8 JSON.
- The assessment endpoint consumes and produces `application/json`.
- Errors use `application/problem+json`.
- Unknown request properties are rejected.
- Dates use ISO-8601 calendar form `YYYY-MM-DD`.
- Enum values use the existing uppercase business names.
- Response fields with no value are omitted rather than serialized as `null`.

## Assessment endpoint

### Request

```http
POST /v1/marketplace-operations/inventory-risk-assessments
Content-Type: application/json
```

```json
{
  "sku": "RED-MOTO-001",
  "periodEnd": "2026-08-31",
  "targetUnits": 1000,
  "unitsSold": 640,
  "availableUnits": 90,
  "dailySalesVelocity": 15,
  "observedOn": "2026-08-10",
  "expectedReplenishmentOn": "2026-08-20"
}
```

Every property is required. The adapter maps these fields exactly to
`InventoryRiskInput`; it must not normalize SKU, dates, or quantities.

### Success response

Status: `200 OK`

```json
{
  "sku": "RED-MOTO-001",
  "observedOn": "2026-08-10",
  "projection": {
    "stockCoverageDays": 6,
    "projectedStockoutOn": "2026-08-16",
    "expectedReplenishmentOn": "2026-08-20",
    "projectedStockoutDays": 4,
    "unitsPotentiallyUnavailable": 60,
    "unitsRemainingToGoal": 360,
    "unitsAtRiskAgainstGoal": 60,
    "shortageProjected": true
  },
  "recommendation": {
    "type": "EXPEDITE_REPLENISHMENT",
    "explanation": "Expedite replenishment to arrive no later than 2026-08-16.",
    "expectedUnitsPreserved": 60
  },
  "expectedImpact": "If completed before the projected stockout, the intervention is expected to preserve up to 60 units toward the goal; this is a projection, not a guaranteed outcome.",
  "trace": [
    "goal.remaining=360",
    "inventory.available=90",
    "velocity.daily=15"
  ]
}
```

The illustrative trace above is abbreviated. The actual response must retain
the complete ordered business trace returned by `InventoryRiskAssessment`.

The recommendation DTO maps the unique `InterventionAlternative` whose
`explanation` equals the selected domain `Decision.statement`. Zero or multiple
matches are internal consistency failures and map to the generic 500 contract;
the route must not infer a recommendation from trace strings.

The API deliberately excludes the legacy `evaluation`, `decisionContext`, and
raw Kernel judgment. It also excludes the directional structured judgment until
a production migration specification defines its semantics.

## Error contract

Every problem response has this shape:

```json
{
  "type": "https://flooow.io/problems/invalid-inventory-risk-request",
  "title": "Invalid inventory risk request",
  "status": 422,
  "detail": "Daily sales velocity must be positive",
  "instance": "/v1/marketplace-operations/inventory-risk-assessments",
  "code": "INVALID_INVENTORY_RISK_REQUEST"
}
```

Required mappings:

| Condition | Status | Code |
| --- | --- | --- |
| Malformed JSON, missing field, wrong JSON type, unknown field, invalid date format | `400 Bad Request` | `MALFORMED_REQUEST` |
| Valid JSON that violates `InventoryRiskInput` invariants | `422 Unprocessable Content` | `INVALID_INVENTORY_RISK_REQUEST` |
| Unsupported request media type | `415 Unsupported Media Type` | `UNSUPPORTED_MEDIA_TYPE` |
| Unmatched route | `404 Not Found` | `RESOURCE_NOT_FOUND` |
| Unexpected server failure | `500 Internal Server Error` | `INTERNAL_ERROR` |

Problem `detail` may contain stable domain validation text. A 500 response must
use a generic detail and must not expose exception, package, class, filesystem,
or stack-trace information.

## Health endpoints

```text
GET /health/live
GET /health/ready
```

Both return `200 OK` and `{"status":"UP"}` while the stateless process can
serve requests. They do not invoke Marketplace Operations or the Kernel.

## OpenAPI

The implementation must commit an OpenAPI 3.1 document and expose it at:

```text
GET /openapi.json
```

The committed document is the reviewable source of truth. Runtime generation
may be evaluated later, but TASK-0054 must not depend on compiler-plugin schema
inference. A contract test must compare the served document with the committed
resource.

## Determinism and side effects

- The route is synchronous.
- It performs no persistence, messaging, network call, or autonomous action.
- It does not read system time to calculate the assessment.
- Repeating the same valid JSON body produces byte-equivalent JSON, apart from
  transport headers not covered by this specification.
- POST is computation-only in this MVP; it creates no durable resource and
  therefore returns 200 rather than 201.

## Dependency rules

```text
marketplace-operations-api -> marketplace-operations -> kernel
```

Forbidden:

- `marketplace-operations-api -> kernel` direct dependency;
- Ktor or serialization dependencies in `marketplace-operations` or `kernel`;
- HTTP DTOs in the business application;
- business evaluation inside a route handler;
- serializing domain or Kernel objects directly as the public response.

## Test plan

TASK-0054 must include:

1. exact success status, content type, and golden JSON for the Red Moto case;
2. byte-equivalent repeated-response test;
3. no-shortage success case;
4. malformed JSON and each missing required property;
5. unknown property rejection;
6. invalid ISO date;
7. representative domain invariant failures mapped to 422;
8. unsupported media type;
9. unknown route;
10. generic 500 response with no internal disclosure;
11. liveness and readiness routes;
12. served OpenAPI equality with the committed resource;
13. dependency verification proving the API has no direct Kernel dependency;
14. existing repository tests and frozen snapshots unchanged.

Repository validation:

```text
./gradlew clean build --rerun-tasks --no-daemon --no-configuration-cache
```

## Rollout and rollback

TASK-0054 is additive. Rollback removes only the API module and its registration.
Marketplace Operations and the Kernel remain usable in process throughout.

## Explicitly out of scope

- authentication and authorization;
- persistence and assessment retrieval;
- idempotency storage;
- marketplace connectors;
- message brokers and CloudEvents;
- distributed tracing beyond framework-neutral logging;
- rate limiting and public internet deployment;
- production migration from legacy to directional reasoning;
- any Kernel modification.

Each out-of-scope capability requires a separate accepted specification.

## Acceptance

Acceptance of ADR-0003 and this specification authorizes TASK-0054 only. The
implementation PR must demonstrate every test-plan item and may not broaden the
scope through convenience dependencies or implicit framework behavior.
