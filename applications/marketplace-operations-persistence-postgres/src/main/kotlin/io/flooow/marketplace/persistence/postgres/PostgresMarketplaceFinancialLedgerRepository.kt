package io.flooow.marketplace.persistence.postgres

import io.flooow.marketplace.operations.economics.EconomicDirection
import io.flooow.marketplace.operations.economics.EconomicExternalReference
import io.flooow.marketplace.operations.economics.EconomicExternalReferenceAbsenceReason
import io.flooow.marketplace.operations.economics.EconomicExternalReferenceState
import io.flooow.marketplace.operations.economics.EconomicSource
import io.flooow.marketplace.operations.economics.EconomicSourceKind
import io.flooow.marketplace.operations.economics.EconomicSourceSystemKey
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerAppendResult
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerAppendRequestId
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerBasis
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerEntryDraft
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerEntryId
import io.flooow.marketplace.operations.economics.ledger.FinancialLedgerStage
import io.flooow.marketplace.operations.economics.ledger.FinancialTrace
import io.flooow.marketplace.operations.economics.ledger.FinancialTraceId
import io.flooow.marketplace.operations.economics.ledger.FinancialTraceOpenRequestId
import io.flooow.marketplace.operations.economics.ledger.FinancialTraceOpenResult
import io.flooow.marketplace.operations.economics.ledger.FinancialTraceReadResult
import io.flooow.marketplace.operations.economics.ledger.MarketplaceFinancialLedgerRepository
import io.flooow.marketplace.operations.economics.ledger.OpenFinancialTrace
import io.flooow.marketplace.operations.economics.ledger.RecordedFinancialLedgerEntry
import io.flooow.organization.OrganizationId
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class PostgresMarketplaceFinancialLedgerRepository(
    private val configuration: PostgresConfiguration
) : MarketplaceFinancialLedgerRepository {

    override fun open(
        command: OpenFinancialTrace,
        traceId: FinancialTraceId
    ): FinancialTraceOpenResult = try {
        connection().use { connection ->
            connection.autoCommit = false
            try {
                open(connection, command, traceId).also { connection.commit() }
            } catch (error: Exception) {
                connection.rollback()
                throw error
            }
        }
    } catch (_: Exception) {
        openAfterFailure(command, traceId)
    }

    override fun append(
        draft: FinancialLedgerEntryDraft,
        entryId: FinancialLedgerEntryId
    ): FinancialLedgerAppendResult = try {
        connection().use { connection ->
            connection.autoCommit = false
            try {
                append(connection, draft, entryId).also { connection.commit() }
            } catch (error: Exception) {
                connection.rollback()
                throw error
            }
        }
    } catch (_: Exception) {
        appendAfterFailure(draft, entryId)
    }

    override fun find(
        organizationId: OrganizationId,
        traceId: FinancialTraceId
    ): FinancialTraceReadResult = try {
        connection().use { connection ->
            val root = rootByTrace(connection, organizationId, traceId)
                ?: return FinancialTraceReadResult.NotFound
            FinancialTraceReadResult.Found(loadTrace(connection, root))
        }
    } catch (_: Exception) {
        FinancialTraceReadResult.IntegrityFailure
    }

    override fun findByOrder(
        organizationId: OrganizationId,
        orderId: MarketplaceOrderId
    ): FinancialTraceReadResult = try {
        connection().use { connection ->
            val root = rootByOrder(connection, organizationId, orderId)
                ?: return FinancialTraceReadResult.NotFound
            FinancialTraceReadResult.Found(loadTrace(connection, root))
        }
    } catch (_: Exception) {
        FinancialTraceReadResult.IntegrityFailure
    }

    private fun open(
        connection: Connection,
        command: OpenFinancialTrace,
        traceId: FinancialTraceId
    ): FinancialTraceOpenResult {
        replayOpen(connection, command)?.let { return it }
        if (!lockActiveOrganization(connection, command.organizationId)) {
            return FinancialTraceOpenResult.OrganizationUnavailable
        }
        rootByOrder(connection, command.organizationId, command.orderId)?.let { existing ->
            return if (existing.matchesOrder(command)) {
                FinancialTraceOpenResult.OrderAlreadyTraced(existing.id)
            } else {
                FinancialTraceOpenResult.IntegrityFailure
            }
        }
        if (rootByTrace(connection, command.organizationId, traceId) != null) {
            return FinancialTraceOpenResult.Conflict
        }

        connection.prepareStatement(
            "INSERT INTO marketplace_financial_trace " +
                "(organization_id,trace_id,open_request_id,order_id,marketplace_key," +
                "external_order_id,currency) VALUES (?,?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, command.organizationId.value)
            statement.setObject(2, traceId.valueForPersistence())
            statement.setObject(3, command.requestId.valueForPersistence())
            statement.setObject(4, command.orderId.value)
            statement.setString(5, command.marketplace.value)
            statement.setString(6, command.externalOrderId.value)
            statement.setString(7, command.currency.code)
            check(statement.executeUpdate() == 1)
        }
        return FinancialTraceOpenResult.Opened(traceId)
    }

    private fun append(
        connection: Connection,
        draft: FinancialLedgerEntryDraft,
        entryId: FinancialLedgerEntryId
    ): FinancialLedgerAppendResult {
        replayAppendRequest(connection, draft)?.let { return it }
        if (!lockActiveOrganization(connection, draft.organizationId)) {
            return FinancialLedgerAppendResult.OrganizationUnavailable
        }
        val root = lockTrace(connection, draft.organizationId, draft.traceId)
            ?: return FinancialLedgerAppendResult.TraceUnavailable
        if (draft.magnitude.currency != root.currency) {
            return FinancialLedgerAppendResult.Conflict
        }

        replaySourceFact(connection, draft)?.let { return it }
        if (entryById(connection, draft.organizationId, entryId) != null) {
            return FinancialLedgerAppendResult.Conflict
        }
        when (validateCorrection(connection, draft)) {
            CorrectionValidation.VALID -> Unit
            CorrectionValidation.UNAVAILABLE ->
                return FinancialLedgerAppendResult.CorrectionTargetUnavailable
            CorrectionValidation.CONFLICT -> return FinancialLedgerAppendResult.Conflict
        }

        insertEntry(connection, draft, entryId)
        return FinancialLedgerAppendResult.Appended(entryId)
    }

    private fun replayOpen(
        connection: Connection,
        command: OpenFinancialTrace
    ): FinancialTraceOpenResult? {
        val existing = connection.prepareStatement(
            "SELECT * FROM marketplace_financial_trace " +
                "WHERE organization_id=? AND open_request_id=?"
        ).use { statement ->
            statement.setObject(1, command.organizationId.value)
            statement.setObject(2, command.requestId.valueForPersistence())
            statement.executeQuery().use { result ->
                if (result.next()) StoredTraceRoot(result) else null
            }
        } ?: return null
        return if (existing.matchesRequest(command)) {
            FinancialTraceOpenResult.AlreadyOpen(existing.id)
        } else {
            FinancialTraceOpenResult.Conflict
        }
    }

    private fun replayAppendRequest(
        connection: Connection,
        draft: FinancialLedgerEntryDraft
    ): FinancialLedgerAppendResult? {
        val existing = connection.prepareStatement(
            "SELECT entry.*,trace.currency FROM marketplace_financial_ledger_entry entry " +
                "JOIN marketplace_financial_trace trace " +
                "ON trace.organization_id=entry.organization_id AND trace.trace_id=entry.trace_id " +
                "WHERE entry.organization_id=? AND entry.append_request_id=?"
        ).use { statement ->
            statement.setObject(1, draft.organizationId.value)
            statement.setObject(2, draft.requestId.valueForPersistence())
            statement.executeQuery().use { result ->
                if (result.next()) storedEntry(result) else null
            }
        } ?: return null
        return if (existing.matches(draft)) {
            FinancialLedgerAppendResult.AlreadyAppended(existing.id)
        } else {
            FinancialLedgerAppendResult.Conflict
        }
    }

    private fun replaySourceFact(
        connection: Connection,
        draft: FinancialLedgerEntryDraft
    ): FinancialLedgerAppendResult? {
        val present = draft.source.externalReference as?
            EconomicExternalReferenceState.Present ?: return null
        val existing = connection.prepareStatement(
            "SELECT entry.*,trace.currency FROM marketplace_financial_ledger_entry entry " +
                "JOIN marketplace_financial_trace trace " +
                "ON trace.organization_id=entry.organization_id AND trace.trace_id=entry.trace_id " +
                "WHERE entry.organization_id=? AND entry.source_kind=? " +
                "AND entry.source_system_key=? AND entry.external_reference=? " +
                "AND entry.stage=? AND entry.basis=?"
        ).use { statement ->
            statement.setObject(1, draft.organizationId.value)
            statement.setString(2, draft.source.kind.name)
            statement.setString(3, draft.source.systemKey.value)
            statement.setString(4, present.reference.value)
            statement.setString(5, draft.stage.name)
            statement.setString(6, draft.basis.name)
            statement.executeQuery().use { result ->
                if (result.next()) storedEntry(result) else null
            }
        } ?: return null
        return if (existing.matchesIgnoringRequest(draft)) {
            FinancialLedgerAppendResult.AlreadyAppended(existing.id)
        } else {
            FinancialLedgerAppendResult.Conflict
        }
    }

    private fun validateCorrection(
        connection: Connection,
        draft: FinancialLedgerEntryDraft
    ): CorrectionValidation {
        val targetId = draft.correctsEntryId ?: return CorrectionValidation.VALID
        val target = connection.prepareStatement(
            "SELECT entry.*,trace.currency FROM marketplace_financial_ledger_entry entry " +
                "JOIN marketplace_financial_trace trace " +
                "ON trace.organization_id=entry.organization_id AND trace.trace_id=entry.trace_id " +
                "WHERE entry.organization_id=? AND entry.entry_id=? FOR UPDATE OF entry"
        ).use { statement ->
            statement.setObject(1, draft.organizationId.value)
            statement.setObject(2, targetId.valueForPersistence())
            statement.executeQuery().use { result ->
                if (result.next()) storedEntry(result) else null
            }
        } ?: return CorrectionValidation.UNAVAILABLE

        if (
            target.traceId != draft.traceId ||
            target.stage != draft.stage ||
            target.basis != draft.basis
        ) {
            return CorrectionValidation.UNAVAILABLE
        }
        val alreadyCorrected = connection.prepareStatement(
            "SELECT 1 FROM marketplace_financial_ledger_entry " +
                "WHERE organization_id=? AND corrects_entry_id=?"
        ).use { statement ->
            statement.setObject(1, draft.organizationId.value)
            statement.setObject(2, targetId.valueForPersistence())
            statement.executeQuery().use(ResultSet::next)
        }
        return if (alreadyCorrected) CorrectionValidation.CONFLICT else CorrectionValidation.VALID
    }

    private fun insertEntry(
        connection: Connection,
        draft: FinancialLedgerEntryDraft,
        entryId: FinancialLedgerEntryId
    ) {
        val present = draft.source.externalReference as?
            EconomicExternalReferenceState.Present
        val absent = draft.source.externalReference as?
            EconomicExternalReferenceState.Absent
        connection.prepareStatement(
            "INSERT INTO marketplace_financial_ledger_entry " +
                "(organization_id,entry_id,append_request_id,trace_id,stage,basis,direction," +
                "magnitude,source_kind,source_system_key,external_reference," +
                "external_reference_absence_reason,occurred_at,corrects_entry_id) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, draft.organizationId.value)
            statement.setObject(2, entryId.valueForPersistence())
            statement.setObject(3, draft.requestId.valueForPersistence())
            statement.setObject(4, draft.traceId.valueForPersistence())
            statement.setString(5, draft.stage.name)
            statement.setString(6, draft.basis.name)
            statement.setString(7, draft.direction.name)
            statement.setBigDecimal(8, draft.magnitude.amount)
            statement.setString(9, draft.source.kind.name)
            statement.setString(10, draft.source.systemKey.value)
            statement.setString(11, present?.reference?.value)
            statement.setString(12, absent?.reason?.name)
            statement.setTimestamp(13, Timestamp.from(draft.occurredAt))
            statement.setObject(14, draft.correctsEntryId?.valueForPersistence())
            check(statement.executeUpdate() == 1)
        }
    }

    private fun openAfterFailure(
        command: OpenFinancialTrace,
        traceId: FinancialTraceId
    ): FinancialTraceOpenResult = try {
        connection().use { connection ->
            replayOpen(connection, command)
                ?: rootByOrder(connection, command.organizationId, command.orderId)?.let {
                    if (it.matchesOrder(command)) FinancialTraceOpenResult.OrderAlreadyTraced(it.id)
                    else FinancialTraceOpenResult.IntegrityFailure
                }
                ?: if (!organizationActive(connection, command.organizationId)) {
                    FinancialTraceOpenResult.OrganizationUnavailable
                } else if (rootByTrace(connection, command.organizationId, traceId) != null) {
                    FinancialTraceOpenResult.Conflict
                } else {
                    FinancialTraceOpenResult.IntegrityFailure
                }
        }
    } catch (_: Exception) {
        FinancialTraceOpenResult.IntegrityFailure
    }

    private fun appendAfterFailure(
        draft: FinancialLedgerEntryDraft,
        entryId: FinancialLedgerEntryId
    ): FinancialLedgerAppendResult = try {
        connection().use { connection ->
            replayAppendRequest(connection, draft)
                ?: if (!organizationActive(connection, draft.organizationId)) {
                    FinancialLedgerAppendResult.OrganizationUnavailable
                } else if (rootByTrace(connection, draft.organizationId, draft.traceId) == null) {
                    FinancialLedgerAppendResult.TraceUnavailable
                } else {
                    replaySourceFact(connection, draft)
                        ?: if (entryById(connection, draft.organizationId, entryId) != null) {
                            FinancialLedgerAppendResult.Conflict
                        } else if (draft.correctsEntryId?.let { target ->
                                correctionExists(connection, draft.organizationId, target)
                            } == true
                        ) {
                            FinancialLedgerAppendResult.Conflict
                        } else {
                            FinancialLedgerAppendResult.IntegrityFailure
                        }
                }
        }
    } catch (_: Exception) {
        FinancialLedgerAppendResult.IntegrityFailure
    }

    private fun rootByTrace(
        connection: Connection,
        organizationId: OrganizationId,
        traceId: FinancialTraceId
    ): StoredTraceRoot? = connection.prepareStatement(
        "SELECT * FROM marketplace_financial_trace WHERE organization_id=? AND trace_id=?"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, traceId.valueForPersistence())
        statement.executeQuery().use { result ->
            if (result.next()) StoredTraceRoot(result) else null
        }
    }

    private fun rootByOrder(
        connection: Connection,
        organizationId: OrganizationId,
        orderId: MarketplaceOrderId
    ): StoredTraceRoot? = connection.prepareStatement(
        "SELECT * FROM marketplace_financial_trace WHERE organization_id=? AND order_id=?"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, orderId.value)
        statement.executeQuery().use { result ->
            if (result.next()) StoredTraceRoot(result) else null
        }
    }

    private fun lockTrace(
        connection: Connection,
        organizationId: OrganizationId,
        traceId: FinancialTraceId
    ): StoredTraceRoot? = connection.prepareStatement(
        "SELECT * FROM marketplace_financial_trace " +
            "WHERE organization_id=? AND trace_id=? FOR SHARE"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, traceId.valueForPersistence())
        statement.executeQuery().use { result ->
            if (result.next()) StoredTraceRoot(result) else null
        }
    }

    private fun entryById(
        connection: Connection,
        organizationId: OrganizationId,
        entryId: FinancialLedgerEntryId
    ): StoredEntry? = connection.prepareStatement(
        "SELECT entry.*,trace.currency FROM marketplace_financial_ledger_entry entry " +
            "JOIN marketplace_financial_trace trace " +
            "ON trace.organization_id=entry.organization_id AND trace.trace_id=entry.trace_id " +
            "WHERE entry.organization_id=? AND entry.entry_id=?"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, entryId.valueForPersistence())
        statement.executeQuery().use { result ->
            if (result.next()) storedEntry(result) else null
        }
    }

    private fun loadTrace(connection: Connection, root: StoredTraceRoot): FinancialTrace {
        val entries = connection.prepareStatement(
            "SELECT entry.*,trace.currency FROM marketplace_financial_ledger_entry entry " +
                "JOIN marketplace_financial_trace trace " +
                "ON trace.organization_id=entry.organization_id AND trace.trace_id=entry.trace_id " +
                "WHERE entry.organization_id=? AND entry.trace_id=? " +
                "ORDER BY entry.recorded_at,entry.entry_id"
        ).use { statement ->
            statement.setObject(1, root.organizationId.value)
            statement.setObject(2, root.id.valueForPersistence())
            statement.executeQuery().use { result ->
                buildList {
                    while (result.next()) add(storedEntry(result).record)
                }
            }
        }
        return FinancialTrace(
            root.organizationId,
            root.id,
            root.requestId,
            root.orderId,
            root.marketplace,
            root.externalOrderId,
            root.currency,
            root.openedAt,
            entries
        )
    }

    private fun lockActiveOrganization(
        connection: Connection,
        organizationId: OrganizationId
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_organization " +
            "WHERE organization_id=? AND status='ACTIVE' FOR SHARE"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.executeQuery().use(ResultSet::next)
    }

    private fun organizationActive(
        connection: Connection,
        organizationId: OrganizationId
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_organization WHERE organization_id=? AND status='ACTIVE'"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.executeQuery().use(ResultSet::next)
    }

    private fun correctionExists(
        connection: Connection,
        organizationId: OrganizationId,
        targetId: FinancialLedgerEntryId
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM marketplace_financial_ledger_entry " +
            "WHERE organization_id=? AND corrects_entry_id=?"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, targetId.valueForPersistence())
        statement.executeQuery().use(ResultSet::next)
    }

    private fun connection(): Connection = DriverManager.getConnection(
        configuration.url,
        configuration.user,
        configuration.password
    )

    private data class StoredTraceRoot(
        val organizationId: OrganizationId,
        val id: FinancialTraceId,
        val requestId: FinancialTraceOpenRequestId,
        val orderId: MarketplaceOrderId,
        val marketplace: MarketplaceKey,
        val externalOrderId: MarketplaceExternalOrderId,
        val currency: MarketplaceCurrency,
        val openedAt: Instant
    ) {
        constructor(result: ResultSet) : this(
            OrganizationId(result.getObject("organization_id", UUID::class.java)),
            FinancialTraceId.of(result.getObject("trace_id", UUID::class.java)),
            FinancialTraceOpenRequestId.of(result.getObject("open_request_id", UUID::class.java)),
            MarketplaceOrderId(result.getObject("order_id", UUID::class.java)),
            MarketplaceKey(result.getString("marketplace_key")),
            MarketplaceExternalOrderId(result.getString("external_order_id")),
            MarketplaceCurrency(result.getString("currency")),
            result.getTimestamp("opened_at").toInstant()
        )

        fun matchesRequest(command: OpenFinancialTrace): Boolean =
            organizationId == command.organizationId &&
                requestId == command.requestId &&
                matchesOrder(command)

        fun matchesOrder(command: OpenFinancialTrace): Boolean =
            organizationId == command.organizationId &&
                orderId == command.orderId &&
                marketplace == command.marketplace &&
                externalOrderId == command.externalOrderId &&
                currency == command.currency
    }

    internal data class StoredEntry(val record: RecordedFinancialLedgerEntry) {
        val id: FinancialLedgerEntryId get() = record.id
        val traceId: FinancialTraceId get() = record.traceId
        val stage: FinancialLedgerStage get() = record.stage
        val basis: FinancialLedgerBasis get() = record.basis

        fun matches(draft: FinancialLedgerEntryDraft): Boolean =
            record.requestId == draft.requestId && matchesIgnoringRequest(draft)

        fun matchesIgnoringRequest(draft: FinancialLedgerEntryDraft): Boolean =
            record.organizationId == draft.organizationId &&
                record.traceId == draft.traceId &&
                record.stage == draft.stage &&
                record.basis == draft.basis &&
                record.direction == draft.direction &&
                record.magnitude == draft.magnitude &&
                record.source == draft.source &&
                record.occurredAt == draft.occurredAt &&
                record.correctsEntryId == draft.correctsEntryId
    }

    private enum class CorrectionValidation {
        VALID,
        UNAVAILABLE,
        CONFLICT
    }
}

private fun storedEntry(result: ResultSet): PostgresMarketplaceFinancialLedgerRepository.StoredEntry {
    val currency = MarketplaceCurrency(result.getString("currency"))
    val reference = result.getString("external_reference")
    val absence = result.getString("external_reference_absence_reason")
    val sourceState = if (reference != null) {
        EconomicExternalReferenceState.Present(EconomicExternalReference(reference))
    } else {
        EconomicExternalReferenceState.Absent(
            EconomicExternalReferenceAbsenceReason.valueOf(requireNotNull(absence))
        )
    }
    val correction = result.getObject("corrects_entry_id", UUID::class.java)
    return PostgresMarketplaceFinancialLedgerRepository.StoredEntry(
        RecordedFinancialLedgerEntry(
            organizationId = OrganizationId(result.getObject("organization_id", UUID::class.java)),
            id = FinancialLedgerEntryId.of(result.getObject("entry_id", UUID::class.java)),
            requestId = FinancialLedgerAppendRequestId.of(
                result.getObject("append_request_id", UUID::class.java)
            ),
            traceId = FinancialTraceId.of(result.getObject("trace_id", UUID::class.java)),
            stage = FinancialLedgerStage.valueOf(result.getString("stage")),
            basis = FinancialLedgerBasis.valueOf(result.getString("basis")),
            direction = EconomicDirection.valueOf(result.getString("direction")),
            magnitude = MarketplaceMoney.parse(currency, result.getBigDecimal("magnitude").toPlainString()),
            source = EconomicSource(
                EconomicSourceKind.valueOf(result.getString("source_kind")),
                EconomicSourceSystemKey(result.getString("source_system_key")),
                sourceState
            ),
            occurredAt = result.getTimestamp("occurred_at").toInstant(),
            recordedAt = result.getTimestamp("recorded_at").toInstant(),
            correctsEntryId = correction?.let(FinancialLedgerEntryId::of)
        )
    )
}
