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
import io.flooow.marketplace.operations.economics.provider.MarketplaceEconomicOrderSourceCapability
import io.flooow.marketplace.operations.economics.provider.MercadoLivreOrderItemSourceObservation
import io.flooow.marketplace.operations.economics.provider.MercadoLivreOrderSourceRecord
import io.flooow.marketplace.operations.economics.provider.MercadoLivrePaymentSourceObservation
import io.flooow.organization.OrganizationId
import java.math.BigDecimal
import java.sql.Connection
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import kotlin.reflect.KClass

class PostgresMercadoLivreOrderSourceCommitter(
    configuration: PostgresConfiguration,
    protector: ConnectorProgressProtector,
    clock: Clock = Clock.systemUTC(),
    override val capability: ConnectorCapability = MarketplaceEconomicOrderSourceCapability.KEY
) : ConnectorPageCommitter {
    private val progress = PostgresConnectorProgressStore(configuration, protector, clock)

    override val recordType: KClass<out ConnectorRecord> =
        MercadoLivreOrderSourceRecord::class

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
        require(!exhausted && nextProgress != null) {
            "Mercado Livre live order source must remain nonterminal"
        }

        val typed = records.map {
            require(it is MercadoLivreOrderSourceRecord) {
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
            exhausted = false,
            observedAt = observedAt,
            persistRecords = { connection ->
                typed.forEachIndexed { ordinal, record ->
                    insertOrder(
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

    private fun insertOrder(
        connection: Connection,
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        capability: ConnectorCapability,
        version: Long,
        ordinal: Int,
        record: MercadoLivreOrderSourceRecord
    ) {
        connection.prepareStatement(
            "INSERT INTO integration_mercado_livre_order_source_observation (" +
                "organization_id,connection_id,capability,input_progress_version," +
                "record_ordinal,external_order_ref,provider_status,date_created," +
                "date_last_updated,date_closed,currency,total_amount,paid_amount," +
                "pack_ref,shipping_ref,observed_at" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, organizationId.value)
            statement.setObject(2, connectionId.value)
            statement.setString(3, capability.value)
            statement.setLong(4, version)
            statement.setInt(5, ordinal)
            statement.setString(6, record.externalOrderReference.encodedForPersistence())
            statement.setString(7, record.providerStatus.encodedForPersistence())
            statement.setTimestamp(8, Timestamp.from(record.dateCreated))
            statement.setTimestamp(9, Timestamp.from(record.dateLastUpdated))
            statement.setTimestamp(10, record.dateClosed?.let(Timestamp::from))
            statement.setString(11, record.currency.encodedForPersistence())
            statement.setBigDecimal(12, record.totalAmount.valueForPersistence())
            statement.setBigDecimal(13, record.paidAmount?.valueForPersistence())
            statement.setString(14, record.packReference?.encodedForPersistence())
            statement.setString(15, record.shippingReference?.encodedForPersistence())
            statement.setTimestamp(16, Timestamp.from(record.observedAt))
            check(statement.executeUpdate() == 1)
        }

        record.orderItems.forEachIndexed { itemOrdinal, item ->
            insertItem(
                connection,
                organizationId,
                connectionId,
                capability,
                version,
                ordinal,
                itemOrdinal,
                item
            )
        }

        record.payments.forEachIndexed { paymentOrdinal, payment ->
            insertPayment(
                connection,
                organizationId,
                connectionId,
                capability,
                version,
                ordinal,
                paymentOrdinal,
                payment
            )
        }
    }

    private fun insertItem(
        connection: Connection,
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        capability: ConnectorCapability,
        version: Long,
        recordOrdinal: Int,
        itemOrdinal: Int,
        item: MercadoLivreOrderItemSourceObservation
    ) {
        connection.prepareStatement(
            "INSERT INTO integration_mercado_livre_order_item_source_observation (" +
                "organization_id,connection_id,capability,input_progress_version," +
                "record_ordinal,item_ordinal,item_ref,variation_ref,seller_sku,quantity,unit_price," +
                "currency,sale_fee,gross_price" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, organizationId.value)
            statement.setObject(2, connectionId.value)
            statement.setString(3, capability.value)
            statement.setLong(4, version)
            statement.setInt(5, recordOrdinal)
            statement.setInt(6, itemOrdinal)
            statement.setString(7, item.itemReference.encodedForPersistence())
            statement.setString(8, item.variationReference?.encodedForPersistence())
            statement.setString(9, item.sellerSku?.encodedForPersistence())
            statement.setBigDecimal(10, item.quantity.valueForPersistence())
            statement.setBigDecimal(11, item.unitPrice.valueForPersistence())
            statement.setString(12, item.currency.encodedForPersistence())
            statement.setBigDecimal(13, item.saleFee?.valueForPersistence())
            statement.setBigDecimal(14, item.grossPrice?.valueForPersistence())
            check(statement.executeUpdate() == 1)
        }
    }

    private fun insertPayment(
        connection: Connection,
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        capability: ConnectorCapability,
        version: Long,
        recordOrdinal: Int,
        paymentOrdinal: Int,
        payment: MercadoLivrePaymentSourceObservation
    ) {
        connection.prepareStatement(
            "INSERT INTO integration_mercado_livre_payment_source_observation (" +
                "organization_id,connection_id,capability,input_progress_version," +
                "record_ordinal,payment_ordinal,payment_ref,provider_status," +
                "transaction_amount,currency,date_created,date_last_modified" +
                ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, organizationId.value)
            statement.setObject(2, connectionId.value)
            statement.setString(3, capability.value)
            statement.setLong(4, version)
            statement.setInt(5, recordOrdinal)
            statement.setInt(6, paymentOrdinal)
            statement.setString(7, payment.paymentReference.encodedForPersistence())
            statement.setString(8, payment.providerStatus.encodedForPersistence())
            statement.setBigDecimal(9, payment.transactionAmount.valueForPersistence())
            statement.setString(10, payment.currency.encodedForPersistence())
            statement.setTimestamp(11, payment.dateCreated?.let(Timestamp::from))
            statement.setTimestamp(12, payment.dateLastModified?.let(Timestamp::from))
            check(statement.executeUpdate() == 1)
        }
    }

    private fun validateExisting(
        connection: Connection,
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        capability: ConnectorCapability,
        version: Long,
        expected: List<MercadoLivreOrderSourceRecord>
    ) {
        connection.prepareStatement(
            "SELECT record_ordinal,external_order_ref,provider_status,date_created," +
                "date_last_updated,date_closed,currency,total_amount,paid_amount," +
                "pack_ref,shipping_ref,observed_at " +
                "FROM integration_mercado_livre_order_source_observation " +
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

                    check(result.getInt("record_ordinal") == index)
                    check(result.getString("external_order_ref") ==
                        record.externalOrderReference.encodedForPersistence())
                    check(result.getString("provider_status") ==
                        record.providerStatus.encodedForPersistence())
                    check(result.getTimestamp("date_created").toInstant() == record.dateCreated)
                    check(result.getTimestamp("date_last_updated").toInstant() == record.dateLastUpdated)
                    check(nullableInstant(result, "date_closed") == record.dateClosed)
                    check(result.getString("currency") == record.currency.encodedForPersistence())
                    check(decimalEquals(result, "total_amount", record.totalAmount.valueForPersistence()))
                    check(decimalEquals(result, "paid_amount", record.paidAmount?.valueForPersistence()))
                    check(result.getString("pack_ref") == record.packReference?.encodedForPersistence())
                    check(result.getString("shipping_ref") ==
                        record.shippingReference?.encodedForPersistence())
                    check(result.getTimestamp("observed_at").toInstant() == record.observedAt)

                    validateItems(
                        connection,
                        organizationId,
                        connectionId,
                        capability,
                        version,
                        index,
                        record.orderItems
                    )
                    validatePayments(
                        connection,
                        organizationId,
                        connectionId,
                        capability,
                        version,
                        index,
                        record.payments
                    )
                    index++
                }
                check(index == expected.size) { "Provider page record integrity failure" }
            }
        }
    }

    private fun validateItems(
        connection: Connection,
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        capability: ConnectorCapability,
        version: Long,
        recordOrdinal: Int,
        expected: List<MercadoLivreOrderItemSourceObservation>
    ) {
        connection.prepareStatement(
            "SELECT item_ordinal,item_ref,variation_ref,seller_sku,quantity,unit_price,currency," +
                "sale_fee,gross_price FROM integration_mercado_livre_order_item_source_observation " +
                "WHERE organization_id=? AND connection_id=? AND capability=? " +
                "AND input_progress_version=? AND record_ordinal=? ORDER BY item_ordinal"
        ).use { statement ->
            statement.setObject(1, organizationId.value)
            statement.setObject(2, connectionId.value)
            statement.setString(3, capability.value)
            statement.setLong(4, version)
            statement.setInt(5, recordOrdinal)
            statement.executeQuery().use { result ->
                var index = 0
                while (result.next()) {
                    check(index < expected.size) { "Provider item integrity failure" }
                    val item = expected[index]
                    check(result.getInt("item_ordinal") == index)
                    check(result.getString("item_ref") == item.itemReference.encodedForPersistence())
                    check(result.getString("variation_ref") ==
                        item.variationReference?.encodedForPersistence())
                    check(result.getString("seller_sku") == item.sellerSku?.encodedForPersistence())
                    check(decimalEquals(result, "quantity", item.quantity.valueForPersistence()))
                    check(decimalEquals(result, "unit_price", item.unitPrice.valueForPersistence()))
                    check(result.getString("currency") == item.currency.encodedForPersistence())
                    check(decimalEquals(result, "sale_fee", item.saleFee?.valueForPersistence()))
                    check(decimalEquals(result, "gross_price", item.grossPrice?.valueForPersistence()))
                    index++
                }
                check(index == expected.size) { "Provider item integrity failure" }
            }
        }
    }

    private fun validatePayments(
        connection: Connection,
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        capability: ConnectorCapability,
        version: Long,
        recordOrdinal: Int,
        expected: List<MercadoLivrePaymentSourceObservation>
    ) {
        connection.prepareStatement(
            "SELECT payment_ordinal,payment_ref,provider_status,transaction_amount,currency," +
                "date_created,date_last_modified " +
                "FROM integration_mercado_livre_payment_source_observation " +
                "WHERE organization_id=? AND connection_id=? AND capability=? " +
                "AND input_progress_version=? AND record_ordinal=? ORDER BY payment_ordinal"
        ).use { statement ->
            statement.setObject(1, organizationId.value)
            statement.setObject(2, connectionId.value)
            statement.setString(3, capability.value)
            statement.setLong(4, version)
            statement.setInt(5, recordOrdinal)
            statement.executeQuery().use { result ->
                var index = 0
                while (result.next()) {
                    check(index < expected.size) { "Provider payment integrity failure" }
                    val payment = expected[index]
                    check(result.getInt("payment_ordinal") == index)
                    check(result.getString("payment_ref") ==
                        payment.paymentReference.encodedForPersistence())
                    check(result.getString("provider_status") ==
                        payment.providerStatus.encodedForPersistence())
                    check(
                        decimalEquals(
                            result,
                            "transaction_amount",
                            payment.transactionAmount.valueForPersistence()
                        )
                    )
                    check(result.getString("currency") ==
                        payment.currency.encodedForPersistence())
                    check(nullableInstant(result, "date_created") == payment.dateCreated)
                    check(nullableInstant(result, "date_last_modified") == payment.dateLastModified)
                    index++
                }
                check(index == expected.size) { "Provider payment integrity failure" }
            }
        }
    }

    private fun nullableInstant(result: ResultSet, column: String): Instant? =
        result.getTimestamp(column)?.toInstant()

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
