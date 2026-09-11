package io.flooow.marketplace.api

import io.flooow.marketplace.operations.economics.reconciliation.DurableReconciliationCase
import io.flooow.marketplace.operations.economics.reconciliation.DurableReconciliationCaseRepository
import io.flooow.marketplace.operations.economics.reconciliation.ReconciliationCaseCursor
import io.flooow.marketplace.operations.economics.reconciliation.ReconciliationCaseId
import io.flooow.marketplace.operations.economics.reconciliation.ReconciliationCasePage
import io.flooow.marketplace.operations.economics.reconciliation.ReconciliationCaseStageDifference
import io.flooow.organization.OrganizationId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

internal const val RECONCILIATION_CASES_PATH = "/v1/reconciliation/cases"

internal class ReconciliationCasesApi(
    private val repository: DurableReconciliationCaseRepository,
    private val cursors: ReconciliationCaseCursorCodec
) {
    fun list(organizationId: OrganizationId, rawCursor: String?, rawLimit: String?): JsonObject {
        val limit = rawLimit?.toIntOrNull() ?: 50
        if (limit !in 1..100) throw InvalidReconciliationCaseCursorException()
        val cursor = rawCursor?.let { cursors.decode(it, organizationId) ?: throw InvalidReconciliationCaseCursorException() }
        val page = repository.list(organizationId, cursor, limit)
        if (page.cases.any { it.organizationId != organizationId }) throw ReconciliationCaseReadFailureException()
        return buildJsonObject {
            put("cases", buildJsonArray { page.cases.forEach { add(caseJson(it)) } })
            page.nextCursor?.let { put("nextCursor", cursors.encode(it, organizationId)) }
        }
    }

    fun detail(organizationId: OrganizationId, rawId: String): JsonObject {
        val id = try { ReconciliationCaseId.of(UUID.fromString(rawId).also { require(it.toString() == rawId) }) }
        catch (_: Exception) { throw InvalidReconciliationCaseIdException() }
        return repository.find(organizationId, id)?.let { value ->
            if (value.organizationId != organizationId) throw ReconciliationCaseReadFailureException()
            caseJson(value)
        } ?: throw ReconciliationCaseNotFoundException()
    }
}

internal class ReconciliationCaseCursorCodec(secret: ByteArray) {
    private val key = hmac(secret, "flooow.reconciliation-case.cursor.v1".toByteArray())
    fun encode(cursor: ReconciliationCaseCursor, organizationId: OrganizationId): String {
        val payload = ByteBuffer.allocate(44)
            .putLong(cursor.observedAt.epochSecond).putInt(cursor.observedAt.nano)
            .putLong(cursor.caseId.value.mostSignificantBits).putLong(cursor.caseId.value.leastSignificantBits)
            .putLong(organizationId.value.mostSignificantBits).putLong(organizationId.value.leastSignificantBits).array()
        val body = BASE64.encodeToString(payload)
        return "v1.$body.${BASE64.encodeToString(hmac(key, "v1.$body".toByteArray(StandardCharsets.US_ASCII)))}"
    }
    fun decode(encoded: String, organizationId: OrganizationId): ReconciliationCaseCursor? = try {
        val parts = encoded.split('.')
        if (parts.size != 3 || parts[0] != "v1") return null
        val payload = DECODER.decode(parts[1]); val tag = DECODER.decode(parts[2])
        if (BASE64.encodeToString(payload) != parts[1] || BASE64.encodeToString(tag) != parts[2]) return null
        if (!MessageDigest.isEqual(tag, hmac(key, "v1.${parts[1]}".toByteArray(StandardCharsets.US_ASCII)))) return null
        if (payload.size != 44) return null
        val values = ByteBuffer.wrap(payload); val observed = Instant.ofEpochSecond(values.long, values.int.toLong())
        val id = UUID(values.long, values.long); val org = UUID(values.long, values.long)
        if (org != organizationId.value) null else ReconciliationCaseCursor(observed, ReconciliationCaseId.of(id))
    } catch (_: Exception) { null }
    companion object { private val BASE64 = Base64.getUrlEncoder().withoutPadding(); private val DECODER = Base64.getUrlDecoder() }
}

private fun hmac(key: ByteArray, value: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(value) }

private fun caseJson(value: DurableReconciliationCase) = buildJsonObject {
    put("caseId", value.caseId.value.toString()); put("marketplaceOrderId", value.orderId.value.toString())
    put("financialTraceId", value.traceId.value.toString()); put("policyVersion", value.policyVersion.value)
    put("currency", value.currency.code); put("status", value.status.name); put("openedAt", value.openedAt.toString())
    put("lastObservedAt", value.lastObservedAt.toString()); value.resolvedAt?.let { put("resolvedAt", it.toString()) }
    put("revision", value.revision); put("absoluteDifferenceSummary", money(value.absoluteDifferenceSummary))
    put("evidenceEntryIds", buildJsonArray { value.evidenceEntryIds.forEach { add(JsonPrimitive(it.value.toString())) } })
    put("stages", buildJsonArray { value.stages.forEach { add(stageJson(it)) } })
}

private fun stageJson(value: ReconciliationCaseStageDifference) = buildJsonObject {
    put("stage", value.stage.name); value.expected?.let { put("expected", money(it)) }
    value.actual?.let { put("actual", money(it)) }; value.signedDifference?.let { put("signedDifference", money(it)) }
    value.absoluteDifference?.let { put("absoluteDifference", money(it)) }; put("tolerance", money(value.tolerance))
    put("expectedEntryIds", buildJsonArray { value.expectedEntryIds.forEach { add(JsonPrimitive(it.value.toString())) } })
    put("actualEntryIds", buildJsonArray { value.actualEntryIds.forEach { add(JsonPrimitive(it.value.toString())) } })
}

private fun money(value: io.flooow.marketplace.operations.economics.MarketplaceMoney) = buildJsonObject {
    put("currency", value.currency.code); put("amount", value.amount.toPlainString())
}

internal class InvalidReconciliationCaseCursorException : RuntimeException()
internal class InvalidReconciliationCaseIdException : RuntimeException()
internal class ReconciliationCaseNotFoundException : RuntimeException()
internal class ReconciliationCaseReadFailureException : RuntimeException()
