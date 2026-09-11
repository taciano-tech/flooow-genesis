package io.flooow.integration.credential

import io.flooow.integration.control.CredentialKind
import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.control.ProviderKey
import io.flooow.organization.OrganizationId
import java.time.Duration
import java.time.Instant
import java.util.UUID

@JvmInline
value class CredentialRotationExecutionId(val value: UUID) {
    companion object {
        fun parse(value: String): CredentialRotationExecutionId {
            val parsed = UUID.fromString(value)
            require(parsed.toString() == value) { "Invalid credential rotation execution identifier" }
            return CredentialRotationExecutionId(parsed)
        }
    }
}

data class CredentialRotationInvocation(
    val organizationId: OrganizationId,
    val connectionId: IntegrationConnectionId,
    val executionId: CredentialRotationExecutionId,
    val deadline: Instant
)

fun interface CredentialRotationCancellation {
    fun isCancelled(): Boolean
    companion object { val NEVER = CredentialRotationCancellation { false } }
}

enum class CredentialRotationAssessment { USABLE, REFRESH_REQUIRED, AUTHENTICATION_REQUIRED }

data class CredentialRotatorDescriptor(val providerKey: ProviderKey, val credentialKind: CredentialKind)
data class CredentialRotationRemoteContext(val deadline: Instant)

enum class CredentialRotationRemoteFailureKind {
    AUTHENTICATION_REQUIRED, AUTHORIZATION_DENIED, RATE_LIMITED,
    REMOTE_TEMPORARY, REMOTE_PERMANENT, REMOTE_DATA_INVALID
}

class ReplacementCredential private constructor(private val bytes: ByteArray) : AutoCloseable {
    fun <T> useBytes(operation: (ByteArray) -> T): T {
        val copy = bytes.copyOf()
        return try { operation(copy) } finally { copy.fill(0) }
    }
    override fun close() = bytes.fill(0)
    override fun toString() = "[REDACTED]"
    companion object {
        const val MAX_BYTES = 16_384
        fun take(ownedBytes: ByteArray): ReplacementCredential = try {
            require(ownedBytes.size in 1..MAX_BYTES) { "Invalid replacement credential" }
            ReplacementCredential(ownedBytes.copyOf())
        } finally { ownedBytes.fill(0) }
    }
}

sealed interface CredentialRefreshResult {
    class Replacement(val credential: ReplacementCredential) : CredentialRefreshResult {
        override fun toString() = "CredentialRefreshResult.Replacement([REDACTED])"
    }
    class RetryableFailure private constructor(
        val kind: CredentialRotationRemoteFailureKind,
        val retryAfter: Duration
    ) : CredentialRefreshResult {
        companion object {
            fun of(kind: CredentialRotationRemoteFailureKind, retryAfter: Duration): RetryableFailure {
                require(kind == CredentialRotationRemoteFailureKind.RATE_LIMITED ||
                    kind == CredentialRotationRemoteFailureKind.REMOTE_TEMPORARY)
                require(!retryAfter.isNegative && !retryAfter.isZero)
                val bounded = if (retryAfter > Duration.ofHours(1)) Duration.ofHours(1) else retryAfter
                return RetryableFailure(kind, bounded)
            }
        }
    }
    class TerminalFailure private constructor(val kind: CredentialRotationRemoteFailureKind) : CredentialRefreshResult {
        companion object {
            fun of(kind: CredentialRotationRemoteFailureKind): TerminalFailure {
                require(kind != CredentialRotationRemoteFailureKind.RATE_LIMITED &&
                    kind != CredentialRotationRemoteFailureKind.REMOTE_TEMPORARY)
                return TerminalFailure(kind)
            }
        }
    }
    data object Indeterminate : CredentialRefreshResult
}

interface CredentialRotator {
    val descriptor: CredentialRotatorDescriptor
    fun assess(credentialBytes: ByteArray, now: Instant): CredentialRotationAssessment
    fun refresh(
        credentialBytes: ByteArray,
        context: CredentialRotationRemoteContext,
        cancellation: CredentialRotationCancellation
    ): CredentialRefreshResult
}

enum class CredentialRotationExecutionState { CLAIMED, REMOTE_STARTED, RETRYABLE, COMPLETED, IN_DOUBT }
enum class CredentialRotationClaimKind { ACQUIRED, BUSY, STALE_VERSION, CONNECTION_UNAVAILABLE, IN_DOUBT, ALREADY_COMPLETED }

data class CredentialRotationClaimResult(
    val kind: CredentialRotationClaimKind,
    val retryAfter: Duration? = null
) {
    init {
        require((kind == CredentialRotationClaimKind.BUSY) == (retryAfter != null))
        require(retryAfter == null || (!retryAfter.isNegative && !retryAfter.isZero))
    }
}

enum class CredentialRotationRemoteStartResult { STARTED, NOT_OWNER, STALE_VERSION, CONNECTION_UNAVAILABLE, IN_DOUBT }

interface CredentialRotationExecutionStore {
    fun claim(
        organizationId: OrganizationId, connectionId: IntegrationConnectionId, bindingVersion: Int,
        executionId: CredentialRotationExecutionId, claimedAt: Instant, leaseExpiresAt: Instant
    ): CredentialRotationClaimResult
    fun markRemoteStarted(
        organizationId: OrganizationId, connectionId: IntegrationConnectionId, bindingVersion: Int,
        executionId: CredentialRotationExecutionId, startedAt: Instant
    ): CredentialRotationRemoteStartResult
    fun markRetryable(
        organizationId: OrganizationId, connectionId: IntegrationConnectionId, bindingVersion: Int,
        executionId: CredentialRotationExecutionId, retryNotBefore: Instant, updatedAt: Instant
    ): Boolean
    fun markCompleted(
        organizationId: OrganizationId, connectionId: IntegrationConnectionId, bindingVersion: Int,
        executionId: CredentialRotationExecutionId, terminalAt: Instant
    ): Boolean
    fun markInDoubt(
        organizationId: OrganizationId, connectionId: IntegrationConnectionId, bindingVersion: Int,
        executionId: CredentialRotationExecutionId, terminalAt: Instant
    ): Boolean
}

enum class CredentialRotationSuccessKind { READY, ROTATED, ROTATED_CLEANUP_REQUIRED }
enum class CredentialRotationFailureKind {
    CONNECTION_UNAVAILABLE, ROTATOR_UNAVAILABLE, AUTHENTICATION_REQUIRED, AUTHORIZATION_DENIED,
    RATE_LIMITED, REMOTE_TEMPORARY, REMOTE_PERMANENT, REMOTE_DATA_INVALID, BUDGET_EXCEEDED,
    CANCELLED, ROTATION_IN_PROGRESS, ROTATION_IN_DOUBT, CREDENTIAL_VERSION_CHANGED, INTERNAL
}

sealed interface CredentialRotationOutcome {
    data class Success(val kind: CredentialRotationSuccessKind, val providerKey: ProviderKey) : CredentialRotationOutcome
    data class Failure(
        val kind: CredentialRotationFailureKind,
        val providerKey: ProviderKey?,
        val retryAfter: Duration? = null
    ) : CredentialRotationOutcome {
        init {
            val retryable = kind == CredentialRotationFailureKind.RATE_LIMITED ||
                kind == CredentialRotationFailureKind.REMOTE_TEMPORARY ||
                kind == CredentialRotationFailureKind.ROTATION_IN_PROGRESS
            require(retryable || retryAfter == null)
        }
    }
}