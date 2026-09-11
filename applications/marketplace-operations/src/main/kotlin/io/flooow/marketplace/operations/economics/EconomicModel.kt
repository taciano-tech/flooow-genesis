package io.flooow.marketplace.operations.economics

import io.flooow.organization.OrganizationId
import java.time.Instant
import java.util.Collections
import java.util.EnumMap

enum class EconomicSourceKind {
    MARKETPLACE,
    ERP,
    MANUAL,
    CALCULATED
}

enum class EconomicExternalReferenceAbsenceReason {
    INTERNAL_ORIGIN
}

sealed interface EconomicExternalReferenceState {
    data class Present(val reference: EconomicExternalReference) : EconomicExternalReferenceState {
        override fun toString(): String = "[REDACTED]"
    }

    data class Absent(
        val reason: EconomicExternalReferenceAbsenceReason
    ) : EconomicExternalReferenceState {
        override fun toString(): String = "[REDACTED]"
    }
}

data class EconomicSource(
    val kind: EconomicSourceKind,
    val systemKey: EconomicSourceSystemKey,
    val externalReference: EconomicExternalReferenceState
) {
    init {
        if (kind == EconomicSourceKind.MARKETPLACE || kind == EconomicSourceKind.ERP) {
            require(externalReference is EconomicExternalReferenceState.Present) {
                "Marketplace and ERP sources require a stable external reference"
            }
        }
    }

    override fun toString(): String = "[REDACTED]"
}

enum class EconomicComponentType {
    REVENUE,
    MARKETPLACE_COMMISSION,
    MARKETPLACE_FEE,
    SHIPPING,
    ADVERTISING,
    TAX,
    PRODUCT_COST,
    FINANCIAL_COST,
    OTHER_ADJUSTMENT
}

enum class EconomicDirection {
    ADDITION,
    DEDUCTION
}

enum class EconomicEvidenceQuality {
    CONFIRMED,
    ESTIMATED
}

data class EconomicComponent(
    val organizationId: OrganizationId,
    val id: EconomicComponentId,
    val orderId: MarketplaceOrderId,
    val type: EconomicComponentType,
    val direction: EconomicDirection,
    val magnitude: MarketplaceMoney,
    val source: EconomicSource,
    val occurredAt: Instant,
    val quality: EconomicEvidenceQuality
) {
    init {
        require(magnitude.amount.signum() >= 0) {
            "Economic component magnitude must not be negative"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

enum class EconomicComponentCoverage {
    COMPLETE,
    NOT_APPLICABLE,
    PARTIAL,
    MISSING
}

class MarketplaceOrder(
    val organizationId: OrganizationId,
    val id: MarketplaceOrderId,
    val marketplace: MarketplaceKey,
    val externalOrderId: MarketplaceExternalOrderId,
    val occurredAt: Instant,
    val currency: MarketplaceCurrency,
    components: Collection<EconomicComponent>,
    coverage: Map<EconomicComponentType, EconomicComponentCoverage>
) {
    val components: List<EconomicComponent> = Collections.unmodifiableList(
        components.sortedWith(compareBy(ECONOMIC_COMPONENT_ID_COMPARATOR) { it.id })
    )

    val coverage: Map<EconomicComponentType, EconomicComponentCoverage> =
        Collections.unmodifiableMap(
            EnumMap<EconomicComponentType, EconomicComponentCoverage>(EconomicComponentType::class.java)
                .apply { putAll(coverage) }
        )

    init {
        validateOwnershipAndCurrency()
        validateDuplicateIdentifiers()
        validateDuplicateSourceFacts()
        validateCoverage()
    }

    private fun validateOwnershipAndCurrency() {
        components.forEach { component ->
            require(component.organizationId == organizationId) {
                "Economic component must belong to the order organization"
            }
            require(component.orderId == id) {
                "Economic component must belong to the order identifier"
            }
            require(component.magnitude.currency == currency) {
                "Economic component currency must match the order currency"
            }
        }
    }

    private fun validateDuplicateIdentifiers() {
        require(components.map { it.id }.toSet().size == components.size) {
            "Economic component identifiers must be unique within an order"
        }
    }

    private fun validateDuplicateSourceFacts() {
        val presentFactKeys = components.mapNotNull { component ->
            val reference = component.source.externalReference
            if (reference is EconomicExternalReferenceState.Present) {
                PresentSourceFactKey(
                    kind = component.source.kind,
                    systemKey = component.source.systemKey,
                    externalReference = reference.reference,
                    componentType = component.type
                )
            } else {
                null
            }
        }
        require(presentFactKeys.toSet().size == presentFactKeys.size) {
            "Present economic source facts must be unique within an order"
        }
    }

    private fun validateCoverage() {
        val expectedTypes = EconomicComponentType.entries.toSet()
        require(coverage.keys == expectedTypes) {
            "Coverage must classify every economic component type exactly once"
        }

        EconomicComponentType.entries.forEach { type ->
            val componentCount = components.count { it.type == type }
            when (coverage.getValue(type)) {
                EconomicComponentCoverage.COMPLETE,
                EconomicComponentCoverage.PARTIAL -> require(componentCount > 0) {
                    "$type coverage requires at least one component"
                }

                EconomicComponentCoverage.NOT_APPLICABLE,
                EconomicComponentCoverage.MISSING -> require(componentCount == 0) {
                    "$type coverage does not permit supplied components"
                }
            }
        }

        require(coverage.getValue(EconomicComponentType.REVENUE) != EconomicComponentCoverage.NOT_APPLICABLE) {
            "Revenue may not be not applicable"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is MarketplaceOrder &&
            organizationId == other.organizationId &&
            id == other.id &&
            marketplace == other.marketplace &&
            externalOrderId == other.externalOrderId &&
            occurredAt == other.occurredAt &&
            currency == other.currency &&
            components == other.components &&
            coverage == other.coverage

    override fun hashCode(): Int {
        var result = organizationId.hashCode()
        result = 31 * result + id.hashCode()
        result = 31 * result + marketplace.hashCode()
        result = 31 * result + externalOrderId.hashCode()
        result = 31 * result + occurredAt.hashCode()
        result = 31 * result + currency.hashCode()
        result = 31 * result + components.hashCode()
        result = 31 * result + coverage.hashCode()
        return result
    }

    override fun toString(): String = "[REDACTED]"
}

private data class PresentSourceFactKey(
    val kind: EconomicSourceKind,
    val systemKey: EconomicSourceSystemKey,
    val externalReference: EconomicExternalReference,
    val componentType: EconomicComponentType
)
