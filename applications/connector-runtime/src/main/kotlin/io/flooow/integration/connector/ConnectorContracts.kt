package io.flooow.integration.connector

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.control.IntegrationControlPlaneService
import io.flooow.integration.control.ProviderKey
import io.flooow.organization.OrganizationId
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.reflect.KClass

@JvmInline
value class ConnectorCapability private constructor(val value: String) {
    companion object {
        private val pattern = Regex("[a-z0-9][a-z0-9.-]{0,99}")

        fun of(value: String): ConnectorCapability {
            require(pattern.matches(value)) { "Invalid connector capability" }
            return ConnectorCapability(value)
        }
    }
}

@JvmInline
value class ConnectorInvocationId(val value: UUID) {
    companion object {
        fun parse(value: String): ConnectorInvocationId {
            val parsed = UUID.fromString(value)
            require(parsed.toString() == value) { "Invalid connector invocation identifier" }
            return ConnectorInvocationId(parsed)
        }
    }
}

data class ConnectorBudget(
    val deadline: Instant,
    val maxRecords: Int,
    val maxResponseBytes: Long
) {
    init {
        require(maxRecords in 1..MAX_RECORDS) { "Invalid connector record budget" }
        require(maxResponseBytes in 1..MAX_RESPONSE_BYTES) {
            "Invalid connector response budget"
        }
    }

    companion object {
        const val MAX_RECORDS = 1_000
        const val MAX_RESPONSE_BYTES = 10L * 1024L * 1024L
    }
}

data class ConnectorInvocation(
    val organizationId: OrganizationId,
    val connectionId: IntegrationConnectionId,
    val capability: ConnectorCapability,
    val invocationId: ConnectorInvocationId,
    val budget: ConnectorBudget
)

fun interface ConnectorCancellation {
    fun isCancelled(): Boolean

    companion object {
        val NEVER = ConnectorCancellation { false }
    }
}

interface ConnectorRecord

class ConnectorProgress private constructor(private val bytes: ByteArray) : AutoCloseable {
    fun <T> useBytes(operation: (ByteArray) -> T): T {
        val scoped = bytes.copyOf()
        return try {
            operation(scoped)
        } finally {
            scoped.fill(0)
        }
    }

    internal fun sameValueAs(other: ConnectorProgress): Boolean =
        useBytes { left -> other.useBytes { right -> left.contentEquals(right) } }

    override fun close() = bytes.fill(0)

    internal fun isCleared(): Boolean = bytes.all { it == 0.toByte() }

    override fun toString(): String = "[REDACTED]"

    companion object {
        const val MAX_BYTES = 4_096

        fun take(ownedBytes: ByteArray): ConnectorProgress = try {
            require(ownedBytes.size in 1..MAX_BYTES) { "Invalid connector progress" }
            ConnectorProgress(ownedBytes.copyOf())
        } finally {
            ownedBytes.fill(0)
        }
    }
}

class VersionedConnectorProgress(
    val version: Long,
    val progress: ConnectorProgress?,
    val exhausted: Boolean = false,
    val lastObservedAt: Instant? = null
) {
    init {
        require(version >= 0) { "Invalid connector progress version" }
        require(!exhausted || (progress == null && lastObservedAt != null)) {
            "Invalid exhausted connector progress"
        }
    }

    override fun toString(): String =
        "VersionedConnectorProgress(version=$version, exhausted=$exhausted, " +
            "progress=[REDACTED])"
}

class ConnectorPageCommitKey private constructor(private val digest: ByteArray) {
    fun <T> useBytes(operation: (ByteArray) -> T): T {
        val scoped = digest.copyOf()
        return try { operation(scoped) } finally { scoped.fill(0) }
    }

    override fun equals(other: Any?): Boolean =
        other is ConnectorPageCommitKey && digest.contentEquals(other.digest)

    override fun hashCode(): Int = digest.contentHashCode()

    override fun toString(): String = "[INTERNAL]"

    companion object {
        internal fun derive(
            organizationId: OrganizationId,
            connectionId: IntegrationConnectionId,
            capability: ConnectorCapability,
            progressVersion: Long
        ): ConnectorPageCommitKey {
            val canonical = listOf(
                organizationId.toString(),
                connectionId.value.toString(),
                capability.value,
                progressVersion.toString()
            ).joinToString("\n")
            return ConnectorPageCommitKey(
                MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toByteArray(StandardCharsets.UTF_8))
            )
        }
    }
}

data class ConnectorProgressProtectionContext(
    val organizationId: OrganizationId,
    val connectionId: IntegrationConnectionId,
    val capability: ConnectorCapability,
    val progressVersion: Long
) {
    init {
        require(progressVersion >= 0) { "Invalid protected progress version" }
    }
}

class SealedConnectorProgress private constructor(private val envelope: ByteArray) : AutoCloseable {
    fun <T> useBytes(operation: (ByteArray) -> T): T {
        val scoped = envelope.copyOf()
        return try { operation(scoped) } finally { scoped.fill(0) }
    }

    override fun toString(): String = "[REDACTED]"
    override fun close() = envelope.fill(0)

    companion object {
        const val MAX_BYTES = 16_384

        fun take(ownedBytes: ByteArray): SealedConnectorProgress = try {
            require(ownedBytes.size in 1..MAX_BYTES) { "Invalid sealed connector progress" }
            SealedConnectorProgress(ownedBytes.copyOf())
        } finally {
            ownedBytes.fill(0)
        }
    }
}

interface ConnectorProgressProtector {
    fun seal(
        context: ConnectorProgressProtectionContext,
        plaintextBytes: ByteArray
    ): SealedConnectorProgress

    fun open(
        context: ConnectorProgressProtectionContext,
        sealedProgress: SealedConnectorProgress
    ): ByteArray
}

data class ConnectorRecordDefinition(
    val capability: ConnectorCapability,
    val recordType: KClass<out ConnectorRecord>
)

class ConnectorDescriptor(
    val providerKey: ProviderKey,
    definitions: Collection<ConnectorRecordDefinition>
) {
    val definitions = definitions.toList()

    init {
        require(this.definitions.isNotEmpty()) { "Connector must declare capabilities" }
        require(this.definitions.map { it.capability }.distinct().size == this.definitions.size) {
            "Connector capability is registered more than once"
        }
    }

    internal fun definition(capability: ConnectorCapability): ConnectorRecordDefinition? =
        definitions.singleOrNull { it.capability == capability }

    override fun toString(): String =
        "ConnectorDescriptor(providerKey=${providerKey.value}, capabilities=" +
            definitions.map { it.capability.value }.sorted() + ")"
}

interface PullConnector {
    val descriptor: ConnectorDescriptor

    fun readPage(
        capability: ConnectorCapability,
        credentialBytes: ByteArray,
        currentProgress: ConnectorProgress?,
        budget: ConnectorBudget,
        cancellation: ConnectorCancellation
    ): ConnectorReadResult
}

sealed interface ConnectorReadResult {
    class Page(val value: ConnectorPage) : ConnectorReadResult {
        override fun toString(): String = "ConnectorReadResult.Page(${value.safeSummary()})"
    }

    class Failed(val failure: ConnectorAdapterFailure) : ConnectorReadResult
}

class ConnectorPage(
    records: Collection<ConnectorRecord>,
    val nextProgress: ConnectorProgress?,
    val observedAt: Instant,
    val exhausted: Boolean,
    val responseBytes: Long
) {
    val records: List<ConnectorRecord> = records.toList()

    internal fun safeSummary(): String =
        "records=${records.size}, exhausted=$exhausted, observedAt=$observedAt"

    override fun toString(): String = "ConnectorPage(${safeSummary()}, progress=[REDACTED])"
}

enum class ConnectorAdapterFailureKind {
    AUTHENTICATION_REQUIRED,
    AUTHORIZATION_DENIED,
    RATE_LIMITED,
    REMOTE_TEMPORARY,
    REMOTE_PERMANENT,
    REMOTE_DATA_INVALID,
    BUDGET_EXCEEDED,
    CANCELLED
}

class ConnectorAdapterFailure private constructor(
    val kind: ConnectorAdapterFailureKind,
    val retryAfter: Duration?
) {
    override fun toString(): String =
        "ConnectorAdapterFailure(kind=$kind, retryAfter=$retryAfter)"

    companion object {
        private val minimumRetry = Duration.ofSeconds(1)
        private val maximumRetry = Duration.ofHours(1)

        fun of(
            kind: ConnectorAdapterFailureKind,
            retryAfter: Duration? = null
        ): ConnectorAdapterFailure {
            val retryable = kind == ConnectorAdapterFailureKind.RATE_LIMITED ||
                kind == ConnectorAdapterFailureKind.REMOTE_TEMPORARY
            require(retryable || retryAfter == null) {
                "Retry hint is not allowed for this connector failure"
            }
            val bounded = retryAfter?.let {
                when {
                    it < minimumRetry -> minimumRetry
                    it > maximumRetry -> maximumRetry
                    else -> it
                }
            }
            return ConnectorAdapterFailure(kind, bounded)
        }
    }
}

enum class ConnectorPageCommitResult { COMMITTED, ALREADY_COMMITTED, STALE_PROGRESS }

interface ConnectorPageCommitter {
    val capability: ConnectorCapability
    val recordType: KClass<out ConnectorRecord>

    fun load(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        capability: ConnectorCapability
    ): VersionedConnectorProgress

    fun commit(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        capability: ConnectorCapability,
        expectedProgressVersion: Long,
        pageCommitKey: ConnectorPageCommitKey,
        records: List<ConnectorRecord>,
        nextProgress: ConnectorProgress?,
        exhausted: Boolean,
        observedAt: Instant
    ): ConnectorPageCommitResult
}

interface ConnectorConnectionAccess {
    fun activeProvider(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId
    ): ProviderKey?

    fun <T> withActiveCredential(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        operation: (ByteArray) -> T
    ): T
}

class IntegrationControlPlaneConnectorAccess(
    private val controlPlane: IntegrationControlPlaneService
) : ConnectorConnectionAccess {
    override fun activeProvider(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId
    ): ProviderKey? = controlPlane.activeConnectionProvider(organizationId, connectionId)

    override fun <T> withActiveCredential(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        operation: (ByteArray) -> T
    ): T = controlPlane.withActiveCredential(organizationId, connectionId, operation)
}

enum class ConnectorSuccessKind { COMMITTED, ALREADY_COMMITTED }

enum class ConnectorExecutionFailureKind {
    AUTHENTICATION_REQUIRED,
    AUTHORIZATION_DENIED,
    RATE_LIMITED,
    REMOTE_TEMPORARY,
    REMOTE_PERMANENT,
    REMOTE_DATA_INVALID,
    BUDGET_EXCEEDED,
    CANCELLED,
    CONNECTION_UNAVAILABLE,
    CONNECTOR_UNAVAILABLE,
    PROGRESS_CONFLICT,
    INTERNAL
}

sealed interface ConnectorExecutionOutcome {
    data class Success(
        val kind: ConnectorSuccessKind,
        val providerKey: ProviderKey,
        val capability: ConnectorCapability,
        val recordCount: Int,
        val exhausted: Boolean,
        val observedAt: Instant
    ) : ConnectorExecutionOutcome

    data class Failure(
        val kind: ConnectorExecutionFailureKind,
        val providerKey: ProviderKey?,
        val capability: ConnectorCapability,
        val retryAfter: Duration? = null
    ) : ConnectorExecutionOutcome
}
