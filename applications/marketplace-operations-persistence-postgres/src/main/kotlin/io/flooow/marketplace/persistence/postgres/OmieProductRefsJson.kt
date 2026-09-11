package io.flooow.marketplace.persistence.postgres

import io.flooow.marketplace.operations.economics.provider.OmieTransactionProductObservation
import io.flooow.marketplace.operations.identity.OmieProductIdentifier
import io.flooow.marketplace.operations.identity.OmieProductIdentifierKind
import java.math.BigDecimal
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

internal object OmieProductRefsJson {
    fun encode(products: List<OmieTransactionProductObservation>): String = buildJsonArray {
        products.forEach { product ->
            add(buildJsonObject {
                put("kind", product.identifier.kind.name)
                put("value", product.identifier.encodedForPersistence())
                put("quantity", product.quantity.canonicalValue())
            })
        }
    }.toString()

    fun decode(json: String): List<Pair<OmieProductIdentifier, BigDecimal>> = runCatching {
        Json.parseToJsonElement(json).jsonArray.mapNotNull { element ->
            val obj = element.jsonObject
            val legacy = obj["kind"] == null
            val kind = if (legacy) OmieProductIdentifierKind.UNKNOWN_LEGACY else
                obj["kind"]?.jsonPrimitive?.content?.let {
                    runCatching { OmieProductIdentifierKind.valueOf(it) }.getOrNull()
                } ?: return@mapNotNull null
            val value = (if (legacy) obj["code"] else obj["value"])
                ?.jsonPrimitive?.content?.trim()
            val quantity = obj["quantity"]?.jsonPrimitive?.content?.toBigDecimalOrNull()
            if (value.isNullOrBlank() || quantity == null) null
            else OmieProductIdentifier(kind, value) to quantity
        }
    }.getOrDefault(emptyList())
}
