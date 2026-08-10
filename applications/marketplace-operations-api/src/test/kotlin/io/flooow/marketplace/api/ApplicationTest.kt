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
import kotlin.test.assertTrue

class ApplicationTest {

    @Test
    fun `red moto request returns exact committed contract`() = testApplication {
        application { module() }

        val response = client.post(assessmentPath) {
            contentType(ContentType.Application.Json)
            setBody(redMotoRequest)
        }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        assertEquals(resource("/red-moto-success.json").trimEnd(), response.bodyAsText())
    }

    @Test
    fun `repeating the same request is byte equivalent`() = testApplication {
        application { module() }

        suspend fun execute() = client.post(assessmentPath) {
            contentType(ContentType.Application.Json)
            setBody(redMotoRequest)
        }.bodyAsText()

        assertEquals(execute(), execute())
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

        assertEquals(HttpStatusCode.OK, response.status)
        assertFalse(body.getValue("projection").jsonObject
            .getValue("shortageProjected").jsonPrimitive.boolean)
        assertEquals(
            "TAKE_NO_ACTION",
            body.getValue("recommendation").jsonObject.getValue("type").jsonPrimitive.content
        )
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
            configureApi { error("secret filesystem C:/internal/path") }
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
            configureApi { error("business evaluation must not run") }
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
