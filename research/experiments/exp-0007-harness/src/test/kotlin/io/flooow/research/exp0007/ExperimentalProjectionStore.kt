package io.flooow.research.exp0007

import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant
import java.util.UUID

internal data class ExperimentalProjectionRow(
    val organizationId: UUID,
    val subjectId: UUID,
    val projectedValue: Long,
    val sourceEvidenceVersion: Long,
    val lastAppliedChangeSequence: Long,
    val projectedAt: Instant
)

internal data class ExperimentalCheckpointRow(
    val organizationId: UUID,
    val projectionName: String,
    val lastChangeSequence: Long
)

internal class ExperimentalProjectionStore(
    private val jdbcUrl: String,
    private val user: String,
    private val password: String
) {

    fun installSchema() {
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS exp0007_sales_projection (
                        organization_id uuid NOT NULL,
                        subject_id uuid NOT NULL,
                        projected_value bigint NOT NULL,
                        source_evidence_version bigint NOT NULL,
                        last_applied_change_sequence bigint NOT NULL,
                        projected_at timestamptz(6) NOT NULL DEFAULT transaction_timestamp(),

                        PRIMARY KEY (organization_id, subject_id),

                        FOREIGN KEY (organization_id)
                            REFERENCES integration_organization (organization_id),

                        CHECK (source_evidence_version >= 0),
                        CHECK (last_applied_change_sequence > 0)
                    )
                    """.trimIndent()
                )

                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS exp0007_processed_change (
                        organization_id uuid NOT NULL,
                        projection_name text NOT NULL,
                        change_sequence bigint NOT NULL,
                        processed_at timestamptz(6) NOT NULL DEFAULT transaction_timestamp(),

                        PRIMARY KEY (
                            organization_id,
                            projection_name,
                            change_sequence
                        ),

                        FOREIGN KEY (organization_id)
                            REFERENCES integration_organization (organization_id),

                        CHECK (
                            length(projection_name) BETWEEN 1 AND 100
                            AND projection_name ~ '^[a-z0-9][a-z0-9-]*$'
                        ),

                        CHECK (change_sequence > 0)
                    )
                    """.trimIndent()
                )

                statement.execute(
                    """
                    CREATE TABLE IF NOT EXISTS exp0007_projection_checkpoint (
                        organization_id uuid NOT NULL,
                        projection_name text NOT NULL,
                        last_change_sequence bigint NOT NULL,
                        updated_at timestamptz(6) NOT NULL DEFAULT transaction_timestamp(),

                        PRIMARY KEY (organization_id, projection_name),

                        FOREIGN KEY (organization_id)
                            REFERENCES integration_organization (organization_id),

                        CHECK (
                            length(projection_name) BETWEEN 1 AND 100
                            AND projection_name ~ '^[a-z0-9][a-z0-9-]*$'
                        ),

                        CHECK (last_change_sequence >= 0)
                    )
                    """.trimIndent()
                )
            }
        }
    }

    fun reset() {
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeUpdate("DELETE FROM exp0007_projection_checkpoint")
                statement.executeUpdate("DELETE FROM exp0007_processed_change")
                statement.executeUpdate("DELETE FROM exp0007_sales_projection")
            }
        }
    }

    fun createOrganization(organizationId: UUID) {
        connection().use { connection ->
            connection.prepareStatement(
                """
                INSERT INTO integration_organization (
                    organization_id,
                    status,
                    created_at,
                    updated_at
                )
                VALUES (?, 'ACTIVE', transaction_timestamp(), transaction_timestamp())
                ON CONFLICT (organization_id) DO NOTHING
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, organizationId)
                statement.executeUpdate()
            }
        }
    }

    fun <T> transaction(block: (Connection) -> T): T {
        connection().use { connection ->
            connection.autoCommit = false

            try {
                val result = block(connection)
                connection.commit()
                return result
            } catch (failure: Throwable) {
                connection.rollback()
                throw failure
            }
        }
    }

    fun applyProjectionMonotonically(
        connection: Connection,
        organizationId: UUID,
        subjectId: UUID,
        projectedValue: Long,
        sourceEvidenceVersion: Long,
        changeSequence: Long
    ): Int {
        require(sourceEvidenceVersion >= 0)
        require(changeSequence > 0)

        connection.prepareStatement(
            """
            INSERT INTO exp0007_sales_projection (
                organization_id,
                subject_id,
                projected_value,
                source_evidence_version,
                last_applied_change_sequence,
                projected_at
            )
            VALUES (?, ?, ?, ?, ?, transaction_timestamp())

            ON CONFLICT (organization_id, subject_id)
            DO UPDATE SET
                projected_value = EXCLUDED.projected_value,
                source_evidence_version = EXCLUDED.source_evidence_version,
                last_applied_change_sequence = EXCLUDED.last_applied_change_sequence,
                projected_at = transaction_timestamp()

            WHERE
                EXCLUDED.last_applied_change_sequence
                    > exp0007_sales_projection.last_applied_change_sequence
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, organizationId)
            statement.setObject(2, subjectId)
            statement.setLong(3, projectedValue)
            statement.setLong(4, sourceEvidenceVersion)
            statement.setLong(5, changeSequence)

            return statement.executeUpdate()
        }
    }

    fun recordProcessedChange(
        connection: Connection,
        organizationId: UUID,
        projectionName: String,
        changeSequence: Long
    ): Int {
        require(projectionName.matches(Regex("^[a-z0-9][a-z0-9-]{0,99}$")))
        require(changeSequence > 0)

        connection.prepareStatement(
            """
            INSERT INTO exp0007_processed_change (
                organization_id,
                projection_name,
                change_sequence,
                processed_at
            )
            VALUES (?, ?, ?, transaction_timestamp())

            ON CONFLICT (
                organization_id,
                projection_name,
                change_sequence
            )
            DO NOTHING
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, organizationId)
            statement.setString(2, projectionName)
            statement.setLong(3, changeSequence)

            return statement.executeUpdate()
        }
    }

    fun advanceCheckpoint(
        connection: Connection,
        organizationId: UUID,
        projectionName: String,
        nextChangeSequence: Long
    ): Int {
        require(projectionName.matches(Regex("^[a-z0-9][a-z0-9-]{0,99}$")))
        require(nextChangeSequence >= 0)

        connection.prepareStatement(
            """
            INSERT INTO exp0007_projection_checkpoint (
                organization_id,
                projection_name,
                last_change_sequence,
                updated_at
            )
            VALUES (?, ?, ?, transaction_timestamp())

            ON CONFLICT (organization_id, projection_name)
            DO UPDATE SET
                last_change_sequence = EXCLUDED.last_change_sequence,
                updated_at = transaction_timestamp()

            WHERE
                EXCLUDED.last_change_sequence
                    > exp0007_projection_checkpoint.last_change_sequence
            """.trimIndent()
        ).use { statement ->
            statement.setObject(1, organizationId)
            statement.setString(2, projectionName)
            statement.setLong(3, nextChangeSequence)

            return statement.executeUpdate()
        }
    }

    fun lastAppliedChangeSequence(
        organizationId: UUID,
        subjectId: UUID
    ): Long? =
        connection().use { connection ->
            connection.prepareStatement(
                """
                SELECT last_applied_change_sequence
                FROM exp0007_sales_projection
                WHERE organization_id = ?
                  AND subject_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, organizationId)
                statement.setObject(2, subjectId)

                statement.executeQuery().use { result ->
                    if (result.next()) {
                        result.getLong("last_applied_change_sequence")
                    } else {
                        null
                    }
                }
            }
        }

    fun projection(
        organizationId: UUID,
        subjectId: UUID
    ): ExperimentalProjectionRow? =
        connection().use { connection ->
            connection.prepareStatement(
                """
                SELECT
                    organization_id,
                    subject_id,
                    projected_value,
                    source_evidence_version,
                    last_applied_change_sequence,
                    projected_at
                FROM exp0007_sales_projection
                WHERE organization_id = ?
                  AND subject_id = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, organizationId)
                statement.setObject(2, subjectId)

                statement.executeQuery().use { result ->
                    if (!result.next()) {
                        null
                    } else {
                        ExperimentalProjectionRow(
                            organizationId = result.getObject("organization_id", UUID::class.java),
                            subjectId = result.getObject("subject_id", UUID::class.java),
                            projectedValue = result.getLong("projected_value"),
                            sourceEvidenceVersion = result.getLong("source_evidence_version"),
                            lastAppliedChangeSequence =
                                result.getLong("last_applied_change_sequence"),
                            projectedAt =
                                result.getTimestamp("projected_at").toInstant()
                        )
                    }
                }
            }
        }

    fun checkpoint(
        organizationId: UUID,
        projectionName: String
    ): ExperimentalCheckpointRow? =
        connection().use { connection ->
            connection.prepareStatement(
                """
                SELECT
                    organization_id,
                    projection_name,
                    last_change_sequence
                FROM exp0007_projection_checkpoint
                WHERE organization_id = ?
                  AND projection_name = ?
                """.trimIndent()
            ).use { statement ->
                statement.setObject(1, organizationId)
                statement.setString(2, projectionName)

                statement.executeQuery().use { result ->
                    if (!result.next()) {
                        null
                    } else {
                        ExperimentalCheckpointRow(
                            organizationId =
                                result.getObject("organization_id", UUID::class.java),
                            projectionName =
                                result.getString("projection_name"),
                            lastChangeSequence =
                                result.getLong("last_change_sequence")
                        )
                    }
                }
            }
        }

    private fun connection(): Connection =
        DriverManager.getConnection(jdbcUrl, user, password)
}
