package io.flooow.integration.provider.mercadolivre

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class MercadoLivreOAuthCredentialEnvelopeTest {
    private val expiry = Instant.parse("2026-09-07T00:00:00Z")

    @Test
    fun `envelope v1 round trips exact synthetic values`() {
        val envelope = sample()
        val bytes = MercadoLivreOAuthCredentialEnvelopeCodec.encode(envelope)
        try {
            val decoded = assertNotNull(
                MercadoLivreOAuthCredentialEnvelopeCodec.decode(bytes)
            )
            assertEquals("client-123", decoded.clientId)
            assertEquals("synthetic-client-secret", decoded.clientSecret)
            assertEquals(8035443L, decoded.authorizedUserId)
            assertEquals("synthetic-access-token", decoded.accessToken)
            assertEquals("synthetic-refresh-token", decoded.refreshToken)
            assertEquals(expiry, decoded.accessTokenExpiresAt)
        } finally {
            bytes.fill(0)
        }
    }

    @Test
    fun `unknown and missing fields fail closed`() {
        val unknown = """
            {
              "schemaVersion":1,
              "clientId":"client-123",
              "clientSecret":"secret",
              "authorizedUserId":1,
              "accessToken":"access",
              "refreshToken":"refresh",
              "accessTokenExpiresAt":"2026-09-07T00:00:00Z",
              "unexpected":"value"
            }
        """.trimIndent().toByteArray()

        val missing = """
            {
              "schemaVersion":1,
              "clientId":"client-123",
              "clientSecret":"secret",
              "authorizedUserId":1,
              "accessToken":"access",
              "accessTokenExpiresAt":"2026-09-07T00:00:00Z"
            }
        """.trimIndent().toByteArray()

        try {
            assertNull(MercadoLivreOAuthCredentialEnvelopeCodec.decode(unknown))
            assertNull(MercadoLivreOAuthCredentialEnvelopeCodec.decode(missing))
        } finally {
            unknown.fill(0)
            missing.fill(0)
        }
    }

    @Test
    fun `oversized encoded envelope is rejected`() {
        val envelope = MercadoLivreOAuthCredentialEnvelope.create(
            clientId = "client-123",
            clientSecret = "s".repeat(8_192),
            authorizedUserId = 1,
            accessToken = "a".repeat(8_192),
            refreshToken = "r".repeat(8_192),
            accessTokenExpiresAt = expiry
        )

        assertFailsWith<IllegalArgumentException> {
            MercadoLivreOAuthCredentialEnvelopeCodec.encode(envelope)
        }
    }

    @Test
    fun `scoped read access exposes only seller id and current access token`() {
        val bytes = MercadoLivreOAuthCredentialEnvelopeCodec.encode(sample())
        try {
            val result = MercadoLivreOAuthCredentialEnvelopeCodec.withReadAccess(bytes) {
                    authorizedUserId,
                    accessToken ->
                assertEquals(8035443L, authorizedUserId)
                assertEquals("synthetic-access-token", accessToken)
                "scoped-result"
            }
            assertEquals("scoped-result", result)
        } finally {
            bytes.fill(0)
        }
    }

    @Test
    fun `scoped read access rejects malformed envelope without callback`() {
        val malformed = """{"schemaVersion":1}""".toByteArray()
        var called = false
        try {
            val result = MercadoLivreOAuthCredentialEnvelopeCodec.withReadAccess(malformed) { _, _ ->
                called = true
                Unit
            }
            assertNull(result)
            assertFalse(called)
        } finally {
            malformed.fill(0)
        }
    }
    @Test
    fun `rendering never exposes credential markers`() {
        val rendered = sample().toString()
        listOf(
            "synthetic-client-secret",
            "synthetic-access-token",
            "synthetic-refresh-token"
        ).forEach {
            assertFalse(rendered.contains(it))
        }
    }

    private fun sample() = MercadoLivreOAuthCredentialEnvelope.create(
        clientId = "client-123",
        clientSecret = "synthetic-client-secret",
        authorizedUserId = 8035443L,
        accessToken = "synthetic-access-token",
        refreshToken = "synthetic-refresh-token",
        accessTokenExpiresAt = expiry
    )
}