package io.flooow.marketplace.persistence.postgres

import io.flooow.marketplace.operations.economics.EconomicComponent
import io.flooow.marketplace.operations.economics.EconomicComponentCoverage
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
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicComponentObservation
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceAttemptOutcome
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceCollectionAttempt
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceCorrection
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceCorrectionReason
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceFamily
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceObservationId
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceSubject
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceVersion
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicExternalIdentityKind
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicExternalIdentityObservation
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidence
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceMerger
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidencePersistResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceReadResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceRepository
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceUpdate
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicFact
import io.flooow.marketplace.operations.economics.evidence.VersionedMarketplaceIndependentEconomicEvidence
import io.flooow.marketplace.operations.economics.evidence.valueForPersistence
import io.flooow.organization.OrganizationId
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.util.UUID

private const val MAX_TRANSACTION_ATTEMPTS = 3
private val RETRYABLE_TRANSACTION_SQL_STATES = setOf("40P01", "40001")

class PostgresMarketplaceIndependentEconomicEvidenceRepository(
    private val configuration: PostgresConfiguration
) : MarketplaceIndependentEconomicEvidenceRepository {

    override fun find(
        subject: MarketplaceEconomicEvidenceSubject
    ): MarketplaceIndependentEconomicEvidenceReadResult = try {
        connection().use { connection ->
            connection.autoCommit = false
            connection.transactionIsolation = Connection.TRANSACTION_REPEATABLE_READ
            try {
                val root = root(connection, subject.organizationId, subject.orderId)
                    ?: return MarketplaceIndependentEconomicEvidenceReadResult.NotFound
                val result = if (!root.matches(subject)) {
                    MarketplaceIndependentEconomicEvidenceReadResult.IntegrityFailure
                } else {
                    val versioned = replay(connection, root)
                    MarketplaceIndependentEconomicEvidenceReadResult.Found(versioned)
                }
                connection.commit()
                result
            } catch (error: Exception) {
                connection.rollback()
                throw error
            }
        }
    } catch (_: Exception) {
        MarketplaceIndependentEconomicEvidenceReadResult.IntegrityFailure
    }

    override fun apply(
        expectedVersion: MarketplaceEconomicEvidenceVersion,
        update: MarketplaceIndependentEconomicEvidenceUpdate
    ): MarketplaceIndependentEconomicEvidencePersistResult {
        repeat(MAX_TRANSACTION_ATTEMPTS) { attempt ->
            try {
                return applyTransaction(expectedVersion, update)
            } catch (error: Exception) {
                if (!error.hasRetryableSqlState() || attempt == MAX_TRANSACTION_ATTEMPTS - 1) {
                    return classifyFailure(update)
                }
            }
        }
        return MarketplaceIndependentEconomicEvidencePersistResult.IntegrityFailure
    }

    private fun applyTransaction(
        expectedVersion: MarketplaceEconomicEvidenceVersion,
        update: MarketplaceIndependentEconomicEvidenceUpdate
    ): MarketplaceIndependentEconomicEvidencePersistResult =
        connection().use { connection ->
            connection.autoCommit = false
            try {
                val outcome = apply(connection, expectedVersion, update)
                if (outcome.rollback) {
                    connection.rollback()
                } else {
                    connection.commit()
                }
                outcome.result
            } catch (error: Exception) {
                connection.rollback()
                throw error
            }
        }

    private fun apply(
        connection: Connection,
        expectedVersion: MarketplaceEconomicEvidenceVersion,
        update: MarketplaceIndependentEconomicEvidenceUpdate
    ): TransactionOutcome {
        val candidateCreated = insertCandidateRoot(connection, update.subject)
        val root = lockRoot(connection, update.subject.organizationId, update.subject.orderId)
            ?: return rollback(MarketplaceIndependentEconomicEvidencePersistResult.IntegrityFailure)
        if (!root.matches(update.subject)) {
            return outcome(
                MarketplaceIndependentEconomicEvidencePersistResult.IntegrityFailure,
                candidateCreated
            )
        }

        val current = replay(connection, root)
        val domainResult = MarketplaceIndependentEconomicEvidenceMerger.apply(
            current.evidence,
            update
        )

        if (domainResult is MarketplaceIndependentEconomicEvidenceResult.Duplicate) {
            return outcome(
                MarketplaceIndependentEconomicEvidencePersistResult.Duplicate(current),
                candidateCreated
            )
        }

        if (!lockActiveOrganization(connection, update.subject.organizationId)) {
            return outcome(
                MarketplaceIndependentEconomicEvidencePersistResult.OrganizationUnavailable,
                candidateCreated
            )
        }

        mapConflict(domainResult)?.let { conflict ->
            return outcome(conflict, candidateCreated)
        }

        val applied = domainResult as? MarketplaceIndependentEconomicEvidenceResult.Applied
            ?: return outcome(
                MarketplaceIndependentEconomicEvidencePersistResult.IntegrityFailure,
                candidateCreated
            )
        if (expectedVersion != current.version) {
            return outcome(
                MarketplaceIndependentEconomicEvidencePersistResult.StaleVersion(current.version),
                candidateCreated
            )
        }

        val nextVersion = current.version.next()
        appendUpdate(connection, nextVersion, update)
        advanceRoot(connection, root, nextVersion)
        return commit(
            MarketplaceIndependentEconomicEvidencePersistResult.Applied(
                VersionedMarketplaceIndependentEconomicEvidence(applied.evidence, nextVersion)
            )
        )
    }

    private fun replay(
        connection: Connection,
        root: StoredRoot
    ): VersionedMarketplaceIndependentEconomicEvidence {
        var evidence = MarketplaceIndependentEconomicEvidence.empty(root.subject)
        var expectedVersion = 1L
        connection.prepareStatement(
            "SELECT evidence_version,update_id,change_kind " +
                "FROM marketplace_economic_evidence_update " +
                "WHERE organization_id=? AND marketplace_order_id=? " +
                "ORDER BY evidence_version"
        ).use { statement ->
            statement.setObject(1, root.subject.organizationId.value)
            statement.setObject(2, root.subject.orderId.value)
            statement.executeQuery().use { result ->
                while (result.next()) {
                    val storedVersion = result.getLong("evidence_version")
                    check(storedVersion == expectedVersion)
                    val update = storedUpdate(
                        connection,
                        root.subject,
                        storedVersion,
                        result.getObject("update_id", UUID::class.java),
                        result.getString("change_kind")
                    )
                    val replayed = MarketplaceIndependentEconomicEvidenceMerger.apply(evidence, update)
                    check(replayed is MarketplaceIndependentEconomicEvidenceResult.Applied)
                    evidence = replayed.evidence
                    expectedVersion += 1
                }
            }
        }
        check(root.version.valueForPersistence() == expectedVersion - 1)
        return VersionedMarketplaceIndependentEconomicEvidence(evidence, root.version)
    }

    private fun storedUpdate(
        connection: Connection,
        subject: MarketplaceEconomicEvidenceSubject,
        version: Long,
        updateId: UUID,
        changeKind: String
    ): MarketplaceIndependentEconomicEvidenceUpdate = when (changeKind) {
        "FACT" -> MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(
            storedFact(connection, subject, version, updateId)
        )
        "ATTEMPT" -> MarketplaceIndependentEconomicEvidenceUpdate.RecordAttempt(
            storedAttempt(connection, subject, version, updateId)
        )
        "CORRECTION" -> MarketplaceIndependentEconomicEvidenceUpdate.Correct(
            storedCorrection(connection, subject, version, updateId)
        )
        else -> error("Unsupported stored economic evidence change kind")
    }

    private fun storedFact(
        connection: Connection,
        subject: MarketplaceEconomicEvidenceSubject,
        version: Long,
        factId: UUID
    ): MarketplaceIndependentEconomicFact {
        val factKind = connection.prepareStatement(
            "SELECT fact_kind FROM marketplace_economic_evidence_fact " +
                "WHERE organization_id=? AND marketplace_order_id=? " +
                "AND fact_id=? AND evidence_version=?"
        ).use { statement ->
            statement.setObject(1, subject.organizationId.value)
            statement.setObject(2, subject.orderId.value)
            statement.setObject(3, factId)
            statement.setLong(4, version)
            statement.executeQuery().use { result ->
                check(result.next())
                result.getString("fact_kind")
            }
        }
        return when (factKind) {
            "COMPONENT" -> storedComponentFact(connection, subject, version, factId)
            "EXTERNAL_IDENTITY" -> storedExternalIdentityFact(connection, subject, version, factId)
            else -> error("Unsupported stored economic evidence fact kind")
        }
    }

    private fun storedComponentFact(
        connection: Connection,
        subject: MarketplaceEconomicEvidenceSubject,
        version: Long,
        factId: UUID
    ): MarketplaceIndependentEconomicFact.Component = connection.prepareStatement(
        "SELECT fact.family,fact.observed_at,component.* " +
            "FROM marketplace_economic_evidence_fact fact " +
            "JOIN marketplace_economic_evidence_component_fact component " +
            "ON component.organization_id=fact.organization_id " +
            "AND component.marketplace_order_id=fact.marketplace_order_id " +
            "AND component.fact_id=fact.fact_id " +
            "WHERE fact.organization_id=? AND fact.marketplace_order_id=? " +
            "AND fact.fact_id=? AND fact.evidence_version=?"
    ).use { statement ->
        statement.setObject(1, subject.organizationId.value)
        statement.setObject(2, subject.orderId.value)
        statement.setObject(3, factId)
        statement.setLong(4, version)
        statement.executeQuery().use { result ->
            check(result.next())
            val currency = MarketplaceCurrency(result.getString("currency").trimEnd())
            MarketplaceIndependentEconomicFact.Component(
                MarketplaceEconomicComponentObservation(
                    id = observationId(factId),
                    subject = subject,
                    family = MarketplaceEconomicEvidenceFamily.valueOf(result.getString("family")),
                    component = EconomicComponent(
                        organizationId = subject.organizationId,
                        id = EconomicComponentId(result.getObject("component_id", UUID::class.java)),
                        orderId = subject.orderId,
                        type = EconomicComponentType.valueOf(result.getString("component_type")),
                        direction = EconomicDirection.valueOf(result.getString("direction")),
                        magnitude = MarketplaceMoney.parse(
                            currency,
                            result.getBigDecimal("magnitude").toPlainString()
                        ),
                        source = storedSource(result),
                        occurredAt = result.getTimestamp("occurred_at").toInstant(),
                        quality = EconomicEvidenceQuality.valueOf(result.getString("quality"))
                    ),
                    coverageClaim = EconomicComponentCoverage.valueOf(result.getString("coverage")),
                    observedAt = result.getTimestamp("observed_at").toInstant()
                )
            )
        }
    }

    private fun storedExternalIdentityFact(
        connection: Connection,
        subject: MarketplaceEconomicEvidenceSubject,
        version: Long,
        factId: UUID
    ): MarketplaceIndependentEconomicFact.ExternalIdentity = connection.prepareStatement(
        "SELECT fact.family,fact.observed_at,identity.* " +
            "FROM marketplace_economic_evidence_fact fact " +
            "JOIN marketplace_economic_evidence_external_identity_fact identity " +
            "ON identity.organization_id=fact.organization_id " +
            "AND identity.marketplace_order_id=fact.marketplace_order_id " +
            "AND identity.fact_id=fact.fact_id " +
            "WHERE fact.organization_id=? AND fact.marketplace_order_id=? " +
            "AND fact.fact_id=? AND fact.evidence_version=?"
    ).use { statement ->
        statement.setObject(1, subject.organizationId.value)
        statement.setObject(2, subject.orderId.value)
        statement.setObject(3, factId)
        statement.setLong(4, version)
        statement.executeQuery().use { result ->
            check(result.next())
            MarketplaceIndependentEconomicFact.ExternalIdentity(
                MarketplaceEconomicExternalIdentityObservation(
                    id = observationId(factId),
                    subject = subject,
                    family = MarketplaceEconomicEvidenceFamily.valueOf(result.getString("family")),
                    kind = MarketplaceEconomicExternalIdentityKind.valueOf(
                        result.getString("identity_kind")
                    ),
                    anchorReference = EconomicExternalReference(result.getString("anchor_reference")),
                    linkedSystemKey = EconomicSourceSystemKey(result.getString("linked_system_key")),
                    linkedReference = EconomicExternalReference(result.getString("linked_reference")),
                    source = storedSource(result),
                    occurredAt = result.getTimestamp("occurred_at").toInstant(),
                    observedAt = result.getTimestamp("observed_at").toInstant()
                )
            )
        }
    }

    private fun storedAttempt(
        connection: Connection,
        subject: MarketplaceEconomicEvidenceSubject,
        version: Long,
        attemptId: UUID
    ): MarketplaceEconomicEvidenceCollectionAttempt = connection.prepareStatement(
        "SELECT * FROM marketplace_economic_evidence_collection_attempt " +
            "WHERE organization_id=? AND marketplace_order_id=? " +
            "AND attempt_id=? AND evidence_version=?"
    ).use { statement ->
        statement.setObject(1, subject.organizationId.value)
        statement.setObject(2, subject.orderId.value)
        statement.setObject(3, attemptId)
        statement.setLong(4, version)
        statement.executeQuery().use { result ->
            check(result.next())
            MarketplaceEconomicEvidenceCollectionAttempt(
                observationId(attemptId),
                subject,
                MarketplaceEconomicEvidenceFamily.valueOf(result.getString("family")),
                EconomicSourceSystemKey(result.getString("source_system_key")),
                MarketplaceEconomicEvidenceAttemptOutcome.valueOf(result.getString("outcome")),
                result.getTimestamp("attempted_at").toInstant()
            )
        }
    }

    private fun storedCorrection(
        connection: Connection,
        subject: MarketplaceEconomicEvidenceSubject,
        version: Long,
        correctionId: UUID
    ): MarketplaceEconomicEvidenceCorrection = connection.prepareStatement(
        "SELECT * FROM marketplace_economic_evidence_correction " +
            "WHERE organization_id=? AND marketplace_order_id=? " +
            "AND correction_id=? AND evidence_version=?"
    ).use { statement ->
        statement.setObject(1, subject.organizationId.value)
        statement.setObject(2, subject.orderId.value)
        statement.setObject(3, correctionId)
        statement.setLong(4, version)
        statement.executeQuery().use { result ->
            check(result.next())
            val replacementId = result.getObject("replacement_fact_id", UUID::class.java)
            MarketplaceEconomicEvidenceCorrection(
                id = observationId(correctionId),
                subject = subject,
                replacement = storedFact(connection, subject, version, replacementId),
                supersedesObservationId = observationId(
                    result.getObject("superseded_fact_id", UUID::class.java)
                ),
                reason = MarketplaceEconomicEvidenceCorrectionReason.valueOf(
                    result.getString("reason")
                ),
                observedAt = result.getTimestamp("observed_at").toInstant()
            )
        }
    }

    private fun insertCandidateRoot(
        connection: Connection,
        subject: MarketplaceEconomicEvidenceSubject
    ): Boolean = connection.prepareStatement(
            "INSERT INTO marketplace_economic_evidence_subject " +
                "(organization_id,marketplace_order_id,marketplace_key,external_order_id,currency) " +
                "VALUES (?,?,?,?,?) " +
                "ON CONFLICT DO NOTHING"
    ).use { statement ->
        statement.setObject(1, subject.organizationId.value)
        statement.setObject(2, subject.orderId.value)
        statement.setString(3, subject.marketplace.value)
        statement.setString(4, subject.externalOrderId.value)
        statement.setString(5, subject.currency.code)
        statement.executeUpdate() == 1
    }

    private fun appendUpdate(
        connection: Connection,
        version: MarketplaceEconomicEvidenceVersion,
        update: MarketplaceIndependentEconomicEvidenceUpdate
    ) {
        val updateId = updateId(update)
        val changeKind = when (update) {
            is MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact -> "FACT"
            is MarketplaceIndependentEconomicEvidenceUpdate.RecordAttempt -> "ATTEMPT"
            is MarketplaceIndependentEconomicEvidenceUpdate.Correct -> "CORRECTION"
        }
        connection.prepareStatement(
            "INSERT INTO marketplace_economic_evidence_update " +
                "(organization_id,marketplace_order_id,evidence_version,update_id,change_kind) " +
                "VALUES (?,?,?,?,?) RETURNING change_sequence"
        ).use { statement ->
            statement.setObject(1, update.subject.organizationId.value)
            statement.setObject(2, update.subject.orderId.value)
            statement.setLong(3, version.valueForPersistence())
            statement.setObject(4, updateId.valueForPersistence())
            statement.setString(5, changeKind)
            statement.executeQuery().use { result -> check(result.next() && result.getLong(1) > 0) }
        }
        when (update) {
            is MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact -> {
                insertIdentifier(connection, update.subject, version, update.fact.id, "FACT")
                insertFact(connection, version, update.fact)
            }
            is MarketplaceIndependentEconomicEvidenceUpdate.RecordAttempt -> {
                insertIdentifier(connection, update.subject, version, update.attempt.id, "ATTEMPT")
                insertAttempt(connection, version, update.attempt)
            }
            is MarketplaceIndependentEconomicEvidenceUpdate.Correct -> {
                insertIdentifier(connection, update.subject, version, update.correction.id, "CORRECTION")
                insertIdentifier(
                    connection,
                    update.subject,
                    version,
                    update.correction.replacement.id,
                    "FACT"
                )
                insertFact(connection, version, update.correction.replacement)
                insertCorrection(connection, version, update.correction)
            }
        }
    }

    private fun insertIdentifier(
        connection: Connection,
        subject: MarketplaceEconomicEvidenceSubject,
        version: MarketplaceEconomicEvidenceVersion,
        id: MarketplaceEconomicEvidenceObservationId,
        kind: String
    ) {
        connection.prepareStatement(
            "INSERT INTO marketplace_economic_evidence_identifier " +
                "(organization_id,marketplace_order_id,observation_id,evidence_version,identifier_kind) " +
                "VALUES (?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, subject.organizationId.value)
            statement.setObject(2, subject.orderId.value)
            statement.setObject(3, id.valueForPersistence())
            statement.setLong(4, version.valueForPersistence())
            statement.setString(5, kind)
            check(statement.executeUpdate() == 1)
        }
    }

    private fun insertFact(
        connection: Connection,
        version: MarketplaceEconomicEvidenceVersion,
        fact: MarketplaceIndependentEconomicFact
    ) {
        val kind = when (fact) {
            is MarketplaceIndependentEconomicFact.Component -> "COMPONENT"
            is MarketplaceIndependentEconomicFact.ExternalIdentity -> "EXTERNAL_IDENTITY"
        }
        connection.prepareStatement(
            "INSERT INTO marketplace_economic_evidence_fact " +
                "(organization_id,marketplace_order_id,fact_id,evidence_version,fact_kind,family,observed_at) " +
                "VALUES (?,?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, fact.subject.organizationId.value)
            statement.setObject(2, fact.subject.orderId.value)
            statement.setObject(3, fact.id.valueForPersistence())
            statement.setLong(4, version.valueForPersistence())
            statement.setString(5, kind)
            statement.setString(6, fact.family.name)
            statement.setTimestamp(7, Timestamp.from(fact.observedAt))
            check(statement.executeUpdate() == 1)
        }
        when (fact) {
            is MarketplaceIndependentEconomicFact.Component -> insertComponentFact(connection, version, fact)
            is MarketplaceIndependentEconomicFact.ExternalIdentity ->
                insertExternalIdentityFact(connection, version, fact)
        }
    }

    private fun insertComponentFact(
        connection: Connection,
        version: MarketplaceEconomicEvidenceVersion,
        fact: MarketplaceIndependentEconomicFact.Component
    ) {
        val observation = fact.observation
        val component = observation.component
        connection.prepareStatement(
            "INSERT INTO marketplace_economic_evidence_component_fact " +
                "(organization_id,marketplace_order_id,fact_id,evidence_version,family,component_id," +
                "component_type,direction,magnitude,currency,source_kind,source_system_key," +
                "source_external_reference,source_external_reference_absence_reason," +
                "occurred_at,quality,coverage) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, observation.subject.organizationId.value)
            statement.setObject(2, observation.subject.orderId.value)
            statement.setObject(3, observation.id.valueForPersistence())
            statement.setLong(4, version.valueForPersistence())
            statement.setString(5, observation.family.name)
            statement.setObject(6, component.id.value)
            statement.setString(7, component.type.name)
            statement.setString(8, component.direction.name)
            statement.setBigDecimal(9, component.magnitude.amount)
            statement.setString(10, component.magnitude.currency.code)
            bindSource(statement, 11, component.source)
            statement.setTimestamp(15, Timestamp.from(component.occurredAt))
            statement.setString(16, component.quality.name)
            statement.setString(17, observation.coverageClaim.name)
            check(statement.executeUpdate() == 1)
        }
    }

    private fun insertExternalIdentityFact(
        connection: Connection,
        version: MarketplaceEconomicEvidenceVersion,
        fact: MarketplaceIndependentEconomicFact.ExternalIdentity
    ) {
        val observation = fact.observation
        connection.prepareStatement(
            "INSERT INTO marketplace_economic_evidence_external_identity_fact " +
                "(organization_id,marketplace_order_id,fact_id,evidence_version,family,identity_kind," +
                "anchor_reference,linked_system_key,linked_reference,source_kind,source_system_key," +
                "source_external_reference,source_external_reference_absence_reason,occurred_at) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, observation.subject.organizationId.value)
            statement.setObject(2, observation.subject.orderId.value)
            statement.setObject(3, observation.id.valueForPersistence())
            statement.setLong(4, version.valueForPersistence())
            statement.setString(5, observation.family.name)
            statement.setString(6, observation.kind.name)
            statement.setString(7, observation.anchorReference.value)
            statement.setString(8, observation.linkedSystemKey.value)
            statement.setString(9, observation.linkedReference.value)
            bindSource(statement, 10, observation.source)
            statement.setTimestamp(14, Timestamp.from(observation.occurredAt))
            check(statement.executeUpdate() == 1)
        }
    }

    private fun insertAttempt(
        connection: Connection,
        version: MarketplaceEconomicEvidenceVersion,
        attempt: MarketplaceEconomicEvidenceCollectionAttempt
    ) {
        connection.prepareStatement(
            "INSERT INTO marketplace_economic_evidence_collection_attempt " +
                "(organization_id,marketplace_order_id,attempt_id,evidence_version," +
                "family,source_system_key,outcome,attempted_at) VALUES (?,?,?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, attempt.subject.organizationId.value)
            statement.setObject(2, attempt.subject.orderId.value)
            statement.setObject(3, attempt.id.valueForPersistence())
            statement.setLong(4, version.valueForPersistence())
            statement.setString(5, attempt.family.name)
            statement.setString(6, attempt.sourceSystemKey.value)
            statement.setString(7, attempt.outcome.name)
            statement.setTimestamp(8, Timestamp.from(attempt.attemptedAt))
            check(statement.executeUpdate() == 1)
        }
    }

    private fun insertCorrection(
        connection: Connection,
        version: MarketplaceEconomicEvidenceVersion,
        correction: MarketplaceEconomicEvidenceCorrection
    ) {
        connection.prepareStatement(
            "INSERT INTO marketplace_economic_evidence_correction " +
                "(organization_id,marketplace_order_id,correction_id,evidence_version," +
                "superseded_fact_id,replacement_fact_id,reason,observed_at) " +
                "VALUES (?,?,?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, correction.subject.organizationId.value)
            statement.setObject(2, correction.subject.orderId.value)
            statement.setObject(3, correction.id.valueForPersistence())
            statement.setLong(4, version.valueForPersistence())
            statement.setObject(5, correction.supersedesObservationId.valueForPersistence())
            statement.setObject(6, correction.replacement.id.valueForPersistence())
            statement.setString(7, correction.reason.name)
            statement.setTimestamp(8, Timestamp.from(correction.observedAt))
            check(statement.executeUpdate() == 1)
        }
    }

    private fun advanceRoot(
        connection: Connection,
        root: StoredRoot,
        nextVersion: MarketplaceEconomicEvidenceVersion
    ) {
        connection.prepareStatement(
            "UPDATE marketplace_economic_evidence_subject SET current_version=? " +
                "WHERE organization_id=? AND marketplace_order_id=? AND current_version=?"
        ).use { statement ->
            statement.setLong(1, nextVersion.valueForPersistence())
            statement.setObject(2, root.subject.organizationId.value)
            statement.setObject(3, root.subject.orderId.value)
            statement.setLong(4, root.version.valueForPersistence())
            check(statement.executeUpdate() == 1)
        }
    }

    private fun root(
        connection: Connection,
        organizationId: OrganizationId,
        orderId: MarketplaceOrderId
    ): StoredRoot? = selectRoot(connection, organizationId, orderId, forUpdate = false)

    private fun lockRoot(
        connection: Connection,
        organizationId: OrganizationId,
        orderId: MarketplaceOrderId
    ): StoredRoot? = selectRoot(connection, organizationId, orderId, forUpdate = true)

    private fun selectRoot(
        connection: Connection,
        organizationId: OrganizationId,
        orderId: MarketplaceOrderId,
        forUpdate: Boolean
    ): StoredRoot? = connection.prepareStatement(
        "SELECT * FROM marketplace_economic_evidence_subject " +
            "WHERE organization_id=? AND marketplace_order_id=?" +
            if (forUpdate) " FOR UPDATE" else ""
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.setObject(2, orderId.value)
        statement.executeQuery().use { result -> if (result.next()) StoredRoot(result) else null }
    }

    private fun lockActiveOrganization(
        connection: Connection,
        organizationId: OrganizationId
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_organization " +
            "WHERE organization_id=? AND status='ACTIVE' FOR UPDATE"
    ).use { statement ->
        statement.setObject(1, organizationId.value)
        statement.executeQuery().use(ResultSet::next)
    }

    private fun classifyFailure(
        update: MarketplaceIndependentEconomicEvidenceUpdate
    ): MarketplaceIndependentEconomicEvidencePersistResult = try {
        connection().use { connection ->
            if (!organizationActive(connection, update.subject.organizationId)) {
                MarketplaceIndependentEconomicEvidencePersistResult.OrganizationUnavailable
            } else {
                MarketplaceIndependentEconomicEvidencePersistResult.IntegrityFailure
            }
        }
    } catch (_: Exception) {
        MarketplaceIndependentEconomicEvidencePersistResult.IntegrityFailure
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

    private fun connection(): Connection = DriverManager.getConnection(
        configuration.url,
        configuration.user,
        configuration.password
    )

    private data class StoredRoot(
        val subject: MarketplaceEconomicEvidenceSubject,
        val version: MarketplaceEconomicEvidenceVersion
    ) {
        constructor(result: ResultSet) : this(
            MarketplaceEconomicEvidenceSubject(
                OrganizationId(result.getObject("organization_id", UUID::class.java)),
                MarketplaceOrderId(result.getObject("marketplace_order_id", UUID::class.java)),
                MarketplaceKey(result.getString("marketplace_key")),
                MarketplaceExternalOrderId(result.getString("external_order_id")),
                MarketplaceCurrency(result.getString("currency").trimEnd())
            ),
            MarketplaceEconomicEvidenceVersion(result.getLong("current_version"))
        )

        fun matches(other: MarketplaceEconomicEvidenceSubject): Boolean = subject == other
    }

    private data class TransactionOutcome(
        val result: MarketplaceIndependentEconomicEvidencePersistResult,
        val rollback: Boolean
    )

    private fun outcome(
        result: MarketplaceIndependentEconomicEvidencePersistResult,
        candidateCreated: Boolean
    ): TransactionOutcome = TransactionOutcome(result, rollback = candidateCreated)

    private fun rollback(
        result: MarketplaceIndependentEconomicEvidencePersistResult
    ): TransactionOutcome = TransactionOutcome(result, rollback = true)

    private fun commit(
        result: MarketplaceIndependentEconomicEvidencePersistResult
    ): TransactionOutcome = TransactionOutcome(result, rollback = false)
}

private fun mapConflict(
    result: MarketplaceIndependentEconomicEvidenceResult
): MarketplaceIndependentEconomicEvidencePersistResult? = when (result) {
    MarketplaceIndependentEconomicEvidenceResult.SubjectMismatch ->
        MarketplaceIndependentEconomicEvidencePersistResult.SubjectMismatch
    MarketplaceIndependentEconomicEvidenceResult.IdentifierConflict ->
        MarketplaceIndependentEconomicEvidencePersistResult.IdentifierConflict
    MarketplaceIndependentEconomicEvidenceResult.SourceFactConflict ->
        MarketplaceIndependentEconomicEvidencePersistResult.SourceFactConflict
    MarketplaceIndependentEconomicEvidenceResult.SupersededFactNotFound ->
        MarketplaceIndependentEconomicEvidencePersistResult.SupersededFactNotFound
    MarketplaceIndependentEconomicEvidenceResult.SupersededTargetNotFact ->
        MarketplaceIndependentEconomicEvidencePersistResult.SupersededTargetNotFact
    MarketplaceIndependentEconomicEvidenceResult.FactAlreadySuperseded ->
        MarketplaceIndependentEconomicEvidencePersistResult.FactAlreadySuperseded
    MarketplaceIndependentEconomicEvidenceResult.ReplacementIdentifierConflict ->
        MarketplaceIndependentEconomicEvidencePersistResult.ReplacementIdentifierConflict
    MarketplaceIndependentEconomicEvidenceResult.ReplacementSourceFactConflict ->
        MarketplaceIndependentEconomicEvidencePersistResult.ReplacementSourceFactConflict
    is MarketplaceIndependentEconomicEvidenceResult.Applied,
    is MarketplaceIndependentEconomicEvidenceResult.Duplicate -> null
}

private fun updateId(
    update: MarketplaceIndependentEconomicEvidenceUpdate
): MarketplaceEconomicEvidenceObservationId = when (update) {
    is MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact -> update.fact.id
    is MarketplaceIndependentEconomicEvidenceUpdate.RecordAttempt -> update.attempt.id
    is MarketplaceIndependentEconomicEvidenceUpdate.Correct -> update.correction.id
}

private fun observationId(value: UUID): MarketplaceEconomicEvidenceObservationId =
    MarketplaceEconomicEvidenceObservationId.parse(value.toString())

private fun storedSource(result: ResultSet): EconomicSource {
    val reference = result.getString("source_external_reference")
    val absence = result.getString("source_external_reference_absence_reason")
    val state = if (reference != null) {
        EconomicExternalReferenceState.Present(EconomicExternalReference(reference))
    } else {
        EconomicExternalReferenceState.Absent(
            EconomicExternalReferenceAbsenceReason.valueOf(requireNotNull(absence))
        )
    }
    return EconomicSource(
        EconomicSourceKind.valueOf(result.getString("source_kind")),
        EconomicSourceSystemKey(result.getString("source_system_key")),
        state
    )
}

private fun bindSource(
    statement: java.sql.PreparedStatement,
    firstIndex: Int,
    source: EconomicSource
) {
    val present = source.externalReference as? EconomicExternalReferenceState.Present
    val absent = source.externalReference as? EconomicExternalReferenceState.Absent
    statement.setString(firstIndex, source.kind.name)
    statement.setString(firstIndex + 1, source.systemKey.value)
    statement.setString(firstIndex + 2, present?.reference?.value)
    statement.setString(firstIndex + 3, absent?.reason?.name)
}

private fun Throwable.hasRetryableSqlState(): Boolean {
    var currentCause: Throwable? = this
    while (currentCause != null) {
        if (currentCause is SQLException) {
            var currentSqlException: SQLException? = currentCause
            while (currentSqlException != null) {
                if (currentSqlException.sqlState in RETRYABLE_TRANSACTION_SQL_STATES) {
                    return true
                }
                currentSqlException = currentSqlException.nextException
            }
        }
        currentCause = currentCause.cause
    }
    return false
}
