package io.flooow.marketplace.api

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.marketplace.operations.economics.ContributionMargin
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthCalculationResult
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjection
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionCursor
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionPage
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionReadResult
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionRecord
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceState
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionWriteResult
import io.flooow.marketplace.operations.live.MarketplaceLivePipelinePromotionSummary
import io.flooow.marketplace.operations.live.MarketplaceLivePipelineResult
import io.flooow.marketplace.operations.live.MarketplaceLivePipelineService
import io.flooow.marketplace.operations.live.MarketplaceLivePipelineSourceSummary
import io.flooow.marketplace.operations.live.MarketplaceLivePipelineSourceStop
import io.flooow.organization.OrganizationId
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.util.logging.Logger

internal const val SALES_INTELLIGENCE_REFRESH_PATH = "/v1/sales-intelligence/refresh"
internal const val SALES_INTELLIGENCE_REACQUISITION_PATH = "/v1/sales-intelligence/reacquire"
internal const val SALES_INTELLIGENCE_ORDERS_PATH = "/v1/sales-intelligence/orders"
private val REFRESH_DEADLINE: Duration = Duration.ofMinutes(2)

internal class SalesIntelligenceApi(
    private val projection: MarketplaceSalesIntelligenceProjection,
    private val refresh: (OrganizationId, IntegrationConnectionId, Instant) ->
        MarketplaceLivePipelineResult,
    private val connectionId: IntegrationConnectionId,
    private val cursors: SalesIntelligenceCursorCodec,
    private val clock: Clock = Clock.systemUTC(),
    private val reacquire: ((OrganizationId, IntegrationConnectionId, Instant) -> MarketplaceLivePipelineResult)? = null
) {
    constructor(
        projection: MarketplaceSalesIntelligenceProjection,
        pipeline: MarketplaceLivePipelineService,
        connectionId: IntegrationConnectionId,
        cursors: SalesIntelligenceCursorCodec,
        clock: Clock = Clock.systemUTC()
    ) : this(
        projection,
        { organizationId, configuredConnectionId, deadline ->
            pipeline.run(organizationId, configuredConnectionId, deadline)
        },
        connectionId,
        cursors,
        clock
    )

    fun list(
        organizationId: OrganizationId,
        rawCursor: String?,
        rawLimit: String?
    ): JsonObject {
        val limit = parseLimit(rawLimit)
        val cursor = rawCursor?.let {
            cursors.decode(it, organizationId) ?: throw InvalidSalesIntelligenceCursorException()
        }
        return when (val result = projection.listByOrganization(organizationId, cursor, limit)) {
            is MarketplaceSalesIntelligenceProjectionReadResult.Success -> {
                val page = result.value
                val nextCursor = page.nextCursor
                if (page.records.size > limit ||
                    page.records.any { it.organizationId != organizationId } ||
                    (nextCursor != null &&
                        (page.records.isEmpty() ||
                            nextCursor.projectedAt != page.records.last().projectedAt ||
                            nextCursor.marketplaceOrderId !=
                            page.records.last().marketplaceOrderId))
                ) {
                    throw SalesIntelligenceReadFailureException()
                }
                pageJson(organizationId, page)
            }
            MarketplaceSalesIntelligenceProjectionReadResult.IntegrityFailure ->
                throw SalesIntelligenceReadFailureException()
        }
    }

    fun detail(organizationId: OrganizationId, rawOrderId: String): JsonObject {
        val orderId = try {
            val parsed = UUID.fromString(rawOrderId)
            if (parsed.toString() != rawOrderId) throw IllegalArgumentException()
            io.flooow.marketplace.operations.economics.MarketplaceOrderId(parsed)
        } catch (_: IllegalArgumentException) {
            throw InvalidSalesIntelligenceOrderIdException()
        }
        return when (
            val result = projection.detailByOrganizationAndSubject(organizationId, orderId)
        ) {
            is MarketplaceSalesIntelligenceProjectionReadResult.Success -> {
                val record = result.value ?: throw SalesIntelligenceNotFoundException()
                if (record.organizationId != organizationId ||
                    record.marketplaceOrderId != orderId
                ) {
                    throw SalesIntelligenceReadFailureException()
                }
                recordJson(record)
            }
            MarketplaceSalesIntelligenceProjectionReadResult.IntegrityFailure ->
                throw SalesIntelligenceReadFailureException()
        }
    }

    fun refresh(organizationId: OrganizationId): JsonObject {
        val now = clock.instant()
        val result = try {
            refresh(organizationId, connectionId, now.plus(REFRESH_DEADLINE))
        } catch (_: LiveRefreshUnavailableException) {
            throw LiveRefreshUnavailableException()
        } catch (_: Exception) {
            throw LiveRefreshInternalFailureException()
        }
        return when (result) {
            is MarketplaceLivePipelineResult.Completed -> {
                result.source.failureKind?.let { SOURCE_FAILURE_LOGGER.warning("marketplace_source_failure category=${it.safeFailureCategory()} retryable=${result.source.stop == MarketplaceLivePipelineSourceStop.RETRYABLE_FAILURE}") }
                refreshJson(result)
            }
            is MarketplaceLivePipelineResult.Blocked -> throw LiveRefreshBlockedException()
        }
    }

    fun reacquire(organizationId: OrganizationId): JsonObject {
        val run = reacquire ?: throw LiveRefreshUnavailableException()
        val result = try {
            run(organizationId, connectionId, clock.instant().plus(REFRESH_DEADLINE))
        } catch (_: Exception) {
            throw LiveRefreshInternalFailureException()
        }
        return when (result) {
            is MarketplaceLivePipelineResult.Completed -> {
                result.source.failureKind?.let { SOURCE_FAILURE_LOGGER.warning("marketplace_reacquisition_failure category=${it.safeFailureCategory()} retryable=${result.source.stop == MarketplaceLivePipelineSourceStop.RETRYABLE_FAILURE}") }
                refreshJson(result)
            }
            is MarketplaceLivePipelineResult.Blocked -> throw LiveRefreshBlockedException()
        }
    }

    private fun pageJson(
        organizationId: OrganizationId,
        page: MarketplaceSalesIntelligenceProjectionPage
    ) = buildJsonObject {
        put("orders", buildJsonArray { page.records.forEach { add(recordJson(it)) } })
        page.nextCursor?.let { put("nextCursor", cursors.encode(it, organizationId)) }
    }

    private fun parseLimit(raw: String?): Int {
        if (raw == null) return 50
        val parsed = raw.toIntOrNull() ?: throw InvalidSalesIntelligenceCursorException()
        return try {
            MarketplaceSalesIntelligenceProjection.requireValidPageSize(parsed)
        } catch (_: IllegalArgumentException) {
            throw InvalidSalesIntelligenceCursorException()
        }
    }
}

private val SOURCE_FAILURE_LOGGER = Logger.getLogger("flooow.marketplace.source")

internal class SalesIntelligenceCursorCodec(secret: ByteArray) {
    private val key = hmac(secret, "flooow.sales-intelligence.cursor.v1".toByteArray())

    fun encode(
        cursor: MarketplaceSalesIntelligenceProjectionCursor,
        organizationId: OrganizationId
    ): String {
        val payload = ByteBuffer.allocate(44)
            .putLong(cursor.projectedAt.epochSecond)
            .putInt(cursor.projectedAt.nano)
            .putLong(cursor.marketplaceOrderId.value.mostSignificantBits)
            .putLong(cursor.marketplaceOrderId.value.leastSignificantBits)
            .putLong(organizationId.value.mostSignificantBits)
            .putLong(organizationId.value.leastSignificantBits)
            .array()
        val encodedPayload = BASE64.encodeToString(payload)
        val tag = hmac(key, ("v1." + encodedPayload).toByteArray(StandardCharsets.US_ASCII))
        return "v1.$encodedPayload.${BASE64.encodeToString(tag)}"
    }

    fun decode(
        encoded: String,
        organizationId: OrganizationId
    ): MarketplaceSalesIntelligenceProjectionCursor? = try {
        val parts = encoded.split('.')
        if (parts.size != 3 || parts[0] != "v1") return null
        val payload = BASE64_DECODER.decode(parts[1])
        val actualTag = BASE64_DECODER.decode(parts[2])
        if (BASE64.encodeToString(payload) != parts[1] ||
            BASE64.encodeToString(actualTag) != parts[2]
        ) return null
        val expectedTag = hmac(
            key,
            ("v1." + parts[1]).toByteArray(StandardCharsets.US_ASCII)
        )
        if (payload.size != 44 || !MessageDigest.isEqual(expectedTag, actualTag)) return null
        val values = ByteBuffer.wrap(payload)
        val projectedAt = Instant.ofEpochSecond(values.long, values.int.toLong())
        val orderId = UUID(values.long, values.long)
        val cursorOrganization = UUID(values.long, values.long)
        if (cursorOrganization != organizationId.value) return null
        MarketplaceSalesIntelligenceProjectionCursor(
            projectedAt,
            io.flooow.marketplace.operations.economics.MarketplaceOrderId(orderId)
        )
    } catch (_: Exception) {
        null
    }

    companion object {
        private val BASE64 = Base64.getUrlEncoder().withoutPadding()
        private val BASE64_DECODER = Base64.getUrlDecoder()
    }
}

private fun hmac(key: ByteArray, value: ByteArray): ByteArray =
    Mac.getInstance("HmacSHA256").run {
        init(SecretKeySpec(key, "HmacSHA256"))
        doFinal(value)
    }

private fun recordJson(record: MarketplaceSalesIntelligenceProjectionRecord): JsonObject =
    buildJsonObject {
        put("marketplaceOrderId", record.marketplaceOrderId.value.toString())
        put("sourceEvidenceVersion", record.sourceEvidenceVersion.valueForPersistence())
        put("projectedAt", record.projectedAt.toString())
        put("assemblyPolicyVersion", record.state.assemblyPolicyVersion.value)
        when (val state = record.state) {
            is MarketplaceSalesIntelligenceState.Unresolved -> {
                put("state", "UNRESOLVED")
                put("unresolvedReasons", buildJsonArray {
                    state.reasons.sortedBy { it.name }.forEach { add(JsonPrimitive(it.name)) }
                })
            }
            is MarketplaceSalesIntelligenceState.Calculated -> {
                put("calculationPolicyVersion", state.calculationPolicyVersion.value)
                when (val calculation = state.calculationResult) {
                    is MarketplaceEconomicTruthCalculationResult.Complete -> {
                        val value = calculation.result
                        if (value.organizationId != record.organizationId ||
                            value.orderId != record.marketplaceOrderId
                        ) {
                            throw SalesIntelligenceReadFailureException()
                        }
                        put("state", "CALCULATED_COMPLETE")
                        put("economicResult", buildJsonObject {
                            put("orderOccurredAt", value.orderOccurredAt.toString())
                            put("currency", value.currency.code)
                            put("grossRevenue", moneyAmount(value.grossRevenue))
                            put("totalMarketplaceFees", moneyAmount(value.totalMarketplaceFees))
                            put("totalShipping", moneyAmount(value.totalShipping))
                            put("totalAdvertising", moneyAmount(value.totalAdvertising))
                            put("totalTaxes", moneyAmount(value.totalTaxes))
                            put("totalProductCost", moneyAmount(value.totalProductCost))
                            put("totalFinancialCost", moneyAmount(value.totalFinancialCost))
                            put("totalOtherAdjustments", moneyAmount(value.totalOtherAdjustments))
                            put("contribution", moneyAmount(value.contribution))
                            when (val margin = value.contributionMargin) {
                                is ContributionMargin.Defined ->
                                    put("contributionMargin", margin.decimalValue.toPlainString())
                                is ContributionMargin.Undefined ->
                                    put("contributionMarginUndefinedReason", margin.reason.name)
                            }
                            put("truthQuality", value.truthQuality.name)
                        })
                    }
                    is MarketplaceEconomicTruthCalculationResult.Incomplete -> {
                        if (calculation.organizationId != record.organizationId ||
                            calculation.orderId != record.marketplaceOrderId
                        ) {
                            throw SalesIntelligenceReadFailureException()
                        }
                        put("state", "CALCULATED_INCOMPLETE")
                        put("missingComponentTypes", buildJsonArray {
                            calculation.missingTypes.sortedBy { it.name }
                                .forEach { add(JsonPrimitive(it.name)) }
                        })
                        put("partialComponentTypes", buildJsonArray {
                            calculation.partialTypes.sortedBy { it.name }
                                .forEach { add(JsonPrimitive(it.name)) }
                        })
                    }
                }
            }
        }
    }

private fun moneyAmount(money: MarketplaceMoney): String = money.amount.toPlainString()

private fun refreshJson(result: MarketplaceLivePipelineResult.Completed) = buildJsonObject {
    put("status", "COMPLETED")
    put("source", sourceJson(result.source))
    put("occurrence", promotionJson(result.occurrence))
    put("revenue", promotionJson(result.revenue))
    put("projection", buildJsonObject {
        put("batches", result.projection.batches)
        put("processedChanges", result.projection.processedChanges)
        put("drained", result.projection.drained)
    })
}

private fun sourceJson(summary: MarketplaceLivePipelineSourceSummary) = buildJsonObject {
    put("status", when (summary.stop) {
        MarketplaceLivePipelineSourceStop.EXHAUSTED -> "EXHAUSTED"
        MarketplaceLivePipelineSourceStop.PAGE_LIMIT -> "BOUNDED"
        MarketplaceLivePipelineSourceStop.RETRYABLE_FAILURE -> "RETRYABLE_FAILURE"
        MarketplaceLivePipelineSourceStop.NON_RETRYABLE_FAILURE -> "FAILED"
    })
    put("invocations", summary.invocations)
    put("committedPages", summary.committedPages)
    put("alreadyCommittedPages", summary.alreadyCommittedPages)
    put("records", summary.records)
    summary.failureKind?.let {
        put("failureCategory", it.safeFailureCategory())
        put("retryable", summary.stop == MarketplaceLivePipelineSourceStop.RETRYABLE_FAILURE)
    }
}

private fun io.flooow.integration.connector.ConnectorExecutionFailureKind.safeFailureCategory(): String = when (this) {
    io.flooow.integration.connector.ConnectorExecutionFailureKind.AUTHENTICATION_REQUIRED -> "AUTHENTICATION"
    io.flooow.integration.connector.ConnectorExecutionFailureKind.AUTHORIZATION_DENIED -> "AUTHORIZATION"
    io.flooow.integration.connector.ConnectorExecutionFailureKind.RATE_LIMITED -> "RATE_LIMIT"
    io.flooow.integration.connector.ConnectorExecutionFailureKind.REMOTE_TEMPORARY -> "PROVIDER_TEMPORARY"
    io.flooow.integration.connector.ConnectorExecutionFailureKind.REMOTE_PERMANENT -> "PROVIDER_PERMANENT"
    io.flooow.integration.connector.ConnectorExecutionFailureKind.REMOTE_DATA_INVALID -> "RESPONSE_SCHEMA"
    io.flooow.integration.connector.ConnectorExecutionFailureKind.BUDGET_EXCEEDED -> "BUDGET_EXHAUSTED"
    io.flooow.integration.connector.ConnectorExecutionFailureKind.CANCELLED -> "CANCELLED"
    io.flooow.integration.connector.ConnectorExecutionFailureKind.CONNECTION_UNAVAILABLE -> "CONNECTION"
    io.flooow.integration.connector.ConnectorExecutionFailureKind.CONNECTOR_UNAVAILABLE -> "CONNECTOR"
    io.flooow.integration.connector.ConnectorExecutionFailureKind.PROGRESS_CONFLICT -> "PERSISTENCE"
    io.flooow.integration.connector.ConnectorExecutionFailureKind.INTERNAL -> "UNKNOWN"
}

private fun promotionJson(summary: MarketplaceLivePipelinePromotionSummary) = buildJsonObject {
    put("batches", summary.batches)
    put("examined", summary.examined)
    put("promoted", summary.promoted)
    put("duplicates", summary.duplicates)
    put("identityConflicts", summary.identityConflicts)
    put("evidenceConflicts", summary.evidenceConflicts)
    put("drained", summary.drained)
}

internal class InvalidSalesIntelligenceCursorException : RuntimeException()
internal class InvalidSalesIntelligenceOrderIdException : RuntimeException()
internal class SalesIntelligenceNotFoundException : RuntimeException()
internal class SalesIntelligenceReadFailureException : RuntimeException()
internal class LiveRefreshBlockedException : RuntimeException()
internal class LiveRefreshUnavailableException : RuntimeException()
internal class LiveRefreshInternalFailureException : RuntimeException()

internal fun testSalesIntelligenceApi(): SalesIntelligenceApi {
    val projection = object : MarketplaceSalesIntelligenceProjection {
        override fun currentBySubject(
            organizationId: OrganizationId,
            marketplaceOrderId: io.flooow.marketplace.operations.economics.MarketplaceOrderId
        ) = MarketplaceSalesIntelligenceProjectionReadResult.Success(null)

        override fun materializeIfNewer(record: MarketplaceSalesIntelligenceProjectionRecord) =
            MarketplaceSalesIntelligenceProjectionWriteResult.IntegrityFailure

        override fun listByOrganization(
            organizationId: OrganizationId,
            cursor: MarketplaceSalesIntelligenceProjectionCursor?,
            limit: Int
        ) = MarketplaceSalesIntelligenceProjectionReadResult.Success(
            MarketplaceSalesIntelligenceProjectionPage(emptyList(), null)
        )

        override fun detailByOrganizationAndSubject(
            organizationId: OrganizationId,
            marketplaceOrderId: io.flooow.marketplace.operations.economics.MarketplaceOrderId
        ) = MarketplaceSalesIntelligenceProjectionReadResult.Success(null)
    }
    return SalesIntelligenceApi(
        projection = projection,
        refresh = { _, _, _ ->
            MarketplaceLivePipelineResult.Completed(
                source = MarketplaceLivePipelineSourceSummary(
                    invocations = 0,
                    committedPages = 0,
                    alreadyCommittedPages = 0,
                    records = 0,
                    stop = MarketplaceLivePipelineSourceStop.EXHAUSTED
                ),
                occurrence = MarketplaceLivePipelinePromotionSummary.EMPTY,
                revenue = MarketplaceLivePipelinePromotionSummary.EMPTY,
                projection = io.flooow.marketplace.operations.live
                    .MarketplaceLivePipelineProjectionSummary.EMPTY
            )
        },
        connectionId = IntegrationConnectionId(
            UUID.fromString("22222222-2222-4222-8222-222222222222")
        ),
        cursors = SalesIntelligenceCursorCodec("test-cursor-key".toByteArray()),
        clock = Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), java.time.ZoneOffset.UTC)
    )
}
