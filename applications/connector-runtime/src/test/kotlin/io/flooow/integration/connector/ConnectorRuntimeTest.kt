package io.flooow.integration.connector

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.control.ProviderKey
import io.flooow.organization.OrganizationId
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ConnectorRuntimeTest {
    private val now = Instant.parse("2026-08-11T20:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val provider = ProviderKey.of("test.provider")
    private val capability = ConnectorCapability.of("test.records.read")

    @Test
    fun `unknown capability fails before credential resolution or adapter work`() {
        val access = FakeConnectionAccess(provider)
        val connector = FakeConnector(provider, capability)
        val runtime = ConnectorRuntime(
            access,
            listOf(connector),
            listOf(FakeCommitter(capability)),
            clock
        )

        val outcome = runtime.execute(invocation(ConnectorCapability.of("test.unknown.read")))

        assertFailure(ConnectorExecutionFailureKind.CONNECTOR_UNAVAILABLE, outcome)
        assertEquals(0, access.credentialResolutions)
        assertEquals(0, connector.calls)
    }

    @Test
    fun `unavailable connection fails before registry secret or adapter work`() {
        val access = FakeConnectionAccess(null)
        val connector = FakeConnector(provider, capability)
        val runtime = ConnectorRuntime(
            access,
            listOf(connector),
            listOf(FakeCommitter(capability)),
            clock
        )

        val outcome = runtime.execute(invocation())

        assertFailure(ConnectorExecutionFailureKind.CONNECTION_UNAVAILABLE, outcome)
        assertEquals(0, access.credentialResolutions)
        assertEquals(0, connector.calls)
    }

    @Test
    fun `successful page commits typed records and clears secret and progress buffers`() {
        val access = FakeConnectionAccess(provider)
        val connector = FakeConnector(provider, capability)
        val committer = FakeCommitter(capability, "page-one")
        val returnedProgress = ConnectorProgress.take("page-two".toByteArray())
        connector.result = ConnectorReadResult.Page(
            ConnectorPage(
                listOf(TestRecord("raw-record-marker")),
                returnedProgress,
                now,
                exhausted = false,
                responseBytes = 128
            )
        )
        val runtime = ConnectorRuntime(access, listOf(connector), listOf(committer), clock)

        val outcome = runtime.execute(invocation())

        val success = assertIs<ConnectorExecutionOutcome.Success>(outcome)
        assertEquals(ConnectorSuccessKind.COMMITTED, success.kind)
        assertEquals(1, success.recordCount)
        assertFalse(success.exhausted)
        assertEquals(1, connector.calls)
        assertEquals(1, committer.acceptedRecords.size)
        assertEquals("page-two", committer.storedProgressText())
        assertTrue(access.lastCredentialBytes!!.all { it == 0.toByte() })
        assertTrue(connector.lastProgressBytes!!.all { it == 0.toByte() })
        assertTrue(returnedProgress.isCleared())
        listOf("credential-marker", "page-one", "page-two", "raw-record-marker").forEach {
            assertFalse(outcome.toString().contains(it))
        }
    }

    @Test
    fun `exhausted page commits without next progress`() {
        val access = FakeConnectionAccess(provider)
        val connector = FakeConnector(provider, capability).apply {
            result = ConnectorReadResult.Page(
                ConnectorPage(emptyList(), null, now, exhausted = true, responseBytes = 0)
            )
        }
        val committer = FakeCommitter(capability)
        val runtime = ConnectorRuntime(
            access,
            listOf(connector),
            listOf(committer),
            clock
        )

        val first = assertIs<ConnectorExecutionOutcome.Success>(runtime.execute(invocation()))
        val repeated = assertIs<ConnectorExecutionOutcome.Success>(runtime.execute(invocation()))

        assertTrue(first.exhausted)
        assertEquals(ConnectorSuccessKind.ALREADY_COMMITTED, repeated.kind)
        assertTrue(repeated.exhausted)
        assertEquals(1, connector.calls)
        assertEquals(1, access.credentialResolutions)
        assertNull(committer.storedProgressText())
    }

    @Test
    fun `record and response limits reject the page without commit`() {
        val connector = FakeConnector(provider, capability).apply {
            result = ConnectorReadResult.Page(
                ConnectorPage(
                    listOf(TestRecord("one"), TestRecord("two")),
                    null,
                    now,
                    exhausted = true,
                    responseBytes = 11
                )
            )
        }
        val committer = FakeCommitter(capability)
        val runtime = ConnectorRuntime(
            FakeConnectionAccess(provider),
            listOf(connector),
            listOf(committer),
            clock
        )

        val outcome = runtime.execute(
            invocation(budget = ConnectorBudget(now.plusSeconds(30), 1, 10))
        )

        assertFailure(ConnectorExecutionFailureKind.BUDGET_EXCEEDED, outcome)
        assertEquals(0, committer.commitCalls)
    }

    @Test
    fun `invalid type timestamp exhaustion and progress invariants never commit`() {
        val invalidPages = listOf(
            ConnectorPage(listOf(OtherRecord("wrong")), null, now, true, 1),
            ConnectorPage(emptyList(), null, now.plus(Duration.ofMinutes(6)), true, 1),
            ConnectorPage(emptyList(), ConnectorProgress.take("extra".toByteArray()), now, true, 1),
            ConnectorPage(emptyList(), null, now, false, 1),
            ConnectorPage(
                emptyList(),
                ConnectorProgress.take("current".toByteArray()),
                now,
                false,
                1
            )
        )

        invalidPages.forEachIndexed { index, page ->
            val connector = FakeConnector(provider, capability).apply {
                result = ConnectorReadResult.Page(page)
            }
            val committer = FakeCommitter(
                capability,
                if (index == invalidPages.lastIndex) "current" else null
            )
            val runtime = ConnectorRuntime(
                FakeConnectionAccess(provider),
                listOf(connector),
                listOf(committer),
                clock
            )

            assertFailure(
                ConnectorExecutionFailureKind.REMOTE_DATA_INVALID,
                runtime.execute(invocation())
            )
            assertEquals(0, committer.commitCalls)
        }
    }

    @Test
    fun `deadline and early cancellation prevent connection lookup`() {
        val access = FakeConnectionAccess(provider)
        val connector = FakeConnector(provider, capability)
        val runtime = ConnectorRuntime(
            access,
            listOf(connector),
            listOf(FakeCommitter(capability)),
            clock
        )

        assertFailure(
            ConnectorExecutionFailureKind.BUDGET_EXCEEDED,
            runtime.execute(invocation(budget = ConnectorBudget(now, 10, 100)))
        )
        assertFailure(
            ConnectorExecutionFailureKind.CANCELLED,
            runtime.execute(invocation(), ConnectorCancellation { true })
        )
        assertFailure(
            ConnectorExecutionFailureKind.BUDGET_EXCEEDED,
            runtime.execute(
                invocation(budget = ConnectorBudget(now.plusSeconds(301), 10, 100))
            )
        )
        assertEquals(0, access.providerLookups)
        assertEquals(0, connector.calls)
    }

    @Test
    fun `future observation tolerance cannot disable timestamp validation`() {
        kotlin.test.assertFailsWith<IllegalArgumentException> {
            ConnectorRuntime(
                FakeConnectionAccess(provider),
                listOf(FakeConnector(provider, capability)),
                listOf(FakeCommitter(capability)),
                clock,
                Duration.ofMinutes(6)
            )
        }
    }

    @Test
    fun `cancellation after the read prevents commit`() {
        val connector = FakeConnector(provider, capability).apply {
            result = ConnectorReadResult.Page(
                ConnectorPage(emptyList(), null, now, exhausted = true, responseBytes = 0)
            )
        }
        val committer = FakeCommitter(capability)
        val runtime = ConnectorRuntime(
            FakeConnectionAccess(provider),
            listOf(connector),
            listOf(committer),
            clock
        )
        var checks = 0

        val outcome = runtime.execute(invocation(), ConnectorCancellation { ++checks >= 6 })

        assertFailure(ConnectorExecutionFailureKind.CANCELLED, outcome)
        assertEquals(1, connector.calls)
        assertEquals(0, committer.commitCalls)
    }

    @Test
    fun `provider retry classification is bounded and runtime never retries`() {
        val connector = FakeConnector(provider, capability).apply {
            result = ConnectorReadResult.Failed(
                ConnectorAdapterFailure.of(
                    ConnectorAdapterFailureKind.RATE_LIMITED,
                    Duration.ZERO
                )
            )
        }
        val runtime = ConnectorRuntime(
            FakeConnectionAccess(provider),
            listOf(connector),
            listOf(FakeCommitter(capability)),
            clock
        )

        val outcome = assertIs<ConnectorExecutionOutcome.Failure>(runtime.execute(invocation()))

        assertEquals(ConnectorExecutionFailureKind.RATE_LIMITED, outcome.kind)
        assertEquals(Duration.ofSeconds(1), outcome.retryAfter)
        assertEquals(1, connector.calls)
    }

    @Test
    fun `adapter and commit exceptions expose only controlled internal failure`() {
        val adapter = FakeConnector(provider, capability).apply {
            failure = IllegalStateException("secret response body marker")
        }
        val adapterRuntime = ConnectorRuntime(
            FakeConnectionAccess(provider),
            listOf(adapter),
            listOf(FakeCommitter(capability)),
            clock
        )
        val adapterOutcome = adapterRuntime.execute(invocation())
        assertFailure(ConnectorExecutionFailureKind.INTERNAL, adapterOutcome)
        assertFalse(adapterOutcome.toString().contains("secret response body marker"))

        val connector = FakeConnector(provider, capability).apply {
            result = ConnectorReadResult.Page(
                ConnectorPage(emptyList(), null, now, exhausted = true, responseBytes = 0)
            )
        }
        val committer = FakeCommitter(capability).apply { failCommit = true }
        val commitOutcome = ConnectorRuntime(
            FakeConnectionAccess(provider),
            listOf(connector),
            listOf(committer),
            clock
        ).execute(invocation())
        assertFailure(ConnectorExecutionFailureKind.INTERNAL, commitOutcome)
        assertFalse(commitOutcome.toString().contains("commit storage marker"))
    }

    @Test
    fun `unexpected cancellation exception is redacted as internal`() {
        val runtime = ConnectorRuntime(
            FakeConnectionAccess(provider),
            listOf(FakeConnector(provider, capability)),
            listOf(FakeCommitter(capability)),
            clock
        )

        val outcome = runtime.execute(invocation()) {
            error("cancellation implementation marker")
        }

        assertFailure(ConnectorExecutionFailureKind.INTERNAL, outcome)
        assertFalse(outcome.toString().contains("cancellation implementation marker"))
    }

    @Test
    fun `credential race becomes unavailable and mismatched committer fails before secret`() {
        val unavailable = FakeConnectionAccess(provider).apply { failCredential = true }
        val connector = FakeConnector(provider, capability)
        assertFailure(
            ConnectorExecutionFailureKind.CONNECTION_UNAVAILABLE,
            ConnectorRuntime(
                unavailable,
                listOf(connector),
                listOf(FakeCommitter(capability)),
                clock
            ).execute(invocation())
        )
        assertEquals(0, connector.calls)

        val mismatchAccess = FakeConnectionAccess(provider)
        val mismatched = FakeCommitter(capability, recordType = OtherRecord::class)
        assertFailure(
            ConnectorExecutionFailureKind.CONNECTOR_UNAVAILABLE,
            ConnectorRuntime(
                mismatchAccess,
                listOf(FakeConnector(provider, capability)),
                listOf(mismatched),
                clock
            ).execute(invocation())
        )
        assertEquals(0, mismatchAccess.credentialResolutions)
    }

    @Test
    fun `stale commit is fenced as a progress conflict`() {
        val connector = FakeConnector(provider, capability).apply {
            result = ConnectorReadResult.Page(
                ConnectorPage(emptyList(), null, now, exhausted = true, responseBytes = 0)
            )
        }
        val committer = FakeCommitter(capability).apply {
            forcedResult = ConnectorPageCommitResult.STALE_PROGRESS
        }

        val outcome = ConnectorRuntime(
            FakeConnectionAccess(provider),
            listOf(connector),
            listOf(committer),
            clock
        ).execute(invocation())

        assertFailure(ConnectorExecutionFailureKind.PROGRESS_CONFLICT, outcome)
    }

    @Test
    fun `concurrent reads accept one page once and acknowledge the duplicate`() {
        val barrier = CyclicBarrier(2)
        val connector = FakeConnector(provider, capability).apply {
            beforeReturn = { barrier.await() }
            resultFactory = {
                ConnectorReadResult.Page(
                    ConnectorPage(
                        listOf(TestRecord("same-record")),
                        null,
                        now,
                        exhausted = true,
                        responseBytes = 10
                    )
                )
            }
        }
        val committer = FakeCommitter(capability)
        val runtime = ConnectorRuntime(
            FakeConnectionAccess(provider),
            listOf(connector),
            listOf(committer),
            clock
        )
        val executor = Executors.newFixedThreadPool(2)
        try {
            val outcomes = executor.invokeAll(
                listOf(
                    Callable { runtime.execute(invocation()) },
                    Callable { runtime.execute(invocation()) }
                )
            ).map { it.get() }

            assertEquals(2, connector.calls)
            assertEquals(1, committer.acceptedRecords.size)
            assertEquals(
                setOf(ConnectorSuccessKind.COMMITTED, ConnectorSuccessKind.ALREADY_COMMITTED),
                outcomes.map { assertIs<ConnectorExecutionOutcome.Success>(it).kind }.toSet()
            )
        } finally {
            executor.shutdownNow()
        }
    }

    private fun invocation(
        selectedCapability: ConnectorCapability = capability,
        budget: ConnectorBudget = ConnectorBudget(now.plusSeconds(30), 100, 10_000)
    ) = ConnectorInvocation(
        ORGANIZATION_ID,
        CONNECTION_ID,
        selectedCapability,
        ConnectorInvocationId(INVOCATION_ID),
        budget
    )

    private fun assertFailure(
        expected: ConnectorExecutionFailureKind,
        outcome: ConnectorExecutionOutcome
    ) {
        assertEquals(expected, assertIs<ConnectorExecutionOutcome.Failure>(outcome).kind)
    }
}

internal data class TestRecord(val marker: String) : ConnectorRecord
internal data class OtherRecord(val marker: String) : ConnectorRecord

internal class FakeConnectionAccess(
    var provider: ProviderKey?,
    private val secret: ByteArray = "credential-marker".toByteArray()
) : ConnectorConnectionAccess {
    var providerLookups = 0
    var credentialResolutions = 0
    var failCredential = false
    var lastCredentialBytes: ByteArray? = null

    override fun activeProvider(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId
    ): ProviderKey? {
        providerLookups++
        return provider
    }

    override fun <T> withActiveCredential(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        operation: (ByteArray) -> T
    ): T {
        credentialResolutions++
        if (failCredential) error("connection lifecycle marker")
        val scoped = secret.copyOf()
        lastCredentialBytes = scoped
        return try {
            operation(scoped)
        } finally {
            scoped.fill(0)
        }
    }
}

internal class FakeConnector(
    provider: ProviderKey,
    capability: ConnectorCapability
) : PullConnector {
    override val descriptor = ConnectorDescriptor(
        provider,
        listOf(ConnectorRecordDefinition(capability, TestRecord::class))
    )
    @Volatile var calls = 0
    var result: ConnectorReadResult = ConnectorReadResult.Page(
        ConnectorPage(emptyList(), null, Instant.EPOCH, exhausted = true, responseBytes = 0)
    )
    var resultFactory: (() -> ConnectorReadResult)? = null
    var failure: Exception? = null
    var beforeReturn: (() -> Unit)? = null
    var lastCredentialBytes: ByteArray? = null
    var lastProgressBytes: ByteArray? = null

    override fun readPage(
        capability: ConnectorCapability,
        credentialBytes: ByteArray,
        currentProgress: ConnectorProgress?,
        budget: ConnectorBudget,
        cancellation: ConnectorCancellation
    ): ConnectorReadResult {
        synchronized(this) { calls++ }
        lastCredentialBytes = credentialBytes
        currentProgress?.useBytes { lastProgressBytes = it }
        failure?.let { throw it }
        beforeReturn?.invoke()
        return resultFactory?.invoke() ?: result
    }
}

internal class FakeCommitter(
    override val capability: ConnectorCapability,
    initialProgress: String? = null,
    override val recordType: KClass<out ConnectorRecord> = TestRecord::class
) : ConnectorPageCommitter {
    private var version = 0L
    private var progress = initialProgress?.toByteArray()
    private var exhausted = false
    private var lastObservedAt: Instant? = null
    private val committedKeys = mutableSetOf<ConnectorPageCommitKey>()
    val acceptedRecords = mutableListOf<ConnectorRecord>()
    var commitCalls = 0
    var failCommit = false
    var forcedResult: ConnectorPageCommitResult? = null

    @Synchronized
    override fun load(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        capability: ConnectorCapability
    ): VersionedConnectorProgress = VersionedConnectorProgress(
        version,
        progress?.copyOf()?.let(ConnectorProgress::take),
        exhausted,
        lastObservedAt
    )

    @Synchronized
    override fun commit(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        capability: ConnectorCapability,
        expectedProgressVersion: Long,
        pageCommitKey: ConnectorPageCommitKey,
        records: List<ConnectorRecord>,
        nextProgress: ConnectorProgress?,
        exhausted: Boolean,
        observedAt: Instant
    ): ConnectorPageCommitResult {
        commitCalls++
        if (failCommit) error("commit storage marker")
        forcedResult?.let { return it }
        if (pageCommitKey in committedKeys) return ConnectorPageCommitResult.ALREADY_COMMITTED
        if (expectedProgressVersion != version) return ConnectorPageCommitResult.STALE_PROGRESS
        acceptedRecords += records
        progress = nextProgress?.useBytes { it.copyOf() }
        this.exhausted = exhausted
        lastObservedAt = observedAt
        committedKeys += pageCommitKey
        version++
        return ConnectorPageCommitResult.COMMITTED
    }

    @Synchronized
    fun storedProgressText(): String? = progress?.decodeToString()
}

private val ORGANIZATION_ID =
    OrganizationId.parse("11111111-1111-4111-8111-111111111111")
private val CONNECTION_ID =
    IntegrationConnectionId(UUID.fromString("22222222-2222-4222-8222-222222222222"))
private val INVOCATION_ID = UUID.fromString("33333333-3333-4333-8333-333333333333")
