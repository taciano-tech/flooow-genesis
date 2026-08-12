package io.flooow.integration.connector

import io.flooow.integration.control.ProviderKey
import java.time.Clock
import java.time.Duration

class ConnectorRuntime(
    private val connections: ConnectorConnectionAccess,
    connectors: Collection<PullConnector>,
    committers: Collection<ConnectorPageCommitter>,
    private val clock: Clock = Clock.systemUTC(),
    private val futureObservationTolerance: Duration = Duration.ofMinutes(5)
) {
    private val connectorRegistry = ConnectorRegistry(connectors)
    private val committerRegistry = ConnectorCommitterRegistry(committers)

    init {
        require(
            !futureObservationTolerance.isNegative &&
                futureObservationTolerance <= MAX_FUTURE_OBSERVATION_TOLERANCE
        ) {
            "Invalid future observation tolerance"
        }
    }

    fun execute(
        invocation: ConnectorInvocation,
        cancellation: ConnectorCancellation = ConnectorCancellation.NEVER
    ): ConnectorExecutionOutcome = try {
        executeControlled(invocation, cancellation)
    } catch (_: Exception) {
        failure(invocation, ConnectorExecutionFailureKind.INTERNAL)
    }

    private fun executeControlled(
        invocation: ConnectorInvocation,
        cancellation: ConnectorCancellation
    ): ConnectorExecutionOutcome {
        gate(invocation, cancellation, null)?.let { return it }

        val provider = try {
            connections.activeProvider(invocation.organizationId, invocation.connectionId)
        } catch (_: Exception) {
            return failure(invocation, ConnectorExecutionFailureKind.INTERNAL)
        } ?: return failure(invocation, ConnectorExecutionFailureKind.CONNECTION_UNAVAILABLE)

        val resolved = connectorRegistry.resolve(provider, invocation.capability)
            ?: return failure(
                invocation,
                ConnectorExecutionFailureKind.CONNECTOR_UNAVAILABLE,
                provider
            )
        val committer = committerRegistry.resolve(resolved.definition)
            ?: return failure(
                invocation,
                ConnectorExecutionFailureKind.CONNECTOR_UNAVAILABLE,
                provider
            )

        gate(invocation, cancellation, provider)?.let { return it }
        val versionedProgress = try {
            committer.load(
                invocation.organizationId,
                invocation.connectionId,
                invocation.capability
            )
        } catch (_: Exception) {
            return failure(invocation, ConnectorExecutionFailureKind.INTERNAL, provider)
        }

        gate(invocation, cancellation, provider)?.let {
            versionedProgress.progress?.clear()
            return it
        }
        if (versionedProgress.exhausted) {
            if (requireNotNull(versionedProgress.lastObservedAt) >
                clock.instant().plus(futureObservationTolerance)) {
                return failure(invocation, ConnectorExecutionFailureKind.INTERNAL, provider)
            }
            return ConnectorExecutionOutcome.Success(
                ConnectorSuccessKind.ALREADY_COMMITTED,
                provider,
                invocation.capability,
                recordCount = 0,
                exhausted = true,
                observedAt = requireNotNull(versionedProgress.lastObservedAt)
            )
        }

        return try {
            gate(invocation, cancellation, provider)?.let { return it }
            val read = invokeAdapter(invocation, cancellation, provider, resolved, versionedProgress)
            when (read) {
                is AdapterInvocation.Controlled -> read.outcome
                AdapterInvocation.ConnectionUnavailable -> failure(
                    invocation,
                    ConnectorExecutionFailureKind.CONNECTION_UNAVAILABLE,
                    provider
                )
                AdapterInvocation.Internal -> failure(
                    invocation,
                    ConnectorExecutionFailureKind.INTERNAL,
                    provider
                )
                is AdapterInvocation.Result -> handleReadResult(
                    invocation,
                    cancellation,
                    provider,
                    resolved,
                    committer,
                    versionedProgress,
                    read.result
                )
            }
        } finally {
            versionedProgress.progress?.clear()
        }
    }

    private fun invokeAdapter(
        invocation: ConnectorInvocation,
        cancellation: ConnectorCancellation,
        provider: ProviderKey,
        resolved: ResolvedConnector,
        versionedProgress: VersionedConnectorProgress
    ): AdapterInvocation = try {
        connections.withActiveCredential(
            invocation.organizationId,
            invocation.connectionId
        ) { credential ->
            gate(invocation, cancellation, provider)?.let {
                return@withActiveCredential AdapterInvocation.Controlled(it)
            }
            try {
                AdapterInvocation.Result(
                    resolved.connector.readPage(
                        invocation.capability,
                        credential,
                        versionedProgress.progress,
                        invocation.budget,
                        cancellation
                    )
                )
            } catch (_: Exception) {
                AdapterInvocation.Internal
            }
        }
    } catch (_: Exception) {
        AdapterInvocation.ConnectionUnavailable
    }

    private fun handleReadResult(
        invocation: ConnectorInvocation,
        cancellation: ConnectorCancellation,
        provider: ProviderKey,
        resolved: ResolvedConnector,
        committer: ConnectorPageCommitter,
        versionedProgress: VersionedConnectorProgress,
        result: ConnectorReadResult
    ): ConnectorExecutionOutcome = when (result) {
        is ConnectorReadResult.Failed -> failure(
            invocation,
            result.failure.kind.toExecutionKind(),
            provider,
            result.failure.retryAfter
        )
        is ConnectorReadResult.Page -> handlePage(
            invocation,
            cancellation,
            provider,
            resolved,
            committer,
            versionedProgress,
            result.value
        )
    }

    private fun handlePage(
        invocation: ConnectorInvocation,
        cancellation: ConnectorCancellation,
        provider: ProviderKey,
        resolved: ResolvedConnector,
        committer: ConnectorPageCommitter,
        versionedProgress: VersionedConnectorProgress,
        page: ConnectorPage
    ): ConnectorExecutionOutcome {
        try {
            validatePage(invocation, resolved, versionedProgress, page)?.let { return it }
            gate(invocation, cancellation, provider)?.let { return it }

            val commit = try {
                committer.commit(
                    invocation.organizationId,
                    invocation.connectionId,
                    invocation.capability,
                    versionedProgress.version,
                    ConnectorPageCommitKey.derive(
                        invocation.organizationId,
                        invocation.connectionId,
                        invocation.capability,
                        versionedProgress.version
                    ),
                    page.records,
                    page.nextProgress,
                    page.exhausted,
                    page.observedAt
                )
            } catch (_: Exception) {
                return failure(invocation, ConnectorExecutionFailureKind.INTERNAL, provider)
            }

            return when (commit) {
                ConnectorPageCommitResult.COMMITTED -> success(
                    invocation,
                    provider,
                    page,
                    ConnectorSuccessKind.COMMITTED
                )
                ConnectorPageCommitResult.ALREADY_COMMITTED -> success(
                    invocation,
                    provider,
                    page,
                    ConnectorSuccessKind.ALREADY_COMMITTED
                )
                ConnectorPageCommitResult.STALE_PROGRESS -> failure(
                    invocation,
                    ConnectorExecutionFailureKind.PROGRESS_CONFLICT,
                    provider
                )
            }
        } finally {
            page.nextProgress?.clear()
        }
    }

    private fun validatePage(
        invocation: ConnectorInvocation,
        resolved: ResolvedConnector,
        versionedProgress: VersionedConnectorProgress,
        page: ConnectorPage
    ): ConnectorExecutionOutcome.Failure? {
        if (page.records.size > invocation.budget.maxRecords ||
            page.responseBytes > invocation.budget.maxResponseBytes) {
            return failure(
                invocation,
                ConnectorExecutionFailureKind.BUDGET_EXCEEDED,
                resolved.connector.descriptor.providerKey
            )
        }
        if (page.responseBytes < 0 ||
            page.observedAt > clock.instant().plus(futureObservationTolerance) ||
            page.records.any { !resolved.definition.recordType.isInstance(it) } ||
            (page.exhausted && page.nextProgress != null) ||
            (!page.exhausted && page.nextProgress == null) ||
            (!page.exhausted && versionedProgress.progress != null &&
                page.nextProgress!!.sameValueAs(versionedProgress.progress))) {
            return failure(
                invocation,
                ConnectorExecutionFailureKind.REMOTE_DATA_INVALID,
                resolved.connector.descriptor.providerKey
            )
        }
        return null
    }

    private fun gate(
        invocation: ConnectorInvocation,
        cancellation: ConnectorCancellation,
        provider: ProviderKey?
    ): ConnectorExecutionOutcome.Failure? = when {
        cancellation.isCancelled() -> failure(
            invocation,
            ConnectorExecutionFailureKind.CANCELLED,
            provider
        )
        !clock.instant().isBefore(invocation.budget.deadline) -> failure(
            invocation,
            ConnectorExecutionFailureKind.BUDGET_EXCEEDED,
            provider
        )
        invocation.budget.deadline > clock.instant().plus(MAX_INVOCATION_DURATION) -> failure(
            invocation,
            ConnectorExecutionFailureKind.BUDGET_EXCEEDED,
            provider
        )
        else -> null
    }

    private fun success(
        invocation: ConnectorInvocation,
        provider: ProviderKey,
        page: ConnectorPage,
        kind: ConnectorSuccessKind
    ) = ConnectorExecutionOutcome.Success(
        kind,
        provider,
        invocation.capability,
        page.records.size,
        page.exhausted,
        page.observedAt
    )

    private fun failure(
        invocation: ConnectorInvocation,
        kind: ConnectorExecutionFailureKind,
        provider: ProviderKey? = null,
        retryAfter: Duration? = null
    ) = ConnectorExecutionOutcome.Failure(
        kind,
        provider,
        invocation.capability,
        retryAfter
    )

    private companion object {
        val MAX_INVOCATION_DURATION: Duration = Duration.ofMinutes(5)
        val MAX_FUTURE_OBSERVATION_TOLERANCE: Duration = Duration.ofMinutes(5)
    }
}

private class ConnectorRegistry(connectors: Collection<PullConnector>) {
    private val byProvider = connectors.associateBy { it.descriptor.providerKey }

    init {
        require(byProvider.size == connectors.size) { "Connector provider is registered twice" }
    }

    fun resolve(provider: ProviderKey, capability: ConnectorCapability): ResolvedConnector? {
        val connector = byProvider[provider] ?: return null
        val definition = connector.descriptor.definition(capability) ?: return null
        return ResolvedConnector(connector, definition)
    }
}

private class ConnectorCommitterRegistry(committers: Collection<ConnectorPageCommitter>) {
    private val byCapability = committers.associateBy { it.capability }

    init {
        require(byCapability.size == committers.size) { "Connector committer is registered twice" }
    }

    fun resolve(definition: ConnectorRecordDefinition): ConnectorPageCommitter? =
        byCapability[definition.capability]?.takeIf { it.recordType == definition.recordType }
}

private data class ResolvedConnector(
    val connector: PullConnector,
    val definition: ConnectorRecordDefinition
)

private sealed interface AdapterInvocation {
    data class Result(val result: ConnectorReadResult) : AdapterInvocation
    data class Controlled(val outcome: ConnectorExecutionOutcome.Failure) : AdapterInvocation
    data object ConnectionUnavailable : AdapterInvocation
    data object Internal : AdapterInvocation
}

private fun ConnectorAdapterFailureKind.toExecutionKind(): ConnectorExecutionFailureKind = when (this) {
    ConnectorAdapterFailureKind.AUTHENTICATION_REQUIRED ->
        ConnectorExecutionFailureKind.AUTHENTICATION_REQUIRED
    ConnectorAdapterFailureKind.AUTHORIZATION_DENIED ->
        ConnectorExecutionFailureKind.AUTHORIZATION_DENIED
    ConnectorAdapterFailureKind.RATE_LIMITED -> ConnectorExecutionFailureKind.RATE_LIMITED
    ConnectorAdapterFailureKind.REMOTE_TEMPORARY ->
        ConnectorExecutionFailureKind.REMOTE_TEMPORARY
    ConnectorAdapterFailureKind.REMOTE_PERMANENT ->
        ConnectorExecutionFailureKind.REMOTE_PERMANENT
    ConnectorAdapterFailureKind.REMOTE_DATA_INVALID ->
        ConnectorExecutionFailureKind.REMOTE_DATA_INVALID
    ConnectorAdapterFailureKind.BUDGET_EXCEEDED ->
        ConnectorExecutionFailureKind.BUDGET_EXCEEDED
    ConnectorAdapterFailureKind.CANCELLED -> ConnectorExecutionFailureKind.CANCELLED
}
