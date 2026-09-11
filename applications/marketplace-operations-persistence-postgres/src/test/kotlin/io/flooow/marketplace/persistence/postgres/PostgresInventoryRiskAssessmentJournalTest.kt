package io.flooow.marketplace.persistence.postgres

import io.flooow.marketplace.operations.inventory.AssessmentIdentifierFactory
import io.flooow.marketplace.operations.inventory.InventoryRiskAssessmentRecorder
import io.flooow.marketplace.operations.inventory.InventoryRiskInput
import io.flooow.organization.OrganizationId
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
    private val organizationId =
        OrganizationId.parse("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val otherOrganizationId =
        OrganizationId.parse("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
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

        val recorded = recorder.record(organizationId, redMotoInput())

        assertEquals(
            recorded,
            assertNotNull(recorder.findById(organizationId, recorded.assessmentId))
        )
        assertNull(recorder.findById(otherOrganizationId, recorded.assessmentId))
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
                        result.next()
                        assertEquals("005", result.getString("version"))
                        assertEquals(true, result.getBoolean("success"))
                        assertTrue(result.next())
                        assertEquals("006", result.getString("version"))
                        assertEquals(true, result.getBoolean("success"))
                        assertTrue(result.next())
                        assertEquals("007", result.getString("version"))
                        assertEquals(true, result.getBoolean("success"))
                        assertTrue(result.next())
                        assertEquals("008", result.getString("version"))
                        assertEquals(true, result.getBoolean("success"))
                        assertTrue(result.next())
                        assertEquals("009", result.getString("version"))
                        assertEquals(true, result.getBoolean("success"))
                        assertTrue(result.next())
                        assertEquals("010", result.getString("version"))
                        assertEquals(true, result.getBoolean("success"))
                        assertTrue(result.next())
                        assertEquals("011", result.getString("version"))
                        assertEquals(true, result.getBoolean("success"))
                        assertTrue(result.next())
                        assertEquals("012", result.getString("version"))
                        assertEquals(true, result.getBoolean("success"))
                        assertTrue(result.next())
                        assertEquals("013", result.getString("version"))
                        assertEquals(true, result.getBoolean("success"))
                        assertTrue(result.next())
                        assertEquals("014", result.getString("version"))
                        assertEquals(true, result.getBoolean("success"))
                        assertTrue(
                            result.getString("version") == "014" && result.getBoolean("success")
                        )
                    }
                }
            }
    }

    @Test
    fun `assessment append stores frozen CloudEvent exactly once`() {
        val recorded = recorder("11111111-1111-4111-8111-111111111111")
            .record(organizationId, redMotoInput())
        val expectedJson = resource("/inventory-risk-assessment-recorded-v2.json").trimEnd()
        val event = InventoryRiskAssessmentRecordedEvent.from(
            recorded,
            UUID.fromString("77777777-7777-4777-8777-777777777777")
        )

        assertEquals(expectedJson, event.structuredJson)

        connection().use { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT organization_id, event_id, assessment_id, event_source, event_type, subject, " +
                        "occurred_at, content_type, event_json::text, published_at " +
                        "FROM integration_event_outbox"
                ).use { result ->
                    assertTrue(result.next())
                    assertEquals(
                        organizationId.value,
                        result.getObject("organization_id", UUID::class.java)
                    )
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
                            "datacontenttype", "dataschema", "floooworganizationid", "data"
                        ),
                        persisted.keys
                    )
                    assertEquals(
                        setOf(
                            "organizationId", "assessmentId", "sku", "observedOn", "shortageProjected",
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
        assertNull(
            recorder.findById(
                organizationId,
                "33333333-3333-4333-8333-333333333333"
            )
        )

        val first = recorder.record(organizationId, redMotoInput())
        assertFails { recorder.record(organizationId, redMotoInput()) }

        assertEquals(first, recorder.findById(organizationId, first.assessmentId))
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
            recorder("55555555-5555-4555-8555-555555555555")
                .record(organizationId, redMotoInput())
        }
        assertEquals(0, rowCount("inventory_risk_assessment_journal"))
        assertEquals(0, rowCount("integration_event_outbox"))
    }

    @Test
    fun `tampered typed data is rejected by digest verification`() {
        val recorder = recorder("44444444-4444-4444-8444-444444444444")
        val recorded = recorder.record(organizationId, redMotoInput())
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

        assertFails { recorder.findById(organizationId, recorded.assessmentId) }
    }

    @Test
    fun `legacy unscoped rows remain preserved but invisible and undispatchable`() {
        val recorder = recorder("11111111-1111-4111-8111-111111111111")
        val recorded = recorder.record(organizationId, redMotoInput())
        connection().use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    "UPDATE integration_event_outbox SET organization_id = NULL, " +
                        "event_type = ?, subject = ?, event_json = ?::jsonb WHERE assessment_id = ?"
                ).use { statement ->
                    statement.setString(
                        1,
                        "io.flooow.marketplace.inventory-risk-assessment.recorded.v1"
                    )
                    statement.setString(
                        2,
                        "/inventory-risk-assessments/${recorded.assessmentId}"
                    )
                    statement.setString(
                        3,
                        resource("/inventory-risk-assessment-recorded-v1.json").trimEnd()
                    )
                    statement.setObject(4, UUID.fromString(recorded.assessmentId))
                    assertEquals(1, statement.executeUpdate())
                }
                connection.prepareStatement(
                    "UPDATE inventory_risk_assessment_journal SET organization_id = NULL " +
                        "WHERE assessment_id = ?"
                ).use { statement ->
                    statement.setObject(1, UUID.fromString(recorded.assessmentId))
                    assertEquals(1, statement.executeUpdate())
                }
                connection.prepareStatement(
                    "INSERT INTO integration_event_delivery " +
                        "(event_id, destination_id, status, next_attempt_at) " +
                        "VALUES (?, 'legacy-quarantine', 'PENDING', ?)"
                ).use { statement ->
                    statement.setObject(
                        1,
                        UUID.fromString("77777777-7777-4777-8777-777777777777")
                    )
                    statement.setTimestamp(2, java.sql.Timestamp.from(Instant.EPOCH))
                    statement.executeUpdate()
                }
                connection.commit()
            } catch (error: Exception) {
                connection.rollback()
                throw error
            }
        }

        assertNull(recorder.findById(organizationId, recorded.assessmentId))
        assertNull(recorder.findById(otherOrganizationId, recorded.assessmentId))
        assertTrue(
            PostgresOutboxDeliveryStore.connect(configuration)
                .claim(organizationId, "scoped-worker", Instant.now())
                .isEmpty()
        )
        assertEquals(1, rowCount("inventory_risk_assessment_journal"))
        assertEquals(1, rowCount("integration_event_outbox"))
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
