package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.marketplace.operations.economics.provider.MarketplaceEconomicProductCostCapability
import io.flooow.marketplace.operations.identity.OmieCatalogIdentityState
import io.flooow.marketplace.operations.identity.OmieCatalogProductEvidence
import io.flooow.marketplace.operations.identity.OmieEvidenceScope
import io.flooow.marketplace.operations.identity.OmieProductCatalogEvidenceRead
import io.flooow.marketplace.operations.identity.OmieProductCatalogEvidenceReader
import io.flooow.organization.OrganizationId
import java.sql.DriverManager

/** Read-only, organization-and-connection-bound view over durable Omie product evidence. */
class PostgresOmieProductCatalogEvidenceReader(
    private val configuration: PostgresConfiguration,
    private val boundConnectionId: IntegrationConnectionId
) : OmieProductCatalogEvidenceReader {
    override fun read(
        organizationId: OrganizationId,
        connectionId: String,
        limit: Int
    ): OmieProductCatalogEvidenceRead {
        require(limit in 1..100_000)
        require(connectionId == boundConnectionId.value.toString()) {
            "Omie catalog evidence connection mismatch"
        }
        val rows = mutableListOf<CatalogRow>()
        DriverManager.getConnection(configuration.url, configuration.user, configuration.password).use { connection ->
            connection.prepareStatement(
                "SELECT source_product_ref,source_integration_ref,source_product_code," +
                    "source_location_ref,position_date,observed_at,input_progress_version,record_ordinal " +
                    "FROM integration_omie_product_cost_source_observation " +
                    "WHERE organization_id=? AND connection_id=? AND capability=? " +
                    "ORDER BY source_product_ref,position_date DESC,observed_at DESC," +
                    "input_progress_version DESC,record_ordinal LIMIT ?"
            ).use { statement ->
                statement.setObject(1, organizationId.value)
                statement.setObject(2, boundConnectionId.value)
                statement.setString(3, MarketplaceEconomicProductCostCapability.KEY.value)
                statement.setInt(4, limit)
                statement.executeQuery().use { result ->
                    while (result.next()) {
                        rows += CatalogRow(
                            result.getString("source_product_ref"),
                            result.getString("source_integration_ref"),
                            result.getString("source_product_code"),
                            result.getString("source_location_ref"),
                            result.getDate("position_date").toLocalDate().toString(),
                            result.getTimestamp("observed_at").toInstant().toString(),
                            result.getLong("input_progress_version"),
                            result.getInt("record_ordinal")
                        )
                    }
                }
            }
        }
        val scope = OmieEvidenceScope(organizationId, connectionId)
        val products = rows.groupBy { it.internalProductId }.toSortedMap().map { (internalId, observations) ->
            val integrationCodes = observations.mapNotNull { it.integrationCode }.toSortedSet()
            val displayCodes = observations.mapNotNull { it.displayCode }.toSortedSet()
            OmieCatalogProductEvidence(
                scope,
                internalId,
                integrationCodes,
                displayCodes,
                if (integrationCodes.size > 1 || displayCodes.size > 1)
                    OmieCatalogIdentityState.CONFLICT else OmieCatalogIdentityState.VALID,
                observations.mapTo(sortedSetOf()) {
                    "omie-catalog:${it.internalProductId}:${it.location}:${it.positionDate}:" +
                        "${it.observedAt}:${it.progressVersion}:${it.recordOrdinal}"
                }
            )
        }
        return OmieProductCatalogEvidenceRead(scope, products, rows.size)
    }

    private data class CatalogRow(
        val internalProductId: String,
        val integrationCode: String?,
        val displayCode: String?,
        val location: String,
        val positionDate: String,
        val observedAt: String,
        val progressVersion: Long,
        val recordOrdinal: Int
    )
}
