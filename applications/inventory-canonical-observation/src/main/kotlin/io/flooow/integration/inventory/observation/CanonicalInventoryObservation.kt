package io.flooow.integration.inventory.observation

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.inventory.mapping.InventoryMappingDecisionId
import io.flooow.integration.inventory.mapping.InventoryMappingTarget
import io.flooow.integration.inventory.mapping.QuantityFactor
import io.flooow.integration.inventory.source.InventorySourceBalanceCapability
import io.flooow.integration.inventory.source.SourceQuantity
import io.flooow.organization.OrganizationId
import java.math.BigInteger
import java.time.Instant
import java.util.UUID

sealed class ObservationUuid protected constructor(private val value: UUID) {
    fun valueForPersistence(): UUID = value
    protected fun sameValue(other: ObservationUuid) = value == other.value
    protected fun valueHash() = value.hashCode()
    override fun toString() = "[INTERNAL]"

    companion object {
        fun canonical(value: String): UUID {
            val parsed = UUID.fromString(value)
            require(parsed.toString() == value) { "Invalid observation identifier" }
            return parsed
        }
    }
}

class CanonicalInventoryObservationId private constructor(value: UUID) : ObservationUuid(value) {
    override fun equals(other: Any?) =
        other is CanonicalInventoryObservationId && sameValue(other)
    override fun hashCode() = valueHash()
    companion object {
        fun of(value: UUID) = CanonicalInventoryObservationId(value)
        fun parse(value: String) = CanonicalInventoryObservationId(canonical(value))
    }
}

class CanonicalInventoryObservationCorrelationId private constructor(value: UUID) :
    ObservationUuid(value) {
    override fun equals(other: Any?) =
        other is CanonicalInventoryObservationCorrelationId && sameValue(other)
    override fun hashCode() = valueHash()
    companion object {
        fun of(value: UUID) = CanonicalInventoryObservationCorrelationId(value)
        fun parse(value: String) = CanonicalInventoryObservationCorrelationId(canonical(value))
    }
}

@ConsistentCopyVisibility
data class ExactInventoryQuantity private constructor(
    private val numerator: BigInteger,
    private val denominator: Long
) {
    fun numeratorForPersistence(): BigInteger = numerator
    fun denominatorForPersistence(): Long = denominator
    override fun toString() = "[REDACTED]"

    companion object {
        const val MAX_DENOMINATOR = 1_000_000_000_000_000L
        private val maximumNumeratorMagnitude = BigInteger.TEN.pow(34)

        fun from(source: SourceQuantity, factor: QuantityFactor): ExactInventoryQuantity {
            val decimal = source.valueForPersistence()
            val rawNumerator = decimal.unscaledValue()
                .multiply(BigInteger.valueOf(factor.numerator))
            val rawDenominator = BigInteger.TEN.pow(decimal.scale())
                .multiply(BigInteger.valueOf(factor.denominator))
            return reduced(rawNumerator, rawDenominator)
        }

        fun fromPersistence(numerator: BigInteger, denominator: Long) =
            reduced(numerator, BigInteger.valueOf(denominator))

        private fun reduced(
            rawNumerator: BigInteger,
            rawDenominator: BigInteger
        ): ExactInventoryQuantity {
            require(rawDenominator.signum() > 0) { "Invalid exact quantity" }
            if (rawNumerator == BigInteger.ZERO) return ExactInventoryQuantity(BigInteger.ZERO, 1)
            val divisor = rawNumerator.abs().gcd(rawDenominator)
            val numerator = rawNumerator.divide(divisor)
            val denominator = rawDenominator.divide(divisor)
            require(numerator.abs() < maximumNumeratorMagnitude) { "Invalid exact quantity" }
            require(denominator <= BigInteger.valueOf(MAX_DENOMINATOR)) {
                "Invalid exact quantity"
            }
            return ExactInventoryQuantity(numerator, denominator.longValueExact())
        }
    }
}

data class CanonicalInventoryMeasures(
    val availableToSell: ExactInventoryQuantity? = null,
    val onHand: ExactInventoryQuantity? = null,
    val reserved: ExactInventoryQuantity? = null,
    val pendingInbound: ExactInventoryQuantity? = null,
    val pendingOutbound: ExactInventoryQuantity? = null
) {
    init {
        require(
            listOf(availableToSell, onHand, reserved, pendingInbound, pendingOutbound)
                .any { it != null }
        ) { "Canonical inventory observation requires at least one measure" }
    }

    override fun toString() = "CanonicalInventoryMeasures([REDACTED])"
}

data class CanonicalInventorySourcePointer(
    val connectionId: IntegrationConnectionId,
    val capability: String = InventorySourceBalanceCapability.VALUE,
    val inputProgressVersion: Long,
    val recordOrdinal: Int
) {
    init {
        require(capability == InventorySourceBalanceCapability.VALUE) {
            "Inventory observation capability unavailable"
        }
        require(inputProgressVersion >= 0) { "Invalid source pointer" }
        require(recordOrdinal in 0..999) { "Invalid source pointer" }
    }

    override fun toString() = "CanonicalInventorySourcePointer([INTERNAL])"
}

data class CanonicalInventoryObservation(
    val id: CanonicalInventoryObservationId,
    val organizationId: OrganizationId,
    val sourcePointer: CanonicalInventorySourcePointer,
    val projectionRevision: Int,
    val mappingDecisionId: InventoryMappingDecisionId,
    val mappingRevision: Int,
    val target: InventoryMappingTarget,
    val measures: CanonicalInventoryMeasures,
    val sourceUpdatedAt: Instant?,
    val sourceCommittedAt: Instant,
    val projectedAt: Instant,
    val correlationId: CanonicalInventoryObservationCorrelationId,
    val supersedesObservationId: CanonicalInventoryObservationId? = null
) {
    init {
        require(projectionRevision > 0 && mappingRevision > 0) {
            "Invalid observation revision"
        }
        require(
            (projectionRevision == 1 && supersedesObservationId == null) ||
                (projectionRevision > 1 && supersedesObservationId != null)
        ) { "Invalid observation predecessor" }
    }

    override fun toString() = "CanonicalInventoryObservation([REDACTED])"
}

sealed interface CanonicalInventoryProjectionResult {
    class Projected(
        val observationId: CanonicalInventoryObservationId,
        val projectionRevision: Int
    ) : CanonicalInventoryProjectionResult {
        override fun toString() = "Projected([INTERNAL])"
    }

    class AlreadyProjected(
        val observationId: CanonicalInventoryObservationId,
        val projectionRevision: Int
    ) : CanonicalInventoryProjectionResult {
        override fun toString() = "AlreadyProjected([INTERNAL])"
    }

    data object Unmapped : CanonicalInventoryProjectionResult
    data object SourceUnavailable : CanonicalInventoryProjectionResult
    data object TargetUnavailable : CanonicalInventoryProjectionResult
    data object Conflict : CanonicalInventoryProjectionResult
    data object IntegrityFailure : CanonicalInventoryProjectionResult
}

fun interface ObservationIdentifierFactory<T> { fun create(): T }

interface CanonicalInventoryObservationRepository {
    fun project(
        organizationId: OrganizationId,
        sourcePointer: CanonicalInventorySourcePointer,
        observationId: CanonicalInventoryObservationId,
        correlationId: CanonicalInventoryObservationCorrelationId
    ): CanonicalInventoryProjectionResult

    fun find(
        organizationId: OrganizationId,
        observationId: CanonicalInventoryObservationId
    ): CanonicalInventoryObservation?

    fun history(
        organizationId: OrganizationId,
        sourcePointer: CanonicalInventorySourcePointer
    ): List<CanonicalInventoryObservation>
}

class CanonicalInventoryObservationService(
    private val repository: CanonicalInventoryObservationRepository,
    private val observationIds: ObservationIdentifierFactory<CanonicalInventoryObservationId> =
        ObservationIdentifierFactory { CanonicalInventoryObservationId.of(UUID.randomUUID()) },
    private val correlationIds:
        ObservationIdentifierFactory<CanonicalInventoryObservationCorrelationId> =
        ObservationIdentifierFactory {
            CanonicalInventoryObservationCorrelationId.of(UUID.randomUUID())
        }
) {
    fun project(
        organizationId: OrganizationId,
        sourcePointer: CanonicalInventorySourcePointer,
        correlationId: CanonicalInventoryObservationCorrelationId = correlationIds.create()
    ) = repository.project(organizationId, sourcePointer, observationIds.create(), correlationId)

    fun find(organizationId: OrganizationId, observationId: CanonicalInventoryObservationId) =
        repository.find(organizationId, observationId)

    fun history(organizationId: OrganizationId, sourcePointer: CanonicalInventorySourcePointer) =
        repository.history(organizationId, sourcePointer)
}
