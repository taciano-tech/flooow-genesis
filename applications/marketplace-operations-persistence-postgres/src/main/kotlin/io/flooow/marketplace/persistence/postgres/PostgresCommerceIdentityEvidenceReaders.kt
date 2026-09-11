package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.marketplace.operations.economics.provider.MarketplaceEconomicOrderSourceCapability
import io.flooow.marketplace.operations.economics.provider.OmieTransactionEvidenceCapability
import io.flooow.marketplace.operations.identity.*
import io.flooow.organization.OrganizationId
import java.math.BigDecimal
import java.sql.DriverManager
import java.sql.ResultSet
import java.time.Instant

/** Read-only reconstruction of the existing V026 Omie evidence table. */
class PostgresOmieIdentityEvidenceReader(
    private val configuration: PostgresConfiguration,
    private val connectionId: IntegrationConnectionId? = null
) : OmieIdentityEvidenceReader {
    override fun read(organizationId: OrganizationId, limit: Int): OmieIdentityEvidenceRead {
        require(limit in 1..10_000)
        val boundConnection = requireNotNull(connectionId) {
            "Omie identity evidence connection scope is required"
        }
        val rows = mutableListOf<OmieRow>()
        DriverManager.getConnection(configuration.url, configuration.user, configuration.password).use { c ->
            c.prepareStatement(
                "SELECT source_order_ref,source_integration_ref,source_customer_order_ref,occurred_at," +
                    "currency,total_amount,product_refs,observed_at,source_fingerprint,capability," +
                    "input_progress_version,record_ordinal " +
                    "FROM integration_omie_transaction_evidence WHERE organization_id=? " +
                "AND capability IN (?,?,?) AND connection_id=? " +
                    "ORDER BY source_order_ref,observed_at DESC,capability DESC,input_progress_version DESC,record_ordinal " +
                    "LIMIT ?"
            ).use { s ->
                s.setObject(1, organizationId.value)
                s.setString(2, OmieTransactionEvidenceCapability.KEY.value)
                s.setString(3, OmieTransactionEvidenceCapability.REACQUISITION_V1_KEY.value)
                s.setString(4, OmieTransactionEvidenceCapability.REACQUISITION_KEY.value)
                s.setObject(5, boundConnection.value)
                s.setInt(6, limit)
                s.executeQuery().use { rs -> while (rs.next()) rows += readRow(rs) }
            }
        }
        val distinctRows = selectPreferredRevisions(rows)
        val scope = OmieEvidenceScope(organizationId, boundConnection.value.toString())
        val records = distinctRows.mapNotNull { it.toDomain(organizationId, scope) }
        return OmieIdentityEvidenceRead(
            records = records,
            persistedRows = distinctRows.size,
            skippedRows = distinctRows.size - records.size,
            integrationReferenceRows = distinctRows.count { !it.integration.isNullOrBlank() },
            customerOrderReferenceRows = distinctRows.count { !it.customer.isNullOrBlank() },
            productEvidenceRows = distinctRows.count { it.hasProductEvidence() },
            amountEvidenceRows = distinctRows.count { it.currency != null && it.amount != null }
        )
    }

    private fun readRow(rs: ResultSet) = OmieRow(
        rs.getString("source_order_ref").trim(),
        rs.getString("source_integration_ref")?.trim()?.takeIf { it.isNotEmpty() },
        rs.getString("source_customer_order_ref")?.trim()?.takeIf { it.isNotEmpty() },
        rs.getTimestamp("occurred_at")?.toInstant(),
        rs.getString("currency")?.trim(),
        rs.getBigDecimal("total_amount"),
        rs.getString("product_refs"),
        rs.getTimestamp("observed_at").toInstant(),
        rs.getString("source_fingerprint"),
        rs.getString("capability"),
        rs.getLong("input_progress_version"),
        rs.getInt("record_ordinal")
    )

    internal fun selectPreferredRevisions(rows: List<OmieRow>): List<OmieRow> =
        rows.groupBy { it.orderRef to it.fingerprint }.values.map { revisions ->
            revisions.maxWithOrNull(
                compareBy<OmieRow> { it.typedProductCount() }
                    .thenBy { it.observed }
                    .thenBy { it.capability }
                    .thenBy { it.progressVersion }
                    .thenBy { it.recordOrdinal }
            ) ?: error("Omie revision group is empty")
        }.sortedWith(compareBy<OmieRow> { it.orderRef }.thenByDescending { it.observed })

    internal data class OmieRow(
        val orderRef: String, val integration: String?, val customer: String?, val occurred: Instant?,
        val currency: String?, val amount: BigDecimal?, val products: String, val observed: Instant,
        val fingerprint: String,
        val capability: String = OmieTransactionEvidenceCapability.KEY.value,
        val progressVersion: Long = 0,
        val recordOrdinal: Int = 0
    ) {
        private fun parsedProducts(): List<Pair<OmieProductIdentifier, BigDecimal>> =
            OmieProductRefsJson.decode(products)

        fun typedProductCount(): Int = parsedProducts().count {
            it.first.kind != OmieProductIdentifierKind.UNKNOWN_LEGACY
        }

        fun hasProductEvidence(): Boolean = parsedProducts().isNotEmpty()

        fun toDomain(org: OrganizationId, scope: OmieEvidenceScope): OmieSalesOrderEvidence? {
            val parsed = parsedProducts()
            val productQuantities = parsed.groupBy({ it.first }, { it.second })
                .mapValues { (_, quantities) -> quantities.fold(BigDecimal.ZERO, BigDecimal::add) }
            val legacyQuantities = productQuantities.entries.groupBy({ it.key.value }, { it.value })
                .mapValues { (_, quantities) -> quantities.fold(BigDecimal.ZERO, BigDecimal::add) }
            val at = occurred ?: return null
            if (integration.isNullOrBlank() && customer.isNullOrBlank() && productQuantities.isEmpty()) return null
            val money = if (currency != null && amount != null) CommerceIdentityAmount(currency, amount) else null
            return OmieSalesOrderEvidence(
                org, legacyQuantities.keys, legacyQuantities, integration, customer, money, at,
                setOf("omie:$orderRef:$fingerprint"), emptySet(), scope,
                productQuantities.keys, productQuantities, observed
            )
        }
    }
}

/** Read-only reconstruction of Mercado Livre source evidence already persisted by V021. */
class PostgresMercadoLivreIdentityEvidenceReader(
    private val configuration: PostgresConfiguration,
    private val connectionId: IntegrationConnectionId? = null
) : MercadoLivreIdentityEvidenceReader {
    override fun read(organizationId: OrganizationId, limit: Int): MercadoLivreIdentityEvidenceRead {
        require(limit in 1..10_000)
        val rows = mutableListOf<MlRow>()
        DriverManager.getConnection(configuration.url, configuration.user, configuration.password).use { c ->
            c.prepareStatement(
                "SELECT DISTINCT ON (external_order_ref) organization_id,external_order_ref,pack_ref,shipping_ref," +
                    "date_created,currency,total_amount,observed_at,connection_id,capability,input_progress_version,record_ordinal " +
                    "FROM integration_mercado_livre_order_source_observation WHERE organization_id=? " +
                "AND capability IN (?,?) AND (? IS NULL OR connection_id=?) " +
                    "ORDER BY external_order_ref,date_last_updated DESC,observed_at DESC,capability DESC,input_progress_version DESC,record_ordinal " +
                    "LIMIT ?"
            ).use { s ->
                s.setObject(1, organizationId.value)
                s.setString(2, MarketplaceEconomicOrderSourceCapability.KEY.value)
                s.setString(3, MarketplaceEconomicOrderSourceCapability.REACQUISITION_KEY.value)
                s.setObject(4, connectionId?.value)
                s.setObject(5, connectionId?.value)
                s.setInt(6, limit)
                s.executeQuery().use { rs -> while (rs.next()) rows += readRow(c, rs) }
            }
        }
        val records = rows.map { it.toDomain(organizationId) }
        return MercadoLivreIdentityEvidenceRead(
            records = records,
            persistedRows = rows.size,
            sellerSkuRows = rows.count { row -> row.items.any { it.sellerSku != null } },
            usableAmountDateRows = rows.size
        )
    }

    private fun readRow(c: java.sql.Connection, rs: ResultSet): MlRow {
        val organizationId = rs.getObject("connection_id")
        val items = mutableListOf<MlItem>()
        c.prepareStatement(
            "SELECT item_ref,seller_sku,quantity FROM integration_mercado_livre_order_item_source_observation " +
                "WHERE organization_id=? AND connection_id=? AND capability IN (?,?) AND input_progress_version=? " +
                "AND record_ordinal=? ORDER BY item_ordinal"
        ).use { s ->
            s.setObject(1, rs.getObject("organization_id") ?: error("organization column unavailable"))
            s.setObject(2, organizationId)
            s.setString(3, rs.getString("capability"))
            s.setString(4, rs.getString("capability"))
            s.setLong(5, rs.getLong("input_progress_version"))
            s.setInt(6, rs.getInt("record_ordinal"))
            s.executeQuery().use { itemRs -> while (itemRs.next()) items += MlItem(
                itemRs.getString("item_ref"), itemRs.getString("seller_sku")?.trim()?.takeIf { it.isNotEmpty() },
                itemRs.getBigDecimal("quantity")
            ) }
        }
        return MlRow(rs.getString("external_order_ref"), rs.getString("pack_ref"), rs.getString("shipping_ref"), rs.getTimestamp("date_created").toInstant(), rs.getString("currency").trim(), rs.getBigDecimal("total_amount"), rs.getTimestamp("observed_at").toInstant(), items)
    }

    private data class MlItem(val item: String, val sellerSku: String?, val quantity: BigDecimal)
    private data class MlRow(val order: String, val pack: String?, val shipping: String?, val occurred: Instant, val currency: String, val amount: BigDecimal, val observed: Instant, val items: List<MlItem>) {
        fun toDomain(org: OrganizationId) = MercadoLivreTransactionEvidence(
            org, order, pack, shipping, items.map { it.item }.toSet(), items.mapNotNull { it.sellerSku }.toSet(),
            items.mapNotNull { item -> item.sellerSku?.let { it to item.quantity } }.toMap(),
            CommerceIdentityAmount(currency, amount), occurred, setOf("mercadolivre:$order:$observed")
        )
    }
}
