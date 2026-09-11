package io.flooow.marketplace.operations.economics.provider.omie

import io.flooow.integration.connector.ConnectorAdapterFailure
import io.flooow.integration.connector.ConnectorAdapterFailureKind
import io.flooow.integration.connector.ConnectorBudget
import io.flooow.integration.connector.ConnectorCancellation
import io.flooow.integration.connector.ConnectorDescriptor
import io.flooow.integration.connector.ConnectorPage
import io.flooow.integration.connector.ConnectorProgress
import io.flooow.integration.connector.ConnectorReadResult
import io.flooow.integration.connector.ConnectorRecordDefinition
import io.flooow.integration.connector.PullConnector
import io.flooow.integration.connector.ConnectorCapability
import io.flooow.integration.control.ProviderKey
import io.flooow.marketplace.operations.economics.provider.MarketplaceEconomicProductCostCapability
import io.flooow.marketplace.operations.economics.provider.OmieDisplayedProductCode
import io.flooow.marketplace.operations.economics.provider.OmieIntegrationReference
import io.flooow.marketplace.operations.economics.provider.OmieLocationReference
import io.flooow.marketplace.operations.economics.provider.OmieProductCostSourceRecord
import io.flooow.marketplace.operations.economics.provider.OmieProductReference
import io.flooow.marketplace.operations.economics.provider.ProviderSourceDecimal
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

data class OmieHttpResponse(
    val statusCode: Int,
    val body: ByteArray,
    val retryAfter: String? = null
) {
    override fun toString(): String =
        "OmieHttpResponse(statusCode=$statusCode, body=[REDACTED], retryAfter=$retryAfter)"
}

fun interface OmieHttpTransport {
    fun post(
        endpoint: URI,
        body: ByteArray,
        timeout: Duration,
        maxResponseBytes: Long
    ): OmieHttpResponse
}

class OmieEconomicEvidenceConnector(
    private val endpoint: URI = DEFAULT_ENDPOINT,
    private val clock: Clock = Clock.system(ZoneId.of("America/Sao_Paulo")),
    private val transport: OmieHttpTransport = JdkOmieHttpTransport()
) : PullConnector {
    override val descriptor = ConnectorDescriptor(
        ProviderKey.of("omie"),
        listOf(
            ConnectorRecordDefinition(
                MarketplaceEconomicProductCostCapability.KEY,
                OmieProductCostSourceRecord::class
            )
        )
    )

    init {
        require(endpoint.scheme.equals("https", ignoreCase = true)) {
            "Omie endpoint must use HTTPS"
        }
        require(endpoint.host != null) { "Omie endpoint must be absolute" }
    }

    override fun readPage(
        capability: ConnectorCapability,
        credentialBytes: ByteArray,
        currentProgress: ConnectorProgress?,
        budget: ConnectorBudget,
        cancellation: ConnectorCancellation
    ): ConnectorReadResult {
        if (capability != MarketplaceEconomicProductCostCapability.KEY) {
            return failed(ConnectorAdapterFailureKind.REMOTE_PERMANENT)
        }
        if (cancellation.isCancelled()) {
            return failed(ConnectorAdapterFailureKind.CANCELLED)
        }

        val now = clock.instant()
        if (!now.isBefore(budget.deadline)) {
            return failed(ConnectorAdapterFailureKind.BUDGET_EXCEEDED)
        }

        val requestedPage = decodeProgress(currentProgress)
            ?: if (currentProgress == null) 1 else {
                return failed(ConnectorAdapterFailureKind.REMOTE_DATA_INVALID)
            }

        val credential = decodeCredential(credentialBytes)
            ?: return failed(ConnectorAdapterFailureKind.AUTHENTICATION_REQUIRED)

        val positionDate = LocalDate.now(clock)
        val pageSize = minOf(budget.maxRecords, MAX_PROVIDER_PAGE_SIZE)
        val requestBytes = requestBody(
            credential,
            requestedPage,
            pageSize,
            positionDate
        )

        val response = try {
            val timeout = Duration.between(clock.instant(), budget.deadline)
            if (timeout.isZero || timeout.isNegative) {
                return failed(ConnectorAdapterFailureKind.BUDGET_EXCEEDED)
            }
            transport.post(endpoint, requestBytes, timeout, budget.maxResponseBytes)
        } catch (_: ResponseTooLargeException) {
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
        } finally {
            requestBytes.fill(0)
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
                    requestedPage,
                    positionDate,
                    observedAt,
                    budget.maxRecords
                )
            } catch (_: BudgetViolation) {
                return failed(ConnectorAdapterFailureKind.BUDGET_EXCEEDED)
            } catch (_: Exception) {
                return failed(ConnectorAdapterFailureKind.REMOTE_DATA_INVALID)
            }

            if (cancellation.isCancelled()) {
                parsed.nextProgress?.close()
                return failed(ConnectorAdapterFailureKind.CANCELLED)
            }

            return ConnectorReadResult.Page(
                ConnectorPage(
                    parsed.records,
                    parsed.nextProgress,
                    observedAt,
                    parsed.exhausted,
                    response.body.size.toLong()
                )
            )
        } finally {
            response.body.fill(0)
        }
    }

    private fun parsePage(
        bytes: ByteArray,
        requestedPage: Int,
        requestedDate: LocalDate,
        observedAt: Instant,
        maxRecords: Int
    ): ParsedPage {
        val root = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        if (root.containsKey("faultstring") || root.containsKey("faultcode")) {
            error("Provider failure")
        }

        val page = root.requiredInt("nPagina")
        val totalPages = root.requiredInt("nTotPaginas")
        val recordCount = root.requiredInt("nRegistros")
        val totalRecords = root.requiredInt("nTotRegistros")
        require(page == requestedPage) { "Provider page mismatch" }
        require(totalPages > 0 && page in 1..totalPages) { "Invalid provider pagination" }
        require(recordCount >= 0 && totalRecords >= recordCount) {
            "Invalid provider record counts"
        }

        val responseDate = root.requiredText("dDataPosicao")
            .let { LocalDate.parse(it, POSITION_DATE_FORMAT) }
        require(responseDate == requestedDate) { "Provider position date mismatch" }

        val products = root["produtos"]?.let {
            if (it === JsonNull) emptyList() else it.jsonArray
        } ?: emptyList()

        if (products.size > maxRecords) throw BudgetViolation()
        require(recordCount == products.size) { "Provider record count mismatch" }

        val records = products.map { element ->
            parseRecord(element.jsonObject, responseDate, observedAt)
        }

        val exhausted = page == totalPages
        val nextProgress = if (exhausted) {
            null
        } else {
            ConnectorProgress.take(encodeProgress(page + 1))
        }

        return ParsedPage(records, nextProgress, exhausted)
    }

    private fun parseRecord(
        value: JsonObject,
        positionDate: LocalDate,
        observedAt: Instant
    ): OmieProductCostSourceRecord {
        val productId = value.requiredLongText("nCodProd", allowZero = false)
        val locationId = value.requiredLongText("codigo_local_estoque", allowZero = true)

        return OmieProductCostSourceRecord(
            productReference = OmieProductReference.of(productId),
            integrationReference = value.optionalText("cCodInt")
                ?.let(OmieIntegrationReference::of),
            displayedProductCode = value.optionalText("cCodigo")
                ?.let(OmieDisplayedProductCode::of),
            locationReference = OmieLocationReference.of(locationId),
            unitCmc = value.optionalDecimal("nCMC"),
            stockBalance = value.optionalDecimal("nSaldo"),
            physicalStock = value.optionalDecimal("fisico"),
            reservedStock = value.optionalDecimal("reservado"),
            positionDate = positionDate,
            observedAt = observedAt
        )
    }

    private fun statusFailure(response: OmieHttpResponse): ConnectorReadResult.Failed? {
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

    private fun requestBody(
        credential: OmieCredential,
        page: Int,
        pageSize: Int,
        positionDate: LocalDate
    ): ByteArray {
        val payload = buildJsonObject {
            put("call", "ListarPosEstoque")
            put("app_key", credential.appKey)
            put("app_secret", credential.appSecret)
            put(
                "param",
                buildJsonArray {
                    add(
                        buildJsonObject {
                            put("nPagina", page)
                            put("nRegPorPagina", pageSize)
                            put("dDataPosicao", positionDate.format(POSITION_DATE_FORMAT))
                            put("cExibeTodos", "S")
                            put("codigo_local_estoque", 0)
                            put("lista_local_estoque", "TODOS")
                        }
                    )
                }
            )
        }
        return payload.toString().toByteArray(Charsets.UTF_8)
    }

    private fun decodeCredential(bytes: ByteArray): OmieCredential? = try {
        val root = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        if (root.keys != setOf("schemaVersion", "appKey", "appSecret")) return null
        val version = root["schemaVersion"]?.jsonPrimitive?.intOrNull ?: return null
        if (version != 1) return null
        val appKey = root.requiredSecret("appKey")
        val appSecret = root.requiredSecret("appSecret")
        OmieCredential(appKey, appSecret)
    } catch (_: Exception) {
        null
    }

    private fun decodeProgress(progress: ConnectorProgress?): Int? {
        if (progress == null) return null
        return try {
            progress.useBytes { bytes ->
                val text = bytes.decodeToString()
                val match = PROGRESS.matchEntire(text) ?: return@useBytes null
                match.groupValues[1].toIntOrNull()?.takeIf { it in 2..MAX_PROVIDER_PAGE }
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeProgress(page: Int): ByteArray {
        require(page in 2..MAX_PROVIDER_PAGE) { "Invalid provider next page" }
        return "page=$page".toByteArray(Charsets.US_ASCII)
    }

    private fun failed(
        kind: ConnectorAdapterFailureKind,
        retryAfter: Duration? = null
    ) = ConnectorReadResult.Failed(ConnectorAdapterFailure.of(kind, retryAfter))

    private data class OmieCredential(
        val appKey: String,
        val appSecret: String
    ) {
        override fun toString(): String = "[REDACTED]"
    }

    private data class ParsedPage(
        val records: List<OmieProductCostSourceRecord>,
        val nextProgress: ConnectorProgress?,
        val exhausted: Boolean
    )

    private class BudgetViolation : RuntimeException()

    companion object {
        val DEFAULT_ENDPOINT: URI =
            URI.create("https://app.omie.com.br/api/v1/estoque/consulta/")
        private val POSITION_DATE_FORMAT: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd/MM/uuuu")
        private val PROGRESS = Regex("page=([1-9][0-9]{0,8})")
        private const val MAX_PROVIDER_PAGE = 999_999_999
        private const val MAX_PROVIDER_PAGE_SIZE = 100
    }
}

private class JdkOmieHttpTransport(
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(HttpClient.Redirect.NEVER)
        .build()
) : OmieHttpTransport {
    override fun post(
        endpoint: URI,
        body: ByteArray,
        timeout: Duration,
        maxResponseBytes: Long
    ): OmieHttpResponse {
        val request = HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        val responseBytes = response.body().use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8_192)
            var total = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                total += read
                if (total > maxResponseBytes) throw ResponseTooLargeException()
                output.write(buffer, 0, read)
            }
            buffer.fill(0)
            output.toByteArray()
        }

        return OmieHttpResponse(
            response.statusCode(),
            responseBytes,
            response.headers().firstValue("Retry-After").orElse(null)
        )
    }
}

private class ResponseTooLargeException : IOException()

private fun parseRetryAfter(value: String?): Duration? =
    value?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.let(Duration::ofSeconds)

private fun JsonObject.requiredInt(name: String): Int {
    val primitive = this[name]?.jsonPrimitive ?: error("Missing provider field")
    require(!primitive.isString) { "Invalid provider field" }
    return primitive.intOrNull ?: error("Invalid provider integer")
}

private fun JsonObject.requiredLongText(name: String, allowZero: Boolean): String {
    val primitive = this[name]?.jsonPrimitive ?: error("Missing provider field")
    require(!primitive.isString) { "Invalid provider field" }
    val text = primitive.content
    require(Regex("[0-9]+").matches(text)) { "Invalid provider identifier" }
    val value = text.toLongOrNull() ?: error("Invalid provider identifier")
    require(if (allowZero) value >= 0 else value > 0) { "Invalid provider identifier" }
    return value.toString()
}

private fun JsonObject.requiredText(name: String): String {
    val primitive = this[name]?.jsonPrimitive ?: error("Missing provider field")
    require(primitive.isString) { "Invalid provider text" }
    return primitive.content.takeIf { it.isNotBlank() } ?: error("Invalid provider text")
}

private fun JsonObject.optionalText(name: String): String? {
    val element = this[name] ?: return null
    if (element === JsonNull) return null
    val primitive = element.jsonPrimitive
    require(primitive.isString) { "Invalid provider text" }
    return primitive.content.trim().takeIf(String::isNotEmpty)
}

private fun JsonObject.optionalDecimal(name: String): ProviderSourceDecimal? {
    val element = this[name] ?: return null
    if (element === JsonNull) return null
    val primitive = element.jsonPrimitive
    require(!primitive.isString) { "Invalid provider decimal" }
    return ProviderSourceDecimal.parse(primitive.content)
}

private fun JsonObject.requiredSecret(name: String): String {
    val primitive = this[name]?.jsonPrimitive ?: error("Missing credential field")
    require(primitive.isString) { "Invalid credential field" }
    val value = primitive.content
    require(value.isNotBlank() && value.length <= 512) { "Invalid credential field" }
    return value
}