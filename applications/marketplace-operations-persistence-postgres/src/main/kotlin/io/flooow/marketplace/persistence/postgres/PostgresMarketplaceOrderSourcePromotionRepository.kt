package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderIdentityResolution
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderOccurrencePromotionOutcome
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderOccurrencePromotionWriteResult
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderSourceKey
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderSourcePendingResult
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderSourcePromotionCandidate
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderSourcePromotionContract
import io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderSourcePromotionRepository
import io.flooow.organization.OrganizationId
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class PostgresMarketplaceOrderSourcePromotionRepository(
    private val configuration: PostgresConfiguration
) : MarketplaceOrderSourcePromotionRepository,
    io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderRevenuePromotionRepository {
    override fun pending(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        limit: Int
    ): MarketplaceOrderSourcePendingResult {
        if (limit !in 1..1_000) {
            return MarketplaceOrderSourcePendingResult.Unavailable
        }

        return try {
            connection().use { connection ->
                connection.prepareStatement(
                    "SELECT source.input_progress_version,source.record_ordinal," +
                        "source.external_order_ref,source.currency,source.date_created," +
                        "source.observed_at " +
                        "FROM integration_mercado_livre_order_source_observation source " +
                        "LEFT JOIN marketplace_order_occurrence_source_promotion promoted " +
                        "ON promoted.organization_id=source.organization_id " +
                        "AND promoted.source_connection_id=source.connection_id " +
                        "AND promoted.source_capability=source.capability " +
                        "AND promoted.source_input_progress_version=source.input_progress_version " +
                        "AND promoted.source_record_ordinal=source.record_ordinal " +
                        "WHERE source.organization_id=? AND source.connection_id=? " +
                        "AND source.capability=? " +
                        "AND promoted.organization_id IS NULL " +
                        "ORDER BY source.input_progress_version,source.record_ordinal " +
                        "LIMIT ?"
                ).use { statement ->
                    statement.setObject(1, organizationId.value)
                    statement.setObject(2, connectionId.value)
                    statement.setString(3, MarketplaceOrderSourcePromotionContract.CAPABILITY)
                    statement.setInt(4, limit)

                    statement.executeQuery().use { result ->
                        val candidates = mutableListOf<MarketplaceOrderSourcePromotionCandidate>()
                        while (result.next()) {
                            candidates += MarketplaceOrderSourcePromotionCandidate(
                                sourceKey = MarketplaceOrderSourceKey(
                                    organizationId = organizationId,
                                    connectionId = connectionId,
                                    capability = MarketplaceOrderSourcePromotionContract.CAPABILITY,
                                    inputProgressVersion =
                                        result.getLong("input_progress_version"),
                                    recordOrdinal = result.getInt("record_ordinal")
                                ),
                                externalOrderId = MarketplaceExternalOrderId(
                                    result.getString("external_order_ref")
                                ),
                                currency = MarketplaceCurrency(
                                    result.getString("currency").trimEnd()
                                ),
                                dateCreated = result.getTimestamp("date_created").toInstant(),
                                observedAt = result.getTimestamp("observed_at").toInstant()
                            )
                        }
                        MarketplaceOrderSourcePendingResult.Available(candidates)
                    }
                }
            }
        } catch (_: Exception) {
            MarketplaceOrderSourcePendingResult.Unavailable
        }
    }

    override fun resolveOrAllocate(
        candidate: MarketplaceOrderSourcePromotionCandidate,
        proposedOrderId: MarketplaceOrderId,
        allocatedAt: Instant
    ): MarketplaceOrderIdentityResolution {
        return try {
            connection().use { connection ->
                connection.autoCommit = false
                try {
                    if (!activeOrganization(connection, candidate.sourceKey.organizationId)) {
                        connection.rollback()
                        return MarketplaceOrderIdentityResolution.Unavailable
                    }

                    if (!sourceMatches(connection, candidate)) {
                        connection.rollback()
                        return MarketplaceOrderIdentityResolution.Unavailable
                    }

                    val inserted = connection.prepareStatement(
                        "INSERT INTO marketplace_order_identity_registry (" +
                            "organization_id,marketplace_key,external_order_id," +
                            "marketplace_order_id,currency,allocated_at," +
                            "first_source_connection_id,first_source_capability," +
                            "first_source_input_progress_version,first_source_record_ordinal" +
                            ") VALUES (?,?,?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING"
                    ).use { statement ->
                        statement.setObject(1, candidate.sourceKey.organizationId.value)
                        statement.setString(
                            2,
                            MarketplaceOrderSourcePromotionContract.MARKETPLACE.value
                        )
                        statement.setString(3, candidate.externalOrderId.value)
                        statement.setObject(4, proposedOrderId.value)
                        statement.setString(5, candidate.currency.code)
                        statement.setTimestamp(6, Timestamp.from(allocatedAt))
                        statement.setObject(7, candidate.sourceKey.connectionId.value)
                        statement.setString(8, candidate.sourceKey.capability)
                        statement.setLong(9, candidate.sourceKey.inputProgressVersion)
                        statement.setInt(10, candidate.sourceKey.recordOrdinal)
                        statement.executeUpdate() == 1
                    }

                    val stored = connection.prepareStatement(
                        "SELECT marketplace_order_id,currency " +
                            "FROM marketplace_order_identity_registry " +
                            "WHERE organization_id=? AND marketplace_key=? " +
                            "AND external_order_id=?"
                    ).use { statement ->
                        statement.setObject(1, candidate.sourceKey.organizationId.value)
                        statement.setString(
                            2,
                            MarketplaceOrderSourcePromotionContract.MARKETPLACE.value
                        )
                        statement.setString(3, candidate.externalOrderId.value)
                        statement.executeQuery().use { result ->
                            if (!result.next()) null else StoredIdentity(
                                MarketplaceOrderId(
                                    result.getObject("marketplace_order_id", UUID::class.java)
                                ),
                                MarketplaceCurrency(result.getString("currency").trimEnd())
                            )
                        }
                    }

                    if (stored == null) {
                        connection.rollback()
                        return MarketplaceOrderIdentityResolution.Unavailable
                    }

                    connection.commit()
                    if (stored.currency == candidate.currency) {
                        MarketplaceOrderIdentityResolution.Resolved(
                            stored.orderId,
                            allocatedNow = inserted
                        )
                    } else {
                        MarketplaceOrderIdentityResolution.Conflict(stored.orderId)
                    }
                } catch (error: Exception) {
                    connection.rollback()
                    throw error
                }
            }
        } catch (_: Exception) {
            MarketplaceOrderIdentityResolution.Unavailable
        }
    }

    override fun markTerminal(
        candidate: MarketplaceOrderSourcePromotionCandidate,
        orderId: MarketplaceOrderId,
        outcome: MarketplaceOrderOccurrencePromotionOutcome,
        promotedAt: Instant
    ): MarketplaceOrderOccurrencePromotionWriteResult {
        return try {
            connection().use { connection ->
                connection.autoCommit = false
                try {
                    val inserted = connection.prepareStatement(
                        "INSERT INTO marketplace_order_occurrence_source_promotion (" +
                            "organization_id,source_connection_id,source_capability," +
                            "source_input_progress_version,source_record_ordinal," +
                            "marketplace_order_id,outcome,promoted_at" +
                            ") VALUES (?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING"
                    ).use { statement ->
                        statement.setObject(1, candidate.sourceKey.organizationId.value)
                        statement.setObject(2, candidate.sourceKey.connectionId.value)
                        statement.setString(3, candidate.sourceKey.capability)
                        statement.setLong(4, candidate.sourceKey.inputProgressVersion)
                        statement.setInt(5, candidate.sourceKey.recordOrdinal)
                        statement.setObject(6, orderId.value)
                        statement.setString(7, outcome.name)
                        statement.setTimestamp(8, Timestamp.from(promotedAt))
                        statement.executeUpdate() == 1
                    }

                    if (inserted) {
                        connection.commit()
                        return MarketplaceOrderOccurrencePromotionWriteResult.APPLIED
                    }

                    val existing = connection.prepareStatement(
                        "SELECT marketplace_order_id,outcome " +
                            "FROM marketplace_order_occurrence_source_promotion " +
                            "WHERE organization_id=? AND source_connection_id=? " +
                            "AND source_capability=? AND source_input_progress_version=? " +
                            "AND source_record_ordinal=?"
                    ).use { statement ->
                        statement.setObject(1, candidate.sourceKey.organizationId.value)
                        statement.setObject(2, candidate.sourceKey.connectionId.value)
                        statement.setString(3, candidate.sourceKey.capability)
                        statement.setLong(4, candidate.sourceKey.inputProgressVersion)
                        statement.setInt(5, candidate.sourceKey.recordOrdinal)
                        statement.executeQuery().use { result ->
                            if (!result.next()) null else StoredTerminal(
                                MarketplaceOrderId(
                                    result.getObject("marketplace_order_id", UUID::class.java)
                                ),
                                MarketplaceOrderOccurrencePromotionOutcome.valueOf(
                                    result.getString("outcome")
                                )
                            )
                        }
                    }

                    connection.commit()
                    if (existing == StoredTerminal(orderId, outcome)) {
                        MarketplaceOrderOccurrencePromotionWriteResult.ALREADY_APPLIED
                    } else {
                        MarketplaceOrderOccurrencePromotionWriteResult.CONFLICT
                    }
                } catch (error: Exception) {
                    connection.rollback()
                    throw error
                }
            }
        } catch (_: Exception) {
            MarketplaceOrderOccurrencePromotionWriteResult.UNAVAILABLE
        }
    }

    override fun pendingRevenue(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        limit: Int
    ): io.flooow.marketplace.operations.economics.promotion.MarketplaceOrderRevenuePendingResult {
        if (limit !in 1..1_000) {
            return io.flooow.marketplace.operations.economics.promotion
                .MarketplaceOrderRevenuePendingResult.Unavailable
        }

        return try {
            connection().use { connection ->
                connection.prepareStatement(
                    "SELECT source.input_progress_version,source.record_ordinal," +
                        "source.external_order_ref,source.currency AS source_currency," +
                        "source.total_amount,source.date_closed,source.observed_at," +
                        "identity.marketplace_order_id,identity.currency AS identity_currency " +
                        "FROM integration_mercado_livre_order_source_observation source " +
                        "JOIN marketplace_order_identity_registry identity " +
                        "ON identity.organization_id=source.organization_id " +
                        "AND identity.marketplace_key=? " +
                        "AND identity.external_order_id=source.external_order_ref " +
                        "LEFT JOIN marketplace_order_revenue_source_promotion promoted " +
                        "ON promoted.organization_id=source.organization_id " +
                        "AND promoted.source_connection_id=source.connection_id " +
                        "AND promoted.source_capability=source.capability " +
                        "AND promoted.source_input_progress_version=source.input_progress_version " +
                        "AND promoted.source_record_ordinal=source.record_ordinal " +
                        "WHERE source.organization_id=? AND source.connection_id=? " +
                        "AND source.capability=? AND source.date_closed IS NOT NULL " +
                        "AND promoted.organization_id IS NULL " +
                        "ORDER BY source.input_progress_version,source.record_ordinal " +
                        "LIMIT ?"
                ).use { statement ->
                    statement.setString(
                        1,
                        MarketplaceOrderSourcePromotionContract.MARKETPLACE.value
                    )
                    statement.setObject(2, organizationId.value)
                    statement.setObject(3, connectionId.value)
                    statement.setString(4, MarketplaceOrderSourcePromotionContract.CAPABILITY)
                    statement.setInt(5, limit)

                    statement.executeQuery().use { result ->
                        val candidates = mutableListOf<
                            io.flooow.marketplace.operations.economics.promotion
                                .MarketplaceOrderRevenuePromotionCandidate
                        >()

                        while (result.next()) {
                            candidates +=
                                io.flooow.marketplace.operations.economics.promotion
                                    .MarketplaceOrderRevenuePromotionCandidate(
                                        sourceKey = MarketplaceOrderSourceKey(
                                            organizationId = organizationId,
                                            connectionId = connectionId,
                                            capability =
                                                MarketplaceOrderSourcePromotionContract.CAPABILITY,
                                            inputProgressVersion =
                                                result.getLong("input_progress_version"),
                                            recordOrdinal =
                                                result.getInt("record_ordinal")
                                        ),
                                        externalOrderId = MarketplaceExternalOrderId(
                                            result.getString("external_order_ref")
                                        ),
                                        sourceCurrency = MarketplaceCurrency(
                                            result.getString("source_currency").trimEnd()
                                        ),
                                        identityCurrency = MarketplaceCurrency(
                                            result.getString("identity_currency").trimEnd()
                                        ),
                                        orderId = MarketplaceOrderId(
                                            result.getObject(
                                                "marketplace_order_id",
                                                UUID::class.java
                                            )
                                        ),
                                        totalAmount = result.getBigDecimal("total_amount"),
                                        dateClosed =
                                            result.getTimestamp("date_closed").toInstant(),
                                        observedAt =
                                            result.getTimestamp("observed_at").toInstant()
                                    )
                        }

                        io.flooow.marketplace.operations.economics.promotion
                            .MarketplaceOrderRevenuePendingResult.Available(candidates)
                    }
                }
            }
        } catch (_: Exception) {
            io.flooow.marketplace.operations.economics.promotion
                .MarketplaceOrderRevenuePendingResult.Unavailable
        }
    }

    override fun markRevenueTerminal(
        candidate:
            io.flooow.marketplace.operations.economics.promotion
                .MarketplaceOrderRevenuePromotionCandidate,
        outcome:
            io.flooow.marketplace.operations.economics.promotion
                .MarketplaceOrderRevenuePromotionOutcome,
        promotedAt: Instant
    ): io.flooow.marketplace.operations.economics.promotion
        .MarketplaceOrderRevenuePromotionWriteResult {
        return try {
            connection().use { connection ->
                connection.autoCommit = false
                try {
                    val inserted = connection.prepareStatement(
                        "INSERT INTO marketplace_order_revenue_source_promotion (" +
                            "organization_id,source_connection_id,source_capability," +
                            "source_input_progress_version,source_record_ordinal," +
                            "marketplace_order_id,outcome,promoted_at" +
                            ") VALUES (?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING"
                    ).use { statement ->
                        statement.setObject(1, candidate.sourceKey.organizationId.value)
                        statement.setObject(2, candidate.sourceKey.connectionId.value)
                        statement.setString(3, candidate.sourceKey.capability)
                        statement.setLong(4, candidate.sourceKey.inputProgressVersion)
                        statement.setInt(5, candidate.sourceKey.recordOrdinal)
                        statement.setObject(6, candidate.orderId.value)
                        statement.setString(7, outcome.name)
                        statement.setTimestamp(8, Timestamp.from(promotedAt))
                        statement.executeUpdate() == 1
                    }

                    if (inserted) {
                        connection.commit()
                        return io.flooow.marketplace.operations.economics.promotion
                            .MarketplaceOrderRevenuePromotionWriteResult.APPLIED
                    }

                    val existing = connection.prepareStatement(
                        "SELECT marketplace_order_id,outcome " +
                            "FROM marketplace_order_revenue_source_promotion " +
                            "WHERE organization_id=? AND source_connection_id=? " +
                            "AND source_capability=? AND source_input_progress_version=? " +
                            "AND source_record_ordinal=?"
                    ).use { statement ->
                        statement.setObject(1, candidate.sourceKey.organizationId.value)
                        statement.setObject(2, candidate.sourceKey.connectionId.value)
                        statement.setString(3, candidate.sourceKey.capability)
                        statement.setLong(4, candidate.sourceKey.inputProgressVersion)
                        statement.setInt(5, candidate.sourceKey.recordOrdinal)
                        statement.executeQuery().use { result ->
                            if (!result.next()) {
                                null
                            } else {
                                MarketplaceOrderId(
                                    result.getObject("marketplace_order_id", UUID::class.java)
                                ) to
                                    io.flooow.marketplace.operations.economics.promotion
                                        .MarketplaceOrderRevenuePromotionOutcome.valueOf(
                                            result.getString("outcome")
                                        )
                            }
                        }
                    }

                    connection.commit()
                    if (existing == (candidate.orderId to outcome)) {
                        io.flooow.marketplace.operations.economics.promotion
                            .MarketplaceOrderRevenuePromotionWriteResult.ALREADY_APPLIED
                    } else {
                        io.flooow.marketplace.operations.economics.promotion
                            .MarketplaceOrderRevenuePromotionWriteResult.CONFLICT
                    }
                } catch (error: Exception) {
                    connection.rollback()
                    throw error
                }
            }
        } catch (_: Exception) {
            io.flooow.marketplace.operations.economics.promotion
                .MarketplaceOrderRevenuePromotionWriteResult.UNAVAILABLE
        }
    }
    private fun activeOrganization(
        connection: Connection,
        organizationId: OrganizationId
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_organization " +
            "WHERE organization_id=? AND status='ACTIVE'"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.executeQuery().use { it.next() }
    }

    private fun sourceMatches(
        connection: Connection,
        candidate: MarketplaceOrderSourcePromotionCandidate
    ): Boolean = connection.prepareStatement(
        "SELECT external_order_ref,currency,date_created,observed_at " +
            "FROM integration_mercado_livre_order_source_observation " +
            "WHERE organization_id=? AND connection_id=? AND capability=? " +
            "AND input_progress_version=? AND record_ordinal=?"
    ).use { statement ->
        statement.setObject(1, candidate.sourceKey.organizationId.value)
        statement.setObject(2, candidate.sourceKey.connectionId.value)
        statement.setString(3, candidate.sourceKey.capability)
        statement.setLong(4, candidate.sourceKey.inputProgressVersion)
        statement.setInt(5, candidate.sourceKey.recordOrdinal)
        statement.executeQuery().use { result ->
            result.next() &&
                result.getString("external_order_ref") == candidate.externalOrderId.value &&
                result.getString("currency").trimEnd() == candidate.currency.code &&
                result.getTimestamp("date_created").toInstant() == candidate.dateCreated &&
                result.getTimestamp("observed_at").toInstant() == candidate.observedAt
        }
    }

    private fun connection(): Connection = DriverManager.getConnection(
        configuration.url,
        configuration.user,
        configuration.password
    )

    private data class StoredIdentity(
        val orderId: MarketplaceOrderId,
        val currency: MarketplaceCurrency
    )

    private data class StoredTerminal(
        val orderId: MarketplaceOrderId,
        val outcome: MarketplaceOrderOccurrencePromotionOutcome
    )
}