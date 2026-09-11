package io.flooow.integration.credential

import io.flooow.integration.control.ActiveCredentialContext
import io.flooow.integration.control.CredentialRotationResult
import io.flooow.integration.control.IntegrationControlPlaneService
import java.time.Clock
import java.time.Duration

interface CredentialRotationCredentialAccess {
    fun activeContext(
        organizationId: io.flooow.organization.OrganizationId,
        connectionId: io.flooow.integration.control.IntegrationConnectionId
    ): ActiveCredentialContext?

    fun <T> withActiveCredentialContext(
        organizationId: io.flooow.organization.OrganizationId,
        connectionId: io.flooow.integration.control.IntegrationConnectionId,
        operation: (ActiveCredentialContext, ByteArray) -> T
    ): T

    fun rotate(
        organizationId: io.flooow.organization.OrganizationId,
        connectionId: io.flooow.integration.control.IntegrationConnectionId,
        expectedVersion: Int,
        replacementBytes: ByteArray
    ): CredentialRotationResult
}

class IntegrationControlPlaneCredentialRotationAccess(
    private val controlPlane: IntegrationControlPlaneService
) : CredentialRotationCredentialAccess {
    override fun activeContext(
        organizationId: io.flooow.organization.OrganizationId,
        connectionId: io.flooow.integration.control.IntegrationConnectionId
    ): ActiveCredentialContext? = controlPlane.activeCredentialContext(organizationId, connectionId)

    override fun <T> withActiveCredentialContext(
        organizationId: io.flooow.organization.OrganizationId,
        connectionId: io.flooow.integration.control.IntegrationConnectionId,
        operation: (ActiveCredentialContext, ByteArray) -> T
    ): T = controlPlane.withActiveCredentialContext(organizationId, connectionId, operation)

    override fun rotate(
        organizationId: io.flooow.organization.OrganizationId,
        connectionId: io.flooow.integration.control.IntegrationConnectionId,
        expectedVersion: Int,
        replacementBytes: ByteArray
    ): CredentialRotationResult = controlPlane.rotateCredential(
        organizationId, connectionId, expectedVersion, replacementBytes
    )
}

class CredentialRotationExecutor(
    private val credentials: CredentialRotationCredentialAccess,
    private val store: CredentialRotationExecutionStore,
    rotators: Collection<CredentialRotator>,
    private val clock: Clock = Clock.systemUTC(),
    private val claimLease: Duration = Duration.ofSeconds(30)
) {
    private val registry = RotatorRegistry(rotators)

    init {
        require(!claimLease.isNegative && !claimLease.isZero && claimLease <= Duration.ofMinutes(1))
    }

    fun execute(
        invocation: CredentialRotationInvocation,
        cancellation: CredentialRotationCancellation = CredentialRotationCancellation.NEVER
    ): CredentialRotationOutcome = try {
        executeControlled(invocation, cancellation)
    } catch (_: Exception) {
        CredentialRotationOutcome.Failure(CredentialRotationFailureKind.INTERNAL, null)
    }

    private fun executeControlled(
        invocation: CredentialRotationInvocation,
        cancellation: CredentialRotationCancellation
    ): CredentialRotationOutcome {
        gate(invocation, cancellation, null)?.let { return it }
        val metadata = try {
            credentials.activeContext(invocation.organizationId, invocation.connectionId)
        } catch (_: Exception) {
            return fail(CredentialRotationFailureKind.INTERNAL, null)
        } ?: return fail(CredentialRotationFailureKind.CONNECTION_UNAVAILABLE, null)

        val rotator = registry.resolve(metadata)
            ?: return fail(CredentialRotationFailureKind.ROTATOR_UNAVAILABLE, metadata)
        gate(invocation, cancellation, metadata)?.let { return it }

        var remoteStarted = false
        return try {
            credentials.withActiveCredentialContext(
                invocation.organizationId,
                invocation.connectionId
            ) { active, bytes ->
                if (active != metadata) {
                    return@withActiveCredentialContext fail(
                        CredentialRotationFailureKind.CREDENTIAL_VERSION_CHANGED, metadata
                    )
                }
                gate(invocation, cancellation, metadata)?.let {
                    return@withActiveCredentialContext it
                }
                when (try { rotator.assess(bytes, clock.instant()) } catch (_: Exception) {
                    return@withActiveCredentialContext fail(CredentialRotationFailureKind.INTERNAL, metadata)
                }) {
                    CredentialRotationAssessment.USABLE ->
                        CredentialRotationOutcome.Success(CredentialRotationSuccessKind.READY, metadata.providerKey)
                    CredentialRotationAssessment.AUTHENTICATION_REQUIRED ->
                        fail(CredentialRotationFailureKind.AUTHENTICATION_REQUIRED, metadata)
                    CredentialRotationAssessment.REFRESH_REQUIRED -> {
                        val now = clock.instant()
                        val leaseEnd = minOf(now.plus(claimLease), invocation.deadline)
                        if (!leaseEnd.isAfter(now)) {
                            return@withActiveCredentialContext fail(CredentialRotationFailureKind.BUDGET_EXCEEDED, metadata)
                        }
                        val claim = try {
                            store.claim(
                                invocation.organizationId, invocation.connectionId, metadata.bindingVersion,
                                invocation.executionId, now, leaseEnd
                            )
                        } catch (_: Exception) {
                            return@withActiveCredentialContext fail(CredentialRotationFailureKind.INTERNAL, metadata)
                        }
                        claimFailure(claim, metadata)?.let { return@withActiveCredentialContext it }
                        gate(invocation, cancellation, metadata)?.let { return@withActiveCredentialContext it }
                        val start = try {
                            store.markRemoteStarted(
                                invocation.organizationId, invocation.connectionId, metadata.bindingVersion,
                                invocation.executionId, clock.instant()
                            )
                        } catch (_: Exception) {
                            return@withActiveCredentialContext fail(CredentialRotationFailureKind.INTERNAL, metadata)
                        }
                        startFailure(start, metadata)?.let { return@withActiveCredentialContext it }
                        remoteStarted = true
                        val result = try {
                            rotator.refresh(bytes, CredentialRotationRemoteContext(invocation.deadline), cancellation)
                        } catch (_: Exception) {
                            markInDoubt(invocation, metadata)
                            return@withActiveCredentialContext fail(CredentialRotationFailureKind.ROTATION_IN_DOUBT, metadata)
                        }
                        handleResult(invocation, metadata, result)
                    }
                }
            }
        } catch (_: Exception) {
            if (remoteStarted) {
                markInDoubt(invocation, metadata)
                fail(CredentialRotationFailureKind.ROTATION_IN_DOUBT, metadata)
            } else {
                fail(CredentialRotationFailureKind.CONNECTION_UNAVAILABLE, metadata)
            }
        }
    }

    private fun handleResult(
        invocation: CredentialRotationInvocation,
        context: ActiveCredentialContext,
        result: CredentialRefreshResult
    ): CredentialRotationOutcome = when (result) {
        is CredentialRefreshResult.Replacement -> result.credential.use { replacement ->
            val rotated = try {
                replacement.useBytes { bytes ->
                    credentials.rotate(
                        invocation.organizationId, invocation.connectionId,
                        context.bindingVersion, bytes
                    )
                }
            } catch (_: Exception) {
                markInDoubt(invocation, context)
                return@use fail(CredentialRotationFailureKind.ROTATION_IN_DOUBT, context)
            }
            if (!markCompleted(invocation, context)) {
                markInDoubt(invocation, context)
                return@use fail(CredentialRotationFailureKind.ROTATION_IN_DOUBT, context)
            }
            when (rotated) {
                CredentialRotationResult.ROTATED -> CredentialRotationOutcome.Success(
                    CredentialRotationSuccessKind.ROTATED, context.providerKey
                )
                CredentialRotationResult.ROTATED_CLEANUP_REQUIRED -> CredentialRotationOutcome.Success(
                    CredentialRotationSuccessKind.ROTATED_CLEANUP_REQUIRED, context.providerKey
                )
                CredentialRotationResult.STALE_VERSION ->
                    fail(CredentialRotationFailureKind.CREDENTIAL_VERSION_CHANGED, context)
            }
        }
        is CredentialRefreshResult.RetryableFailure -> {
            val now = clock.instant()
            val marked = try {
                store.markRetryable(
                    invocation.organizationId, invocation.connectionId, context.bindingVersion,
                    invocation.executionId, now.plus(result.retryAfter), now
                )
            } catch (_: Exception) { false }
            if (!marked) {
                markInDoubt(invocation, context)
                fail(CredentialRotationFailureKind.ROTATION_IN_DOUBT, context)
            } else fail(result.kind.toFailure(), context, result.retryAfter)
        }
        is CredentialRefreshResult.TerminalFailure -> {
            if (!markCompleted(invocation, context)) {
                markInDoubt(invocation, context)
                fail(CredentialRotationFailureKind.ROTATION_IN_DOUBT, context)
            } else fail(result.kind.toFailure(), context)
        }
        CredentialRefreshResult.Indeterminate -> {
            markInDoubt(invocation, context)
            fail(CredentialRotationFailureKind.ROTATION_IN_DOUBT, context)
        }
    }

    private fun markCompleted(i: CredentialRotationInvocation, c: ActiveCredentialContext): Boolean = try {
        store.markCompleted(i.organizationId, i.connectionId, c.bindingVersion, i.executionId, clock.instant())
    } catch (_: Exception) { false }

    private fun markInDoubt(i: CredentialRotationInvocation, c: ActiveCredentialContext) {
        try { store.markInDoubt(i.organizationId, i.connectionId, c.bindingVersion, i.executionId, clock.instant()) }
        catch (_: Exception) { }
    }

    private fun claimFailure(r: CredentialRotationClaimResult, c: ActiveCredentialContext): CredentialRotationOutcome.Failure? = when (r.kind) {
        CredentialRotationClaimKind.ACQUIRED -> null
        CredentialRotationClaimKind.BUSY -> fail(CredentialRotationFailureKind.ROTATION_IN_PROGRESS, c, r.retryAfter)
        CredentialRotationClaimKind.STALE_VERSION, CredentialRotationClaimKind.ALREADY_COMPLETED ->
            fail(CredentialRotationFailureKind.CREDENTIAL_VERSION_CHANGED, c)
        CredentialRotationClaimKind.CONNECTION_UNAVAILABLE -> fail(CredentialRotationFailureKind.CONNECTION_UNAVAILABLE, c)
        CredentialRotationClaimKind.IN_DOUBT -> fail(CredentialRotationFailureKind.ROTATION_IN_DOUBT, c)
    }

    private fun startFailure(r: CredentialRotationRemoteStartResult, c: ActiveCredentialContext): CredentialRotationOutcome.Failure? = when (r) {
        CredentialRotationRemoteStartResult.STARTED -> null
        CredentialRotationRemoteStartResult.NOT_OWNER -> fail(CredentialRotationFailureKind.ROTATION_IN_PROGRESS, c)
        CredentialRotationRemoteStartResult.STALE_VERSION -> fail(CredentialRotationFailureKind.CREDENTIAL_VERSION_CHANGED, c)
        CredentialRotationRemoteStartResult.CONNECTION_UNAVAILABLE -> fail(CredentialRotationFailureKind.CONNECTION_UNAVAILABLE, c)
        CredentialRotationRemoteStartResult.IN_DOUBT -> fail(CredentialRotationFailureKind.ROTATION_IN_DOUBT, c)
    }

    private fun gate(
        i: CredentialRotationInvocation,
        cancellation: CredentialRotationCancellation,
        c: ActiveCredentialContext?
    ): CredentialRotationOutcome.Failure? {
        val now = clock.instant()
        return when {
            cancellation.isCancelled() -> fail(CredentialRotationFailureKind.CANCELLED, c)
            !now.isBefore(i.deadline) -> fail(CredentialRotationFailureKind.BUDGET_EXCEEDED, c)
            i.deadline > now.plus(Duration.ofMinutes(5)) -> fail(CredentialRotationFailureKind.BUDGET_EXCEEDED, c)
            else -> null
        }
    }

    private fun fail(
        kind: CredentialRotationFailureKind,
        c: ActiveCredentialContext?,
        retryAfter: Duration? = null
    ) = CredentialRotationOutcome.Failure(kind, c?.providerKey, retryAfter)
}

private class RotatorRegistry(rotators: Collection<CredentialRotator>) {
    private val values = rotators.associateBy { it.descriptor.providerKey to it.descriptor.credentialKind }
    init { require(values.size == rotators.size) { "Credential rotator registered twice" } }
    fun resolve(c: ActiveCredentialContext) = values[c.providerKey to c.credentialKind]
}

private fun CredentialRotationRemoteFailureKind.toFailure() = when (this) {
    CredentialRotationRemoteFailureKind.AUTHENTICATION_REQUIRED -> CredentialRotationFailureKind.AUTHENTICATION_REQUIRED
    CredentialRotationRemoteFailureKind.AUTHORIZATION_DENIED -> CredentialRotationFailureKind.AUTHORIZATION_DENIED
    CredentialRotationRemoteFailureKind.RATE_LIMITED -> CredentialRotationFailureKind.RATE_LIMITED
    CredentialRotationRemoteFailureKind.REMOTE_TEMPORARY -> CredentialRotationFailureKind.REMOTE_TEMPORARY
    CredentialRotationRemoteFailureKind.REMOTE_PERMANENT -> CredentialRotationFailureKind.REMOTE_PERMANENT
    CredentialRotationRemoteFailureKind.REMOTE_DATA_INVALID -> CredentialRotationFailureKind.REMOTE_DATA_INVALID
}