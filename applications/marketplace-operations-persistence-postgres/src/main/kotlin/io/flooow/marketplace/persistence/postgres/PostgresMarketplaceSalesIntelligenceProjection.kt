package io.flooow.marketplace.persistence.postgres

import io.flooow.marketplace.operations.economics.ContributionMarginUndefinedReason
import io.flooow.marketplace.operations.economics.EconomicCalculationPolicyVersion
import io.flooow.marketplace.operations.economics.EconomicComponent
import io.flooow.marketplace.operations.economics.EconomicComponentId
import io.flooow.marketplace.operations.economics.EconomicComponentType
import io.flooow.marketplace.operations.economics.EconomicDirection
import io.flooow.marketplace.operations.economics.EconomicEvidenceQuality
import io.flooow.marketplace.operations.economics.EconomicExternalReference
import io.flooow.marketplace.operations.economics.EconomicExternalReferenceAbsenceReason
import io.flooow.marketplace.operations.economics.EconomicExternalReferenceState
import io.flooow.marketplace.operations.economics.EconomicSource
import io.flooow.marketplace.operations.economics.EconomicSourceKind
import io.flooow.marketplace.operations.economics.EconomicSourceSystemKey
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthAssemblyNotReadyReason
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthAssemblyPolicyVersion
import io.flooow.marketplace.operations.economics.MarketplaceEconomicTruthQuality
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.evidence.ChangeSequenceCheckpoint
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceVersion
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceCalculationSnapshot
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceContributionMarginSnapshot
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjection
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionCursor
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionPage
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionReadResult
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionRecord
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceProjectionWriteResult
import io.flooow.marketplace.operations.economics.sales.MarketplaceSalesIntelligenceState
import io.flooow.marketplace.operations.economics.sales.calculationPolicyVersionOf
import io.flooow.organization.OrganizationId
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Timestamp
import java.sql.Types
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PostgresMarketplaceSalesIntelligenceProjection(
    private val configuration: PostgresConfiguration
) : MarketplaceSalesIntelligenceProjection {
    override fun currentBySubject(
        organizationId: OrganizationId,
        marketplaceOrderId: MarketplaceOrderId
    ): MarketplaceSalesIntelligenceProjectionReadResult<MarketplaceSalesIntelligenceProjectionRecord?> =
        readOne(organizationId, marketplaceOrderId)

    override fun detailByOrganizationAndSubject(
        organizationId: OrganizationId,
        marketplaceOrderId: MarketplaceOrderId
    ): MarketplaceSalesIntelligenceProjectionReadResult<MarketplaceSalesIntelligenceProjectionRecord?> =
        readOne(organizationId, marketplaceOrderId)

    override fun materializeIfNewer(
        record: MarketplaceSalesIntelligenceProjectionRecord
    ): MarketplaceSalesIntelligenceProjectionWriteResult = try {
        val encoded = encodeState(record.state)
        connection().use { connection ->
            connection.prepareStatement(UPSERT_SQL).use { statement ->
                statement.setObject(1, record.organizationId.value)
                statement.setObject(2, record.marketplaceOrderId.value)
                statement.setLong(3, record.sourceEvidenceVersion.valueForPersistence())
                statement.setString(4, encoded.stateKind)
                statement.setString(5, record.state.assemblyPolicyVersion.value)
                if (encoded.calculationPolicyVersion == null) {
                    statement.setNull(6, Types.VARCHAR)
                } else {
                    statement.setString(6, encoded.calculationPolicyVersion)
                }
                if (encoded.calculationKind == null) {
                    statement.setNull(7, Types.VARCHAR)
                } else {
                    statement.setString(7, encoded.calculationKind)
                }
                statement.setObject(8, encoded.payload, Types.OTHER)
                statement.setLong(9, record.lastAppliedChangeSequence.valueForPersistence())
                statement.setTimestamp(10, Timestamp.from(record.projectedAt))
                statement.executeQuery().use { result ->
                    if (result.next()) {
                        MarketplaceSalesIntelligenceProjectionWriteResult.Applied
                    } else {
                        MarketplaceSalesIntelligenceProjectionWriteResult.NoOpAlreadyCurrent
                    }
                }
            }
        }
    } catch (_: Exception) {
        MarketplaceSalesIntelligenceProjectionWriteResult.IntegrityFailure
    }

    override fun listByOrganization(
        organizationId: OrganizationId,
        cursor: MarketplaceSalesIntelligenceProjectionCursor?,
        limit: Int
    ): MarketplaceSalesIntelligenceProjectionReadResult<MarketplaceSalesIntelligenceProjectionPage> {
        val validated = MarketplaceSalesIntelligenceProjection.requireValidPageSize(limit)
        return try {
            val rows = connection().use { connection ->
                val sql = if (cursor == null) LIST_FIRST_SQL else LIST_AFTER_SQL
                connection.prepareStatement(sql).use { statement ->
                    statement.setObject(1, organizationId.value)
                    var index = 2
                    if (cursor != null) {
                        val timestamp = Timestamp.from(cursor.projectedAt)
                        statement.setTimestamp(index++, timestamp)
                        statement.setTimestamp(index++, timestamp)
                        statement.setObject(index++, cursor.marketplaceOrderId.value)
                    }
                    statement.setInt(index, validated + 1)
                    statement.executeQuery().use { result ->
                        buildList {
                            while (result.next()) add(result.toRecord())
                        }
                    }
                }
            }

            val hasMore = rows.size > validated
            val visible = if (hasMore) rows.take(validated) else rows
            val nextCursor = if (hasMore && visible.isNotEmpty()) {
                visible.last().let {
                    MarketplaceSalesIntelligenceProjectionCursor(
                        projectedAt = it.projectedAt,
                        marketplaceOrderId = it.marketplaceOrderId
                    )
                }
            } else {
                null
            }
            MarketplaceSalesIntelligenceProjectionReadResult.Success(
                MarketplaceSalesIntelligenceProjectionPage(visible, nextCursor)
            )
        } catch (_: Exception) {
            MarketplaceSalesIntelligenceProjectionReadResult.IntegrityFailure
        }
    }

    private fun readOne(
        organizationId: OrganizationId,
        marketplaceOrderId: MarketplaceOrderId
    ): MarketplaceSalesIntelligenceProjectionReadResult<MarketplaceSalesIntelligenceProjectionRecord?> =
        try {
            val value = connection().use { connection ->
                connection.prepareStatement(DETAIL_SQL).use { statement ->
                    statement.setObject(1, organizationId.value)
                    statement.setObject(2, marketplaceOrderId.value)
                    statement.executeQuery().use { result ->
                        if (result.next()) result.toRecord() else null
                    }
                }
            }
            MarketplaceSalesIntelligenceProjectionReadResult.Success(value)
        } catch (_: Exception) {
            MarketplaceSalesIntelligenceProjectionReadResult.IntegrityFailure
        }

    private fun ResultSet.toRecord(): MarketplaceSalesIntelligenceProjectionRecord {
        val organizationId = OrganizationId(getObject("organization_id", UUID::class.java))
        val orderId = MarketplaceOrderId(getObject("marketplace_order_id", UUID::class.java))
        val assemblyPolicyVersion =
            MarketplaceEconomicTruthAssemblyPolicyVersion(getString("assembly_policy_version"))
        val stateKind = getString("state_kind")
        val payload = Json.parseToJsonElement(getString("state_payload")).jsonObject

        val state = when (stateKind) {
            "UNRESOLVED" -> {
                val reasons = payload.requiredArray("reasons")
                    .map { MarketplaceEconomicTruthAssemblyNotReadyReason.valueOf(it.jsonPrimitive.content) }
                    .toSet()
                MarketplaceSalesIntelligenceState.Unresolved(
                    assemblyPolicyVersion,
                    reasons
                )
            }

            "CALCULATED" -> {
                val policyText = getString("calculation_policy_version")
                    ?: error("calculation policy missing")
                val kind = getString("calculation_kind")
                    ?: error("calculation kind missing")
                val calculationPolicyVersion = EconomicCalculationPolicyVersion(policyText)
                val snapshot = decodeCalculationSnapshot(
                    kind = kind,
                    payload = payload,
                    organizationId = organizationId,
                    marketplaceOrderId = orderId
                )
                val calculationResult = snapshot.toCalculationResult(
                    organizationId,
                    orderId,
                    calculationPolicyVersion
                )
                MarketplaceSalesIntelligenceState.Calculated(
                    assemblyPolicyVersion = assemblyPolicyVersion,
                    calculationPolicyVersion = calculationPolicyVersion,
                    calculationResult = calculationResult
                )
            }

            else -> error("unknown projection state")
        }

        return MarketplaceSalesIntelligenceProjectionRecord(
            organizationId = organizationId,
            marketplaceOrderId = orderId,
            sourceEvidenceVersion = MarketplaceEconomicEvidenceVersion(
                getLong("source_evidence_version")
            ),
            state = state,
            lastAppliedChangeSequence = ChangeSequenceCheckpoint(
                getLong("last_applied_change_sequence")
            ),
            projectedAt = getTimestamp("projected_at").toInstant()
        )
    }

    private fun encodeState(state: MarketplaceSalesIntelligenceState): EncodedState =
        when (state) {
            is MarketplaceSalesIntelligenceState.Unresolved -> EncodedState(
                stateKind = "UNRESOLVED",
                calculationPolicyVersion = null,
                calculationKind = null,
                payload = buildJsonObject {
                    put(
                        "reasons",
                        buildJsonArray {
                            state.reasons
                                .sortedBy { it.name }
                                .forEach { add(JsonPrimitive(it.name)) }
                        }
                    )
                }.toString()
            )

            is MarketplaceSalesIntelligenceState.Calculated -> {
                val snapshot = MarketplaceSalesIntelligenceCalculationSnapshot.from(
                    state.calculationResult
                )
                val kind = when (snapshot) {
                    is MarketplaceSalesIntelligenceCalculationSnapshot.Complete -> "COMPLETE"
                    is MarketplaceSalesIntelligenceCalculationSnapshot.Incomplete -> "INCOMPLETE"
                }
                EncodedState(
                    stateKind = "CALCULATED",
                    calculationPolicyVersion =
                        calculationPolicyVersionOf(state.calculationResult).value,
                    calculationKind = kind,
                    payload = encodeCalculationSnapshot(snapshot).toString()
                )
            }
        }

    private fun encodeCalculationSnapshot(
        snapshot: MarketplaceSalesIntelligenceCalculationSnapshot
    ): JsonObject = when (snapshot) {
        is MarketplaceSalesIntelligenceCalculationSnapshot.Complete -> buildJsonObject {
            put("marketplace", JsonPrimitive(snapshot.marketplace.value))
            put("externalOrderId", JsonPrimitive(snapshot.externalOrderId.value))
            put("orderOccurredAt", JsonPrimitive(snapshot.orderOccurredAt.toString()))
            put("currency", JsonPrimitive(snapshot.currency.code))
            put("grossRevenue", money(snapshot.grossRevenue))
            put("totalMarketplaceFees", money(snapshot.totalMarketplaceFees))
            put("totalShipping", money(snapshot.totalShipping))
            put("totalAdvertising", money(snapshot.totalAdvertising))
            put("totalTaxes", money(snapshot.totalTaxes))
            put("totalProductCost", money(snapshot.totalProductCost))
            put("totalFinancialCost", money(snapshot.totalFinancialCost))
            put("totalOtherAdjustments", money(snapshot.totalOtherAdjustments))
            put("contribution", money(snapshot.contribution))
            put("contributionMargin", contributionMargin(snapshot.contributionMargin))
            put("truthQuality", JsonPrimitive(snapshot.truthQuality.name))
            put("components", components(snapshot.components))
        }

        is MarketplaceSalesIntelligenceCalculationSnapshot.Incomplete -> buildJsonObject {
            put(
                "missingTypes",
                buildJsonArray {
                    snapshot.missingTypes.forEach { add(JsonPrimitive(it.name)) }
                }
            )
            put(
                "partialTypes",
                buildJsonArray {
                    snapshot.partialTypes.forEach { add(JsonPrimitive(it.name)) }
                }
            )
            put("suppliedComponents", components(snapshot.suppliedComponents))
        }
    }

    private fun decodeCalculationSnapshot(
        kind: String,
        payload: JsonObject,
        organizationId: OrganizationId,
        marketplaceOrderId: MarketplaceOrderId
    ): MarketplaceSalesIntelligenceCalculationSnapshot = when (kind) {
        "COMPLETE" -> MarketplaceSalesIntelligenceCalculationSnapshot.Complete(
            marketplace = MarketplaceKey(payload.requiredString("marketplace")),
            externalOrderId = MarketplaceExternalOrderId(
                payload.requiredString("externalOrderId")
            ),
            orderOccurredAt = Instant.parse(payload.requiredString("orderOccurredAt")),
            currency = MarketplaceCurrency(payload.requiredString("currency")),
            grossRevenue = payload.requiredObject("grossRevenue").toMoney(),
            totalMarketplaceFees = payload.requiredObject("totalMarketplaceFees").toMoney(),
            totalShipping = payload.requiredObject("totalShipping").toMoney(),
            totalAdvertising = payload.requiredObject("totalAdvertising").toMoney(),
            totalTaxes = payload.requiredObject("totalTaxes").toMoney(),
            totalProductCost = payload.requiredObject("totalProductCost").toMoney(),
            totalFinancialCost = payload.requiredObject("totalFinancialCost").toMoney(),
            totalOtherAdjustments = payload.requiredObject("totalOtherAdjustments").toMoney(),
            contribution = payload.requiredObject("contribution").toMoney(),
            contributionMargin = payload.requiredObject("contributionMargin").toContributionMargin(),
            truthQuality = MarketplaceEconomicTruthQuality.valueOf(
                payload.requiredString("truthQuality")
            ),
            components = decodeComponents(
                payload.requiredArray("components"),
                organizationId,
                marketplaceOrderId
            )
        )

        "INCOMPLETE" -> MarketplaceSalesIntelligenceCalculationSnapshot.Incomplete(
            missingTypes = payload.requiredArray("missingTypes")
                .map { EconomicComponentType.valueOf(it.jsonPrimitive.content) },
            partialTypes = payload.requiredArray("partialTypes")
                .map { EconomicComponentType.valueOf(it.jsonPrimitive.content) },
            suppliedComponents = decodeComponents(
                payload.requiredArray("suppliedComponents"),
                organizationId,
                marketplaceOrderId
            )
        )

        else -> error("unknown calculation kind")
    }

    private fun money(value: MarketplaceMoney): JsonObject = buildJsonObject {
        put("currency", JsonPrimitive(value.currency.code))
        put("amount", JsonPrimitive(value.amount.toPlainString()))
    }

    private fun JsonObject.toMoney(): MarketplaceMoney =
        MarketplaceMoney.parse(
            MarketplaceCurrency(requiredString("currency")),
            requiredString("amount")
        )

    private fun contributionMargin(
        value: MarketplaceSalesIntelligenceContributionMarginSnapshot
    ): JsonObject = when (value) {
        is MarketplaceSalesIntelligenceContributionMarginSnapshot.Defined ->
            buildJsonObject {
                put("kind", JsonPrimitive("DEFINED"))
                put("value", JsonPrimitive(value.decimalValue.toPlainString()))
            }

        is MarketplaceSalesIntelligenceContributionMarginSnapshot.Undefined ->
            buildJsonObject {
                put("kind", JsonPrimitive("UNDEFINED"))
                put("reason", JsonPrimitive(value.reason.name))
            }
    }

    private fun JsonObject.toContributionMargin():
        MarketplaceSalesIntelligenceContributionMarginSnapshot =
        when (requiredString("kind")) {
            "DEFINED" ->
                MarketplaceSalesIntelligenceContributionMarginSnapshot.Defined(
                    BigDecimal(requiredString("value"))
                )
            "UNDEFINED" ->
                MarketplaceSalesIntelligenceContributionMarginSnapshot.Undefined(
                    ContributionMarginUndefinedReason.valueOf(requiredString("reason"))
                )
            else -> error("unknown contribution margin kind")
        }

    private fun components(values: List<EconomicComponent>): JsonArray =
        buildJsonArray {
            values.forEach { value ->
                add(
                    buildJsonObject {
                        put("id", JsonPrimitive(value.id.value.toString()))
                        put("type", JsonPrimitive(value.type.name))
                        put("direction", JsonPrimitive(value.direction.name))
                        put("magnitude", money(value.magnitude))
                        put("source", source(value.source))
                        put("occurredAt", JsonPrimitive(value.occurredAt.toString()))
                        put("quality", JsonPrimitive(value.quality.name))
                    }
                )
            }
        }

    private fun decodeComponents(
        values: JsonArray,
        organizationId: OrganizationId,
        marketplaceOrderId: MarketplaceOrderId
    ): List<EconomicComponent> = values.map { element ->
        val value = element.jsonObject
        EconomicComponent(
            organizationId = organizationId,
            id = EconomicComponentId.parse(value.requiredString("id")),
            orderId = marketplaceOrderId,
            type = EconomicComponentType.valueOf(value.requiredString("type")),
            direction = EconomicDirection.valueOf(value.requiredString("direction")),
            magnitude = value.requiredObject("magnitude").toMoney(),
            source = value.requiredObject("source").toSource(),
            occurredAt = Instant.parse(value.requiredString("occurredAt")),
            quality = EconomicEvidenceQuality.valueOf(value.requiredString("quality"))
        )
    }

    private fun source(value: EconomicSource): JsonObject = buildJsonObject {
        put("kind", JsonPrimitive(value.kind.name))
        put("systemKey", JsonPrimitive(value.systemKey.value))
        when (val reference = value.externalReference) {
            is EconomicExternalReferenceState.Present -> {
                put("referenceKind", JsonPrimitive("PRESENT"))
                put("reference", JsonPrimitive(reference.reference.value))
            }
            is EconomicExternalReferenceState.Absent -> {
                put("referenceKind", JsonPrimitive("ABSENT"))
                put("absenceReason", JsonPrimitive(reference.reason.name))
            }
        }
    }

    private fun JsonObject.toSource(): EconomicSource {
        val externalReference = when (requiredString("referenceKind")) {
            "PRESENT" ->
                EconomicExternalReferenceState.Present(
                    EconomicExternalReference(requiredString("reference"))
                )
            "ABSENT" ->
                EconomicExternalReferenceState.Absent(
                    EconomicExternalReferenceAbsenceReason.valueOf(
                        requiredString("absenceReason")
                    )
                )
            else -> error("unknown source reference kind")
        }
        return EconomicSource(
            kind = EconomicSourceKind.valueOf(requiredString("kind")),
            systemKey = EconomicSourceSystemKey(requiredString("systemKey")),
            externalReference = externalReference
        )
    }

    private fun JsonObject.requiredString(name: String): String =
        get(name)?.jsonPrimitive?.contentOrNull ?: error("missing payload field")

    private fun JsonObject.requiredObject(name: String): JsonObject =
        get(name)?.jsonObject ?: error("missing payload object")

    private fun JsonObject.requiredArray(name: String): JsonArray =
        get(name)?.jsonArray ?: error("missing payload array")

    private fun connection(): Connection = DriverManager.getConnection(
        configuration.url,
        configuration.user,
        configuration.password
    )

    private data class EncodedState(
        val stateKind: String,
        val calculationPolicyVersion: String?,
        val calculationKind: String?,
        val payload: String
    )

    companion object {
        private const val DETAIL_SQL =
            "SELECT * FROM marketplace_sales_intelligence_projection " +
                "WHERE organization_id=? AND marketplace_order_id=?"

        private const val LIST_FIRST_SQL =
            "SELECT * FROM marketplace_sales_intelligence_projection " +
                "WHERE organization_id=? " +
                "ORDER BY projected_at DESC, marketplace_order_id DESC LIMIT ?"

        private const val LIST_AFTER_SQL =
            "SELECT * FROM marketplace_sales_intelligence_projection " +
                "WHERE organization_id=? AND " +
                "(projected_at<? OR (projected_at=? AND marketplace_order_id<?)) " +
                "ORDER BY projected_at DESC, marketplace_order_id DESC LIMIT ?"

        private const val UPSERT_SQL =
            "INSERT INTO marketplace_sales_intelligence_projection " +
                "(organization_id,marketplace_order_id,source_evidence_version," +
                "state_kind,assembly_policy_version,calculation_policy_version," +
                "calculation_kind,state_payload,last_applied_change_sequence,projected_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?) " +
                "ON CONFLICT (organization_id,marketplace_order_id) DO UPDATE SET " +
                "source_evidence_version=EXCLUDED.source_evidence_version," +
                "state_kind=EXCLUDED.state_kind," +
                "assembly_policy_version=EXCLUDED.assembly_policy_version," +
                "calculation_policy_version=EXCLUDED.calculation_policy_version," +
                "calculation_kind=EXCLUDED.calculation_kind," +
                "state_payload=EXCLUDED.state_payload," +
                "last_applied_change_sequence=EXCLUDED.last_applied_change_sequence," +
                "projected_at=EXCLUDED.projected_at " +
                "WHERE EXCLUDED.last_applied_change_sequence>" +
                "marketplace_sales_intelligence_projection.last_applied_change_sequence " +
                "RETURNING last_applied_change_sequence"
    }
}
