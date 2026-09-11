package io.flooow.marketplace.operations.economics.evidence

import io.flooow.marketplace.operations.economics.EconomicComponent
import io.flooow.marketplace.operations.economics.EconomicComponentCoverage
import io.flooow.marketplace.operations.economics.EconomicComponentType
import io.flooow.marketplace.operations.economics.EconomicExternalReference
import io.flooow.marketplace.operations.economics.EconomicExternalReferenceState
import io.flooow.marketplace.operations.economics.EconomicSource
import io.flooow.marketplace.operations.economics.EconomicSourceKind
import io.flooow.marketplace.operations.economics.EconomicSourceSystemKey
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.organization.OrganizationId
import java.time.Instant
import java.util.Collections
import java.util.UUID

data class MarketplaceEconomicEvidenceSubject(
    val organizationId: OrganizationId,
    val orderId: MarketplaceOrderId,
    val marketplace: MarketplaceKey,
    val externalOrderId: MarketplaceExternalOrderId,
    val currency: MarketplaceCurrency
) {
    override fun toString(): String = "[REDACTED]"
}

@JvmInline
value class MarketplaceEconomicEvidenceObservationId internal constructor(internal val value: UUID) :
    Comparable<MarketplaceEconomicEvidenceObservationId> {
    override fun compareTo(other: MarketplaceEconomicEvidenceObservationId): Int =
        compareUuid(value, other.value)

    override fun toString(): String = "[INTERNAL]"

    companion object {
        fun parse(value: String): MarketplaceEconomicEvidenceObservationId {
            val parsed = try {
                UUID.fromString(value)
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Observation identifier must be a canonical lowercase UUID")
            }
            require(parsed.toString() == value) {
                "Observation identifier must be a canonical lowercase UUID"
            }
            return MarketplaceEconomicEvidenceObservationId(parsed)
        }
    }
}

enum class MarketplaceEconomicEvidenceFamily {
    MARKETPLACE_ORDER,
    MARKETPLACE_PAYMENT,
    MARKETPLACE_SHIPPING,
    PRODUCT_COST,
    FISCAL_INVOICE,
    FISCAL_TAX,
    ADS_IDENTITY,
    ADS_ALLOCATION;

    override fun toString(): String = "[REDACTED]"
}

data class MarketplaceEconomicComponentObservation(
    val id: MarketplaceEconomicEvidenceObservationId,
    val subject: MarketplaceEconomicEvidenceSubject,
    val family: MarketplaceEconomicEvidenceFamily,
    val component: EconomicComponent,
    val coverageClaim: EconomicComponentCoverage,
    val observedAt: Instant
) {
    init {
        require(component.organizationId == subject.organizationId) {
            "Economic evidence organization must match its subject"
        }
        require(component.orderId == subject.orderId) {
            "Economic evidence order must match its subject"
        }
        require(component.magnitude.currency == subject.currency) {
            "Economic evidence currency must match its subject"
        }
        require(
            coverageClaim == EconomicComponentCoverage.COMPLETE ||
                coverageClaim == EconomicComponentCoverage.PARTIAL
        ) {
            "Economic evidence coverage must be complete or partial"
        }
        requireMicrosecondPrecision(component.occurredAt, "Economic evidence occurrence time")
        requireMicrosecondPrecision(observedAt, "Economic evidence observation time")
        requireSourceTimeOrder(component.source, component.occurredAt, observedAt)
        require(isPermittedFinancialCombination(family, component.type)) {
            "Economic evidence family and component type are incompatible"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

data class MarketplaceEconomicOrderOccurrenceObservation(
    val id: MarketplaceEconomicEvidenceObservationId,
    val subject: MarketplaceEconomicEvidenceSubject,
    val source: EconomicSource,
    val occurredAt: Instant,
    val observedAt: Instant
) {
    init {
        requireMicrosecondPrecision(occurredAt, "Economic order occurrence time")
        requireMicrosecondPrecision(observedAt, "Economic order occurrence observation time")
        requireSourceTimeOrder(source, occurredAt, observedAt)
    }

    override fun toString(): String = "[REDACTED]"
}

enum class MarketplaceEconomicExternalIdentityKind {
    MARKETPLACE_PAYMENT,
    ERP_ORDER,
    FISCAL_INVOICE,
    MARKETPLACE_ITEM_TO_AD_GROUP;

    override fun toString(): String = "[REDACTED]"
}

data class MarketplaceEconomicExternalIdentityObservation(
    val id: MarketplaceEconomicEvidenceObservationId,
    val subject: MarketplaceEconomicEvidenceSubject,
    val family: MarketplaceEconomicEvidenceFamily,
    val kind: MarketplaceEconomicExternalIdentityKind,
    val anchorReference: EconomicExternalReference,
    val linkedSystemKey: EconomicSourceSystemKey,
    val linkedReference: EconomicExternalReference,
    val source: EconomicSource,
    val occurredAt: Instant,
    val observedAt: Instant
) {
    init {
        require(isPermittedIdentityCombination(family, kind)) {
            "Economic evidence family and identity kind are incompatible"
        }
        requireMicrosecondPrecision(occurredAt, "Economic identity occurrence time")
        requireMicrosecondPrecision(observedAt, "Economic identity observation time")
        requireSourceTimeOrder(source, occurredAt, observedAt)
    }

    override fun toString(): String = "[REDACTED]"
}

enum class MarketplaceEconomicEvidenceAttemptOutcome {
    NO_EVIDENCE,
    AMBIGUOUS,
    TEMPORARY_FAILURE;

    override fun toString(): String = "[REDACTED]"
}

data class MarketplaceEconomicEvidenceCollectionAttempt(
    val id: MarketplaceEconomicEvidenceObservationId,
    val subject: MarketplaceEconomicEvidenceSubject,
    val family: MarketplaceEconomicEvidenceFamily,
    val sourceSystemKey: EconomicSourceSystemKey,
    val outcome: MarketplaceEconomicEvidenceAttemptOutcome,
    val attemptedAt: Instant
) {
    init {
        requireMicrosecondPrecision(attemptedAt, "Economic evidence attempt time")
    }

    override fun toString(): String = "[REDACTED]"
}

sealed interface MarketplaceIndependentEconomicFact {
    val id: MarketplaceEconomicEvidenceObservationId
    val subject: MarketplaceEconomicEvidenceSubject
    val family: MarketplaceEconomicEvidenceFamily
    val observedAt: Instant

    data class Component(
        val observation: MarketplaceEconomicComponentObservation
    ) : MarketplaceIndependentEconomicFact {
        override val id: MarketplaceEconomicEvidenceObservationId get() = observation.id
        override val subject: MarketplaceEconomicEvidenceSubject get() = observation.subject
        override val family: MarketplaceEconomicEvidenceFamily get() = observation.family
        override val observedAt: Instant get() = observation.observedAt

        override fun toString(): String = "[REDACTED]"
    }

    data class ExternalIdentity(
        val observation: MarketplaceEconomicExternalIdentityObservation
    ) : MarketplaceIndependentEconomicFact {
        override val id: MarketplaceEconomicEvidenceObservationId get() = observation.id
        override val subject: MarketplaceEconomicEvidenceSubject get() = observation.subject
        override val family: MarketplaceEconomicEvidenceFamily get() = observation.family
        override val observedAt: Instant get() = observation.observedAt

        override fun toString(): String = "[REDACTED]"
    }

    data class OrderOccurrence(
        val observation: MarketplaceEconomicOrderOccurrenceObservation
    ) : MarketplaceIndependentEconomicFact {
        override val id: MarketplaceEconomicEvidenceObservationId get() = observation.id
        override val subject: MarketplaceEconomicEvidenceSubject get() = observation.subject
        override val family: MarketplaceEconomicEvidenceFamily
            get() = MarketplaceEconomicEvidenceFamily.MARKETPLACE_ORDER
        override val observedAt: Instant get() = observation.observedAt

        override fun toString(): String = "[REDACTED]"
    }
}

enum class MarketplaceEconomicEvidenceCorrectionReason {
    SOURCE_CORRECTION,
    MAPPING_CORRECTION,
    VERIFIED_MANUAL_CORRECTION;

    override fun toString(): String = "[REDACTED]"
}

data class MarketplaceEconomicEvidenceCorrection(
    val id: MarketplaceEconomicEvidenceObservationId,
    val subject: MarketplaceEconomicEvidenceSubject,
    val replacement: MarketplaceIndependentEconomicFact,
    val supersedesObservationId: MarketplaceEconomicEvidenceObservationId,
    val reason: MarketplaceEconomicEvidenceCorrectionReason,
    val observedAt: Instant
) {
    init {
        require(id != supersedesObservationId && replacement.id != supersedesObservationId && id != replacement.id) {
            "Correction, replacement, and superseded identifiers must be distinct"
        }
        require(replacement.subject == subject) {
            "Correction replacement must belong to its subject"
        }
        requireMicrosecondPrecision(observedAt, "Economic evidence correction time")
        require(!observedAt.isBefore(replacement.observedAt)) {
            "Correction time must not precede replacement observation time"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

class MarketplaceIndependentEconomicEvidence internal constructor(
    val subject: MarketplaceEconomicEvidenceSubject,
    facts: Collection<MarketplaceIndependentEconomicFact>,
    attempts: Collection<MarketplaceEconomicEvidenceCollectionAttempt>,
    corrections: Collection<MarketplaceEconomicEvidenceCorrection>
) {
    val facts: List<MarketplaceIndependentEconomicFact> = Collections.unmodifiableList(
        facts.sortedWith(FACT_COMPARATOR)
    )
    val attempts: List<MarketplaceEconomicEvidenceCollectionAttempt> = Collections.unmodifiableList(
        attempts.sortedWith(ATTEMPT_COMPARATOR)
    )
    val corrections: List<MarketplaceEconomicEvidenceCorrection> = Collections.unmodifiableList(
        corrections.sortedWith(CORRECTION_COMPARATOR)
    )
    val historicalFacts: List<MarketplaceIndependentEconomicFact> get() = facts
    val activeFacts: List<MarketplaceIndependentEconomicFact> = Collections.unmodifiableList(
        activeFacts(this.facts, this.corrections)
    )

    init {
        validateSubjects()
        validatePrimaryIdentifiers()
        validateCorrections()
        validateActiveSourceFacts()
    }

    private fun validateSubjects() {
        require(facts.all { it.subject == subject }) {
            "Every economic fact must belong to the aggregate subject"
        }
        require(attempts.all { it.subject == subject }) {
            "Every economic attempt must belong to the aggregate subject"
        }
        require(corrections.all { it.subject == subject && it.replacement.subject == subject }) {
            "Every economic correction must belong to the aggregate subject"
        }
    }

    private fun validatePrimaryIdentifiers() {
        val identifiers = facts.map { it.id } + attempts.map { it.id } + corrections.map { it.id }
        require(identifiers.toSet().size == identifiers.size) {
            "Economic evidence identifiers must be globally unique"
        }
    }

    private fun validateCorrections() {
        val factsById = facts.associateBy { it.id }
        require(corrections.map { it.supersedesObservationId }.toSet().size == corrections.size) {
            "An economic fact may be superseded only once"
        }
        corrections.forEach { correction ->
            val supersededFact = factsById[correction.supersedesObservationId]
            require(supersededFact != null) {
                "Correction target must be a retained economic fact"
            }
            require(factsById[correction.replacement.id] == correction.replacement) {
                "Correction replacement must be retained exactly"
            }
            require(!correction.observedAt.isBefore(supersededFact.observedAt)) {
                "Correction time must not precede superseded observation time"
            }
        }

        val replacements = corrections.associate {
            it.supersedesObservationId to it.replacement.id
        }
        facts.forEach { fact ->
            val visited = mutableSetOf<MarketplaceEconomicEvidenceObservationId>()
            var current: MarketplaceEconomicEvidenceObservationId? = fact.id
            while (current != null) {
                require(visited.add(current)) {
                    "Economic evidence correction chain must not contain a cycle"
                }
                current = replacements[current]
            }
        }
    }

    private fun validateActiveSourceFacts() {
        val keys = activeFacts.mapNotNull(::canonicalSourceFactKey)
        require(keys.toSet().size == keys.size) {
            "Active economic source facts must be unique"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is MarketplaceIndependentEconomicEvidence &&
            subject == other.subject &&
            facts == other.facts &&
            attempts == other.attempts &&
            corrections == other.corrections

    override fun hashCode(): Int {
        var result = subject.hashCode()
        result = 31 * result + facts.hashCode()
        result = 31 * result + attempts.hashCode()
        result = 31 * result + corrections.hashCode()
        return result
    }

    override fun toString(): String = "[REDACTED]"

    companion object {
        fun empty(subject: MarketplaceEconomicEvidenceSubject): MarketplaceIndependentEconomicEvidence =
            MarketplaceIndependentEconomicEvidence(subject, emptyList(), emptyList(), emptyList())
    }
}

sealed interface MarketplaceIndependentEconomicEvidenceUpdate {
    val subject: MarketplaceEconomicEvidenceSubject

    data class ObserveFact(val fact: MarketplaceIndependentEconomicFact) :
        MarketplaceIndependentEconomicEvidenceUpdate {
        override val subject: MarketplaceEconomicEvidenceSubject get() = fact.subject
        override fun toString(): String = "[REDACTED]"
    }

    data class RecordAttempt(val attempt: MarketplaceEconomicEvidenceCollectionAttempt) :
        MarketplaceIndependentEconomicEvidenceUpdate {
        override val subject: MarketplaceEconomicEvidenceSubject get() = attempt.subject
        override fun toString(): String = "[REDACTED]"
    }

    data class Correct(val correction: MarketplaceEconomicEvidenceCorrection) :
        MarketplaceIndependentEconomicEvidenceUpdate {
        override val subject: MarketplaceEconomicEvidenceSubject get() = correction.subject
        override fun toString(): String = "[REDACTED]"
    }
}

sealed interface MarketplaceIndependentEconomicEvidenceResult {
    data class Applied(val evidence: MarketplaceIndependentEconomicEvidence) :
        MarketplaceIndependentEconomicEvidenceResult {
        override fun toString(): String = "[REDACTED]"
    }

    data class Duplicate(val evidence: MarketplaceIndependentEconomicEvidence) :
        MarketplaceIndependentEconomicEvidenceResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object SubjectMismatch : MarketplaceIndependentEconomicEvidenceResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object IdentifierConflict : MarketplaceIndependentEconomicEvidenceResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object SourceFactConflict : MarketplaceIndependentEconomicEvidenceResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object SupersededFactNotFound : MarketplaceIndependentEconomicEvidenceResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object SupersededTargetNotFact : MarketplaceIndependentEconomicEvidenceResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object FactAlreadySuperseded : MarketplaceIndependentEconomicEvidenceResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object ReplacementIdentifierConflict : MarketplaceIndependentEconomicEvidenceResult {
        override fun toString(): String = "[REDACTED]"
    }

    data object ReplacementSourceFactConflict : MarketplaceIndependentEconomicEvidenceResult {
        override fun toString(): String = "[REDACTED]"
    }
}

object MarketplaceIndependentEconomicEvidenceMerger {
    fun apply(
        current: MarketplaceIndependentEconomicEvidence,
        update: MarketplaceIndependentEconomicEvidenceUpdate
    ): MarketplaceIndependentEconomicEvidenceResult {
        if (update.subject != current.subject) {
            return MarketplaceIndependentEconomicEvidenceResult.SubjectMismatch
        }
        return when (update) {
            is MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact -> observe(current, update.fact)
            is MarketplaceIndependentEconomicEvidenceUpdate.RecordAttempt -> record(current, update.attempt)
            is MarketplaceIndependentEconomicEvidenceUpdate.Correct -> correct(current, update.correction)
        }
    }

    private fun observe(
        current: MarketplaceIndependentEconomicEvidence,
        fact: MarketplaceIndependentEconomicFact
    ): MarketplaceIndependentEconomicEvidenceResult {
        classifyPrimaryIdentifier(current, fact.id, fact)?.let { return it }
        val sourceFactKey = canonicalSourceFactKey(fact)
        if (sourceFactKey != null) {
            current.activeFacts
                .firstOrNull { canonicalSourceFactKey(it) == sourceFactKey }
                ?.let { existing ->
                    return if (sameCanonicalMeaning(existing, fact)) {
                        MarketplaceIndependentEconomicEvidenceResult.Duplicate(current)
                    } else {
                        MarketplaceIndependentEconomicEvidenceResult.SourceFactConflict
                    }
                }
        }
        return MarketplaceIndependentEconomicEvidenceResult.Applied(
            MarketplaceIndependentEconomicEvidence(
                current.subject,
                current.facts + fact,
                current.attempts,
                current.corrections
            )
        )
    }

    private fun record(
        current: MarketplaceIndependentEconomicEvidence,
        attempt: MarketplaceEconomicEvidenceCollectionAttempt
    ): MarketplaceIndependentEconomicEvidenceResult {
        classifyPrimaryIdentifier(current, attempt.id, attempt)?.let { return it }
        return MarketplaceIndependentEconomicEvidenceResult.Applied(
            MarketplaceIndependentEconomicEvidence(
                current.subject,
                current.facts,
                current.attempts + attempt,
                current.corrections
            )
        )
    }

    private fun correct(
        current: MarketplaceIndependentEconomicEvidence,
        correction: MarketplaceEconomicEvidenceCorrection
    ): MarketplaceIndependentEconomicEvidenceResult {
        classifyPrimaryIdentifier(current, correction.id, correction)?.let { return it }

        val superseded = current.facts.firstOrNull { it.id == correction.supersedesObservationId }
        if (superseded == null) {
            return if (
                current.attempts.any { it.id == correction.supersedesObservationId } ||
                current.corrections.any { it.id == correction.supersedesObservationId }
            ) {
                MarketplaceIndependentEconomicEvidenceResult.SupersededTargetNotFact
            } else {
                MarketplaceIndependentEconomicEvidenceResult.SupersededFactNotFound
            }
        }
        if (current.corrections.any { it.supersedesObservationId == superseded.id }) {
            return MarketplaceIndependentEconomicEvidenceResult.FactAlreadySuperseded
        }
        require(!correction.observedAt.isBefore(superseded.observedAt)) {
            "Correction time must not precede superseded observation time"
        }
        if (hasPrimaryIdentifier(current, correction.replacement.id)) {
            return MarketplaceIndependentEconomicEvidenceResult.ReplacementIdentifierConflict
        }
        val replacementKey = canonicalSourceFactKey(correction.replacement)
        if (
            replacementKey != null &&
            current.activeFacts.any {
                it.id != superseded.id && canonicalSourceFactKey(it) == replacementKey
            }
        ) {
            return MarketplaceIndependentEconomicEvidenceResult.ReplacementSourceFactConflict
        }

        return MarketplaceIndependentEconomicEvidenceResult.Applied(
            MarketplaceIndependentEconomicEvidence(
                current.subject,
                current.facts + correction.replacement,
                current.attempts,
                current.corrections + correction
            )
        )
    }

    override fun toString(): String = "[REDACTED]"
}

private fun classifyPrimaryIdentifier(
    current: MarketplaceIndependentEconomicEvidence,
    id: MarketplaceEconomicEvidenceObservationId,
    payload: Any
): MarketplaceIndependentEconomicEvidenceResult? {
    current.facts.firstOrNull { it.id == id }?.let {
        return if (it == payload) {
            MarketplaceIndependentEconomicEvidenceResult.Duplicate(current)
        } else {
            MarketplaceIndependentEconomicEvidenceResult.IdentifierConflict
        }
    }
    current.attempts.firstOrNull { it.id == id }?.let {
        return if (it == payload) {
            MarketplaceIndependentEconomicEvidenceResult.Duplicate(current)
        } else {
            MarketplaceIndependentEconomicEvidenceResult.IdentifierConflict
        }
    }
    current.corrections.firstOrNull { it.id == id }?.let {
        return if (it == payload) {
            MarketplaceIndependentEconomicEvidenceResult.Duplicate(current)
        } else {
            MarketplaceIndependentEconomicEvidenceResult.IdentifierConflict
        }
    }
    return null
}

private fun hasPrimaryIdentifier(
    current: MarketplaceIndependentEconomicEvidence,
    id: MarketplaceEconomicEvidenceObservationId
): Boolean = current.facts.any { it.id == id } ||
    current.attempts.any { it.id == id } ||
    current.corrections.any { it.id == id }

private sealed interface CanonicalSourceFactKey

private data class FinancialSourceFactKey(
    val sourceKind: EconomicSourceKind,
    val sourceSystemKey: EconomicSourceSystemKey,
    val externalReference: EconomicExternalReference,
    val componentType: EconomicComponentType
) : CanonicalSourceFactKey

private data class OrderOccurrenceSourceFactKey(
    val sourceKind: EconomicSourceKind,
    val sourceSystemKey: EconomicSourceSystemKey,
    val externalReference: EconomicExternalReference
) : CanonicalSourceFactKey

private fun canonicalSourceFactKey(
    fact: MarketplaceIndependentEconomicFact
): CanonicalSourceFactKey? = when (fact) {
    is MarketplaceIndependentEconomicFact.Component -> financialSourceFactKey(fact)
    is MarketplaceIndependentEconomicFact.ExternalIdentity -> null
    is MarketplaceIndependentEconomicFact.OrderOccurrence -> orderOccurrenceSourceFactKey(fact)
}

private fun financialSourceFactKey(
    fact: MarketplaceIndependentEconomicFact.Component
): FinancialSourceFactKey? {
    val component = fact.observation.component
    val reference = component.source.externalReference as? EconomicExternalReferenceState.Present ?: return null
    return FinancialSourceFactKey(
        component.source.kind,
        component.source.systemKey,
        reference.reference,
        component.type
    )
}

private fun orderOccurrenceSourceFactKey(
    fact: MarketplaceIndependentEconomicFact.OrderOccurrence
): OrderOccurrenceSourceFactKey? {
    val source = fact.observation.source
    if (source.kind != EconomicSourceKind.MARKETPLACE && source.kind != EconomicSourceKind.ERP) {
        return null
    }
    val reference = source.externalReference as? EconomicExternalReferenceState.Present ?: return null
    return OrderOccurrenceSourceFactKey(
        source.kind,
        source.systemKey,
        reference.reference
    )
}

private fun sameCanonicalMeaning(
    left: MarketplaceIndependentEconomicFact,
    right: MarketplaceIndependentEconomicFact
): Boolean = when {
    left is MarketplaceIndependentEconomicFact.Component &&
        right is MarketplaceIndependentEconomicFact.Component ->
        sameEconomicMeaning(left, right)

    left is MarketplaceIndependentEconomicFact.OrderOccurrence &&
        right is MarketplaceIndependentEconomicFact.OrderOccurrence ->
        sameOrderOccurrenceMeaning(left, right)

    else -> false
}

private fun sameEconomicMeaning(
    left: MarketplaceIndependentEconomicFact.Component,
    right: MarketplaceIndependentEconomicFact.Component
): Boolean {
    val leftObservation = left.observation
    val rightObservation = right.observation
    val leftComponent = leftObservation.component
    val rightComponent = rightObservation.component
    return leftObservation.family == rightObservation.family &&
        leftObservation.coverageClaim == rightObservation.coverageClaim &&
        leftComponent.direction == rightComponent.direction &&
        leftComponent.magnitude == rightComponent.magnitude &&
        leftComponent.occurredAt == rightComponent.occurredAt &&
        leftComponent.quality == rightComponent.quality
}

private fun sameOrderOccurrenceMeaning(
    left: MarketplaceIndependentEconomicFact.OrderOccurrence,
    right: MarketplaceIndependentEconomicFact.OrderOccurrence
): Boolean =
    left.observation.occurredAt == right.observation.occurredAt &&
        left.observation.source == right.observation.source

private fun activeFacts(
    facts: List<MarketplaceIndependentEconomicFact>,
    corrections: List<MarketplaceEconomicEvidenceCorrection>
): List<MarketplaceIndependentEconomicFact> {
    val superseded = corrections.mapTo(mutableSetOf()) { it.supersedesObservationId }
    return facts.filterNot { it.id in superseded }.sortedWith(FACT_COMPARATOR)
}

private fun isPermittedFinancialCombination(
    family: MarketplaceEconomicEvidenceFamily,
    type: EconomicComponentType
): Boolean = when (family) {
    MarketplaceEconomicEvidenceFamily.MARKETPLACE_ORDER -> type in setOf(
        EconomicComponentType.REVENUE,
        EconomicComponentType.MARKETPLACE_COMMISSION,
        EconomicComponentType.MARKETPLACE_FEE
    )
    MarketplaceEconomicEvidenceFamily.MARKETPLACE_SHIPPING -> type == EconomicComponentType.SHIPPING
    MarketplaceEconomicEvidenceFamily.PRODUCT_COST -> type == EconomicComponentType.PRODUCT_COST
    MarketplaceEconomicEvidenceFamily.FISCAL_TAX -> type == EconomicComponentType.TAX
    MarketplaceEconomicEvidenceFamily.ADS_ALLOCATION -> type == EconomicComponentType.ADVERTISING
    MarketplaceEconomicEvidenceFamily.MARKETPLACE_PAYMENT,
    MarketplaceEconomicEvidenceFamily.FISCAL_INVOICE,
    MarketplaceEconomicEvidenceFamily.ADS_IDENTITY -> false
}

private fun isPermittedIdentityCombination(
    family: MarketplaceEconomicEvidenceFamily,
    kind: MarketplaceEconomicExternalIdentityKind
): Boolean = when (family) {
    MarketplaceEconomicEvidenceFamily.MARKETPLACE_PAYMENT ->
        kind == MarketplaceEconomicExternalIdentityKind.MARKETPLACE_PAYMENT
    MarketplaceEconomicEvidenceFamily.FISCAL_INVOICE ->
        kind == MarketplaceEconomicExternalIdentityKind.FISCAL_INVOICE
    MarketplaceEconomicEvidenceFamily.ADS_IDENTITY ->
        kind == MarketplaceEconomicExternalIdentityKind.MARKETPLACE_ITEM_TO_AD_GROUP
    MarketplaceEconomicEvidenceFamily.MARKETPLACE_ORDER ->
        kind == MarketplaceEconomicExternalIdentityKind.ERP_ORDER
    MarketplaceEconomicEvidenceFamily.MARKETPLACE_SHIPPING,
    MarketplaceEconomicEvidenceFamily.PRODUCT_COST,
    MarketplaceEconomicEvidenceFamily.FISCAL_TAX,
    MarketplaceEconomicEvidenceFamily.ADS_ALLOCATION -> false
}

private fun requireMicrosecondPrecision(value: Instant, label: String) {
    require(value.nano % 1_000 == 0) { "$label must use microsecond precision" }
}

private fun requireSourceTimeOrder(source: EconomicSource, occurredAt: Instant, observedAt: Instant) {
    if (source.kind == EconomicSourceKind.MANUAL || source.kind == EconomicSourceKind.CALCULATED) {
        require(!observedAt.isBefore(occurredAt)) {
            "Internal observation time must not precede occurrence time"
        }
    }
}

private fun compareUuid(left: UUID, right: UUID): Int {
    val most = java.lang.Long.compareUnsigned(left.mostSignificantBits, right.mostSignificantBits)
    return if (most != 0) {
        most
    } else {
        java.lang.Long.compareUnsigned(left.leastSignificantBits, right.leastSignificantBits)
    }
}

private fun compareTimeAndId(
    leftTime: Instant,
    leftId: MarketplaceEconomicEvidenceObservationId,
    rightTime: Instant,
    rightId: MarketplaceEconomicEvidenceObservationId
): Int {
    val time = leftTime.compareTo(rightTime)
    return if (time != 0) time else leftId.compareTo(rightId)
}

private val FACT_COMPARATOR = Comparator<MarketplaceIndependentEconomicFact> { left, right ->
    compareTimeAndId(left.observedAt, left.id, right.observedAt, right.id)
}

private val ATTEMPT_COMPARATOR = Comparator<MarketplaceEconomicEvidenceCollectionAttempt> { left, right ->
    compareTimeAndId(left.attemptedAt, left.id, right.attemptedAt, right.id)
}

private val CORRECTION_COMPARATOR = Comparator<MarketplaceEconomicEvidenceCorrection> { left, right ->
    compareTimeAndId(left.observedAt, left.id, right.observedAt, right.id)
}
