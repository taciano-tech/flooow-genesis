package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.connector.*
import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.marketplace.operations.economics.provider.*
import io.flooow.organization.OrganizationId
import java.sql.Connection
import java.sql.Timestamp
import java.time.Clock
import java.time.Instant
import kotlin.reflect.KClass

class PostgresOmieTransactionEvidenceCommitter(configuration: PostgresConfiguration, protector: ConnectorProgressProtector, clock: Clock = Clock.systemUTC(), override val capability: ConnectorCapability = OmieTransactionEvidenceCapability.KEY) : ConnectorPageCommitter {
    private val progress = PostgresConnectorProgressStore(configuration, protector, clock)
    override val recordType: KClass<out ConnectorRecord> = OmieTransactionEvidenceRecord::class
    override fun load(organizationId: OrganizationId, connectionId: IntegrationConnectionId, capability: ConnectorCapability): VersionedConnectorProgress { require(capability == this.capability); return progress.load(organizationId, connectionId, capability) }
    override fun commit(organizationId: OrganizationId, connectionId: IntegrationConnectionId, capability: ConnectorCapability, expectedProgressVersion: Long, pageCommitKey: ConnectorPageCommitKey, records: List<ConnectorRecord>, nextProgress: ConnectorProgress?, exhausted: Boolean, observedAt: Instant): ConnectorPageCommitResult {
        require(capability == this.capability)
        val typed = records.map { require(it is OmieTransactionEvidenceRecord); it }
        require(typed.all { it.observedAt == observedAt })
        return progress.commitPage(organizationId, connectionId, capability, expectedProgressVersion, pageCommitKey, typed.size, nextProgress, exhausted, observedAt,
            persistRecords = { c -> typed.forEachIndexed { i, r -> insert(c, organizationId, connectionId, capability, expectedProgressVersion, i, r) } },
            validateExistingRecords = { c -> c.prepareStatement("SELECT source_order_ref,source_fingerprint FROM integration_omie_transaction_evidence WHERE organization_id=? AND connection_id=? AND capability=? AND input_progress_version=? ORDER BY record_ordinal").use { s -> s.setObject(1, organizationId.value); s.setObject(2, connectionId.value); s.setString(3, capability.value); s.setLong(4, expectedProgressVersion); s.executeQuery().use { rs -> var i=0; while(rs.next()){ check(i<typed.size && rs.getString(1)==typed[i].orderReference.encodedForPersistence() && rs.getString(2)==typed[i].sourceFingerprint); i++ }; check(i==typed.size) } } })
    }
    private fun insert(c: Connection, o: OrganizationId, id: IntegrationConnectionId, cap: ConnectorCapability, v: Long, ordinal: Int, r: OmieTransactionEvidenceRecord) {
        c.prepareStatement("INSERT INTO integration_omie_transaction_evidence (organization_id,connection_id,capability,input_progress_version,record_ordinal,source_order_ref,source_integration_ref,source_customer_order_ref,occurred_at,source_status,currency,total_amount,product_refs,observed_at,source_fingerprint) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?)").use { s ->
            s.setObject(1,o.value); s.setObject(2,id.value); s.setString(3,cap.value); s.setLong(4,v); s.setInt(5,ordinal); s.setString(6,r.orderReference.encodedForPersistence()); s.setString(7,r.integrationOrderReference?.encodedForPersistence()); s.setString(8,r.customerOrderReference?.encodedForPersistence()); s.setTimestamp(9,r.occurredAt?.let(Timestamp::from)); s.setString(10,r.status?.encodedForPersistence()); s.setString(11,r.currency?.encodedForPersistence()); s.setBigDecimal(12,r.totalAmount?.valueForPersistence()); s.setString(13,OmieProductRefsJson.encode(r.products)); s.setTimestamp(14,Timestamp.from(r.observedAt)); s.setString(15,r.sourceFingerprint); check(s.executeUpdate()==1)
        }
    }
}
