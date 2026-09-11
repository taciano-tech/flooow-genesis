package io.flooow.marketplace.operations.economics.provider.omie

import io.flooow.integration.connector.ConnectorAdapterFailureKind
import io.flooow.integration.connector.ConnectorBudget
import io.flooow.integration.connector.ConnectorCancellation
import io.flooow.integration.connector.ConnectorCapability
import io.flooow.integration.connector.ConnectorDescriptor
import io.flooow.integration.connector.ConnectorPage
import io.flooow.integration.connector.ConnectorProgress
import io.flooow.integration.connector.ConnectorReadResult
import io.flooow.integration.connector.ConnectorRecordDefinition
import io.flooow.integration.connector.PullConnector
import io.flooow.integration.control.ProviderKey
import io.flooow.marketplace.operations.economics.provider.MarketplaceEconomicProductCostCapability
import io.flooow.marketplace.operations.economics.provider.OmieProductCostSourceRecord
import io.flooow.marketplace.operations.economics.provider.OmieTransactionEvidenceCapability
import io.flooow.marketplace.operations.economics.provider.OmieTransactionEvidenceRecord
import java.time.Instant
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class OmieProviderConnectorTest {
    @Test
    fun `registers one Omie provider and delegates each capability exactly`() {
        val transactions = StubConnector(
            OmieTransactionEvidenceCapability.KEY,
            OmieTransactionEvidenceRecord::class
        )
        val productCost = StubConnector(
            MarketplaceEconomicProductCostCapability.KEY,
            OmieProductCostSourceRecord::class
        )
        val connector = OmieProviderConnector(listOf(transactions, productCost))

        assertEquals(ProviderKey.of("omie"), connector.descriptor.providerKey)
        assertEquals(
            setOf(
                OmieTransactionEvidenceCapability.KEY,
                MarketplaceEconomicProductCostCapability.KEY
            ),
            connector.descriptor.definitions.mapTo(mutableSetOf()) { it.capability }
        )

        connector.readPage(
            MarketplaceEconomicProductCostCapability.KEY,
            byteArrayOf(1),
            null,
            ConnectorBudget(Instant.parse("2026-09-11T12:01:00Z"), 100, 1024),
            ConnectorCancellation.NEVER
        )

        assertEquals(0, transactions.invocations)
        assertEquals(1, productCost.invocations)
    }

    @Test
    fun `unsupported capability fails closed`() {
        val connector = OmieProviderConnector(
            listOf(
                StubConnector(
                    MarketplaceEconomicProductCostCapability.KEY,
                    OmieProductCostSourceRecord::class
                )
            )
        )

        val result = connector.readPage(
            ConnectorCapability.of("unsupported"),
            byteArrayOf(1),
            null,
            ConnectorBudget(Instant.parse("2026-09-11T12:01:00Z"), 100, 1024),
            ConnectorCancellation.NEVER
        )

        assertEquals(
            ConnectorAdapterFailureKind.REMOTE_PERMANENT,
            assertIs<ConnectorReadResult.Failed>(result).failure.kind
        )
    }

    private class StubConnector(
        capability: ConnectorCapability,
        recordType: KClass<out io.flooow.integration.connector.ConnectorRecord>
    ) : PullConnector {
        var invocations = 0
        override val descriptor = ConnectorDescriptor(
            ProviderKey.of("omie"),
            listOf(ConnectorRecordDefinition(capability, recordType))
        )

        override fun readPage(
            capability: ConnectorCapability,
            credentialBytes: ByteArray,
            currentProgress: ConnectorProgress?,
            budget: ConnectorBudget,
            cancellation: ConnectorCancellation
        ): ConnectorReadResult {
            invocations += 1
            return ConnectorReadResult.Page(
                ConnectorPage(emptyList(), null, Instant.parse("2026-09-11T12:00:00Z"), true, 0)
            )
        }
    }
}
