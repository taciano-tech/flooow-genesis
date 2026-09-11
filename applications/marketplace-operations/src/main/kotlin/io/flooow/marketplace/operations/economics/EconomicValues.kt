package io.flooow.marketplace.operations.economics

import java.math.BigDecimal
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.text.Normalizer
import java.util.UUID

@JvmInline
value class MarketplaceOrderId(val value: UUID) {
    override fun toString(): String = "[INTERNAL]"

    companion object {
        fun parse(value: String): MarketplaceOrderId = MarketplaceOrderId(parseCanonicalUuid(value))
    }
}

@JvmInline
value class EconomicComponentId(val value: UUID) {
    override fun toString(): String = "[INTERNAL]"

    companion object {
        fun parse(value: String): EconomicComponentId = EconomicComponentId(parseCanonicalUuid(value))
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

data class MarketplaceKey(val value: String) {
    init {
        validateCanonicalText(value, maxUtf8Bytes = 100, label = "Marketplace key")
        require(KEY_PATTERN.matches(value)) {
            "Marketplace key must use lowercase letters, digits, dots, or hyphens"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

data class MarketplaceExternalOrderId(val value: String) {
    init {
        validateCanonicalText(value, maxUtf8Bytes = 256, label = "External order identifier")
    }

    override fun toString(): String = "[REDACTED]"
}

data class EconomicSourceSystemKey(val value: String) {
    init {
        validateCanonicalText(value, maxUtf8Bytes = 100, label = "Economic source system key")
        require(KEY_PATTERN.matches(value)) {
            "Economic source system key must use lowercase letters, digits, dots, or hyphens"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

data class EconomicExternalReference(val value: String) {
    init {
        validateCanonicalText(value, maxUtf8Bytes = 256, label = "Economic external reference")
    }

    override fun toString(): String = "[REDACTED]"
}

data class EconomicCalculationPolicyVersion(val value: String) {
    init {
        validateCanonicalText(value, maxUtf8Bytes = 64, label = "Calculation policy version")
        require(POLICY_VERSION_PATTERN.matches(value)) {
            "Calculation policy version has invalid characters"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

data class MarketplaceCurrency(val code: String) {
    init {
        require(CURRENCY_PATTERN.matches(code)) {
            "Currency must contain exactly three uppercase ASCII letters"
        }
    }

    override fun toString(): String = "[REDACTED]"
}

class MarketplaceMoney private constructor(
    val currency: MarketplaceCurrency,
    amount: BigDecimal
) {
    val amount: BigDecimal = normalizeAndValidate(amount)

    operator fun plus(other: MarketplaceMoney): MarketplaceMoney {
        requireSameCurrency(other)
        return calculated(currency, amount.add(other.amount))
    }

    operator fun minus(other: MarketplaceMoney): MarketplaceMoney {
        requireSameCurrency(other)
        return calculated(currency, amount.subtract(other.amount))
    }

    private fun requireSameCurrency(other: MarketplaceMoney) {
        require(currency == other.currency) { "Money currencies must match" }
    }

    override fun equals(other: Any?): Boolean =
        other is MarketplaceMoney &&
            currency == other.currency &&
            amount.compareTo(other.amount) == 0

    override fun hashCode(): Int = 31 * currency.hashCode() + canonicalHashAmount(amount).hashCode()

    override fun toString(): String = "[REDACTED]"

    companion object {
        private val CANONICAL_AMOUNT_PATTERN = Regex("-?(0|[1-9][0-9]*)(\\.[0-9]{1,6})?")
        private val EXCLUSIVE_ABSOLUTE_LIMIT = BigDecimal("1000000000000000000")

        fun parse(currency: MarketplaceCurrency, canonicalAmount: String): MarketplaceMoney {
            require(CANONICAL_AMOUNT_PATTERN.matches(canonicalAmount)) {
                "Money amount must be canonical decimal text with at most six fraction digits"
            }
            return MarketplaceMoney(currency, BigDecimal(canonicalAmount))
        }

        internal fun zero(currency: MarketplaceCurrency): MarketplaceMoney =
            MarketplaceMoney(currency, BigDecimal.ZERO)

        internal fun calculated(
            currency: MarketplaceCurrency,
            amount: BigDecimal
        ): MarketplaceMoney = MarketplaceMoney(currency, amount)

        private fun normalizeAndValidate(value: BigDecimal): BigDecimal {
            require(value.scale() <= 6) { "Money amount scale must not exceed six" }
            require(value.abs() < EXCLUSIVE_ABSOLUTE_LIMIT) {
                "Money amount exceeds the supported bound"
            }
            if (value.signum() == 0) {
                return BigDecimal.ZERO
            }
            return value.stripTrailingZeros()
        }

        private fun canonicalHashAmount(value: BigDecimal): BigDecimal =
            if (value.signum() == 0) BigDecimal.ZERO else value.stripTrailingZeros()
    }
}

private val KEY_PATTERN = Regex("[a-z0-9][a-z0-9.-]*")
private val POLICY_VERSION_PATTERN = Regex("[a-z0-9][a-z0-9./-]*")
private val CURRENCY_PATTERN = Regex("[A-Z]{3}")

private fun validateCanonicalText(value: String, maxUtf8Bytes: Int, label: String) {
    require(value.isNotEmpty()) { "$label must not be empty" }
    require(value == value.trim()) { "$label must not contain surrounding whitespace" }
    require(Normalizer.isNormalized(value, Normalizer.Form.NFC)) {
        "$label must be NFC normalized"
    }
    require(value.none { Character.isISOControl(it.code) }) {
        "$label must not contain ISO control characters"
    }
    val utf8Encoder = StandardCharsets.UTF_8.newEncoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
    require(utf8Encoder.canEncode(value)) {
        "$label must not contain unpaired UTF-16 surrogate code units"
    }
    require(value.toByteArray(StandardCharsets.UTF_8).size <= maxUtf8Bytes) {
        "$label exceeds its UTF-8 byte limit"
    }
}

internal val ECONOMIC_COMPONENT_ID_COMPARATOR: Comparator<EconomicComponentId> =
    Comparator { left, right ->
        val mostSignificant = java.lang.Long.compareUnsigned(
            left.value.mostSignificantBits,
            right.value.mostSignificantBits
        )
        if (mostSignificant != 0) {
            mostSignificant
        } else {
            java.lang.Long.compareUnsigned(
                left.value.leastSignificantBits,
                right.value.leastSignificantBits
            )
        }
    }
