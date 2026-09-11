package io.flooow.marketplace.operations.identity

import io.flooow.organization.OrganizationId
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class CrossSystemProductIdentityScope(
    val organizationId: OrganizationId,
    val mercadoLivreConnectionId: String,
    val omieConnectionId: String
) {
    init {
        requireCanonicalUuid(mercadoLivreConnectionId)
        requireCanonicalUuid(omieConnectionId)
        require(mercadoLivreConnectionId != omieConnectionId)
    }
}

data class MercadoLivreProductIdentity(val itemId: String, val sellerSku: String) {
    init {
        requireIdentity(itemId)
        requireIdentity(sellerSku)
    }
    override fun toString() = "MercadoLivreProductIdentity([REDACTED])"
}

data class OmieProviderProductIdentity(val providerProductId: String) {
    init { requireIdentity(providerProductId) }
    override fun toString() = "OmieProviderProductIdentity([REDACTED])"
}

data class CrossSystemProductIdentityRelation(
    val scope: CrossSystemProductIdentityScope,
    val mercadoLivre: MercadoLivreProductIdentity,
    val omie: OmieProviderProductIdentity
) {
    override fun toString() = "CrossSystemProductIdentityRelation([REDACTED])"
}

@JvmInline
value class CrossSystemProductIdentityDecisionId(val value: UUID) {
    override fun toString() = "[INTERNAL]"
    companion object {
        fun parse(value: String) = CrossSystemProductIdentityDecisionId(canonicalUuid(value))
    }
}

@JvmInline
value class CrossSystemProductIdentityCorrelationId(val value: UUID) {
    override fun toString() = "[INTERNAL]"
    companion object {
        fun parse(value: String) = CrossSystemProductIdentityCorrelationId(canonicalUuid(value))
    }
}

class CrossSystemProductIdentityPrincipal private constructor(private val value: String) {
    fun encodedForPersistence() = value
    override fun equals(other: Any?) =
        other is CrossSystemProductIdentityPrincipal && value == other.value
    override fun hashCode() = value.hashCode()
    override fun toString() = "[REDACTED]"

    companion object {
        fun of(value: String) = CrossSystemProductIdentityPrincipal(validatedText(value, 128))
    }
}

class CrossSystemProductIdentityProvenance private constructor(private val value: String) {
    fun encodedForPersistence() = value
    override fun equals(other: Any?) =
        other is CrossSystemProductIdentityProvenance && value == other.value
    override fun hashCode() = value.hashCode()
    override fun toString() = "[REDACTED]"

    companion object {
        fun of(value: String) = CrossSystemProductIdentityProvenance(validatedText(value, 256))
    }
}

enum class CrossSystemProductIdentityDecisionKind { CONFIRMED, REJECTED }
enum class CrossSystemProductIdentityDecisionReason {
    EXPLICIT_CONFIRMATION,
    EXPLICIT_REJECTION,
    CORRECTION
}

data class CrossSystemProductIdentityDecisionRequest(
    val id: CrossSystemProductIdentityDecisionId,
    val relation: CrossSystemProductIdentityRelation,
    val kind: CrossSystemProductIdentityDecisionKind,
    val principal: CrossSystemProductIdentityPrincipal,
    val reason: CrossSystemProductIdentityDecisionReason,
    val provenance: CrossSystemProductIdentityProvenance,
    val correlationId: CrossSystemProductIdentityCorrelationId,
    val supersedesDecisionId: CrossSystemProductIdentityDecisionId? = null
) {
    init {
        require(
            when {
                supersedesDecisionId != null -> reason == CrossSystemProductIdentityDecisionReason.CORRECTION
                kind == CrossSystemProductIdentityDecisionKind.CONFIRMED ->
                    reason == CrossSystemProductIdentityDecisionReason.EXPLICIT_CONFIRMATION
                else -> reason == CrossSystemProductIdentityDecisionReason.EXPLICIT_REJECTION
            }
        ) { "Invalid product identity decision reason" }
        require(id != supersedesDecisionId)
    }
    override fun toString() = "CrossSystemProductIdentityDecisionRequest([REDACTED])"
}

data class CrossSystemProductIdentityDecision(
    val request: CrossSystemProductIdentityDecisionRequest,
    val revision: Int,
    val decidedAt: Instant
) {
    init {
        require(revision > 0)
        require((revision == 1) == (request.supersedesDecisionId == null))
        require(decidedAt.nano % 1_000 == 0)
    }
    override fun toString() = "CrossSystemProductIdentityDecision([REDACTED])"
}

sealed interface CrossSystemProductIdentityWriteResult {
    data class Applied(val decision: CrossSystemProductIdentityDecision) :
        CrossSystemProductIdentityWriteResult
    data class AlreadyApplied(val decision: CrossSystemProductIdentityDecision) :
        CrossSystemProductIdentityWriteResult
    data object Conflict : CrossSystemProductIdentityWriteResult
    data object ScopeUnavailable : CrossSystemProductIdentityWriteResult
    data object EvidenceUnavailable : CrossSystemProductIdentityWriteResult
    data object IntegrityFailure : CrossSystemProductIdentityWriteResult
}

sealed interface CrossSystemProductIdentityResolution {
    data class Confirmed(val decision: CrossSystemProductIdentityDecision) :
        CrossSystemProductIdentityResolution
    data class Rejected(val decision: CrossSystemProductIdentityDecision) :
        CrossSystemProductIdentityResolution
    data object Unresolved : CrossSystemProductIdentityResolution
    data object Conflict : CrossSystemProductIdentityResolution
}

interface CrossSystemProductIdentityDecisionRepository {
    fun record(
        request: CrossSystemProductIdentityDecisionRequest,
        decidedAt: Instant
    ): CrossSystemProductIdentityWriteResult

    fun find(
        organizationId: OrganizationId,
        id: CrossSystemProductIdentityDecisionId
    ): CrossSystemProductIdentityDecision?

    fun history(relation: CrossSystemProductIdentityRelation): List<CrossSystemProductIdentityDecision>

    fun currentForMarketplaceIdentity(
        scope: CrossSystemProductIdentityScope,
        identity: MercadoLivreProductIdentity
    ): List<CrossSystemProductIdentityDecision>
}

class CrossSystemProductIdentityConfirmationService(
    private val repository: CrossSystemProductIdentityDecisionRepository,
    private val clock: Clock = Clock.systemUTC()
) {
    fun record(request: CrossSystemProductIdentityDecisionRequest) =
        repository.record(request, clock.instant().truncatedTo(ChronoUnit.MICROS))

    fun find(organizationId: OrganizationId, id: CrossSystemProductIdentityDecisionId) =
        repository.find(organizationId, id)

    fun history(relation: CrossSystemProductIdentityRelation) = repository.history(relation)

    fun resolve(
        relation: CrossSystemProductIdentityRelation
    ): CrossSystemProductIdentityResolution {
        val current = repository.currentForMarketplaceIdentity(
            relation.scope,
            relation.mercadoLivre
        )
        if (current.any { it.request.relation.scope != relation.scope ||
                it.request.relation.mercadoLivre != relation.mercadoLivre }) {
            return CrossSystemProductIdentityResolution.Conflict
        }
        val confirmations = current.filter {
            it.request.kind == CrossSystemProductIdentityDecisionKind.CONFIRMED
        }
        if (confirmations.size > 1) return CrossSystemProductIdentityResolution.Conflict
        val currentRelation = current.singleOrNull { it.request.relation == relation }
        return when {
            confirmations.singleOrNull()?.request?.relation == relation ->
                CrossSystemProductIdentityResolution.Confirmed(confirmations.single())
            currentRelation?.request?.kind == CrossSystemProductIdentityDecisionKind.REJECTED ->
                CrossSystemProductIdentityResolution.Rejected(currentRelation)
            else -> CrossSystemProductIdentityResolution.Unresolved
        }
    }
}

private fun requireIdentity(value: String) {
    validatedText(value, 128)
}

private fun validatedText(value: String, maxBytes: Int): String {
    require(value.isNotEmpty() && value == value.trim()) { "Invalid identity text" }
    require(value.none(Char::isISOControl)) { "Invalid identity text" }
    require(value.toByteArray(Charsets.UTF_8).size <= maxBytes) { "Invalid identity text" }
    return value
}

private fun requireCanonicalUuid(value: String) {
    canonicalUuid(value)
}

private fun canonicalUuid(value: String): UUID {
    val parsed = UUID.fromString(value)
    require(parsed.toString() == value) { "Invalid scoped connection identifier" }
    return parsed
}
