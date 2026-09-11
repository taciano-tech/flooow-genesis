package io.flooow.marketplace.persistence.postgres

import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerEntryId
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerStage
import io.flooow.marketplace.operations.economics.ledger.FinancialTraceId
import io.flooow.marketplace.operations.economics.reconciliation.DurableReconciliationCase
import io.flooow.marketplace.operations.economics.reconciliation.DurableReconciliationCaseRepository
import io.flooow.marketplace.operations.economics.reconciliation.FinancialReconciliationPolicyVersion
import io.flooow.marketplace.operations.economics.reconciliation.ReconciliationCaseCursor
import io.flooow.marketplace.operations.economics.reconciliation.ReconciliationCaseId
import io.flooow.marketplace.operations.economics.reconciliation.ReconciliationCasePage
import io.flooow.marketplace.operations.economics.reconciliation.ReconciliationCaseStageDifference
import io.flooow.marketplace.operations.economics.reconciliation.ReconciliationCaseStatus
import io.flooow.organization.OrganizationId
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
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class PostgresDurableReconciliationCaseRepository(
    private val configuration: PostgresConfiguration
) : DurableReconciliationCaseRepository {
    override fun save(value: DurableReconciliationCase): DurableReconciliationCase {
        connection().use { connection ->
            connection.prepareStatement(UPSERT_SQL).use { statement ->
                statement.setObject(1, value.organizationId.value)
                statement.setObject(2, value.caseId.valueForPersistence())
                statement.setObject(3, value.orderId.value)
                statement.setObject(4, value.traceId.valueForPersistence())
                statement.setString(5, value.policyVersion.value)
                statement.setString(6, value.currency.code)
                statement.setString(7, value.status.name)
                statement.setTimestamp(8, Timestamp.from(value.openedAt))
                statement.setTimestamp(9, Timestamp.from(value.lastObservedAt))
                if (value.resolvedAt == null) statement.setNull(10, Types.TIMESTAMP_WITH_TIMEZONE)
                else statement.setTimestamp(10, Timestamp.from(value.resolvedAt))
                statement.setLong(11, value.revision)
                statement.setBigDecimal(12, value.absoluteDifferenceSummary.amount)
                statement.setObject(13, encodeStages(value.stages), Types.OTHER)
                statement.setObject(14, buildJsonArray {
                    value.evidenceEntryIds.forEach { add(JsonPrimitive(it.value.toString())) }
                }.toString(), Types.OTHER)
                statement.executeUpdate()
            }
        }
        return value
    }

    override fun find(organizationId: OrganizationId, caseId: ReconciliationCaseId): DurableReconciliationCase? =
        connection().use { connection ->
            connection.prepareStatement(DETAIL_SQL).use { statement ->
                statement.setObject(1, organizationId.value)
                statement.setObject(2, caseId.valueForPersistence())
                statement.executeQuery().use { result -> if (result.next()) result.toCase() else null }
            }
        }

    override fun list(organizationId: OrganizationId, cursor: ReconciliationCaseCursor?, limit: Int): ReconciliationCasePage {
        require(limit in 1..100)
        return connection().use { connection ->
            val sql = if (cursor == null) LIST_FIRST_SQL else LIST_AFTER_SQL
            connection.prepareStatement(sql).use { statement ->
                statement.setObject(1, organizationId.value)
                var index = 2
                if (cursor != null) {
                    statement.setTimestamp(index++, Timestamp.from(cursor.observedAt))
                    statement.setTimestamp(index++, Timestamp.from(cursor.observedAt))
                    statement.setObject(index++, cursor.caseId.valueForPersistence())
                }
                statement.setInt(index, limit + 1)
                statement.executeQuery().use { result ->
                    val rows = buildList { while (result.next()) add(result.toCase()) }
                    val visible = rows.take(limit)
                    ReconciliationCasePage(
                        visible,
                        if (rows.size > limit) ReconciliationCaseCursor(
                            visible.last().lastObservedAt, visible.last().caseId
                        ) else null
                    )
                }
            }
        }
    }

    private fun connection(): Connection = DriverManager.getConnection(configuration.url, configuration.user, configuration.password)

    private fun encodeStages(stages: List<ReconciliationCaseStageDifference>): String = buildJsonArray {
        stages.forEach { stage -> add(buildJsonObject {
            put("stage", JsonPrimitive(stage.stage.name))
            putMoney("expected", stage.expected)
            putMoney("actual", stage.actual)
            putMoney("signedDifference", stage.signedDifference)
            putMoney("absoluteDifference", stage.absoluteDifference)
            putMoney("tolerance", stage.tolerance)
            put("expectedEntryIds", buildJsonArray { stage.expectedEntryIds.forEach { add(JsonPrimitive(it.value.toString())) } })
            put("actualEntryIds", buildJsonArray { stage.actualEntryIds.forEach { add(JsonPrimitive(it.value.toString())) } })
        }) }
    }.toString()

    private fun kotlinx.serialization.json.JsonObjectBuilder.putMoney(name: String, value: MarketplaceMoney?) {
        if (value == null) put(name, JsonNull)
        else put(name, buildJsonObject { put("currency", JsonPrimitive(value.currency.code)); put("amount", JsonPrimitive(value.amount.toPlainString())) })
    }

    private fun ResultSet.toCase(): DurableReconciliationCase {
        val currency = MarketplaceCurrency(getString("currency"))
        val stageValues = Json.parseToJsonElement(getString("stage_details")).jsonArray.map { it.jsonObject }
        fun money(value: JsonObject): MarketplaceMoney = MarketplaceMoney.parse(currency, value.string("amount"))
        fun optionalMoney(element: JsonElement?): MarketplaceMoney? = element?.jsonObject?.let(::money)
        val stages = stageValues.map { value ->
            ReconciliationCaseStageDifference(
                FinancialLedgerStage.valueOf(value.string("stage")),
                optionalMoney(value["expected"]), optionalMoney(value["actual"]),
                optionalMoney(value["signedDifference"]), optionalMoney(value["absoluteDifference"]),
                money(value["tolerance"]!!.jsonObject), ids(value["expectedEntryIds"]), ids(value["actualEntryIds"])
            )
        }
        val evidence = Json.parseToJsonElement(getString("evidence_entry_ids")).jsonArray.map {
            FinancialLedgerEntryId.of(UUID.fromString(it.jsonPrimitive.content))
        }
        return DurableReconciliationCase(
            ReconciliationCaseId.of(getObject("case_id", UUID::class.java)),
            OrganizationId(getObject("organization_id", UUID::class.java)),
            MarketplaceOrderId(getObject("marketplace_order_id", UUID::class.java)),
            FinancialTraceId.of(getObject("financial_trace_id", UUID::class.java)),
            FinancialReconciliationPolicyVersion(getString("policy_version")), currency,
            ReconciliationCaseStatus.valueOf(getString("status")),
            getTimestamp("opened_at").toInstant(), getTimestamp("last_observed_at").toInstant(),
            getTimestamp("resolved_at")?.toInstant(), getLong("revision"),
            MarketplaceMoney.parse(currency, getBigDecimal("absolute_difference_summary").toPlainString()), stages, evidence
        )
    }

    private fun ids(element: JsonElement?): List<FinancialLedgerEntryId> = element?.jsonArray?.map {
        FinancialLedgerEntryId.of(UUID.fromString(it.jsonPrimitive.content))
    } ?: emptyList()

    companion object {
        private const val DETAIL_SQL = "SELECT * FROM marketplace_reconciliation_case WHERE organization_id=? AND case_id=?"
        private const val LIST_FIRST_SQL = "SELECT * FROM marketplace_reconciliation_case WHERE organization_id=? ORDER BY last_observed_at DESC,case_id DESC LIMIT ?"
        private const val LIST_AFTER_SQL = "SELECT * FROM marketplace_reconciliation_case WHERE organization_id=? AND (last_observed_at<? OR (last_observed_at=? AND case_id<?)) ORDER BY last_observed_at DESC,case_id DESC LIMIT ?"
        private const val UPSERT_SQL = """
            INSERT INTO marketplace_reconciliation_case
            (organization_id,case_id,marketplace_order_id,financial_trace_id,policy_version,currency,status,
             opened_at,last_observed_at,resolved_at,revision,absolute_difference_summary,stage_details,evidence_entry_ids)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT (organization_id, financial_trace_id, policy_version) DO UPDATE SET
              status=EXCLUDED.status,last_observed_at=EXCLUDED.last_observed_at,resolved_at=EXCLUDED.resolved_at,
              revision=EXCLUDED.revision,absolute_difference_summary=EXCLUDED.absolute_difference_summary,
              stage_details=EXCLUDED.stage_details,evidence_entry_ids=EXCLUDED.evidence_entry_ids
            WHERE EXCLUDED.revision > marketplace_reconciliation_case.revision
        """
    }
}

// Kept local to keep JDBC reconstruction code compact and avoid leaking driver types into the domain.
private fun JsonObject.string(name: String): String = get(name)?.jsonPrimitive?.content ?: error("missing case field")
private fun JsonObject.jsonObject(name: String): JsonObject = get(name)?.jsonObject ?: error("missing case money")
