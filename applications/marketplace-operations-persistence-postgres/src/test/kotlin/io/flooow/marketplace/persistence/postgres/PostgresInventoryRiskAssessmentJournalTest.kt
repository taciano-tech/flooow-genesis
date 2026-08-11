package io.flooow.marketplace.persistence.postgres

import io.flooow.marketplace.operations.inventory.AssessmentIdentifierFactory
import io.flooow.marketplace.operations.inventory.InventoryRiskAssessmentRecorder
import io.flooow.marketplace.operations.inventory.InventoryRiskInput
import java.sql.DriverManager
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFails
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

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
        journal = PostgresInventoryRiskAssessmentJournal.connect(
            configuration,
            IntegrationEventIdentifierFactory {
                UUID.fromString("77777777-7777-4777-8777-777777777777")
            }
        )
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
                        result.next()
                        assertEquals("002", result.getString("version"))
                        assertEquals(true, result.getBoolean("success"))
                        result.next()
                        assertEquals("003", result.getString("version"))
                        assertEquals(true, result.getBoolean("success"))
                        result.next()
                        assertEquals("004", result.getString("version"))
                        assertEquals(true, result.getBoolean("success"))
                        assertTrue(!result.next())
                    }
                }
            }
    }

    @Test
    fun `assessment append stores frozen CloudEvent exactly once`() {
        val recorded = recorder("11111111-1111-4111-8111-111111111111")
            .record(redMotoInput())
        val expectedJson = resource("/inventory-risk-assessment-recorded-v1.json").trimEnd()
        val event = InventoryRiskAssessmentRecordedEvent.from(
            recorded,
            UUID.fromString("77777777-7777-4777-8777-777777777777")
        )

        assertEquals(expectedJson, event.structuredJson)

        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT event_id, assessment_id, event_source, event_type, subject, " +
                        "occurred_at, content_type, event_json::text, published_at " +
                        "FROM integration_event_outbox"
                ).use { result ->
                    assertTrue(result.next())
                    assertEquals(event.id, result.getObject("event_id", UUID::class.java))
                    assertEquals(event.assessmentId, result.getObject("assessment_id", UUID::class.java))
                    assertEquals(event.source, result.getString("event_source"))
                    assertEquals(event.type, result.getString("event_type"))
                    assertEquals(event.subject, result.getString("subject"))
                    assertEquals(event.occurredAt, result.getTimestamp("occurred_at").toInstant())
                    assertEquals(event.contentType, result.getString("content_type"))
                    assertNull(result.getTimestamp("published_at"))
                    val persisted = Json.parseToJsonElement(result.getString("event_json")).jsonObject
                    assertEquals("1.0", persisted.getValue("specversion").jsonPrimitive.content)
                    assertEquals(
                        recorded.assessmentId,
                        persisted.getValue("data").jsonObject
                            .getValue("assessmentId").jsonPrimitive.content
                    )
                    assertEquals(
                        setOf(
                            "specversion", "id", "source", "type", "subject", "time",
                            "datacontenttype", "dataschema", "data"
                        ),
                        persisted.keys
                    )
                    assertEquals(
                        setOf(
                            "assessmentId", "sku", "observedOn", "shortageProjected",
                            "unitsAtRiskAgainstGoal", "recommendationType",
                            "expectedUnitsPreserved"
                        ),
                        persisted.getValue("data").jsonObject.keys
                    )
                    listOf("token", "password", "trace", "explanation", "expectedImpact")
                        .forEach { forbidden -> assertFalse(event.structuredJson.contains(forbidden)) }
                    assertTrue(!result.next())
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
        assertEquals(1, rowCount("inventory_risk_assessment_journal"))
        assertEquals(1, rowCount("integration_event_outbox"))
    }

    @Test
    fun `outbox insert failure rolls back assessment`() {
        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    "CREATE FUNCTION reject_outbox_insert() RETURNS trigger LANGUAGE plpgsql " +
                        "AS 'BEGIN RAISE EXCEPTION ''forced outbox failure''; END'"
                )
                statement.execute(
                    "CREATE TRIGGER reject_outbox BEFORE INSERT ON integration_event_outbox " +
                        "FOR EACH ROW EXECUTE FUNCTION reject_outbox_insert()"
                )
            }
        }

        assertFails {
            recorder("55555555-5555-4555-8555-555555555555").record(redMotoInput())
        }
        assertEquals(0, rowCount("inventory_risk_assessment_journal"))
        assertEquals(0, rowCount("integration_event_outbox"))
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

    private fun connection() =
        DriverManager.getConnection(configuration.url, configuration.user, configuration.password)

    private fun rowCount(table: String): Int {
        require(table in setOf("inventory_risk_assessment_journal", "integration_event_outbox"))
        return connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT COUNT(*) FROM $table").use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
        }
    }

    private fun resource(path: String): String =
        requireNotNull(javaClass.getResource(path)).readText()
}
