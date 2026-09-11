package io.flooow.integration.provider.mercadolivre

import io.flooow.integration.control.CredentialBinding
import io.flooow.integration.control.CredentialKind
import io.flooow.integration.control.IdentifierFactory
import io.flooow.integration.control.IntegrationAuditEntry
import io.flooow.integration.control.IntegrationAuditEntryId
import io.flooow.integration.control.IntegrationConnection
import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.control.IntegrationConnectionStatus
import io.flooow.integration.credential.IntegrationControlPlaneCredentialRotationAccess
import io.flooow.integration.control.IntegrationControlPlaneRepository
import io.flooow.integration.control.IntegrationControlPlaneService
import io.flooow.integration.control.IntegrationDestination
import io.flooow.integration.control.IntegrationDestinationId
import io.flooow.integration.control.IntegrationDestinationStatus
import io.flooow.integration.control.IntegrationOrganization
import io.flooow.integration.control.IntegrationOrganizationStatus
import io.flooow.integration.control.ProviderKey
import io.flooow.integration.control.SecretReference
import io.flooow.integration.control.SecretVault
import io.flooow.integration.credential.CredentialRefreshResult
import io.flooow.integration.credential.CredentialRotationAssessment
import io.flooow.integration.credential.CredentialRotationCancellation
import io.flooow.integration.credential.CredentialRotationClaimKind
import io.flooow.integration.credential.CredentialRotationClaimResult
import io.flooow.integration.credential.CredentialRotationExecutionId
import io.flooow.integration.credential.CredentialRotationExecutionStore
import io.flooow.integration.credential.CredentialRotationExecutor
import io.flooow.integration.credential.CredentialRotationInvocation
import io.flooow.integration.credential.CredentialRotationOutcome
import io.flooow.integration.credential.CredentialRotationRemoteContext
import io.flooow.integration.credential.CredentialRotationRemoteFailureKind
import io.flooow.integration.credential.CredentialRotationRemoteStartResult
import io.flooow.integration.credential.CredentialRotationSuccessKind
import io.flooow.organization.OrganizationId
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class MercadoLivreOAuthCredentialRotatorTest {
    private val now = Instant.parse("2026-09-06T20:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)

    @Test
    fun `descriptor is canonical Mercado Livre OAuth`() {
        val rotator = rotator(RecordingTransport { validResponse() })

        assertEquals("br.com.mercadolivre", rotator.descriptor.providerKey.value)
        assertEquals(
            CredentialKind.OAUTH2_AUTHORIZATION_CODE,
            rotator.descriptor.credentialKind
        )
    }

    @Test
    fun `non expired envelope is usable without HTTP`() {
        val transport = RecordingTransport { validResponse() }
        val rotator = rotator(transport)
        val bytes = encoded(now.plusSeconds(60))

        try {
            assertEquals(
                CredentialRotationAssessment.USABLE,
                rotator.assess(bytes, now)
            )
            assertEquals(0, transport.calls.get())
        } finally {
            bytes.fill(0)
        }
    }

    @Test
    fun `expired envelope requires refresh`() {
        val transport = RecordingTransport { validResponse() }
        val bytes = encoded(now.minusSeconds(1))

        try {
            assertEquals(
                CredentialRotationAssessment.REFRESH_REQUIRED,
                rotator(transport).assess(bytes, now)
            )
        } finally {
            bytes.fill(0)
        }
    }

    @Test
    fun `malformed envelope requires authentication without HTTP`() {
        val transport = RecordingTransport { validResponse() }
        val malformed = """{"schemaVersion":1}""".toByteArray()

        try {
            assertEquals(
                CredentialRotationAssessment.AUTHENTICATION_REQUIRED,
                rotator(transport).assess(malformed, now)
            )
            assertEquals(0, transport.calls.get())
        } finally {
            malformed.fill(0)
        }
    }

    @Test
    fun `refresh request uses exact form fields and no internal retry`() {
        val transport = RecordingTransport { validResponse(expiresIn = 17) }
        val bytes = encoded(now.minusSeconds(1))

        val result = try {
            rotator(transport).refresh(
                bytes,
                CredentialRotationRemoteContext(now.plusSeconds(20)),
                CredentialRotationCancellation { true }
            )
        } finally {
            bytes.fill(0)
        }

        val replacementResult = assertIs<CredentialRefreshResult.Replacement>(result)
        assertEquals(1, transport.calls.get())
        assertEquals(
            "grant_type=refresh_token&client_id=client-123&" +
                "client_secret=synthetic-client-secret&" +
                "refresh_token=synthetic-refresh-token",
            transport.lastBody
        )

        replacementResult.credential.use { credential ->
            credential.useBytes { replacementBytes ->
                val replacement = assertNotNull(
                    MercadoLivreOAuthCredentialEnvelopeCodec.decode(replacementBytes)
                )
                assertEquals("new-access-token", replacement.accessToken)
                assertEquals("new-refresh-token", replacement.refreshToken)
                assertEquals(now.plusSeconds(17), replacement.accessTokenExpiresAt)
                assertEquals(8035443L, replacement.authorizedUserId)
            }
        }
    }

    @Test
    fun `HTTPS endpoint is mandatory and default transport never redirects`() {
        assertFailsWith<IllegalArgumentException> {
            MercadoLivreOAuthCredentialRotator(
                URI.create("http://example.test/oauth/token"),
                clock,
                RecordingTransport { validResponse() }
            )
        }

        assertEquals(
            HttpClient.Redirect.NEVER,
            JdkMercadoLivreTokenHttpTransport.REDIRECT_POLICY
        )
    }

    @Test
    fun `mismatched user missing refresh token and malformed success are indeterminate`() {
        val scenarios = listOf(
            validResponse(userId = 999L),
            MercadoLivreTokenHttpResponse(
                200,
                """
                    {
                      "access_token":"new-access-token",
                      "token_type":"bearer",
                      "expires_in":21600,
                      "user_id":8035443
                    }
                """.trimIndent().toByteArray()
            ),
            MercadoLivreTokenHttpResponse(200, "not-json".toByteArray())
        )

        scenarios.forEach { response ->
            val transport = RecordingTransport { response }
            val bytes = encoded(now.minusSeconds(1))
            val result = try {
                rotator(transport).refresh(
                    bytes,
                    CredentialRotationRemoteContext(now.plusSeconds(20)),
                    CredentialRotationCancellation.NEVER
                )
            } finally {
                bytes.fill(0)
            }
            assertEquals(CredentialRefreshResult.Indeterminate, result)
            assertEquals(1, transport.calls.get())
        }
    }

    @Test
    fun `provider failures map with single use safety`() {
        val cases = listOf(
            MercadoLivreTokenHttpResponse(
                400,
                """{"error":"invalid_grant"}""".toByteArray()
            ) to CredentialRotationRemoteFailureKind.AUTHENTICATION_REQUIRED,
            MercadoLivreTokenHttpResponse(401, "{}".toByteArray()) to
                CredentialRotationRemoteFailureKind.AUTHENTICATION_REQUIRED,
            MercadoLivreTokenHttpResponse(403, "{}".toByteArray()) to
                CredentialRotationRemoteFailureKind.AUTHORIZATION_DENIED
        )

        cases.forEach { (response, expected) ->
            val bytes = encoded(now.minusSeconds(1))
            val result = try {
                rotator(RecordingTransport { response }).refresh(
                    bytes,
                    CredentialRotationRemoteContext(now.plusSeconds(20)),
                    CredentialRotationCancellation.NEVER
                )
            } finally {
                bytes.fill(0)
            }

            assertEquals(
                expected,
                assertIs<CredentialRefreshResult.TerminalFailure>(result).kind
            )
        }
    }

    @Test
    fun `explicit 429 is retryable with bounded hint`() {
        val bytes = encoded(now.minusSeconds(1))
        val result = try {
            rotator(
                RecordingTransport {
                    MercadoLivreTokenHttpResponse(
                        429,
                        "{}".toByteArray(),
                        retryAfter = "17"
                    )
                }
            ).refresh(
                bytes,
                CredentialRotationRemoteContext(now.plusSeconds(20)),
                CredentialRotationCancellation.NEVER
            )
        } finally {
            bytes.fill(0)
        }

        val retry = assertIs<CredentialRefreshResult.RetryableFailure>(result)
        assertEquals(CredentialRotationRemoteFailureKind.RATE_LIMITED, retry.kind)
        assertEquals(Duration.ofSeconds(17), retry.retryAfter)
    }

    @Test
    fun `5xx IO and oversized response are indeterminate`() {
        val serverFailure = refreshWith(
            RecordingTransport {
                MercadoLivreTokenHttpResponse(503, "{}".toByteArray())
            }
        )
        assertEquals(CredentialRefreshResult.Indeterminate, serverFailure)

        val ioTransport = object : MercadoLivreTokenHttpTransport {
            override fun post(
                endpoint: URI,
                body: ByteArray,
                timeout: Duration,
                maxResponseBytes: Int
            ): MercadoLivreTokenHttpResponse {
                throw IOException("synthetic transport failure")
            }
        }
        assertEquals(
            CredentialRefreshResult.Indeterminate,
            refreshWith(ioTransport)
        )

        val oversized = ByteArray(
            MercadoLivreOAuthCredentialRotator.MAX_RESPONSE_BYTES + 1
        ) { 'x'.code.toByte() }

        assertEquals(
            CredentialRefreshResult.Indeterminate,
            refreshWith(
                RecordingTransport {
                    MercadoLivreTokenHttpResponse(200, oversized)
                }
            )
        )
    }

    @Test
    fun `executor integration rotates exactly one Control Plane binding`() {
        val repository = MemoryControlPlaneRepository()
        val vault = MemoryVault()
        var sequence = 100L
        val control = IntegrationControlPlaneService(
            repository,
            vault,
            clock,
            organizationIds = IdentifierFactory {
                OrganizationId(UUID(0, sequence++))
            },
            connectionIds = IdentifierFactory {
                IntegrationConnectionId(UUID(1, sequence++))
            },
            auditIds = IdentifierFactory {
                IntegrationAuditEntryId(UUID(2, sequence++))
            },
            correlationIds = IdentifierFactory { UUID(3, sequence++) }
        )

        val organization = control.createOrganization()
        val connection = control.createConnection(
            organization.id,
            ProviderKey.of("br.com.mercadolivre"),
            CredentialKind.OAUTH2_AUTHORIZATION_CODE
        )
        control.bindInitialCredential(
            organization.id,
            connection.id,
            encoded(now.minusSeconds(1))
        )

        val transport = RecordingTransport { validResponse(expiresIn = 29) }
        val store = ImmediateRotationStore()
        val executor = CredentialRotationExecutor(
            IntegrationControlPlaneCredentialRotationAccess(control),
            store,
            listOf(rotator(transport)),
            clock
        )

        val outcome = executor.execute(
            CredentialRotationInvocation(
                organization.id,
                connection.id,
                CredentialRotationExecutionId(UUID.randomUUID()),
                now.plusSeconds(60)
            )
        )

        assertEquals(
            CredentialRotationSuccessKind.ROTATED,
            assertIs<CredentialRotationOutcome.Success>(outcome).kind
        )
        assertEquals(1, transport.calls.get())
        assertEquals(1, store.remoteStarts)
        assertEquals(1, store.completions)
        assertEquals(
            2,
            control.activeCredentialContext(
                organization.id,
                connection.id
            )?.bindingVersion
        )

        control.withActiveCredential(organization.id, connection.id) { bytes ->
            val current = assertNotNull(
                MercadoLivreOAuthCredentialEnvelopeCodec.decode(bytes)
            )
            assertEquals("new-access-token", current.accessToken)
            assertEquals("new-refresh-token", current.refreshToken)
            assertEquals(now.plusSeconds(29), current.accessTokenExpiresAt)
        }

        val rendered = outcome.toString()
        assertFalse(rendered.contains("new-access-token"))
        assertFalse(rendered.contains("new-refresh-token"))
        assertFalse(rendered.contains("synthetic-client-secret"))
    }

    private fun refreshWith(
        transport: MercadoLivreTokenHttpTransport
    ): CredentialRefreshResult {
        val bytes = encoded(now.minusSeconds(1))
        return try {
            rotator(transport).refresh(
                bytes,
                CredentialRotationRemoteContext(now.plusSeconds(20)),
                CredentialRotationCancellation.NEVER
            )
        } finally {
            bytes.fill(0)
        }
    }

    private fun rotator(transport: MercadoLivreTokenHttpTransport) =
        MercadoLivreOAuthCredentialRotator(
            URI.create("https://example.test/oauth/token"),
            clock,
            transport
        )

    private fun encoded(expiry: Instant): ByteArray =
        MercadoLivreOAuthCredentialEnvelopeCodec.encode(
            MercadoLivreOAuthCredentialEnvelope.create(
                clientId = "client-123",
                clientSecret = "synthetic-client-secret",
                authorizedUserId = 8035443L,
                accessToken = "synthetic-access-token",
                refreshToken = "synthetic-refresh-token",
                accessTokenExpiresAt = expiry
            )
        )

    private fun validResponse(
        expiresIn: Long = 21_600,
        userId: Long = 8035443L
    ) = MercadoLivreTokenHttpResponse(
        200,
        """
            {
              "access_token":"new-access-token",
              "token_type":"bearer",
              "expires_in":$expiresIn,
              "scope":"offline_access read",
              "user_id":$userId,
              "refresh_token":"new-refresh-token"
            }
        """.trimIndent().toByteArray()
    )
}

private class RecordingTransport(
    private val response: () -> MercadoLivreTokenHttpResponse
) : MercadoLivreTokenHttpTransport {
    val calls = AtomicInteger()
    var lastBody: String? = null

    override fun post(
        endpoint: URI,
        body: ByteArray,
        timeout: Duration,
        maxResponseBytes: Int
    ): MercadoLivreTokenHttpResponse {
        calls.incrementAndGet()
        lastBody = body.decodeToString()
        return response()
    }
}

private class ImmediateRotationStore : CredentialRotationExecutionStore {
    var remoteStarts = 0
    var completions = 0

    override fun claim(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        bindingVersion: Int,
        executionId: CredentialRotationExecutionId,
        claimedAt: Instant,
        leaseExpiresAt: Instant
    ) = CredentialRotationClaimResult(CredentialRotationClaimKind.ACQUIRED)

    override fun markRemoteStarted(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        bindingVersion: Int,
        executionId: CredentialRotationExecutionId,
        startedAt: Instant
    ): CredentialRotationRemoteStartResult {
        remoteStarts += 1
        return CredentialRotationRemoteStartResult.STARTED
    }

    override fun markRetryable(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        bindingVersion: Int,
        executionId: CredentialRotationExecutionId,
        retryNotBefore: Instant,
        updatedAt: Instant
    ): Boolean = true

    override fun markCompleted(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        bindingVersion: Int,
        executionId: CredentialRotationExecutionId,
        terminalAt: Instant
    ): Boolean {
        completions += 1
        return true
    }

    override fun markInDoubt(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        bindingVersion: Int,
        executionId: CredentialRotationExecutionId,
        terminalAt: Instant
    ): Boolean = true
}

private class MemoryVault : SecretVault {
    private val values = mutableMapOf<SecretReference, ByteArray>()
    private var sequence = 0

    override fun store(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        credentialBytes: ByteArray
    ): SecretReference = try {
        SecretReference.of("vault://ml-test/${++sequence}").also {
            values[it] = credentialBytes.copyOf()
        }
    } finally {
        credentialBytes.fill(0)
    }

    override fun <T> withSecret(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        reference: SecretReference,
        operation: (ByteArray) -> T
    ): T {
        val scoped = requireNotNull(values[reference]).copyOf()
        return try {
            operation(scoped)
        } finally {
            scoped.fill(0)
        }
    }

    override fun revoke(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        reference: SecretReference
    ) {
        values.remove(reference)?.fill(0)
    }
}

private class MemoryControlPlaneRepository : IntegrationControlPlaneRepository {
    private val organizations = mutableMapOf<OrganizationId, IntegrationOrganization>()
    private val connections =
        mutableMapOf<Pair<OrganizationId, IntegrationConnectionId>, IntegrationConnection>()
    private val bindings =
        mutableMapOf<Pair<OrganizationId, IntegrationConnectionId>, CredentialBinding>()

    override fun createOrganization(
        organization: IntegrationOrganization,
        audit: IntegrationAuditEntry
    ) {
        check(organizations.putIfAbsent(organization.id, organization) == null)
    }

    override fun findOrganization(id: OrganizationId): IntegrationOrganization? =
        organizations[id]

    override fun changeOrganizationStatus(
        id: OrganizationId,
        expected: IntegrationOrganizationStatus,
        updated: IntegrationOrganization,
        audit: IntegrationAuditEntry
    ): Boolean {
        val current = organizations[id] ?: return false
        if (current.status != expected) return false
        organizations[id] = updated
        return true
    }

    override fun createConnection(
        connection: IntegrationConnection,
        audit: IntegrationAuditEntry
    ) {
        connections[connection.organizationId to connection.id] = connection
    }

    override fun findConnection(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId
    ): IntegrationConnection? = connections[organizationId to connectionId]

    override fun bindInitialCredential(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        reference: SecretReference,
        now: Instant,
        audit: IntegrationAuditEntry
    ): Boolean {
        val key = organizationId to connectionId
        val connection = connections[key] ?: return false
        if (
            organizations[organizationId]?.status != IntegrationOrganizationStatus.ACTIVE ||
            connection.status != IntegrationConnectionStatus.DRAFT
        ) return false

        connections[key] = connection.copy(
            status = IntegrationConnectionStatus.ACTIVE,
            bindingVersion = 1,
            updatedAt = now
        )
        bindings[key] = CredentialBinding(
            organizationId,
            connectionId,
            1,
            reference,
            now,
            null
        )
        return true
    }

    override fun rotateCredential(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        expectedVersion: Int,
        newReference: SecretReference,
        now: Instant,
        audit: IntegrationAuditEntry
    ): SecretReference? {
        val key = organizationId to connectionId
        val connection = connections[key] ?: return null
        val binding = bindings[key] ?: return null
        if (
            organizations[organizationId]?.status != IntegrationOrganizationStatus.ACTIVE ||
            connection.status != IntegrationConnectionStatus.ACTIVE ||
            connection.bindingVersion != expectedVersion ||
            binding.version != expectedVersion ||
            binding.revokedAt != null
        ) return null

        connections[key] = connection.copy(
            bindingVersion = expectedVersion + 1,
            updatedAt = now
        )
        bindings[key] = CredentialBinding(
            organizationId,
            connectionId,
            expectedVersion + 1,
            newReference,
            now,
            null
        )
        return binding.secretReference
    }

    override fun currentBinding(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId
    ): CredentialBinding? = bindings[organizationId to connectionId]

    override fun changeConnectionStatus(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        expected: IntegrationConnectionStatus,
        target: IntegrationConnectionStatus,
        now: Instant,
        audit: IntegrationAuditEntry
    ): Boolean {
        val key = organizationId to connectionId
        val current = connections[key] ?: return false
        if (current.status != expected) return false
        connections[key] = current.copy(status = target, updatedAt = now)
        return true
    }

    override fun revokeConnection(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        now: Instant,
        audit: IntegrationAuditEntry
    ): Boolean {
        val key = organizationId to connectionId
        val current = connections[key] ?: return false
        val binding = bindings[key] ?: return false
        connections[key] = current.copy(
            status = IntegrationConnectionStatus.REVOKED,
            updatedAt = now
        )
        bindings[key] = binding.copy(revokedAt = now)
        return true
    }

    override fun registerDestination(
        destination: IntegrationDestination,
        audit: IntegrationAuditEntry
    ) = Unit

    override fun findDestination(
        organizationId: OrganizationId,
        destinationId: IntegrationDestinationId
    ): IntegrationDestination? = null

    override fun changeDestinationStatus(
        organizationId: OrganizationId,
        destinationId: IntegrationDestinationId,
        expected: IntegrationDestinationStatus,
        target: IntegrationDestinationStatus,
        now: Instant,
        audit: IntegrationAuditEntry
    ): Boolean = false

    override fun auditEntries(
        organizationId: OrganizationId
    ): List<IntegrationAuditEntry> = emptyList()
}