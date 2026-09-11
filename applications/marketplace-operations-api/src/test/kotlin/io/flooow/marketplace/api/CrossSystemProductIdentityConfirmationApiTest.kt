package io.flooow.marketplace.api

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityConfirmationService
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityDecision
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityDecisionId
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityDecisionRepository
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityDecisionRequest
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityRelation
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityScope
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityWriteResult
import io.flooow.marketplace.operations.identity.MercadoLivreProductIdentity
import io.flooow.organization.OrganizationId
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CrossSystemProductIdentityConfirmationApiTest {
    private val organization = OrganizationId(UUID.fromString("10000000-0000-0000-0000-000000000001"))
    private val mlConnection = IntegrationConnectionId(UUID.fromString("20000000-0000-0000-0000-000000000001"))
    private val omieConnection = IntegrationConnectionId(UUID.fromString("30000000-0000-0000-0000-000000000001"))

    @Test
    fun `authenticated write binds organization and connections server side and reads immutable decision`() =
        testApplication {
            val repository = ApiDecisionRepository()
            application {
                configureApi(
                    ServiceToken.test(TEST_SERVICE_TOKEN), organization,
                    record = { _, _ -> error("not used") },
                    crossSystemProductIdentityApi = api(repository, mlConnection, omieConnection)
                )
            }

            assertEquals(
                HttpStatusCode.Unauthorized,
                client.post(CROSS_SYSTEM_PRODUCT_IDENTITY_DECISIONS_PATH).status
            )
            val response = client.post(CROSS_SYSTEM_PRODUCT_IDENTITY_DECISIONS_PATH) {
                bearerAuth(TEST_SERVICE_TOKEN)
                contentType(ContentType.Application.Json)
                setBody(body())
            }
            assertEquals(HttpStatusCode.Created, response.status)
            val recorded = assertNotNull(repository.recorded)
            assertEquals(organization, recorded.request.relation.scope.organizationId)
            assertEquals(mlConnection.value.toString(), recorded.request.relation.scope.mercadoLivreConnectionId)
            assertEquals(omieConnection.value.toString(), recorded.request.relation.scope.omieConnectionId)

            val read = client.get(
                "$CROSS_SYSTEM_PRODUCT_IDENTITY_DECISIONS_PATH/40000000-0000-0000-0000-000000000001"
            ) { bearerAuth(TEST_SERVICE_TOKEN) }
            assertEquals(HttpStatusCode.OK, read.status)
            assertEquals(true, read.bodyAsText().contains("\"decision\":\"CONFIRMED\""))
        }

    @Test
    fun `missing server connection scope fails closed`() = testApplication {
        application {
            configureApi(
                ServiceToken.test(TEST_SERVICE_TOKEN), organization,
                record = { _, _ -> error("not used") },
                crossSystemProductIdentityApi = api(ApiDecisionRepository(), null, omieConnection)
            )
        }
        val unavailable = client.post(CROSS_SYSTEM_PRODUCT_IDENTITY_DECISIONS_PATH) {
            bearerAuth(TEST_SERVICE_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(body())
        }
        assertEquals(HttpStatusCode.ServiceUnavailable, unavailable.status)
    }

    @Test
    fun `caller supplied organization scope is rejected`() = testApplication {
        application {
            configureApi(
                ServiceToken.test(TEST_SERVICE_TOKEN), organization,
                record = { _, _ -> error("not used") },
                crossSystemProductIdentityApi = api(ApiDecisionRepository(), mlConnection, omieConnection)
            )
        }
        val widened = client.post(CROSS_SYSTEM_PRODUCT_IDENTITY_DECISIONS_PATH) {
            bearerAuth(TEST_SERVICE_TOKEN)
            contentType(ContentType.Application.Json)
            setBody(body().dropLast(1) + ",\"organizationId\":\"${UUID.randomUUID()}\"}")
        }
        assertEquals(HttpStatusCode.BadRequest, widened.status)
    }

    private fun api(
        repository: CrossSystemProductIdentityDecisionRepository,
        ml: IntegrationConnectionId?,
        omie: IntegrationConnectionId?
    ) = CrossSystemProductIdentityConfirmationApi(
        CrossSystemProductIdentityConfirmationService(
            repository,
            Clock.fixed(Instant.parse("2026-09-11T18:00:00Z"), ZoneOffset.UTC)
        ),
        ml,
        omie
    )

    private fun body() = """{
        "decisionId":"40000000-0000-0000-0000-000000000001",
        "correlationId":"50000000-0000-0000-0000-000000000001",
        "decision":"CONFIRMED",
        "mercadoLivreItemId":"MLB-1",
        "mercadoLivreSellerSku":"SKU-1",
        "omieProviderProductId":"OMIE-1",
        "reason":"EXPLICIT_CONFIRMATION",
        "provenance":"TASK-0165K explicit review"
    }"""

    private class ApiDecisionRepository : CrossSystemProductIdentityDecisionRepository {
        var recorded: CrossSystemProductIdentityDecision? = null

        override fun record(request: CrossSystemProductIdentityDecisionRequest, decidedAt: Instant) =
            CrossSystemProductIdentityWriteResult.Applied(
                CrossSystemProductIdentityDecision(request, 1, decidedAt).also { recorded = it }
            )

        override fun find(organizationId: OrganizationId, id: CrossSystemProductIdentityDecisionId) =
            recorded?.takeIf {
                it.request.relation.scope.organizationId == organizationId && it.request.id == id
            }

        override fun history(relation: CrossSystemProductIdentityRelation) = emptyList<CrossSystemProductIdentityDecision>()

        override fun currentForMarketplaceIdentity(
            scope: CrossSystemProductIdentityScope,
            identity: MercadoLivreProductIdentity
        ) = emptyList<CrossSystemProductIdentityDecision>()
    }
}
