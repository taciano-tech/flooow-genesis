package io.flooow.marketplace.operations.economics.provider.omie

import io.flooow.integration.connector.ConnectorAdapterFailure
import io.flooow.integration.connector.ConnectorAdapterFailureKind
import io.flooow.integration.connector.ConnectorBudget
import io.flooow.integration.connector.ConnectorCancellation
import io.flooow.integration.connector.ConnectorCapability
import io.flooow.integration.connector.ConnectorDescriptor
import io.flooow.integration.connector.ConnectorProgress
import io.flooow.integration.connector.ConnectorReadResult
import io.flooow.integration.connector.PullConnector
import io.flooow.integration.control.ProviderKey

/** One Omie provider registration that preserves capability-specific adapters. */
class OmieProviderConnector(
    private val delegates: Collection<PullConnector> = listOf(
        OmieTransactionEvidenceConnector(),
        OmieEconomicEvidenceConnector()
    )
) : PullConnector {
    private val byCapability = delegates
        .flatMap { delegate -> delegate.descriptor.definitions.map { it.capability to delegate } }
        .toMap()

    override val descriptor = ConnectorDescriptor(
        OMIE_PROVIDER,
        delegates.flatMap { it.descriptor.definitions }
    )

    init {
        require(delegates.isNotEmpty()) { "Omie connector requires delegates" }
        require(delegates.all { it.descriptor.providerKey == OMIE_PROVIDER }) {
            "Omie connector delegate provider mismatch"
        }
        require(byCapability.size == delegates.sumOf { it.descriptor.definitions.size }) {
            "Omie connector capability is registered twice"
        }
    }

    override fun readPage(
        capability: ConnectorCapability,
        credentialBytes: ByteArray,
        currentProgress: ConnectorProgress?,
        budget: ConnectorBudget,
        cancellation: ConnectorCancellation
    ): ConnectorReadResult = byCapability[capability]?.readPage(
        capability,
        credentialBytes,
        currentProgress,
        budget,
        cancellation
    ) ?: ConnectorReadResult.Failed(
        ConnectorAdapterFailure.of(ConnectorAdapterFailureKind.REMOTE_PERMANENT)
    )

    private companion object {
        val OMIE_PROVIDER = ProviderKey.of("omie")
    }
}
