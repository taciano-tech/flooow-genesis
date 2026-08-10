package io.flooow.marketplace.persistence.postgres

import io.flooow.marketplace.operations.inventory.AssessmentIdentifierFactory
import io.flooow.marketplace.operations.inventory.InventoryRiskAssessmentRecorder
import io.flooow.marketplace.operations.inventory.InventoryRiskInput
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PostgresInventoryRiskAssessmentJournalTest {
    private lateinit var postgres: PostgreSQLContainer
    private lateinit var configuration: PostgresConfiguration
    private lateinit var journal: PostgresInventoryRiskAssessmentJournal

    @BeforeTest
    fun startPostgres() {
        postgres = PostgreSQLContainer("postgres:18.4")
        postgres.start()
        configuration = PostgresConfiguration(
            url = postgres.jdbcUrl,
            user = postgres.username,
            password = postgres.password
        )
        journal = PostgresInventoryRiskAssessmentJournal.connect(configuration)
    }

    @AfterTest
    fun stopPostgres() {
        postgres.stop()
    }

    @Test
    fun `migration and exact record round trip`() {
        val recorder = recorder("11111111-1111-4111-8111-111111111111")

        val recorded = recorder.record(redMotoInput())

        assertEquals(recorded, assertNotNull(recorder.findById(recorded.assessmentId)))
        DriverManager.getConnection(configuration.url, configuration.user, configuration.password)
            .use { connection ->
                connection.createStatement().use { statement ->
                    statement.executeQuery(
                        "SELECT version, success FROM flyway_schema_history ORDER BY installed_rank"
                    ).use { result ->
                        result.next()
                        assertEquals("001", result.getString("version"))
                        assertEquals(true, result.getBoolean("success"))
                    }
                }
            }
    }

    @Test
    fun `missing record returns null and duplicate id fails atomically`() {
        val recorder = recorder("22222222-2222-4222-8222-222222222222")
        assertNull(recorder.findById("33333333-3333-4333-8333-333333333333"))

        val first = recorder.record(redMotoInput())
        assertFails { recorder.record(redMotoInput()) }

        assertEquals(first, recorder.findById(first.assessmentId))
    }

    @Test
    fun `tampered typed data is rejected by digest verification`() {
        val recorder = recorder("44444444-4444-4444-8444-444444444444")
        val recorded = recorder.record(redMotoInput())
        DriverManager.getConnection(configuration.url, configuration.user, configuration.password)
            .use { connection ->
                connection.prepareStatement(
                    "UPDATE inventory_risk_assessment_journal SET expected_impact = ? " +
                        "WHERE assessment_id = ?::uuid"
                ).use { statement ->
                    statement.setString(1, "tampered")
                    statement.setString(2, recorded.assessmentId)
                    statement.executeUpdate()
                }
            }

        assertFails { recorder.findById(recorded.assessmentId) }
    }

    private fun recorder(id: String) = InventoryRiskAssessmentRecorder(
        journal = journal,
        identifierFactory = AssessmentIdentifierFactory { id },
        clock = Clock.fixed(Instant.parse("2026-08-10T13:00:00Z"), ZoneOffset.UTC)
    )

    private fun redMotoInput() = InventoryRiskInput(
        sku = "RED-MOTO-001",
        periodEnd = LocalDate.parse("2026-08-31"),
        targetUnits = 300,
        unitsSold = 180,
        availableUnits = 90,
        dailySalesVelocity = 15,
        observedOn = LocalDate.parse("2026-08-10"),
        expectedReplenishmentOn = LocalDate.parse("2026-08-20")
    )
}
