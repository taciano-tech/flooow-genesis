package io.flooow.marketplace.api

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityConfirmationService
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityCorrelationId
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityDecision
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityDecisionId
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityDecisionKind
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityDecisionReason
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityDecisionRequest
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityPrincipal
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityProvenance
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityRelation
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityScope
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityWriteResult
import io.flooow.marketplace.operations.identity.MercadoLivreProductIdentity
import io.flooow.marketplace.operations.identity.OmieProviderProductIdentity
import io.flooow.organization.OrganizationId
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal const val CROSS_SYSTEM_PRODUCT_IDENTITY_DECISIONS_PATH =
    "/v1/commerce-identity/product-decisions"

internal data class CrossSystemProductIdentityApiResult(
    val status: HttpStatusCode,
    val body: JsonObject
)

/** Narrow authenticated seam for explicit evidence; connections remain server-selected. */
internal class CrossSystemProductIdentityConfirmationApi(
    private val service: CrossSystemProductIdentityConfirmationService,
    private val mercadoLivreConnectionId: IntegrationConnectionId?,
    private val omieConnectionId: IntegrationConnectionId?
) {
    fun record(organizationId: OrganizationId, body: String): CrossSystemProductIdentityApiResult {
        val mlConnection = mercadoLivreConnectionId ?: return unavailable()
        val omieConnection = omieConnectionId ?: return unavailable()
        val request = decode(body, organizationId, mlConnection, omieConnection)
        return when (val result = service.record(request)) {
            is CrossSystemProductIdentityWriteResult.Applied ->
                CrossSystemProductIdentityApiResult(HttpStatusCode.Created, decisionJson(result.decision))
            is CrossSystemProductIdentityWriteResult.AlreadyApplied ->
                CrossSystemProductIdentityApiResult(HttpStatusCode.OK, decisionJson(result.decision))
            CrossSystemProductIdentityWriteResult.Conflict -> problem(
                HttpStatusCode.Conflict, "PRODUCT_IDENTITY_DECISION_CONFLICT",
                "The decision conflicts with current explicit identity evidence"
            )
            CrossSystemProductIdentityWriteResult.ScopeUnavailable -> unavailable()
            CrossSystemProductIdentityWriteResult.EvidenceUnavailable -> problem(
                HttpStatusCode.UnprocessableEntity, "PRODUCT_IDENTITY_EVIDENCE_UNAVAILABLE",
                "Exact durable provider identity evidence is unavailable"
            )
            CrossSystemProductIdentityWriteResult.IntegrityFailure -> problem(
                HttpStatusCode.InternalServerError, "PRODUCT_IDENTITY_INTEGRITY_FAILURE",
                "The identity decision could not be persisted safely"
            )
        }
    }

    fun find(organizationId: OrganizationId, decisionId: String): CrossSystemProductIdentityApiResult {
        val id = try {
            CrossSystemProductIdentityDecisionId.parse(decisionId)
        } catch (_: IllegalArgumentException) {
            throw CrossSystemProductIdentityMalformedException("Invalid decision identifier")
        }
        val decision = service.find(organizationId, id) ?: return problem(
            HttpStatusCode.NotFound, "PRODUCT_IDENTITY_DECISION_NOT_FOUND",
            "The identity decision was not found"
        )
        return CrossSystemProductIdentityApiResult(HttpStatusCode.OK, decisionJson(decision))
    }

    private fun decode(
        body: String,
        organizationId: OrganizationId,
        mlConnection: IntegrationConnectionId,
        omieConnection: IntegrationConnectionId
    ): CrossSystemProductIdentityDecisionRequest = try {
        val value = strictJson.parseToJsonElement(body).jsonObject
        val required = setOf(
            "decisionId", "correlationId", "decision", "mercadoLivreItemId",
            "mercadoLivreSellerSku", "omieProviderProductId", "reason", "provenance"
        )
        val permitted = required + "supersedesDecisionId"
        require(value.keys.all(permitted::contains) && required.all(value::containsKey))
        CrossSystemProductIdentityDecisionRequest(
            CrossSystemProductIdentityDecisionId.parse(value.text("decisionId")),
            CrossSystemProductIdentityRelation(
                CrossSystemProductIdentityScope(
                    organizationId,
                    mlConnection.value.toString(),
                    omieConnection.value.toString()
                ),
                MercadoLivreProductIdentity(
                    value.text("mercadoLivreItemId"),
                    value.text("mercadoLivreSellerSku")
                ),
                OmieProviderProductIdentity(value.text("omieProviderProductId"))
            ),
            CrossSystemProductIdentityDecisionKind.valueOf(value.text("decision")),
            CrossSystemProductIdentityPrincipal.of("service-bearer:${organizationId.value}"),
            CrossSystemProductIdentityDecisionReason.valueOf(value.text("reason")),
            CrossSystemProductIdentityProvenance.of(value.text("provenance")),
            CrossSystemProductIdentityCorrelationId.parse(value.text("correlationId")),
            value["supersedesDecisionId"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.content
                ?.let(CrossSystemProductIdentityDecisionId::parse)
        )
    } catch (_: Exception) {
        throw CrossSystemProductIdentityMalformedException("Invalid product identity decision")
    }

    private fun unavailable() = problem(
        HttpStatusCode.ServiceUnavailable, "PRODUCT_IDENTITY_CONFIRMATION_UNAVAILABLE",
        "The governed product identity scope is unavailable"
    )
}

internal class CrossSystemProductIdentityMalformedException(message: String) : RuntimeException(message)

private val strictJson = Json { ignoreUnknownKeys = false }

private fun JsonObject.text(name: String): String =
    requireNotNull(this[name]) { "Missing $name" }.jsonPrimitive.content

private fun decisionJson(decision: CrossSystemProductIdentityDecision) = buildJsonObject {
    val request = decision.request
    put("decisionId", request.id.value.toString())
    put("decision", request.kind.name)
    put("revision", decision.revision)
    request.supersedesDecisionId?.let { put("supersedesDecisionId", it.value.toString()) }
    put("mercadoLivreItemId", request.relation.mercadoLivre.itemId)
    put("mercadoLivreSellerSku", request.relation.mercadoLivre.sellerSku)
    put("omieProviderProductId", request.relation.omie.providerProductId)
    put("reason", request.reason.name)
    put("correlationId", request.correlationId.value.toString())
    put("decidedAt", decision.decidedAt.toString())
}

private fun problem(status: HttpStatusCode, code: String, detail: String) =
    CrossSystemProductIdentityApiResult(status, buildJsonObject {
        put("status", status.value)
        put("code", code)
        put("detail", detail)
    })
