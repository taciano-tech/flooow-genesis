package io.flooow.marketplace.operations.identity

import io.flooow.organization.OrganizationId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class CrossSystemProductIdentityConfirmationTest {
    private val organization = OrganizationId.parse("11111111-1111-4111-8111-111111111111")
    private val scope = CrossSystemProductIdentityScope(
        organization,
        "22222222-2222-4222-8222-222222222222",
        "33333333-3333-4333-8333-333333333333"
    )
    private val relation = CrossSystemProductIdentityRelation(
        scope,
        MercadoLivreProductIdentity("MLB123", "SKU-1"),
        OmieProviderProductIdentity("101")
    )

    @Test
    fun `explicit decision alone confirms and correction can reject`() {
        val repository = MemoryRepository()
        val service = service(repository)
        val confirmed = request(kind = CrossSystemProductIdentityDecisionKind.CONFIRMED)
        assertIs<CrossSystemProductIdentityWriteResult.Applied>(service.record(confirmed))
        assertIs<CrossSystemProductIdentityResolution.Confirmed>(service.resolve(relation))

        val rejected = request(
            id = "55555555-5555-4555-8555-555555555555",
            kind = CrossSystemProductIdentityDecisionKind.REJECTED,
            supersedes = confirmed.id,
            reason = CrossSystemProductIdentityDecisionReason.CORRECTION
        )
        assertIs<CrossSystemProductIdentityWriteResult.Applied>(service.record(rejected))
        assertIs<CrossSystemProductIdentityResolution.Rejected>(service.resolve(relation))
        assertEquals(2, service.history(relation).size)
    }

    @Test
    fun `candidate and within Omie exact evidence never transitively confirm`() {
        val repository = MemoryRepository()
        val service = service(repository)
        assertIs<CrossSystemProductIdentityResolution.Unresolved>(service.resolve(relation))

        val policy = CommerceIdentityPolicy("test")
        val transaction = OmieSalesOrderEvidence(
            organization, setOf("SKU-1"), mapOf("SKU-1" to java.math.BigDecimal.ONE),
            "ORDER", null, null, Instant.EPOCH, setOf("omie:ORDER:fingerprint"), emptySet(),
            OmieEvidenceScope(organization, scope.omieConnectionId),
            setOf(OmieProductIdentifier(OmieProductIdentifierKind.DISPLAY_PRODUCT_CODE, "SKU-1")),
            mapOf(
                OmieProductIdentifier(OmieProductIdentifierKind.DISPLAY_PRODUCT_CODE, "SKU-1") to
                    java.math.BigDecimal.ONE
            )
        )
        val candidate = ProductIdentityBridge.candidates(
            MercadoLivreTransactionEvidence(
                organization, "ORDER", null, null, setOf("MLB123"), setOf("SKU-1"),
                mapOf("SKU-1" to java.math.BigDecimal.ONE), null, Instant.EPOCH, setOf("ml:ORDER")
            ),
            listOf(transaction), policy, Instant.EPOCH
        ).single()
        assertEquals(CommerceIdentityMatchState.CANDIDATE, candidate.state)
        assertEquals(CommerceIdentityConfirmationState.SUGGESTED, candidate.confirmationState)

        val withinOmie = WithinOmieProductResolver.resolve(
            transaction,
            OmieProductCatalogEvidenceRead(
                OmieEvidenceScope(organization, scope.omieConnectionId),
                listOf(
                    OmieCatalogProductEvidence(
                        OmieEvidenceScope(organization, scope.omieConnectionId), "101", emptySet(),
                        setOf("SKU-1"), OmieCatalogIdentityState.VALID, setOf("catalog:101")
                    )
                ),
                1
            )
        ).single()
        assertEquals(CommerceIdentityMatchState.EXACT_CONFIRMED, withinOmie.state)
        assertIs<CrossSystemProductIdentityResolution.Unresolved>(service.resolve(relation))
    }

    private fun service(repository: MemoryRepository) = CrossSystemProductIdentityConfirmationService(
        repository,
        Clock.fixed(Instant.parse("2026-09-11T17:00:00Z"), ZoneOffset.UTC)
    )

    private fun request(
        id: String = "44444444-4444-4444-8444-444444444444",
        kind: CrossSystemProductIdentityDecisionKind,
        supersedes: CrossSystemProductIdentityDecisionId? = null,
        reason: CrossSystemProductIdentityDecisionReason = if (
            kind == CrossSystemProductIdentityDecisionKind.CONFIRMED
        ) CrossSystemProductIdentityDecisionReason.EXPLICIT_CONFIRMATION
        else CrossSystemProductIdentityDecisionReason.EXPLICIT_REJECTION
    ) = CrossSystemProductIdentityDecisionRequest(
        CrossSystemProductIdentityDecisionId.parse(id), relation, kind,
        CrossSystemProductIdentityPrincipal.of("service-principal"), reason,
        CrossSystemProductIdentityProvenance.of("ticket:TASK-0165K"),
        CrossSystemProductIdentityCorrelationId(UUID.fromString("66666666-6666-4666-8666-666666666666")),
        supersedes
    )

    private class MemoryRepository : CrossSystemProductIdentityDecisionRepository {
        private val decisions = mutableListOf<CrossSystemProductIdentityDecision>()
        override fun record(request: CrossSystemProductIdentityDecisionRequest, decidedAt: Instant):
            CrossSystemProductIdentityWriteResult {
            decisions.singleOrNull { it.request.id == request.id }?.let {
                return if (it.request == request) CrossSystemProductIdentityWriteResult.AlreadyApplied(it)
                else CrossSystemProductIdentityWriteResult.IntegrityFailure
            }
            val previous = request.supersedesDecisionId?.let { id ->
                decisions.singleOrNull { it.request.id == id }
                    ?: return CrossSystemProductIdentityWriteResult.Conflict
            }
            if (previous != null && previous.request.relation != request.relation) {
                return CrossSystemProductIdentityWriteResult.Conflict
            }
            val decision = CrossSystemProductIdentityDecision(request, (previous?.revision ?: 0) + 1, decidedAt)
            decisions += decision
            return CrossSystemProductIdentityWriteResult.Applied(decision)
        }
        override fun find(organizationId: OrganizationId, id: CrossSystemProductIdentityDecisionId) =
            decisions.singleOrNull { it.request.relation.scope.organizationId == organizationId && it.request.id == id }
        override fun history(relation: CrossSystemProductIdentityRelation) =
            decisions.filter { it.request.relation == relation }.sortedBy { it.revision }
        override fun currentForMarketplaceIdentity(
            scope: CrossSystemProductIdentityScope,
            identity: MercadoLivreProductIdentity
        ) = decisions.filter { candidate ->
            candidate.request.relation.scope == scope && candidate.request.relation.mercadoLivre == identity &&
                decisions.none { it.request.supersedesDecisionId == candidate.request.id }
        }
    }
}
