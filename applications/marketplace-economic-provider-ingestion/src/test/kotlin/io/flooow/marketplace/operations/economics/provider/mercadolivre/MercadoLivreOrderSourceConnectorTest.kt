package io.flooow.marketplace.operations.economics.provider.mercadolivre

import io.flooow.integration.connector.ConnectorAdapterFailureKind
import io.flooow.integration.connector.ConnectorBudget
import io.flooow.integration.connector.ConnectorCancellation
import io.flooow.integration.connector.ConnectorCapability
import io.flooow.integration.connector.ConnectorProgress
import io.flooow.integration.connector.ConnectorReadResult
import io.flooow.integration.provider.mercadolivre.MercadoLivreOAuthCredentialEnvelope
import io.flooow.integration.provider.mercadolivre.MercadoLivreOAuthCredentialEnvelopeCodec
import io.flooow.marketplace.operations.economics.provider.MarketplaceEconomicOrderSourceCapability
import io.flooow.marketplace.operations.economics.provider.MercadoLivreOrderSourceRecord
import java.io.IOException
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MercadoLivreOrderSourceConnectorTest {
    private val now = Instant.parse("2026-09-06T20:15:30Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `descriptor and capability are exact`() {
        val connector = connector(RecordingTransport { validPage() })
        assertEquals("br.com.mercadolivre", connector.descriptor.providerKey.value)
        assertEquals(2, connector.descriptor.definitions.size)
        assertTrue(connector.descriptor.definitions.map { it.capability }.containsAll(
            listOf(
                MarketplaceEconomicOrderSourceCapability.KEY,
                MarketplaceEconomicOrderSourceCapability.REACQUISITION_KEY
            )
        ))
        assertTrue(connector.descriptor.definitions.all { it.recordType == MercadoLivreOrderSourceRecord::class })
    }

    @Test
    fun `first read uses previous fully closed UTC hour and one seller GET`() {
        val transport = RecordingTransport { validPage() }
        val connector = connector(transport)
        val credential = credential()

        val result = try {
            connector.readPage(
                MarketplaceEconomicOrderSourceCapability.KEY,
                credential,
                null,
                budget(maxRecords = 50),
                ConnectorCancellation.NEVER
            )
        } finally {
            credential.fill(0)
        }

        val page = assertIs<ConnectorReadResult.Page>(result).value
        assertFalse(page.exhausted)
        assertEquals(1, page.records.size)
        assertEquals(1, transport.calls.get())
        assertEquals("synthetic-access-token", transport.lastToken)

        val uri = requireNotNull(transport.lastUri).toString()
        assertTrue(uri.contains("seller=8035443"))
        assertTrue(uri.contains("order.date_last_updated.from=2026-09-06T19%3A00%3A00.000Z"))
        assertTrue(uri.contains("order.date_last_updated.to=2026-09-06T20%3A00%3A00.000Z"))
        assertTrue(uri.contains("offset=0"))
        assertTrue(uri.contains("limit=50"))

        page.nextProgress!!.use {
            assertEquals(
                "v1|hour=2026-09-06T20:00:00Z|offset=0",
                it.useBytes(ByteArray::decodeToString)
            )
        }

        val record = assertIs<MercadoLivreOrderSourceRecord>(page.records.single())
        assertEquals("2000009713473608", record.externalOrderReference.encodedForPersistence())
        assertEquals("paid", record.providerStatus.encodedForPersistence())
        assertEquals("BRL", record.currency.encodedForPersistence())
        assertEquals("125.92", record.totalAmount.canonicalValue())
        assertEquals("125.92", record.paidAmount?.canonicalValue())
        assertEquals("MLB333", record.orderItems.single().itemReference.encodedForPersistence())
        assertEquals("SKU-MLB333", record.orderItems.single().sellerSku?.encodedForPersistence())
        assertEquals("11.07", record.orderItems.single().saleFee?.canonicalValue())
        assertEquals("91776699099", record.payments.single().paymentReference.encodedForPersistence())
    }

    @Test
    fun `reacquisition first read starts forty eight hours behind current UTC hour`() {
        val transport = RecordingTransport { validPage() }
        val connector = connector(transport)
        val credential = credential()

        val result = try {
            connector.readPage(
                MarketplaceEconomicOrderSourceCapability.REACQUISITION_KEY,
                credential,
                null,
                budget(maxRecords = 50),
                ConnectorCancellation.NEVER
            )
        } finally {
            credential.fill(0)
        }

        val page = assertIs<ConnectorReadResult.Page>(result).value
        assertEquals(1, transport.calls.get())

        val uri = requireNotNull(transport.lastUri).toString()
        assertTrue(uri.contains(
            "order.date_last_updated.from=2026-09-04T20%3A00%3A00.000Z"
        ))
        assertTrue(uri.contains(
            "order.date_last_updated.to=2026-09-04T21%3A00%3A00.000Z"
        ))

        page.nextProgress!!.use {
            assertEquals(
                "v1|hour=2026-09-04T21:00:00Z|offset=0",
                it.useBytes(ByteArray::decodeToString)
            )
        }
    }

    @Test
    fun `paging stays in hour until total consumed`() {
        val transport = RecordingTransport {
            validPage(total = 3, offset = 0, limit = 2, includeTwo = true)
        }
        val connector = connector(transport)
        val credential = credential()

        val result = try {
            connector.readPage(
                MarketplaceEconomicOrderSourceCapability.KEY,
                credential,
                null,
                budget(maxRecords = 2),
                ConnectorCancellation.NEVER
            )
        } finally {
            credential.fill(0)
        }

        val page = assertIs<ConnectorReadResult.Page>(result).value
        assertFalse(page.exhausted)
        assertEquals(2, page.records.size)
        page.nextProgress!!.use {
            assertEquals(
                "v1|hour=2026-09-06T19:00:00Z|offset=2",
                it.useBytes(ByteArray::decodeToString)
            )
        }
    }

    @Test
    fun `caught up current hour makes zero HTTP calls and stays nonterminal`() {
        val transport = RecordingTransport { validPage() }
        val connector = connector(transport)
        val progress = ConnectorProgress.take(
            "v1|hour=2026-09-06T20:00:00Z|offset=0".toByteArray()
        )
        val credential = credential()

        val result = try {
            progress.use {
                connector.readPage(
                    MarketplaceEconomicOrderSourceCapability.KEY,
                    credential,
                    it,
                    budget(),
                    ConnectorCancellation.NEVER
                )
            }
        } finally {
            credential.fill(0)
        }

        val failed = assertIs<ConnectorReadResult.Failed>(result)
        assertEquals(ConnectorAdapterFailureKind.REMOTE_TEMPORARY, failed.failure.kind)
        assertEquals(Duration.ofMinutes(44).plusSeconds(30), failed.failure.retryAfter)
        assertEquals(0, transport.calls.get())
    }

    @Test
    fun `cancelled invalid capability and malformed progress fail before HTTP`() {
        val transport = RecordingTransport { validPage() }
        val connector = connector(transport)
        val credential = credential()

        try {
            assertEquals(
                ConnectorAdapterFailureKind.CANCELLED,
                failure(
                    connector.readPage(
                        MarketplaceEconomicOrderSourceCapability.KEY,
                        credential,
                        null,
                        budget(),
                        ConnectorCancellation { true }
                    )
                )
            )

            assertEquals(
                ConnectorAdapterFailureKind.REMOTE_PERMANENT,
                failure(
                    connector.readPage(
                        ConnectorCapability.of("other"),
                        credential,
                        null,
                        budget(),
                        ConnectorCancellation.NEVER
                    )
                )
            )

            val malformed = ConnectorProgress.take("page=2".toByteArray())
            malformed.use {
                assertEquals(
                    ConnectorAdapterFailureKind.REMOTE_DATA_INVALID,
                    failure(
                        connector.readPage(
                            MarketplaceEconomicOrderSourceCapability.KEY,
                            credential,
                            it,
                            budget(),
                            ConnectorCancellation.NEVER
                        )
                    )
                )
            }

            assertEquals(0, transport.calls.get())
        } finally {
            credential.fill(0)
        }
    }

    @Test
    fun `malformed credential fails without request`() {
        val transport = RecordingTransport { validPage() }
        val malformed = """{"schemaVersion":1}""".toByteArray()
        try {
            val result = connector(transport).readPage(
                MarketplaceEconomicOrderSourceCapability.KEY,
                malformed,
                null,
                budget(),
                ConnectorCancellation.NEVER
            )
            assertEquals(
                ConnectorAdapterFailureKind.AUTHENTICATION_REQUIRED,
                failure(result)
            )
            assertEquals(0, transport.calls.get())
        } finally {
            malformed.fill(0)
        }
    }

    @Test
    fun `provider failure mapping is typed and never retries internally`() {
        val cases = listOf(
            401 to ConnectorAdapterFailureKind.AUTHENTICATION_REQUIRED,
            403 to ConnectorAdapterFailureKind.AUTHORIZATION_DENIED,
            429 to ConnectorAdapterFailureKind.RATE_LIMITED,
            503 to ConnectorAdapterFailureKind.REMOTE_TEMPORARY,
            400 to ConnectorAdapterFailureKind.REMOTE_PERMANENT
        )

        cases.forEach { (status, expected) ->
            val transport = RecordingTransport {
                MercadoLivreOrderHttpResponse(
                    status,
                    "{}".toByteArray(),
                    retryAfter = if (status == 429) "17" else null
                )
            }
            val credential = credential()
            val result = try {
                connector(transport).readPage(
                    MarketplaceEconomicOrderSourceCapability.KEY,
                    credential,
                    null,
                    budget(),
                    ConnectorCancellation.NEVER
                )
            } finally {
                credential.fill(0)
            }

            assertEquals(expected, failure(result))
            assertEquals(1, transport.calls.get())
            if (status == 429) {
                val failed = assertIs<ConnectorReadResult.Failed>(result)
                assertEquals(Duration.ofSeconds(17), failed.failure.retryAfter)
            }
        }
    }

    @Test
    fun `206 accepts only documented non modeled missing content`() {
        val acceptedTransport = RecordingTransport {
            validPage(
                status = 206,
                contentMissing = "buyer, feedback"
            )
        }
        val acceptedCredential = credential()
        val accepted = try {
            connector(acceptedTransport).readPage(
                MarketplaceEconomicOrderSourceCapability.KEY,
                acceptedCredential,
                null,
                budget(),
                ConnectorCancellation.NEVER
            )
        } finally {
            acceptedCredential.fill(0)
        }
        assertIs<ConnectorReadResult.Page>(accepted)

        val rejectedTransport = RecordingTransport {
            validPage(
                status = 206,
                contentMissing = "order_items"
            )
        }
        val rejectedCredential = credential()
        val rejected = try {
            connector(rejectedTransport).readPage(
                MarketplaceEconomicOrderSourceCapability.KEY,
                rejectedCredential,
                null,
                budget(),
                ConnectorCancellation.NEVER
            )
        } finally {
            rejectedCredential.fill(0)
        }
        assertEquals(
            ConnectorAdapterFailureKind.REMOTE_DATA_INVALID,
            failure(rejected)
        )
    }

    @Test
    fun `malformed paging and response budget fail closed`() {
        val badPaging = RecordingTransport {
            validPage(offset = 1)
        }
        val credentialA = credential()
        val resultA = try {
            connector(badPaging).readPage(
                MarketplaceEconomicOrderSourceCapability.KEY,
                credentialA,
                null,
                budget(),
                ConnectorCancellation.NEVER
            )
        } finally {
            credentialA.fill(0)
        }
        assertEquals(
            ConnectorAdapterFailureKind.REMOTE_DATA_INVALID,
            failure(resultA)
        )

        val oversized = RecordingTransport {
            MercadoLivreOrderHttpResponse(
                200,
                ByteArray(1_001) { 'x'.code.toByte() }
            )
        }
        val credentialB = credential()
        val resultB = try {
            connector(oversized).readPage(
                MarketplaceEconomicOrderSourceCapability.KEY,
                credentialB,
                null,
                ConnectorBudget(now.plusSeconds(30), 50, 1_000),
                ConnectorCancellation.NEVER
            )
        } finally {
            credentialB.fill(0)
        }
        assertEquals(
            ConnectorAdapterFailureKind.BUDGET_EXCEEDED,
            failure(resultB)
        )
    }

    @Test
    fun `IO uncertainty is remote temporary and response rendering redacts body`() {
        val io = object : MercadoLivreOrderHttpTransport {
            override fun get(
                endpoint: URI,
                accessToken: String,
                timeout: Duration,
                maxResponseBytes: Long
            ): MercadoLivreOrderHttpResponse {
                throw IOException("synthetic I/O")
            }
        }
        val credential = credential()
        val result = try {
            connector(io).readPage(
                MarketplaceEconomicOrderSourceCapability.KEY,
                credential,
                null,
                budget(),
                ConnectorCancellation.NEVER
            )
        } finally {
            credential.fill(0)
        }
        assertEquals(
            ConnectorAdapterFailureKind.REMOTE_TEMPORARY,
            failure(result)
        )

        val rendered = MercadoLivreOrderHttpResponse(
            200,
            "secret-body-marker".toByteArray()
        ).toString()
        assertFalse(rendered.contains("secret-body-marker"))
    }

    private fun connector(transport: MercadoLivreOrderHttpTransport) =
        MercadoLivreOrderSourceConnector(
            URI.create("https://example.test/orders/search"),
            clock,
            transport
        )

    private fun budget(maxRecords: Int = 50) =
        ConnectorBudget(now.plusSeconds(30), maxRecords, 100_000)

    private fun credential(): ByteArray =
        MercadoLivreOAuthCredentialEnvelopeCodec.encode(
            MercadoLivreOAuthCredentialEnvelope.create(
                clientId = "client-123",
                clientSecret = "synthetic-client-secret",
                authorizedUserId = 8035443L,
                accessToken = "synthetic-access-token",
                refreshToken = "synthetic-refresh-token",
                accessTokenExpiresAt = now.plusSeconds(3_600)
            )
        )

    private fun failure(result: ConnectorReadResult): ConnectorAdapterFailureKind =
        assertIs<ConnectorReadResult.Failed>(result).failure.kind

    private fun validPage(
        status: Int = 200,
        contentMissing: String? = null,
        total: Int = 1,
        offset: Int = 0,
        limit: Int = 50,
        includeTwo: Boolean = false
    ): MercadoLivreOrderHttpResponse {
        val first = orderJson(
            "2000009713473608",
            "MLB333",
            "91776699099"
        )
        val second = orderJson(
            "2000009713473609",
            "MLB334",
            "91776699100"
        )
        val results = if (includeTwo) "$first,$second" else first

        return MercadoLivreOrderHttpResponse(
            status,
            """
                {
                  "paging":{"total":$total,"offset":$offset,"limit":$limit},
                  "results":[$results]
                }
            """.trimIndent().toByteArray(),
            contentMissing = contentMissing
        )
    }

    private fun orderJson(order: String, item: String, payment: String) =
        """
        {
          "id":$order,
          "status":"paid",
          "date_created":"2026-09-06T15:05:00.000-04:00",
          "date_closed":"2026-09-06T15:06:00.000-04:00",
          "date_last_updated":"2026-09-06T19:07:08.123Z",
          "currency_id":"BRL",
          "total_amount":125.92,
          "paid_amount":125.92,
          "pack_id":2000006556183755,
          "shipping":{"id":46803546483},
          "order_items":[{
            "item":{"id":"$item","seller_sku":"SKU-$item","variation_id":null,"title":"PII-like ignored title"},
            "quantity":1,
            "unit_price":62.96,
            "full_unit_price":72.37,
            "currency_id":"BRL",
            "sale_fee":11.07
          }],
          "payments":[{
            "id":$payment,
            "status":"approved",
            "transaction_amount":125.92,
            "currency_id":"BRL",
            "date_created":"2026-09-06T19:05:30.000Z",
            "date_last_modified":"2026-09-06T19:06:30.000Z",
            "reason":"ignored PII-like reason"
          }],
          "buyer":{"email":"ignored@example.test","phone":"ignored"}
        }
        """.trimIndent()
}

private class RecordingTransport(
    private val response: () -> MercadoLivreOrderHttpResponse
) : MercadoLivreOrderHttpTransport {
    val calls = AtomicInteger()
    var lastUri: URI? = null
    var lastToken: String? = null

    override fun get(
        endpoint: URI,
        accessToken: String,
        timeout: Duration,
        maxResponseBytes: Long
    ): MercadoLivreOrderHttpResponse {
        calls.incrementAndGet()
        lastUri = endpoint
        lastToken = accessToken
        return response()
    }
}
