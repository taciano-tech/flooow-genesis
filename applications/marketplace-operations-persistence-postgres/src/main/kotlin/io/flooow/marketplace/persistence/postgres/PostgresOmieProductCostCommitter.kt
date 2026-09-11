package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.connector.ConnectorCapability
import io.flooow.integration.connector.ConnectorPageCommitKey
import io.flooow.integration.connector.ConnectorPageCommitResult
import io.flooow.integration.connector.ConnectorPageCommitter
import io.flooow.integration.connector.ConnectorProgress
import io.flooow.integration.connector.ConnectorProgressProtector
import io.flooow.integration.connector.ConnectorRecord
import io.flooow.integration.connector.VersionedConnectorProgress
import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.marketplace.operations.economics.provider.MarketplaceEconomicProductCostCapability
import io.flooow.marketplace.operations.economics.provider.OmieProductCostSourceRecord
import io.flooow.organization.OrganizationId
import java.math.BigDecimal
import java.sql.Connection
import java.sql.Date
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import kotlin.reflect.KClass

class PostgresOmieProductCostCommitter(
    configuration: PostgresConfiguration,
    protector: ConnectorProgressProtector,
    clock: Clock = Clock.systemUTC()
) : ConnectorPageCommitter {
    private val progress = PostgresConnectorProgressStore(configuration, protector, clock)

    override val capability: ConnectorCapability =
        MarketplaceEconomicProductCostCapability.KEY

    override val recordType: KClass<out ConnectorRecord> =
        OmieProductCostSourceRecord::class

    override fun load(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        capability: ConnectorCapability
    ): VersionedConnectorProgress {
        require(capability == this.capability) { "Connector capability unavailable" }
        return progress.load(organizationId, connectionId, capability)
    }

    override fun commit(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        capability: ConnectorCapability,
        expectedProgressVersion: Long,
        pageCommitKey: ConnectorPageCommitKey,
        records: List<ConnectorRecord>,
        nextProgress: ConnectorProgress?,
        exhausted: Boolean,
        observedAt: Instant
    ): ConnectorPageCommitResult {
        require(capability == this.capability) { "Connector capability unavailable" }

        val typed = records.map {
            require(it is OmieProductCostSourceRecord) {
                "Connector record type unavailable"
            }
            it
        }
        require(typed.size <= 1_000) { "Connector page exceeds record limit" }
        require(typed.all { it.observedAt == observedAt }) {
            "Provider observation time must match connector page"
        }

        return progress.commitPage(
            organizationId,
            connectionId,
            capability,
            expectedProgressVersion,
            pageCommitKey,
            typed.size,
            nextProgress,
            exhausted,
            observedAt,
            persistRecords = { connection ->
                typed.forEachIndexed { ordinal, record ->
                    insertRecord(
                        connection,
                        organizationId,
                        connectionId,
                        capability,
                        expectedProgressVersion,
                        ordinal,
                        record
                    )
                }
            },
            validateExistingRecords = { connection ->
                validateExisting(
                    connection,
                    organizationId,
                    connectionId,
                    capability,
                    expectedProgressVersion,
                    typed
                )
            }
        )
    }

    private fun insertRecord(
        connection: Connection,
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        capability: ConnectorCapability,
        version: Long,
        ordinal: Int,
        record: OmieProductCostSourceRecord
    ) {
        connection.prepareStatement(
            "INSERT INTO integration_omie_product_cost_source_observation (" +
                "organization_id,connection_id,capability,input_progress_version," +
                "record_ordinal,source_product_ref,source_integration_ref," +
                "source_product_code,source_location_ref,unit_cmc,stock_balance," +
                "physical_stock,reserved_stock,position_date,observed_at" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, organizationId.value)
            statement.setObject(2, connectionId.value)
            statement.setString(3, capability.value)
            statement.setLong(4, version)
            statement.setInt(5, ordinal)
            statement.setString(6, record.productReference.encodedForPersistence())
            statement.setString(7, record.integrationReference?.encodedForPersistence())
            statement.setString(8, record.displayedProductCode?.encodedForPersistence())
            statement.setString(9, record.locationReference.encodedForPersistence())
            statement.setBigDecimal(10, record.unitCmc?.valueForPersistence())
            statement.setBigDecimal(11, record.stockBalance?.valueForPersistence())
            statement.setBigDecimal(12, record.physicalStock?.valueForPersistence())
            statement.setBigDecimal(13, record.reservedStock?.valueForPersistence())
            statement.setDate(14, Date.valueOf(record.positionDate))
            statement.setTimestamp(15, Timestamp.from(record.observedAt))
            check(statement.executeUpdate() == 1)
        }
    }

    private fun validateExisting(
        connection: Connection,
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        capability: ConnectorCapability,
        version: Long,
        expected: List<OmieProductCostSourceRecord>
    ) {
        connection.prepareStatement(
            "SELECT record_ordinal,source_product_ref,source_integration_ref," +
                "source_product_code,source_location_ref,unit_cmc,stock_balance," +
                "physical_stock,reserved_stock,position_date,observed_at " +
                "FROM integration_omie_product_cost_source_observation " +
                "WHERE organization_id=? AND connection_id=? AND capability=? " +
                "AND input_progress_version=? ORDER BY record_ordinal"
        ).use { statement ->
            statement.setObject(1, organizationId.value)
            statement.setObject(2, connectionId.value)
            statement.setString(3, capability.value)
            statement.setLong(4, version)
            statement.executeQuery().use { result ->
                var index = 0
                while (result.next()) {
                    check(index < expected.size) { "Provider page record integrity failure" }
                    val record = expected[index]
                    check(result.getInt("record_ordinal") == index) {
                        "Provider page record integrity failure"
                    }
                    check(result.getString("source_product_ref") ==
                        record.productReference.encodedForPersistence()) {
                        "Provider page record integrity failure"
                    }
                    check(result.getString("source_integration_ref") ==
                        record.integrationReference?.encodedForPersistence()) {
                        "Provider page record integrity failure"
                    }
                    check(result.getString("source_product_code") ==
                        record.displayedProductCode?.encodedForPersistence()) {
                        "Provider page record integrity failure"
                    }
                    check(result.getString("source_location_ref") ==
                        record.locationReference.encodedForPersistence()) {
                        "Provider page record integrity failure"
                    }
                    check(decimalEquals(result, "unit_cmc", record.unitCmc?.valueForPersistence())) {
                        "Provider page record integrity failure"
                    }
                    check(
                        decimalEquals(
                            result,
                            "stock_balance",
                            record.stockBalance?.valueForPersistence()
                        )
                    ) { "Provider page record integrity failure" }
                    check(
                        decimalEquals(
                            result,
                            "physical_stock",
                            record.physicalStock?.valueForPersistence()
                        )
                    ) { "Provider page record integrity failure" }
                    check(
                        decimalEquals(
                            result,
                            "reserved_stock",
                            record.reservedStock?.valueForPersistence()
                        )
                    ) { "Provider page record integrity failure" }
                    check(result.getDate("position_date").toLocalDate() == record.positionDate) {
                        "Provider page record integrity failure"
                    }
                    check(result.getTimestamp("observed_at").toInstant() == record.observedAt) {
                        "Provider page record integrity failure"
                    }
                    index++
                }
                check(index == expected.size) { "Provider page record integrity failure" }
            }
        }
    }

    private fun decimalEquals(
        result: ResultSet,
        column: String,
        expected: BigDecimal?
    ): Boolean {
        val actual = result.getBigDecimal(column)
        return when {
            actual == null && expected == null -> true
            actual == null || expected == null -> false
            else -> actual.compareTo(expected) == 0
        }
    }
}