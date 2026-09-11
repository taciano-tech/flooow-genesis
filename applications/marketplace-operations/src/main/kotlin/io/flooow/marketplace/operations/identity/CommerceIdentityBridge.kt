package io.flooow.marketplace.operations.identity

import io.flooow.organization.OrganizationId
import java.math.BigDecimal
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.abs

enum class CommerceIdentitySystem { MERCADO_LIVRE, OMIE }
enum class CommerceIdentityType { ORDER_ID, PACK_ID, SHIPMENT_ID, ITEM_ID, SELLER_SKU, ERP_PRODUCT_ID, ERP_PRODUCT_INTEGRATION_CODE, ERP_PRODUCT_DISPLAY_CODE, ERP_PRODUCT_UNKNOWN_LEGACY, ERP_PRODUCT_CODE, ERP_ORDER_INTEGRATION_CODE, ERP_CUSTOMER_ORDER_NUMBER }
enum class CommerceIdentityMatchState { EXACT_CONFIRMED, CANDIDATE, AMBIGUOUS, CONFLICT, UNRESOLVED }
enum class CommerceIdentityConfirmationState { SUGGESTED, GOVERNED_CONFIRMED }
enum class CommerceIdentityEvidenceKind { DECLARED_EXTERNAL_REFERENCE, EXACT_SELLER_SKU_TO_OMIE_PRODUCT_REFERENCE_TEXT, EXACT_QUANTITY, EXACT_ORDER_VALUE, SAME_CALENDAR_DATE, DATE_WITHIN_ONE_DAY, EXACT_ITEM_SET, CONFLICTING_EXTERNAL_REFERENCE }

data class CommerceIdentityEvidence(
    val organizationId: OrganizationId,
    val sourceSystem: CommerceIdentitySystem,
    val sourceIdentityType: CommerceIdentityType,
    val sourceIdentityValue: String,
    val kind: CommerceIdentityEvidenceKind,
    val provenance: String,
    val observedAt: Instant
) {
    init {
        require(sourceIdentityValue.isNotBlank() && sourceIdentityValue == sourceIdentityValue.trim())
        require(sourceIdentityValue.none(Char::isISOControl) && sourceIdentityValue.length <= 128)
        require(provenance.isNotBlank() && provenance.length <= 128)
        require(observedAt.nano % 1_000 == 0)
    }
    override fun toString() = "CommerceIdentityEvidence([REDACTED])"
}

data class CommerceIdentityAmount(val currency: String, val value: BigDecimal) {
    init { require(Regex("[A-Z]{3}").matches(currency)); require(value.scale() <= 6) }
}

data class MercadoLivreTransactionEvidence(
    val organizationId: OrganizationId, val orderId: String, val packId: String?, val shipmentId: String?,
    val itemIds: Set<String>, val sellerSkus: Set<String>, val quantityBySku: Map<String, BigDecimal>,
    val orderAmount: CommerceIdentityAmount?, val occurredAt: Instant, val evidenceReferences: Set<String>
) {
    init {
        require(orderId.isNotBlank()); require(itemIds.none { it.isBlank() }); require(sellerSkus.none { it.isBlank() })
        require(quantityBySku.keys.all { it in sellerSkus }); require(evidenceReferences.isNotEmpty()); require(occurredAt.nano % 1_000 == 0)
    }
}

data class OmieSalesOrderEvidence(
    val organizationId: OrganizationId, val productCodes: Set<String>, val quantityByCode: Map<String, BigDecimal>,
    val integrationCode: String?, val customerOrderNumber: String?, val orderAmount: CommerceIdentityAmount?,
    val occurredAt: Instant, val evidenceReferences: Set<String>, val declaredMarketplaceOrderIds: Set<String>,
    val scope: OmieEvidenceScope,
    val productIdentifiers: Set<OmieProductIdentifier> = productCodes.mapTo(linkedSetOf()) {
        OmieProductIdentifier(OmieProductIdentifierKind.UNKNOWN_LEGACY, it)
    },
    val quantityByProductIdentifier: Map<OmieProductIdentifier, BigDecimal> =
        productIdentifiers.associateWith { quantityByCode[it.value] ?: BigDecimal.ZERO },
    val sourceObservedAt: Instant = occurredAt
) {
    init {
        require(productCodes.none { it.isBlank() }); require(quantityByCode.keys.all { it in productCodes })
        require(productIdentifiers.none { it.value.isBlank() })
        require(quantityByProductIdentifier.keys.all { it in productIdentifiers })
        require(scope.organizationId == organizationId)
        require(integrationCode != null || customerOrderNumber != null || productCodes.isNotEmpty())
        require(evidenceReferences.isNotEmpty()); require(occurredAt.nano % 1_000 == 0)
        require(sourceObservedAt.nano % 1_000 == 0)
    }
}

data class CommerceIdentityPolicy(
    val version: String, val candidateMinimumEvidence: Int = 2,
    val valueTolerance: BigDecimal = BigDecimal("0.02"), val dateWindowDays: Long = 1
) {
    init { require(version.isNotBlank()); require(candidateMinimumEvidence >= 2); require(valueTolerance >= BigDecimal.ZERO); require(dateWindowDays >= 0) }
}

data class CommerceIdentityCandidate(
    val organizationId: OrganizationId, val sourceSystem: CommerceIdentitySystem, val sourceIdentityType: CommerceIdentityType,
    val sourceIdentityValue: String, val targetSystem: CommerceIdentitySystem, val targetIdentityType: CommerceIdentityType,
    val targetIdentityValue: String, val state: CommerceIdentityMatchState, val confirmationState: CommerceIdentityConfirmationState,
    val evidence: List<CommerceIdentityEvidence>, val policyVersion: String, val assessedAt: Instant
) {
    init {
        require(sourceIdentityValue.isNotBlank() && targetIdentityValue.isNotBlank()); require(evidence.isNotEmpty())
        require(evidence.all { it.organizationId == organizationId }); require(assessedAt.nano % 1_000 == 0)
        require(confirmationState == CommerceIdentityConfirmationState.SUGGESTED || state == CommerceIdentityMatchState.EXACT_CONFIRMED)
    }
    override fun toString() = "CommerceIdentityCandidate([REDACTED])"
}

data class CommerceIdentityAssessment(
    val organizationId: OrganizationId, val transaction: CommerceIdentityCandidate,
    val productCandidates: List<CommerceIdentityCandidate>, val policyVersion: String, val assessedAt: Instant
) {
    init { require(transaction.organizationId == organizationId); require(productCandidates.all { it.organizationId == organizationId }) }
}

object CommerceIdentityBridge {
    fun assess(marketplace: MercadoLivreTransactionEvidence, omieOrders: List<OmieSalesOrderEvidence>, policy: CommerceIdentityPolicy, assessedAt: Instant): CommerceIdentityAssessment {
        require(omieOrders.all { it.organizationId == marketplace.organizationId }) { "Identity evidence organization mismatch" }
        require(assessedAt.nano % 1_000 == 0)
        val exact = omieOrders.filter { it.hasDeclaredOrder(marketplace.orderId) }
        val transaction = when {
            exact.size == 1 -> candidate(marketplace, exact.single(), CommerceIdentityMatchState.EXACT_CONFIRMED,
                listOf(evidence(marketplace, CommerceIdentityEvidenceKind.DECLARED_EXTERNAL_REFERENCE, "OMIE_DECLARED_EXTERNAL_REFERENCE", assessedAt)), policy, assessedAt, true)
            exact.size > 1 -> unresolved(marketplace, CommerceIdentityMatchState.CONFLICT, policy, assessedAt, CommerceIdentityEvidenceKind.CONFLICTING_EXTERNAL_REFERENCE)
            else -> candidateAssessment(marketplace, omieOrders, policy, assessedAt)
        }
        return CommerceIdentityAssessment(marketplace.organizationId, transaction, productCandidates(marketplace, omieOrders, policy, assessedAt), policy.version, assessedAt)
    }

    private fun candidateAssessment(m: MercadoLivreTransactionEvidence, orders: List<OmieSalesOrderEvidence>, p: CommerceIdentityPolicy, at: Instant): CommerceIdentityCandidate {
        val scored = orders.mapNotNull { o -> score(m, o, p, at) }.sortedWith(compareByDescending<Pair<OmieSalesOrderEvidence, List<CommerceIdentityEvidence>>> { it.second.size }.thenBy { it.first.integrationCode ?: it.first.customerOrderNumber ?: "" })
        if (scored.isEmpty()) return unresolved(m, CommerceIdentityMatchState.UNRESOLVED, p, at)
        val top = scored.first(); val tied = scored.count { it.second.size == top.second.size }
        return candidate(m, top.first, if (tied > 1) CommerceIdentityMatchState.AMBIGUOUS else CommerceIdentityMatchState.CANDIDATE, top.second, p, at, false)
    }

    private fun productCandidates(m: MercadoLivreTransactionEvidence, orders: List<OmieSalesOrderEvidence>, p: CommerceIdentityPolicy, at: Instant): List<CommerceIdentityCandidate> =
        ProductIdentityBridge.candidates(m, orders, p, at)

    private fun score(m: MercadoLivreTransactionEvidence, o: OmieSalesOrderEvidence, p: CommerceIdentityPolicy, at: Instant): Pair<OmieSalesOrderEvidence, List<CommerceIdentityEvidence>>? {
        if (o.declaredMarketplaceOrderIds.isNotEmpty()) return null
        val shared = m.sellerSkus.intersect(o.productCodes); if (shared.isEmpty()) return null
        val e = mutableListOf(evidence(m, CommerceIdentityEvidenceKind.EXACT_SELLER_SKU_TO_OMIE_PRODUCT_REFERENCE_TEXT, "MGI_EXACT_SELLER_SKU_TO_OMIE_PRODUCT_REFERENCE_TEXT", at))
        if (shared.all { m.quantityBySku[it] == o.quantityByCode[it] }) e += evidence(m, CommerceIdentityEvidenceKind.EXACT_QUANTITY, "MGI_EVIDENCE_WEIGHTED_RECONCILIATION", at)
        val delta = if (m.orderAmount != null && o.orderAmount != null && m.orderAmount.currency == o.orderAmount.currency) m.orderAmount.value.subtract(o.orderAmount.value).abs() else null
        if (delta != null && delta <= p.valueTolerance) e += evidence(m, CommerceIdentityEvidenceKind.EXACT_ORDER_VALUE, "MGI_EVIDENCE_WEIGHTED_RECONCILIATION", at)
        val days = abs(ChronoUnit.DAYS.between(m.occurredAt, o.occurredAt))
        if (days == 0L) e += evidence(m, CommerceIdentityEvidenceKind.SAME_CALENDAR_DATE, "MGI_EVIDENCE_WEIGHTED_RECONCILIATION", at)
        else if (days <= p.dateWindowDays) e += evidence(m, CommerceIdentityEvidenceKind.DATE_WITHIN_ONE_DAY, "MGI_EVIDENCE_WEIGHTED_RECONCILIATION", at)
        return if (e.size >= p.candidateMinimumEvidence) o to e else null
    }

    private fun evidence(m: MercadoLivreTransactionEvidence, kind: CommerceIdentityEvidenceKind, provenance: String, at: Instant) = CommerceIdentityEvidence(m.organizationId, CommerceIdentitySystem.MERCADO_LIVRE, CommerceIdentityType.ORDER_ID, m.orderId, kind, provenance, at)
    private fun candidate(m: MercadoLivreTransactionEvidence, o: OmieSalesOrderEvidence, state: CommerceIdentityMatchState, e: List<CommerceIdentityEvidence>, p: CommerceIdentityPolicy, at: Instant, confirmed: Boolean) = CommerceIdentityCandidate(m.organizationId, CommerceIdentitySystem.MERCADO_LIVRE, CommerceIdentityType.ORDER_ID, m.orderId, CommerceIdentitySystem.OMIE, if (o.integrationCode != null) CommerceIdentityType.ERP_ORDER_INTEGRATION_CODE else CommerceIdentityType.ERP_CUSTOMER_ORDER_NUMBER, o.integrationCode ?: o.customerOrderNumber ?: o.productCodes.sorted().first(), state, if (confirmed) CommerceIdentityConfirmationState.GOVERNED_CONFIRMED else CommerceIdentityConfirmationState.SUGGESTED, e, p.version, at)
    private fun unresolved(m: MercadoLivreTransactionEvidence, state: CommerceIdentityMatchState, p: CommerceIdentityPolicy, at: Instant, kind: CommerceIdentityEvidenceKind = CommerceIdentityEvidenceKind.CONFLICTING_EXTERNAL_REFERENCE) = CommerceIdentityCandidate(m.organizationId, CommerceIdentitySystem.MERCADO_LIVRE, CommerceIdentityType.ORDER_ID, m.orderId, CommerceIdentitySystem.OMIE, CommerceIdentityType.ERP_ORDER_INTEGRATION_CODE, "UNRESOLVED", state, CommerceIdentityConfirmationState.SUGGESTED, listOf(evidence(m, kind, "MGI_IDENTITY_BOUNDARY", at)), p.version, at)
    private fun OmieSalesOrderEvidence.hasDeclaredOrder(orderId: String) = declaredMarketplaceOrderIds.any { it.trim().removePrefix("#") == orderId.trim().removePrefix("#") }
}
