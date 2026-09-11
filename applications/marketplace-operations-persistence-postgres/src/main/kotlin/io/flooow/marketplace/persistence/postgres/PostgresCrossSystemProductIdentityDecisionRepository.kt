package io.flooow.marketplace.persistence.postgres

import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityCorrelationId
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityDecision
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityDecisionId
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityDecisionKind
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityDecisionReason
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityDecisionRepository
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityDecisionRequest
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityPrincipal
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityProvenance
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityRelation
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityScope
import io.flooow.marketplace.operations.identity.CrossSystemProductIdentityWriteResult
import io.flooow.marketplace.operations.identity.MercadoLivreProductIdentity
import io.flooow.marketplace.operations.identity.OmieProviderProductIdentity
import io.flooow.organization.OrganizationId
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.sql.SQLException
import java.sql.Timestamp
import java.time.Instant
import java.util.UUID

class PostgresCrossSystemProductIdentityDecisionRepository(
    private val configuration: PostgresConfiguration
) : CrossSystemProductIdentityDecisionRepository {
    override fun record(
        request: CrossSystemProductIdentityDecisionRequest,
        decidedAt: Instant
    ): CrossSystemProductIdentityWriteResult = try {
        transaction { connection ->
            lockMarketplaceIdentity(connection, request.relation)
            decisionById(connection, request.relation.scope.organizationId, request.id)?.let {
                return@transaction if (it.request == request) {
                    CrossSystemProductIdentityWriteResult.AlreadyApplied(it)
                } else {
                    CrossSystemProductIdentityWriteResult.IntegrityFailure
                }
            }
            if (!scopeAvailable(connection, request.relation.scope)) {
                return@transaction CrossSystemProductIdentityWriteResult.ScopeUnavailable
            }
            if (!evidenceAvailable(connection, request.relation)) {
                return@transaction CrossSystemProductIdentityWriteResult.EvidenceUnavailable
            }

            val previous = request.supersedesDecisionId?.let { id ->
                val stored = decisionById(connection, request.relation.scope.organizationId, id)
                    ?: return@transaction CrossSystemProductIdentityWriteResult.Conflict
                if (stored.request.relation != request.relation ||
                    isSuperseded(connection, stored.request.relation.scope.organizationId, id)) {
                    return@transaction CrossSystemProductIdentityWriteResult.Conflict
                }
                stored
            }
            if (previous == null && currentForRelation(connection, request.relation) != null) {
                return@transaction CrossSystemProductIdentityWriteResult.Conflict
            }
            if (request.kind == CrossSystemProductIdentityDecisionKind.CONFIRMED &&
                competingConfirmation(connection, request.relation, request.supersedesDecisionId)) {
                return@transaction CrossSystemProductIdentityWriteResult.Conflict
            }

            val decision = CrossSystemProductIdentityDecision(
                request,
                (previous?.revision ?: 0) + 1,
                decidedAt
            )
            insert(connection, decision)
            CrossSystemProductIdentityWriteResult.Applied(decision)
        }
    } catch (error: Exception) {
        if (error is SQLException && error.sqlState == "23505") classifyCollision(request)
        else CrossSystemProductIdentityWriteResult.IntegrityFailure
    }

    override fun find(
        organizationId: OrganizationId,
        id: CrossSystemProductIdentityDecisionId
    ): CrossSystemProductIdentityDecision? = connection().use {
        decisionById(it, organizationId, id)
    }

    override fun history(
        relation: CrossSystemProductIdentityRelation
    ): List<CrossSystemProductIdentityDecision> = connection().use { connection ->
        connection.prepareStatement(
            "SELECT * FROM integration_cross_system_product_identity_decision d WHERE " +
                relationPredicate("d") + " ORDER BY revision,decided_at,decision_id"
        ).use { statement ->
            bindRelation(statement, relation)
            statement.executeQuery().use(::readAll)
        }
    }

    override fun currentForMarketplaceIdentity(
        scope: CrossSystemProductIdentityScope,
        identity: MercadoLivreProductIdentity
    ): List<CrossSystemProductIdentityDecision> = connection().use { connection ->
        connection.prepareStatement(
            "SELECT d.* FROM integration_cross_system_product_identity_decision d " +
                "WHERE d.organization_id=? AND d.mercado_livre_connection_id=? " +
                "AND d.mercado_livre_item_id=? AND d.mercado_livre_seller_sku=? " +
                "AND d.omie_connection_id=? AND NOT EXISTS (SELECT 1 FROM " +
                "integration_cross_system_product_identity_decision child WHERE " +
                "child.organization_id=d.organization_id AND " +
                "child.supersedes_decision_id=d.decision_id) " +
                "ORDER BY d.omie_provider_product_id,d.decision_id"
        ).use { statement ->
            statement.setObject(1, scope.organizationId.value)
            statement.setObject(2, UUID.fromString(scope.mercadoLivreConnectionId))
            statement.setString(3, identity.itemId)
            statement.setString(4, identity.sellerSku)
            statement.setObject(5, UUID.fromString(scope.omieConnectionId))
            statement.executeQuery().use(::readAll)
        }
    }

    private fun scopeAvailable(
        connection: Connection,
        scope: CrossSystemProductIdentityScope
    ): Boolean = connection.prepareStatement(
        "SELECT count(*) FROM integration_organization o JOIN integration_connection c " +
            "ON c.organization_id=o.organization_id WHERE o.organization_id=? " +
            "AND o.status='ACTIVE' AND c.status='ACTIVE' AND " +
            "((c.connection_id=? AND c.provider_key='br.com.mercadolivre') OR " +
            "(c.connection_id=? AND c.provider_key='omie'))"
    ).use { statement ->
        statement.setObject(1, scope.organizationId.value)
        statement.setObject(2, UUID.fromString(scope.mercadoLivreConnectionId))
        statement.setObject(3, UUID.fromString(scope.omieConnectionId))
        statement.executeQuery().use { it.next() && it.getInt(1) == 2 }
    }

    private fun evidenceAvailable(
        connection: Connection,
        relation: CrossSystemProductIdentityRelation
    ): Boolean {
        val scope = relation.scope
        val ml = connection.prepareStatement(
            "SELECT 1 FROM integration_mercado_livre_order_item_source_observation " +
                "WHERE organization_id=? AND connection_id=? AND item_ref=? AND seller_sku=? LIMIT 1"
        ).use { statement ->
            statement.setObject(1, scope.organizationId.value)
            statement.setObject(2, UUID.fromString(scope.mercadoLivreConnectionId))
            statement.setString(3, relation.mercadoLivre.itemId)
            statement.setString(4, relation.mercadoLivre.sellerSku)
            statement.executeQuery().use(ResultSet::next)
        }
        if (!ml) return false
        return connection.prepareStatement(
            "SELECT 1 FROM integration_omie_product_cost_source_observation " +
                "WHERE organization_id=? AND connection_id=? AND source_product_ref=? LIMIT 1"
        ).use { statement ->
            statement.setObject(1, scope.organizationId.value)
            statement.setObject(2, UUID.fromString(scope.omieConnectionId))
            statement.setString(3, relation.omie.providerProductId)
            statement.executeQuery().use(ResultSet::next)
        }
    }

    private fun lockMarketplaceIdentity(connection: Connection, relation: CrossSystemProductIdentityRelation) {
        val key = listOf(
            relation.scope.organizationId.value,
            relation.scope.mercadoLivreConnectionId,
            relation.mercadoLivre.itemId,
            relation.mercadoLivre.sellerSku,
            relation.scope.omieConnectionId
        ).joinToString("\n")
        connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtextextended(?,0))").use {
            it.setString(1, key)
            it.executeQuery().use(ResultSet::next)
        }
    }

    private fun competingConfirmation(
        connection: Connection,
        relation: CrossSystemProductIdentityRelation,
        superseded: CrossSystemProductIdentityDecisionId?
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_cross_system_product_identity_decision d WHERE " +
            "d.organization_id=? AND d.mercado_livre_connection_id=? " +
            "AND d.mercado_livre_item_id=? AND d.mercado_livre_seller_sku=? " +
            "AND d.omie_connection_id=? AND d.decision_kind='CONFIRMED' " +
            "AND (?::uuid IS NULL OR d.decision_id<>?::uuid) AND NOT EXISTS " +
            "(SELECT 1 FROM integration_cross_system_product_identity_decision child WHERE " +
            "child.organization_id=d.organization_id AND child.supersedes_decision_id=d.decision_id) LIMIT 1"
    ).use { statement ->
        statement.setObject(1, relation.scope.organizationId.value)
        statement.setObject(2, UUID.fromString(relation.scope.mercadoLivreConnectionId))
        statement.setString(3, relation.mercadoLivre.itemId)
        statement.setString(4, relation.mercadoLivre.sellerSku)
        statement.setObject(5, UUID.fromString(relation.scope.omieConnectionId))
        statement.setObject(6, superseded?.value)
        statement.setObject(7, superseded?.value)
        statement.executeQuery().use(ResultSet::next)
    }

    private fun currentForRelation(
        connection: Connection,
        relation: CrossSystemProductIdentityRelation
    ): CrossSystemProductIdentityDecision? = connection.prepareStatement(
        "SELECT d.* FROM integration_cross_system_product_identity_decision d WHERE " +
            relationPredicate("d") + " AND NOT EXISTS (SELECT 1 FROM " +
            "integration_cross_system_product_identity_decision child WHERE " +
            "child.organization_id=d.organization_id AND child.supersedes_decision_id=d.decision_id)"
    ).use { statement ->
        bindRelation(statement, relation)
        statement.executeQuery().use { if (it.next()) read(it) else null }
    }

    private fun isSuperseded(
        connection: Connection,
        organizationId: OrganizationId,
        id: CrossSystemProductIdentityDecisionId
    ): Boolean = connection.prepareStatement(
        "SELECT 1 FROM integration_cross_system_product_identity_decision WHERE " +
            "organization_id=? AND supersedes_decision_id=?"
    ).use {
        it.setObject(1, organizationId.value)
        it.setObject(2, id.value)
        it.executeQuery().use(ResultSet::next)
    }

    private fun insert(connection: Connection, decision: CrossSystemProductIdentityDecision) {
        val request = decision.request
        val relation = request.relation
        connection.prepareStatement(
            "INSERT INTO integration_cross_system_product_identity_decision (" +
                "organization_id,decision_id,mercado_livre_connection_id,mercado_livre_item_id," +
                "mercado_livre_seller_sku,omie_connection_id,omie_provider_product_id," +
                "decision_kind,revision,supersedes_decision_id,principal_ref,reason,provenance," +
                "correlation_id,decided_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)"
        ).use { statement ->
            statement.setObject(1, relation.scope.organizationId.value)
            statement.setObject(2, request.id.value)
            statement.setObject(3, UUID.fromString(relation.scope.mercadoLivreConnectionId))
            statement.setString(4, relation.mercadoLivre.itemId)
            statement.setString(5, relation.mercadoLivre.sellerSku)
            statement.setObject(6, UUID.fromString(relation.scope.omieConnectionId))
            statement.setString(7, relation.omie.providerProductId)
            statement.setString(8, request.kind.name)
            statement.setInt(9, decision.revision)
            statement.setObject(10, request.supersedesDecisionId?.value)
            statement.setString(11, request.principal.encodedForPersistence())
            statement.setString(12, request.reason.name)
            statement.setString(13, request.provenance.encodedForPersistence())
            statement.setObject(14, request.correlationId.value)
            statement.setTimestamp(15, Timestamp.from(decision.decidedAt))
            check(statement.executeUpdate() == 1)
        }
    }

    private fun decisionById(
        connection: Connection,
        organizationId: OrganizationId,
        id: CrossSystemProductIdentityDecisionId
    ): CrossSystemProductIdentityDecision? = connection.prepareStatement(
        "SELECT * FROM integration_cross_system_product_identity_decision " +
            "WHERE organization_id=? AND decision_id=?"
    ).use {
        it.setObject(1, organizationId.value)
        it.setObject(2, id.value)
        it.executeQuery().use { result -> if (result.next()) read(result) else null }
    }

    private fun readAll(result: ResultSet) = buildList {
        while (result.next()) add(read(result))
    }

    private fun read(result: ResultSet): CrossSystemProductIdentityDecision {
        val organizationId = OrganizationId(result.getObject("organization_id", UUID::class.java))
        val relation = CrossSystemProductIdentityRelation(
            CrossSystemProductIdentityScope(
                organizationId,
                result.getObject("mercado_livre_connection_id", UUID::class.java).toString(),
                result.getObject("omie_connection_id", UUID::class.java).toString()
            ),
            MercadoLivreProductIdentity(
                result.getString("mercado_livre_item_id"),
                result.getString("mercado_livre_seller_sku")
            ),
            OmieProviderProductIdentity(result.getString("omie_provider_product_id"))
        )
        val request = CrossSystemProductIdentityDecisionRequest(
            CrossSystemProductIdentityDecisionId(
                result.getObject("decision_id", UUID::class.java)
            ),
            relation,
            CrossSystemProductIdentityDecisionKind.valueOf(result.getString("decision_kind")),
            CrossSystemProductIdentityPrincipal.of(result.getString("principal_ref")),
            CrossSystemProductIdentityDecisionReason.valueOf(result.getString("reason")),
            CrossSystemProductIdentityProvenance.of(result.getString("provenance")),
            CrossSystemProductIdentityCorrelationId(
                result.getObject("correlation_id", UUID::class.java)
            ),
            result.getObject("supersedes_decision_id", UUID::class.java)
                ?.let(::CrossSystemProductIdentityDecisionId)
        )
        return CrossSystemProductIdentityDecision(
            request,
            result.getInt("revision"),
            result.getTimestamp("decided_at").toInstant()
        )
    }

    private fun relationPredicate(alias: String) =
        "$alias.organization_id=? AND $alias.mercado_livre_connection_id=? " +
            "AND $alias.mercado_livre_item_id=? AND $alias.mercado_livre_seller_sku=? " +
            "AND $alias.omie_connection_id=? AND $alias.omie_provider_product_id=?"

    private fun bindRelation(
        statement: java.sql.PreparedStatement,
        relation: CrossSystemProductIdentityRelation
    ) {
        statement.setObject(1, relation.scope.organizationId.value)
        statement.setObject(2, UUID.fromString(relation.scope.mercadoLivreConnectionId))
        statement.setString(3, relation.mercadoLivre.itemId)
        statement.setString(4, relation.mercadoLivre.sellerSku)
        statement.setObject(5, UUID.fromString(relation.scope.omieConnectionId))
        statement.setString(6, relation.omie.providerProductId)
    }

    private fun classifyCollision(
        request: CrossSystemProductIdentityDecisionRequest
    ): CrossSystemProductIdentityWriteResult = try {
        connection().use { connection ->
            val existing = decisionById(
                connection,
                request.relation.scope.organizationId,
                request.id
            )
            if (existing?.request == request) {
                CrossSystemProductIdentityWriteResult.AlreadyApplied(existing)
            } else if (existing != null) {
                CrossSystemProductIdentityWriteResult.IntegrityFailure
            } else {
                CrossSystemProductIdentityWriteResult.Conflict
            }
        }
    } catch (_: Exception) {
        CrossSystemProductIdentityWriteResult.IntegrityFailure
    }

    private fun connection() = DriverManager.getConnection(
        configuration.url,
        configuration.user,
        configuration.password
    )

    private fun <T> transaction(operation: (Connection) -> T): T = connection().use { connection ->
        connection.autoCommit = false
        try {
            operation(connection).also { connection.commit() }
        } catch (error: Exception) {
            connection.rollback()
            throw error
        }
    }
}
