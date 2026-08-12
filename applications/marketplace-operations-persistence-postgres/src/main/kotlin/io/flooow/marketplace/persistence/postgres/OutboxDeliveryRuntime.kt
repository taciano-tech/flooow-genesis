package io.flooow.marketplace.persistence.postgres

import io.flooow.organization.OrganizationId
import java.sql.Connection
import java.sql.DriverManager
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import org.flywaydb.core.Flyway

private const val MAX_ATTEMPTS = 8
private val destinationPattern = Regex("[a-z0-9][a-z0-9._-]{0,99}")
private val workerPattern = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,99}")
private val errorCodePattern = Regex("[A-Z0-9_]{1,64}")

@JvmInline
value class DeliveryDestinationId private constructor(val value: String) {
    companion object {
        fun of(value: String): DeliveryDestinationId {
            require(destinationPattern.matches(value)) { "Invalid delivery destination identifier" }
            return DeliveryDestinationId(value)
        }
    }
}

@JvmInline
value class DeliveryErrorCode private constructor(val value: String) {
    companion object {
        fun of(value: String): DeliveryErrorCode {
            require(errorCodePattern.matches(value)) { "Invalid delivery error code" }
            return DeliveryErrorCode(value)
        }
    }
}

sealed interface DeliverySinkResult {
    data object Delivered : DeliverySinkResult
    data class RetryableFailure(val code: DeliveryErrorCode) : DeliverySinkResult
    data class PermanentFailure(val code: DeliveryErrorCode) : DeliverySinkResult
}

fun interface IntegrationEventSink {
    fun deliver(delivery: ClaimedIntegrationEvent): DeliverySinkResult
}

data class ClaimedIntegrationEvent(
    val organizationId: OrganizationId,
    val destinationId: DeliveryDestinationId,
    val eventId: UUID,
    val eventType: String,
    val contentType: String,
    val structuredCloudEvent: ByteArray,
    val attemptCount: Int,
    val recoveredExpiredLease: Boolean
) {
    override fun equals(other: Any?): Boolean = other is ClaimedIntegrationEvent &&
        organizationId == other.organizationId && destinationId == other.destinationId &&
        eventId == other.eventId &&
        eventType == other.eventType && contentType == other.contentType &&
        structuredCloudEvent.contentEquals(other.structuredCloudEvent) &&
        attemptCount == other.attemptCount && recoveredExpiredLease == other.recoveredExpiredLease

    override fun hashCode(): Int = arrayOf(
        destinationId,
        organizationId,
        eventId,
        eventType,
        contentType,
        structuredCloudEvent.contentHashCode(),
        attemptCount,
        recoveredExpiredLease
    ).contentHashCode()
}

enum class DeliverySettlement { SETTLED, LEASE_CONFLICT }

data class DeliveryAttemptObservation(
    val destinationId: String,
    val eventId: String,
    val eventType: String,
    val attemptCount: Int,
    val outcome: String,
    val durationNanos: Long,
    val errorCode: String?
)

fun interface DeliveryTelemetry {
    fun record(observation: DeliveryAttemptObservation)

    companion object {
        val NONE = DeliveryTelemetry { }
    }
}

class PostgresOutboxDeliveryStore private constructor(
    private val configuration: PostgresConfiguration
) {
    fun enqueue(
        organizationId: OrganizationId,
        eventId: UUID,
        destinationId: DeliveryDestinationId,
        nextAttemptAt: Instant
    ): Boolean =
        connection().use { connection ->
            connection.prepareStatement(
                "INSERT INTO integration_event_delivery " +
                    "(organization_id, event_id, destination_id, status, next_attempt_at) " +
                    "SELECT ?, ?, ?, 'PENDING', ? WHERE EXISTS (" +
                    "SELECT 1 FROM integration_destination d " +
                    "JOIN integration_connection c ON c.organization_id = d.organization_id " +
                    "AND c.connection_id = d.connection_id " +
                    "JOIN integration_organization o ON o.organization_id = d.organization_id " +
                    "WHERE d.organization_id = ? AND d.destination_id = ? " +
                    "AND d.status = 'ACTIVE' AND c.status = 'ACTIVE' AND o.status = 'ACTIVE') " +
                    "ON CONFLICT DO NOTHING"
            ).use { statement ->
                statement.setObject(1, organizationId.value)
                statement.setObject(2, eventId)
                statement.setString(3, destinationId.value)
                statement.setTimestamp(4, java.sql.Timestamp.from(nextAttemptAt))
                statement.setObject(5, organizationId.value)
                statement.setString(6, destinationId.value)
                statement.executeUpdate() == 1
            }
        }

    fun claim(
        organizationId: OrganizationId,
        workerId: String,
        now: Instant,
        leaseDuration: Duration = Duration.ofSeconds(30),
        batchSize: Int = 10
    ): List<ClaimedIntegrationEvent> {
        require(workerPattern.matches(workerId)) { "Invalid delivery worker identifier" }
        require(leaseDuration in Duration.ofSeconds(5)..Duration.ofMinutes(5)) {
            "Lease duration must be between 5 seconds and 5 minutes"
        }
        require(batchSize in 1..100) { "Batch size must be between 1 and 100" }

        return transaction { connection ->
            val candidates = connection.prepareStatement(
                "SELECT d.event_id, d.destination_id, d.status, d.attempt_count, " +
                    "o.event_type, o.content_type, o.event_json::text " +
                    "FROM integration_event_delivery d " +
                    "JOIN integration_event_outbox o ON o.event_id = d.event_id " +
                    "AND o.organization_id = d.organization_id " +
                    "WHERE d.organization_id = ? AND ((d.status = 'PENDING' AND d.next_attempt_at <= ?) OR " +
                    "(d.status = 'IN_FLIGHT' AND d.lease_until <= ?)) " +
                    "ORDER BY d.next_attempt_at, o.created_at, d.event_id, d.destination_id " +
                    "FOR UPDATE OF d SKIP LOCKED LIMIT ?"
            ).use { statement ->
                statement.setObject(1, organizationId.value)
                statement.setTimestamp(2, java.sql.Timestamp.from(now))
                statement.setTimestamp(3, java.sql.Timestamp.from(now))
                statement.setInt(4, batchSize)
                statement.executeQuery().use { result ->
                    buildList {
                        while (result.next()) {
                            add(
                                Candidate(
                                    eventId = result.getObject("event_id", UUID::class.java),
                                    destinationId = DeliveryDestinationId.of(
                                        result.getString("destination_id")
                                    ),
                                    attemptCount = result.getInt("attempt_count") + 1,
                                    recovered = result.getString("status") == "IN_FLIGHT",
                                    eventType = result.getString("event_type"),
                                    contentType = result.getString("content_type"),
                                    eventJson = result.getString("event_json")
                                )
                            )
                        }
                    }
                }
            }

            candidates.forEach { candidate ->
                connection.prepareStatement(
                    "UPDATE integration_event_delivery SET status = 'IN_FLIGHT', " +
                        "attempt_count = ?, lease_owner = ?, lease_until = ?, " +
                    "last_attempt_at = ?, last_error_code = NULL, updated_at = ? " +
                        "WHERE organization_id = ? AND event_id = ? AND destination_id = ?"
                ).use { statement ->
                    statement.setInt(1, candidate.attemptCount)
                    statement.setString(2, workerId)
                    statement.setTimestamp(3, java.sql.Timestamp.from(now.plus(leaseDuration)))
                    statement.setTimestamp(4, java.sql.Timestamp.from(now))
                    statement.setTimestamp(5, java.sql.Timestamp.from(now))
                    statement.setObject(6, organizationId.value)
                    statement.setObject(7, candidate.eventId)
                    statement.setString(8, candidate.destinationId.value)
                    check(statement.executeUpdate() == 1)
                }
            }

            candidates.map { candidate ->
                ClaimedIntegrationEvent(
                    organizationId = organizationId,
                    destinationId = candidate.destinationId,
                    eventId = candidate.eventId,
                    eventType = candidate.eventType,
                    contentType = candidate.contentType,
                    structuredCloudEvent = canonicalCloudEvent(candidate.eventJson),
                    attemptCount = candidate.attemptCount,
                    recoveredExpiredLease = candidate.recovered
                )
            }
        }
    }

    fun renew(
        delivery: ClaimedIntegrationEvent,
        workerId: String,
        now: Instant,
        leaseDuration: Duration
    ): DeliverySettlement {
        require(workerPattern.matches(workerId)) { "Invalid delivery worker identifier" }
        require(leaseDuration in Duration.ofSeconds(5)..Duration.ofMinutes(5)) {
            "Lease duration must be between 5 seconds and 5 minutes"
        }
        return updateOwnedLease(
            delivery,
            workerId,
            now,
            "lease_until = ?, updated_at = ?",
            listOf(now.plus(leaseDuration), now)
        )
    }

    fun settle(
        delivery: ClaimedIntegrationEvent,
        workerId: String,
        result: DeliverySinkResult,
        now: Instant
    ): DeliverySettlement {
        require(workerPattern.matches(workerId)) { "Invalid delivery worker identifier" }
        val (assignment, values) = when (result) {
            DeliverySinkResult.Delivered ->
                "status = 'DELIVERED', delivered_at = ?, lease_owner = NULL, " +
                    "lease_until = NULL, last_error_code = NULL, updated_at = ?" to
                    listOf(now, now)
            is DeliverySinkResult.PermanentFailure -> deadLetter(result.code, now)
            is DeliverySinkResult.RetryableFailure -> if (delivery.attemptCount >= MAX_ATTEMPTS) {
                deadLetter(DeliveryErrorCode.of("MAX_ATTEMPTS"), now)
            } else {
                "status = 'PENDING', next_attempt_at = ?, lease_owner = NULL, " +
                    "lease_until = NULL, last_error_code = ?, updated_at = ?" to
                    listOf(now.plus(retryDelay(delivery.attemptCount)), result.code.value, now)
            }
        }
        return updateOwnedLease(delivery, workerId, now, assignment, values)
    }

    private fun deadLetter(code: DeliveryErrorCode, now: Instant) =
        "status = 'DEAD_LETTER', dead_lettered_at = ?, lease_owner = NULL, " +
            "lease_until = NULL, last_error_code = ?, updated_at = ?" to
            listOf(now, code.value, now)

    private fun updateOwnedLease(
        delivery: ClaimedIntegrationEvent,
        workerId: String,
        ownershipValidAt: Instant,
        assignment: String,
        values: List<Any>
    ): DeliverySettlement = connection().use { connection ->
        connection.prepareStatement(
            "UPDATE integration_event_delivery SET $assignment " +
                "WHERE organization_id = ? AND event_id = ? AND destination_id = ? AND " +
                "status = 'IN_FLIGHT' AND lease_owner = ? AND " +
                "attempt_count = ? AND lease_until > ?"
        ).use { statement ->
            var index = 1
            values.forEach { value ->
                when (value) {
                    is Instant -> statement.setTimestamp(index, java.sql.Timestamp.from(value))
                    is String -> statement.setString(index, value)
                    else -> error("Unsupported delivery update value")
                }
                index++
            }
            statement.setObject(index++, delivery.organizationId.value)
            statement.setObject(index++, delivery.eventId)
            statement.setString(index++, delivery.destinationId.value)
            statement.setString(index++, workerId)
            statement.setInt(index++, delivery.attemptCount)
            statement.setTimestamp(index, java.sql.Timestamp.from(ownershipValidAt))
            if (statement.executeUpdate() == 1) {
                DeliverySettlement.SETTLED
            } else {
                DeliverySettlement.LEASE_CONFLICT
            }
        }
    }

    private fun connection(): Connection = DriverManager.getConnection(
        configuration.url,
        configuration.user,
        configuration.password
    )

    private fun <T> transaction(block: (Connection) -> T): T = connection().use { connection ->
        connection.autoCommit = false
        try {
            block(connection).also { connection.commit() }
        } catch (error: Exception) {
            connection.rollback()
            throw error
        }
    }

    companion object {
        fun connect(configuration: PostgresConfiguration): PostgresOutboxDeliveryStore {
            Flyway.configure()
                .dataSource(configuration.url, configuration.user, configuration.password)
                .load()
                .migrate()
            return PostgresOutboxDeliveryStore(configuration)
        }
    }
}

class OutboxDeliveryDispatcher(
    private val store: PostgresOutboxDeliveryStore,
    private val sink: IntegrationEventSink,
    private val clock: Clock = Clock.systemUTC(),
    private val telemetry: DeliveryTelemetry = DeliveryTelemetry.NONE,
    private val nanoTime: () -> Long = System::nanoTime
) {
    fun dispatchBatch(
        organizationId: OrganizationId,
        workerId: String,
        leaseDuration: Duration = Duration.ofSeconds(30),
        batchSize: Int = 10,
        shouldContinue: () -> Boolean = { true }
    ): Int {
        if (!shouldContinue()) return 0
        val claims = store.claim(
            organizationId,
            workerId,
            clock.instant(),
            leaseDuration,
            batchSize
        )
        var attempted = 0
        for (delivery in claims) {
            if (!shouldContinue()) break
            val started = nanoTime()
            val result = try {
                sink.deliver(delivery)
            } catch (_: Exception) {
                DeliverySinkResult.RetryableFailure(DeliveryErrorCode.of("SINK_EXCEPTION"))
            }
            val settledAt = clock.instant()
            val settlement = store.settle(delivery, workerId, result, settledAt)
            val outcome = if (settlement == DeliverySettlement.LEASE_CONFLICT) {
                "lease_conflict"
            } else {
                when (result) {
                    DeliverySinkResult.Delivered -> "delivered"
                    is DeliverySinkResult.RetryableFailure ->
                        if (delivery.attemptCount >= MAX_ATTEMPTS) {
                            "dead_letter"
                        } else {
                            "retryable_failure"
                        }
                    is DeliverySinkResult.PermanentFailure -> "dead_letter"
                }
            }
            telemetry.record(
                DeliveryAttemptObservation(
                    destinationId = delivery.destinationId.value,
                    eventId = delivery.eventId.toString(),
                    eventType = delivery.eventType,
                    attemptCount = delivery.attemptCount,
                    outcome = outcome,
                    durationNanos = nanoTime() - started,
                    errorCode = when (result) {
                        DeliverySinkResult.Delivered -> null
                        is DeliverySinkResult.RetryableFailure -> result.code.value
                        is DeliverySinkResult.PermanentFailure -> result.code.value
                    }
                )
            )
            attempted++
        }
        return attempted
    }
}

private data class Candidate(
    val eventId: UUID,
    val destinationId: DeliveryDestinationId,
    val attemptCount: Int,
    val recovered: Boolean,
    val eventType: String,
    val contentType: String,
    val eventJson: String
)

private fun retryDelay(attemptCount: Int): Duration = when (attemptCount) {
    1 -> Duration.ofMinutes(1)
    2 -> Duration.ofMinutes(2)
    3 -> Duration.ofMinutes(4)
    4 -> Duration.ofMinutes(8)
    5 -> Duration.ofMinutes(16)
    6 -> Duration.ofMinutes(32)
    else -> Duration.ofMinutes(60)
}

private fun canonicalCloudEvent(value: String): ByteArray {
    val stored = Json.parseToJsonElement(value).jsonObject
    val data = stored.getValue("data").jsonObject
    val canonical = buildJsonObject {
        copy(stored, "specversion")
        copy(stored, "id")
        copy(stored, "source")
        copy(stored, "type")
        copy(stored, "subject")
        copy(stored, "time")
        copy(stored, "datacontenttype")
        copy(stored, "dataschema")
        copy(stored, "floooworganizationid")
        put("data", buildJsonObject {
            copy(data, "organizationId")
            copy(data, "assessmentId")
            copy(data, "sku")
            copy(data, "observedOn")
            copy(data, "shortageProjected")
            copy(data, "unitsAtRiskAgainstGoal")
            copy(data, "recommendationType")
            copy(data, "expectedUnitsPreserved")
        })
    }
    return Json.encodeToString(canonical).toByteArray(Charsets.UTF_8)
}

private fun kotlinx.serialization.json.JsonObjectBuilder.copy(source: JsonObject, name: String) {
    put(name, source.getValue(name))
}
