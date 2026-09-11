package io.flooow.marketplace.api

import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.credential.CredentialRotationExecutionId
import io.flooow.integration.credential.CredentialRotationExecutor
import io.flooow.integration.credential.CredentialRotationFailureKind
import io.flooow.integration.credential.CredentialRotationInvocation
import io.flooow.integration.credential.CredentialRotationOutcome
import io.flooow.organization.OrganizationId
import java.time.Clock
import java.time.Duration
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal const val MERCADO_LIVRE_CREDENTIAL_ROTATION_PATH =
    "/v1/integrations/mercadolivre/credential/rotate"

private val ROTATION_DEADLINE: Duration = Duration.ofMinutes(1)

internal class MercadoLivreCredentialRotationApi(
    private val executor: CredentialRotationExecutor,
    private val configuredConnectionId: IntegrationConnectionId?,
    private val clock: Clock = Clock.systemUTC()
) {
    fun rotate(organizationId: OrganizationId): JsonObject {
        val connectionId = configuredConnectionId
            ?: throw MercadoLivreCredentialRotationUnavailableException()

        return when (
            val outcome = executor.execute(
                CredentialRotationInvocation(
                    organizationId = organizationId,
                    connectionId = connectionId,
                    executionId = CredentialRotationExecutionId(UUID.randomUUID()),
                    deadline = clock.instant().plus(ROTATION_DEADLINE)
                )
            )
        ) {
            is CredentialRotationOutcome.Success -> buildJsonObject {
                put("status", outcome.kind.name)
                put("provider", outcome.providerKey.value)
            }

            is CredentialRotationOutcome.Failure ->
                throw MercadoLivreCredentialRotationFailureException(
                    outcome.kind,
                    outcome.retryAfter?.seconds
                )
        }
    }
}

internal class MercadoLivreCredentialRotationUnavailableException : RuntimeException()

internal class MercadoLivreCredentialRotationFailureException(
    val kind: CredentialRotationFailureKind,
    val retryAfterSeconds: Long?
) : RuntimeException()
