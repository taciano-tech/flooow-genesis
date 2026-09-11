package io.flooow.marketplace.api

import io.flooow.marketplace.operations.economics.reconciliation.SystemicDivergenceSignal
import io.flooow.marketplace.operations.economics.reconciliation.SystemicDivergenceSignalCursor
import io.flooow.marketplace.operations.economics.reconciliation.SystemicDivergenceSignalId
import io.flooow.marketplace.operations.economics.reconciliation.SystemicDivergenceSignalRepository
import io.flooow.organization.OrganizationId
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Instant
import java.util.Base64
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val SYSTEMIC_DIVERGENCES_PATH = "/v1/reconciliation/systemic-divergences"

internal class SystemicDivergencesApi(private val repository: SystemicDivergenceSignalRepository, private val cursors: SystemicDivergenceCursorCodec) {
    fun list(organizationId: OrganizationId, rawCursor: String?, rawLimit: String?) = buildJsonObject {
        val limit = rawLimit?.toIntOrNull() ?: 50
        if (limit !in 1..100) throw InvalidSystemicDivergenceCursorException()
        val cursor = rawCursor?.let { cursors.decode(it, organizationId) ?: throw InvalidSystemicDivergenceCursorException() }
        val page = repository.list(organizationId, cursor, limit)
        put("signals", buildJsonArray { page.signals.forEach { add(signalJson(it)) } })
        page.nextCursor?.let { put("nextCursor", cursors.encode(it, organizationId)) }
    }
    fun detail(organizationId: OrganizationId, rawId: String) = try {
        val id = SystemicDivergenceSignalId.of(UUID.fromString(rawId).also { require(it.toString() == rawId) })
        repository.find(organizationId, id)?.let(::signalJson) ?: throw SystemicDivergenceSignalNotFoundException()
    } catch (e: SystemicDivergenceSignalNotFoundException) { throw e } catch (_: Exception) { throw InvalidSystemicDivergenceSignalIdException() }
}

internal class SystemicDivergenceCursorCodec(secret: ByteArray) {
    private val key = systemicHmac(secret, "flooow.systemic-divergence.cursor.v1".toByteArray())
    fun encode(cursor: SystemicDivergenceSignalCursor, organizationId: OrganizationId): String {
        val payload = ByteBuffer.allocate(44).putLong(cursor.lastSeenAt.epochSecond).putInt(cursor.lastSeenAt.nano).putLong(cursor.signalId.value.mostSignificantBits).putLong(cursor.signalId.value.leastSignificantBits).putLong(organizationId.value.mostSignificantBits).putLong(organizationId.value.leastSignificantBits).array()
        val body = BASE64.encodeToString(payload); return "v1.$body.${BASE64.encodeToString(systemicHmac(key, "v1.$body".toByteArray(StandardCharsets.US_ASCII)))}"
    }
    fun decode(value: String, organizationId: OrganizationId): SystemicDivergenceSignalCursor? = try {
        val parts = value.split('.'); if(parts.size != 3 || parts[0] != "v1") return null
        val payload = DECODER.decode(parts[1]); val tag = DECODER.decode(parts[2]); if(!MessageDigest.isEqual(tag, systemicHmac(key, "v1.${parts[1]}".toByteArray(StandardCharsets.US_ASCII)))) return null
        val b = ByteBuffer.wrap(payload); val cursor = SystemicDivergenceSignalCursor(Instant.ofEpochSecond(b.long, b.int.toLong()), SystemicDivergenceSignalId.of(UUID(b.long, b.long))); val org = UUID(b.long, b.long)
        if(org == organizationId.value) cursor else null
    } catch (_: Exception) { null }
    companion object { private val BASE64 = Base64.getUrlEncoder().withoutPadding(); private val DECODER = Base64.getUrlDecoder() }
}

private fun systemicHmac(key: ByteArray, value: ByteArray): ByteArray = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(value) }

private fun signalJson(value: SystemicDivergenceSignal) = buildJsonObject {
    put("signalId", value.signalId.value.toString()); put("stage", value.stage.name); put("currency", value.currency.code); put("policyVersion", value.policyVersion); put("window", value.window.toString()); put("firstSeenAt", value.firstSeenAt.toString()); put("lastSeenAt", value.lastSeenAt.toString()); put("occurrenceCount", value.occurrenceCount); put("absoluteDifference", buildJsonObject { put("currency", value.currency.code); put("amount", value.absoluteDifference.amount.toPlainString()) }); put("status", value.status.name); put("revision", value.revision); put("caseIds", buildJsonArray { value.caseIds.forEach { add(JsonPrimitive(it.value.toString())) } })
}

internal class InvalidSystemicDivergenceCursorException : RuntimeException()
internal class InvalidSystemicDivergenceSignalIdException : RuntimeException()
internal class SystemicDivergenceSignalNotFoundException : RuntimeException()
