package io.flooow.marketplace.persistence.postgres

import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerStage
import io.flooow.marketplace.operations.economics.reconciliation.ReconciliationCaseId
import io.flooow.marketplace.operations.economics.reconciliation.SystemicDivergenceSignal
import io.flooow.marketplace.operations.economics.reconciliation.SystemicDivergenceSignalCursor
import io.flooow.marketplace.operations.economics.reconciliation.SystemicDivergenceSignalId
import io.flooow.marketplace.operations.economics.reconciliation.SystemicDivergenceSignalPage
import io.flooow.marketplace.operations.economics.reconciliation.SystemicDivergenceSignalRepository
import io.flooow.marketplace.operations.economics.reconciliation.SystemicDivergenceSignalStatus
import io.flooow.organization.OrganizationId
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Duration
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray

class PostgresSystemicDivergenceSignalRepository(private val configuration: PostgresConfiguration) : SystemicDivergenceSignalRepository {
    override fun save(value: SystemicDivergenceSignal): SystemicDivergenceSignal {
        DriverManager.getConnection(configuration.url, configuration.user, configuration.password).use { connection ->
            connection.prepareStatement(UPSERT).use { statement ->
                statement.setObject(1, value.organizationId.value); statement.setObject(2, value.signalId.valueForPersistence())
                statement.setString(3, value.stage.name); statement.setString(4, value.currency.code); statement.setString(5, value.policyVersion)
                statement.setLong(6, value.window.toMillis()); statement.setTimestamp(7, Timestamp.from(value.firstSeenAt)); statement.setTimestamp(8, Timestamp.from(value.lastSeenAt))
                statement.setInt(9, value.occurrenceCount); statement.setBigDecimal(10, value.absoluteDifference.amount); statement.setString(11, value.status.name)
                statement.setLong(12, value.revision); statement.setObject(13, buildJsonArray { value.caseIds.forEach { add(JsonPrimitive(it.value.toString())) } }.toString(), java.sql.Types.OTHER)
                statement.executeUpdate()
            }
        }
        return value
    }
    override fun find(organizationId: OrganizationId, signalId: SystemicDivergenceSignalId): SystemicDivergenceSignal? = query(DETAIL, organizationId, signalId.valueForPersistence()) { if(it.next()) it.toSignal() else null }
    override fun list(organizationId: OrganizationId, cursor: SystemicDivergenceSignalCursor?, limit: Int): SystemicDivergenceSignalPage {
        require(limit in 1..100)
        val rows = if(cursor == null) query(LIST, organizationId) { buildList { while(it.next()) add(it.toSignal()) } } else query(LIST_AFTER, organizationId, Timestamp.from(cursor.lastSeenAt), Timestamp.from(cursor.lastSeenAt), cursor.signalId.valueForPersistence()) { buildList { while(it.next()) add(it.toSignal()) } }
        val visible = rows.take(limit)
        return SystemicDivergenceSignalPage(visible, if(rows.size > limit) SystemicDivergenceSignalCursor(visible.last().lastSeenAt, visible.last().signalId) else null)
    }
    private fun <T> query(sql: String, organizationId: OrganizationId, vararg args: Any, block: (java.sql.ResultSet) -> T): T = DriverManager.getConnection(configuration.url, configuration.user, configuration.password).use { c -> c.prepareStatement(sql).use { s -> s.setObject(1, organizationId.value); args.forEachIndexed { i, v -> s.setObject(i + 2, v) }; s.executeQuery().use(block) } }
    private fun ResultSet.toSignal(): SystemicDivergenceSignal {
        val currency = MarketplaceCurrency(getString("currency"))
        val ids = Json.parseToJsonElement(getString("case_ids")).jsonArray.map { ReconciliationCaseId.of(UUID.fromString(it.toString().trim('"'))) }
        return SystemicDivergenceSignal(SystemicDivergenceSignalId.of(getObject("signal_id", UUID::class.java)), OrganizationId(getObject("organization_id", UUID::class.java)), FinancialLedgerStage.valueOf(getString("stage")), currency, getString("policy_version"), Duration.ofMillis(getLong("window_millis")), getTimestamp("first_seen_at").toInstant(), getTimestamp("last_seen_at").toInstant(), getInt("occurrence_count"), MarketplaceMoney.parse(currency, getBigDecimal("absolute_difference").toPlainString()), SystemicDivergenceSignalStatus.valueOf(getString("status")), ids, getLong("revision"))
    }
    companion object {
        private const val DETAIL = "SELECT * FROM marketplace_systemic_divergence_signal WHERE organization_id=? AND signal_id=?"
        private const val LIST = "SELECT * FROM marketplace_systemic_divergence_signal WHERE organization_id=? ORDER BY last_seen_at DESC,signal_id DESC LIMIT 101"
        private const val LIST_AFTER = "SELECT * FROM marketplace_systemic_divergence_signal WHERE organization_id=? AND (last_seen_at<? OR (last_seen_at=? AND signal_id<?)) ORDER BY last_seen_at DESC,signal_id DESC LIMIT 101"
        private const val UPSERT = """INSERT INTO marketplace_systemic_divergence_signal (organization_id,signal_id,stage,currency,policy_version,window_millis,first_seen_at,last_seen_at,occurrence_count,absolute_difference,status,revision,case_ids) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT (organization_id,signal_id) DO UPDATE SET first_seen_at=EXCLUDED.first_seen_at,last_seen_at=EXCLUDED.last_seen_at,occurrence_count=EXCLUDED.occurrence_count,absolute_difference=EXCLUDED.absolute_difference,revision=EXCLUDED.revision,case_ids=EXCLUDED.case_ids WHERE EXCLUDED.revision > marketplace_systemic_divergence_signal.revision"""
    }
}
