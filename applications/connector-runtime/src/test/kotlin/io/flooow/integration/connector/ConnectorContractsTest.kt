package io.flooow.integration.connector

import io.flooow.integration.control.ProviderKey
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConnectorContractsTest {
    @Test
    fun `capability invocation and budgets reject noncanonical or unbounded input`() {
        assertEquals("inventory.snapshot.read", ConnectorCapability.of("inventory.snapshot.read").value)
        assertEquals(
            UUID.fromString("11111111-1111-4111-8111-111111111111"),
            ConnectorInvocationId.parse("11111111-1111-4111-8111-111111111111").value
        )

        listOf("Inventory.read", "inventory/read", "Ã¡", "").forEach {
            assertFailsWith<IllegalArgumentException> { ConnectorCapability.of(it) }
        }
        listOf("not-a-uuid", "11111111-1111-4111-8111-11111111111A").forEach {
            assertFailsWith<IllegalArgumentException> { ConnectorInvocationId.parse(it) }
        }
        assertFailsWith<IllegalArgumentException> {
            ConnectorBudget(Instant.EPOCH, 0, 1)
        }
        assertFailsWith<IllegalArgumentException> {
            ConnectorBudget(Instant.EPOCH, 1, ConnectorBudget.MAX_RESPONSE_BYTES + 1)
        }
    }

    @Test
    fun `progress consumes ownership redacts text and zeroes every scoped copy`() {
        val owned = "opaque-scroll-marker".toByteArray()
        val progress = ConnectorProgress.take(owned)
        assertTrue(owned.all { it == 0.toByte() })
        assertEquals("[REDACTED]", progress.toString())

        lateinit var scoped: ByteArray
        val size = progress.useBytes {
            scoped = it
            it.size
        }

        assertEquals("opaque-scroll-marker".length, size)
        assertTrue(scoped.all { it == 0.toByte() })
        progress.clear()
        assertTrue(progress.isCleared())
    }

    @Test
    fun `exhausted progress is terminal and contains no checkpoint`() {
        assertFailsWith<IllegalArgumentException> {
            VersionedConnectorProgress(1, null, exhausted = true)
        }
        assertFailsWith<IllegalArgumentException> {
            VersionedConnectorProgress(
                1,
                ConnectorProgress.take("unexpected".toByteArray()),
                exhausted = true,
                lastObservedAt = Instant.EPOCH
            )
        }
        assertTrue(
            VersionedConnectorProgress(
                1,
                null,
                exhausted = true,
                lastObservedAt = Instant.EPOCH
            ).exhausted
        )
    }

    @Test
    fun `retry hints exist only for retryable failures and are bounded`() {
        assertEquals(
            Duration.ofSeconds(1),
            ConnectorAdapterFailure.of(
                ConnectorAdapterFailureKind.RATE_LIMITED,
                Duration.ZERO
            ).retryAfter
        )
        assertEquals(
            Duration.ofHours(1),
            ConnectorAdapterFailure.of(
                ConnectorAdapterFailureKind.REMOTE_TEMPORARY,
                Duration.ofDays(2)
            ).retryAfter
        )
        assertFailsWith<IllegalArgumentException> {
            ConnectorAdapterFailure.of(
                ConnectorAdapterFailureKind.AUTHENTICATION_REQUIRED,
                Duration.ofSeconds(1)
            )
        }
    }

    @Test
    fun `descriptors and registries reject duplicate ownership`() {
        val provider = ProviderKey.of("test.provider")
        val capability = ConnectorCapability.of("test.records.read")
        assertFailsWith<IllegalArgumentException> {
            ConnectorDescriptor(
                provider,
                listOf(
                    ConnectorRecordDefinition(capability, TestRecord::class),
                    ConnectorRecordDefinition(capability, OtherRecord::class)
                )
            )
        }

        val connector = FakeConnector(provider, capability)
        val committer = FakeCommitter(capability)
        assertFailsWith<IllegalArgumentException> {
            ConnectorRuntime(
                FakeConnectionAccess(provider),
                listOf(connector, FakeConnector(provider, capability)),
                listOf(committer)
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ConnectorRuntime(
                FakeConnectionAccess(provider),
                listOf(connector),
                listOf(committer, FakeCommitter(capability))
            )
        }
    }
}
