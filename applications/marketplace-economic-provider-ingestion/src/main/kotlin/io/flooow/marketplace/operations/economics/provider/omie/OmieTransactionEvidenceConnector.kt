package io.flooow.marketplace.operations.economics.provider.omie

import io.flooow.integration.connector.*
import io.flooow.integration.control.ProviderKey
import io.flooow.marketplace.operations.economics.provider.*
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.security.MessageDigest
import kotlinx.serialization.json.*

/** Read-only Omie ListarPedidos adapter, promoted from the MGI connector contract. */
class OmieTransactionEvidenceConnector(
    private val endpoint: URI = DEFAULT_ENDPOINT,
    private val clock: Clock = Clock.systemUTC(),
    private val transport: OmieHttpTransport = JdkOmieTransactionTransport()
) : PullConnector {
    override val descriptor = ConnectorDescriptor(
        ProviderKey.of("omie"),
        listOf(
            ConnectorRecordDefinition(OmieTransactionEvidenceCapability.KEY, OmieTransactionEvidenceRecord::class),
            ConnectorRecordDefinition(OmieTransactionEvidenceCapability.REACQUISITION_V1_KEY, OmieTransactionEvidenceRecord::class),
            ConnectorRecordDefinition(OmieTransactionEvidenceCapability.REACQUISITION_KEY, OmieTransactionEvidenceRecord::class)
        )
    )

    init { require(endpoint.scheme.equals("https", true)); require(endpoint.host != null) }

    override fun readPage(capability: ConnectorCapability, credentialBytes: ByteArray, currentProgress: ConnectorProgress?, budget: ConnectorBudget, cancellation: ConnectorCancellation): ConnectorReadResult {
        if (capability != OmieTransactionEvidenceCapability.KEY &&
            capability != OmieTransactionEvidenceCapability.REACQUISITION_V1_KEY &&
            capability != OmieTransactionEvidenceCapability.REACQUISITION_KEY
        ) return failed(ConnectorAdapterFailureKind.REMOTE_PERMANENT)
        if (cancellation.isCancelled()) return failed(ConnectorAdapterFailureKind.CANCELLED)
        if (!clock.instant().isBefore(budget.deadline)) return failed(ConnectorAdapterFailureKind.BUDGET_EXCEEDED)
        val page = decodePage(currentProgress) ?: if (currentProgress == null) 1 else return failed(ConnectorAdapterFailureKind.REMOTE_DATA_INVALID)
        val credential = decodeCredential(credentialBytes) ?: return failed(ConnectorAdapterFailureKind.AUTHENTICATION_REQUIRED)
        val body = buildJsonObject {
            put("call", "ListarPedidos"); put("app_key", credential.first); put("app_secret", credential.second)
            put("param", buildJsonArray { add(buildJsonObject { put("pagina", page); put("registros_por_pagina", minOf(budget.maxRecords, 100)); put("apenas_importado_api", "N") }) })
        }.toString().toByteArray()
        val response = try {
            transport.post(endpoint, body, Duration.between(clock.instant(), budget.deadline), budget.maxResponseBytes)
        } catch (_: Exception) { body.fill(0); return failed(ConnectorAdapterFailureKind.REMOTE_TEMPORARY) } finally { body.fill(0) }
        try {
            if (response.statusCode !in 200..299) return failed(if (response.statusCode == 429) ConnectorAdapterFailureKind.RATE_LIMITED else if (response.statusCode in 500..599) ConnectorAdapterFailureKind.REMOTE_TEMPORARY else ConnectorAdapterFailureKind.REMOTE_PERMANENT)
            val observed = clock.instant().truncatedTo(ChronoUnit.MICROS)
            val root = Json.parseToJsonElement(response.body.decodeToString()).jsonObject
            if (root.containsKey("faultstring") || root.containsKey("faultcode")) return failed(ConnectorAdapterFailureKind.REMOTE_PERMANENT)
            val totalPages = (root["nTotPaginas"] ?: root["total_de_paginas"])?.jsonPrimitive?.intOrNull ?: 1
            val nodes = sequenceOf("pedido_venda", "pedidos", "lista_pedidos", "pedido_venda_produto").mapNotNull { root[it] }.firstOrNull()
            val records = (nodes as? JsonArray ?: JsonArray(emptyList())).map { parseRecord(it.jsonObject, observed) }
            if (records.size > budget.maxRecords) return failed(ConnectorAdapterFailureKind.BUDGET_EXCEEDED)
            val next = if (page >= totalPages) null else ConnectorProgress.take("page=${page + 1}".toByteArray())
            return ConnectorReadResult.Page(ConnectorPage(records, next, observed, next == null, response.body.size.toLong()))
        } catch (_: Exception) { return failed(ConnectorAdapterFailureKind.REMOTE_DATA_INVALID) } finally { response.body.fill(0) }
    }

    private fun parseRecord(o: JsonObject, observed: Instant): OmieTransactionEvidenceRecord {
        val header = (o["cabecalho"] ?: o["cab"] ?: o).jsonObject
        val ref = text(header, "codigo_pedido") ?: text(o, "codigo_pedido") ?: error("Missing order code")
        val additional = (o["informacoes_adicionais"] ?: o["informacoes_adicionais_pedido"])?.jsonObject
        val integration = text(header, "codigo_pedido_integracao")?.let(OmieIntegrationReference::of)
        val customer = (text(additional, "numero_pedido_cliente") ?: text(header, "numero_pedido_cliente"))
            ?.let(OmieCustomerOrderReference::of)
        val occurred = (text(header, "data_previsao") ?: text(header, "data_faturamento") ?: text(header, "data_pedido"))?.let { parseDate(it) }
        val totals = o["total_pedido"]?.jsonObject
        val amount = (text(totals, "valor_total_pedido") ?: text(header, "valor_total_pedido") ?: text(header, "valor_total"))
            ?.let { ProviderSourceDecimal.parse(it.replace(',', '.')) }
        val currency = text(header, "codigo_moeda")?.uppercase()?.let(MercadoLivreSourceCurrency::of)
        val status = text(header, "etapa")?.let(OmieOrderStatus::of)
        val details = (o["det"] ?: o["itens"] ?: o["detalhes"]) as? JsonArray ?: JsonArray(emptyList())
        val products = details.flatMap { d ->
            val detail = d.jsonObject
            val x = detail["produto"]?.jsonObject ?: detail["ide"]?.jsonObject ?: detail
            val qty = (text(x, "quantidade") ?: text(detail, "quantidade"))
                ?.let { ProviderSourceDecimal.parse(it.replace(',', '.')) } ?: return@flatMap emptyList()
            listOf(
                "codigo_produto" to OmieTransactionProductIdentifierKind.INTERNAL_PRODUCT_ID,
                "codigo_produto_integracao" to OmieTransactionProductIdentifierKind.INTEGRATION_PRODUCT_CODE,
                "codigo" to OmieTransactionProductIdentifierKind.DISPLAY_PRODUCT_CODE,
                // Omie's compact pedido aliases use cCodInt for the integration
                // code and cCodigo for the displayed product code.
                "cCodInt" to OmieTransactionProductIdentifierKind.INTEGRATION_PRODUCT_CODE,
                "cCodigo" to OmieTransactionProductIdentifierKind.DISPLAY_PRODUCT_CODE
            ).mapNotNull { (field, kind) ->
                text(x, field)?.let { value ->
                    OmieTransactionProductObservation(
                        OmieTransactionProductIdentifier.of(kind, value),
                        qty
                    )
                }
            }.distinctBy { it.identifier.kind to it.identifier.encodedForPersistence() }
        }
        val fingerprint = MessageDigest.getInstance("SHA-256").digest(o.toString().toByteArray()).joinToString("") { "%02x".format(it) }
        return OmieTransactionEvidenceRecord(OmieOrderReference.of(ref), integration, customer, occurred, status, currency, amount, products, observed, fingerprint)
    }

    private fun text(o: JsonObject?, key: String): String? = (o?.get(key) as? JsonPrimitive)?.content?.trim()?.takeIf { it.isNotEmpty() }
    private fun parseDate(v: String): Instant? = runCatching { LocalDate.parse(v.take(10), DateTimeFormatter.ofPattern("dd/MM/uuuu")).atStartOfDay(ZoneOffset.UTC).toInstant() }.getOrNull()
    private fun decodePage(p: ConnectorProgress?) = p?.useBytes { Regex("page=([1-9][0-9]*)").matchEntire(it.decodeToString())?.groupValues?.get(1)?.toIntOrNull()?.takeIf { n -> n >= 2 } }
    private fun decodeCredential(b: ByteArray): Pair<String, String>? = runCatching { val o = Json.parseToJsonElement(b.decodeToString()).jsonObject; if (o["schemaVersion"]?.jsonPrimitive?.intOrNull != 1) null else Pair(o["appKey"]!!.jsonPrimitive.content, o["appSecret"]!!.jsonPrimitive.content).takeIf { it.first.isNotBlank() && it.second.isNotBlank() } }.getOrNull()
    private fun failed(k: ConnectorAdapterFailureKind) = ConnectorReadResult.Failed(ConnectorAdapterFailure.of(k))
    companion object { val DEFAULT_ENDPOINT: URI = URI.create("https://app.omie.com.br/api/v1/produtos/pedido/") }
}

private class JdkOmieTransactionTransport(private val client: HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) : OmieHttpTransport {
    override fun post(endpoint: URI, body: ByteArray, timeout: Duration, maxResponseBytes: Long): OmieHttpResponse {
        val response = client.send(HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type", "application/json").header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofByteArray(body)).build(), HttpResponse.BodyHandlers.ofInputStream())
        val out = ByteArrayOutputStream(); response.body().use { input -> val buf = ByteArray(8192); var n: Int; var total = 0L; while (input.read(buf).also { n = it } >= 0) { total += n; if (total > maxResponseBytes) throw IOException("response too large"); out.write(buf, 0, n) } }
        return OmieHttpResponse(response.statusCode(), out.toByteArray(), response.headers().firstValue("Retry-After").orElse(null))
    }
}
