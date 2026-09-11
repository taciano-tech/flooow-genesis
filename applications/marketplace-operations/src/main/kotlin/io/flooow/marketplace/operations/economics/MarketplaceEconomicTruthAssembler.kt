package io.flooow.marketplace.operations.economics

import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidence
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicFact
import java.util.Collections
import java.util.EnumMap

data class MarketplaceEconomicTruthAssemblyPolicyVersion(val value: String) {
    init {
        require(POLICY_VERSION_PATTERN.matches(value)) {
            "Economic truth assembly policy version is invalid"
        }
    }

    override fun toString(): String = "[REDACTED]"

    private companion object {
        val POLICY_VERSION_PATTERN = Regex("^[a-z0-9-]+(?:/[1-9][0-9]*)$")
    }
}

enum class MarketplaceEconomicTruthAssemblyNotReadyReason {
    ORDER_OCCURRED_AT_UNRESOLVED,
    ORDER_OCCURRED_AT_CONFLICT,
    INCONSISTENT_ACTIVE_FACTS;

    override fun toString(): String = "[REDACTED]"
}

sealed interface MarketplaceEconomicTruthAssemblyResult {
    val assemblyPolicyVersion: MarketplaceEconomicTruthAssemblyPolicyVersion

    data class Ready(
        val order: MarketplaceOrder,
        override val assemblyPolicyVersion: MarketplaceEconomicTruthAssemblyPolicyVersion
    ) : MarketplaceEconomicTruthAssemblyResult {
        override fun toString(): String = "[REDACTED]"
    }

    class NotReady(
        reasons: Set<MarketplaceEconomicTruthAssemblyNotReadyReason>,
        override val assemblyPolicyVersion: MarketplaceEconomicTruthAssemblyPolicyVersion
    ) : MarketplaceEconomicTruthAssemblyResult {
        val reasons: Set<MarketplaceEconomicTruthAssemblyNotReadyReason> =
            Collections.unmodifiableSet(reasons.toSet())

        init {
            require(reasons.isNotEmpty()) {
                "Economic truth assembly NotReady requires at least one reason"
            }
        }

        override fun equals(other: Any?): Boolean =
            other is NotReady &&
                reasons == other.reasons &&
                assemblyPolicyVersion == other.assemblyPolicyVersion

        override fun hashCode(): Int =
            31 * reasons.hashCode() + assemblyPolicyVersion.hashCode()

        override fun toString(): String = "[REDACTED]"
    }
}

object MarketplaceEconomicTruthAssembler {
    val POLICY_VERSION = MarketplaceEconomicTruthAssemblyPolicyVersion(
        "marketplace-economic-truth-assembly/1"
    )

    fun assemble(
        evidence: MarketplaceIndependentEconomicEvidence
    ): MarketplaceEconomicTruthAssemblyResult {
        val activeFacts = evidence.activeFacts
        val occurrenceTimes = activeFacts
            .filterIsInstance<MarketplaceIndependentEconomicFact.OrderOccurrence>()
            .mapTo(linkedSetOf()) { it.observation.occurredAt }

        if (occurrenceTimes.isEmpty()) {
            return MarketplaceEconomicTruthAssemblyResult.NotReady(
                setOf(
                    MarketplaceEconomicTruthAssemblyNotReadyReason.ORDER_OCCURRED_AT_UNRESOLVED
                ),
                POLICY_VERSION
            )
        }

        if (occurrenceTimes.size > 1) {
            return MarketplaceEconomicTruthAssemblyResult.NotReady(
                setOf(
                    MarketplaceEconomicTruthAssemblyNotReadyReason.ORDER_OCCURRED_AT_CONFLICT
                ),
                POLICY_VERSION
            )
        }

        val components = activeFacts
            .filterIsInstance<MarketplaceIndependentEconomicFact.Component>()
            .map { it.observation.component }

        val coverage = EnumMap<EconomicComponentType, EconomicComponentCoverage>(
            EconomicComponentType::class.java
        )
        EconomicComponentType.entries.forEach { type ->
            coverage[type] = if (components.any { it.type == type }) {
                EconomicComponentCoverage.PARTIAL
            } else {
                EconomicComponentCoverage.MISSING
            }
        }

        val subject = evidence.subject
        if (!componentsAreConsistentWithSubjectAndOrder(components, subject)) {
            return MarketplaceEconomicTruthAssemblyResult.NotReady(
                setOf(
                    MarketplaceEconomicTruthAssemblyNotReadyReason.INCONSISTENT_ACTIVE_FACTS
                ),
                POLICY_VERSION
            )
        }

        val order = MarketplaceOrder(
            organizationId = subject.organizationId,
            id = subject.orderId,
            marketplace = subject.marketplace,
            externalOrderId = subject.externalOrderId,
            occurredAt = occurrenceTimes.single(),
            currency = subject.currency,
            components = components,
            coverage = coverage
        )

        return MarketplaceEconomicTruthAssemblyResult.Ready(
            order,
            POLICY_VERSION
        )
    }

    private fun componentsAreConsistentWithSubjectAndOrder(
        components: List<EconomicComponent>,
        subject: io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceSubject
    ): Boolean {
        if (components.any {
                it.organizationId != subject.organizationId ||
                    it.orderId != subject.orderId ||
                    it.magnitude.currency != subject.currency
            }) {
            return false
        }

        if (components.map { it.id }.toSet().size != components.size) {
            return false
        }

        val sourceFactKeys = components.mapNotNull { component ->
            val reference = component.source.externalReference
            if (reference is EconomicExternalReferenceState.Present) {
                ComponentSourceFactKey(
                    component.source.kind,
                    component.source.systemKey,
                    reference.reference,
                    component.type
                )
            } else {
                null
            }
        }

        return sourceFactKeys.toSet().size == sourceFactKeys.size
    }

    override fun toString(): String = "[REDACTED]"
}

private data class ComponentSourceFactKey(
    val sourceKind: EconomicSourceKind,
    val sourceSystemKey: EconomicSourceSystemKey,
    val externalReference: EconomicExternalReference,
    val componentType: EconomicComponentType
)
