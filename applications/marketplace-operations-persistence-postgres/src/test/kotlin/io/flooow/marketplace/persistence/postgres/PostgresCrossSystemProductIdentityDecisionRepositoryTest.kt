package io.flooow.marketplace.persistence.postgres

import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityConfirmationService
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityCorrelationId
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityDecisionId
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityDecisionKind
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityDecisionReason
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityDecisionRequest
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityPrincipal
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityProvenance
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityRelation
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityResolution
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityScope
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityWriteResult
import io.flooow.marketplace.operations.identity.MercadoLivreProductIdentity
import io.flooow.marketplace.operations.identity.OmieProviderProductIdentity
import io.flooow.organization.OrganizationId
import java.sql.DriverManager
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertFailsWith
import org.flywaydb.core.Flyway
import org.testcontainers.postgresql.PostgreSQLContainer

class PostgresCrossSystemProductIdentityDecisionRepositoryTest {
    private val now = Instant.parse("2026-09-11T18:00:00Z")
    private lateinit var postgres: PostgreSQLContainer
    private lateinit var configuration: PostgresConfiguration
    private lateinit var repository: PostgresCrossSystemProductIdentityDecisionRepository
    private val organization = OrganizationId(UUID.randomUUID())
    private lateinit var mlConnection: UUID
    private lateinit var omieConnection: UUID

    @BeforeTest
    fun startPostgres() {
        postgres = PostgreSQLContainer("postgres:18.4")
        postgres.start()
        configuration = PostgresConfiguration(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway.configure().dataSource(configuration.url, configuration.user, configuration.password)
            .load().migrate()
        repository = PostgresCrossSystemProductIdentityDecisionRepository(configuration)
        mlConnection = UUID.randomUUID()
        omieConnection = UUID.randomUUID()
        seedScopeAndEvidence()
    }

    @AfterTest
    fun stopPostgres() = postgres.stop()

    @Test
    fun `explicit decisions are append only replayable correctable and conflict closed`() {
        val first = request(UUID.randomUUID(), "OMIE-1", CrossSystemProductIdentityDecisionKind.CONFIRMED)
        assertIs<CrossSystemProductIdentityWriteResult.Applied>(repository.record(first, now))
        assertIs<CrossSystemProductIdentityWriteResult.AlreadyApplied>(repository.record(first, now.plusSeconds(1)))

        val competing = request(UUID.randomUUID(), "OMIE-2", CrossSystemProductIdentityDecisionKind.CONFIRMED)
        assertEquals(
            CrossSystemProductIdentityWriteResult.Conflict,
            repository.record(competing, now.plusSeconds(2))
        )

        val correction = request(
            UUID.randomUUID(), "OMIE-1", CrossSystemProductIdentityDecisionKind.REJECTED,
            CrossSystemProductIdentityDecisionReason.CORRECTION,
            first.id
        )
        val corrected = assertIs<CrossSystemProductIdentityWriteResult.Applied>(
            repository.record(correction, now.plusSeconds(3))
        )
        assertEquals(2, corrected.decision.revision)
        assertEquals(2, repository.history(first.relation).size)
        assertEquals(2, rowCount())
        assertFailsWith<SQLException> {
            execute(
                "UPDATE integration_cross_system_product_identity_decision SET provenance='changed' " +
                    "WHERE organization_id=? AND decision_id=?",
                organization.value, first.id.value
            )
        }
        assertIs<CrossSystemProductIdentityResolution.Rejected>(
            CrossSystemProductIdentityConfirmationService(repository).resolve(first.relation)
        )

        assertIs<CrossSystemProductIdentityWriteResult.Applied>(
            repository.record(competing, now.plusSeconds(4))
        )
        assertIs<CrossSystemProductIdentityResolution.Confirmed>(
            CrossSystemProductIdentityConfirmationService(repository).resolve(competing.relation)
        )
    }

    @Test
    fun `scope evidence connection and organization isolation fail closed`() {
        val rejected = request(
            UUID.randomUUID(), "OMIE-2", CrossSystemProductIdentityDecisionKind.REJECTED,
            CrossSystemProductIdentityDecisionReason.EXPLICIT_REJECTION
        )
        assertIs<CrossSystemProductIdentityWriteResult.Applied>(repository.record(rejected, now))
        assertNull(repository.find(OrganizationId(UUID.randomUUID()), rejected.id))

        val wrongConnection = rejected.copy(
            id = CrossSystemProductIdentityDecisionId(UUID.randomUUID()),
            relation = rejected.relation.copy(
                scope = rejected.relation.scope.copy(omieConnectionId = UUID.randomUUID().toString())
            )
        )
        assertEquals(
            CrossSystemProductIdentityWriteResult.ScopeUnavailable,
            repository.record(wrongConnection, now)
        )

        val absentEvidence = rejected.copy(
            id = CrossSystemProductIdentityDecisionId(UUID.randomUUID()),
            relation = rejected.relation.copy(
                mercadoLivre = MercadoLivreProductIdentity("MLB-ABSENT", "SKU-ABSENT")
            )
        )
        assertEquals(
            CrossSystemProductIdentityWriteResult.EvidenceUnavailable,
            repository.record(absentEvidence, now)
        )

        val collision = rejected.copy(provenance = CrossSystemProductIdentityProvenance.of("different"))
        assertEquals(
            CrossSystemProductIdentityWriteResult.IntegrityFailure,
            repository.record(collision, now)
        )
    }

    private fun request(
        id: UUID,
        product: String,
        kind: CrossSystemProductIdentityDecisionKind,
        reason: CrossSystemProductIdentityDecisionReason =
            CrossSystemProductIdentityDecisionReason.EXPLICIT_CONFIRMATION,
        supersedes: CrossSystemProductIdentityDecisionId? = null
    ) = CrossSystemProductIdentityDecisionRequest(
        CrossSystemProductIdentityDecisionId(id),
        CrossSystemProductIdentityRelation(
            CrossSystemProductIdentityScope(
                organization, mlConnection.toString(), omieConnection.toString()
            ),
            MercadoLivreProductIdentity("MLB-1", "SKU-EXACT"),
            OmieProviderProductIdentity(product)
        ),
        kind,
        CrossSystemProductIdentityPrincipal.of("operator:test"),
        reason,
        CrossSystemProductIdentityProvenance.of("TASK-0165K test evidence"),
        CrossSystemProductIdentityCorrelationId(UUID.randomUUID()),
        supersedes
    )

    private fun seedScopeAndEvidence() {
        execute(
            "INSERT INTO integration_organization VALUES (?,'ACTIVE',?,?)",
            organization.value, Timestamp.from(now), Timestamp.from(now)
        )
        connection(mlConnection, "br.com.mercadolivre", "OAUTH2_AUTHORIZATION_CODE")
        connection(omieConnection, "omie", "STATIC_API_CREDENTIAL")
        page(mlConnection, "marketplace-economic.order-source", 1)
        page(omieConnection, "marketplace-economic.product-cost", 2)
        execute(
            "INSERT INTO integration_mercado_livre_order_source_observation " +
                "(organization_id,connection_id,capability,input_progress_version,record_ordinal," +
                "external_order_ref,provider_status,date_created,date_last_updated,currency," +
                "total_amount,observed_at) VALUES (?,?,'marketplace-economic.order-source',0,0," +
                "'ORDER-1','paid',?,?,'BRL',10,?)",
            organization.value, mlConnection, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now)
        )
        execute(
            "INSERT INTO integration_mercado_livre_order_item_source_observation " +
                "(organization_id,connection_id,capability,input_progress_version,record_ordinal," +
                "item_ordinal,item_ref,quantity,unit_price,currency,seller_sku) VALUES " +
                "(?,?,'marketplace-economic.order-source',0,0,0,'MLB-1',1,10,'BRL','SKU-EXACT')",
            organization.value, mlConnection
        )
        listOf("OMIE-1", "OMIE-2").forEachIndexed { index, product ->
            execute(
                "INSERT INTO integration_omie_product_cost_source_observation " +
                    "(organization_id,connection_id,capability,input_progress_version,record_ordinal," +
                    "source_product_ref,source_location_ref,position_date,observed_at) VALUES " +
                    "(?,?,'marketplace-economic.product-cost',0,?,'$product','LOCAL','2026-09-11',?)",
                organization.value, omieConnection, index, Timestamp.from(now)
            )
        }
    }

    private fun connection(id: UUID, provider: String, credential: String) = execute(
        "INSERT INTO integration_connection VALUES (?,?,?,?,'ACTIVE',1,?,?)",
        organization.value, id, provider, credential, Timestamp.from(now), Timestamp.from(now)
    )

    private fun page(id: UUID, capability: String, keyByte: Byte) {
        execute(
            "INSERT INTO integration_connector_progress VALUES (?,?,?,1,NULL,true,?,?)",
            organization.value, id, capability, Timestamp.from(now), Timestamp.from(now)
        )
        execute(
            "INSERT INTO integration_connector_page_commit VALUES (?,?,?,0,?,2,true,?,?)",
            organization.value, id, capability, ByteArray(32) { keyByte },
            Timestamp.from(now), Timestamp.from(now)
        )
    }

    private fun rowCount(): Int = connection().use { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT count(*) FROM integration_cross_system_product_identity_decision")
                .use { it.next(); it.getInt(1) }
        }
    }

    private fun execute(sql: String, vararg values: Any) = connection().use { connection ->
        connection.prepareStatement(sql).use { statement ->
            values.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
            statement.executeUpdate()
        }
    }

    private fun connection() = DriverManager.getConnection(
        configuration.url, configuration.user, configuration.password
    )
}
