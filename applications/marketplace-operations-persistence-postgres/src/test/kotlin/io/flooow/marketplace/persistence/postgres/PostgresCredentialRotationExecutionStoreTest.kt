package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.control.CredentialKind
import io.flooow.integration.control.IdentifierFactory
import io.flooow.integration.control.IntegrationAuditEntryId
import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.control.IntegrationControlPlaneService
import io.flooow.integration.control.ProviderKey
import io.flooow.integration.control.SecretReference
import io.flooow.integration.control.SecretVault
import io.flooow.integration.credential.CredentialRotationClaimKind
import io.flooow.integration.credential.CredentialRotationExecutionId
import io.flooow.integration.credential.CredentialRotationRemoteStartResult
import io.flooow.organization.OrganizationId
import java.sql.DriverManager
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.testcontainers.postgresql.PostgreSQLContainer
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PostgresCredentialRotationExecutionStoreTest {
    private val now=Instant.parse("2026-09-06T18:00:00Z")
    private val clock=Clock.fixed(now,ZoneOffset.UTC)
    private lateinit var postgres:PostgreSQLContainer
    private lateinit var config:PostgresConfiguration
    private lateinit var control:IntegrationControlPlaneService
    private lateinit var store:PostgresCredentialRotationExecutionStore
    private var seq=100L

    @BeforeTest fun start() {
        postgres=PostgreSQLContainer("postgres:18.4"); postgres.start()
        config=PostgresConfiguration(postgres.jdbcUrl,postgres.username,postgres.password)
        val repo=PostgresIntegrationControlPlaneRepository.connect(config)
        control=IntegrationControlPlaneService(
            repo, TestVault(), clock,
            organizationIds=IdentifierFactory { OrganizationId(UUID(0,seq++)) },
            connectionIds=IdentifierFactory { IntegrationConnectionId(UUID(1,seq++)) },
            auditIds=IdentifierFactory { IntegrationAuditEntryId(UUID(2,seq++)) },
            correlationIds=IdentifierFactory { UUID(3,seq++) }
        )
        store=PostgresCredentialRotationExecutionStore(config)
    }
    @AfterTest fun stop(){ postgres.stop() }

    @Test fun `concurrent first claims converge to one owner`() {
        val a=active(); val pool=Executors.newFixedThreadPool(2)
        try {
            val ids=listOf(exec(),exec())
            val kinds=pool.invokeAll(ids.map { id -> Callable {
                store.claim(a.first,a.second,1,id,now,now.plusSeconds(30)).kind
            }}).map { it.get() }
            assertEquals(1,kinds.count { it==CredentialRotationClaimKind.ACQUIRED })
            assertEquals(1,kinds.count { it==CredentialRotationClaimKind.BUSY })
            assertEquals(1,count())
        } finally { pool.shutdownNow() }
    }

    @Test fun `expired claimed is reclaimable but expired remote started becomes in doubt`() {
        val a=active(); val first=exec(); val second=exec()
        assertEquals(CredentialRotationClaimKind.ACQUIRED,
            store.claim(a.first,a.second,1,first,now,now.plusSeconds(1)).kind)
        assertEquals(CredentialRotationClaimKind.ACQUIRED,
            store.claim(a.first,a.second,1,second,now.plusSeconds(2),now.plusSeconds(32)).kind)
        assertEquals(second.value,currentExecution())

        store.markRemoteStarted(a.first,a.second,1,second,now.plusSeconds(3))
        val third=store.claim(a.first,a.second,1,exec(),now.plusSeconds(40),now.plusSeconds(70))
        assertEquals(CredentialRotationClaimKind.IN_DOUBT,third.kind)
        assertEquals("IN_DOUBT",state())
        val again=store.claim(a.first,a.second,1,exec(),now.plusSeconds(41),now.plusSeconds(71))
        assertEquals(CredentialRotationClaimKind.IN_DOUBT,again.kind)
    }

    @Test fun `retryable respects not before`() {
        val a=active(); val first=exec(); val second=exec()
        store.claim(a.first,a.second,1,first,now,now.plusSeconds(30))
        assertEquals(CredentialRotationRemoteStartResult.STARTED,
            store.markRemoteStarted(a.first,a.second,1,first,now.plusSeconds(1)))
        assertTrue(store.markRetryable(a.first,a.second,1,first,now.plusSeconds(20),now.plusSeconds(2)))
        val early=store.claim(a.first,a.second,1,second,now.plusSeconds(10),now.plusSeconds(40))
        assertEquals(CredentialRotationClaimKind.BUSY,early.kind); assertEquals(Duration.ofSeconds(10),early.retryAfter)
        val ready=store.claim(a.first,a.second,1,second,now.plusSeconds(20),now.plusSeconds(50))
        assertEquals(CredentialRotationClaimKind.ACQUIRED,ready.kind); assertEquals("CLAIMED",state())
    }

    @Test fun `stale binding cannot cross remote started`() {
        val a=active(); val id=exec()
        store.claim(a.first,a.second,1,id,now,now.plusSeconds(30))
        control.rotateCredential(a.first,a.second,1,"new-binding-marker".toByteArray())
        assertEquals(CredentialRotationRemoteStartResult.STALE_VERSION,
            store.markRemoteStarted(a.first,a.second,1,id,now.plusSeconds(1)))
        assertEquals("COMPLETED",state())
    }

    @Test fun `organization isolation and restart preserve fence without secret material`() {
        val a=active("private-credential-marker"); val other=control.createOrganization(); val id=exec()
        val foreign=store.claim(other.id,a.second,1,exec(),now,now.plusSeconds(30))
        assertEquals(CredentialRotationClaimKind.CONNECTION_UNAVAILABLE,foreign.kind)
        assertEquals(0,count())
        store.claim(a.first,a.second,1,id,now,now.plusSeconds(30))
        store.markRemoteStarted(a.first,a.second,1,id,now.plusSeconds(1))
        val restarted=PostgresCredentialRotationExecutionStore(config)
        assertEquals(CredentialRotationClaimKind.BUSY,
            restarted.claim(a.first,a.second,1,exec(),now.plusSeconds(2),now.plusSeconds(32)).kind)
        val row=connection().use { c -> c.createStatement().use { s ->
            s.executeQuery("SELECT row_to_json(r)::text FROM integration_credential_rotation_execution r").use { r -> r.next(); r.getString(1) }
        }}
        assertFalse(row.contains("private-credential-marker")); assertFalse(row.contains("vault://")); assertTrue(row.contains("REMOTE_STARTED"))
    }

    private fun active(secret:String="synthetic-current"):Pair<OrganizationId,IntegrationConnectionId> {
        val o=control.createOrganization(); val c=control.createConnection(o.id,ProviderKey.of("test.oauth"),CredentialKind.OAUTH2_AUTHORIZATION_CODE)
        control.bindInitialCredential(o.id,c.id,secret.toByteArray()); return o.id to c.id
    }
    private fun exec()=CredentialRotationExecutionId(UUID.randomUUID())
    private fun count()=connection().use { c -> c.createStatement().use { s -> s.executeQuery("SELECT count(*) FROM integration_credential_rotation_execution").use { r -> r.next(); r.getInt(1) } } }
    private fun state()=connection().use { c -> c.createStatement().use { s -> s.executeQuery("SELECT state FROM integration_credential_rotation_execution").use { r -> r.next(); r.getString(1) } } }
    private fun currentExecution()=connection().use { c -> c.createStatement().use { s -> s.executeQuery("SELECT execution_id FROM integration_credential_rotation_execution").use { r -> r.next(); r.getObject(1,UUID::class.java) } } }
    private fun connection()=DriverManager.getConnection(config.url,config.user,config.password)
}

private class TestVault:SecretVault {
    private val data=mutableMapOf<SecretReference,ByteArray>(); private var seq=0
    override fun store(organizationId:OrganizationId,connectionId:IntegrationConnectionId,credentialBytes:ByteArray):SecretReference=try {
        SecretReference.of("vault://rotation/${++seq}").also { data[it]=credentialBytes.copyOf() }
    } finally { credentialBytes.fill(0) }
    override fun <T> withSecret(organizationId:OrganizationId,connectionId:IntegrationConnectionId,reference:SecretReference,operation:(ByteArray)->T):T {
        val b=requireNotNull(data[reference]).copyOf(); return try { operation(b) } finally { b.fill(0) }
    }
    override fun revoke(organizationId:OrganizationId,connectionId:IntegrationConnectionId,reference:SecretReference) { data.remove(reference)?.fill(0) }
}