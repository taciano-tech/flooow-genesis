package io.flooow.integration.credential

import io.flooow.integration.control.ActiveCredentialContext
import io.flooow.integration.control.CredentialKind
import io.flooow.integration.control.CredentialRotationResult
import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.control.ProviderKey
import io.flooow.organization.OrganizationId
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CredentialRotationExecutorTest {
    private val now = Instant.parse("2026-09-06T18:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val org = OrganizationId(UUID(0, 1))
    private val connection = IntegrationConnectionId(UUID(1, 1))
    private val provider = ProviderKey.of("test.oauth")

    @Test fun `unknown rotator fails before secret resolution`() {
        val access = FakeAccess(context())
        val out = executor(access, FakeStore(), emptyList()).execute(invocation())
        assertEquals(CredentialRotationFailureKind.ROTATOR_UNAVAILABLE, assertIs<CredentialRotationOutcome.Failure>(out).kind)
        assertEquals(0, access.secretUses.get())
    }

    @Test fun `usable and auth-required assessment make no claim`() {
        listOf(
            CredentialRotationAssessment.USABLE to CredentialRotationSuccessKind.READY,
            CredentialRotationAssessment.AUTHENTICATION_REQUIRED to null
        ).forEach { (assessment, success) ->
            val store = FakeStore(); val rotator = FakeRotator(provider, assessment)
            val out = executor(FakeAccess(context()), store, listOf(rotator)).execute(invocation())
            if (success != null) assertEquals(success, assertIs<CredentialRotationOutcome.Success>(out).kind)
            else assertEquals(CredentialRotationFailureKind.AUTHENTICATION_REQUIRED, assertIs<CredentialRotationOutcome.Failure>(out).kind)
            assertEquals(0, store.claims.get()); assertEquals(0, rotator.refreshes.get())
        }
    }

    @Test fun `replacement uses control plane authority and completes fence`() {
        val access = FakeAccess(context())
        val store = FakeStore()
        val marker = "replacement-private-marker"
        val rotator = FakeRotator(provider, CredentialRotationAssessment.REFRESH_REQUIRED) {
            CredentialRefreshResult.Replacement(ReplacementCredential.take(marker.toByteArray()))
        }
        val out = executor(access, store, listOf(rotator)).execute(invocation())
        assertEquals(CredentialRotationSuccessKind.ROTATED, assertIs<CredentialRotationOutcome.Success>(out).kind)
        assertEquals(2, access.context.bindingVersion); assertTrue(store.completed); assertTrue(store.started)
        assertFalse(out.toString().contains(marker))
    }

    @Test fun `retryable terminal and indeterminate remain controlled`() {
        val retryStore = FakeStore()
        val retry = executor(FakeAccess(context()), retryStore, listOf(
            FakeRotator(provider, CredentialRotationAssessment.REFRESH_REQUIRED) {
                CredentialRefreshResult.RetryableFailure.of(
                    CredentialRotationRemoteFailureKind.RATE_LIMITED, Duration.ofSeconds(12)
                )
            }
        )).execute(invocation())
        val retryFailure = assertIs<CredentialRotationOutcome.Failure>(retry)
        assertEquals(CredentialRotationFailureKind.RATE_LIMITED, retryFailure.kind)
        assertEquals(Duration.ofSeconds(12), retryFailure.retryAfter); assertTrue(retryStore.retryable)

        val terminalStore = FakeStore()
        val terminal = executor(FakeAccess(context()), terminalStore, listOf(
            FakeRotator(provider, CredentialRotationAssessment.REFRESH_REQUIRED) {
                CredentialRefreshResult.TerminalFailure.of(CredentialRotationRemoteFailureKind.AUTHENTICATION_REQUIRED)
            }
        )).execute(invocation())
        assertEquals(CredentialRotationFailureKind.AUTHENTICATION_REQUIRED, assertIs<CredentialRotationOutcome.Failure>(terminal).kind)
        assertTrue(terminalStore.completed)

        val doubtStore = FakeStore()
        val doubt = executor(FakeAccess(context()), doubtStore, listOf(
            FakeRotator(provider, CredentialRotationAssessment.REFRESH_REQUIRED) { CredentialRefreshResult.Indeterminate }
        )).execute(invocation())
        assertEquals(CredentialRotationFailureKind.ROTATION_IN_DOUBT, assertIs<CredentialRotationOutcome.Failure>(doubt).kind)
        assertTrue(doubtStore.inDoubt)
    }

    @Test fun `provider exception after remote start becomes in doubt`() {
        val store = FakeStore(); val marker="provider-private-error"
        val out = executor(FakeAccess(context()), store, listOf(
            FakeRotator(provider, CredentialRotationAssessment.REFRESH_REQUIRED) { error(marker) }
        )).execute(invocation())
        assertEquals(CredentialRotationFailureKind.ROTATION_IN_DOUBT, assertIs<CredentialRotationOutcome.Failure>(out).kind)
        assertTrue(store.inDoubt); assertFalse(out.toString().contains(marker))
    }

    @Test fun `stale replacement cannot overwrite newer binding`() {
        val access = FakeAccess(context()).apply { rotationResult = CredentialRotationResult.STALE_VERSION }
        val store = FakeStore()
        val out = executor(access, store, listOf(
            FakeRotator(provider, CredentialRotationAssessment.REFRESH_REQUIRED) {
                CredentialRefreshResult.Replacement(ReplacementCredential.take("stale".toByteArray()))
            }
        )).execute(invocation())
        assertEquals(CredentialRotationFailureKind.CREDENTIAL_VERSION_CHANGED, assertIs<CredentialRotationOutcome.Failure>(out).kind)
        assertTrue(store.completed); assertEquals(1, access.context.bindingVersion)
    }

    @Test fun `cancellation horizon and duplicate registration fail closed`() {
        val rotator=FakeRotator(provider, CredentialRotationAssessment.REFRESH_REQUIRED)
        val store=FakeStore(); val access=FakeAccess(context())
        val cancelled=executor(access,store,listOf(rotator)).execute(invocation(), CredentialRotationCancellation { true })
        assertEquals(CredentialRotationFailureKind.CANCELLED, assertIs<CredentialRotationOutcome.Failure>(cancelled).kind)
        val long=executor(access,store,listOf(rotator)).execute(invocation(now.plus(Duration.ofMinutes(6))))
        assertEquals(CredentialRotationFailureKind.BUDGET_EXCEEDED, assertIs<CredentialRotationOutcome.Failure>(long).kind)
        assertEquals(0, rotator.refreshes.get()); assertEquals(0, store.claims.get())
        assertFails { executor(access, store, listOf(rotator, FakeRotator(provider, CredentialRotationAssessment.USABLE))) }
    }

    private fun context() = ActiveCredentialContext(provider, CredentialKind.OAUTH2_AUTHORIZATION_CODE, 1)
    private fun invocation(deadline: Instant = now.plusSeconds(60)) = CredentialRotationInvocation(
        org, connection, CredentialRotationExecutionId(UUID.randomUUID()), deadline
    )
    private fun executor(a: FakeAccess, s: FakeStore, r: Collection<CredentialRotator>) =
        CredentialRotationExecutor(a, s, r, clock)
}

private class FakeAccess(var context: ActiveCredentialContext) : CredentialRotationCredentialAccess {
    val secretUses=AtomicInteger(); var rotationResult=CredentialRotationResult.ROTATED
    override fun activeContext(organizationId: OrganizationId, connectionId: IntegrationConnectionId)=context
    override fun <T> withActiveCredentialContext(
        organizationId: OrganizationId, connectionId: IntegrationConnectionId,
        operation: (ActiveCredentialContext, ByteArray) -> T
    ): T {
        secretUses.incrementAndGet(); val bytes="synthetic-current".toByteArray()
        return try { operation(context,bytes) } finally { bytes.fill(0) }
    }
    override fun rotate(
        organizationId: OrganizationId, connectionId: IntegrationConnectionId,
        expectedVersion: Int, replacementBytes: ByteArray
    ): CredentialRotationResult = try {
        if (rotationResult == CredentialRotationResult.ROTATED ||
            rotationResult == CredentialRotationResult.ROTATED_CLEANUP_REQUIRED) {
            context=context.copy(bindingVersion=expectedVersion+1)
        }
        rotationResult
    } finally { replacementBytes.fill(0) }
}

private class FakeRotator(
    provider: ProviderKey,
    private val assessment: CredentialRotationAssessment,
    private val result: () -> CredentialRefreshResult = { CredentialRefreshResult.Indeterminate }
) : CredentialRotator {
    override val descriptor=CredentialRotatorDescriptor(provider, CredentialKind.OAUTH2_AUTHORIZATION_CODE)
    val refreshes=AtomicInteger()
    override fun assess(credentialBytes: ByteArray, now: Instant)=assessment
    override fun refresh(
        credentialBytes: ByteArray, context: CredentialRotationRemoteContext,
        cancellation: CredentialRotationCancellation
    ): CredentialRefreshResult { refreshes.incrementAndGet(); return result() }
}

private class FakeStore : CredentialRotationExecutionStore {
    val claims=AtomicInteger(); var started=false; var retryable=false; var completed=false; var inDoubt=false
    override fun claim(
        organizationId: OrganizationId, connectionId: IntegrationConnectionId, bindingVersion: Int,
        executionId: CredentialRotationExecutionId, claimedAt: Instant, leaseExpiresAt: Instant
    ): CredentialRotationClaimResult { claims.incrementAndGet(); return CredentialRotationClaimResult(CredentialRotationClaimKind.ACQUIRED) }
    override fun markRemoteStarted(
        organizationId: OrganizationId, connectionId: IntegrationConnectionId, bindingVersion: Int,
        executionId: CredentialRotationExecutionId, startedAt: Instant
    ): CredentialRotationRemoteStartResult { started=true; return CredentialRotationRemoteStartResult.STARTED }
    override fun markRetryable(
        organizationId: OrganizationId, connectionId: IntegrationConnectionId, bindingVersion: Int,
        executionId: CredentialRotationExecutionId, retryNotBefore: Instant, updatedAt: Instant
    ): Boolean { retryable=true; return true }
    override fun markCompleted(
        organizationId: OrganizationId, connectionId: IntegrationConnectionId, bindingVersion: Int,
        executionId: CredentialRotationExecutionId, terminalAt: Instant
    ): Boolean { completed=true; return true }
    override fun markInDoubt(
        organizationId: OrganizationId, connectionId: IntegrationConnectionId, bindingVersion: Int,
        executionId: CredentialRotationExecutionId, terminalAt: Instant
    ): Boolean { inDoubt=true; return true }
}