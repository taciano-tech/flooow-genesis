package io.flooow.integration.security

import io.flooow.integration.connector.*
import io.flooow.integration.control.*
import io.flooow.organization.OrganizationId
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.nio.file.attribute.PosixFilePermission
import java.security.SecureRandom
import java.util.Base64
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

class MvpRuntimeMasterKey private constructor(private val bytes: ByteArray) : AutoCloseable {
    fun <T> useBytes(block: (ByteArray) -> T): T {
        val copy = bytes.copyOf()
        return try { block(copy) } finally { copy.fill(0) }
    }
    override fun close() = bytes.fill(0)
    override fun toString() = "[REDACTED]"
    companion object {
        const val ENV = "FLOOOW_RUNTIME_MASTER_KEY_BASE64"
        fun fromEnvironment(environment: Map<String,String> = System.getenv()): MvpRuntimeMasterKey {
            val encoded = environment[ENV] ?: error("Runtime master key is not configured")
            if (encoded != encoded.trim()) error("Runtime master key configuration is invalid")
            val decoded = try { Base64.getDecoder().decode(encoded) }
            catch (_: IllegalArgumentException) { error("Runtime master key configuration is invalid") }
            try {
                if (decoded.size != 32) error("Runtime master key configuration is invalid")
                return MvpRuntimeMasterKey(decoded.copyOf())
            } finally { decoded.fill(0) }
        }
    }
}

class MvpSecureRuntime(
    masterKey: MvpRuntimeMasterKey,
    vaultRoot: Path,
    random: SecureRandom = SecureRandom()
) : AutoCloseable {
    private val vaultKey = derive(masterKey, "flooow.secret-vault.v1")
    private val progressKey = derive(masterKey, "flooow.connector-progress.v1")
    val secretVault: SecretVault = EncryptedFileSecretVault(vaultRoot, vaultKey, random)
    val progressProtector: ConnectorProgressProtector =
        AesGcmConnectorProgressProtector(progressKey, random)
    override fun close() { vaultKey.fill(0); progressKey.fill(0) }
    private fun derive(master: MvpRuntimeMasterKey, label: String): ByteArray =
        master.useBytes {
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(it, "HmacSHA256"))
                doFinal(label.toByteArray(StandardCharsets.UTF_8))
            }
        }
}

class EncryptedFileSecretVault(
    root: Path,
    keyBytes: ByteArray,
    private val random: SecureRandom = SecureRandom()
) : SecretVault, AutoCloseable {
    private val root = root.toAbsolutePath().normalize()
    private val key = keyBytes.copyOf()
    init { require(key.size == 32); Files.createDirectories(this.root) }

    override fun store(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        credentialBytes: ByteArray
    ): SecretReference {
        require(credentialBytes.size in 1..65536) { "Credential size is invalid" }
        repeat(8) {
            val id = UUID.randomUUID()
            val encoded = "fsv1:$id"
            val ref = SecretReference.of(encoded)
            val target = pathFor(organizationId, connectionId, ref)
            if (Files.exists(target)) return@repeat
            val nonce = ByteArray(12).also(random::nextBytes)
            val aad = vaultAad(organizationId, connectionId, encoded)
            val envelope = encrypt(FSV1, key, nonce, aad, credentialBytes)
            try {
                Files.createDirectories(target.parent); secureDir(target.parent)
                val temp = Files.createTempFile(target.parent, ".pending-", ".secret")
                try {
                    Files.write(temp, envelope); secureFile(temp)
                    try { Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE) }
                    catch (_: AtomicMoveNotSupportedException) { Files.move(temp,target) }
                    secureFile(target)
                    return ref
                } finally { Files.deleteIfExists(temp) }
            } finally { nonce.fill(0); aad.fill(0); envelope.fill(0) }
        }
        error("Unable to allocate secret reference")
    }

    override fun <T> withSecret(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        reference: SecretReference,
        operation: (ByteArray) -> T
    ): T {
        val encoded = canonical(reference)
        val target = pathFor(organizationId, connectionId, reference)
        val envelope = try { Files.readAllBytes(target) } catch (_: Exception) { error("Secret is unavailable") }
        val aad = vaultAad(organizationId, connectionId, encoded)
        val plain = try { decrypt(FSV1,key,aad,envelope) }
        catch (_: Exception) { error("Secret is unavailable") }
        finally { envelope.fill(0); aad.fill(0) }
        return try { operation(plain) } finally { plain.fill(0) }
    }

    override fun revoke(
        organizationId: OrganizationId,
        connectionId: IntegrationConnectionId,
        reference: SecretReference
    ) {
        canonical(reference)
        try { Files.deleteIfExists(pathFor(organizationId,connectionId,reference)) }
        catch (_: Exception) { error("Secret revocation failed") }
    }

    override fun close() = key.fill(0)

    private fun canonical(reference: SecretReference): String {
        val e = reference.encodedForPersistence()
        require(e.startsWith("fsv1:")) { "Invalid secret reference" }
        val raw=e.removePrefix("fsv1:")
        val id=try{UUID.fromString(raw)}catch(_:IllegalArgumentException){throw IllegalArgumentException("Invalid secret reference")}
        require(id.toString()==raw && e=="fsv1:$raw"){"Invalid secret reference"}
        return e
    }
    private fun pathFor(o:OrganizationId,c:IntegrationConnectionId,r:SecretReference):Path{
        val uuid=canonical(r).removePrefix("fsv1:")
        val p=root.resolve(o.value.toString()).resolve(c.value.toString()).resolve("$uuid.secret").normalize()
        require(p.startsWith(root)){"Invalid secret reference"}
        return p
    }
}

class AesGcmConnectorProgressProtector(
    keyBytes: ByteArray,
    private val random: SecureRandom = SecureRandom()
) : ConnectorProgressProtector, AutoCloseable {
    private val key=keyBytes.copyOf()
    init{require(key.size==32)}
    override fun seal(context:ConnectorProgressProtectionContext,plaintextBytes:ByteArray):SealedConnectorProgress{
        require(plaintextBytes.size in 1..ConnectorProgress.MAX_BYTES)
        val nonce=ByteArray(12).also(random::nextBytes)
        val aad=progressAad(context)
        val envelope=encrypt(FCP1,key,nonce,aad,plaintextBytes)
        return try{SealedConnectorProgress.take(envelope.copyOf())}
        finally{nonce.fill(0);aad.fill(0);envelope.fill(0)}
    }
    override fun open(context:ConnectorProgressProtectionContext,sealedProgress:SealedConnectorProgress):ByteArray{
        val aad=progressAad(context)
        return try{
            sealedProgress.useBytes{decrypt(FCP1,key,aad,it).also{p->require(p.size in 1..ConnectorProgress.MAX_BYTES)}}
        }catch(_:Exception){error("Protected connector progress is unavailable")}
        finally{aad.fill(0)}
    }
    override fun close()=key.fill(0)
}

private val FSV1=byteArrayOf(70,83,86,49)
private val FCP1=byteArrayOf(70,67,80,49)

private fun encrypt(magic:ByteArray,key:ByteArray,nonce:ByteArray,aad:ByteArray,plain:ByteArray):ByteArray{
    val cipher=Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,nonce))
    cipher.updateAAD(aad)
    val ct=cipher.doFinal(plain)
    return try{ByteBuffer.allocate(magic.size+nonce.size+ct.size).put(magic).put(nonce).put(ct).array()}
    finally{ct.fill(0)}
}
private fun decrypt(magic:ByteArray,key:ByteArray,aad:ByteArray,envelope:ByteArray):ByteArray{
    require(envelope.size>magic.size+12+16)
    require(envelope.copyOfRange(0,4).contentEquals(magic))
    val nonce=envelope.copyOfRange(4,16)
    val ct=envelope.copyOfRange(16,envelope.size)
    return try{
        Cipher.getInstance("AES/GCM/NoPadding").run{
            init(Cipher.DECRYPT_MODE,SecretKeySpec(key,"AES"),GCMParameterSpec(128,nonce))
            updateAAD(aad); doFinal(ct)
        }
    }finally{nonce.fill(0);ct.fill(0)}
}
private fun vaultAad(o:OrganizationId,c:IntegrationConnectionId,r:String)=
    listOf("FSV1",o.value.toString(),c.value.toString(),r).joinToString("\n").toByteArray(StandardCharsets.UTF_8)
private fun progressAad(c:ConnectorProgressProtectionContext)=
    listOf("FCP1",c.organizationId.value.toString(),c.connectionId.value.toString(),c.capability.value,c.progressVersion.toString())
        .joinToString("\n").toByteArray(StandardCharsets.UTF_8)
private fun secureDir(p:Path){try{Files.setPosixFilePermissions(p,setOf(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE,PosixFilePermission.OWNER_EXECUTE))}catch(_:UnsupportedOperationException){}}
private fun secureFile(p:Path){try{Files.setPosixFilePermissions(p,setOf(PosixFilePermission.OWNER_READ,PosixFilePermission.OWNER_WRITE))}catch(_:UnsupportedOperationException){}}