package io.flooow.marketplace.operations.economics.reconciliation

import io.flooow.marketplace.operations.economics.EconomicDirection
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerBasis
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerEntryId
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerStage
import io.flooow.marketplace.operations.economics.ledger.FinancialTrace
import io.flooow.marketplace.operations.economics.ledger.FinancialTraceId
import io.flooow.marketplace.operations.economics.ledger.RecordedFinancialLedgerEntry
import io.flooow.organization.OrganizationId
import java.util.Collections
import java.util.EnumMap
import java.util.UUID

data class FinancialReconciliationPolicyVersion(val value: String) {
    init {
        require(POLICY_VERSION_PATTERN.matches(value)) {
            "Financial reconciliation policy version is invalid"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

class FinancialReconciliationPolicy(
    val version: FinancialReconciliationPolicyVersion,
    val currency: MarketplaceCurrency,
    tolerancesByStage: Map<FinancialLedgerStage, MarketplaceMoney>
) {
    val tolerancesByStage: Map<FinancialLedgerStage, MarketplaceMoney> =
        Collections.unmodifiableMap(
            EnumMap<FinancialLedgerStage, MarketplaceMoney>(FinancialLedgerStage::class.java)
                .apply { putAll(tolerancesByStage) }
        )

    init {
        require(tolerancesByStage.keys == FinancialLedgerStage.entries.toSet()) {
            "Financial reconciliation policy must cover every ledger stage"
        }
        require(tolerancesByStage.values.all { it.currency == currency }) {
            "Financial reconciliation tolerance currency must match policy currency"
        }
        require(tolerancesByStage.values.all { it.amount.signum() >= 0 }) {
            "Financial reconciliation tolerances must not be negative"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is FinancialReconciliationPolicy &&
            version == other.version &&
            currency == other.currency &&
            tolerancesByStage == other.tolerancesByStage

    override fun hashCode(): Int {
        var result = version.hashCode()
        result = 31 * result + currency.hashCode()
        result = 31 * result + tolerancesByStage.hashCode()
        return result
    }

    override fun toString(): String = "[REDACTED]"
}

sealed interface FinancialReconciliationSide {
    data object NotObserved : FinancialReconciliationSide {
        override fun toString(): String = "[REDACTED]"
    }

    class Observed internal constructor(
        val netAmount: MarketplaceMoney,
        effectiveEntryIds: Collection<FinancialLedgerEntryId>
    ) : FinancialReconciliationSide {
        val effectiveEntryIds: List<FinancialLedgerEntryId> = Collections.unmodifiableList(
            effectiveEntryIds.sortedWith(FINANCIAL_LEDGER_ENTRY_ID_COMPARATOR)
        )

        init {
            require(effectiveEntryIds.isNotEmpty()) {
                "Observed reconciliation side requires effective entries"
            }
            require(effectiveEntryIds.toSet().size == effectiveEntryIds.size) {
                "Observed reconciliation side entry identifiers must be unique"
            }
        }

        override fun equals(other: Any?): Boolean =
            other is Observed &&
                netAmount == other.netAmount &&
                effectiveEntryIds == other.effectiveEntryIds

        override fun hashCode(): Int = 31 * netAmount.hashCode() + effectiveEntryIds.hashCode()

        override fun toString(): String = "[REDACTED]"
    }
}

sealed interface FinancialReconciliationDifference {
    data object NotComparable : FinancialReconciliationDifference {
        override fun toString(): String = "[REDACTED]"
    }

    class Compared internal constructor(
        val signedDifference: MarketplaceMoney,
        val absoluteDifference: MarketplaceMoney,
        val tolerance: MarketplaceMoney
    ) : FinancialReconciliationDifference {
        init {
            require(
                signedDifference.currency == absoluteDifference.currency &&
                    signedDifference.currency == tolerance.currency
            ) {
                "Financial reconciliation comparison currencies must match"
            }
            require(absoluteDifference.amount.signum() >= 0 && tolerance.amount.signum() >= 0) {
                "Financial reconciliation absolute values must not be negative"
            }
            require(absoluteDifference.amount.compareTo(signedDifference.amount.abs()) == 0) {
                "Financial reconciliation absolute difference must match signed difference"
            }
        }

        override fun equals(other: Any?): Boolean =
            other is Compared &&
                signedDifference == other.signedDifference &&
                absoluteDifference == other.absoluteDifference &&
                tolerance == other.tolerance

        override fun hashCode(): Int {
            var result = signedDifference.hashCode()
            result = 31 * result + absoluteDifference.hashCode()
            result = 31 * result + tolerance.hashCode()
            return result
        }

        override fun toString(): String = "[REDACTED]"
    }
}

enum class FinancialReconciliationStatus {
    PENDING,
    PARTIALLY_RECONCILED,
    DIVERGENCE,
    FULLY_RECONCILED
}

class FinancialReconciliationLine internal constructor(
    val stage: FinancialLedgerStage,
    val expected: FinancialReconciliationSide,
    val actual: FinancialReconciliationSide,
    val difference: FinancialReconciliationDifference,
    val status: FinancialReconciliationStatus
) {
    init {
        require(
            expected !is FinancialReconciliationSide.NotObserved ||
                actual !is FinancialReconciliationSide.NotObserved
        ) {
            "Financial reconciliation line requires at least one observed side"
        }
        val bothObserved = expected is FinancialReconciliationSide.Observed &&
            actual is FinancialReconciliationSide.Observed
        require(bothObserved == (difference is FinancialReconciliationDifference.Compared)) {
            "Financial reconciliation difference must agree with side presence"
        }
        require(status == classifyLine(expected, actual, difference)) {
            "Financial reconciliation line status is inconsistent"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is FinancialReconciliationLine &&
            stage == other.stage &&
            expected == other.expected &&
            actual == other.actual &&
            difference == other.difference &&
            status == other.status

    override fun hashCode(): Int {
        var result = stage.hashCode()
        result = 31 * result + expected.hashCode()
        result = 31 * result + actual.hashCode()
        result = 31 * result + difference.hashCode()
        result = 31 * result + status.hashCode()
        return result
    }

    override fun toString(): String = "[REDACTED]"
}

class FinancialReconciliationAssessment internal constructor(
    val organizationId: OrganizationId,
    val traceId: FinancialTraceId,
    val orderId: MarketplaceOrderId,
    val currency: MarketplaceCurrency,
    val policyVersion: FinancialReconciliationPolicyVersion,
    lines: Collection<FinancialReconciliationLine>,
    val status: FinancialReconciliationStatus
) {
    val lines: List<FinancialReconciliationLine> = Collections.unmodifiableList(
        lines.sortedBy { it.stage.ordinal }
    )

    init {
        require(lines.isNotEmpty()) {
            "Financial reconciliation assessment requires lines"
        }
        require(lines.map { it.stage }.toSet().size == lines.size) {
            "Financial reconciliation stages must be unique"
        }
        require(status == aggregateStatus(lines)) {
            "Financial reconciliation assessment status is inconsistent"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is FinancialReconciliationAssessment &&
            organizationId == other.organizationId &&
            traceId == other.traceId &&
            orderId == other.orderId &&
            currency == other.currency &&
            policyVersion == other.policyVersion &&
            lines == other.lines &&
            status == other.status

    override fun hashCode(): Int {
        var result = organizationId.hashCode()
        result = 31 * result + traceId.hashCode()
        result = 31 * result + orderId.hashCode()
        result = 31 * result + currency.hashCode()
        result = 31 * result + policyVersion.hashCode()
        result = 31 * result + lines.hashCode()
        result = 31 * result + status.hashCode()
        return result
    }

    override fun toString(): String = "[REDACTED]"
}

enum class FinancialReconciliationNotAssessableReason {
    NO_FINANCIAL_FACTS
}

sealed interface FinancialReconciliationResult {
    data class Assessed(val assessment: FinancialReconciliationAssessment) :
        FinancialReconciliationResult {
        override fun toString(): String = "[REDACTED]"
    }

    data class NotAssessable(val reason: FinancialReconciliationNotAssessableReason) :
        FinancialReconciliationResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object PolicyCurrencyMismatch : FinancialReconciliationResult {
        override fun toString(): String = "[REDACTED]"
    }
}

object MarketplaceFinancialReconciliation {
    fun assess(
        trace: FinancialTrace,
        policy: FinancialReconciliationPolicy
    ): FinancialReconciliationResult {
        if (trace.currency != policy.currency) {
            return FinancialReconciliationResult.PolicyCurrencyMismatch
        }

        val correctedEntryIds = trace.entries.mapNotNullTo(mutableSetOf()) {
            it.correctsEntryId
        }
        val effectiveEntries = trace.entries.filter { it.id !in correctedEntryIds }
        if (effectiveEntries.isEmpty()) {
            return FinancialReconciliationResult.NotAssessable(
                FinancialReconciliationNotAssessableReason.NO_FINANCIAL_FACTS
            )
        }

        val lines = FinancialLedgerStage.entries.mapNotNull { stage ->
            val stageEntries = effectiveEntries.filter { it.stage == stage }
            if (stageEntries.isEmpty()) {
                null
            } else {
                line(stage, stageEntries, trace.currency, policy.tolerancesByStage.getValue(stage))
            }
        }

        return FinancialReconciliationResult.Assessed(
            FinancialReconciliationAssessment(
                organizationId = trace.organizationId,
                traceId = trace.id,
                orderId = trace.orderId,
                currency = trace.currency,
                policyVersion = policy.version,
                lines = lines,
                status = aggregateStatus(lines)
            )
        )
    }

    private fun line(
        stage: FinancialLedgerStage,
        entries: List<RecordedFinancialLedgerEntry>,
        currency: MarketplaceCurrency,
        tolerance: MarketplaceMoney
    ): FinancialReconciliationLine {
        val expected = side(entries, FinancialLedgerBasis.EXPECTED, currency)
        val actual = side(entries, FinancialLedgerBasis.ACTUAL, currency)
        val difference = difference(expected, actual, tolerance)
        return FinancialReconciliationLine(
            stage = stage,
            expected = expected,
            actual = actual,
            difference = difference,
            status = classifyLine(expected, actual, difference)
        )
    }

    private fun side(
        entries: List<RecordedFinancialLedgerEntry>,
        basis: FinancialLedgerBasis,
        currency: MarketplaceCurrency
    ): FinancialReconciliationSide {
        val matching = entries.filter { it.basis == basis }
        if (matching.isEmpty()) {
            return FinancialReconciliationSide.NotObserved
        }
        val net = matching.fold(MarketplaceMoney.zero(currency)) { total, entry ->
            when (entry.direction) {
                EconomicDirection.ADDITION -> total + entry.magnitude
                EconomicDirection.DEDUCTION -> total - entry.magnitude
            }
        }
        return FinancialReconciliationSide.Observed(net, matching.map { it.id })
    }

    private fun difference(
        expected: FinancialReconciliationSide,
        actual: FinancialReconciliationSide,
        tolerance: MarketplaceMoney
    ): FinancialReconciliationDifference {
        if (
            expected !is FinancialReconciliationSide.Observed ||
            actual !is FinancialReconciliationSide.Observed
        ) {
            return FinancialReconciliationDifference.NotComparable
        }
        val signed = actual.netAmount - expected.netAmount
        return FinancialReconciliationDifference.Compared(
            signedDifference = signed,
            absoluteDifference = MarketplaceMoney.calculated(signed.currency, signed.amount.abs()),
            tolerance = tolerance
        )
    }
}

private fun classifyLine(
    expected: FinancialReconciliationSide,
    actual: FinancialReconciliationSide,
    difference: FinancialReconciliationDifference
): FinancialReconciliationStatus {
    if (expected is FinancialReconciliationSide.Observed &&
        actual is FinancialReconciliationSide.NotObserved
    ) {
        return FinancialReconciliationStatus.PENDING
    }
    if (expected is FinancialReconciliationSide.NotObserved &&
        actual is FinancialReconciliationSide.Observed
    ) {
        return FinancialReconciliationStatus.DIVERGENCE
    }

    expected as FinancialReconciliationSide.Observed
    actual as FinancialReconciliationSide.Observed
    difference as FinancialReconciliationDifference.Compared

    if (difference.absoluteDifference.amount <= difference.tolerance.amount) {
        return FinancialReconciliationStatus.FULLY_RECONCILED
    }

    val expectedAmount = expected.netAmount.amount
    val actualAmount = actual.netAmount.amount
    val isPartialProgress = expectedAmount.signum() != 0 &&
        actualAmount.signum() == expectedAmount.signum() &&
        actualAmount.signum() != 0 &&
        actualAmount.abs() < expectedAmount.abs()
    return if (isPartialProgress) {
        FinancialReconciliationStatus.PARTIALLY_RECONCILED
    } else {
        FinancialReconciliationStatus.DIVERGENCE
    }
}

private fun aggregateStatus(
    lines: Collection<FinancialReconciliationLine>
): FinancialReconciliationStatus = when {
    lines.any { it.status == FinancialReconciliationStatus.DIVERGENCE } ->
        FinancialReconciliationStatus.DIVERGENCE
    lines.all { it.status == FinancialReconciliationStatus.FULLY_RECONCILED } ->
        FinancialReconciliationStatus.FULLY_RECONCILED
    lines.all { it.status == FinancialReconciliationStatus.PENDING } ->
        FinancialReconciliationStatus.PENDING
    else -> FinancialReconciliationStatus.PARTIALLY_RECONCILED
}

private val FINANCIAL_LEDGER_ENTRY_ID_COMPARATOR =
    Comparator<FinancialLedgerEntryId> { left, right ->
        compareUuidUnsigned(left.valueForPersistence(), right.valueForPersistence())
    }

private fun compareUuidUnsigned(left: UUID, right: UUID): Int {
    val most = java.lang.Long.compareUnsigned(left.mostSignificantBits, right.mostSignificantBits)
    return if (most != 0) {
        most
    } else {
        java.lang.Long.compareUnsigned(left.leastSignificantBits, right.leastSignificantBits)
    }
}

private val POLICY_VERSION_PATTERN = Regex("[a-z0-9][a-z0-9./-]{0,99}")
