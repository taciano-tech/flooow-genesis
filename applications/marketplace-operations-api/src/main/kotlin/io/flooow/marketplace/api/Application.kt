package io.flooow.marketplace.api

import io.flooow.marketplace.operations.inventory.AssessmentIdentifierFactory
import io.flooow.marketplace.operations.inventory.InventoryRiskAssessmentJournal
import io.flooow.marketplace.operations.inventory.InventoryRiskAssessmentRecorder
import io.flooow.marketplace.operations.inventory.InventoryRiskInput
import io.flooow.marketplace.operations.inventory.PersistenceIntegrityException
import io.flooow.marketplace.operations.inventory.PersistenceUnavailableException
import io.flooow.marketplace.operations.inventory.RecordedInventoryRiskAssessment
import io.flooow.marketplace.persistence.postgres.PostgresConfiguration
import io.flooow.marketplace.persistence.postgres.PostgresInventoryRiskAssessmentJournal
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.request.contentType
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.response.header
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeParseException
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

private const val ASSESSMENT_PATH =
    "/v1/marketplace-operations/inventory-risk-assessments"
private val problemContentType = ContentType.parse("application/problem+json")
private val jsonContentType = ContentType.Application.Json.withCharset(Charsets.UTF_8)

private val json = Json {
    explicitNulls = false
    ignoreUnknownKeys = false
}

fun main() {
    val host = System.getenv("HOST") ?: "0.0.0.0"
    val port = System.getenv("PORT")?.toIntOrNull() ?: 8080
    val journal = PostgresInventoryRiskAssessmentJournal.connect(
        PostgresConfiguration.fromEnvironment()
    )
    val recorder = InventoryRiskAssessmentRecorder(journal)
    embeddedServer(Netty, host = host, port = port) {
        configureApi(recorder::record, recorder::findById)
    }.start(wait = true)
}

fun Application.module() {
    val journal = InMemoryAssessmentJournal()
    val sequence = AtomicLong()
    val recorder = InventoryRiskAssessmentRecorder(
        journal = journal,
        identifierFactory = AssessmentIdentifierFactory {
            val suffix = sequence.incrementAndGet().toString().padStart(12, '1')
            "11111111-1111-4111-8111-$suffix"
        },
        clock = Clock.fixed(Instant.parse("2026-08-10T13:00:00Z"), ZoneOffset.UTC)
    )
    configureApi(recorder::record, recorder::findById)
}

internal fun Application.configureApi(
    record: (InventoryRiskInput) -> RecordedInventoryRiskAssessment,
    findById: (String) -> RecordedInventoryRiskAssessment? = { null }
) {
    install(StatusPages) {
        exception<MalformedRequestException> { call, cause ->
            call.respondProblem(
                status = HttpStatusCode.BadRequest,
                type = "https://flooow.io/problems/malformed-request",
                title = "Malformed request",
                detail = cause.message ?: "Request body is invalid",
                code = "MALFORMED_REQUEST"
            )
        }
        exception<DomainValidationException> { call, cause ->
            call.respondProblem(
                status = HttpStatusCode.UnprocessableEntity,
                type = "https://flooow.io/problems/invalid-inventory-risk-request",
                title = "Invalid inventory risk request",
                detail = cause.message ?: "Inventory risk request is invalid",
                code = "INVALID_INVENTORY_RISK_REQUEST"
            )
        }
        exception<UnsupportedMediaTypeException> { call, _ ->
            call.respondProblem(
                status = HttpStatusCode.UnsupportedMediaType,
                type = "https://flooow.io/problems/unsupported-media-type",
                title = "Unsupported media type",
                detail = "Content-Type must be application/json",
                code = "UNSUPPORTED_MEDIA_TYPE"
            )
        }
        exception<MalformedAssessmentIdException> { call, cause ->
            call.respondProblem(
                status = HttpStatusCode.BadRequest,
                type = "https://flooow.io/problems/malformed-assessment-id",
                title = "Malformed assessment identifier",
                detail = cause.message ?: "Assessment identifier is invalid",
                code = "MALFORMED_ASSESSMENT_ID"
            )
        }
        exception<AssessmentNotFoundException> { call, _ ->
            call.respondProblem(
                status = HttpStatusCode.NotFound,
                type = "https://flooow.io/problems/assessment-not-found",
                title = "Assessment not found",
                detail = "The requested assessment was not found",
                code = "ASSESSMENT_NOT_FOUND"
            )
        }
        exception<PersistenceUnavailableException> { call, _ ->
            call.respondProblem(
                status = HttpStatusCode.ServiceUnavailable,
                type = "https://flooow.io/problems/persistence-unavailable",
                title = "Persistence unavailable",
                detail = "Assessment persistence is temporarily unavailable",
                code = "PERSISTENCE_UNAVAILABLE"
            )
        }
        exception<PersistenceIntegrityException> { call, _ ->
            call.respondProblem(
                status = HttpStatusCode.InternalServerError,
                type = "https://flooow.io/problems/persistence-integrity-failure",
                title = "Persistence integrity failure",
                detail = "The persisted assessment could not be verified",
                code = "PERSISTENCE_INTEGRITY_FAILURE"
            )
        }
        exception<Throwable> { call, _ ->
            call.respondProblem(
                status = HttpStatusCode.InternalServerError,
                type = "https://flooow.io/problems/internal-error",
                title = "Internal server error",
                detail = "The server could not complete the request",
                code = "INTERNAL_ERROR"
            )
        }
        status(HttpStatusCode.NotFound) { call, _ ->
            call.respondProblem(
                status = HttpStatusCode.NotFound,
                type = "https://flooow.io/problems/resource-not-found",
                title = "Resource not found",
                detail = "The requested resource was not found",
                code = "RESOURCE_NOT_FOUND"
            )
        }
    }

    routing {
        get("/health/live") {
            call.respondJson(buildJsonObject { put("status", "UP") })
        }
        get("/health/ready") {
            call.respondJson(buildJsonObject { put("status", "UP") })
        }
        get("/openapi.json") {
            val openApi = requireNotNull(
                Application::class.java.getResource("/openapi.json")
            ) { "Committed OpenAPI resource is missing" }.readText()
            call.respondText(openApi, jsonContentType, HttpStatusCode.OK)
        }
        post(ASSESSMENT_PATH) {
            if (!call.request.contentType().withoutParameters()
                    .match(ContentType.Application.Json)
            ) {
                throw UnsupportedMediaTypeException()
            }

            val request = decodeRequest(call.receiveText())
            val input = try {
                request.toDomain()
            } catch (error: IllegalArgumentException) {
                throw DomainValidationException(
                    error.message ?: "Inventory risk request is invalid"
                )
            }
            val recorded = record(input)
            call.response.header(
                "Location",
                "$ASSESSMENT_PATH/${recorded.assessmentId}"
            )
            call.respondJson(recordedAssessmentJson(recorded), HttpStatusCode.Created)
        }
        get("$ASSESSMENT_PATH/{assessmentId}") {
            val assessmentId = canonicalAssessmentId(
                call.parameters["assessmentId"].orEmpty()
            )
            val recorded = findById(assessmentId) ?: throw AssessmentNotFoundException()
            call.respondJson(recordedAssessmentJson(recorded))
        }
    }
}

private suspend fun io.ktor.server.application.ApplicationCall.respondJson(
    body: JsonElement,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    respondText(json.encodeToString(JsonElement.serializer(), body), jsonContentType, status)
}

private suspend fun io.ktor.server.application.ApplicationCall.respondProblem(
    status: HttpStatusCode,
    type: String,
    title: String,
    detail: String,
    code: String
) {
    val body = buildJsonObject {
        put("type", type)
        put("title", title)
        put("status", status.value)
        put("detail", detail)
        put("instance", request.path())
        put("code", code)
    }
    respondText(
        json.encodeToString(JsonElement.serializer(), body),
        problemContentType.withCharset(Charsets.UTF_8),
        status
    )
}

private data class InventoryRiskRequest(
    val sku: String,
    val periodEnd: LocalDate,
    val targetUnits: Int,
    val unitsSold: Int,
    val availableUnits: Int,
    val dailySalesVelocity: Int,
    val observedOn: LocalDate,
    val expectedReplenishmentOn: LocalDate
) {
    fun toDomain() = InventoryRiskInput(
        sku = sku,
        periodEnd = periodEnd,
        targetUnits = targetUnits,
        unitsSold = unitsSold,
        availableUnits = availableUnits,
        dailySalesVelocity = dailySalesVelocity,
        observedOn = observedOn,
        expectedReplenishmentOn = expectedReplenishmentOn
    )
}

private fun decodeRequest(body: String): InventoryRiskRequest {
    val objectNode = try {
        json.parseToJsonElement(body).jsonObject
    } catch (_: Exception) {
        throw MalformedRequestException("Request body must be a valid JSON object")
    }

    val required = setOf(
        "sku",
        "periodEnd",
        "targetUnits",
        "unitsSold",
        "availableUnits",
        "dailySalesVelocity",
        "observedOn",
        "expectedReplenishmentOn"
    )
    val unknown = objectNode.keys - required
    if (unknown.isNotEmpty()) {
        throw MalformedRequestException("Unknown request property: ${unknown.sorted().first()}")
    }
    val missing = required - objectNode.keys
    if (missing.isNotEmpty()) {
        throw MalformedRequestException("Missing required property: ${missing.sorted().first()}")
    }

    return InventoryRiskRequest(
        sku = objectNode.requiredString("sku"),
        periodEnd = objectNode.requiredDate("periodEnd"),
        targetUnits = objectNode.requiredInt("targetUnits"),
        unitsSold = objectNode.requiredInt("unitsSold"),
        availableUnits = objectNode.requiredInt("availableUnits"),
        dailySalesVelocity = objectNode.requiredInt("dailySalesVelocity"),
        observedOn = objectNode.requiredDate("observedOn"),
        expectedReplenishmentOn = objectNode.requiredDate("expectedReplenishmentOn")
    )
}

private fun JsonObject.requiredString(name: String): String = try {
    getValue(name).jsonPrimitive.content.also {
        if (!getValue(name).jsonPrimitive.isString) {
            throw IllegalArgumentException()
        }
    }
} catch (_: Exception) {
    throw MalformedRequestException("Property '$name' must be a string")
}

private fun JsonObject.requiredInt(name: String): Int = try {
    getValue(name).jsonPrimitive.int
} catch (_: Exception) {
    throw MalformedRequestException("Property '$name' must be an integer")
}

private fun JsonObject.requiredDate(name: String): LocalDate {
    val value = requiredString(name)
    return try {
        LocalDate.parse(value)
    } catch (_: DateTimeParseException) {
        throw MalformedRequestException("Property '$name' must be an ISO-8601 date")
    }
}

private fun recordedAssessmentJson(assessment: RecordedInventoryRiskAssessment): JsonObject {
    val selected = assessment.recommendation
    return buildJsonObject {
        put("assessmentId", assessment.assessmentId)
        put("recordedAt", assessment.recordedAt.toString())
        put("sku", assessment.input.sku)
        put("observedOn", assessment.input.observedOn.toString())
        put("projection", buildJsonObject {
            put("stockCoverageDays", assessment.projection.stockCoverageDays)
            put("projectedStockoutOn", assessment.projection.projectedStockoutOn.toString())
            put("expectedReplenishmentOn", assessment.projection.expectedReplenishmentOn.toString())
            put("projectedStockoutDays", assessment.projection.projectedStockoutDays)
            put("unitsPotentiallyUnavailable", assessment.projection.unitsPotentiallyUnavailable)
            put("unitsRemainingToGoal", assessment.projection.unitsRemainingToGoal)
            put("unitsAtRiskAgainstGoal", assessment.projection.unitsAtRiskAgainstGoal)
            put("shortageProjected", assessment.projection.shortageProjected)
        })
        put("recommendation", buildJsonObject {
            put("type", selected.type.name)
            put("explanation", selected.explanation)
            put("expectedUnitsPreserved", selected.expectedUnitsPreserved)
        })
        put("expectedImpact", assessment.expectedImpact)
        put("trace", buildJsonArray {
            assessment.trace.forEach { add(JsonPrimitive(it)) }
        })
    }
}

private fun canonicalAssessmentId(value: String): String {
    val parsed = try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        throw MalformedAssessmentIdException("Assessment identifier must be a canonical UUID")
    }
    if (parsed.toString() != value) {
        throw MalformedAssessmentIdException("Assessment identifier must be a canonical UUID")
    }
    return value
}

private class InMemoryAssessmentJournal : InventoryRiskAssessmentJournal {
    private val records = linkedMapOf<String, RecordedInventoryRiskAssessment>()

    override fun append(record: RecordedInventoryRiskAssessment) {
        check(records.putIfAbsent(record.assessmentId, record) == null)
    }

    override fun findById(assessmentId: String): RecordedInventoryRiskAssessment? =
        records[assessmentId]
}

private class MalformedRequestException(message: String) : RuntimeException(message)
private class DomainValidationException(message: String) : RuntimeException(message)
private class UnsupportedMediaTypeException : RuntimeException()
private class MalformedAssessmentIdException(message: String) : RuntimeException(message)
private class AssessmentNotFoundException : RuntimeException()
