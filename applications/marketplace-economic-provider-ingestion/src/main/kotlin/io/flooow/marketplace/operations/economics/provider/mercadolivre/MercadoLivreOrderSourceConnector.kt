package io.flooow.marketplace.operations.economics.provider.mercadolivre

import io.flooow.integration.connector.ConnectorAdapterFailure
import io.flooow.integration.connector.ConnectorAdapterFailureKind
import io.flooow.integration.connector.ConnectorBudget
import io.flooow.integration.connector.ConnectorCancellation
import io.flooow.integration.connector.ConnectorCapability
import io.flooow.integration.connector.ConnectorDescriptor
import io.flooow.integration.connector.ConnectorPage
import io.flooow.integration.connector.ConnectorProgress
import io.flooow.integration.connector.ConnectorReadResult
import io.flooow.integration.connector.ConnectorRecordDefinition
import io.flooow.integration.connector.PullConnector
import io.flooow.integration.control.ProviderKey
import io.flooow.integration.provider.mercadolivre.MercadoLivreOAuthCredentialEnvelopeCodec
import io.flooow.marketplace.operations.economics.provider.MarketplaceEconomicOrderSourceCapability
import io.flooow.marketplace.operations.economics.provider.MercadoLivreItemReference
import io.flooow.marketplace.operations.economics.provider.MercadoLivreOrderItemSourceObservation
import io.flooow.marketplace.operations.economics.provider.MercadoLivreOrderReference
import io.flooow.marketplace.operations.economics.provider.MercadoLivreOrderSourceRecord
import io.flooow.marketplace.operations.economics.provider.MercadoLivrePackReference
import io.flooow.marketplace.operations.economics.provider.MercadoLivrePaymentReference
import io.flooow.marketplace.operations.economics.provider.MercadoLivrePaymentSourceObservation
import io.flooow.marketplace.operations.economics.provider.MercadoLivreProviderStatus
import io.flooow.marketplace.operations.economics.provider.MercadoLivreShippingReference
import io.flooow.marketplace.operations.economics.provider.MercadoLivreSellerSkuReference
import io.flooow.marketplace.operations.economics.provider.MercadoLivreSourceCurrency
import io.flooow.marketplace.operations.economics.provider.MercadoLivreVariationReference
import io.flooow.marketplace.operations.economics.provider.ProviderSourceDecimal
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class MercadoLivreOrderHttpResponse(
    val statusCode: Int,
    val body: ByteArray,
    val retryAfter: String? = null,
    val contentMissing: String? = null
) {
    override fun toString(): String =
        "MercadoLivreOrderHttpResponse(statusCode=$statusCode, body=[REDACTED])"
}

fun interface MercadoLivreOrderHttpTransport {
    fun get(
        endpoint: URI,
        accessToken: String,
        timeout: Duration,
        maxResponseBytes: Long
    ): MercadoLivreOrderHttpResponse
}

class MercadoLivreOrderSourceConnector(
    private val endpoint: URI = DEFAULT_ENDPOINT,
    private val clock: Clock = Clock.systemUTC(),
    private val transport: MercadoLivreOrderHttpTransport = JdkMercadoLivreOrderHttpTransport()
) : PullConnector {
    override val descriptor = ConnectorDescriptor(
        ProviderKey.of("br.com.mercadolivre"),
        listOf(
            ConnectorRecordDefinition(
                MarketplaceEconomicOrderSourceCapability.KEY,
                MercadoLivreOrderSourceRecord::class
            ),
            ConnectorRecordDefinition(
                MarketplaceEconomicOrderSourceCapability.REACQUISITION_KEY,
                MercadoLivreOrderSourceRecord::class
            )
        )
    )

    init {
        require(endpoint.scheme.equals("https", ignoreCase = true)) {
            "Mercado Livre order endpoint must use HTTPS"
        }
        require(endpoint.host != null) {
            "Mercado Livre order endpoint must be absolute"
        }
    }

    override fun readPage(
        capability: ConnectorCapability,
        credentialBytes: ByteArray,
        currentProgress: ConnectorProgress?,
        budget: ConnectorBudget,
        cancellation: ConnectorCancellation
    ): ConnectorReadResult {
        if (capability != MarketplaceEconomicOrderSourceCapability.KEY &&
            capability != MarketplaceEconomicOrderSourceCapability.REACQUISITION_KEY
        ) {
            return failed(ConnectorAdapterFailureKind.REMOTE_PERMANENT)
        }
        if (cancellation.isCancelled()) {
            return failed(ConnectorAdapterFailureKind.CANCELLED)
        }

        val now = clock.instant()
        if (!now.isBefore(budget.deadline)) {
            return failed(ConnectorAdapterFailureKind.BUDGET_EXCEEDED)
        }

        val currentHour = now.truncatedTo(ChronoUnit.HOURS)
        val cursor = if (currentProgress == null) {
            val lookbackHours = if (
                capability == MarketplaceEconomicOrderSourceCapability.REACQUISITION_KEY
            ) {
                REACQUISITION_LOOKBACK_HOURS
            } else {
                NORMAL_LOOKBACK_HOURS
            }
            SourceCursor(currentHour.minus(lookbackHours, ChronoUnit.HOURS), 0)
        } else {
            decodeProgress(currentProgress)
                ?: return failed(ConnectorAdapterFailureKind.REMOTE_DATA_INVALID)
        }

        if (cursor.windowFromHour > currentHour) {
            return failed(ConnectorAdapterFailureKind.REMOTE_DATA_INVALID)
        }

        if (cursor.windowFromHour == currentHour) {
            val untilNextHour = Duration.between(now, currentHour.plus(1, ChronoUnit.HOURS))
            return failed(
                ConnectorAdapterFailureKind.REMOTE_TEMPORARY,
                untilNextHour.coerceAtLeast(Duration.ofSeconds(1))
            )
        }

        val pageSize = minOf(budget.maxRecords, MAX_PROVIDER_PAGE_SIZE)
        val result = MercadoLivreOAuthCredentialEnvelopeCodec.withReadAccess(
            credentialBytes
        ) { sellerId, accessToken ->
            if (cancellation.isCancelled()) {
                return@withReadAccess failed(ConnectorAdapterFailureKind.CANCELLED)
            }
            executeRemote(cursor, sellerId, accessToken, pageSize, budget, cancellation)
        }

        return result ?: failed(ConnectorAdapterFailureKind.AUTHENTICATION_REQUIRED)
    }

    private fun executeRemote(
        cursor: SourceCursor,
        sellerId: Long,
        accessToken: String,
        pageSize: Int,
        budget: ConnectorBudget,
        cancellation: ConnectorCancellation
    ): ConnectorReadResult {
        val requestUri = buildRequestUri(cursor, sellerId, pageSize)
        val remaining = Duration.between(clock.instant(), budget.deadline)
        if (remaining.isZero || remaining.isNegative) {
            return failed(ConnectorAdapterFailureKind.BUDGET_EXCEEDED)
        }

        val response = try {
            transport.get(requestUri, accessToken, remaining, budget.maxResponseBytes)
        } catch (_: MercadoLivreOrderResponseTooLargeException) {
            return failed(ConnectorAdapterFailureKind.BUDGET_EXCEEDED)
        } catch (_: HttpTimeoutException) {
            return failed(ConnectorAdapterFailureKind.BUDGET_EXCEEDED)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return failed(ConnectorAdapterFailureKind.CANCELLED)
        } catch (_: IOException) {
            return failed(ConnectorAdapterFailureKind.REMOTE_TEMPORARY)
        } catch (_: Exception) {
            return failed(ConnectorAdapterFailureKind.REMOTE_TEMPORARY)
        }

        try {
            if (response.body.size.toLong() > budget.maxResponseBytes) {
                return failed(ConnectorAdapterFailureKind.BUDGET_EXCEEDED)
            }
            if (cancellation.isCancelled()) {
                return failed(ConnectorAdapterFailureKind.CANCELLED)
            }

            statusFailure(response)?.let { return it }

            val observedAt = clock.instant().truncatedTo(ChronoUnit.MICROS)
            val parsed = try {
                parsePage(
                    response.body,
                    cursor,
                    pageSize,
                    budget.maxRecords,
                    observedAt
                )
            } catch (_: BudgetViolation) {
                return failed(ConnectorAdapterFailureKind.BUDGET_EXCEEDED)
            } catch (_: Exception) {
                return failed(ConnectorAdapterFailureKind.REMOTE_DATA_INVALID)
            }

            return ConnectorReadResult.Page(
                ConnectorPage(
                    parsed.records,
                    ConnectorProgress.take(encodeProgress(parsed.nextCursor)),
                    observedAt,
                    exhausted = false,
                    responseBytes = response.body.size.toLong()
                )
            )
        } finally {
            response.body.fill(0)
        }
    }

    private fun statusFailure(
        response: MercadoLivreOrderHttpResponse
    ): ConnectorReadResult.Failed? {
        if (response.statusCode == 206) {
            val missing = response.contentMissing
                ?.split(",")
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.map { it.lowercase() }
                ?.toSet()
                ?: emptySet()

            if (missing.isEmpty() || !ALLOWED_PARTIAL_MISSING.containsAll(missing)) {
                return failed(ConnectorAdapterFailureKind.REMOTE_DATA_INVALID)
            }
            return null
        }

        val kind = when (response.statusCode) {
            in 200..299 -> return null
            401 -> ConnectorAdapterFailureKind.AUTHENTICATION_REQUIRED
            403 -> ConnectorAdapterFailureKind.AUTHORIZATION_DENIED
            408, 425 -> ConnectorAdapterFailureKind.REMOTE_TEMPORARY
            429 -> ConnectorAdapterFailureKind.RATE_LIMITED
            in 500..599 -> ConnectorAdapterFailureKind.REMOTE_TEMPORARY
            else -> ConnectorAdapterFailureKind.REMOTE_PERMANENT
        }

        val retry = if (
            kind == ConnectorAdapterFailureKind.RATE_LIMITED ||
            kind == ConnectorAdapterFailureKind.REMOTE_TEMPORARY
        ) {
            parseRetryAfter(response.retryAfter)
        } else {
            null
        }

        return failed(kind, retry)
    }

    private fun parsePage(
        bytes: ByteArray,
        requested: SourceCursor,
        requestedLimit: Int,
        maxRecords: Int,
        observedAt: Instant
    ): ParsedPage {
        val root = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        val paging = root.requiredObject("paging")
        val total = paging.requiredInt("total")
        val offset = paging.requiredInt("offset")
        val limit = paging.requiredInt("limit")

        require(total >= 0 && offset >= 0 && limit > 0) {
            "Invalid provider paging"
        }
        require(offset == requested.offset && limit == requestedLimit) {
            "Provider paging mismatch"
        }

        val results = root["results"]?.jsonArray ?: error("Missing provider results")
        if (results.size > maxRecords) throw BudgetViolation()
        require(offset.toLong() + results.size.toLong() <= total.toLong()) {
            "Provider paging exceeds total"
        }
        require(results.isNotEmpty() || offset >= total) {
            "Provider returned empty nonterminal page"
        }

        val records = results.map {
            parseOrder(it.jsonObject, observedAt)
        }

        val consumed = offset + results.size
        val next = if (consumed >= total) {
            SourceCursor(requested.windowFromHour.plus(1, ChronoUnit.HOURS), 0)
        } else {
            SourceCursor(requested.windowFromHour, consumed)
        }

        return ParsedPage(records, next)
    }

    private fun parseOrder(
        value: JsonObject,
        observedAt: Instant
    ): MercadoLivreOrderSourceRecord {
        val items = value.requiredArray("order_items")
        require(items.isNotEmpty() && items.size <= MAX_CHILDREN) {
            "Invalid provider order item count"
        }

        val payments = value.optionalArray("payments")
        require(payments.size <= MAX_CHILDREN) {
            "Invalid provider payment count"
        }

        return MercadoLivreOrderSourceRecord(
            externalOrderReference = MercadoLivreOrderReference.of(
                value.requiredIdentifier("id")
            ),
            providerStatus = MercadoLivreProviderStatus.of(
                value.requiredText("status")
            ),
            dateCreated = value.requiredInstant("date_created"),
            dateLastUpdated = value.requiredInstant("date_last_updated"),
            dateClosed = value.optionalInstant("date_closed"),
            currency = MercadoLivreSourceCurrency.of(value.requiredText("currency_id")),
            totalAmount = value.requiredDecimal("total_amount"),
            paidAmount = value.optionalDecimal("paid_amount"),
            packReference = value.optionalIdentifier("pack_id")?.let(
                MercadoLivrePackReference::of
            ),
            shippingReference = value.optionalObject("shipping")
                ?.optionalIdentifier("id")
                ?.let(MercadoLivreShippingReference::of),
            orderItems = items.map { parseItem(it.jsonObject) },
            payments = payments.map { parsePayment(it.jsonObject) },
            observedAt = observedAt
        )
    }

    private fun parseItem(value: JsonObject): MercadoLivreOrderItemSourceObservation {
        val item = value.requiredObject("item")
        return MercadoLivreOrderItemSourceObservation(
            itemReference = MercadoLivreItemReference.of(
                item.requiredIdentifier("id")
            ),
            variationReference = item.optionalIdentifier("variation_id")
                ?.let(MercadoLivreVariationReference::of),
            quantity = value.requiredDecimal("quantity"),
            unitPrice = value.requiredDecimal("unit_price"),
            currency = MercadoLivreSourceCurrency.of(value.requiredText("currency_id")),
            saleFee = value.optionalDecimal("sale_fee"),
            grossPrice = value.optionalDecimal("full_unit_price"),
            sellerSku = (item["seller_sku"] as? JsonPrimitive)?.content?.trim()
                ?.takeIf { it.isNotEmpty() }?.let(MercadoLivreSellerSkuReference::of)
        )
    }

    private fun parsePayment(value: JsonObject): MercadoLivrePaymentSourceObservation =
        MercadoLivrePaymentSourceObservation(
            paymentReference = MercadoLivrePaymentReference.of(
                value.requiredIdentifier("id")
            ),
            providerStatus = MercadoLivreProviderStatus.of(
                value.requiredText("status")
            ),
            transactionAmount = value.requiredDecimal("transaction_amount"),
            currency = MercadoLivreSourceCurrency.of(value.requiredText("currency_id")),
            dateCreated = value.optionalInstant("date_created"),
            dateLastModified = value.optionalInstant("date_last_modified")
        )

    private fun buildRequestUri(
        cursor: SourceCursor,
        sellerId: Long,
        pageSize: Int
    ): URI {
        val values = listOf(
            "seller" to sellerId.toString(),
            "order.date_last_updated.from" to SOURCE_DATE_FORMAT.format(cursor.windowFromHour),
            "order.date_last_updated.to" to SOURCE_DATE_FORMAT.format(
                cursor.windowFromHour.plus(1, ChronoUnit.HOURS)
            ),
            "offset" to cursor.offset.toString(),
            "limit" to pageSize.toString()
        )

        val query = values.joinToString("&") { (name, value) ->
            "${encodeQuery(name)}=${encodeQuery(value)}"
        }

        return URI.create("${endpoint}?$query")
    }

    private fun decodeProgress(progress: ConnectorProgress): SourceCursor? = try {
        progress.useBytes { bytes ->
            val text = bytes.decodeToString()
            val match = PROGRESS.matchEntire(text) ?: return@useBytes null
            val hour = Instant.parse(match.groupValues[1])
            val offset = match.groupValues[2].toIntOrNull() ?: return@useBytes null
            if (hour != hour.truncatedTo(ChronoUnit.HOURS)) return@useBytes null
            if (offset !in 0..MAX_OFFSET) return@useBytes null
            SourceCursor(hour, offset)
        }
    } catch (_: Exception) {
        null
    }

    private fun encodeProgress(cursor: SourceCursor): ByteArray {
        require(cursor.windowFromHour == cursor.windowFromHour.truncatedTo(ChronoUnit.HOURS)) {
            "Provider progress hour must be aligned"
        }
        require(cursor.offset in 0..MAX_OFFSET) { "Provider progress offset is invalid" }
        return "v1|hour=${cursor.windowFromHour}|offset=${cursor.offset}"
            .toByteArray(StandardCharsets.US_ASCII)
    }

    private fun failed(
        kind: ConnectorAdapterFailureKind,
        retryAfter: Duration? = null
    ) = ConnectorReadResult.Failed(ConnectorAdapterFailure.of(kind, retryAfter))

    private data class SourceCursor(
        val windowFromHour: Instant,
        val offset: Int
    )

    private data class ParsedPage(
        val records: List<MercadoLivreOrderSourceRecord>,
        val nextCursor: SourceCursor
    )

    private class BudgetViolation : RuntimeException()

    companion object {
        val DEFAULT_ENDPOINT: URI =
            URI.create("https://api.mercadolibre.com/orders/search")

        internal const val MAX_PROVIDER_PAGE_SIZE = 50
        internal const val MAX_CHILDREN = 100
        internal const val MAX_OFFSET = 10_000_000
        internal const val NORMAL_LOOKBACK_HOURS = 1L
        internal const val REACQUISITION_LOOKBACK_HOURS = 48L

        private val SOURCE_DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
                .withZone(ZoneOffset.UTC)

        private val PROGRESS =
            Regex("v1\\|hour=([0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:00:00Z)\\|offset=([0-9]{1,8})")

        private val ALLOWED_PARTIAL_MISSING = setOf(
            "buyer",
            "feedback",
            "location",
            "geolocation",
            "seller_address"
        )
    }
}

class JdkMercadoLivreOrderHttpTransport(
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
) : MercadoLivreOrderHttpTransport {
    override fun get(
        endpoint: URI,
        accessToken: String,
        timeout: Duration,
        maxResponseBytes: Long
    ): MercadoLivreOrderHttpResponse {
        require(!timeout.isNegative && !timeout.isZero) {
            "Invalid Mercado Livre order request timeout"
        }

        val request = HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("Accept", "application/json")
            .header("Authorization", "Bearer $accessToken")
            .GET()
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        val responseBytes = response.body().use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            try {
                var total = 0L
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxResponseBytes) {
                        throw MercadoLivreOrderResponseTooLargeException()
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            } finally {
                buffer.fill(0)
            }
        }

        return MercadoLivreOrderHttpResponse(
            statusCode = response.statusCode(),
            body = responseBytes,
            retryAfter = response.headers().firstValue("Retry-After").orElse(null),
            contentMissing = response.headers().firstValue("X-Content-Missing").orElse(null)
        )
    }
}

private class MercadoLivreOrderResponseTooLargeException : IOException()

private fun encodeQuery(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)

private fun parseRetryAfter(value: String?): Duration? =
    value?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.let(Duration::ofSeconds)

private fun JsonObject.requiredObject(name: String): JsonObject =
    this[name]?.let {
        require(it !is JsonNull) { "Missing provider object" }
        it.jsonObject
    } ?: error("Missing provider object")

private fun JsonObject.optionalObject(name: String): JsonObject? {
    val value = this[name] ?: return null
    if (value is JsonNull) return null
    return value.jsonObject
}

private fun JsonObject.requiredArray(name: String): JsonArray =
    this[name]?.let {
        require(it !is JsonNull) { "Missing provider array" }
        it.jsonArray
    } ?: error("Missing provider array")

private fun JsonObject.optionalArray(name: String): JsonArray {
    val value = this[name] ?: return JsonArray(emptyList())
    if (value is JsonNull) return JsonArray(emptyList())
    return value.jsonArray
}

private fun JsonObject.requiredText(name: String): String {
    val value = this[name]?.jsonPrimitive ?: error("Missing provider text")
    require(value.isString) { "Invalid provider text" }
    return value.content.trim().takeIf(String::isNotEmpty)
        ?: error("Invalid provider text")
}

private fun JsonObject.requiredIdentifier(name: String): String {
    val value = this[name]?.jsonPrimitive ?: error("Missing provider identifier")
    val text = value.content.trim()
    require(text.isNotEmpty() && text.none(Char::isISOControl)) {
        "Invalid provider identifier"
    }
    return text
}

private fun JsonObject.optionalIdentifier(name: String): String? {
    val element = this[name] ?: return null
    if (element is JsonNull) return null
    val text = element.jsonPrimitive.content.trim()
    require(text.isNotEmpty() && text.none(Char::isISOControl)) {
        "Invalid provider identifier"
    }
    return text
}

private fun JsonObject.requiredInt(name: String): Int {
    val value = this[name]?.jsonPrimitive ?: error("Missing provider integer")
    require(!value.isString) { "Invalid provider integer" }
    return value.intOrNull ?: error("Invalid provider integer")
}

private fun JsonObject.requiredDecimal(name: String): ProviderSourceDecimal {
    val value = this[name]?.jsonPrimitive ?: error("Missing provider decimal")
    require(!value.isString) { "Invalid provider decimal" }
    return ProviderSourceDecimal.parse(value.content)
}

private fun JsonObject.optionalDecimal(name: String): ProviderSourceDecimal? {
    val element = this[name] ?: return null
    if (element is JsonNull) return null
    val value = element.jsonPrimitive
    require(!value.isString) { "Invalid provider decimal" }
    return ProviderSourceDecimal.parse(value.content)
}

private fun JsonObject.requiredInstant(name: String): Instant =
    parseProviderInstant(requiredText(name))

private fun JsonObject.optionalInstant(name: String): Instant? {
    val element = this[name] ?: return null
    if (element is JsonNull) return null
    val value = element.jsonPrimitive
    require(value.isString) { "Invalid provider time" }
    return parseProviderInstant(value.content)
}

private fun parseProviderInstant(value: String): Instant =
    OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        .toInstant()
        .truncatedTo(ChronoUnit.MICROS)

private fun Duration.coerceAtLeast(minimum: Duration): Duration =
    if (this < minimum) minimum else this
