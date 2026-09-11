package io.flooow.marketplace.operations.economics.provider.omie

import io.flooow.integration.connector.ConnectorBudget
import io.flooow.integration.connector.ConnectorCancellation
import io.flooow.integration.connector.ConnectorReadResult
import io.flooow.marketplace.operations.economics.provider.OmieTransactionEvidenceCapability
import io.flooow.marketplace.operations.economics.provider.OmieTransactionEvidenceRecord
import io.flooow.marketplace.operations.economics.provider.OmieTransactionProductIdentifierKind
import java.net.URI
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OmieTransactionEvidenceConnectorTest {
    private val now = Instant.parse("2026-09-10T12:00:00Z")
    private val credential = """{"schemaVersion":1,"appKey":"key","appSecret":"secret"}""".toByteArray()

    @Test
    fun `typed parser uses a fresh reacquisition generation while retaining historical readability`() {
        val connector = connector {
            OmieHttpResponse(200, """{"nPagina":1,"nTotPaginas":1,"pedido_venda_produto":[]}""".toByteArray())
        }

        assertEquals(
            "marketplace-economic.omie-transaction-evidence.reacquisition-v1",
            OmieTransactionEvidenceCapability.REACQUISITION_V1_KEY.value
        )
        assertEquals(
            "marketplace-economic.omie-transaction-evidence.reacquisition-v2",
            OmieTransactionEvidenceCapability.REACQUISITION_KEY.value
        )
        assertTrue(connector.descriptor.definitions.any {
            it.capability == OmieTransactionEvidenceCapability.REACQUISITION_V1_KEY
        })
        assertIs<ConnectorReadResult.Page>(
            connector.readPage(
                OmieTransactionEvidenceCapability.REACQUISITION_KEY,
                credential.copyOf(), null,
                ConnectorBudget(now.plusSeconds(30), 100, 100_000),
                ConnectorCancellation.NEVER
            )
        )
    }

    @Test
    fun `parses documented pedido venda shape including customer reference products and amount`() {
        val connector = connector {
            OmieHttpResponse(200, """
                {"nPagina":1,"nTotPaginas":1,"pedido_venda_produto":[{
                  "cabecalho":{"codigo_pedido":123,"codigo_pedido_integracao":"ERP-1","data_previsao":"10/09/2026"},
                  "informacoes_adicionais":{"numero_pedido_cliente":"ML-123"},
                  "det":[{"produto":{"codigo_produto_integracao":"SKU-1","quantidade":2}}],
                  "total_pedido":{"valor_total_pedido":95.02}
                }]}
            """.trimIndent().toByteArray())
        }
        val page = assertIs<ConnectorReadResult.Page>(connector.read()).value
        val record = assertIs<OmieTransactionEvidenceRecord>(page.records.single())
        assertEquals("ERP-1", record.integrationOrderReference!!.encodedForPersistence())
        assertEquals("ML-123", record.customerOrderReference!!.encodedForPersistence())
        assertEquals("SKU-1", record.products.single().identifier.encodedForPersistence())
        assertEquals(OmieTransactionProductIdentifierKind.INTEGRATION_PRODUCT_CODE, record.products.single().identifier.kind)
        assertEquals("2", record.products.single().quantity.canonicalValue())
        assertEquals("95.02", record.totalAmount!!.canonicalValue())
        assertNull(record.currency)
    }

    @Test
    fun `supports alternate documented keys without converting integration ref to marketplace id`() {
        val connector = connector {
            OmieHttpResponse(200, """
                {"nPagina":1,"nTotPaginas":1,"pedido_venda_produto":[{
                  "cab":{"codigo_pedido":"124","codigo_pedido_integracao":"ERP-2"},
                  "itens":[{"produto":{"cCodigo":"SKU-2","quantidade":"1"}}],
                  "total_pedido":{"valor_total_pedido":"10,00"}
                }]}
            """.trimIndent().toByteArray())
        }
        val record = assertIs<OmieTransactionEvidenceRecord>(connector.read().let { (it as ConnectorReadResult.Page).value.records.single() })
        assertEquals("ERP-2", record.integrationOrderReference!!.encodedForPersistence())
        assertNull(record.customerOrderReference)
        assertEquals("SKU-2", record.products.single().identifier.encodedForPersistence())
        assertEquals(OmieTransactionProductIdentifierKind.DISPLAY_PRODUCT_CODE, record.products.single().identifier.kind)
        assertNull(record.currency)
    }

    @Test
    fun `preserves every supported product identifier kind without collapsing aliases`() {
        val connector = connector {
            OmieHttpResponse(200, """
                {"nPagina":1,"nTotPaginas":1,"pedido_venda_produto":[{
                  "cabecalho":{"codigo_pedido":"125"},
                  "det":[{"produto":{
                    "codigo_produto":"101",
                    "codigo_produto_integracao":"INT-101",
                    "codigo":"DISPLAY-101",
                    "cCodInt":"INT-101",
                    "cCodigo":"DISPLAY-101",
                    "quantidade":"3"
                  }}]
                }]}
            """.trimIndent().toByteArray())
        }
        val record = assertIs<OmieTransactionEvidenceRecord>(
            (connector.read() as ConnectorReadResult.Page).value.records.single()
        )

        assertEquals(
            setOf(
                OmieTransactionProductIdentifierKind.INTERNAL_PRODUCT_ID to "101",
                OmieTransactionProductIdentifierKind.INTEGRATION_PRODUCT_CODE to "INT-101",
                OmieTransactionProductIdentifierKind.DISPLAY_PRODUCT_CODE to "DISPLAY-101"
            ),
            record.products.map { it.identifier.kind to it.identifier.encodedForPersistence() }.toSet()
        )
    }

    @Test fun `maps codigo_produto to internal product id`() =
        assertProductField("codigo_produto", OmieTransactionProductIdentifierKind.INTERNAL_PRODUCT_ID)

    @Test fun `maps codigo_produto_integracao to integration product code`() =
        assertProductField("codigo_produto_integracao", OmieTransactionProductIdentifierKind.INTEGRATION_PRODUCT_CODE)

    @Test fun `maps codigo to display product code`() =
        assertProductField("codigo", OmieTransactionProductIdentifierKind.DISPLAY_PRODUCT_CODE)

    @Test fun `maps cCodInt to integration product code`() =
        assertProductField("cCodInt", OmieTransactionProductIdentifierKind.INTEGRATION_PRODUCT_CODE)

    @Test fun `maps cCodigo to display product code`() =
        assertProductField("cCodigo", OmieTransactionProductIdentifierKind.DISPLAY_PRODUCT_CODE)

    private fun assertProductField(field: String, kind: OmieTransactionProductIdentifierKind) {
        val connector = connector {
            OmieHttpResponse(200, """
                {"nPagina":1,"nTotPaginas":1,"pedido_venda_produto":[{
                  "cabecalho":{"codigo_pedido":"FIELD-TEST"},
                  "det":[{"produto":{"$field":"VALUE-1","quantidade":"1"}}]
                }]}
            """.trimIndent().toByteArray())
        }
        val record = assertIs<OmieTransactionEvidenceRecord>(
            (connector.read() as ConnectorReadResult.Page).value.records.single()
        )
        assertEquals(kind, record.products.single().identifier.kind)
        assertEquals("VALUE-1", record.products.single().identifier.encodedForPersistence())
    }

    private fun connector(response: () -> OmieHttpResponse) = OmieTransactionEvidenceConnector(
        URI.create("https://example.test/omie"), Clock.fixed(now, ZoneOffset.UTC), OmieHttpTransport { _, _, _, _ -> response() }
    )

    private fun OmieTransactionEvidenceConnector.read() = readPage(
        OmieTransactionEvidenceCapability.KEY, credential.copyOf(), null,
        ConnectorBudget(now.plusSeconds(30), 100, 100_000), ConnectorCancellation.NEVER
    )
}
