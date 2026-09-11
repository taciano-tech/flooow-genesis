package io.flooow.marketplace.api

import io.flooow.integration.connector.ConnectorBudget
import io.flooow.integration.connector.ConnectorExecutionFailureKind
import io.flooow.integration.connector.ConnectorExecutionOutcome
import io.flooow.integration.connector.ConnectorInvocation
import io.flooow.integration.connector.ConnectorInvocationId
import io.flooow.integration.connector.ConnectorRuntime
import io.flooow.integration.connector.ConnectorSuccessKind
import io.flooow.integration.control.CredentialKind
import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.control.IntegrationControlPlaneService
import io.flooow.integration.control.ProviderKey
import io.flooow.marketplace.operations.economics.provider.OmieTransactionEvidenceCapability
import io.flooow.organization.OrganizationId
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID

internal const val OMIE_EVIDENCE_REFRESH_PATH = "/v1/commerce-identity/omie/refresh"
internal const val OMIE_EVIDENCE_REACQUISITION_PATH = "/v1/commerce-identity/omie/reacquire"
internal const val OMIE_PRODUCT_COST_REFRESH_PATH =
    "/v1/commerce-identity/omie/product-cost/refresh"

private val OMIE_PROVIDER = ProviderKey.of("omie")
private val OMIE_CAPABILITY = OmieTransactionEvidenceCapability.KEY
private val REFRESH_DEADLINE = Duration.ofMinutes(2)
private const val DEFAULT_MAX_PAGES = 10
private const val MAX_RECORDS_PER_PAGE = 100
private const val MAX_RESPONSE_BYTES = 2L * 1024L * 1024L

/**
 * Read-only, organization-scoped Omie evidence ingestion. This class only
 * executes ConnectorRuntime and reports operational metadata; it never invokes
 * identity confirmation or writes Economic Truth.
 */
internal class OmieEvidenceRefreshApi(
    private val controlPlane: IntegrationControlPlaneService,
    private val runtime: ConnectorRuntime,
    private val configuredConnectionId: IntegrationConnectionId?,
    private val clock: Clock = Clock.systemUTC(),
    private val capability: io.flooow.integration.connector.ConnectorCapability = OMIE_CAPABILITY,
    private val maxPages: Int = DEFAULT_MAX_PAGES
) {
    init {
        require(maxPages in 1..100) { "Invalid Omie refresh page limit" }
    }

    fun refresh(organizationId: OrganizationId): JsonObject {
        val connectionId = configuredConnectionId
            ?: throw OmieEvidenceRefreshConfigurationUnavailableException()
        val context = controlPlane.activeCredentialContext(organizationId, connectionId)
        if (context == null || context.providerKey != OMIE_PROVIDER ||
            context.credentialKind != CredentialKind.STATIC_API_CREDENTIAL
        ) {
            throw OmieEvidenceRefreshConnectionUnavailableException()
        }

        val deadline = clock.instant().plus(REFRESH_DEADLINE)
        var invocations = 0
        var committedPages = 0
        var alreadyCommittedPages = 0
        var records = 0L

        repeat(maxPages) {
            val outcome = runtime.execute(
                ConnectorInvocation(
                    organizationId = organizationId,
                    connectionId = connectionId,
                    capability = capability,
                    invocationId = ConnectorInvocationId(UUID.randomUUID()),
                    budget = ConnectorBudget(deadline, MAX_RECORDS_PER_PAGE, MAX_RESPONSE_BYTES)
                )
            )
            invocations += 1
            when (outcome) {
                is ConnectorExecutionOutcome.Success -> {
                    if (outcome.providerKey != OMIE_PROVIDER || outcome.capability != capability) {
                        throw OmieEvidenceRefreshFailureException()
                    }
                    records += outcome.recordCount.toLong()
                    when (outcome.kind) {
                        ConnectorSuccessKind.COMMITTED -> committedPages += 1
                        ConnectorSuccessKind.ALREADY_COMMITTED -> alreadyCommittedPages += 1
                    }
                    if (outcome.exhausted) {
                        return completed(invocations, committedPages, alreadyCommittedPages, records)
                    }
                }
                is ConnectorExecutionOutcome.Failure ->
                    throw OmieEvidenceRefreshFailureException(outcome.kind)
            }
        }
        throw OmieEvidenceRefreshFailureException(ConnectorExecutionFailureKind.BUDGET_EXCEEDED)
    }

    private fun completed(
        invocations: Int,
        committedPages: Int,
        alreadyCommittedPages: Int,
        records: Long
    ) = buildJsonObject {
        put("status", "COMPLETED")
        put("connectionStatus", "ACTIVE")
        put("invocations", invocations)
        put("committedPages", committedPages)
        put("alreadyCommittedPages", alreadyCommittedPages)
        put("records", records)
        put("acquisitionGeneration", capability.value)
    }
}

internal class OmieEvidenceRefreshConfigurationUnavailableException : RuntimeException()
internal class OmieEvidenceRefreshConnectionUnavailableException : RuntimeException()
internal class OmieEvidenceRefreshFailureException(
    val failureKind: ConnectorExecutionFailureKind? = null
) : RuntimeException()
