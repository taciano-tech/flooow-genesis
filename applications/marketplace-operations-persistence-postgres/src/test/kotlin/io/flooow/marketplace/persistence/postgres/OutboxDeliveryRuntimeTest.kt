package io.flooow.marketplace.persistence.postgres

import io.flooow.marketplace.operations.inventory.AssessmentIdentifierFactory
import io.flooow.marketplace.operations.inventory.InventoryRiskAssessmentRecorder
import io.flooow.marketplace.operations.inventory.InventoryRiskInput
import io.flooow.organization.OrganizationId
import java.sql.DriverManager
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OutboxDeliveryRuntimeTest {
    private val organizationId =
        OrganizationId.parse("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa")
    private val otherOrganizationId =
        OrganizationId.parse("bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb")
    private lateinit var postgres: PostgreSQLContainer
    private lateinit var configuration: PostgresConfiguration
    private lateinit var store: PostgresOutboxDeliveryStore
    private val now = Instant.parse("2026-08-10T13:00:00Z")

    @BeforeTest
    fun startPostgres() {
        postgres = PostgreSQLContainer("postgres:18.4")
        postgres.start()
        configuration = PostgresConfiguration(
            url = postgres.jdbcUrl,
            user = postgres.username,
            password = postgres.password
        )
        store = PostgresOutboxDeliveryStore.connect(configuration)
    }

    @AfterTest
    fun stopPostgres() {
        postgres.stop()
    }

    @Test
    fun `migration enqueue claim and canonical payload obey contract`() {
        val eventId = UUID.fromString("77777777-7777-4777-8777-777777777777")
        recordAssessment("11111111-1111-4111-8111-111111111111", eventId)
        val destination = DeliveryDestinationId.of("integration-test.v1")
        registerDestination(destination)

        assertTrue(store.enqueue(organizationId, eventId, destination, now))
        assertFalse(store.enqueue(organizationId, eventId, destination, now.plusSeconds(10)))
        assertFails { store.enqueue(organizationId, UUID.randomUUID(), destination, now) }
        assertFails { DeliveryDestinationId.of("HTTPS://secret.example") }
        assertFails { DeliveryErrorCode.of("response body") }

        val claim = store.claim(organizationId, "worker-1", now, batchSize = 1).single()
        assertEquals(organizationId, claim.organizationId)
        assertEquals(eventId, claim.eventId)
        assertEquals(destination, claim.destinationId)
        assertEquals(1, claim.attemptCount)
        assertFalse(claim.recoveredExpiredLease)
        assertContentEquals(
            resource("/inventory-risk-assessment-recorded-v2.json").trimEnd()
                .toByteArray(Charsets.UTF_8),
            claim.structuredCloudEvent
        )
        assertEquals("IN_FLIGHT", delivery(eventId).status)
        assertEquals(null, publishedAt(eventId))
    }

    @Test
    fun `parallel claims are disjoint ordered and expired leases recover`() {
        val first = UUID.fromString("11111111-1111-4111-8111-111111111117")
        val second = UUID.fromString("22222222-2222-4222-8222-222222222227")
        recordAssessment("11111111-1111-4111-8111-111111111111", first)
        recordAssessment("22222222-2222-4222-8222-222222222222", second)
        val destination = DeliveryDestinationId.of("parallel-test")
        registerDestination(destination)
        store.enqueue(organizationId, first, destination, now.minusSeconds(1))
        store.enqueue(organizationId, second, destination, now)

        val pool = Executors.newFixedThreadPool(2)
        val claims = try {
            pool.invokeAll(
                listOf(
                    Callable {
                        store.claim(organizationId, "worker-a", now, batchSize = 1).single()
                    },
                    Callable {
                        store.claim(organizationId, "worker-b", now, batchSize = 1).single()
                    }
                )
            ).map { it.get() }
        } finally {
            pool.shutdownNow()
        }
        assertEquals(setOf(first, second), claims.mapTo(mutableSetOf()) { it.eventId })
        assertTrue(store.claim(organizationId, "worker-c", now.plusSeconds(29)).isEmpty())

        assertEquals(
            DeliverySettlement.LEASE_CONFLICT,
            store.renew(claims.first(), "worker-a", now.plusSeconds(31), Duration.ofSeconds(30))
        )

        val recovered = store.claim(
            organizationId,
            "worker-c",
            now.plusSeconds(31),
            batchSize = 2
        )
        assertEquals(listOf(first, second), recovered.map { it.eventId })
        assertTrue(recovered.all { it.recoveredExpiredLease && it.attemptCount == 2 })
        assertEquals(
            DeliverySettlement.LEASE_CONFLICT,
            store.settle(claims.first(), "worker-a", DeliverySinkResult.Delivered, now)
        )
        assertEquals(
            DeliverySettlement.SETTLED,
            store.renew(recovered.first(), "worker-c", now.plusSeconds(32), Duration.ofSeconds(30))
        )
        assertEquals(
            DeliverySettlement.SETTLED,
            store.settle(
                recovered.first(),
                "worker-c",
                DeliverySinkResult.Delivered,
                now.plusSeconds(33)
            )
        )
    }

    @Test
    fun `dispatcher retries dead letters sanitizes failures and records safe telemetry`() {
        val retryEvent = UUID.fromString("33333333-3333-4333-8333-333333333337")
        recordAssessment("33333333-3333-4333-8333-333333333333", retryEvent)
        val destination = DeliveryDestinationId.of("retry-test")
        registerDestination(destination)
        store.enqueue(organizationId, retryEvent, destination, now)
        val clock = MutableClock(now)
        val observations = mutableListOf<DeliveryAttemptObservation>()
        val dispatcher = OutboxDeliveryDispatcher(
            store = store,
            sink = IntegrationEventSink {
                DeliverySinkResult.RetryableFailure(DeliveryErrorCode.of("REMOTE_UNAVAILABLE"))
            },
            clock = clock,
            telemetry = DeliveryTelemetry(observations::add),
            nanoTime = sequenceOf(100L, 150L, 200L, 260L, 300L, 370L, 400L, 480L,
                500L, 590L, 600L, 700L, 800L, 910L, 1_000L, 1_120L).iterator()::next
        )

        val delays = listOf(1L, 2L, 4L, 8L, 16L, 32L, 60L)
        delays.forEachIndexed { index, delayMinutes ->
            assertEquals(1, dispatcher.dispatchBatch(organizationId, "retry-worker"))
            val state = delivery(retryEvent)
            assertEquals("PENDING", state.status)
            assertEquals(index + 1, state.attemptCount)
            assertEquals(clock.instant().plus(Duration.ofMinutes(delayMinutes)), state.nextAttemptAt)
            clock.advance(Duration.ofMinutes(delayMinutes))
        }
        assertEquals(1, dispatcher.dispatchBatch(organizationId, "retry-worker"))
        val dead = delivery(retryEvent)
        assertEquals("DEAD_LETTER", dead.status)
        assertEquals(8, dead.attemptCount)
        assertEquals("MAX_ATTEMPTS", dead.errorCode)
        assertEquals(8, observations.size)
        assertEquals("dead_letter", observations.last().outcome)
        assertTrue(observations.all { it.destinationId == "retry-test" })
        assertTrue(observations.all { it.errorCode == "REMOTE_UNAVAILABLE" })
        assertTrue(observations.none { it.toString().contains("RED-MOTO-001") })
        assertEquals(null, publishedAt(retryEvent))

        val exceptionEvent = UUID.fromString("44444444-4444-4444-8444-444444444447")
        recordAssessment("44444444-4444-4444-8444-444444444444", exceptionEvent)
        val exceptionDestination = DeliveryDestinationId.of("exception-test")
        registerDestination(exceptionDestination)
        store.enqueue(organizationId, exceptionEvent, exceptionDestination, clock.instant())
        val exceptionDispatcher = OutboxDeliveryDispatcher(
            store,
            IntegrationEventSink { error("secret response RED-MOTO-001") },
            clock
        )
        exceptionDispatcher.dispatchBatch(organizationId, "exception-worker")
        assertEquals("SINK_EXCEPTION", delivery(exceptionEvent).errorCode)
        assertFalse(delivery(exceptionEvent).raw.contains("secret response"))
    }

    @Test
    fun `network runs after commit permanent failure settles and cancellation claims nothing`() {
        val eventId = UUID.fromString("55555555-5555-4555-8555-555555555557")
        recordAssessment("55555555-5555-4555-8555-555555555555", eventId)
        val destination = DeliveryDestinationId.of("permanent-test")
        registerDestination(destination)
        store.enqueue(organizationId, eventId, destination, now)
        val clock = MutableClock(now)
        var observedCommittedClaim = false
        val dispatcher = OutboxDeliveryDispatcher(
            store,
            IntegrationEventSink { delivery ->
                observedCommittedClaim = delivery(delivery.eventId).status == "IN_FLIGHT"
                DeliverySinkResult.PermanentFailure(DeliveryErrorCode.of("DESTINATION_REJECTED"))
            },
            clock
        )

        assertEquals(
            0,
            dispatcher.dispatchBatch(
                organizationId,
                "cancelled-worker",
                shouldContinue = { false }
            )
        )
        assertEquals("PENDING", delivery(eventId).status)
        assertEquals(1, dispatcher.dispatchBatch(organizationId, "active-worker"))
        assertTrue(observedCommittedClaim)
        assertEquals("DEAD_LETTER", delivery(eventId).status)
        assertEquals("DESTINATION_REJECTED", delivery(eventId).errorCode)
    }

    @Test
    fun `enqueue claim and fencing never cross organization boundaries`() {
        val eventId = UUID.fromString("66666666-6666-4666-8666-666666666667")
        recordAssessment("66666666-6666-4666-8666-666666666666", eventId)
        val destination = DeliveryDestinationId.of("organization-boundary")
        registerDestination(destination)

        assertFalse(store.enqueue(otherOrganizationId, eventId, destination, now))
        assertTrue(store.enqueue(organizationId, eventId, destination, now))
        assertTrue(store.claim(otherOrganizationId, "foreign-worker", now).isEmpty())

        val claim = store.claim(organizationId, "owner-worker", now).single()
        assertEquals(
            DeliverySettlement.LEASE_CONFLICT,
            store.settle(
                claim.copy(organizationId = otherOrganizationId),
                "owner-worker",
                DeliverySinkResult.Delivered,
                now.plusSeconds(1)
            )
        )
        assertEquals("IN_FLIGHT", delivery(eventId).status)
        assertEquals(
            DeliverySettlement.SETTLED,
            store.settle(
                claim,
                "owner-worker",
                DeliverySinkResult.Delivered,
                now.plusSeconds(1)
            )
        )
    }

    @Test
    fun `enqueue requires active organization connection and destination`() {
        val eventId = UUID.fromString("77777777-7777-4777-8777-777777777778")
        recordAssessment("77777777-7777-4777-8777-777777777779", eventId)
        val destination = DeliveryDestinationId.of("lifecycle-boundary")

        assertFalse(store.enqueue(organizationId, eventId, destination, now))
        registerDestination(destination)
        updateControlPlaneStatus("integration_destination", "SUSPENDED")
        assertFalse(store.enqueue(organizationId, eventId, destination, now))
        updateControlPlaneStatus("integration_destination", "ACTIVE")
        updateControlPlaneStatus("integration_connection", "SUSPENDED")
        assertFalse(store.enqueue(organizationId, eventId, destination, now))
        updateControlPlaneStatus("integration_connection", "ACTIVE")
        updateControlPlaneStatus("integration_organization", "SUSPENDED")
        assertFalse(store.enqueue(organizationId, eventId, destination, now))
        updateControlPlaneStatus("integration_organization", "ACTIVE")
        updateControlPlaneStatus("integration_connection", "REVOKED")
        assertFalse(store.enqueue(organizationId, eventId, destination, now))
    }

    private fun recordAssessment(assessmentId: String, eventId: UUID) {
        val journal = PostgresInventoryRiskAssessmentJournal.connect(
            configuration,
            IntegrationEventIdentifierFactory { eventId }
        )
        InventoryRiskAssessmentRecorder(
            journal = journal,
            identifierFactory = AssessmentIdentifierFactory { assessmentId },
            clock = Clock.fixed(now, ZoneOffset.UTC)
        ).record(organizationId, redMotoInput())
    }

    private fun registerDestination(destinationId: DeliveryDestinationId) {
        connection().use { connection ->
            connection.autoCommit = false
            try {
                connection.prepareStatement(
                    "INSERT INTO integration_organization VALUES (?, 'ACTIVE', ?, ?) " +
                        "ON CONFLICT DO NOTHING"
                ).use { statement ->
                    statement.setObject(1, organizationId.value)
                    statement.setTimestamp(2, java.sql.Timestamp.from(now))
                    statement.setTimestamp(3, java.sql.Timestamp.from(now))
                    statement.executeUpdate()
                }
                val connectionId = UUID.fromString("cccccccc-cccc-4ccc-8ccc-cccccccccccc")
                connection.prepareStatement(
                    "INSERT INTO integration_connection " +
                        "(organization_id, connection_id, provider_key, credential_kind, status, " +
                        "binding_version, created_at, updated_at) " +
                        "VALUES (?, ?, 'test.provider', 'STATIC_API_CREDENTIAL', 'ACTIVE', 1, ?, ?) " +
                        "ON CONFLICT DO NOTHING"
                ).use { statement ->
                    statement.setObject(1, organizationId.value)
                    statement.setObject(2, connectionId)
                    statement.setTimestamp(3, java.sql.Timestamp.from(now))
                    statement.setTimestamp(4, java.sql.Timestamp.from(now))
                    statement.executeUpdate()
                }
                connection.prepareStatement(
                    "INSERT INTO integration_destination VALUES (?, ?, ?, 'ACTIVE', ?, ?) " +
                        "ON CONFLICT DO NOTHING"
                ).use { statement ->
                    statement.setObject(1, organizationId.value)
                    statement.setObject(2, connectionId)
                    statement.setString(3, destinationId.value)
                    statement.setTimestamp(4, java.sql.Timestamp.from(now))
                    statement.setTimestamp(5, java.sql.Timestamp.from(now))
                    statement.executeUpdate()
                }
                connection.commit()
            } catch (error: Exception) {
                connection.rollback()
                throw error
            }
        }
    }

    private fun updateControlPlaneStatus(table: String, status: String) {
        require(
            table in setOf(
                "integration_organization",
                "integration_connection",
                "integration_destination"
            )
        )
        connection().use { connection ->
            connection.prepareStatement(
                "UPDATE $table SET status = ? WHERE organization_id = ?"
            ).use { statement ->
                statement.setString(1, status)
                statement.setObject(2, organizationId.value)
                assertTrue(statement.executeUpdate() >= 1)
            }
        }
    }

    private fun delivery(eventId: UUID): DeliveryRow = connection().use { connection ->
        connection.prepareStatement(
            "SELECT status, attempt_count, next_attempt_at, last_error_code, " +
                "row_to_json(d)::text FROM integration_event_delivery d WHERE event_id = ?"
        ).use { statement ->
            statement.setObject(1, eventId)
            statement.executeQuery().use { result ->
                check(result.next())
                DeliveryRow(
                    status = result.getString("status"),
                    attemptCount = result.getInt("attempt_count"),
                    nextAttemptAt = result.getTimestamp("next_attempt_at").toInstant(),
                    errorCode = result.getString("last_error_code"),
                    raw = result.getString(5)
                )
            }
        }
    }

    private fun publishedAt(eventId: UUID): Instant? = connection().use { connection ->
        connection.prepareStatement(
            "SELECT published_at FROM integration_event_outbox WHERE event_id = ?"
        ).use { statement ->
            statement.setObject(1, eventId)
            statement.executeQuery().use { result ->
                check(result.next())
                result.getTimestamp(1)?.toInstant()
            }
        }
    }

    private fun connection() =
        DriverManager.getConnection(configuration.url, configuration.user, configuration.password)

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

    private fun resource(path: String): String =
        requireNotNull(javaClass.getResource(path)).readText()

    private data class DeliveryRow(
        val status: String,
        val attemptCount: Int,
        val nextAttemptAt: Instant,
        val errorCode: String?,
        val raw: String
    )

    private class MutableClock(private var current: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneOffset.UTC
        override fun withZone(zone: ZoneId): Clock = this
        override fun instant(): Instant = current
        fun advance(duration: Duration) {
            current = current.plus(duration)
        }
    }
}
