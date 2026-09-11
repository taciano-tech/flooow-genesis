package io.flooow.integration.provider.mercadolivre

import io.flooow.integration.credential.ReplacementCredential
import java.time.Instant
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

class MercadoLivreOAuthCredentialEnvelope private constructor(
    val clientId: String,
    internal val clientSecret: String,
    val authorizedUserId: Long,
    internal val accessToken: String,
    internal val refreshToken: String,
    val accessTokenExpiresAt: Instant
) {
    override fun toString(): String = "MercadoLivreOAuthCredentialEnvelope([REDACTED])"

    internal fun replacement(
        newAccessToken: String,
        newRefreshToken: String,
        newAccessTokenExpiresAt: Instant
    ): MercadoLivreOAuthCredentialEnvelope = create(
        clientId = clientId,
        clientSecret = clientSecret,
        authorizedUserId = authorizedUserId,
        accessToken = newAccessToken,
        refreshToken = newRefreshToken,
        accessTokenExpiresAt = newAccessTokenExpiresAt
    )

    companion object {
        internal const val MAX_CLIENT_ID_LENGTH = 256
        internal const val MAX_SECRET_LENGTH = 8_192
        internal const val MAX_TOKEN_LENGTH = 8_192

        fun create(
            clientId: String,
            clientSecret: String,
            authorizedUserId: Long,
            accessToken: String,
            refreshToken: String,
            accessTokenExpiresAt: Instant
        ): MercadoLivreOAuthCredentialEnvelope {
            requireProviderText(clientId, MAX_CLIENT_ID_LENGTH, "client identifier")
            requireProviderText(clientSecret, MAX_SECRET_LENGTH, "client credential")
            requireProviderText(accessToken, MAX_TOKEN_LENGTH, "access credential")
            requireProviderText(refreshToken, MAX_TOKEN_LENGTH, "refresh credential")
            require(authorizedUserId > 0) { "Invalid authorized provider user" }

            return MercadoLivreOAuthCredentialEnvelope(
                clientId,
                clientSecret,
                authorizedUserId,
                accessToken,
                refreshToken,
                accessTokenExpiresAt
            )
        }
    }
}

object MercadoLivreOAuthCredentialEnvelopeCodec {
    private const val SCHEMA_VERSION = 1

    private val exactFields = setOf(
        "schemaVersion",
        "clientId",
        "clientSecret",
        "authorizedUserId",
        "accessToken",
        "refreshToken",
        "accessTokenExpiresAt"
    )

    fun encode(envelope: MercadoLivreOAuthCredentialEnvelope): ByteArray {
        val value = buildJsonObject {
            put("schemaVersion", SCHEMA_VERSION)
            put("clientId", envelope.clientId)
            put("clientSecret", envelope.clientSecret)
            put("authorizedUserId", envelope.authorizedUserId)
            put("accessToken", envelope.accessToken)
            put("refreshToken", envelope.refreshToken)
            put("accessTokenExpiresAt", envelope.accessTokenExpiresAt.toString())
        }

        val bytes = value.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > ReplacementCredential.MAX_BYTES) {
            bytes.fill(0)
            throw IllegalArgumentException("Credential envelope is too large")
        }
        return bytes
    }

    fun <T> withReadAccess(
        bytes: ByteArray,
        operation: (authorizedUserId: Long, accessToken: String) -> T
    ): T? {
        val envelope = decode(bytes) ?: return null
        return operation(envelope.authorizedUserId, envelope.accessToken)
    }
    fun decode(bytes: ByteArray): MercadoLivreOAuthCredentialEnvelope? {
        if (bytes.isEmpty() || bytes.size > ReplacementCredential.MAX_BYTES) return null

        return try {
            val root = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
            if (root.keys != exactFields) return null

            val version = root.requiredNumber("schemaVersion").intOrNull ?: return null
            if (version != SCHEMA_VERSION) return null

            val clientId = root.requiredString("clientId")
            val clientSecret = root.requiredString("clientSecret")
            val userId = root.requiredNumber("authorizedUserId").longOrNull ?: return null
            val accessToken = root.requiredString("accessToken")
            val refreshToken = root.requiredString("refreshToken")
            val expiresAt = Instant.parse(root.requiredString("accessTokenExpiresAt"))

            MercadoLivreOAuthCredentialEnvelope.create(
                clientId,
                clientSecret,
                userId,
                accessToken,
                refreshToken,
                expiresAt
            )
        } catch (_: Exception) {
            null
        }
    }
}

private fun JsonObject.requiredString(name: String): String {
    val primitive = this[name]?.jsonPrimitive ?: error("Missing credential field")
    require(primitive.isString) { "Invalid credential field" }
    return primitive.content
}

private fun JsonObject.requiredNumber(name: String) =
    (this[name]?.jsonPrimitive ?: error("Missing credential field")).also {
        require(!it.isString) { "Invalid credential field" }
    }

private fun requireProviderText(value: String, maxLength: Int, label: String) {
    require(value.length in 1..maxLength) { "Invalid $label" }
    require(value.none { it.isISOControl() }) { "Invalid $label" }
    require(value.isNotBlank()) { "Invalid $label" }
}