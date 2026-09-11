package io.flooow.integration.provider.mercadolivre

import io.flooow.integration.control.CredentialKind
import io.flooow.integration.control.ProviderKey
import io.flooow.integration.credential.CredentialRefreshResult
import io.flooow.integration.credential.CredentialRotationAssessment
import io.flooow.integration.credential.CredentialRotationCancellation
import io.flooow.integration.credential.CredentialRotationRemoteContext
import io.flooow.integration.credential.CredentialRotationRemoteFailureKind
import io.flooow.integration.credential.CredentialRotator
import io.flooow.integration.credential.CredentialRotatorDescriptor
import io.flooow.integration.credential.ReplacementCredential
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class MercadoLivreTokenHttpResponse(
    val statusCode: Int,
    val body: ByteArray,
    val retryAfter: String? = null
) {
    override fun toString(): String =
        "MercadoLivreTokenHttpResponse(statusCode=$statusCode, body=[REDACTED], retryAfter=$retryAfter)"
}

fun interface MercadoLivreTokenHttpTransport {
    fun post(
        endpoint: URI,
        body: ByteArray,
        timeout: Duration,
        maxResponseBytes: Int
    ): MercadoLivreTokenHttpResponse
}

class JdkMercadoLivreTokenHttpTransport(
    private val client: HttpClient = HttpClient.newBuilder()
        .followRedirects(REDIRECT_POLICY)
        .build()
) : MercadoLivreTokenHttpTransport {
    override fun post(
        endpoint: URI,
        body: ByteArray,
        timeout: Duration,
        maxResponseBytes: Int
    ): MercadoLivreTokenHttpResponse {
        require(!timeout.isNegative && !timeout.isZero) { "Invalid token request timeout" }

        val request = HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("Accept", "application/json")
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
        val responseBytes = response.body().use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4_096)
            try {
                var total = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > maxResponseBytes) {
                        throw MercadoLivreTokenResponseTooLargeException()
                    }
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            } finally {
                buffer.fill(0)
            }
        }

        return MercadoLivreTokenHttpResponse(
            statusCode = response.statusCode(),
            body = responseBytes,
            retryAfter = response.headers().firstValue("Retry-After").orElse(null)
        )
    }

    companion object {
        internal val REDIRECT_POLICY: HttpClient.Redirect = HttpClient.Redirect.NEVER
    }
}

class MercadoLivreOAuthCredentialRotator(
    private val endpoint: URI = DEFAULT_ENDPOINT,
    private val clock: Clock = Clock.systemUTC(),
    private val transport: MercadoLivreTokenHttpTransport =
        JdkMercadoLivreTokenHttpTransport()
) : CredentialRotator {

    override val descriptor = CredentialRotatorDescriptor(
        ProviderKey.of("br.com.mercadolivre"),
        CredentialKind.OAUTH2_AUTHORIZATION_CODE
    )

    init {
        require(endpoint.scheme.equals("https", ignoreCase = true)) {
            "Mercado Livre token endpoint must use HTTPS"
        }
        require(endpoint.host != null) { "Mercado Livre token endpoint must be absolute" }
    }

    override fun assess(
        credentialBytes: ByteArray,
        now: java.time.Instant
    ): CredentialRotationAssessment {
        val envelope = MercadoLivreOAuthCredentialEnvelopeCodec.decode(credentialBytes)
            ?: return CredentialRotationAssessment.AUTHENTICATION_REQUIRED

        return if (now.isBefore(envelope.accessTokenExpiresAt)) {
            CredentialRotationAssessment.USABLE
        } else {
            CredentialRotationAssessment.REFRESH_REQUIRED
        }
    }

    override fun refresh(
        credentialBytes: ByteArray,
        context: CredentialRotationRemoteContext,
        cancellation: CredentialRotationCancellation
    ): CredentialRefreshResult {
        val envelope = MercadoLivreOAuthCredentialEnvelopeCodec.decode(credentialBytes)
            ?: return terminal(CredentialRotationRemoteFailureKind.AUTHENTICATION_REQUIRED)

        val now = clock.instant()
        val remaining = Duration.between(now, context.deadline)
        if (remaining.isNegative || remaining.isZero) {
            return CredentialRefreshResult.RetryableFailure.of(
                CredentialRotationRemoteFailureKind.REMOTE_TEMPORARY,
                LOCAL_SAFE_RETRY_DELAY
            )
        }

        val timeout = minOf(remaining, MAX_HTTP_TIMEOUT)
        val requestBytes = refreshForm(envelope)

        val response = try {
            transport.post(endpoint, requestBytes, timeout, MAX_RESPONSE_BYTES)
        } catch (_: HttpTimeoutException) {
            return CredentialRefreshResult.Indeterminate
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
            return CredentialRefreshResult.Indeterminate
        } catch (_: IOException) {
            return CredentialRefreshResult.Indeterminate
        } catch (_: Exception) {
            return CredentialRefreshResult.Indeterminate
        } finally {
            requestBytes.fill(0)
        }

        try {
            if (response.body.size > MAX_RESPONSE_BYTES) {
                return CredentialRefreshResult.Indeterminate
            }
            return mapResponse(response, envelope)
        } finally {
            response.body.fill(0)
        }
    }

    private fun mapResponse(
        response: MercadoLivreTokenHttpResponse,
        envelope: MercadoLivreOAuthCredentialEnvelope
    ): CredentialRefreshResult = when (response.statusCode) {
        in 200..299 -> parseReplacement(response.body, envelope)

        400 -> {
            if (parseErrorCode(response.body) == "invalid_grant") {
                terminal(CredentialRotationRemoteFailureKind.AUTHENTICATION_REQUIRED)
            } else {
                terminal(CredentialRotationRemoteFailureKind.REMOTE_PERMANENT)
            }
        }

        401 -> terminal(CredentialRotationRemoteFailureKind.AUTHENTICATION_REQUIRED)
        403 -> terminal(CredentialRotationRemoteFailureKind.AUTHORIZATION_DENIED)

        429 -> CredentialRefreshResult.RetryableFailure.of(
            CredentialRotationRemoteFailureKind.RATE_LIMITED,
            parseRetryAfter(response.retryAfter)
        )

        408, 425 -> CredentialRefreshResult.Indeterminate
        in 500..599 -> CredentialRefreshResult.Indeterminate
        in 400..499 -> terminal(CredentialRotationRemoteFailureKind.REMOTE_PERMANENT)
        else -> CredentialRefreshResult.Indeterminate
    }

    private fun parseReplacement(
        body: ByteArray,
        current: MercadoLivreOAuthCredentialEnvelope
    ): CredentialRefreshResult = try {
        val root = Json.parseToJsonElement(body.decodeToString()).jsonObject
        val accessToken = root.requiredString("access_token")
        val tokenType = root.requiredString("token_type")
        val refreshToken = root.requiredString("refresh_token")
        val expiresIn = root.requiredPositiveLong("expires_in")
        val userId = root.requiredPositiveLong("user_id")

        require(tokenType.equals("bearer", ignoreCase = true)) {
            "Invalid provider token type"
        }
        require(expiresIn <= MAX_EXPIRES_IN_SECONDS) {
            "Invalid provider token lifetime"
        }
        require(userId == current.authorizedUserId) {
            "Provider user changed during credential refresh"
        }

        val replacement = current.replacement(
            newAccessToken = accessToken,
            newRefreshToken = refreshToken,
            newAccessTokenExpiresAt = clock.instant().plusSeconds(expiresIn)
        )

        val encoded = MercadoLivreOAuthCredentialEnvelopeCodec.encode(replacement)
        CredentialRefreshResult.Replacement(ReplacementCredential.take(encoded))
    } catch (_: Exception) {
        CredentialRefreshResult.Indeterminate
    }

    private fun refreshForm(
        envelope: MercadoLivreOAuthCredentialEnvelope
    ): ByteArray {
        val fields = listOf(
            "grant_type" to "refresh_token",
            "client_id" to envelope.clientId,
            "client_secret" to envelope.clientSecret,
            "refresh_token" to envelope.refreshToken
        )

        return fields.joinToString("&") { (name, value) ->
            "${encodeForm(name)}=${encodeForm(value)}"
        }.toByteArray(StandardCharsets.UTF_8)
    }

    private fun parseErrorCode(body: ByteArray): String? {
        return try {
            val root = Json.parseToJsonElement(body.decodeToString()).jsonObject
            val element = root["error"] ?: return null
            val primitive = element.jsonPrimitive
            if (!primitive.isString) null else primitive.content
        } catch (_: Exception) {
            null
        }
    }

    private fun terminal(
        kind: CredentialRotationRemoteFailureKind
    ) = CredentialRefreshResult.TerminalFailure.of(kind)

    companion object {
        val DEFAULT_ENDPOINT: URI =
            URI.create("https://api.mercadolibre.com/oauth/token")

        internal const val MAX_RESPONSE_BYTES = 32 * 1024
        internal const val MAX_EXPIRES_IN_SECONDS = 7L * 24L * 60L * 60L
        internal val MAX_HTTP_TIMEOUT: Duration = Duration.ofSeconds(30)
        internal val LOCAL_SAFE_RETRY_DELAY: Duration = Duration.ofSeconds(1)
        internal val DEFAULT_RATE_LIMIT_RETRY: Duration = Duration.ofSeconds(30)
    }
}

private class MercadoLivreTokenResponseTooLargeException : IOException()

private fun encodeForm(value: String): String =
    URLEncoder.encode(value, StandardCharsets.UTF_8)

private fun parseRetryAfter(value: String?): Duration {
    val seconds = value
        ?.trim()
        ?.toLongOrNull()
        ?.coerceAtLeast(1)
        ?: MercadoLivreOAuthCredentialRotator.DEFAULT_RATE_LIMIT_RETRY.seconds

    return Duration.ofSeconds(seconds)
}

private fun JsonObject.requiredString(name: String): String {
    val primitive = this[name]?.jsonPrimitive ?: error("Missing provider token field")
    require(primitive.isString) { "Invalid provider token field" }
    return primitive.content.takeIf { it.isNotBlank() }
        ?: error("Invalid provider token field")
}

private fun JsonObject.requiredPositiveLong(name: String): Long {
    val primitive = this[name]?.jsonPrimitive ?: error("Missing provider token field")
    require(!primitive.isString) { "Invalid provider token field" }
    return primitive.longOrNull?.takeIf { it > 0 }
        ?: error("Invalid provider token field")
}