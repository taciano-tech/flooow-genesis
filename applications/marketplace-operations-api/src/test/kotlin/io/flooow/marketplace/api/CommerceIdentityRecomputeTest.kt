package io.flooow.marketplace.api

import io.flooow.marketplace.operations.identity.*
import io.flooow.organization.OrganizationId
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class CommerceIdentityRecomputeTest {
    private val organization = OrganizationId.parse("11111111-1111-4111-8111-111111111111")
    private val at = Instant.parse("2026-09-09T12:00:00Z")

    @Test
    fun `recompute is authenticated bodyless and exposes real evaluator health`() = testApplication {
        application {
            val recompute = CommerceIdentityRecomputeApi(
                MercadoLivreIdentityEvidenceReader { org, _ -> MercadoLivreIdentityEvidenceRead(listOf(ml(org)), 1, 1, 1) },
                OmieIdentityEvidenceReader { org, _ ->
                    OmieIdentityEvidenceRead(listOf(omie(org).copy(integrationCode = "123456789012", declaredMarketplaceOrderIds = emptySet())), 1, 0)
                }
            )
            configureApi(
                ServiceToken.test(TEST_SERVICE_TOKEN), organization,
                record = { _, _ -> error("not used") },
                commerceIdentityHealthApi = CommerceIdentityHealthApi(recompute::current),
                commerceIdentityRecomputeApi = recompute
            )
        }
        val unauthorized = client.post("/v1/commerce-identity/recompute")
        assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)

        val withBody = client.post("/v1/commerce-identity/recompute") {
            bearerAuth(TEST_SERVICE_TOKEN)
            contentType(ContentType.Application.Json)
            setBody("{}")
        }
        assertEquals(HttpStatusCode.BadRequest, withBody.status)

        val response = client.post("/v1/commerce-identity/recompute") {
            bearerAuth(TEST_SERVICE_TOKEN)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"exactConfirmed\":1")
        assertContains(response.bodyAsText(), "\"mlSellerSkuRows\":1")
        assertContains(response.bodyAsText(), "\"omieProductEvidenceRows\":0")
        assertContains(response.bodyAsText(), "\"omieTransactionProductReferences\":1")
        assertContains(response.bodyAsText(), "\"omieUnknownLegacyProductReferences\":1")
        assertContains(response.bodyAsText(), "\"crossSystemProductCandidates\":1")
        assertFalse(response.bodyAsText().contains("withinOmieProductExact"))
        assertFalse(response.bodyAsText().contains("secret"))

        val health = client.get("/v1/commerce-identity/health") {
            bearerAuth(TEST_SERVICE_TOKEN)
        }
        assertEquals(HttpStatusCode.OK, health.status)
        assertContains(health.bodyAsText(), "\"exactConfirmed\":1")
    }

    @Test
    fun `zero evidence recompute remains explicit zero`() = testApplication {
        application {
            configureApi(
                ServiceToken.test(TEST_SERVICE_TOKEN), organization,
                record = { _, _ -> error("not used") },
                commerceIdentityRecomputeApi = CommerceIdentityRecomputeApi(
                    MercadoLivreIdentityEvidenceReader { _, _ -> MercadoLivreIdentityEvidenceRead(emptyList(), 0, 0, 0) },
                    OmieIdentityEvidenceReader { _, _ -> OmieIdentityEvidenceRead(emptyList(), 0, 0) }
                )
            )
        }
        val response = client.post("/v1/commerce-identity/recompute") {
            bearerAuth(TEST_SERVICE_TOKEN)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"mlTransactionsInspected\":0")
        assertFalse(response.bodyAsText().contains("coveragePercentage"))
    }

    @Test
    fun `non evaluable persisted Omie rows are reported without failing recompute`() = testApplication {
        application {
            val records = List(117) { index -> omie(organization, "ERP-$index") }
            configureApi(
                ServiceToken.test(TEST_SERVICE_TOKEN), organization,
                record = { _, _ -> error("not used") },
                commerceIdentityRecomputeApi = CommerceIdentityRecomputeApi(
                    MercadoLivreIdentityEvidenceReader { _, _ -> MercadoLivreIdentityEvidenceRead(emptyList(), 0, 0, 0) },
                    OmieIdentityEvidenceReader { _, _ -> OmieIdentityEvidenceRead(records, 132, 15) }
                )
            )
        }
        val response = client.post("/v1/commerce-identity/recompute") {
            bearerAuth(TEST_SERVICE_TOKEN)
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"omiePersistedRows\":132")
        assertContains(response.bodyAsText(), "\"omieIdentityEvaluableRows\":117")
        assertContains(response.bodyAsText(), "\"omieNonEvaluableRows\":15")
    }

    @Test
    fun `recompute recognizes only observed Omie external references`() = testApplication {
        application {
            configureApi(
                ServiceToken.test(TEST_SERVICE_TOKEN), organization,
                record = { _, _ -> error("not used") },
                commerceIdentityRecomputeApi = CommerceIdentityRecomputeApi(
                    MercadoLivreIdentityEvidenceReader { org, _ ->
                        MercadoLivreIdentityEvidenceRead(listOf(ml(org).copy(orderId = "SYNTH-ML-ORDER")), 1, 1, 1)
                    },
                    OmieIdentityEvidenceReader { org, _ ->
                        OmieIdentityEvidenceRead(listOf(
                            OmieSalesOrderEvidence(org, emptySet(), emptyMap(), "#SYNTH-ML-ORDER", null, null, at, setOf("omie:order:revision"), emptySet(), scope(org))
                        ), 1, 0)
                    }
                )
            )
        }
        val response = client.post("/v1/commerce-identity/recompute") { bearerAuth(TEST_SERVICE_TOKEN) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"exactConfirmed\":1")
        assertContains(response.bodyAsText(), "\"omieExplicitMarketplaceOrderReferenceRows\":1")
    }

    @Test
    fun `sparse and enriched revisions of one Omie order aggregate`() = testApplication {
        application {
            configureApi(
                ServiceToken.test(TEST_SERVICE_TOKEN), organization,
                record = { _, _ -> error("not used") },
                commerceIdentityRecomputeApi = CommerceIdentityRecomputeApi(
                    MercadoLivreIdentityEvidenceReader { org, _ ->
                        MercadoLivreIdentityEvidenceRead(listOf(ml(org).copy(orderId = "SYNTH-ML-ORDER")), 1, 1, 1)
                    },
                    OmieIdentityEvidenceReader { org, _ ->
                        OmieIdentityEvidenceRead(listOf(
                            OmieSalesOrderEvidence(org, emptySet(), emptyMap(), "ERP-1", null, null, at, setOf("omie:OM-1:old"), emptySet(), scope(org)),
                            OmieSalesOrderEvidence(org, emptySet(), emptyMap(), null, "#SYNTH-ML-ORDER", null, at, setOf("omie:OM-1:new"), emptySet(), scope(org))
                        ), 2, 0)
                    }
                )
            )
        }
        val response = client.post("/v1/commerce-identity/recompute") { bearerAuth(TEST_SERVICE_TOKEN) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"omieIdentityEvaluableRows\":1")
        assertContains(response.bodyAsText(), "\"exactConfirmed\":1")
        assertContains(response.bodyAsText(), "\"conflict\":0")
    }

    @Test
    fun `distinct Omie source orders declaring one ML order preserve conflict`() = testApplication {
        application {
            configureApi(
                ServiceToken.test(TEST_SERVICE_TOKEN), organization,
                record = { _, _ -> error("not used") },
                commerceIdentityRecomputeApi = CommerceIdentityRecomputeApi(
                    MercadoLivreIdentityEvidenceReader { org, _ ->
                        MercadoLivreIdentityEvidenceRead(listOf(ml(org).copy(orderId = "SYNTH-ML-ORDER")), 1, 1, 1)
                    },
                    OmieIdentityEvidenceReader { org, _ ->
                        OmieIdentityEvidenceRead(listOf(
                            OmieSalesOrderEvidence(org, emptySet(), emptyMap(), "#SYNTH-ML-ORDER", null, null, at, setOf("omie:OM-1:one"), emptySet(), scope(org)),
                            OmieSalesOrderEvidence(org, emptySet(), emptyMap(), "SYNTH-ML-ORDER", null, null, at, setOf("omie:OM-2:two"), emptySet(), scope(org))
                        ), 2, 0)
                    }
                )
            )
        }
        val response = client.post("/v1/commerce-identity/recompute") { bearerAuth(TEST_SERVICE_TOKEN) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"omieIdentityEvaluableRows\":2")
        assertContains(response.bodyAsText(), "\"exactConfirmed\":0")
        assertContains(response.bodyAsText(), "\"conflict\":1")
    }

    @Test
    fun `contradictory references on one Omie source order fail closed`() = testApplication {
        application {
            configureApi(
                ServiceToken.test(TEST_SERVICE_TOKEN), organization,
                record = { _, _ -> error("not used") },
                commerceIdentityRecomputeApi = CommerceIdentityRecomputeApi(
                    MercadoLivreIdentityEvidenceReader { org, _ ->
                        MercadoLivreIdentityEvidenceRead(listOf(ml(org).copy(orderId = "SYNTH-ML-ORDER")), 1, 1, 1)
                    },
                    OmieIdentityEvidenceReader { org, _ ->
                        OmieIdentityEvidenceRead(listOf(
                            OmieSalesOrderEvidence(org, emptySet(), emptyMap(), "ERP-A", null, null, at, setOf("omie:OM-1:one"), emptySet(), scope(org)),
                            OmieSalesOrderEvidence(org, emptySet(), emptyMap(), "ERP-B", null, null, at, setOf("omie:OM-1:two"), emptySet(), scope(org))
                        ), 2, 0)
                    }
                )
            )
        }
        val response = client.post("/v1/commerce-identity/recompute") { bearerAuth(TEST_SERVICE_TOKEN) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertContains(response.bodyAsText(), "\"omieIdentityEvaluableRows\":2")
        assertContains(response.bodyAsText(), "\"exactConfirmed\":0")
    }

    @Test
    fun `same source order aggregation is deterministic regardless revision order`() {
        val sparse = OmieSalesOrderEvidence(
            organization, emptySet(), emptyMap(), "ERP-1", null, null, at,
            setOf("omie:OM-1:old"), emptySet(), scope(organization)
        )
        val enriched = OmieSalesOrderEvidence(
            organization, setOf("SKU"), mapOf("SKU" to BigDecimal.ONE), null,
            "#SYNTH-ML-ORDER", CommerceIdentityAmount("BRL", BigDecimal("10.00")), at,
            setOf("omie:OM-1:new"), emptySet(), scope(organization)
        )
        fun recompute(revisions: List<OmieSalesOrderEvidence>) = CommerceIdentityRecomputeApi(
            MercadoLivreIdentityEvidenceReader { org, _ ->
                MercadoLivreIdentityEvidenceRead(listOf(ml(org).copy(orderId = "SYNTH-ML-ORDER")), 1, 1, 1)
            },
            OmieIdentityEvidenceReader { org, _ -> OmieIdentityEvidenceRead(revisions.map { it.copy(organizationId = org) }, 2, 0) },
            Clock.fixed(at, ZoneOffset.UTC)
        ).recompute(organization).toString()

        assertEquals(recompute(listOf(sparse, enriched)), recompute(listOf(enriched, sparse)))
    }

    private fun ml(org: OrganizationId) = MercadoLivreTransactionEvidence(
        org, "123456789012", null, null, emptySet(), setOf("SKU"),
        mapOf("SKU" to BigDecimal.ONE), CommerceIdentityAmount("BRL", BigDecimal("10.00")), at, setOf("ml:evidence")
    )

    private fun omie(org: OrganizationId, integration: String = "ERP-1") = OmieSalesOrderEvidence(
        org, setOf("SKU"), mapOf("SKU" to BigDecimal.ONE), integration, null,
        CommerceIdentityAmount("BRL", BigDecimal("10.00")), at, setOf("omie:evidence"), setOf("123456789012"), scope(org)
    )

    private fun scope(org: OrganizationId) = OmieEvidenceScope(org, "omie-connection")
}
