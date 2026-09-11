package io.flooow.marketplace.api

import io.flooow.integration.control.ActiveCredentialContext
import io.flooow.integration.control.CredentialKind
import io.flooow.integration.control.CredentialRotationResult
import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.control.ProviderKey
import io.flooow.integration.credential.CredentialRefreshResult
import io.flooow.integration.credential.CredentialRotationAssessment
import io.flooow.integration.credential.CredentialRotationCancellation
import io.flooow.integration.credential.CredentialRotationClaimKind
import io.flooow.integration.credential.CredentialRotationClaimResult
import io.flooow.integration.credential.CredentialRotationCredentialAccess
import io.flooow.integration.credential.CredentialRotationExecutionId
import io.flooow.integration.credential.CredentialRotationExecutionStore
import io.flooow.integration.credential.CredentialRotationExecutor
import io.flooow.integration.credential.CredentialRotationRemoteContext
import io.flooow.integration.credential.CredentialRotationRemoteStartResult
import io.flooow.integration.credential.CredentialRotator
import io.flooow.integration.credential.CredentialRotatorDescriptor
import io.flooow.organization.OrganizationId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.jsonPrimitive

class MercadoLivreCredentialRotationApiTest {

    private val organizationId =
        OrganizationId(UUID.fromString("7b6798b7-eefa-4493-9053-4515f0a39c4c"))

    private val connectionId =
        IntegrationConnectionId(UUID.fromString("33e7a252-292c-44ef-bab8-7a06b662c0fb"))

    private val now = Instant.parse("2026-09-10T19:00:00Z")

    @Test
    fun `usable Mercado Livre credential returns READY without exposing credential`() {
        val executor = CredentialRotationExecutor(
            FakeCredentialAccess(),
            FakeRotationStore(),
            listOf(UsableMercadoLivreRotator()),
            Clock.fixed(now, ZoneOffset.UTC)
        )

        val api = MercadoLivreCredentialRotationApi(
            executor,
            connectionId,
            Clock.fixed(now, ZoneOffset.UTC)
        )

        val response = api.rotate(organizationId)

        assertEquals("READY", response.getValue("status").jsonPrimitive.content)
        assertEquals(
            "br.com.mercadolivre",
            response.getValue("provider").jsonPrimitive.content
        )
        assertEquals(setOf("status", "provider"), response.keys)
    }

    private inner class FakeCredentialAccess : CredentialRotationCredentialAccess {
        private val provider = ProviderKey.of("br.com.mercadolivre")

        override fun activeContext(
            organizationId: OrganizationId,
            connectionId: IntegrationConnectionId
        ): ActiveCredentialContext =
            ActiveCredentialContext(
                provider,
                CredentialKind.OAUTH2_AUTHORIZATION_CODE,
                1
            )

        override fun <T> withActiveCredentialContext(
            organizationId: OrganizationId,
            connectionId: IntegrationConnectionId,
            operation: (ActiveCredentialContext, ByteArray) -> T
        ): T {
            val context = activeContext(organizationId, connectionId)
            return operation(context, "synthetic-credential".toByteArray())
        }

        override fun rotate(
            organizationId: OrganizationId,
            connectionId: IntegrationConnectionId,
            expectedVersion: Int,
            replacementBytes: ByteArray
        ): CredentialRotationResult = CredentialRotationResult.ROTATED
    }

    private class UsableMercadoLivreRotator : CredentialRotator {
        override val descriptor = CredentialRotatorDescriptor(
            ProviderKey.of("br.com.mercadolivre"),
            CredentialKind.OAUTH2_AUTHORIZATION_CODE
        )

        override fun assess(
            credentialBytes: ByteArray,
            now: Instant
        ): CredentialRotationAssessment =
            CredentialRotationAssessment.USABLE

        override fun refresh(
            credentialBytes: ByteArray,
            context: CredentialRotationRemoteContext,
            cancellation: CredentialRotationCancellation
        ): CredentialRefreshResult =
            error("refresh must not run for usable credential")
    }

    private class FakeRotationStore : CredentialRotationExecutionStore {
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
        ) = CredentialRotationRemoteStartResult.STARTED

        override fun markRetryable(
            organizationId: OrganizationId,
            connectionId: IntegrationConnectionId,
            bindingVersion: Int,
            executionId: CredentialRotationExecutionId,
            retryNotBefore: Instant,
            updatedAt: Instant
        ) = true

        override fun markCompleted(
            organizationId: OrganizationId,
            connectionId: IntegrationConnectionId,
            bindingVersion: Int,
            executionId: CredentialRotationExecutionId,
            terminalAt: Instant
        ) = true

        override fun markInDoubt(
            organizationId: OrganizationId,
            connectionId: IntegrationConnectionId,
            bindingVersion: Int,
            executionId: CredentialRotationExecutionId,
            terminalAt: Instant
        ) = true
    }
}
