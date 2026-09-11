package io.flooow.marketplace.api

import io.flooow.integration.control.CredentialKind
import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.control.IntegrationControlPlaneService
import io.flooow.integration.control.ProviderKey
import io.flooow.integration.provider.mercadolivre.MercadoLivreOAuthCredentialEnvelope
import io.flooow.integration.provider.mercadolivre.MercadoLivreOAuthCredentialEnvelopeCodec
import io.flooow.organization.OrganizationId
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.Base64
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

data class MercadoLivreOAuthBootstrapConfiguration(
    val clientId: String,
    val clientSecret: String,
    val redirectUri: String,
    val pkceEnabled: Boolean = false
) {
    init {
        require(clientId.isNotBlank() && clientSecret.isNotBlank())
        require(URI.create(redirectUri).scheme in setOf("http", "https"))
    }
    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()) =
            MercadoLivreOAuthBootstrapConfiguration(
                requireNotNull(environment["FLOOOW_MERCADO_LIVRE_CLIENT_ID"]) { "FLOOOW_MERCADO_LIVRE_CLIENT_ID is required" },
                requireNotNull(environment["FLOOOW_MERCADO_LIVRE_CLIENT_SECRET"]) { "FLOOOW_MERCADO_LIVRE_CLIENT_SECRET is required" },
                requireNotNull(environment["FLOOOW_MERCADO_LIVRE_REDIRECT_URI"]) { "FLOOOW_MERCADO_LIVRE_REDIRECT_URI is required" },
                environment["FLOOOW_MERCADO_LIVRE_PKCE_ENABLED"]?.toBooleanStrictOrNull() ?: false
            )
    }
}

data class MercadoLivreOAuthStart(val authorizationUrl: String)
data class MercadoLivreOAuthCompletion(val connectionId: IntegrationConnectionId, val authorizedUserId: Long)

interface MercadoLivreOAuthBootstrapTransport {
    fun exchangeCode(form: String, timeout: Duration): ByteArray
    fun fetchUser(accessToken: String, timeout: Duration): ByteArray
}

class MercadoLivreOAuthBootstrap(
    private val controlPlane: IntegrationControlPlaneService,
    private val organizationId: OrganizationId,
    private val configuration: MercadoLivreOAuthBootstrapConfiguration,
    private val transport: MercadoLivreOAuthBootstrapTransport = JdkMercadoLivreOAuthBootstrapTransport(),
    private val clock: Clock = Clock.systemUTC(),
    private val random: SecureRandom = SecureRandom()
) {
    private data class Session(val stateDigest: String, val verifier: String?, val connectionId: IntegrationConnectionId, val expiresAt: Instant)
    private val sessions = ConcurrentHashMap<String, Session>()

    fun start(): MercadoLivreOAuthStart {
        val state = randomValue(32)
        val verifier = if (configuration.pkceEnabled) randomValue(48) else null
        val challenge = verifier?.let(::challenge)
        val connection = controlPlane.createConnection(organizationId, ProviderKey.of("br.com.mercadolivre"), CredentialKind.OAUTH2_AUTHORIZATION_CODE)
        sessions[stateDigest(state)] = Session(stateDigest(state), verifier, connection.id, clock.instant().plus(Duration.ofMinutes(10)))
        val query = linkedMapOf("response_type" to "code", "client_id" to configuration.clientId, "redirect_uri" to configuration.redirectUri, "state" to state)
        if (challenge != null) { query["code_challenge"] = challenge; query["code_challenge_method"] = "S256" }
        return MercadoLivreOAuthStart("https://auth.mercadolivre.com.br/authorization?" + query.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" })
    }

    fun callback(code: String?, state: String?): MercadoLivreOAuthCompletion {
        require(!code.isNullOrBlank() && !state.isNullOrBlank()) { "OAuth callback is incomplete" }
        val session = sessions.remove(stateDigest(state)) ?: error("OAuth state is invalid or already consumed")
        require(clock.instant().isBefore(session.expiresAt)) { "OAuth state expired" }
        val form = linkedMapOf("grant_type" to "authorization_code", "client_id" to configuration.clientId, "client_secret" to configuration.clientSecret, "code" to code, "redirect_uri" to configuration.redirectUri)
        session.verifier?.let { form["code_verifier"] = it }
        val token = parseToken(transport.exchangeCode(form.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }, Duration.ofSeconds(30)))
        val userId = parseUserId(transport.fetchUser(token.accessToken, Duration.ofSeconds(30)))
        require(userId == token.userId) { "Mercado Livre user identity mismatch" }
        val envelope = MercadoLivreOAuthCredentialEnvelope.create(configuration.clientId, configuration.clientSecret, userId, token.accessToken, token.refreshToken, clock.instant().plusSeconds(token.expiresIn))
        val bytes = MercadoLivreOAuthCredentialEnvelopeCodec.encode(envelope)
        try { controlPlane.bindInitialCredential(organizationId, session.connectionId, bytes) } finally { bytes.fill(0) }
        return MercadoLivreOAuthCompletion(session.connectionId, userId)
    }

    private data class Token(val accessToken: String, val refreshToken: String, val userId: Long, val expiresIn: Long)
    private fun parseToken(bytes: ByteArray): Token = try {
        val o = Json.parseToJsonElement(bytes.decodeToString()).jsonObject
        require(o["token_type"]?.jsonPrimitive?.content.equals("Bearer", true))
        val access = o["access_token"]!!.jsonPrimitive.content; val refresh = o["refresh_token"]!!.jsonPrimitive.content
        val user = o["user_id"]!!.jsonPrimitive.longOrNull!!; val expires = o["expires_in"]!!.jsonPrimitive.longOrNull!!
        require(access.isNotBlank() && refresh.isNotBlank() && user > 0 && expires > 0); Token(access, refresh, user, expires)
    } catch (_: Exception) { error("Mercado Livre OAuth token response is invalid") } finally { bytes.fill(0) }
    private fun parseUserId(bytes: ByteArray): Long = try { Json.parseToJsonElement(bytes.decodeToString()).jsonObject["id"]!!.jsonPrimitive.longOrNull?.takeIf { it > 0 } ?: error("invalid id") } catch (_: Exception) { error("Mercado Livre identity response is invalid") } finally { bytes.fill(0) }
    private fun randomValue(size: Int): String { val bytes = ByteArray(size); random.nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).also { bytes.fill(0) } }
    private fun stateDigest(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(StandardCharsets.UTF_8)).let { Base64.getUrlEncoder().withoutPadding().encodeToString(it).also { _ -> it.fill(0) } }
    private fun challenge(verifier: String) = Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)))
    private fun enc(value: String) = URLEncoder.encode(value, StandardCharsets.UTF_8)
}

private class JdkMercadoLivreOAuthBootstrapTransport(private val client: HttpClient = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()) : MercadoLivreOAuthBootstrapTransport {
    override fun exchangeCode(form: String, timeout: Duration): ByteArray = post("https://api.mercadolibre.com/oauth/token", form, timeout)
    override fun fetchUser(accessToken: String, timeout: Duration): ByteArray = client.send(HttpRequest.newBuilder(URI.create("https://api.mercadolibre.com/users/me")).timeout(timeout).header("Authorization", "Bearer $accessToken").header("Accept", "application/json").GET().build(), HttpResponse.BodyHandlers.ofByteArray()).also { require(it.statusCode() in 200..299) { "Mercado Livre identity lookup failed" } }.body()
    private fun post(url: String, form: String, timeout: Duration): ByteArray = client.send(HttpRequest.newBuilder(URI.create(url)).timeout(timeout).header("Content-Type", "application/x-www-form-urlencoded").header("Accept", "application/json").POST(HttpRequest.BodyPublishers.ofString(form)).build(), HttpResponse.BodyHandlers.ofByteArray()).also { require(it.statusCode() in 200..299) { "Mercado Livre OAuth token exchange failed" } }.body()
}
