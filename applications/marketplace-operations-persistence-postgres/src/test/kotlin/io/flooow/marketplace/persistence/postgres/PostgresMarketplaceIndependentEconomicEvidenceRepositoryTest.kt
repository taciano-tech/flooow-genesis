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
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicOrderOccurrenceObservation
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceSubject
import io.flooow.marketplace.operations.economics.evidence.MarketplaceEconomicEvidenceVersion
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceMerger
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidencePersistResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceReadResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceResult
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicEvidenceUpdate
import io.flooow.marketplace.operations.economics.evidence.MarketplaceIndependentEconomicFact
import io.flooow.organization.OrganizationId
import java.math.BigDecimal
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer

class PostgresMarketplaceIndependentEconomicEvidenceRepositoryTest {
    private lateinit var postgres: PostgreSQLContainer
    private lateinit var configuration: PostgresConfiguration
    private lateinit var repository: PostgresMarketplaceIndependentEconomicEvidenceRepository
    private val identifiers = AtomicLong(1)
    private val baseTime = Instant.parse("2026-09-01T12:00:00.123456Z")

    @BeforeTest
    fun startPostgres() {
        postgres = PostgreSQLContainer("postgres:18.4")
        postgres.start()
        configuration = PostgresConfiguration(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure()
            .dataSource(configuration.url, configuration.user, configuration.password)
            .load()
            .migrate()
        repository = PostgresMarketplaceIndependentEconomicEvidenceRepository(configuration)
    }

    @AfterTest
    fun stopPostgres() = postgres.stop()

    @Test
    fun `V015 migrates complete structural boundary`() {
        assertEquals(
            "015",
            queryOne("SELECT version FROM flyway_schema_history WHERE version='015' AND success")
        )
        val actualTables = queryStrings(
            "SELECT table_name FROM information_schema.tables " +
                "WHERE table_schema='public' AND table_name LIKE 'marketplace_economic_evidence_%'"
        ).toSet()
        assertTrue(actualTables.containsAll(EXPECTED_TABLES))
        assertEquals(
            listOf("bigint", "false"),
            queryStrings(
                "SELECT format_type(seqtypid,NULL), seqcycle::text FROM pg_sequence " +
                    "WHERE seqrelid='marketplace_economic_evidence_change_sequence'::regclass"
            )
        )
        assertEquals(
            listOf("numeric:24:6"),
            queryStrings(
                "SELECT data_type || ':' || numeric_precision || ':' || numeric_scale " +
                    "FROM information_schema.columns " +
                    "WHERE table_name='marketplace_economic_evidence_component_fact' " +
                    "AND column_name='magnitude'"
            )
        )
        assertTrue(
            queryStrings(
                "SELECT indexdef FROM pg_indexes " +
                    "WHERE tablename='marketplace_economic_evidence_update'"
            ).any { index ->
                "organization_id, change_sequence" in index && "UNIQUE" in index
            }
        )
        assertEquals(
            APPEND_ONLY_TABLES,
            queryStrings(
                "SELECT event_object_table FROM information_schema.triggers " +
                    "WHERE trigger_name LIKE 'protect_marketplace_economic_%_mutation' " +
                    "GROUP BY event_object_table ORDER BY event_object_table"
            ).toSet()
        )
    }

    @Test
    fun `V017 migrates durable order occurrence structural boundary`() {
        assertEquals(
            "017",
            queryOne("SELECT version FROM flyway_schema_history WHERE version='017' AND success")
        )
        assertEquals(
            listOf("timestamp with time zone:6"),
            queryStrings(
                "SELECT data_type || ':' || datetime_precision " +
                    "FROM information_schema.columns " +
                    "WHERE table_name='marketplace_economic_evidence_order_occurrence_fact' " +
                    "AND column_name='occurred_at'"
            )
        )
        assertTrue(
            queryStrings(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint " +
                    "WHERE conrelid='marketplace_economic_evidence_fact'::regclass " +
                    "AND contype='c'"
            ).any { definition -> "ORDER_OCCURRENCE" in definition }
        )
        assertTrue(
            queryStrings(
                "SELECT event_object_table FROM information_schema.triggers " +
                    "WHERE trigger_name='protect_marketplace_economic_order_occurrence_fact_mutation'"
            ).contains("marketplace_economic_evidence_order_occurrence_fact")
        )
    }
    @Test
    fun `subject root is organization scoped immutable versioned and undeletable`() {
        val organization = createOrganization()
        val otherOrganization = createOrganization()
        val order = uuid()
        createSubject(organization, order)
        createSubject(otherOrganization, order, externalOrder = "other-order")

        assertEquals(2, countSubjects(order))
        assertFailsWith<SQLException> {
            createSubject(uuid(), uuid())
        }
        assertFailsWith<SQLException> {
            execute(
                "INSERT INTO marketplace_economic_evidence_subject " +
                    "(organization_id,marketplace_order_id,marketplace_key,external_order_id,currency,current_version) " +
                    "VALUES (?,?,?,?,?,?)",
                organization,
                uuid(),
                "mercado-livre",
                "invalid-version",
                "BRL",
                -1L
            )
        }
        assertFailsWith<SQLException> {
            execute(
                "UPDATE marketplace_economic_evidence_subject SET marketplace_key='amazon' " +
                    "WHERE organization_id=? AND marketplace_order_id=?",
                organization,
                order
            )
        }
        assertFailsWith<SQLException> {
            execute(
                "UPDATE marketplace_economic_evidence_subject SET current_version=2 " +
                    "WHERE organization_id=? AND marketplace_order_id=?",
                organization,
                order
            )
        }

        appendComponentFact(Subject(organization, order), version = 1)
        assertEquals("1", subjectVersion(organization, order))
        assertFailsWith<SQLException> {
            execute(
                "UPDATE marketplace_economic_evidence_subject SET current_version=0 " +
                    "WHERE organization_id=? AND marketplace_order_id=?",
                organization,
                order
            )
        }
        assertFailsWith<SQLException> {
            execute(
                "DELETE FROM marketplace_economic_evidence_subject " +
                    "WHERE organization_id=? AND marketplace_order_id=?",
                organization,
                order
            )
        }
    }

    @Test
    fun `journal identifiers and history enforce uniqueness shape and append only`() {
        val subject = Subject(createOrganization(), uuid())
        createSubject(subject.organizationId, subject.orderId)
        val first = appendComponentFact(subject, version = 1)

        assertFailsWith<SQLException> {
            execute(
                "INSERT INTO marketplace_economic_evidence_update " +
                    "(organization_id,marketplace_order_id,evidence_version,update_id,change_kind) " +
                    "VALUES (?,?,?,?,?)",
                subject.organizationId,
                subject.orderId,
                1L,
                uuid(),
                "FACT"
            )
        }
        assertFailsWith<SQLException> {
            execute(
                "INSERT INTO marketplace_economic_evidence_update " +
                    "(organization_id,marketplace_order_id,evidence_version,update_id,change_kind) " +
                    "VALUES (?,?,?,?,?)",
                subject.organizationId,
                subject.orderId,
                2L,
                first.updateId,
                "FACT"
            )
        }
        assertFailsWith<SQLException> {
            execute(
                "INSERT INTO marketplace_economic_evidence_update " +
                    "(organization_id,marketplace_order_id,evidence_version,update_id,change_kind) " +
                    "VALUES (?,?,?,?,?)",
                subject.organizationId,
                subject.orderId,
                2L,
                uuid(),
                "UNKNOWN"
            )
        }
        assertFailsWith<SQLException> {
            execute(
                "INSERT INTO marketplace_economic_evidence_update " +
                    "(organization_id,marketplace_order_id,evidence_version,update_id,change_kind,change_sequence) " +
                    "VALUES (?,?,?,?,?,?)",
                subject.organizationId,
                subject.orderId,
                2L,
                uuid(),
                "FACT",
                first.changeSequence
            )
        }
        assertFailsWith<SQLException> {
            execute(
                "INSERT INTO marketplace_economic_evidence_identifier " +
                    "(organization_id,marketplace_order_id,observation_id,evidence_version,identifier_kind) " +
                    "VALUES (?,?,?,?,?)",
                subject.organizationId,
                subject.orderId,
                first.updateId,
                1L,
                "ATTEMPT"
            )
        }

        assertFailsWith<SQLException> {
            execute(
                "UPDATE marketplace_economic_evidence_update SET change_kind='ATTEMPT' " +
                    "WHERE organization_id=? AND marketplace_order_id=?",
                subject.organizationId,
                subject.orderId
            )
        }
        assertFailsWith<SQLException> {
            execute(
                "DELETE FROM marketplace_economic_evidence_component_fact " +
                    "WHERE organization_id=? AND marketplace_order_id=?",
                subject.organizationId,
                subject.orderId
            )
        }
        assertFailsWith<SQLException> {
            execute(
                "DELETE FROM marketplace_economic_evidence_update " +
                    "WHERE organization_id=? AND marketplace_order_id=?",
                subject.organizationId,
                subject.orderId
            )
        }
        assertEquals(1, count("marketplace_economic_evidence_update", subject))
        assertEquals(1, count("marketplace_economic_evidence_component_fact", subject))
    }

    @Test
    fun `component decimal and economic timestamps preserve exact precision`() {
        val subject = Subject(createOrganization(), uuid())
        createSubject(subject.organizationId, subject.orderId)
        val amount = BigDecimal("123456789012345678.123456")
        val applied = appendComponentFact(subject, 1, amount = amount, observedAt = baseTime)

        connection().use { connection ->
            connection.prepareStatement(
                "SELECT magnitude,occurred_at,observed_at FROM " +
                    "marketplace_economic_evidence_component_fact component " +
                    "JOIN marketplace_economic_evidence_fact fact USING " +
                    "(organization_id,marketplace_order_id,fact_id,evidence_version) " +
                    "WHERE component.organization_id=? AND component.marketplace_order_id=?"
            ).use { statement ->
                statement.setObject(1, subject.organizationId)
                statement.setObject(2, subject.orderId)
                statement.executeQuery().use { result ->
                    assertTrue(result.next())
                    assertEquals(amount, result.getBigDecimal("magnitude"))
                    assertEquals(baseTime, result.getTimestamp("occurred_at").toInstant())
                    assertEquals(baseTime, result.getTimestamp("observed_at").toInstant())
                }
            }
        }
        val committedAt = queryInstant(
            "SELECT committed_at FROM marketplace_economic_evidence_update " +
                "WHERE organization_id='${subject.organizationId}'::uuid " +
                "AND marketplace_order_id='${subject.orderId}'::uuid"
        )
        assertNotEquals(Instant.parse("2000-01-01T00:00:00Z"), committedAt)
        assertEquals(0, committedAt.nano % 1_000)
        assertTrue(applied.changeSequence > 0)
    }

    @Test
    fun `correction links remain inside organization aggregate and version`() {
        val first = Subject(createOrganization(), uuid())
        val crossOrganization = Subject(createOrganization(), uuid())
        val crossAggregate = Subject(first.organizationId, uuid())
        createSubject(first.organizationId, first.orderId)
        createSubject(crossOrganization.organizationId, crossOrganization.orderId)
        createSubject(crossAggregate.organizationId, crossAggregate.orderId)
        val original = appendComponentFact(first, 1)
        val foreignOrganizationFact = appendComponentFact(crossOrganization, 1)
        val foreignAggregateFact = appendComponentFact(crossAggregate, 1)

        assertFailsWith<SQLException> {
            insertCorrectionRow(
                first,
                evidenceVersion = 2,
                correctionId = uuid(),
                supersededFactId = original.updateId,
                replacementFactId = foreignOrganizationFact.updateId
            )
        }
        assertFailsWith<SQLException> {
            insertCorrectionRow(
                first,
                evidenceVersion = 2,
                correctionId = uuid(),
                supersededFactId = original.updateId,
                replacementFactId = foreignAggregateFact.updateId
            )
        }

        val correction = appendCorrection(first, 2, original.updateId)
        assertEquals("2", subjectVersion(first.organizationId, first.orderId))
        assertTrue(correction.replacementFactId != original.updateId)
        assertFailsWith<SQLException> {
            appendCorrection(first, 3, original.updateId)
        }
    }

    @Test
    fun `change sequence is non cycling rollback safe gapped and checkpoint indexed`() {
        val rolledBack = connection().use { connection ->
            connection.autoCommit = false
            val value = connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT nextval('marketplace_economic_evidence_change_sequence')"
                ).use { result -> result.next(); result.getLong(1) }
            }
            connection.rollback()
            value
        }
        val next = queryLong("SELECT nextval('marketplace_economic_evidence_change_sequence')")
        assertTrue(next > rolledBack)
        assertNotEquals(rolledBack, next)

        val organization = createOrganization()
        val first = Subject(organization, uuid())
        val second = Subject(organization, uuid())
        createSubject(organization, first.orderId, externalOrder = "first")
        createSubject(organization, second.orderId, externalOrder = "second")
        val firstApplied = appendComponentFact(first, 1)
        val secondApplied = appendComponentFact(second, 1)
        assertTrue(secondApplied.changeSequence > firstApplied.changeSequence)
        assertEquals(
            listOf(firstApplied.changeSequence, secondApplied.changeSequence),
            checkpoint(organization, firstApplied.changeSequence - 1)
        )
        assertEquals(
            listOf(secondApplied.changeSequence),
            checkpoint(organization, firstApplied.changeSequence)
        )
        assertTrue(
            explainCheckpointQuery(organization).any { line ->
                "Index" in line && "marketplace_economic_evidence_update" in line
            }
        )
    }

    @Test
    fun `adapter applies facts attempts and corrections with durable versions and sequences`() {
        val factSubject = domainSubject()
        val attemptSubject = domainSubject()
        val correctionSubject = domainSubject()
        createOrganization(factSubject.organizationId.value)
        createOrganization(attemptSubject.organizationId.value)
        createOrganization(correctionSubject.organizationId.value)

        val factUpdate = factUpdate(factSubject)
        val factApplied = assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
            repository.apply(MarketplaceEconomicEvidenceVersion.ZERO, factUpdate)
        )
        assertEquals(1L, factApplied.versionedEvidence.version.valueForPersistence())

        val attemptApplied = assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
            repository.apply(MarketplaceEconomicEvidenceVersion.ZERO, attemptUpdate(attemptSubject))
        )
        assertEquals(1L, attemptApplied.versionedEvidence.version.valueForPersistence())

        val original = factUpdate(correctionSubject)
        assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
            repository.apply(MarketplaceEconomicEvidenceVersion.ZERO, original)
        )
        val correctionApplied = assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
            repository.apply(
                MarketplaceEconomicEvidenceVersion(1),
                correctionUpdate(correctionSubject, original.fact)
            )
        )
        assertEquals(2L, correctionApplied.versionedEvidence.version.valueForPersistence())
        assertEquals(4, totalJournalRows())
        assertEquals(4, distinctChangeSequences())
    }

    @Test
    fun `order occurrence round trips across repository restart preserving exact timestamps`() {
        val subject = domainSubject()
        createOrganization(subject.organizationId.value)
        val occurredAt = Instant.parse("2026-09-01T12:00:01.123456Z")
        val observedAt = Instant.parse("2026-09-01T12:00:02.654321Z")
        val update = orderOccurrenceUpdate(
            subject,
            sourceReference = "order-occurrence-round-trip",
            occurredAt = occurredAt,
            observedAt = observedAt
        )

        val applied = assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
            repository.apply(MarketplaceEconomicEvidenceVersion.ZERO, update)
        )
        assertEquals(1L, applied.versionedEvidence.version.valueForPersistence())
        assertEquals(1, journalRows(subject))
        assertEquals(1, orderOccurrenceRows(subject))

        val restarted = PostgresMarketplaceIndependentEconomicEvidenceRepository(configuration)
        val found = assertIs<MarketplaceIndependentEconomicEvidenceReadResult.Found>(
            restarted.find(subject)
        )
        assertEquals(applied.versionedEvidence, found.versionedEvidence)

        connection().use { connection ->
            connection.prepareStatement(
                "SELECT occurrence.occurred_at,fact.observed_at " +
                    "FROM marketplace_economic_evidence_order_occurrence_fact occurrence " +
                    "JOIN marketplace_economic_evidence_fact fact USING " +
                    "(organization_id,marketplace_order_id,fact_id,evidence_version,fact_kind,family) " +
                    "WHERE occurrence.organization_id=? AND occurrence.marketplace_order_id=?"
            ).use { statement ->
                statement.setObject(1, subject.organizationId.value)
                statement.setObject(2, subject.orderId.value)
                statement.executeQuery().use { result ->
                    assertTrue(result.next())
                    assertEquals(occurredAt, result.getTimestamp("occurred_at").toInstant())
                    assertEquals(observedAt, result.getTimestamp("observed_at").toInstant())
                }
            }
        }
    }

    @Test
    fun `order occurrence supports every canonical source shape after restart`() {
        val cases = listOf(
            Triple(EconomicSourceKind.MARKETPLACE, "meli-br", "marketplace-order-1"),
            Triple(EconomicSourceKind.ERP, "omie-br", "erp-order-1"),
            Triple(EconomicSourceKind.MANUAL, "manual-ops", null),
            Triple(EconomicSourceKind.CALCULATED, "economic-engine", null)
        )

        cases.forEachIndexed { index, (kind, systemKey, externalReference) ->
            val subject = domainSubject()
            createOrganization(subject.organizationId.value)
            val update = orderOccurrenceUpdate(
                subject = subject,
                sourceKind = kind,
                sourceSystemKey = systemKey,
                sourceReference = externalReference,
                occurredAt = baseTime.plusSeconds(10L + index),
                observedAt = baseTime.plusSeconds(20L + index)
            )

            assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
                repository.apply(MarketplaceEconomicEvidenceVersion.ZERO, update)
            )

            val found = assertIs<MarketplaceIndependentEconomicEvidenceReadResult.Found>(
                PostgresMarketplaceIndependentEconomicEvidenceRepository(configuration).find(subject)
            )
            assertEquals(1, found.versionedEvidence.evidence.activeFacts.size)
            assertEquals(update.fact, found.versionedEvidence.evidence.activeFacts.single())
        }
    }

    @Test
    fun `order occurrence duplicate and source fact conflict remain deterministic after restart`() {
        val subject = domainSubject()
        createOrganization(subject.organizationId.value)
        val update = orderOccurrenceUpdate(
            subject,
            sourceReference = "stable-order-occurrence-key",
            occurredAt = baseTime.plusSeconds(1),
            observedAt = baseTime.plusSeconds(2)
        )
        assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
            repository.apply(MarketplaceEconomicEvidenceVersion.ZERO, update)
        )
        val sequenceBefore = maximumChangeSequence(subject.organizationId.value)

        val restarted = PostgresMarketplaceIndependentEconomicEvidenceRepository(configuration)
        assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Duplicate>(
            restarted.apply(MarketplaceEconomicEvidenceVersion.ZERO, update)
        )
        assertEquals(sequenceBefore, maximumChangeSequence(subject.organizationId.value))

        val conflict = orderOccurrenceUpdate(
            subject = subject,
            sourceReference = "stable-order-occurrence-key",
            occurredAt = baseTime.plusSeconds(9),
            observedAt = baseTime.plusSeconds(10)
        )
        assertIs<MarketplaceIndependentEconomicEvidencePersistResult.SourceFactConflict>(
            restarted.apply(MarketplaceEconomicEvidenceVersion(1), conflict)
        )
        assertEquals(1, journalRows(subject))
        assertEquals(1, orderOccurrenceRows(subject))
        assertEquals(sequenceBefore, maximumChangeSequence(subject.organizationId.value))
    }

    @Test
    fun `order occurrence correction survives restart and preserves historical fact`() {
        val subject = domainSubject()
        createOrganization(subject.organizationId.value)
        val original = orderOccurrenceUpdate(
            subject,
            sourceReference = "order-occurrence-corrected",
            occurredAt = baseTime.plusSeconds(1),
            observedAt = baseTime.plusSeconds(2)
        )
        assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
            repository.apply(MarketplaceEconomicEvidenceVersion.ZERO, original)
        )

        val restarted = PostgresMarketplaceIndependentEconomicEvidenceRepository(configuration)
        val correction = orderOccurrenceCorrectionUpdate(
            subject = subject,
            original = original.fact,
            occurredAt = baseTime.plusSeconds(3),
            observedAt = baseTime.plusSeconds(4)
        )
        val corrected = assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
            restarted.apply(MarketplaceEconomicEvidenceVersion(1), correction)
        )
        assertEquals(2L, corrected.versionedEvidence.version.valueForPersistence())
        assertEquals(2, orderOccurrenceRows(subject))
        assertEquals(1, evidenceCorrectionRows(subject))

        val afterSecondRestart = assertIs<MarketplaceIndependentEconomicEvidenceReadResult.Found>(
            PostgresMarketplaceIndependentEconomicEvidenceRepository(configuration).find(subject)
        )
        assertEquals(2, afterSecondRestart.versionedEvidence.evidence.historicalFacts.size)
        assertEquals(1, afterSecondRestart.versionedEvidence.evidence.activeFacts.size)
        assertEquals(
            correction.correction.replacement,
            afterSecondRestart.versionedEvidence.evidence.activeFacts.single()
        )
    }

    @Test
    fun `malformed durable order occurrence fails closed after restart`() {
        val subject = domainSubject()
        createOrganization(subject.organizationId.value)
        assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
            repository.apply(
                MarketplaceEconomicEvidenceVersion.ZERO,
                orderOccurrenceUpdate(subject, sourceReference = "malformed-order-occurrence")
            )
        )

        connection().use { connection ->
            connection.createStatement().use {
                it.execute(
                    "ALTER TABLE marketplace_economic_evidence_order_occurrence_fact DISABLE TRIGGER USER"
                )
            }
            connection.prepareStatement(
                "DELETE FROM marketplace_economic_evidence_order_occurrence_fact " +
                    "WHERE organization_id=? AND marketplace_order_id=?"
            ).use { statement ->
                statement.setObject(1, subject.organizationId.value)
                statement.setObject(2, subject.orderId.value)
                assertEquals(1, statement.executeUpdate())
            }
            connection.createStatement().use {
                it.execute(
                    "ALTER TABLE marketplace_economic_evidence_order_occurrence_fact ENABLE TRIGGER USER"
                )
            }
        }

        assertIs<MarketplaceIndependentEconomicEvidenceReadResult.IntegrityFailure>(
            PostgresMarketplaceIndependentEconomicEvidenceRepository(configuration).find(subject)
        )
    }
    @Test
    fun `duplicate wins over stale expected version without durable mutation`() {
        val subject = domainSubject()
        createOrganization(subject.organizationId.value)
        val update = factUpdate(subject)
        assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
            repository.apply(MarketplaceEconomicEvidenceVersion.ZERO, update)
        )
        val sequenceBefore = maximumChangeSequence(subject.organizationId.value)

        val duplicate = assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Duplicate>(
            repository.apply(MarketplaceEconomicEvidenceVersion.ZERO, update)
        )
        assertEquals(1L, duplicate.versionedEvidence.version.valueForPersistence())
        assertEquals(1, journalRows(subject))
        assertEquals(sequenceBefore, maximumChangeSequence(subject.organizationId.value))
    }

    @Test
    fun `stale and conflict outcomes write neither history nor sequence`() {
        val subject = domainSubject()
        createOrganization(subject.organizationId.value)
        val original = factUpdate(subject)
        assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
            repository.apply(MarketplaceEconomicEvidenceVersion.ZERO, original)
        )
        val sequenceBefore = maximumChangeSequence(subject.organizationId.value)

        assertIs<MarketplaceIndependentEconomicEvidencePersistResult.StaleVersion>(
            repository.apply(
                MarketplaceEconomicEvidenceVersion.ZERO,
                factUpdate(subject, sourceReference = "second-source")
            )
        )
        val conflicting = factUpdate(
            subject,
            observationId = original.fact.id,
            amount = "11",
            sourceReference = "different-payload"
        )
        assertIs<MarketplaceIndependentEconomicEvidencePersistResult.IdentifierConflict>(
            repository.apply(MarketplaceEconomicEvidenceVersion(1), conflicting)
        )
        assertEquals(1, journalRows(subject))
        assertEquals(sequenceBefore, maximumChangeSequence(subject.organizationId.value))
    }

    @Test
    fun `concurrent first writers converge through candidate root lock`() {
        val subject = domainSubject()
        createOrganization(subject.organizationId.value)
        val update = factUpdate(subject)

        val results = concurrent(
            { repository.apply(MarketplaceEconomicEvidenceVersion.ZERO, update) },
            { repository.apply(MarketplaceEconomicEvidenceVersion.ZERO, update) }
        )
        assertEquals(1, results.count { it is MarketplaceIndependentEconomicEvidencePersistResult.Applied })
        assertEquals(1, results.count { it is MarketplaceIndependentEconomicEvidencePersistResult.Duplicate })
        assertEquals(1, journalRows(subject))
        assertEquals("1", subjectVersion(subject.organizationId.value, subject.orderId.value))
    }

    @Test
    fun `rejected first update rolls candidate root back completely`() {
        val subject = domainSubject()
        createOrganization(subject.organizationId.value, status = "SUSPENDED")

        assertIs<MarketplaceIndependentEconomicEvidencePersistResult.OrganizationUnavailable>(
            repository.apply(MarketplaceEconomicEvidenceVersion.ZERO, factUpdate(subject))
        )
        assertEquals(0, subjectRows(subject))
        assertEquals(0, journalRows(subject))
    }

    @Test
    fun `same organization aggregates receive unique sequences in completion order`() {
        val organization = OrganizationId(uuid())
        createOrganization(organization.value)
        val first = domainSubject(organization)
        val second = domainSubject(organization)
        val completionOrder = ConcurrentLinkedQueue<MarketplaceEconomicEvidenceSubject>()

        val results = concurrent(
            {
                repository.apply(MarketplaceEconomicEvidenceVersion.ZERO, factUpdate(first))
                    .also { completionOrder.add(first) }
            },
            {
                repository.apply(MarketplaceEconomicEvidenceVersion.ZERO, factUpdate(second))
                    .also { completionOrder.add(second) }
            }
        )
        assertTrue(results.all { it is MarketplaceIndependentEconomicEvidencePersistResult.Applied })
        val completed = completionOrder.toList()
        assertEquals(2, completed.size)
        val sequences = completed.map(::changeSequence)
        assertEquals(2, sequences.toSet().size)
        assertTrue(sequences[0] < sequences[1])
    }

    @Test
    fun `different organizations advance independently without sequence collision`() {
        val first = domainSubject()
        val second = domainSubject()
        createOrganization(first.organizationId.value)
        createOrganization(second.organizationId.value)

        val results = concurrent(
            { repository.apply(MarketplaceEconomicEvidenceVersion.ZERO, factUpdate(first)) },
            { repository.apply(MarketplaceEconomicEvidenceVersion.ZERO, factUpdate(second)) }
        )
        assertTrue(results.all { it is MarketplaceIndependentEconomicEvidencePersistResult.Applied })
        assertNotEquals(changeSequence(first), changeSequence(second))
        assertEquals(1, journalRows(first))
        assertEquals(1, journalRows(second))
    }

    @Test
    fun `find returns not found and replays fact plus correction deterministically`() {
        val absent = domainSubject()
        assertIs<MarketplaceIndependentEconomicEvidenceReadResult.NotFound>(repository.find(absent))

        val subject = domainSubject()
        createOrganization(subject.organizationId.value)
        val original = factUpdate(subject)
        val firstApplied = assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
            repository.apply(MarketplaceEconomicEvidenceVersion.ZERO, original)
        )
        val correction = correctionUpdate(subject, original.fact)
        val expected = assertIs<MarketplaceIndependentEconomicEvidenceResult.Applied>(
            MarketplaceIndependentEconomicEvidenceMerger.apply(
                firstApplied.versionedEvidence.evidence,
                correction
            )
        ).evidence
        assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
            repository.apply(MarketplaceEconomicEvidenceVersion(1), correction)
        )

        val found = assertIs<MarketplaceIndependentEconomicEvidenceReadResult.Found>(
            PostgresMarketplaceIndependentEconomicEvidenceRepository(configuration).find(subject)
        )
        assertEquals(2L, found.versionedEvidence.version.valueForPersistence())
        assertEquals(expected, found.versionedEvidence.evidence)
        assertEquals(2, found.versionedEvidence.evidence.historicalFacts.size)
        assertEquals(1, found.versionedEvidence.evidence.activeFacts.size)
    }

    @Test
    fun `malformed persisted history fails closed without SQL leakage`() {
        val subject = domainSubject()
        createOrganization(subject.organizationId.value)
        assertIs<MarketplaceIndependentEconomicEvidencePersistResult.Applied>(
            repository.apply(MarketplaceEconomicEvidenceVersion.ZERO, factUpdate(subject))
        )
        connection().use { connection ->
            connection.createStatement().use {
                it.execute("ALTER TABLE marketplace_economic_evidence_component_fact DISABLE TRIGGER USER")
            }
            connection.prepareStatement(
                "DELETE FROM marketplace_economic_evidence_component_fact " +
                    "WHERE organization_id=? AND marketplace_order_id=?"
            ).use { statement ->
                statement.setObject(1, subject.organizationId.value)
                statement.setObject(2, subject.orderId.value)
                assertEquals(1, statement.executeUpdate())
            }
            connection.createStatement().use {
                it.execute("ALTER TABLE marketplace_economic_evidence_component_fact ENABLE TRIGGER USER")
            }
        }

        assertIs<MarketplaceIndependentEconomicEvidenceReadResult.IntegrityFailure>(
            repository.find(subject)
        )
    }

    private fun appendComponentFact(
        subject: Subject,
        version: Long,
        updateId: UUID = uuid(),
        amount: BigDecimal = BigDecimal("10.000000"),
        observedAt: Instant = baseTime.plusSeconds(version)
    ): AppliedFixture = transaction { connection ->
        execute(
            connection,
            "INSERT INTO marketplace_economic_evidence_update " +
                "(organization_id,marketplace_order_id,evidence_version,update_id,change_kind,committed_at) " +
                "VALUES (?,?,?,?,?,?)",
            subject.organizationId,
            subject.orderId,
            version,
            updateId,
            "FACT",
            Timestamp.from(Instant.parse("2000-01-01T00:00:00Z"))
        )
        execute(
            connection,
            "INSERT INTO marketplace_economic_evidence_identifier " +
                "(organization_id,marketplace_order_id,observation_id,evidence_version,identifier_kind) " +
                "VALUES (?,?,?,?,?)",
            subject.organizationId,
            subject.orderId,
            updateId,
            version,
            "FACT"
        )
        insertComponentFact(connection, subject, updateId, version, amount, observedAt)
        execute(
            connection,
            "UPDATE marketplace_economic_evidence_subject SET current_version=? " +
                "WHERE organization_id=? AND marketplace_order_id=?",
            version,
            subject.organizationId,
            subject.orderId
        )
        val changeSequence = queryLong(
            connection,
            "SELECT change_sequence FROM marketplace_economic_evidence_update " +
                "WHERE organization_id=? AND marketplace_order_id=? AND evidence_version=?",
            subject.organizationId,
            subject.orderId,
            version
        )
        AppliedFixture(updateId, changeSequence)
    }

    private fun appendCorrection(
        subject: Subject,
        version: Long,
        supersededFactId: UUID
    ): CorrectionFixture = transaction { connection ->
        val correctionId = uuid()
        val replacementFactId = uuid()
        execute(
            connection,
            "INSERT INTO marketplace_economic_evidence_update " +
                "(organization_id,marketplace_order_id,evidence_version,update_id,change_kind) " +
                "VALUES (?,?,?,?,?)",
            subject.organizationId,
            subject.orderId,
            version,
            correctionId,
            "CORRECTION"
        )
        execute(
            connection,
            "INSERT INTO marketplace_economic_evidence_identifier " +
                "(organization_id,marketplace_order_id,observation_id,evidence_version,identifier_kind) " +
                "VALUES (?,?,?,?,?),(?,?,?,?,?)",
            subject.organizationId,
            subject.orderId,
            correctionId,
            version,
            "CORRECTION",
            subject.organizationId,
            subject.orderId,
            replacementFactId,
            version,
            "FACT"
        )
        insertComponentFact(
            connection,
            subject,
            replacementFactId,
            version,
            BigDecimal("11.000000"),
            baseTime.plusSeconds(version)
        )
        insertCorrectionRow(
            connection,
            subject,
            version,
            correctionId,
            supersededFactId,
            replacementFactId
        )
        execute(
            connection,
            "UPDATE marketplace_economic_evidence_subject SET current_version=? " +
                "WHERE organization_id=? AND marketplace_order_id=?",
            version,
            subject.organizationId,
            subject.orderId
        )
        CorrectionFixture(correctionId, replacementFactId)
    }

    private fun insertComponentFact(
        connection: Connection,
        subject: Subject,
        factId: UUID,
        version: Long,
        amount: BigDecimal,
        observedAt: Instant
    ) {
        execute(
            connection,
            "INSERT INTO marketplace_economic_evidence_fact " +
                "(organization_id,marketplace_order_id,fact_id,evidence_version,fact_kind,family,observed_at) " +
                "VALUES (?,?,?,?,?,?,?)",
            subject.organizationId,
            subject.orderId,
            factId,
            version,
            "COMPONENT",
            "MARKETPLACE_ORDER",
            Timestamp.from(observedAt)
        )
        execute(
            connection,
            "INSERT INTO marketplace_economic_evidence_component_fact " +
                "(organization_id,marketplace_order_id,fact_id,evidence_version,fact_kind,family," +
                "component_id,component_type,direction,magnitude,currency,source_kind,source_system_key," +
                "source_external_reference,source_external_reference_absence_reason,occurred_at,quality,coverage) " +
                "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            subject.organizationId,
            subject.orderId,
            factId,
            version,
            "COMPONENT",
            "MARKETPLACE_ORDER",
            uuid(),
            "REVENUE",
            "ADDITION",
            amount,
            "BRL",
            "MARKETPLACE",
            "meli-br",
            "source-$factId",
            null,
            Timestamp.from(observedAt),
            "CONFIRMED",
            "COMPLETE"
        )
    }

    private fun insertCorrectionRow(
        subject: Subject,
        evidenceVersion: Long,
        correctionId: UUID,
        supersededFactId: UUID,
        replacementFactId: UUID
    ) = connection().use { connection ->
        insertCorrectionRow(
            connection,
            subject,
            evidenceVersion,
            correctionId,
            supersededFactId,
            replacementFactId
        )
    }

    private fun insertCorrectionRow(
        connection: Connection,
        subject: Subject,
        evidenceVersion: Long,
        correctionId: UUID,
        supersededFactId: UUID,
        replacementFactId: UUID
    ) {
        execute(
            connection,
            "INSERT INTO marketplace_economic_evidence_correction " +
                "(organization_id,marketplace_order_id,correction_id,evidence_version," +
                "superseded_fact_id,replacement_fact_id,reason,observed_at) VALUES (?,?,?,?,?,?,?,?)",
            subject.organizationId,
            subject.orderId,
            correctionId,
            evidenceVersion,
            supersededFactId,
            replacementFactId,
            "SOURCE_CORRECTION",
            Timestamp.from(baseTime.plusSeconds(evidenceVersion))
        )
    }

    private fun createOrganization(
        organizationId: UUID = uuid(),
        status: String = "ACTIVE"
    ): UUID = organizationId.also {
        execute(
            "INSERT INTO integration_organization " +
                "(organization_id,status,created_at,updated_at) VALUES (?,?,?,?)",
            organizationId,
            status,
            Timestamp.from(baseTime),
            Timestamp.from(baseTime)
        )
    }

    private fun createSubject(
        organizationId: UUID,
        orderId: UUID,
        externalOrder: String = "order-$orderId"
    ) {
        execute(
            "INSERT INTO marketplace_economic_evidence_subject " +
                "(organization_id,marketplace_order_id,marketplace_key,external_order_id,currency) " +
                "VALUES (?,?,?,?,?)",
            organizationId,
            orderId,
            "mercado-livre",
            externalOrder,
            "BRL"
        )
    }

    private fun count(table: String, subject: Subject): Int = connection().use { connection ->
        connection.prepareStatement(
            "SELECT count(*) FROM $table WHERE organization_id=? AND marketplace_order_id=?"
        ).use { statement ->
            statement.setObject(1, subject.organizationId)
            statement.setObject(2, subject.orderId)
            statement.executeQuery().use { result -> result.next(); result.getInt(1) }
        }
    }

    private fun countSubjects(orderId: UUID): Int = connection().use { connection ->
        connection.prepareStatement(
            "SELECT count(*) FROM marketplace_economic_evidence_subject WHERE marketplace_order_id=?"
        ).use { statement ->
            statement.setObject(1, orderId)
            statement.executeQuery().use { result -> result.next(); result.getInt(1) }
        }
    }

    private fun subjectVersion(organizationId: UUID, orderId: UUID): String = queryOne(
        "SELECT current_version::text FROM marketplace_economic_evidence_subject " +
            "WHERE organization_id='$organizationId'::uuid AND marketplace_order_id='$orderId'::uuid"
    )

    private fun checkpoint(organizationId: UUID, checkpoint: Long): List<Long> = connection().use { connection ->
        connection.prepareStatement(
            "SELECT change_sequence FROM marketplace_economic_evidence_update " +
                "WHERE organization_id=? AND change_sequence>? ORDER BY change_sequence"
        ).use { statement ->
            statement.setObject(1, organizationId)
            statement.setLong(2, checkpoint)
            statement.executeQuery().use { result ->
                buildList { while (result.next()) add(result.getLong(1)) }
            }
        }
    }

    private fun explainCheckpointQuery(organizationId: UUID): List<String> = connection().use { connection ->
        connection.createStatement().use { it.execute("SET enable_seqscan=off") }
        connection.prepareStatement(
            "EXPLAIN SELECT change_sequence FROM marketplace_economic_evidence_update " +
                "WHERE organization_id=? AND change_sequence>? ORDER BY change_sequence"
        ).use { statement ->
            statement.setObject(1, organizationId)
            statement.setLong(2, 0)
            statement.executeQuery().use { result ->
                buildList { while (result.next()) add(result.getString(1)) }
            }
        }
    }

    private fun queryStrings(sql: String): List<String> = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result ->
                buildList {
                    while (result.next()) {
                        for (column in 1..result.metaData.columnCount) add(result.getString(column))
                    }
                }
            }
        }
    }

    private fun queryOne(sql: String): String = queryStrings(sql).single()

    private fun queryLong(sql: String): Long = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result -> result.next(); result.getLong(1) }
        }
    }

    private fun queryLong(connection: Connection, sql: String, vararg values: Any?): Long =
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeQuery().use { result -> result.next(); result.getLong(1) }
        }

    private fun queryInstant(sql: String): Instant = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(sql).use { result -> result.next(); result.getTimestamp(1).toInstant() }
        }
    }

    private fun execute(sql: String, vararg values: Any?): Int = connection().use { connection ->
        execute(connection, sql, *values)
    }

    private fun execute(connection: Connection, sql: String, vararg values: Any?): Int =
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }

    private fun <T> transaction(block: (Connection) -> T): T = connection().use { connection ->
        connection.autoCommit = false
        try {
            val result = block(connection)
            connection.commit()
            result
        } catch (failure: Throwable) {
            connection.rollback()
            throw failure
        }
    }

    private fun connection(): Connection = DriverManager.getConnection(
        configuration.url,
        configuration.user,
        configuration.password
    )

    private fun uuid(): UUID = UUID(15, identifiers.getAndIncrement())

    private fun domainSubject(
        organizationId: OrganizationId = OrganizationId(uuid())
    ): MarketplaceEconomicEvidenceSubject = MarketplaceEconomicEvidenceSubject(
        organizationId,
        MarketplaceOrderId(uuid()),
        MarketplaceKey("mercado-livre"),
        MarketplaceExternalOrderId("order-${identifiers.getAndIncrement()}"),
        MarketplaceCurrency("BRL")
    )

    private fun factUpdate(
        subject: MarketplaceEconomicEvidenceSubject,
        observationId: MarketplaceEconomicEvidenceObservationId = observationId(uuid()),
        amount: String = "10",
        sourceReference: String = "source-${identifiers.getAndIncrement()}"
    ): MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact =
        MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(
            MarketplaceIndependentEconomicFact.Component(
                MarketplaceEconomicComponentObservation(
                    observationId,
                    subject,
                    MarketplaceEconomicEvidenceFamily.MARKETPLACE_ORDER,
                    EconomicComponent(
                        subject.organizationId,
                        EconomicComponentId(uuid()),
                        subject.orderId,
                        EconomicComponentType.REVENUE,
                        EconomicDirection.ADDITION,
                        MarketplaceMoney.parse(subject.currency, amount),
                        EconomicSource(
                            EconomicSourceKind.MARKETPLACE,
                            EconomicSourceSystemKey("meli-br"),
                            EconomicExternalReferenceState.Present(
                                EconomicExternalReference(sourceReference)
                            )
                        ),
                        baseTime.plusSeconds(1),
                        EconomicEvidenceQuality.CONFIRMED
                    ),
                    EconomicComponentCoverage.COMPLETE,
                    baseTime.plusSeconds(2)
                )
            )
        )

    private fun orderOccurrenceUpdate(
        subject: MarketplaceEconomicEvidenceSubject,
        sourceKind: EconomicSourceKind = EconomicSourceKind.MARKETPLACE,
        sourceSystemKey: String = "meli-br",
        sourceReference: String? = "order-occurrence-${identifiers.getAndIncrement()}",
        occurredAt: Instant = baseTime.plusSeconds(1),
        observedAt: Instant = baseTime.plusSeconds(2)
    ): MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact =
        MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(
            orderOccurrenceFact(
                subject = subject,
                sourceKind = sourceKind,
                sourceSystemKey = sourceSystemKey,
                sourceReference = sourceReference,
                occurredAt = occurredAt,
                observedAt = observedAt
            )
        )

    private fun orderOccurrenceCorrectionUpdate(
        subject: MarketplaceEconomicEvidenceSubject,
        original: MarketplaceIndependentEconomicFact,
        occurredAt: Instant = baseTime.plusSeconds(3),
        observedAt: Instant = baseTime.plusSeconds(4)
    ): MarketplaceIndependentEconomicEvidenceUpdate.Correct {
        val originalOccurrence = original as MarketplaceIndependentEconomicFact.OrderOccurrence
        val replacement = orderOccurrenceFact(
            subject = subject,
            sourceKind = originalOccurrence.observation.source.kind,
            sourceSystemKey = originalOccurrence.observation.source.systemKey.value,
            sourceReference = when (
                val referenceState = originalOccurrence.observation.source.externalReference
            ) {
                is EconomicExternalReferenceState.Present -> referenceState.reference.value
                is EconomicExternalReferenceState.Absent -> null
            },
            occurredAt = occurredAt,
            observedAt = observedAt
        )
        return MarketplaceIndependentEconomicEvidenceUpdate.Correct(
            MarketplaceEconomicEvidenceCorrection(
                observationId(uuid()),
                subject,
                replacement,
                original.id,
                MarketplaceEconomicEvidenceCorrectionReason.SOURCE_CORRECTION,
                observedAt.plusSeconds(1)
            )
        )
    }

    private fun orderOccurrenceFact(
        subject: MarketplaceEconomicEvidenceSubject,
        sourceKind: EconomicSourceKind = EconomicSourceKind.MARKETPLACE,
        sourceSystemKey: String = "meli-br",
        sourceReference: String? = "order-occurrence-${identifiers.getAndIncrement()}",
        occurredAt: Instant = baseTime.plusSeconds(1),
        observedAt: Instant = baseTime.plusSeconds(2)
    ): MarketplaceIndependentEconomicFact.OrderOccurrence {
        val referenceState = if (sourceReference != null) {
            EconomicExternalReferenceState.Present(EconomicExternalReference(sourceReference))
        } else {
            EconomicExternalReferenceState.Absent(
                EconomicExternalReferenceAbsenceReason.INTERNAL_ORIGIN
            )
        }
        return MarketplaceIndependentEconomicFact.OrderOccurrence(
            MarketplaceEconomicOrderOccurrenceObservation(
                observationId(uuid()),
                subject,
                EconomicSource(
                    sourceKind,
                    EconomicSourceSystemKey(sourceSystemKey),
                    referenceState
                ),
                occurredAt,
                observedAt
            )
        )
    }
    private fun attemptUpdate(
        subject: MarketplaceEconomicEvidenceSubject
    ): MarketplaceIndependentEconomicEvidenceUpdate.RecordAttempt =
        MarketplaceIndependentEconomicEvidenceUpdate.RecordAttempt(
            MarketplaceEconomicEvidenceCollectionAttempt(
                observationId(uuid()),
                subject,
                MarketplaceEconomicEvidenceFamily.MARKETPLACE_SHIPPING,
                EconomicSourceSystemKey("meli-br"),
                MarketplaceEconomicEvidenceAttemptOutcome.NO_EVIDENCE,
                baseTime.plusSeconds(3)
            )
        )

    private fun correctionUpdate(
        subject: MarketplaceEconomicEvidenceSubject,
        original: MarketplaceIndependentEconomicFact
    ): MarketplaceIndependentEconomicEvidenceUpdate.Correct {
        val component = (original as MarketplaceIndependentEconomicFact.Component).observation.component
        val replacementId = observationId(uuid())
        val replacement = MarketplaceIndependentEconomicFact.Component(
            MarketplaceEconomicComponentObservation(
                replacementId,
                subject,
                original.family,
                component.copyWithMagnitude(subject, "11"),
                EconomicComponentCoverage.COMPLETE,
                baseTime.plusSeconds(4)
            )
        )
        return MarketplaceIndependentEconomicEvidenceUpdate.Correct(
            MarketplaceEconomicEvidenceCorrection(
                observationId(uuid()),
                subject,
                replacement,
                original.id,
                MarketplaceEconomicEvidenceCorrectionReason.SOURCE_CORRECTION,
                baseTime.plusSeconds(5)
            )
        )
    }

    private fun EconomicComponent.copyWithMagnitude(
        subject: MarketplaceEconomicEvidenceSubject,
        amount: String
    ): EconomicComponent = EconomicComponent(
        subject.organizationId,
        EconomicComponentId(uuid()),
        subject.orderId,
        type,
        direction,
        MarketplaceMoney.parse(subject.currency, amount),
        source,
        baseTime.plusSeconds(3),
        quality
    )

    private fun observationId(value: UUID): MarketplaceEconomicEvidenceObservationId =
        MarketplaceEconomicEvidenceObservationId.parse(value.toString())

    private fun journalRows(subject: MarketplaceEconomicEvidenceSubject): Int =
        count(
            "marketplace_economic_evidence_update",
            Subject(subject.organizationId.value, subject.orderId.value)
        )

    private fun subjectRows(subject: MarketplaceEconomicEvidenceSubject): Int =
        count(
            "marketplace_economic_evidence_subject",
            Subject(subject.organizationId.value, subject.orderId.value)
        )

    private fun evidenceIdentifierRows(subject: MarketplaceEconomicEvidenceSubject): Int =
        count(
            "marketplace_economic_evidence_identifier",
            Subject(subject.organizationId.value, subject.orderId.value)
        )

    private fun evidenceFactRows(subject: MarketplaceEconomicEvidenceSubject): Int =
        count(
            "marketplace_economic_evidence_fact",
            Subject(subject.organizationId.value, subject.orderId.value)
        )

    private fun evidenceCorrectionRows(subject: MarketplaceEconomicEvidenceSubject): Int =
        count(
            "marketplace_economic_evidence_correction",
            Subject(subject.organizationId.value, subject.orderId.value)
        )

    private fun orderOccurrenceRows(subject: MarketplaceEconomicEvidenceSubject): Int =
        count(
            "marketplace_economic_evidence_order_occurrence_fact",
            Subject(subject.organizationId.value, subject.orderId.value)
        )

    private fun maximumChangeSequenceOrZero(organizationId: UUID): Long = queryLong(
        "SELECT COALESCE(max(change_sequence),0) FROM marketplace_economic_evidence_update " +
            "WHERE organization_id='$organizationId'::uuid"
    )

    private fun maximumChangeSequence(organizationId: UUID): Long = queryLong(
        "SELECT max(change_sequence) FROM marketplace_economic_evidence_update " +
            "WHERE organization_id='$organizationId'::uuid"
    )

    private fun totalJournalRows(): Int = queryOne(
        "SELECT count(*)::text FROM marketplace_economic_evidence_update"
    ).toInt()

    private fun distinctChangeSequences(): Int = queryOne(
        "SELECT count(DISTINCT change_sequence)::text FROM marketplace_economic_evidence_update"
    ).toInt()

    private fun changeSequence(subject: MarketplaceEconomicEvidenceSubject): Long = queryLong(
        "SELECT change_sequence FROM marketplace_economic_evidence_update " +
            "WHERE organization_id='${subject.organizationId.value}'::uuid " +
            "AND marketplace_order_id='${subject.orderId.value}'::uuid"
    )

    private fun <T> concurrent(first: () -> T, second: () -> T): List<T> {
        val executor = Executors.newFixedThreadPool(2)
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        return try {
            val futures = listOf(first, second).map { operation ->
                executor.submit(Callable {
                    ready.countDown()
                    check(start.await(10, TimeUnit.SECONDS))
                    operation()
                })
            }
            check(ready.await(10, TimeUnit.SECONDS))
            start.countDown()
            futures.map { it.get(30, TimeUnit.SECONDS) }
        } finally {
            executor.shutdownNow()
        }
    }

    private data class Subject(val organizationId: UUID, val orderId: UUID)
    private data class AppliedFixture(val updateId: UUID, val changeSequence: Long)
    private data class CorrectionFixture(val correctionId: UUID, val replacementFactId: UUID)

    private companion object {
        val EXPECTED_TABLES = setOf(
            "marketplace_economic_evidence_subject",
            "marketplace_economic_evidence_update",
            "marketplace_economic_evidence_identifier",
            "marketplace_economic_evidence_fact",
            "marketplace_economic_evidence_component_fact",
            "marketplace_economic_evidence_external_identity_fact",
            "marketplace_economic_evidence_order_occurrence_fact",
            "marketplace_economic_evidence_collection_attempt",
            "marketplace_economic_evidence_correction"
        )
        val APPEND_ONLY_TABLES = EXPECTED_TABLES - "marketplace_economic_evidence_subject"
    }
}
