package io.flooow.marketplace.operations.economics.provider

import io.flooow.integration.connector.ConnectorCapability
import io.flooow.integration.connector.ConnectorRecord
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.text.Normalizer

object MarketplaceEconomicProductCostCapability {
    const val VALUE = "marketplace-economic.product-cost"
    val KEY: ConnectorCapability = ConnectorCapability.of(VALUE)
}

sealed class ProviderSourceText protected constructor(
    private val encoded: String,
    maximumBytes: Int
) {
    init {
        require(encoded.isNotEmpty() && encoded == encoded.trim()) {
            "Invalid provider source text"
        }
        require(encoded.none(Char::isISOControl)) { "Invalid provider source text" }
        require(encoded.toByteArray(Charsets.UTF_8).size <= maximumBytes) {
            "Invalid provider source text"
        }
    }

    fun encodedForPersistence(): String = encoded

    override fun equals(other: Any?): Boolean =
        other != null && javaClass == other.javaClass &&
            other is ProviderSourceText && encoded == other.encoded

    override fun hashCode(): Int = 31 * javaClass.hashCode() + encoded.hashCode()
    override fun toString(): String = "[REDACTED]"

    companion object {
        fun normalize(value: String): String =
            Normalizer.normalize(value, Normalizer.Form.NFC)
    }
}

class OmieProductReference private constructor(value: String) :
    ProviderSourceText(value, 64) {
    companion object {
        fun of(value: String) = OmieProductReference(normalize(value))
    }
}

class OmieIntegrationReference private constructor(value: String) :
    ProviderSourceText(value, 60) {
    companion object {
        fun of(value: String) = OmieIntegrationReference(normalize(value))
    }
}

class OmieDisplayedProductCode private constructor(value: String) :
    ProviderSourceText(value, 60) {
    companion object {
        fun of(value: String) = OmieDisplayedProductCode(normalize(value))
    }
}

class OmieLocationReference private constructor(value: String) :
    ProviderSourceText(value, 64) {
    companion object {
        fun of(value: String) = OmieLocationReference(normalize(value))
    }
}

class ProviderSourceDecimal private constructor(private val value: BigDecimal) {
    fun valueForPersistence(): BigDecimal = value
    fun canonicalValue(): String = value.toPlainString()

    override fun equals(other: Any?): Boolean =
        other is ProviderSourceDecimal && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.stripTrailingZeros().hashCode()
    override fun toString(): String = "[REDACTED]"

    companion object {
        private val canonical = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]{1,6})?")
        private val maximumMagnitude = BigDecimal("1000000000000000000")

        fun parse(value: String): ProviderSourceDecimal {
            require(canonical.matches(value)) { "Invalid provider decimal" }
            var normalized = BigDecimal(value).stripTrailingZeros()
            if (normalized.scale() < 0) normalized = normalized.setScale(0)
            if (normalized.compareTo(BigDecimal.ZERO) == 0) normalized = BigDecimal.ZERO
            require(normalized.scale() <= 6 && normalized.abs() < maximumMagnitude) {
                "Invalid provider decimal"
            }
            return ProviderSourceDecimal(normalized)
        }
    }
}

class OmieProductCostSourceRecord(
    val productReference: OmieProductReference,
    val integrationReference: OmieIntegrationReference?,
    val displayedProductCode: OmieDisplayedProductCode?,
    val locationReference: OmieLocationReference,
    val unitCmc: ProviderSourceDecimal?,
    val stockBalance: ProviderSourceDecimal?,
    val physicalStock: ProviderSourceDecimal?,
    val reservedStock: ProviderSourceDecimal?,
    val positionDate: LocalDate,
    val observedAt: Instant
) : ConnectorRecord {
    override fun toString(): String = "OmieProductCostSourceRecord([REDACTED])"
}
object MarketplaceEconomicOrderSourceCapability {
    const val VALUE = "marketplace-economic.order-source"
    val KEY: ConnectorCapability = ConnectorCapability.of(VALUE)
    const val REACQUISITION_VALUE = "marketplace-economic.order-source.reacquisition-v1"
    val REACQUISITION_KEY: ConnectorCapability = ConnectorCapability.of(REACQUISITION_VALUE)
}

class MercadoLivreOrderReference private constructor(value: String) :
    ProviderSourceText(value, 64) {
    companion object {
        fun of(value: String) = MercadoLivreOrderReference(normalize(value))
    }
}

class MercadoLivreProviderStatus private constructor(value: String) :
    ProviderSourceText(value, 64) {
    companion object {
        fun of(value: String) = MercadoLivreProviderStatus(normalize(value))
    }
}

class MercadoLivreSourceCurrency private constructor(value: String) :
    ProviderSourceText(value, 3) {
    init {
        require(Regex("[A-Z]{3}").matches(encodedForPersistence())) {
            "Invalid provider source currency"
        }
    }

    companion object {
        fun of(value: String) = MercadoLivreSourceCurrency(normalize(value))
    }
}

class MercadoLivrePackReference private constructor(value: String) :
    ProviderSourceText(value, 64) {
    companion object {
        fun of(value: String) = MercadoLivrePackReference(normalize(value))
    }
}

class MercadoLivreShippingReference private constructor(value: String) :
    ProviderSourceText(value, 64) {
    companion object {
        fun of(value: String) = MercadoLivreShippingReference(normalize(value))
    }
}

class MercadoLivreItemReference private constructor(value: String) :
    ProviderSourceText(value, 64) {
    companion object {
        fun of(value: String) = MercadoLivreItemReference(normalize(value))
    }
}

class MercadoLivreVariationReference private constructor(value: String) :
    ProviderSourceText(value, 64) {
    companion object {
        fun of(value: String) = MercadoLivreVariationReference(normalize(value))
    }
}

class MercadoLivreSellerSkuReference private constructor(value: String) :
    ProviderSourceText(value, 128) {
    companion object {
        fun of(value: String) = MercadoLivreSellerSkuReference(normalize(value))
    }
}

class MercadoLivrePaymentReference private constructor(value: String) :
    ProviderSourceText(value, 64) {
    companion object {
        fun of(value: String) = MercadoLivrePaymentReference(normalize(value))
    }
}

class MercadoLivreOrderItemSourceObservation(
    val itemReference: MercadoLivreItemReference,
    val variationReference: MercadoLivreVariationReference?,
    val quantity: ProviderSourceDecimal,
    val unitPrice: ProviderSourceDecimal,
    val currency: MercadoLivreSourceCurrency,
    val saleFee: ProviderSourceDecimal?,
    val grossPrice: ProviderSourceDecimal?,
    val sellerSku: MercadoLivreSellerSkuReference? = null
) {
    init {
        require(quantity.valueForPersistence() > BigDecimal.ZERO) {
            "Provider item quantity must be positive"
        }
        require(unitPrice.valueForPersistence() >= BigDecimal.ZERO) {
            "Provider item unit price must be nonnegative"
        }
        require(saleFee == null || saleFee.valueForPersistence() >= BigDecimal.ZERO) {
            "Provider item sale fee must be nonnegative"
        }
        require(grossPrice == null || grossPrice.valueForPersistence() >= BigDecimal.ZERO) {
            "Provider item gross price must be nonnegative"
        }
    }

    override fun toString(): String = "MercadoLivreOrderItemSourceObservation([REDACTED])"
}

class MercadoLivrePaymentSourceObservation(
    val paymentReference: MercadoLivrePaymentReference,
    val providerStatus: MercadoLivreProviderStatus,
    val transactionAmount: ProviderSourceDecimal,
    val currency: MercadoLivreSourceCurrency,
    val dateCreated: Instant?,
    val dateLastModified: Instant?
) {
    init {
        require(transactionAmount.valueForPersistence() >= BigDecimal.ZERO) {
            "Provider payment amount must be nonnegative"
        }
        require(dateCreated == null || dateCreated == dateCreated.truncatedTo(ChronoUnit.MICROS)) {
            "Provider payment creation time must use microsecond precision"
        }
        require(
            dateLastModified == null ||
                dateLastModified == dateLastModified.truncatedTo(ChronoUnit.MICROS)
        ) {
            "Provider payment modification time must use microsecond precision"
        }
    }

    override fun toString(): String = "MercadoLivrePaymentSourceObservation([REDACTED])"
}

class MercadoLivreOrderSourceRecord(
    val externalOrderReference: MercadoLivreOrderReference,
    val providerStatus: MercadoLivreProviderStatus,
    val dateCreated: Instant,
    val dateLastUpdated: Instant,
    val dateClosed: Instant?,
    val currency: MercadoLivreSourceCurrency,
    val totalAmount: ProviderSourceDecimal,
    val paidAmount: ProviderSourceDecimal?,
    val packReference: MercadoLivrePackReference?,
    val shippingReference: MercadoLivreShippingReference?,
    orderItems: Collection<MercadoLivreOrderItemSourceObservation>,
    payments: Collection<MercadoLivrePaymentSourceObservation>,
    val observedAt: Instant
) : ConnectorRecord {
    val orderItems: List<MercadoLivreOrderItemSourceObservation> = orderItems.toList()
    val payments: List<MercadoLivrePaymentSourceObservation> = payments.toList()

    init {
        require(totalAmount.valueForPersistence() >= BigDecimal.ZERO) {
            "Provider order total amount must be nonnegative"
        }
        require(paidAmount == null || paidAmount.valueForPersistence() >= BigDecimal.ZERO) {
            "Provider order paid amount must be nonnegative"
        }
        require(dateCreated == dateCreated.truncatedTo(ChronoUnit.MICROS)) {
            "Provider order creation time must use microsecond precision"
        }
        require(dateLastUpdated == dateLastUpdated.truncatedTo(ChronoUnit.MICROS)) {
            "Provider order update time must use microsecond precision"
        }
        require(dateClosed == null || dateClosed == dateClosed.truncatedTo(ChronoUnit.MICROS)) {
            "Provider order close time must use microsecond precision"
        }
        require(observedAt == observedAt.truncatedTo(ChronoUnit.MICROS)) {
            "Provider observation time must use microsecond precision"
        }
        require(this.orderItems.isNotEmpty() && this.orderItems.size <= 100) {
            "Provider order item count is invalid"
        }
        require(this.payments.size <= 100) {
            "Provider payment count is invalid"
        }
    }

    override fun toString(): String = "MercadoLivreOrderSourceRecord([REDACTED])"
}

object OmieTransactionEvidenceCapability {
    const val VALUE = "marketplace-economic.omie-transaction-evidence"
    val KEY: ConnectorCapability = ConnectorCapability.of(VALUE)
    const val REACQUISITION_V1_VALUE = "marketplace-economic.omie-transaction-evidence.reacquisition-v1"
    val REACQUISITION_V1_KEY: ConnectorCapability = ConnectorCapability.of(REACQUISITION_V1_VALUE)
    const val REACQUISITION_VALUE = "marketplace-economic.omie-transaction-evidence.reacquisition-v2"
    val REACQUISITION_KEY: ConnectorCapability = ConnectorCapability.of(REACQUISITION_VALUE)
}

class OmieOrderReference private constructor(value: String) : ProviderSourceText(value, 64) {
    companion object { fun of(value: String) = OmieOrderReference(normalize(value)) }
}

class OmieCustomerOrderReference private constructor(value: String) : ProviderSourceText(value, 128) {
    companion object { fun of(value: String) = OmieCustomerOrderReference(normalize(value)) }
}

class OmieOrderStatus private constructor(value: String) : ProviderSourceText(value, 64) {
    companion object { fun of(value: String) = OmieOrderStatus(normalize(value)) }
}

enum class OmieTransactionProductIdentifierKind {
    INTERNAL_PRODUCT_ID,
    INTEGRATION_PRODUCT_CODE,
    DISPLAY_PRODUCT_CODE,
    UNKNOWN_LEGACY
}

class OmieTransactionProductIdentifier private constructor(
    val kind: OmieTransactionProductIdentifierKind,
    value: String
) : ProviderSourceText(value, 128) {
    override fun equals(other: Any?): Boolean =
        other is OmieTransactionProductIdentifier &&
            kind == other.kind && encodedForPersistence() == other.encodedForPersistence()

    override fun hashCode(): Int = 31 * kind.hashCode() + encodedForPersistence().hashCode()

    companion object {
        fun of(kind: OmieTransactionProductIdentifierKind, value: String) =
            OmieTransactionProductIdentifier(kind, normalize(value))
    }
}

class OmieTransactionProductObservation(
    val identifier: OmieTransactionProductIdentifier,
    val quantity: ProviderSourceDecimal
) {
    init { require(quantity.valueForPersistence() >= BigDecimal.ZERO) }
    override fun toString() = "OmieTransactionProductObservation([REDACTED])"
}

class OmieTransactionEvidenceRecord(
    val orderReference: OmieOrderReference,
    val integrationOrderReference: OmieIntegrationReference?,
    val customerOrderReference: OmieCustomerOrderReference?,
    val occurredAt: Instant?,
    val status: OmieOrderStatus?,
    val currency: MercadoLivreSourceCurrency?,
    val totalAmount: ProviderSourceDecimal?,
    products: Collection<OmieTransactionProductObservation>,
    val observedAt: Instant,
    val sourceFingerprint: String
) : ConnectorRecord {
    val products: List<OmieTransactionProductObservation> = products.toList()
    init {
        require(products.size <= 100)
        require(totalAmount == null || totalAmount.valueForPersistence() >= BigDecimal.ZERO)
        require(occurredAt == null || occurredAt.nano % 1_000 == 0)
        require(observedAt.nano % 1_000 == 0)
        require(sourceFingerprint.isNotBlank() && sourceFingerprint.length <= 128)
    }
    override fun toString() = "OmieTransactionEvidenceRecord([REDACTED])"
}
