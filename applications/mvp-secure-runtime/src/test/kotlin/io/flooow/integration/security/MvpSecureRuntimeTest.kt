package io.flooow.integration.security

import io.flooow.integration.connector.*
import io.flooow.integration.control.*
import io.flooow.organization.OrganizationId
import java.nio.file.Files
import java.util.Base64
import java.util.UUID
import kotlin.io.path.createTempDirectory
import kotlin.test.*

class MvpSecureRuntimeTest {
    private val oa=OrganizationId(UUID(0,901))
    private val ob=OrganizationId(UUID(0,902))
    private val ca=IntegrationConnectionId(UUID(0,903))
    private val cb=IntegrationConnectionId(UUID(0,904))

    @Test fun `master key configuration strict and redacted`() {
        val raw=ByteArray(32){(it+1).toByte()}
        val valid=Base64.getEncoder().encodeToString(raw)
        MvpRuntimeMasterKey.fromEnvironment(mapOf(MvpRuntimeMasterKey.ENV to valid)).close()
        listOf(
            emptyMap(),
            mapOf(MvpRuntimeMasterKey.ENV to " $valid"),
            mapOf(MvpRuntimeMasterKey.ENV to "bad!!"),
            mapOf(MvpRuntimeMasterKey.ENV to Base64.getEncoder().encodeToString(ByteArray(31)))
        ).forEach{
            val e=runCatching{MvpRuntimeMasterKey.fromEnvironment(it)}.exceptionOrNull()
            assertNotNull(e); assertFalse(e.message.orEmpty().contains(valid))
        }
    }

    @Test fun `vault restart scope tamper revoke and reference safety`() {
        val root=createTempDirectory("flooow-vault")
        val key=ByteArray(32){(it+10).toByte()}
        val plain="oauth-secret".toByteArray()
        val v1=EncryptedFileSecretVault(root,key)
        val ref=v1.store(oa,ca,plain)
        assertTrue(ref.encodedForPersistence().startsWith("fsv1:"))
        assertFalse(ref.toString().contains("fsv1:"))
        v1.close()

        val v2=EncryptedFileSecretVault(root,key)
        v2.withSecret(oa,ca,ref){assertContentEquals(plain,it)}
        assertFails{v2.withSecret(ob,ca,ref){}}
        assertFails{v2.withSecret(oa,cb,ref){}}
        assertFails{v2.withSecret(oa,ca,SecretReference.of("fsv1:../../escape")){}}

        val id=ref.encodedForPersistence().removePrefix("fsv1:")
        val p=root.resolve(oa.value.toString()).resolve(ca.value.toString()).resolve("$id.secret")
        val bytes=Files.readAllBytes(p)
        bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte()
        Files.write(p,bytes)
        assertFails{v2.withSecret(oa,ca,ref){}}
        v2.revoke(oa,ca,ref); v2.revoke(oa,ca,ref)
        assertFails{v2.withSecret(oa,ca,ref){}}
        v2.close()
    }

    @Test fun `vault fresh stores differ`() {
        val root=createTempDirectory("flooow-vault2")
        val v=EncryptedFileSecretVault(root,ByteArray(32){7})
        val a=v.store(oa,ca,"same".toByteArray())
        val b=v.store(oa,ca,"same".toByteArray())
        assertNotEquals(a,b)
        v.close()
    }

    @Test fun `progress randomized authenticated and context bound`() {
        val p=AesGcmConnectorProgressProtector(ByteArray(32){9})
        val ctx=ConnectorProgressProtectionContext(oa,ca,ConnectorCapability.of("marketplace-economic.order-source"),7)
        val plain="v1|hour=2026-09-07T14:00:00Z|offset=50".toByteArray()
        val a=p.seal(ctx,plain); val b=p.seal(ctx,plain)
        val ea=a.useBytes{it.copyOf()}; val eb=b.useBytes{it.copyOf()}
        assertFalse(ea.contentEquals(eb))
        assertTrue(ea.size<=SealedConnectorProgress.MAX_BYTES)
        assertContentEquals(plain,p.open(ctx,a))
        listOf(
            ctx.copy(organizationId=ob),
            ctx.copy(connectionId=cb),
            ctx.copy(capability=ConnectorCapability.of("other.source")),
            ctx.copy(progressVersion=8)
        ).forEach{assertFails{p.open(it,a)}}
        val tampered=ea.copyOf(); tampered[tampered.lastIndex]=(tampered.last().toInt() xor 1).toByte()
        val sealed=SealedConnectorProgress.take(tampered)
        assertFails{p.open(ctx,sealed)}
        a.close(); b.close(); sealed.close(); p.close()
    }
}