package io.flooow.research.exp0006

import io.flooow.marketplace.persistence.postgres.PostgresConfiguration
import io.flooow.organization.OrganizationId
import java.sql.DriverManager
import java.util.UUID

class ExperimentalPendingDiscoveryQueries(
    private val configuration: PostgresConfiguration
) {
    fun queryA(projectionName: ExperimentalProjectionName, limit: Int): List<OrganizationId> =
        execute(QUERY_A, projectionName, limit)

    fun queryB(projectionName: ExperimentalProjectionName, limit: Int): List<OrganizationId> =
        execute(QUERY_B, projectionName, limit)

    fun queryC(projectionName: ExperimentalProjectionName, limit: Int): List<OrganizationId> =
        execute(QUERY_C, projectionName, limit)

    fun explainA(projectionName: ExperimentalProjectionName, limit: Int): List<String> =
        explain(QUERY_A, projectionName, limit)

    fun explainB(projectionName: ExperimentalProjectionName, limit: Int): List<String> =
        explain(QUERY_B, projectionName, limit)

    fun explainC(projectionName: ExperimentalProjectionName, limit: Int): List<String> =
        explain(QUERY_C, projectionName, limit)

    private fun execute(
        sql: String,
        projectionName: ExperimentalProjectionName,
        limit: Int
    ): List<OrganizationId> {
        require(limit in 1..10_000)
        return DriverManager.getConnection(
            configuration.url,
            configuration.user,
            configuration.password
        ).use { connection ->
            connection.prepareStatement(sql).use { statement ->
                statement.setString(1, projectionName.valueForPersistence())
                statement.setInt(2, limit)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(OrganizationId(result.getObject("organization_id", UUID::class.java)))
                        }
                    }
                }
            }
        }
    }

    private fun explain(
        sql: String,
        projectionName: ExperimentalProjectionName,
        limit: Int
    ): List<String> {
        require(limit in 1..10_000)
        return DriverManager.getConnection(
            configuration.url,
            configuration.user,
            configuration.password
        ).use { connection ->
            connection.prepareStatement("EXPLAIN (ANALYZE, BUFFERS) $sql").use { statement ->
                statement.setString(1, projectionName.valueForPersistence())
                statement.setInt(2, limit)
                statement.executeQuery().use { result ->
                    buildList { while (result.next()) add(result.getString(1)) }
                }
            }
        }
    }

    companion object {
        const val QUERY_A =
            "SELECT h.organization_id " +
                "FROM (" +
                "SELECT organization_id, max(change_sequence) AS maximum_change_sequence " +
                "FROM marketplace_economic_evidence_update " +
                "GROUP BY organization_id" +
                ") h " +
                "LEFT JOIN projection_checkpoint c " +
                "ON c.organization_id = h.organization_id " +
                "AND c.projection_name = ? " +
                "WHERE h.maximum_change_sequence > COALESCE(c.last_change_sequence, 0) " +
                "ORDER BY h.organization_id ASC " +
                "LIMIT ?"

        const val QUERY_B =
            "SELECT o.organization_id " +
                "FROM integration_organization o " +
                "LEFT JOIN projection_checkpoint c " +
                "ON c.organization_id = o.organization_id " +
                "AND c.projection_name = ? " +
                "WHERE EXISTS (" +
                "SELECT 1 " +
                "FROM marketplace_economic_evidence_update u " +
                "WHERE u.organization_id = o.organization_id " +
                "AND u.change_sequence > COALESCE(c.last_change_sequence, 0)" +
                ") " +
                "ORDER BY o.organization_id ASC " +
                "LIMIT ?"

        const val QUERY_C =
            "SELECT o.organization_id " +
                "FROM integration_organization o " +
                "LEFT JOIN projection_checkpoint c " +
                "ON c.organization_id = o.organization_id " +
                "AND c.projection_name = ? " +
                "WHERE (" +
                "SELECT u.change_sequence " +
                "FROM marketplace_economic_evidence_update u " +
                "WHERE u.organization_id = o.organization_id " +
                "ORDER BY u.change_sequence DESC " +
                "LIMIT 1" +
                ") > COALESCE(c.last_change_sequence, 0) " +
                "ORDER BY o.organization_id ASC " +
                "LIMIT ?"
    }
}
