package io.flooow.marketplace.operations.economics.provider.omie

import io.flooow.integration.connector.ConnectorAdapterFailureKind
import io.flooow.integration.connector.ConnectorBudget
import io.flooow.integration.connector.ConnectorCancellation
import io.flooow.integration.connector.ConnectorProgress
import io.flooow.integration.connector.ConnectorReadResult
import io.flooow.marketplace.operations.economics.provider.MarketplaceEconomicProductCostCapability
import io.flooow.marketplace.operations.economics.provider.OmieProductCostSourceRecord
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OmieEconomicEvidenceConnectorTest {
    private val now = Instant.parse("2026-09-06T14:30:00Z")
    private val clock = Clock.fixed(now, ZoneId.of("America/Sao_Paulo"))
    private val credential =
        """{"schemaVersion":1,"appKey":"test-app-key","appSecret":"test-app-secret"}"""
            .toByteArray()

    @Test
    fun `reads one bounded page and preserves exact provider product location evidence`() {
        val calls = AtomicInteger()
        val transport = OmieHttpTransport { endpoint, body, _, _ ->
            calls.incrementAndGet()
            assertEquals("https", endpoint.scheme)
            val request = body.decodeToString()
            assertTrue(request.contains("\"call\":\"ListarPosEstoque\""))
            assertTrue(request.contains("\"nPagina\":1"))
            assertTrue(request.contains("\"nRegPorPagina\":100"))
            assertTrue(request.contains("\"cExibeTodos\":\"S\""))
            assertTrue(request.contains("\"lista_local_estoque\":\"TODOS\""))
            assertTrue(request.contains("test-app-key"))
            assertTrue(request.contains("test-app-secret"))
            response(
                page = 1,
                totalPages = 2,
                products = """
                    {
                      "nCodProd":3415304571,
                      "cCodInt":"INT-1",
                      "cCodigo":"SKU-1",
                      "cDescricao":"ignored description",
                      "nSaldo":13,
                      "nCMC":21.817094,
                      "codigo_local_estoque":3415174133,
                      "reservado":0,
                      "fisico":13
                    }
                """.trimIndent()
            )
        }

        val result = connector(transport).readPage(
            MarketplaceEconomicProductCostCapability.KEY,
            credential.copyOf(),
            null,
            budget(),
            ConnectorCancellation.NEVER
        )

        val page = assertIs<ConnectorReadResult.Page>(result).value
        assertEquals(1, calls.get())
        assertFalse(page.exhausted)
        assertEquals(now, page.observedAt)
        val record = assertIs<OmieProductCostSourceRecord>(page.records.single())
        assertEquals("3415304571", record.productReference.encodedForPersistence())
        assertEquals("3415174133", record.locationReference.encodedForPersistence())
        assertEquals("21.817094", record.unitCmc!!.canonicalValue())
        assertEquals("13", record.stockBalance!!.canonicalValue())
        assertNull(record.toString().takeIf { it.contains("SKU-1") })
        page.nextProgress!!.use {
            assertEquals("page=2", it.useBytes(ByteArray::decodeToString))
        }
    }

    @Test
    fun `progress selects next numbered page and terminal page has no progress`() {
        val calls = AtomicInteger()
        val transport = OmieHttpTransport { _, body, _, _ ->
            calls.incrementAndGet()
            assertTrue(body.decodeToString().contains("\"nPagina\":2"))
            response(page = 2, totalPages = 2, products = "")
        }
        val progress = ConnectorProgress.take("page=2".toByteArray())

        val result = try {
            connector(transport).readPage(
                MarketplaceEconomicProductCostCapability.KEY,
                credential.copyOf(),
                progress,
                budget(),
                ConnectorCancellation.NEVER
            )
        } finally {
            progress.close()
        }

        val page = assertIs<ConnectorReadResult.Page>(result).value
        assertEquals(1, calls.get())
        assertTrue(page.exhausted)
        assertNull(page.nextProgress)
    }

    @Test
    fun `malformed credential and progress fail before network and reveal no secret`() {
        val calls = AtomicInteger()
        val transport = OmieHttpTransport { _, _, _, _ ->
            calls.incrementAndGet()
            error("network must not run")
        }

        val badCredential = connector(transport).readPage(
            MarketplaceEconomicProductCostCapability.KEY,
            """{"schemaVersion":1,"appKey":"","appSecret":"secret-marker"}""".toByteArray(),
            null,
            budget(),
            ConnectorCancellation.NEVER
        )
        assertFailure(badCredential, ConnectorAdapterFailureKind.AUTHENTICATION_REQUIRED)
        assertFalse(badCredential.toString().contains("secret-marker"))

        val badProgress = ConnectorProgress.take("page=not-a-number".toByteArray())
        val progressOutcome = try {
            connector(transport).readPage(
                MarketplaceEconomicProductCostCapability.KEY,
                credential.copyOf(),
                badProgress,
                budget(),
                ConnectorCancellation.NEVER
            )
        } finally {
            badProgress.close()
        }
        assertFailure(progressOutcome, ConnectorAdapterFailureKind.REMOTE_DATA_INVALID)
        assertEquals(0, calls.get())
    }

    @Test
    fun `http endpoint must remain https`() {
        assertFails {
            OmieEconomicEvidenceConnector(
                URI.create("http://localhost/omie"),
                clock,
                OmieHttpTransport { _, _, _, _ -> error("unused") }
            )
        }
    }

    @Test
    fun `missing cmc remains missing while explicit zero remains observed zero`() {
        val transport = OmieHttpTransport { _, _, _, _ ->
            response(
                page = 1,
                totalPages = 1,
                products = """
                    {
                      "nCodProd":1,
                      "cCodigo":"A",
                      "nSaldo":0,
                      "codigo_local_estoque":10,
                      "reservado":0,
                      "fisico":0
                    },
                    {
                      "nCodProd":2,
                      "cCodigo":"B",
                      "nSaldo":0,
                      "nCMC":0,
                      "codigo_local_estoque":10,
                      "reservado":0,
                      "fisico":0
                    }
                """.trimIndent()
            )
        }

        val page = assertIs<ConnectorReadResult.Page>(
            connector(transport).readPage(
                MarketplaceEconomicProductCostCapability.KEY,
                credential.copyOf(),
                null,
                budget(),
                ConnectorCancellation.NEVER
            )
        ).value

        val first = assertIs<OmieProductCostSourceRecord>(page.records[0])
        val second = assertIs<OmieProductCostSourceRecord>(page.records[1])
        assertNull(first.unitCmc)
        assertEquals("0", second.unitCmc!!.canonicalValue())
    }

    @Test
    fun `malformed decimal fails closed`() {
        val transport = OmieHttpTransport { _, _, _, _ ->
            response(
                page = 1,
                totalPages = 1,
                products = """
                    {
                      "nCodProd":1,
                      "nCMC":1.0000001,
                      "codigo_local_estoque":10
                    }
                """.trimIndent()
            )
        }

        assertFailure(
            connector(transport).readPage(
                MarketplaceEconomicProductCostCapability.KEY,
                credential.copyOf(),
                null,
                budget(),
                ConnectorCancellation.NEVER
            ),
            ConnectorAdapterFailureKind.REMOTE_DATA_INVALID
        )
    }

    @Test
    fun `provider response and record budgets fail closed`() {
        val oversized = OmieHttpTransport { _, _, _, max ->
            OmieHttpResponse(200, ByteArray((max + 1).toInt()) { 'x'.code.toByte() })
        }
        assertFailure(
            connector(oversized).readPage(
                MarketplaceEconomicProductCostCapability.KEY,
                credential.copyOf(),
                null,
                ConnectorBudget(now.plusSeconds(30), 10, 128),
                ConnectorCancellation.NEVER
            ),
            ConnectorAdapterFailureKind.BUDGET_EXCEEDED
        )

        val tooMany = OmieHttpTransport { _, _, _, _ ->
            response(
                page = 1,
                totalPages = 1,
                products = (1..2).joinToString(",") {
                    """{"nCodProd":$it,"nCMC":1,"codigo_local_estoque":10}"""
                }
            )
        }
        assertFailure(
            connector(tooMany).readPage(
                MarketplaceEconomicProductCostCapability.KEY,
                credential.copyOf(),
                null,
                ConnectorBudget(now.plusSeconds(30), 1, 100_000),
                ConnectorCancellation.NEVER
            ),
            ConnectorAdapterFailureKind.BUDGET_EXCEEDED
        )
    }

    @Test
    fun `cancellation is checked before and after provider work`() {
        val beforeCalls = AtomicInteger()
        val before = connector(
            OmieHttpTransport { _, _, _, _ ->
                beforeCalls.incrementAndGet()
                response(1, 1, "")
            }
        ).readPage(
            MarketplaceEconomicProductCostCapability.KEY,
            credential.copyOf(),
            null,
            budget(),
            ConnectorCancellation { true }
        )
        assertFailure(before, ConnectorAdapterFailureKind.CANCELLED)
        assertEquals(0, beforeCalls.get())

        val cancelled = AtomicBoolean(false)
        val after = connector(
            OmieHttpTransport { _, _, _, _ ->
                cancelled.set(true)
                response(1, 1, "")
            }
        ).readPage(
            MarketplaceEconomicProductCostCapability.KEY,
            credential.copyOf(),
            null,
            budget(),
            ConnectorCancellation { cancelled.get() }
        )
        assertFailure(after, ConnectorAdapterFailureKind.CANCELLED)
    }

    @Test
    fun `http failures map to connector taxonomy without body leakage`() {
        val cases = listOf(
            401 to ConnectorAdapterFailureKind.AUTHENTICATION_REQUIRED,
            403 to ConnectorAdapterFailureKind.AUTHORIZATION_DENIED,
            429 to ConnectorAdapterFailureKind.RATE_LIMITED,
            500 to ConnectorAdapterFailureKind.REMOTE_TEMPORARY,
            400 to ConnectorAdapterFailureKind.REMOTE_PERMANENT
        )

        cases.forEach { (status, expected) ->
            val marker = "private-provider-marker-$status"
            val result = connector(
                OmieHttpTransport { _, _, _, _ ->
                    OmieHttpResponse(status, marker.toByteArray(), "2")
                }
            ).readPage(
                MarketplaceEconomicProductCostCapability.KEY,
                credential.copyOf(),
                null,
                budget(),
                ConnectorCancellation.NEVER
            )
            assertFailure(result, expected)
            assertFalse(result.toString().contains(marker))
        }
    }

    @Test
    fun `representative provider page stays bounded at capability page maximum`() {
        val products = (1..100).joinToString(",") {
            """{"nCodProd":$it,"cCodigo":"SKU-$it","nSaldo":1,"nCMC":12.345678,"codigo_local_estoque":10,"reservado":0,"fisico":1}"""
        }
        val result = connector(
            OmieHttpTransport { _, request, _, _ ->
                assertTrue(request.decodeToString().contains("\"nRegPorPagina\":100"))
                response(1, 1, products)
            }
        ).readPage(
            MarketplaceEconomicProductCostCapability.KEY,
            credential.copyOf(),
            null,
            ConnectorBudget(now.plusSeconds(30), 1_000, 1_000_000),
            ConnectorCancellation.NEVER
        )

        val page = assertIs<ConnectorReadResult.Page>(result).value
        assertEquals(100, page.records.size)
        assertTrue(page.exhausted)
    }

    private fun connector(transport: OmieHttpTransport) =
        OmieEconomicEvidenceConnector(
            URI.create("https://example.test/omie"),
            clock,
            transport
        )

    private fun budget() = ConnectorBudget(now.plusSeconds(30), 100, 100_000)

    private fun response(page: Int, totalPages: Int, products: String): OmieHttpResponse {
        val count = if (products.isBlank()) 0 else products.split("},").size
        val body = """
            {
              "nPagina":$page,
              "nTotPaginas":$totalPages,
              "nRegistros":$count,
              "nTotRegistros":$count,
              "dDataPosicao":"06/09/2026",
              "produtos":[
                $products
              ]
            }
        """.trimIndent().toByteArray()
        return OmieHttpResponse(200, body)
    }

    private fun assertFailure(
        result: ConnectorReadResult,
        kind: ConnectorAdapterFailureKind
    ) {
        val failed = assertIs<ConnectorReadResult.Failed>(result)
        assertEquals(kind, failed.failure.kind)
    }
}