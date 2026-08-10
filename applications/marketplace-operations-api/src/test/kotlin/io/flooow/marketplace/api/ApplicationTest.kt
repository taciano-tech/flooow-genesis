package io.flooow.marketplace.api

import io.flooow.marketplace.operations.inventory.InventoryRiskEvaluator
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ApplicationTest {

    @Test
    fun `red moto request returns exact committed contract`() = testApplication {
        application { module() }

        val response = client.post(assessmentPath) {
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
            contentType(ContentType.Application.Json)
            setBody(redMotoRequest)
        }
        val location = assertNotNull(created.headers["Location"])

        val retrieved = client.get(location)

        assertEquals(HttpStatusCode.OK, retrieved.status)
        assertEquals(created.bodyAsText(), retrieved.bodyAsText())
    }

    @Test
    fun `malformed and missing assessment identifiers use specific problems`() = testApplication {
        application { module() }

        val malformed = client.get("$assessmentPath/not-a-uuid")
        val missing = client.get(
            "$assessmentPath/99999999-9999-4999-8999-999999999999"
        )

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
            configureApi(record = { error("secret filesystem C:/internal/path") })
        }

        val response = client.post(assessmentPath) {
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
    fun `health endpoints are available without business evaluation`() = testApplication {
        application {
            configureApi(record = { error("business evaluation must not run") })
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

        val response = client.get("/openapi.json")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(resource("/openapi.json"), response.bodyAsText())
        assertTrue(response.bodyAsText().contains("\"openapi\": \"3.1.0\""))
    }

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
