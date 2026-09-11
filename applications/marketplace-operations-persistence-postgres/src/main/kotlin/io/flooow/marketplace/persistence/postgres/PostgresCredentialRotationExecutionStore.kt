package io.flooow.marketplace.persistence.postgres

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.credential.CredentialRotationClaimKind
import io.flooow.integration.credential.CredentialRotationClaimResult
import io.flooow.integration.credential.CredentialRotationExecutionId
import io.flooow.integration.credential.CredentialRotationExecutionState
import io.flooow.integration.credential.CredentialRotationExecutionStore
import io.flooow.integration.credential.CredentialRotationRemoteStartResult
import io.flooow.organization.OrganizationId
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Timestamp
import java.time.Duration
import java.time.Instant
import java.util.UUID

class PostgresCredentialRotationExecutionStore(
    private val configuration: PostgresConfiguration
) : CredentialRotationExecutionStore {

    override fun claim(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        bindingVersion: Int,
        executionId: CredentialRotationExecutionId,
        claimedAt: Instant,
        leaseExpiresAt: Instant
    ): CredentialRotationClaimResult {
        require(bindingVersion > 0)
        require(leaseExpiresAt.isAfter(claimedAt))
        return transaction { c ->
            when (validateBinding(c, organizationId, connectionId, bindingVersion)) {
                BindingState.UNAVAILABLE -> return@transaction result(CredentialRotationClaimKind.CONNECTION_UNAVAILABLE)
                BindingState.STALE -> return@transaction result(CredentialRotationClaimKind.STALE_VERSION)
                BindingState.CURRENT -> Unit
            }
            if (
                tryInsertClaim(
                    c,
                    organizationId,
                    connectionId,
                    bindingVersion,
                    executionId,
                    claimedAt,
                    leaseExpiresAt
                )
            ) {
                return@transaction result(CredentialRotationClaimKind.ACQUIRED)
            }

            val row = requireNotNull(
                lockRow(c, organizationId, connectionId, bindingVersion)
            ) {
                "Credential rotation claim conflict did not produce a durable fence"
            }

            when (row.state) {
                CredentialRotationExecutionState.CLAIMED -> {
                    if (row.executionId == executionId.value || !claimedAt.isBefore(row.leaseExpiresAt)) {
                        replaceClaim(c, organizationId, connectionId, bindingVersion, executionId, claimedAt, leaseExpiresAt)
                        result(CredentialRotationClaimKind.ACQUIRED)
                    } else busy(claimedAt, row.leaseExpiresAt)
                }
                CredentialRotationExecutionState.REMOTE_STARTED -> {
                    if (!claimedAt.isBefore(row.leaseExpiresAt)) {
                        settleInDoubt(c, organizationId, connectionId, bindingVersion, claimedAt)
                        result(CredentialRotationClaimKind.IN_DOUBT)
                    } else busy(claimedAt, row.leaseExpiresAt)
                }
                CredentialRotationExecutionState.RETRYABLE -> {
                    val retryAt=requireNotNull(row.retryNotBefore)
                    if (!claimedAt.isBefore(retryAt)) {
                        replaceClaim(c, organizationId, connectionId, bindingVersion, executionId, claimedAt, leaseExpiresAt)
                        result(CredentialRotationClaimKind.ACQUIRED)
                    } else busy(claimedAt,retryAt)
                }
                CredentialRotationExecutionState.COMPLETED -> result(CredentialRotationClaimKind.ALREADY_COMPLETED)
                CredentialRotationExecutionState.IN_DOUBT -> result(CredentialRotationClaimKind.IN_DOUBT)
            }
        }
    }

    override fun markRemoteStarted(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        bindingVersion: Int,
        executionId: CredentialRotationExecutionId,
        startedAt: Instant
    ): CredentialRotationRemoteStartResult = transaction { c ->
        val row=lockRow(c,organizationId,connectionId,bindingVersion)
            ?: return@transaction CredentialRotationRemoteStartResult.NOT_OWNER
        if (row.state == CredentialRotationExecutionState.IN_DOUBT)
            return@transaction CredentialRotationRemoteStartResult.IN_DOUBT
        if (row.state != CredentialRotationExecutionState.CLAIMED || row.executionId != executionId.value ||
            !startedAt.isBefore(row.leaseExpiresAt))
            return@transaction CredentialRotationRemoteStartResult.NOT_OWNER
        when (validateBinding(c,organizationId,connectionId,bindingVersion)) {
            BindingState.UNAVAILABLE -> return@transaction CredentialRotationRemoteStartResult.CONNECTION_UNAVAILABLE
            BindingState.STALE -> {
                completeClaim(c,organizationId,connectionId,bindingVersion,executionId,startedAt)
                return@transaction CredentialRotationRemoteStartResult.STALE_VERSION
            }
            BindingState.CURRENT -> Unit
        }
        val updated=c.prepareStatement(
            "UPDATE integration_credential_rotation_execution SET state='REMOTE_STARTED',"+
                "remote_started_at=?,retry_not_before=NULL,terminal_at=NULL,updated_at=? "+
                "WHERE organization_id=? AND connection_id=? AND binding_version=? AND execution_id=? AND state='CLAIMED'"
        ).use { s ->
            s.setTimestamp(1,Timestamp.from(startedAt)); s.setTimestamp(2,Timestamp.from(startedAt))
            s.setObject(3,organizationId.value); s.setObject(4,connectionId.value); s.setInt(5,bindingVersion); s.setObject(6,executionId.value)
            s.executeUpdate()==1
        }
        if (updated) CredentialRotationRemoteStartResult.STARTED else CredentialRotationRemoteStartResult.NOT_OWNER
    }

    override fun markRetryable(
        organizationId: OrganizationId, connectionId: IntegrationConnectionId, bindingVersion: Int,
        executionId: CredentialRotationExecutionId, retryNotBefore: Instant, updatedAt: Instant
    ): Boolean {
        require(retryNotBefore.isAfter(updatedAt))
        return updateOwned(
            organizationId,connectionId,bindingVersion,executionId,
            "UPDATE integration_credential_rotation_execution SET state='RETRYABLE',retry_not_before=?,terminal_at=NULL,updated_at=? "+
                "WHERE organization_id=? AND connection_id=? AND binding_version=? AND execution_id=? AND state='REMOTE_STARTED'"
        ) { s -> s.setTimestamp(1,Timestamp.from(retryNotBefore)); s.setTimestamp(2,Timestamp.from(updatedAt)) }
    }

    override fun markCompleted(
        organizationId: OrganizationId, connectionId: IntegrationConnectionId, bindingVersion: Int,
        executionId: CredentialRotationExecutionId, terminalAt: Instant
    ): Boolean = updateOwned(
        organizationId,connectionId,bindingVersion,executionId,
        "UPDATE integration_credential_rotation_execution SET state='COMPLETED',retry_not_before=NULL,terminal_at=?,updated_at=? "+
            "WHERE organization_id=? AND connection_id=? AND binding_version=? AND execution_id=? AND state IN ('CLAIMED','REMOTE_STARTED','RETRYABLE')"
    ) { s -> s.setTimestamp(1,Timestamp.from(terminalAt)); s.setTimestamp(2,Timestamp.from(terminalAt)) }

    override fun markInDoubt(
        organizationId: OrganizationId, connectionId: IntegrationConnectionId, bindingVersion: Int,
        executionId: CredentialRotationExecutionId, terminalAt: Instant
    ): Boolean = updateOwned(
        organizationId,connectionId,bindingVersion,executionId,
        "UPDATE integration_credential_rotation_execution SET state='IN_DOUBT',retry_not_before=NULL,terminal_at=?,updated_at=? "+
            "WHERE organization_id=? AND connection_id=? AND binding_version=? AND execution_id=? AND state='REMOTE_STARTED'"
    ) { s -> s.setTimestamp(1,Timestamp.from(terminalAt)); s.setTimestamp(2,Timestamp.from(terminalAt)) }

    private fun validateBinding(c: Connection, org: OrganizationId, id: IntegrationConnectionId, expected: Int): BindingState =
        c.prepareStatement(
            "SELECT c.binding_version FROM integration_organization o JOIN integration_connection c ON c.organization_id=o.organization_id "+
                "JOIN integration_credential_binding b ON b.organization_id=c.organization_id AND b.connection_id=c.connection_id "+
                "AND b.binding_version=c.binding_version AND b.revoked_at IS NULL WHERE o.organization_id=? AND c.connection_id=? "+
                "AND o.status='ACTIVE' AND c.status='ACTIVE' FOR SHARE OF o,c,b"
        ).use { s ->
            s.setObject(1,org.value); s.setObject(2,id.value)
            s.executeQuery().use { r -> if (!r.next()) BindingState.UNAVAILABLE else if (r.getInt(1)==expected) BindingState.CURRENT else BindingState.STALE }
        }

    private fun lockRow(c: Connection, org: OrganizationId, id: IntegrationConnectionId, version: Int): Row? =
        c.prepareStatement(
            "SELECT execution_id,state,lease_expires_at,retry_not_before FROM integration_credential_rotation_execution "+
                "WHERE organization_id=? AND connection_id=? AND binding_version=? FOR UPDATE"
        ).use { s ->
            s.setObject(1,org.value); s.setObject(2,id.value); s.setInt(3,version)
            s.executeQuery().use { r -> if (!r.next()) null else Row(
                r.getObject("execution_id",UUID::class.java), CredentialRotationExecutionState.valueOf(r.getString("state")),
                r.getTimestamp("lease_expires_at").toInstant(), r.getTimestamp("retry_not_before")?.toInstant()
            ) }
        }

    private fun tryInsertClaim(
        c: Connection,
        org: OrganizationId,
        id: IntegrationConnectionId,
        version: Int,
        execution: CredentialRotationExecutionId,
        now: Instant,
        lease: Instant
    ): Boolean = c.prepareStatement(
        "INSERT INTO integration_credential_rotation_execution " +
            "(organization_id,connection_id,binding_version,execution_id,state," +
            "claimed_at,lease_expires_at,remote_started_at,retry_not_before,terminal_at,updated_at) " +
            "VALUES (?,?,?,?,'CLAIMED',?,?,NULL,NULL,NULL,?) " +
            "ON CONFLICT (organization_id,connection_id,binding_version) DO NOTHING"
    ).use { s ->
        s.setObject(1,org.value)
        s.setObject(2,id.value)
        s.setInt(3,version)
        s.setObject(4,execution.value)
        s.setTimestamp(5,Timestamp.from(now))
        s.setTimestamp(6,Timestamp.from(lease))
        s.setTimestamp(7,Timestamp.from(now))
        s.executeUpdate() == 1
    }

    private fun replaceClaim(c: Connection, org: OrganizationId, id: IntegrationConnectionId, version: Int,
        execution: CredentialRotationExecutionId, now: Instant, lease: Instant) {
        c.prepareStatement(
            "UPDATE integration_credential_rotation_execution SET execution_id=?,state='CLAIMED',claimed_at=?,lease_expires_at=?,"+
                "remote_started_at=NULL,retry_not_before=NULL,terminal_at=NULL,updated_at=? WHERE organization_id=? AND connection_id=? AND binding_version=?"
        ).use { s ->
            s.setObject(1,execution.value); s.setTimestamp(2,Timestamp.from(now)); s.setTimestamp(3,Timestamp.from(lease)); s.setTimestamp(4,Timestamp.from(now))
            s.setObject(5,org.value); s.setObject(6,id.value); s.setInt(7,version); check(s.executeUpdate()==1)
        }
    }

    private fun settleInDoubt(c: Connection, org: OrganizationId, id: IntegrationConnectionId, version: Int, now: Instant) {
        c.prepareStatement(
            "UPDATE integration_credential_rotation_execution SET state='IN_DOUBT',retry_not_before=NULL,terminal_at=?,updated_at=? "+
                "WHERE organization_id=? AND connection_id=? AND binding_version=? AND state='REMOTE_STARTED'"
        ).use { s ->
            s.setTimestamp(1,Timestamp.from(now)); s.setTimestamp(2,Timestamp.from(now)); s.setObject(3,org.value); s.setObject(4,id.value); s.setInt(5,version)
            check(s.executeUpdate()==1)
        }
    }

    private fun completeClaim(c: Connection, org: OrganizationId, id: IntegrationConnectionId, version: Int,
        execution: CredentialRotationExecutionId, now: Instant) {
        c.prepareStatement(
            "UPDATE integration_credential_rotation_execution SET state='COMPLETED',retry_not_before=NULL,terminal_at=?,updated_at=? "+
                "WHERE organization_id=? AND connection_id=? AND binding_version=? AND execution_id=? AND state='CLAIMED'"
        ).use { s ->
            s.setTimestamp(1,Timestamp.from(now)); s.setTimestamp(2,Timestamp.from(now)); s.setObject(3,org.value); s.setObject(4,id.value)
            s.setInt(5,version); s.setObject(6,execution.value); s.executeUpdate()
        }
    }

    private fun updateOwned(org: OrganizationId,id: IntegrationConnectionId,version: Int,execution: CredentialRotationExecutionId,
        sql: String,prefix:(java.sql.PreparedStatement)->Unit):Boolean = connection().use { c ->
        c.prepareStatement(sql).use { s -> prefix(s); s.setObject(3,org.value); s.setObject(4,id.value); s.setInt(5,version); s.setObject(6,execution.value); s.executeUpdate()==1 }
    }

    private fun busy(now: Instant, until: Instant): CredentialRotationClaimResult {
        var d=Duration.between(now,until); if (d <= Duration.ZERO) d=Duration.ofMillis(1); if (d > Duration.ofHours(1)) d=Duration.ofHours(1)
        return CredentialRotationClaimResult(CredentialRotationClaimKind.BUSY,d)
    }
    private fun result(k: CredentialRotationClaimKind)=CredentialRotationClaimResult(k)
    private fun connection()=DriverManager.getConnection(configuration.url,configuration.user,configuration.password)
    private fun <T> transaction(block:(Connection)->T):T=connection().use { c -> c.autoCommit=false; try { block(c).also { c.commit() } } catch(e:Exception){ c.rollback(); throw e } }
    private data class Row(val executionId:UUID,val state:CredentialRotationExecutionState,val leaseExpiresAt:Instant,val retryNotBefore:Instant?)
    private enum class BindingState { CURRENT, STALE, UNAVAILABLE }
}