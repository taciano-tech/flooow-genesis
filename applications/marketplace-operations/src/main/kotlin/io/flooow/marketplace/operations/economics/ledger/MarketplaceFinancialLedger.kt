package io.flooow.marketplace.operations.economics.ledger

import io.flooow.marketplace.operations.economics.EconomicDirection
import io.flooow.marketplace.operations.economics.EconomicExternalReferenceState
import io.flooow.marketplace.operations.economics.EconomicSource
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.organization.OrganizationId
import java.time.Instant
import java.util.Collections
import java.util.UUID

@JvmInline
value class FinancialTraceId(val value: UUID) {
    fun valueForPersistence(): UUID = value
    override fun toString(): String = "[INTERNAL]"

    companion object {
        fun parse(value: String): FinancialTraceId = FinancialTraceId(parseCanonicalUuid(value))
        fun of(value: UUID): FinancialTraceId = FinancialTraceId(value)
    }
}

@JvmInline
value class FinancialLedgerEntryId(val value: UUID) {
    fun valueForPersistence(): UUID = value
    override fun toString(): String = "[INTERNAL]"

    companion object {
        fun parse(value: String): FinancialLedgerEntryId =
            FinancialLedgerEntryId(parseCanonicalUuid(value))

        fun of(value: UUID): FinancialLedgerEntryId = FinancialLedgerEntryId(value)
    }
}

@JvmInline
value class FinancialTraceOpenRequestId(val value: UUID) {
    fun valueForPersistence(): UUID = value
    override fun toString(): String = "[INTERNAL]"

    companion object {
        fun parse(value: String): FinancialTraceOpenRequestId =
            FinancialTraceOpenRequestId(parseCanonicalUuid(value))

        fun of(value: UUID): FinancialTraceOpenRequestId = FinancialTraceOpenRequestId(value)
    }
}

@JvmInline
value class FinancialLedgerAppendRequestId(val value: UUID) {
    fun valueForPersistence(): UUID = value
    override fun toString(): String = "[INTERNAL]"

    companion object {
        fun parse(value: String): FinancialLedgerAppendRequestId =
            FinancialLedgerAppendRequestId(parseCanonicalUuid(value))

        fun of(value: UUID): FinancialLedgerAppendRequestId = FinancialLedgerAppendRequestId(value)
    }
}

private fun parseCanonicalUuid(value: String): UUID {
    val parsed = try {
        UUID.fromString(value)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Identifier must be a canonical lowercase UUID")
    }
    require(parsed.toString() == value) {
        "Identifier must be a canonical lowercase UUID"
    }
    return parsed
}

data class OpenFinancialTrace(
    val organizationId: OrganizationId,
    val requestId: FinancialTraceOpenRequestId,
    val orderId: MarketplaceOrderId,
    val marketplace: MarketplaceKey,
    val externalOrderId: MarketplaceExternalOrderId,
    val currency: MarketplaceCurrency
) {
    override fun toString(): String = "[REDACTED]"
}

enum class FinancialLedgerStage {
    SALE,
    MARKETPLACE_COMMISSION,
    MARKETPLACE_FEE,
    SHIPPING,
    ADVERTISING,
    TAX,
    PRODUCT_COST,
    FINANCIAL_COST,
    OTHER_ADJUSTMENT,
    SETTLEMENT,
    PAYMENT_ACCOUNT,
    BANK
}

enum class FinancialLedgerBasis {
    EXPECTED,
    ACTUAL
}

data class FinancialLedgerEntryDraft(
    val organizationId: OrganizationId,
    val requestId: FinancialLedgerAppendRequestId,
    val traceId: FinancialTraceId,
    val stage: FinancialLedgerStage,
    val basis: FinancialLedgerBasis,
    val direction: EconomicDirection,
    val magnitude: MarketplaceMoney,
    val source: EconomicSource,
    val occurredAt: Instant,
    val correctsEntryId: FinancialLedgerEntryId? = null
) {
    init {
        require(magnitude.amount.signum() >= 0) {
            "Financial ledger magnitude must not be negative"
        }
        require(occurredAt.nano % 1_000 == 0) {
            "Financial ledger occurrence time must use microsecond precision"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

data class RecordedFinancialLedgerEntry(
    val organizationId: OrganizationId,
    val id: FinancialLedgerEntryId,
    val requestId: FinancialLedgerAppendRequestId,
    val traceId: FinancialTraceId,
    val stage: FinancialLedgerStage,
    val basis: FinancialLedgerBasis,
    val direction: EconomicDirection,
    val magnitude: MarketplaceMoney,
    val source: EconomicSource,
    val occurredAt: Instant,
    val recordedAt: Instant,
    val correctsEntryId: FinancialLedgerEntryId? = null
) {
    init {
        require(magnitude.amount.signum() >= 0) {
            "Financial ledger magnitude must not be negative"
        }
        require(occurredAt.nano % 1_000 == 0 && recordedAt.nano % 1_000 == 0) {
            "Financial ledger timestamps must use microsecond precision"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

class FinancialTrace(
    val organizationId: OrganizationId,
    val id: FinancialTraceId,
    val requestId: FinancialTraceOpenRequestId,
    val orderId: MarketplaceOrderId,
    val marketplace: MarketplaceKey,
    val externalOrderId: MarketplaceExternalOrderId,
    val currency: MarketplaceCurrency,
    val openedAt: Instant,
    entries: Collection<RecordedFinancialLedgerEntry>
) {
    val entries: List<RecordedFinancialLedgerEntry> = Collections.unmodifiableList(
        entries.sortedWith(
            compareBy<RecordedFinancialLedgerEntry> { it.recordedAt }
                .thenComparator { left, right -> compareUuidUnsigned(left.id.value, right.id.value) }
        )
    )

    init {
        require(openedAt.nano % 1_000 == 0) {
            "Financial trace opening time must use microsecond precision"
        }
        require(entries.all { it.organizationId == organizationId && it.traceId == id }) {
            "Financial ledger entries must belong to their trace"
        }
        require(entries.all { it.magnitude.currency == currency }) {
            "Financial ledger entry currency must match its trace"
        }
        require(entries.map { it.id }.toSet().size == entries.size) {
            "Financial ledger entry identifiers must be unique"
        }
        require(entries.map { it.requestId }.toSet().size == entries.size) {
            "Financial ledger append request identifiers must be unique"
        }
        requirePresentSourceFactsUnique()
        requireCorrectionChainsValid()
    }

    private fun requirePresentSourceFactsUnique() {
        val keys = entries.mapNotNull { entry ->
            val state = entry.source.externalReference
            if (state is EconomicExternalReferenceState.Present) {
                PresentFinancialSourceFact(
                    entry.source.kind,
                    entry.source.systemKey,
                    state.reference,
                    entry.stage,
                    entry.basis
                )
            } else {
                null
            }
        }
        require(keys.toSet().size == keys.size) {
            "Present financial source facts must be unique"
        }
    }

    private fun requireCorrectionChainsValid() {
        val byId = entries.associateBy { it.id }
        val targets = entries.mapNotNull { it.correctsEntryId }
        require(targets.toSet().size == targets.size) {
            "A financial ledger entry may have only one direct correction"
        }
        entries.forEach { entry ->
            val targetId = entry.correctsEntryId ?: return@forEach
            val target = requireNotNull(byId[targetId]) {
                "Financial ledger correction target must exist in the trace"
            }
            require(target.stage == entry.stage && target.basis == entry.basis) {
                "Financial ledger correction target must share stage and basis"
            }
            require(target.id != entry.id) {
                "Financial ledger entry cannot correct itself"
            }
        }
        entries.forEach { entry ->
            val visited = mutableSetOf<FinancialLedgerEntryId>()
            var current: RecordedFinancialLedgerEntry? = entry
            while (current?.correctsEntryId != null) {
                require(visited.add(current.id)) { "Financial ledger correction cycle" }
                current = byId[current.correctsEntryId]
            }
        }
    }

    override fun equals(other: Any?): Boolean =
        other is FinancialTrace &&
            organizationId == other.organizationId &&
            id == other.id &&
            requestId == other.requestId &&
            orderId == other.orderId &&
            marketplace == other.marketplace &&
            externalOrderId == other.externalOrderId &&
            currency == other.currency &&
            openedAt == other.openedAt &&
            entries == other.entries

    override fun hashCode(): Int {
        var result = organizationId.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + requestId.hashCode()
        result = 31 * result + orderId.hashCode()
        result = 31 * result + marketplace.hashCode()
        result = 31 * result + externalOrderId.hashCode()
        result = 31 * result + currency.hashCode()
        result = 31 * result + openedAt.hashCode()
        result = 31 * result + entries.hashCode()
        return result
    }

    override fun toString(): String = "[REDACTED]"
}

sealed interface FinancialTraceOpenResult {
    data class Opened(val traceId: FinancialTraceId) : FinancialTraceOpenResult {
        override fun toString(): String = "[REDACTED]"
    }

    data class AlreadyOpen(val traceId: FinancialTraceId) : FinancialTraceOpenResult {
        override fun toString(): String = "[REDACTED]"
    }

    data class OrderAlreadyTraced(val traceId: FinancialTraceId) : FinancialTraceOpenResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object OrganizationUnavailable : FinancialTraceOpenResult
    data object Conflict : FinancialTraceOpenResult
    data object IntegrityFailure : FinancialTraceOpenResult
}

sealed interface FinancialLedgerAppendResult {
    data class Appended(val entryId: FinancialLedgerEntryId) : FinancialLedgerAppendResult {
        override fun toString(): String = "[REDACTED]"
    }

    data class AlreadyAppended(val entryId: FinancialLedgerEntryId) :
        FinancialLedgerAppendResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object TraceUnavailable : FinancialLedgerAppendResult
    data object OrganizationUnavailable : FinancialLedgerAppendResult
    data object CorrectionTargetUnavailable : FinancialLedgerAppendResult
    data object Conflict : FinancialLedgerAppendResult
    data object IntegrityFailure : FinancialLedgerAppendResult
}

sealed interface FinancialTraceReadResult {
    data class Found(val trace: FinancialTrace) : FinancialTraceReadResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object NotFound : FinancialTraceReadResult
    data object IntegrityFailure : FinancialTraceReadResult
}

interface MarketplaceFinancialLedgerRepository {
    fun open(
        command: OpenFinancialTrace,
        traceId: FinancialTraceId
    ): FinancialTraceOpenResult

    fun append(
        draft: FinancialLedgerEntryDraft,
        entryId: FinancialLedgerEntryId
    ): FinancialLedgerAppendResult

    fun find(
        organizationId: OrganizationId,
        traceId: FinancialTraceId
    ): FinancialTraceReadResult

    fun findByOrder(
        organizationId: OrganizationId,
        orderId: MarketplaceOrderId
    ): FinancialTraceReadResult
}

private data class PresentFinancialSourceFact(
    val kind: io.flooow.marketplace.operations.economics.EconomicSourceKind,
    val systemKey: io.flooow.marketplace.operations.economics.EconomicSourceSystemKey,
    val externalReference: io.flooow.marketplace.operations.economics.EconomicExternalReference,
    val stage: FinancialLedgerStage,
    val basis: FinancialLedgerBasis
)

private fun compareUuidUnsigned(left: UUID, right: UUID): Int {
    val most = java.lang.Long.compareUnsigned(left.mostSignificantBits, right.mostSignificantBits)
    return if (most != 0) {
        most
    } else {
        java.lang.Long.compareUnsigned(left.leastSignificantBits, right.leastSignificantBits)
    }
}
