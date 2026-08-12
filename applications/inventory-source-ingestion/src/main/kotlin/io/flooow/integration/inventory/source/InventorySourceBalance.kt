package io.flooow.integration.inventory.source

import io.flooow.integration.connector.ConnectorCapability
import io.flooow.integration.connector.ConnectorRecord
import java.math.BigDecimal
import java.text.Normalizer
import java.time.Instant

object InventorySourceBalanceCapability {
    const val VALUE = "inventory.source-balance.read"
    val KEY: ConnectorCapability = ConnectorCapability.of(VALUE)
}

sealed class SourceText protected constructor(
    private val encoded: String,
    maximumBytes: Int
) {
    init {
        require(encoded.isNotEmpty() && encoded == encoded.trim()) { "Invalid source text" }
        require(encoded.none(Char::isISOControl)) { "Invalid source text" }
        require(encoded.toByteArray(Charsets.UTF_8).size <= maximumBytes) { "Invalid source text" }
    }

    fun encodedForPersistence(): String = encoded

    override fun equals(other: Any?): Boolean =
        other != null && javaClass == other.javaClass &&
            other is SourceText && encoded == other.encoded

    override fun hashCode(): Int = 31 * javaClass.hashCode() + encoded.hashCode()
    override fun toString(): String = "[REDACTED]"

    companion object {
        fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFC)
    }
}

class SourceItemReference private constructor(value: String) : SourceText(value, 256) {
    companion object { fun of(value: String) = SourceItemReference(normalize(value)) }
}

class SourceLocationReference private constructor(value: String) : SourceText(value, 256) {
    companion object { fun of(value: String) = SourceLocationReference(normalize(value)) }
}

class SourceSku private constructor(value: String) : SourceText(value, 256) {
    companion object { fun of(value: String) = SourceSku(normalize(value)) }
}

class SourceUnitCode private constructor(value: String) : SourceText(value, 32) {
    companion object { fun of(value: String) = SourceUnitCode(normalize(value)) }
}

class SourceVersion private constructor(value: String) : SourceText(value, 128) {
    companion object { fun of(value: String) = SourceVersion(normalize(value)) }
}

class SourceQuantity private constructor(private val value: BigDecimal) {
    fun valueForPersistence(): BigDecimal = value
    fun canonicalValue(): String = value.toPlainString()
    override fun equals(other: Any?): Boolean =
        other is SourceQuantity && value.compareTo(other.value) == 0

    override fun hashCode(): Int = value.stripTrailingZeros().hashCode()
    override fun toString(): String = "[REDACTED]"

    companion object {
        private val canonicalInput = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]{1,6})?")
        private val maximumMagnitude = BigDecimal("1000000000000000000")

        fun parse(value: String): SourceQuantity {
            require(canonicalInput.matches(value)) { "Invalid source quantity" }
            var normalized = BigDecimal(value).stripTrailingZeros()
            if (normalized.scale() < 0) normalized = normalized.setScale(0)
            if (normalized.compareTo(BigDecimal.ZERO) == 0) normalized = BigDecimal.ZERO
            require(normalized.scale() <= 6 && normalized.abs() < maximumMagnitude) {
                "Invalid source quantity"
            }
            return SourceQuantity(normalized)
        }
    }
}

class InventorySourceBalanceRecord(
    val sourceItemReference: SourceItemReference,
    val sourceLocationReference: SourceLocationReference? = null,
    val sourceSku: SourceSku? = null,
    val sourceUnitCode: SourceUnitCode? = null,
    val sourceUpdatedAt: Instant? = null,
    val sourceVersion: SourceVersion? = null,
    val availableToSell: SourceQuantity? = null,
    val onHand: SourceQuantity? = null,
    val reserved: SourceQuantity? = null,
    val pendingInbound: SourceQuantity? = null,
    val pendingOutbound: SourceQuantity? = null
) : ConnectorRecord {
    init {
        require(
            listOf(availableToSell, onHand, reserved, pendingInbound, pendingOutbound)
                .any { it != null }
        ) { "Inventory source balance requires at least one measure" }
    }

    override fun toString(): String = "InventorySourceBalanceRecord([REDACTED])"
}
