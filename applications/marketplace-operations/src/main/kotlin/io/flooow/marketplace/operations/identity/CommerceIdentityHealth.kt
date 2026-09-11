package io.flooow.marketplace.operations.identity

import io.flooow.organization.OrganizationId
import java.time.Instant

data class CommerceIdentityRelation(
    val relationId: String,
    val organizationId: OrganizationId,
    val marketplaceOrderId: String,
    val omieIdentity: String,
    val state: CommerceIdentityMatchState,
    val evidence: List<CommerceIdentityEvidence>,
    val policyVersion: String,
    val evaluatedAt: Instant
) {
    init { require(relationId.isNotBlank()); require(evidence.isNotEmpty()); require(evidence.all { it.organizationId == organizationId }) }
}

data class CommerceIdentityHealth(
    val organizationId: OrganizationId,
    val mlTransactionsInspected: Int,
    val omieTransactionsInspected: Int,
    val exactConfirmed: Int,
    val candidate: Int,
    val ambiguous: Int,
    val conflict: Int,
    val unresolved: Int,
    val evaluationWindow: String?,
    val policyVersion: String,
    val evaluatedAt: Instant,
    val topMatchReasons: Map<String, Int>,
    val topGapReasons: Map<String, Int>
) {
    val coveragePercentage: java.math.BigDecimal? = mlTransactionsInspected.takeIf { it > 0 }?.let {
        java.math.BigDecimal(exactConfirmed * 100).divide(java.math.BigDecimal(it), 2, java.math.RoundingMode.HALF_UP)
    }
}

data class CommerceIdentityHealthEvaluation(val health: CommerceIdentityHealth, val relations: List<CommerceIdentityRelation>)

object CommerceIdentityHealthEvaluator {
    fun evaluate(
        marketplace: List<MercadoLivreTransactionEvidence>,
        omie: List<OmieSalesOrderEvidence>,
        policy: CommerceIdentityPolicy,
        evaluatedAt: Instant,
        evaluationWindow: String? = null
    ): CommerceIdentityHealthEvaluation {
        val organization = marketplace.firstOrNull()?.organizationId ?: omie.firstOrNull()?.organizationId
            ?: error("At least one source record is required")
        return evaluate(organization, marketplace, omie, policy, evaluatedAt, evaluationWindow)
    }

    fun evaluate(
        organization: OrganizationId,
        marketplace: List<MercadoLivreTransactionEvidence>,
        omie: List<OmieSalesOrderEvidence>,
        policy: CommerceIdentityPolicy,
        evaluatedAt: Instant,
        evaluationWindow: String? = null
    ): CommerceIdentityHealthEvaluation {
        require(marketplace.all { it.organizationId == organization })
        require(omie.all { it.organizationId == organization })
        val assessments = marketplace.map { CommerceIdentityBridge.assess(it, omie, policy, evaluatedAt) }
        val relations = assessments.map { a ->
            val c = a.transaction
            CommerceIdentityRelation("${c.organizationId.value}:${c.sourceIdentityValue}:${c.targetIdentityValue}:${policy.version}", organization, c.sourceIdentityValue, c.targetIdentityValue, c.state, c.evidence, policy.version, evaluatedAt)
        }
        val counts = relations.groupingBy { it.state }.eachCount()
        val reasons = relations.flatMap { it.evidence }.groupingBy { it.kind.name }.eachCount().toSortedMap()
        val gaps = relations.filter { it.state != CommerceIdentityMatchState.EXACT_CONFIRMED }.flatMap { it.evidence }.groupingBy { it.kind.name }.eachCount().toSortedMap()
        return CommerceIdentityHealthEvaluation(CommerceIdentityHealth(organization, marketplace.size, omie.size,
            counts[CommerceIdentityMatchState.EXACT_CONFIRMED] ?: 0, counts[CommerceIdentityMatchState.CANDIDATE] ?: 0,
            counts[CommerceIdentityMatchState.AMBIGUOUS] ?: 0, counts[CommerceIdentityMatchState.CONFLICT] ?: 0,
            counts[CommerceIdentityMatchState.UNRESOLVED] ?: 0, evaluationWindow, policy.version, evaluatedAt, reasons, gaps), relations)
    }
}
