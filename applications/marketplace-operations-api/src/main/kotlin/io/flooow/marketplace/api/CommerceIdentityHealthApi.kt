package io.flooow.marketplace.api

import io.flooow.marketplace.operations.identity.CommerceIdentityHealthEvaluation
import io.flooow.organization.OrganizationId
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Read-only candidate diagnostics; explicit cross-system decisions use their separate governed seam. */
class CommerceIdentityHealthApi(private val current: (OrganizationId) -> CommerceIdentityHealthEvaluation?) {
    fun health(organizationId: OrganizationId) = current(organizationId)?.let { e ->
        buildJsonObject {
            put("organizationScoped", true); put("mlTransactionsInspected", e.health.mlTransactionsInspected); put("omieTransactionsInspected", e.health.omieTransactionsInspected)
            put("exactConfirmed", e.health.exactConfirmed); put("candidate", e.health.candidate); put("ambiguous", e.health.ambiguous); put("conflict", e.health.conflict); put("unresolved", e.health.unresolved)
            e.health.coveragePercentage?.let { put("coveragePercentage", it.toPlainString()) }
            put("evaluationWindow", e.health.evaluationWindow); put("policyVersion", e.health.policyVersion); put("evaluatedAt", e.health.evaluatedAt.toString())
            put("topMatchReasons", buildJsonObject { e.health.topMatchReasons.forEach { (k,v) -> put(k,v) } }); put("topGapReasons", buildJsonObject { e.health.topGapReasons.forEach { (k,v) -> put(k,v) } })
        }
    } ?: buildJsonObject { put("available", false); put("reason", "REAL_EVALUATION_NOT_AVAILABLE") }

    fun relations(organizationId: OrganizationId) = buildJsonArray {
        current(organizationId)?.relations?.forEach { r -> add(buildJsonObject { put("relationId", r.relationId); put("marketplaceOrderId", r.marketplaceOrderId); put("omieIdentity", r.omieIdentity); put("state", r.state.name); put("policyVersion", r.policyVersion); put("evaluatedAt", r.evaluatedAt.toString()) }) }
    }
}
