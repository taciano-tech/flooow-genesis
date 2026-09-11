package io.flooow.marketplace.api

import io.flooow.integration.control.CredentialKind
import io.flooow.integration.control.IntegrationConnectionId
import io.flooow.integration.control.IntegrationControlPlaneService
import io.flooow.integration.control.ProviderKey
import io.flooow.organization.OrganizationId
import java.nio.charset.StandardCharsets
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class OmieStaticCredentialBootstrapResult(
    val connectionId: IntegrationConnectionId
)

class OmieStaticCredentialBootstrap(
    private val controlPlane: IntegrationControlPlaneService
) {
    fun bootstrap(
        organizationId: OrganizationId,
        appKey: String,
        appSecret: String
    ): OmieStaticCredentialBootstrapResult {
        require(appKey.isNotBlank() && appSecret.isNotBlank())
        val connection = controlPlane.createConnection(
            organizationId,
            ProviderKey.of("omie"),
            CredentialKind.STATIC_API_CREDENTIAL
        )
        val envelope = buildJsonObject {
            put("schemaVersion", 1)
            put("appKey", appKey)
            put("appSecret", appSecret)
        }.toString().toByteArray(StandardCharsets.UTF_8)
        try {
            controlPlane.bindInitialCredential(organizationId, connection.id, envelope)
        } finally {
            envelope.fill(0)
        }
        return OmieStaticCredentialBootstrapResult(connection.id)
    }
}
