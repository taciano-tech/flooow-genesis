package io.flooow.marketplace.api

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.marketplace.operations.economics.EconomicCalculationPolicyVersion
import io.flooow.marketplace.operations.economics.EconomicComponentType
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthAssemblyNotReadyReason
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthAssemblyPolicyVersion
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthQuality
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.evidence.ChangeSequenceCheckpoint
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceVersion
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceCalculationSnapshot
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceContributionMarginSnapshot
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjection
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionCursor
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionPage
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionReadResult
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionRecord
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionWriteResult
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceState
import io.flooow.marketplace.operations.live.MarketplaceLivePipelineBlockDetail
import io.flooow.marketplace.operations.live.MarketplaceLivePipelineProjectionSummary
import io.flooow.marketplace.operations.live.MarketplaceLivePipelinePromotionSummary
import io.flooow.marketplace.operations.live.MarketplaceLivePipelineResult
import io.flooow.marketplace.operations.live.MarketplaceLivePipelineSourceStop
import io.flooow.marketplace.operations.live.MarketplaceLivePipelineSourceSummary
import io.flooow.marketplace.operations.inventory.InventoryRiskEvaluator
import io.flooow.organization.OrganizationId
import io.ktor.server.application.Application
import io.ktor.client.request.get
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.math.BigDecimal
import java.util.UUID

class ApplicationTest {

    @Test
    fun `Mercado Livre credential rotation requires service bearer`() = testApplication {
        application {
            configureApi(
                ServiceToken.test(TEST_SERVICE_TOKEN),
                TEST_ORGANIZATION_ID,
                record = { _, _ -> error("not used") },
                mercadoLivreCredentialRotationApi = testMercadoLivreCredentialRotationApi()
            )
        }

        val response = client.post(MERCADO_LIVRE_CREDENTIAL_ROTATION_PATH)

        assertProblem(
            response.status,
            response.bodyAsText(),
            401,
            "AUTHENTICATION_REQUIRED"
        )
    }

    @Test
    fun `Mercado Livre credential rotation rejects body and query`() = testApplication {
        application {
            configureApi(
                ServiceToken.test(TEST_SERVICE_TOKEN),
                TEST_ORGANIZATION_ID,
                record = { _, _ -> error("not used") },
                mercadoLivreCredentialRotationApi = testMercadoLivreCredentialRotationApi()
            )
        }

        val queryResponse = client.post(
            "$MERCADO_LIVRE_CREDENTIAL_ROTATION_PATH?unexpected=true"
        ) {
            bearerAuth(TEST_SERVICE_TOKEN)
        }

        val bodyResponse = client.post(MERCADO_LIVRE_CREDENTIAL_ROTATION_PATH) {
            bearerAuth(TEST_SERVICE_TOKEN)
            setBody("{}")
        }

        assertProblem(
            queryResponse.status,
            queryResponse.bodyAsText(),
            400,
            "MALFORMED_REQUEST"
        )
        assertProblem(
            bodyResponse.status,
            bodyResponse.bodyAsText(),
            400,
            "MALFORMED_REQUEST"
        )
    }

    @Test
    fun `red moto request returns exact committed contract`() = testApplication {
        application { module() }

        val response = client.post(assessmentPath) {
            bearerAuth(TEST_SERVICE_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(redMotoRequest)
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(
            "$assessmentPath/11111111-1111-4111-8111-111111111111",
            response.headers["Location"]
        )
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals(resource("/red-moto-success.json").trimEnd(), response.bodyAsText())
    }

    @Test
    fun `equivalent requests have stable business result and distinct identities`() = testApplication {
        application { module() }

        suspend fun execute() = client.post(assessmentPath) {
            bearerAuth(TEST_SERVICE_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(redMotoRequest)
        }.bodyAsText().let { Json.parseToJsonElement(it).jsonObject }

        val first = execute()
        val second = execute()
        assertNotEquals(first.getValue("assessmentId"), second.getValue("assessmentId"))
        assertEquals(
            first.filterKeys { it !in setOf("assessmentId", "recordedAt") },
            second.filterKeys { it !in setOf("assessmentId", "recordedAt") }
        )
    }

    @Test
    fun `no shortage returns take no action`() = testApplication {
        application { module() }
        val request = redMotoRequest.replace("\"availableUnits\":90", "\"availableUnits\":300")

        val response = client.post(assessmentPath) {
            bearerAuth(TEST_SERVICE_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject

        assertEquals(HttpStatusCode.Created, response.status)
        assertFalse(body.getValue("projection").jsonObject
            .getValue("shortageProjected").jsonPrimitive.boolean)
        assertEquals(
            "TAKE_NO_ACTION",
            body.getValue("recommendation").jsonObject.getValue("type").jsonPrimitive.content
        )
    }

    @Test
    fun `created assessment is retrievable from location`() = testApplication {
        application { module() }
        val created = client.post(assessmentPath) {
            bearerAuth(TEST_SERVICE_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(redMotoRequest)
        }
        val location = assertNotNull(created.headers["Location"])

        val retrieved = client.get(location) { bearerAuth(TEST_SERVICE_TOKEN) }

        assertEquals(HttpStatusCode.OK, retrieved.status)
        assertEquals(created.bodyAsText(), retrieved.bodyAsText())
    }

    @Test
    fun `malformed and missing assessment identifiers use specific problems`() = testApplication {
        application { module() }

        val malformed = client.get("$assessmentPath/not-a-uuid") {
            bearerAuth(TEST_SERVICE_TOKEN)
        }
        val missing = client.get(
            "$assessmentPath/99999999-9999-4999-8999-999999999999"
        ) { bearerAuth(TEST_SERVICE_TOKEN) }

        assertProblem(malformed.status, malformed.bodyAsText(), 400, "MALFORMED_ASSESSMENT_ID")
        assertProblem(missing.status, missing.bodyAsText(), 404, "ASSESSMENT_NOT_FOUND")
    }

    @Test
    fun `malformed JSON and wrong types return 400`() = testApplication {
        application { module() }

        listOf(
            "{",
            redMotoRequest.replace("\"targetUnits\":1000", "\"targetUnits\":\"many\"")
        ).forEach { body ->
            val response = client.post(assessmentPath) {
                bearerAuth(TEST_SERVICE_TOKEN)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            assertProblem(response.status, response.bodyAsText(), 400, "MALFORMED_REQUEST")
        }
    }

    @Test
    fun `every missing required property returns 400`() = testApplication {
        application { module() }
        val request = Json.parseToJsonElement(redMotoRequest).jsonObject

        request.keys.forEach { omitted ->
            val body = request.filterKeys { it != omitted }
                .entries.joinToString(prefix = "{", postfix = "}") { (key, value) ->
                    "\"$key\":$value"
                }
            val response = client.post(assessmentPath) {
                bearerAuth(TEST_SERVICE_TOKEN)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            assertProblem(response.status, response.bodyAsText(), 400, "MALFORMED_REQUEST")
        }
    }

    @Test
    fun `unknown property and invalid date return 400`() = testApplication {
        application { module() }
        val cases = listOf(
            redMotoRequest.dropLast(1) + ",\"unexpected\":true}",
            redMotoRequest.replace("2026-08-31", "31/08/2026")
        )

        cases.forEach { body ->
            val response = client.post(assessmentPath) {
                bearerAuth(TEST_SERVICE_TOKEN)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            assertProblem(response.status, response.bodyAsText(), 400, "MALFORMED_REQUEST")
        }
    }

    @Test
    fun `domain invariant violations return 422`() = testApplication {
        application { module() }
        val cases = listOf(
            redMotoRequest.replace("\"dailySalesVelocity\":15", "\"dailySalesVelocity\":0"),
            redMotoRequest.replace("\"availableUnits\":90", "\"availableUnits\":-1"),
            redMotoRequest.replace("\"sku\":\"RED-MOTO-001\"", "\"sku\":\" RED-MOTO-001\"")
        )

        cases.forEach { body ->
            val response = client.post(assessmentPath) {
                bearerAuth(TEST_SERVICE_TOKEN)
                contentType(ContentType.Application.Json)
                setBody(body)
            }
            assertProblem(
                response.status,
                response.bodyAsText(),
                422,
                "INVALID_INVENTORY_RISK_REQUEST"
            )
        }
    }

    @Test
    fun `unsupported media type returns 415`() = testApplication {
        application { module() }

        val response = client.post(assessmentPath) {
            bearerAuth(TEST_SERVICE_TOKEN)
            contentType(ContentType.Text.Plain)
            setBody(redMotoRequest)
        }

        assertEquals(
            ContentType.parse("application/problem+json"),
            response.contentType()?.withoutParameters()
        )
        assertProblem(response.status, response.bodyAsText(), 415, "UNSUPPORTED_MEDIA_TYPE")
    }

    @Test
    fun `unknown route returns 404`() = testApplication {
        application { module() }

        val response = client.get("/does-not-exist")

        assertProblem(response.status, response.bodyAsText(), 404, "RESOURCE_NOT_FOUND")
    }

    @Test
    fun `unexpected failure returns generic 500 without disclosure`() = testApplication {
        application {
            configureApi(
                serviceToken = ServiceToken.test(TEST_SERVICE_TOKEN),
                serviceOrganizationId = TEST_ORGANIZATION_ID,
                record = { _, _ -> error("secret filesystem C:/internal/path") }
            )
        }

        val response = client.post(assessmentPath) {
            bearerAuth(TEST_SERVICE_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(redMotoRequest)
        }
        val body = response.bodyAsText()

        assertProblem(response.status, body, 500, "INTERNAL_ERROR")
        assertFalse(body.contains("secret"))
        assertFalse(body.contains("C:/internal"))
        assertFalse(body.contains("IllegalStateException"))
    }

    @Test
    fun `missing and invalid credentials return indistinguishable challenge`() = testApplication {
        application { module() }

        val missing = client.post(assessmentPath)
        val invalid = listOf(
            client.post(assessmentPath) { bearerAuth("invalid-token") },
            client.post(assessmentPath) {
                header(HttpHeaders.Authorization, "Basic abc123")
            },
            client.post(assessmentPath) {
                bearerAuth(TEST_SERVICE_TOKEN.replaceFirst('t', 'T'))
            },
            client.post(assessmentPath) {
                header(HttpHeaders.Authorization, "Bearer ${TEST_SERVICE_TOKEN.dropLast(1)}")
            },
            client.post(assessmentPath) {
                headers.append(HttpHeaders.Authorization, "Bearer invalid-one")
                headers.append(HttpHeaders.Authorization, "Bearer invalid-two")
            }
        )

        listOf(missing, *invalid.toTypedArray()).forEachIndexed { index, response ->
            assertEquals(HttpStatusCode.Unauthorized, response.status, "credential case $index")
            assertProblem(response.status, response.bodyAsText(), 401, "AUTHENTICATION_REQUIRED")
            assertEquals(
                "Bearer realm=flooow-marketplace-operations",
                response.headers["WWW-Authenticate"]
            )
            assertEquals("no-store", response.headers["Cache-Control"])
        }
        invalid.forEach { assertEquals(missing.bodyAsText(), it.bodyAsText()) }
        assertFalse(missing.bodyAsText().contains(TEST_SERVICE_TOKEN))
    }

    @Test
    fun `authentication runs before request parsing and business evaluation`() = testApplication {
        var evaluations = 0
        application {
            configureApi(
                serviceToken = ServiceToken.test(TEST_SERVICE_TOKEN),
                serviceOrganizationId = TEST_ORGANIZATION_ID,
                record = { _, _ ->
                    evaluations += 1
                    error("must not evaluate")
                }
            )
        }

        val response = client.post(assessmentPath) {
            contentType(ContentType.Application.Json)
            setBody("not-json")
        }

        assertProblem(response.status, response.bodyAsText(), 401, "AUTHENTICATION_REQUIRED")
        assertEquals(0, evaluations)
    }

    @Test
    fun `authentication runs before persistence lookup`() = testApplication {
        var lookups = 0
        application {
            configureApi(
                serviceToken = ServiceToken.test(TEST_SERVICE_TOKEN),
                serviceOrganizationId = TEST_ORGANIZATION_ID,
                record = { _, _ -> error("must not record") },
                findById = { _, _ ->
                    lookups += 1
                    error("must not query")
                }
            )
        }

        val response = client.get(
            "$assessmentPath/99999999-9999-4999-8999-999999999999"
        )

        assertProblem(response.status, response.bodyAsText(), 401, "AUTHENTICATION_REQUIRED")
        assertEquals(0, lookups)
    }

    @Test
    fun `authenticated organization is server owned and request headers cannot override it`() =
        testApplication {
            var observedOrganization = TEST_ORGANIZATION_ID
            application {
                configureApi(
                    serviceToken = ServiceToken.test(TEST_SERVICE_TOKEN),
                    serviceOrganizationId = TEST_ORGANIZATION_ID,
                    record = { _, _ -> error("must not record") },
                    findById = { organizationId, _ ->
                        observedOrganization = organizationId
                        null
                    }
                )
            }

            val response = client.get(
                "$assessmentPath/11111111-1111-4111-8111-111111111111"
            ) {
                bearerAuth(TEST_SERVICE_TOKEN)
                header(
                    "X-Organization-Id",
                    "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
                )
            }

            assertEquals(HttpStatusCode.NotFound, response.status)
            assertEquals(TEST_ORGANIZATION_ID, observedOrganization)
        }

    @Test
    fun `service token configuration fails safely`() {
        val valid = "A".repeat(43)
        ServiceToken.fromEnvironment(mapOf("FLOOOW_SERVICE_TOKEN" to valid))

        listOf(
            emptyMap(),
            mapOf("FLOOOW_SERVICE_TOKEN" to "short"),
            mapOf("FLOOOW_SERVICE_TOKEN" to " $valid"),
            mapOf("FLOOOW_SERVICE_TOKEN" to "${valid.dropLast(1)}\n"),
            mapOf("FLOOOW_SERVICE_TOKEN" to LOCAL_SERVICE_TOKEN)
        ).forEach { environment ->
            val error = kotlin.runCatching {
                ServiceToken.fromEnvironment(environment)
            }.exceptionOrNull()
            assertNotNull(error)
            assertFalse(error.message.orEmpty().contains(valid))
            assertFalse(error.message.orEmpty().contains(LOCAL_SERVICE_TOKEN))
        }

        ServiceToken.fromEnvironment(
            mapOf(
                "FLOOOW_SERVICE_TOKEN" to LOCAL_SERVICE_TOKEN,
                "FLOOOW_ENVIRONMENT" to "local"
            )
        )

        assertEquals(
            TEST_ORGANIZATION_ID,
            serviceOrganizationFromEnvironment(
                mapOf("FLOOOW_SERVICE_ORGANIZATION_ID" to TEST_ORGANIZATION_ID.toString())
            )
        )
        listOf(emptyMap(), mapOf("FLOOOW_SERVICE_ORGANIZATION_ID" to "not-a-uuid"))
            .forEach { environment ->
                assertNotNull(
                    kotlin.runCatching {
                        serviceOrganizationFromEnvironment(environment)
                    }.exceptionOrNull()
                )
            }
    }

    @Test
    fun `health endpoints are available without business evaluation`() = testApplication {
        application {
            configureApi(
                serviceToken = ServiceToken.test(TEST_SERVICE_TOKEN),
                serviceOrganizationId = TEST_ORGANIZATION_ID,
                record = { _, _ -> error("business evaluation must not run") }
            )
        }

        listOf("/health/live", "/health/ready").forEach { path ->
            val response = client.get(path)
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("{\"status\":\"UP\"}", response.bodyAsText())
        }
    }

    @Test
    fun `served OpenAPI equals committed resource`() = testApplication {
        application { module() }

        val response = client.get("/openapi.json") {
            bearerAuth(TEST_SERVICE_TOKEN)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(resource("/openapi.json"), response.bodyAsText())
        assertTrue(response.bodyAsText().contains("\"openapi\": \"3.1.0\""))
        assertTrue(response.bodyAsText().contains("\"serviceBearer\""))
    }

    @Test
    fun `sales list is organization scoped bounded and uses authenticated cursor`() =
        testApplication {
            val firstRecord = unresolvedRecord()
            val next = MarketplaceSalesIntelligenceProjectionCursor(
                firstRecord.projectedAt,
                firstRecord.marketplaceOrderId
            )
            val projection = FakeSalesProjection(
                listResult = MarketplaceSalesIntelligenceProjectionReadResult.Success(
                    MarketplaceSalesIntelligenceProjectionPage(listOf(firstRecord), next)
                )
            )
            application { configureForSales(projection) }

            val first = client.get(SALES_INTELLIGENCE_ORDERS_PATH) {
                bearerAuth(TEST_SERVICE_TOKEN)
                header("X-Organization-Id", "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
            }
            val firstJson = Json.parseToJsonElement(first.bodyAsText()).jsonObject
            val cursor = firstJson.getValue("nextCursor").jsonPrimitive.content

            assertEquals(HttpStatusCode.OK, first.status)
            assertEquals(TEST_ORGANIZATION_ID, projection.lastOrganization)
            assertEquals(50, projection.lastLimit)
            assertEquals("UNRESOLVED", firstJson.getValue("orders").jsonArray.single()
                .jsonObject.getValue("state").jsonPrimitive.content)
            assertFalse(first.bodyAsText().contains(TEST_ORGANIZATION_ID.toString()))

            val second = client.get("$SALES_INTELLIGENCE_ORDERS_PATH?limit=1&cursor=$cursor") {
                bearerAuth(TEST_SERVICE_TOKEN)
            }
            assertEquals(HttpStatusCode.OK, second.status)
            assertEquals(1, projection.lastLimit)
            assertEquals(next, projection.lastCursor)

            val tampered = client.get(
                "$SALES_INTELLIGENCE_ORDERS_PATH?cursor=${cursor.dropLast(1)}A"
            ) { bearerAuth(TEST_SERVICE_TOKEN) }
            assertProblem(
                tampered.status,
                tampered.bodyAsText(),
                400,
                "INVALID_SALES_INTELLIGENCE_CURSOR"
            )
        }

    @Test
    fun `sales cursor is bound to organization and invalid limits never reach persistence`() {
        val codec = SalesIntelligenceCursorCodec("cursor-secret".toByteArray())
        val cursor = MarketplaceSalesIntelligenceProjectionCursor(
            Instant.parse("2026-09-07T12:00:00.123456Z"),
            MarketplaceOrderId(UUID.fromString("33333333-3333-4333-8333-333333333333"))
        )
        val encoded = codec.encode(cursor, TEST_ORGANIZATION_ID)
        assertEquals(cursor, codec.decode(encoded, TEST_ORGANIZATION_ID))
        assertEquals(
            null,
            codec.decode(
                encoded,
                OrganizationId.parse("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
            )
        )

        testApplication {
            val projection = FakeSalesProjection()
            application { configureForSales(projection) }
            listOf("0", "201", "x", "1&limit=2").forEach { limit ->
                val response = client.get("$SALES_INTELLIGENCE_ORDERS_PATH?limit=$limit") {
                    bearerAuth(TEST_SERVICE_TOKEN)
                }
                assertProblem(
                    response.status,
                    response.bodyAsText(),
                    400,
                    "INVALID_SALES_INTELLIGENCE_CURSOR"
                )
            }
            assertEquals(0, projection.listCalls)
        }
    }

    @Test
    fun `sales detail exposes closed projection states without provider identity`() =
        testApplication {
            val unresolved = unresolvedRecord()
            val incomplete = incompleteRecord()
            val complete = completeRecord()
            val records = listOf(unresolved, incomplete, complete).associateBy {
                it.marketplaceOrderId
            }
            val projection = FakeSalesProjection(
                detail = { orderId ->
                    MarketplaceSalesIntelligenceProjectionReadResult.Success(records[orderId])
                }
            )
            application { configureForSales(projection) }

            val bodies = records.keys.map { orderId ->
                val response = client.get(
                    "$SALES_INTELLIGENCE_ORDERS_PATH/${orderId.value}"
                ) { bearerAuth(TEST_SERVICE_TOKEN) }
                assertEquals(HttpStatusCode.OK, response.status)
                response.bodyAsText()
            }

            assertTrue(bodies[0].contains("UNRESOLVED"))
            assertTrue(bodies[1].contains("CALCULATED_INCOMPLETE"))
            assertTrue(bodies[1].contains("PRODUCT_COST"))
            assertTrue(bodies[2].contains("CALCULATED_COMPLETE"))
            assertTrue(bodies[2].contains("\"grossRevenue\":\"125.4\""))
            bodies.forEach {
                assertFalse(it.contains("external-order-secret"))
                assertFalse(it.contains("provider"))
                assertFalse(it.contains("systemKey"))
                assertFalse(it.contains("externalReference"))
            }
        }

    @Test
    fun `sales detail returns stable invalid missing and read failure problems`() =
        testApplication {
            val projection = FakeSalesProjection()
            application { configureForSales(projection) }

            val invalid = client.get("$SALES_INTELLIGENCE_ORDERS_PATH/not-a-uuid") {
                bearerAuth(TEST_SERVICE_TOKEN)
            }
            val missing = client.get(
                "$SALES_INTELLIGENCE_ORDERS_PATH/33333333-3333-4333-8333-333333333333"
            ) { bearerAuth(TEST_SERVICE_TOKEN) }
            projection.detail = {
                MarketplaceSalesIntelligenceProjectionReadResult.IntegrityFailure
            }
            val failure = client.get(
                "$SALES_INTELLIGENCE_ORDERS_PATH/33333333-3333-4333-8333-333333333333"
            ) { bearerAuth(TEST_SERVICE_TOKEN) }

            assertProblem(invalid.status, invalid.bodyAsText(), 400,
                "INVALID_SALES_INTELLIGENCE_ORDER_ID")
            assertProblem(missing.status, missing.bodyAsText(), 404,
                "SALES_INTELLIGENCE_NOT_FOUND")
            assertProblem(failure.status, failure.bodyAsText(), 503,
                "SALES_INTELLIGENCE_READ_FAILURE")
        }

    @Test
    fun `sales reads fail closed when an adapter returns another organization`() =
        testApplication {
            val leaked = unresolvedRecord().copy(
                organizationId = OrganizationId.parse(
                    "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
                )
            )
            val projection = FakeSalesProjection(
                listResult = MarketplaceSalesIntelligenceProjectionReadResult.Success(
                    MarketplaceSalesIntelligenceProjectionPage(listOf(leaked), null)
                ),
                detail = {
                    MarketplaceSalesIntelligenceProjectionReadResult.Success(leaked)
                }
            )
            application { configureForSales(projection) }

            val list = client.get(SALES_INTELLIGENCE_ORDERS_PATH) {
                bearerAuth(TEST_SERVICE_TOKEN)
            }
            val detail = client.get(
                "$SALES_INTELLIGENCE_ORDERS_PATH/${leaked.marketplaceOrderId.value}"
            ) { bearerAuth(TEST_SERVICE_TOKEN) }

            listOf(list, detail).forEach {
                assertProblem(
                    it.status,
                    it.bodyAsText(),
                    503,
                    "SALES_INTELLIGENCE_READ_FAILURE"
                )
                assertFalse(it.bodyAsText().contains(leaked.organizationId.toString()))
            }
        }

    @Test
    fun `refresh invokes exactly one bounded run with server owned scope and safe summary`() =
        testApplication {
            val observed = mutableListOf<Triple<OrganizationId, IntegrationConnectionId, Instant>>()
            val connectionId = IntegrationConnectionId(
                UUID.fromString("22222222-2222-4222-8222-222222222222")
            )
            application {
                configureForSales(
                    FakeSalesProjection(),
                    connectionId = connectionId,
                    refresh = { organizationId, configuredConnectionId, deadline ->
                        observed += Triple(organizationId, configuredConnectionId, deadline)
                        completedRefresh()
                    }
                )
            }

            val response = client.post(SALES_INTELLIGENCE_REFRESH_PATH) {
                bearerAuth(TEST_SERVICE_TOKEN)
                header("X-Organization-Id", "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
            }
            val body = response.bodyAsText()

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(1, observed.size)
            assertEquals(TEST_ORGANIZATION_ID, observed.single().first)
            assertEquals(connectionId, observed.single().second)
            assertEquals(Instant.parse("2026-09-07T12:02:00Z"), observed.single().third)
            assertTrue(body.contains("\"status\":\"COMPLETED\""))
            listOf("organization", "connection", "credential", "marketplaceOrderId",
                "amount").forEach { assertFalse(body.contains(it, true)) }
        }

    @Test
    fun `refresh rejects input and maps blocked and internal outcomes without disclosure`() =
        testApplication {
            var invocations = 0
            var mode = 0
            application {
                configureForSales(
                    FakeSalesProjection(),
                    refresh = { _, _, _ ->
                        invocations += 1
                        when (mode) {
                            0 -> MarketplaceLivePipelineResult.Blocked(
                                MarketplaceLivePipelineBlockDetail.ProjectionIntegrityFailure
                            )
                            1 -> throw LiveRefreshUnavailableException()
                            else -> error("credential at C:/private/path")
                        }
                    }
                )
            }

            val withBody = client.post(SALES_INTELLIGENCE_REFRESH_PATH) {
                bearerAuth(TEST_SERVICE_TOKEN)
                setBody("{}")
            }
            assertProblem(withBody.status, withBody.bodyAsText(), 400, "MALFORMED_REQUEST")
            assertEquals(0, invocations)

            val blocked = client.post(SALES_INTELLIGENCE_REFRESH_PATH) {
                bearerAuth(TEST_SERVICE_TOKEN)
            }
            assertProblem(blocked.status, blocked.bodyAsText(), 409, "LIVE_REFRESH_BLOCKED")
            mode = 1
            val unavailable = client.post(SALES_INTELLIGENCE_REFRESH_PATH) {
                bearerAuth(TEST_SERVICE_TOKEN)
            }
            assertProblem(
                unavailable.status,
                unavailable.bodyAsText(),
                503,
                "LIVE_REFRESH_UNAVAILABLE"
            )
            mode = 2
            val internal = client.post(SALES_INTELLIGENCE_REFRESH_PATH) {
                bearerAuth(TEST_SERVICE_TOKEN)
            }
            assertProblem(
                internal.status,
                internal.bodyAsText(),
                500,
                "LIVE_REFRESH_INTERNAL_FAILURE"
            )
            assertFalse(internal.bodyAsText().contains("credential"))
            assertFalse(internal.bodyAsText().contains("C:/private"))
            assertEquals(3, invocations)
        }

    @Test
    fun `sales endpoints authenticate before reads and refresh`() = testApplication {
        val projection = FakeSalesProjection()
        var refreshes = 0
        application {
            configureForSales(projection, refresh = { _, _, _ ->
                refreshes += 1
                completedRefresh()
            })
        }

        listOf(
            client.get(SALES_INTELLIGENCE_ORDERS_PATH),
            client.get("$SALES_INTELLIGENCE_ORDERS_PATH/not-a-uuid"),
            client.post(SALES_INTELLIGENCE_REFRESH_PATH)
        ).forEach {
            assertProblem(it.status, it.bodyAsText(), 401, "AUTHENTICATION_REQUIRED")
        }
        assertEquals(0, projection.listCalls)
        assertEquals(0, projection.detailCalls)
        assertEquals(0, refreshes)
    }

    @Test
    fun `Mercado Livre connection configuration is strict and non-disclosing`() {
        val id = "22222222-2222-4222-8222-222222222222"
        assertEquals(
            IntegrationConnectionId(UUID.fromString(id)),
            mercadoLivreConnectionFromEnvironment(
                mapOf("FLOOOW_MERCADO_LIVRE_CONNECTION_ID" to id)
            )
        )
        listOf(emptyMap(), mapOf("FLOOOW_MERCADO_LIVRE_CONNECTION_ID" to "NOT-$id"))
            .forEach { environment ->
                val error = kotlin.runCatching {
                    mercadoLivreConnectionFromEnvironment(environment)
                }.exceptionOrNull()
                assertNotNull(error)
                assertFalse(error.message.orEmpty().contains(id))
        }
        assertNull(
            mercadoLivreConnectionFromEnvironmentOrNull(
                mapOf("FLOOOW_MERCADO_LIVRE_CONNECTION_ID" to "   ")
            )
        )
        assertNull(
            omieConnectionFromEnvironmentOrNull(
                mapOf("FLOOOW_OMIE_CONNECTION_ID" to "")
            )
        )
        assertEquals(
            IntegrationConnectionId(UUID.fromString(id)),
            omieConnectionFromEnvironmentOrNull(
                mapOf("FLOOOW_OMIE_CONNECTION_ID" to id)
            )
        )
    }

    private fun Application.configureForSales(
        projection: MarketplaceSalesIntelligenceProjection,
        connectionId: IntegrationConnectionId = IntegrationConnectionId(
            UUID.fromString("22222222-2222-4222-8222-222222222222")
        ),
        refresh: (OrganizationId, IntegrationConnectionId, Instant) ->
            MarketplaceLivePipelineResult = { _, _, _ -> completedRefresh() }
    ) {
        configureApi(
            serviceToken = ServiceToken.test(TEST_SERVICE_TOKEN),
            serviceOrganizationId = TEST_ORGANIZATION_ID,
            record = { _, _ -> error("inventory assessment is outside this test") },
            salesIntelligenceApi = SalesIntelligenceApi(
                projection = projection,
                refresh = refresh,
                connectionId = connectionId,
                cursors = SalesIntelligenceCursorCodec("test-cursor-secret".toByteArray()),
                clock = Clock.fixed(
                    Instant.parse("2026-09-07T12:00:00Z"),
                    ZoneOffset.UTC
                )
            )
        )
    }

    private fun unresolvedRecord() = projectionRecord(
        "33333333-3333-4333-8333-333333333331",
        MarketplaceSalesIntelligenceState.Unresolved(
            MarketplaceEconomicTruthAssemblyPolicyVersion(
                "marketplace-economic-truth-assembly/1"
            ),
            setOf(MarketplaceEconomicTruthAssemblyNotReadyReason.ORDER_OCCURRED_AT_UNRESOLVED)
        )
    )

    private fun incompleteRecord(): MarketplaceSalesIntelligenceProjectionRecord {
        val orderId = MarketplaceOrderId(
            UUID.fromString("33333333-3333-4333-8333-333333333332")
        )
        val policy = EconomicCalculationPolicyVersion("marketplace-economic-truth/1")
        val calculation = MarketplaceSalesIntelligenceCalculationSnapshot.Incomplete(
            missingTypes = listOf(EconomicComponentType.PRODUCT_COST),
            partialTypes = listOf(EconomicComponentType.SHIPPING),
            suppliedComponents = emptyList()
        ).toCalculationResult(TEST_ORGANIZATION_ID, orderId, policy)
        return projectionRecord(
            orderId.value.toString(),
            MarketplaceSalesIntelligenceState.Calculated(
                MarketplaceEconomicTruthAssemblyPolicyVersion(
                    "marketplace-economic-truth-assembly/1"
                ),
                policy,
                calculation
            )
        )
    }

    private fun completeRecord(): MarketplaceSalesIntelligenceProjectionRecord {
        val orderId = MarketplaceOrderId(
            UUID.fromString("33333333-3333-4333-8333-333333333333")
        )
        val currency = MarketplaceCurrency("BRL")
        fun money(value: String) = MarketplaceMoney.parse(currency, value)
        val policy = EconomicCalculationPolicyVersion("marketplace-economic-truth/1")
        val calculation = MarketplaceSalesIntelligenceCalculationSnapshot.Complete(
            marketplace = MarketplaceKey("br.com.mercadolivre"),
            externalOrderId = MarketplaceExternalOrderId("external-order-secret"),
            orderOccurredAt = Instant.parse("2026-09-07T11:00:00Z"),
            currency = currency,
            grossRevenue = money("125.40"),
            totalMarketplaceFees = money("10.00"),
            totalShipping = money("5.00"),
            totalAdvertising = money("0"),
            totalTaxes = money("0"),
            totalProductCost = money("50.00"),
            totalFinancialCost = money("0"),
            totalOtherAdjustments = money("0"),
            contribution = money("60.40"),
            contributionMargin = MarketplaceSalesIntelligenceContributionMarginSnapshot.Defined(
                BigDecimal("0.481658")
            ),
            truthQuality = MarketplaceEconomicTruthQuality.CONFIRMED,
            components = emptyList()
        ).toCalculationResult(TEST_ORGANIZATION_ID, orderId, policy)
        return projectionRecord(
            orderId.value.toString(),
            MarketplaceSalesIntelligenceState.Calculated(
                MarketplaceEconomicTruthAssemblyPolicyVersion(
                    "marketplace-economic-truth-assembly/1"
                ),
                policy,
                calculation
            )
        )
    }

    private fun projectionRecord(
        orderId: String,
        state: MarketplaceSalesIntelligenceState
    ) = MarketplaceSalesIntelligenceProjectionRecord(
        organizationId = TEST_ORGANIZATION_ID,
        marketplaceOrderId = MarketplaceOrderId(UUID.fromString(orderId)),
        sourceEvidenceVersion = MarketplaceEconomicEvidenceVersion(3),
        state = state,
        lastAppliedChangeSequence = ChangeSequenceCheckpoint(7),
        projectedAt = Instant.parse("2026-09-07T12:00:00.123456Z")
    )

    private fun completedRefresh() = MarketplaceLivePipelineResult.Completed(
        source = MarketplaceLivePipelineSourceSummary(
            invocations = 1,
            committedPages = 1,
            alreadyCommittedPages = 0,
            records = 2,
            stop = MarketplaceLivePipelineSourceStop.PAGE_LIMIT
        ),
        occurrence = MarketplaceLivePipelinePromotionSummary(
            batches = 1,
            examined = 2,
            promoted = 1,
            duplicates = 1,
            identityConflicts = 0,
            evidenceConflicts = 0,
            drained = true
        ),
        revenue = MarketplaceLivePipelinePromotionSummary(
            batches = 1,
            examined = 1,
            promoted = 1,
            duplicates = 0,
            identityConflicts = 0,
            evidenceConflicts = 0,
            drained = true
        ),
        projection = MarketplaceLivePipelineProjectionSummary(
            batches = 1,
            processedChanges = 2,
            drained = true
        )
    )

    private fun assertProblem(
        status: HttpStatusCode,
        body: String,
        expectedStatus: Int,
        expectedCode: String
    ) {
        assertEquals(expectedStatus, status.value)
        val problem = Json.parseToJsonElement(body).jsonObject
        assertEquals(expectedStatus, problem.getValue("status").jsonPrimitive.content.toInt())
        assertEquals(expectedCode, problem.getValue("code").jsonPrimitive.content)
    }

    private fun resource(path: String): String = requireNotNull(
        ApplicationTest::class.java.getResource(path)
    ).readText()

    private companion object {
        const val assessmentPath =
            "/v1/marketplace-operations/inventory-risk-assessments"

        val redMotoRequest = """
            {
              "sku":"RED-MOTO-001",
              "periodEnd":"2026-08-31",
              "targetUnits":1000,
              "unitsSold":640,
              "availableUnits":90,
              "dailySalesVelocity":15,
              "observedOn":"2026-08-10",
              "expectedReplenishmentOn":"2026-08-20"
            }
        """.trimIndent()
    }
}

private class FakeSalesProjection(
    var listResult: MarketplaceSalesIntelligenceProjectionReadResult<
        MarketplaceSalesIntelligenceProjectionPage
    > = MarketplaceSalesIntelligenceProjectionReadResult.Success(
        MarketplaceSalesIntelligenceProjectionPage(emptyList(), null)
    ),
    var detail: (MarketplaceOrderId) -> MarketplaceSalesIntelligenceProjectionReadResult<
        MarketplaceSalesIntelligenceProjectionRecord?
    > = { MarketplaceSalesIntelligenceProjectionReadResult.Success(null) }
) : MarketplaceSalesIntelligenceProjection {
    var listCalls = 0
    var detailCalls = 0
    var lastOrganization: OrganizationId? = null
    var lastLimit: Int? = null
    var lastCursor: MarketplaceSalesIntelligenceProjectionCursor? = null

    override fun currentBySubject(
        organizationId: OrganizationId,
        marketplaceOrderId: MarketplaceOrderId
    ): MarketplaceSalesIntelligenceProjectionReadResult<
        MarketplaceSalesIntelligenceProjectionRecord?
    > = MarketplaceSalesIntelligenceProjectionReadResult.Success(null)

    override fun materializeIfNewer(
        record: MarketplaceSalesIntelligenceProjectionRecord
    ): MarketplaceSalesIntelligenceProjectionWriteResult =
        MarketplaceSalesIntelligenceProjectionWriteResult.IntegrityFailure

    override fun listByOrganization(
        organizationId: OrganizationId,
        cursor: MarketplaceSalesIntelligenceProjectionCursor?,
        limit: Int
    ): MarketplaceSalesIntelligenceProjectionReadResult<
        MarketplaceSalesIntelligenceProjectionPage
    > {
        listCalls += 1
        lastOrganization = organizationId
        lastCursor = cursor
        lastLimit = limit
        return listResult
    }

    override fun detailByOrganizationAndSubject(
        organizationId: OrganizationId,
        marketplaceOrderId: MarketplaceOrderId
    ): MarketplaceSalesIntelligenceProjectionReadResult<
        MarketplaceSalesIntelligenceProjectionRecord?
    > {
        detailCalls += 1
        lastOrganization = organizationId
        return detail(marketplaceOrderId)
    }
}

private fun testMercadoLivreCredentialRotationApi(): MercadoLivreCredentialRotationApi {
    val organizationId = TEST_ORGANIZATION_ID
    val connectionId = IntegrationConnectionId(
        UUID.fromString("33e7a252-292c-44ef-bab8-7a06b662c0fb")
    )
    val provider = io.flooow.integration.control.ProviderKey.of("br.com.mercadolivre")
    val context = io.flooow.integration.control.ActiveCredentialContext(
        provider,
        io.flooow.integration.control.CredentialKind.OAUTH2_AUTHORIZATION_CODE,
        1
    )

    val access =
        object : io.flooow.integration.credential.CredentialRotationCredentialAccess {
            override fun activeContext(
                organizationId: OrganizationId,
                connectionId: IntegrationConnectionId
            ) = context

            override fun <T> withActiveCredentialContext(
                organizationId: OrganizationId,
                connectionId: IntegrationConnectionId,
                operation: (
                    io.flooow.integration.control.ActiveCredentialContext,
                    ByteArray
                ) -> T
            ): T = operation(context, "synthetic".toByteArray())

            override fun rotate(
                organizationId: OrganizationId,
                connectionId: IntegrationConnectionId,
                expectedVersion: Int,
                replacementBytes: ByteArray
            ) = io.flooow.integration.control.CredentialRotationResult.ROTATED
        }

    val store =
        object : io.flooow.integration.credential.CredentialRotationExecutionStore {
            override fun claim(
                organizationId: OrganizationId,
                connectionId: IntegrationConnectionId,
                bindingVersion: Int,
                executionId: io.flooow.integration.credential.CredentialRotationExecutionId,
                claimedAt: Instant,
                leaseExpiresAt: Instant
            ) = io.flooow.integration.credential.CredentialRotationClaimResult(
                io.flooow.integration.credential.CredentialRotationClaimKind.ACQUIRED
            )

            override fun markRemoteStarted(
                organizationId: OrganizationId,
                connectionId: IntegrationConnectionId,
                bindingVersion: Int,
                executionId: io.flooow.integration.credential.CredentialRotationExecutionId,
                startedAt: Instant
            ) = io.flooow.integration.credential.CredentialRotationRemoteStartResult.STARTED

            override fun markRetryable(
                organizationId: OrganizationId,
                connectionId: IntegrationConnectionId,
                bindingVersion: Int,
                executionId: io.flooow.integration.credential.CredentialRotationExecutionId,
                retryNotBefore: Instant,
                updatedAt: Instant
            ) = true

            override fun markCompleted(
                organizationId: OrganizationId,
                connectionId: IntegrationConnectionId,
                bindingVersion: Int,
                executionId: io.flooow.integration.credential.CredentialRotationExecutionId,
                terminalAt: Instant
            ) = true

            override fun markInDoubt(
                organizationId: OrganizationId,
                connectionId: IntegrationConnectionId,
                bindingVersion: Int,
                executionId: io.flooow.integration.credential.CredentialRotationExecutionId,
                terminalAt: Instant
            ) = true
        }

    val rotator =
        object : io.flooow.integration.credential.CredentialRotator {
            override val descriptor =
                io.flooow.integration.credential.CredentialRotatorDescriptor(
                    provider,
                    io.flooow.integration.control.CredentialKind.OAUTH2_AUTHORIZATION_CODE
                )

            override fun assess(
                credentialBytes: ByteArray,
                now: Instant
            ) = io.flooow.integration.credential.CredentialRotationAssessment.USABLE

            override fun refresh(
                credentialBytes: ByteArray,
                context: io.flooow.integration.credential.CredentialRotationRemoteContext,
                cancellation: io.flooow.integration.credential.CredentialRotationCancellation
            ): io.flooow.integration.credential.CredentialRefreshResult =
                error("refresh must not execute")
        }

    return MercadoLivreCredentialRotationApi(
        io.flooow.integration.credential.CredentialRotationExecutor(
            access,
            store,
            listOf(rotator),
            Clock.fixed(
                Instant.parse("2026-09-10T19:00:00Z"),
                ZoneOffset.UTC
            )
        ),
        connectionId,
        Clock.fixed(
            Instant.parse("2026-09-10T19:00:00Z"),
            ZoneOffset.UTC
        )
    )
}
