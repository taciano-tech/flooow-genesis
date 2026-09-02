package io.flooow.marketplace.operations.economics.evidence

import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.organization.OrganizationId
import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MarketplaceIndependentEconomicEvidencePersistenceTest {
    private val subject = MarketplaceEconomicEvidenceSubject(
        OrganizationId.parse("10000000-0000-0000-0000-000000000001"),
        MarketplaceOrderId.parse("20000000-0000-0000-0000-000000000001"),
        MarketplaceKey("mercado-livre"),
        MarketplaceExternalOrderId("sensitive-order-reference"),
        MarketplaceCurrency("BRL")
    )
    private val evidence = MarketplaceIndependentEconomicEvidence.empty(subject)
    private val version = MarketplaceEconomicEvidenceVersion(7)
    private val versionedEvidence = VersionedMarketplaceIndependentEconomicEvidence(evidence, version)

    @Test
    fun `version is non negative ordered value equal incrementable bounded and internal`() {
        assertEquals(0L, MarketplaceEconomicEvidenceVersion.ZERO.valueForPersistence())
        assertFailsWith<IllegalArgumentException> { MarketplaceEconomicEvidenceVersion(-1) }
        assertEquals(MarketplaceEconomicEvidenceVersion(7), version)
        assertTrue(MarketplaceEconomicEvidenceVersion(6) < version)
        assertTrue(MarketplaceEconomicEvidenceVersion(8) > version)
        assertEquals(8L, version.next().valueForPersistence())
        assertEquals(1L, MarketplaceEconomicEvidenceVersion.ZERO.next().valueForPersistence())
        assertFailsWith<IllegalStateException> {
            MarketplaceEconomicEvidenceVersion(Long.MAX_VALUE).next()
        }
        assertEquals("[INTERNAL]", version.toString())
        val rawValueReturnTypeNames = setOf("long", "java.lang.Long")
        assertEquals(
            listOf("valueForPersistence"),
            MarketplaceEconomicEvidenceVersion::class.java.methods
                .filter { Modifier.isPublic(it.modifiers) && it.returnType.name in rawValueReturnTypeNames }
                .map { it.name }
        )
        val rawValueField = MarketplaceEconomicEvidenceVersion::class.java.declaredFields
            .single { it.name == "value" }
        assertTrue(Modifier.isPrivate(rawValueField.modifiers))
    }

    @Test
    fun `versioned evidence has exact value semantics and redacted rendering`() {
        assertEquals(
            VersionedMarketplaceIndependentEconomicEvidence(evidence, version),
            versionedEvidence
        )
        assertSame(evidence, versionedEvidence.evidence)
        assertEquals(version, versionedEvidence.version)
        assertEquals("[REDACTED]", versionedEvidence.toString())
    }

    @Test
    fun `read result surface is closed payload preserving and redacted`() {
        assertEquals(
            setOf("Found", "IntegrityFailure", "NotFound"),
            MarketplaceIndependentEconomicEvidenceReadResult::class.java.declaredClasses
                .mapTo(mutableSetOf()) { it.simpleName }
        )
        val found = MarketplaceIndependentEconomicEvidenceReadResult.Found(versionedEvidence)
        assertSame(versionedEvidence, found.versionedEvidence)
        assertRedacted(
            found,
            MarketplaceIndependentEconomicEvidenceReadResult.NotFound,
            MarketplaceIndependentEconomicEvidenceReadResult.IntegrityFailure
        )
    }

    @Test
    fun `persist result surface is exact payload preserving and redacted`() {
        assertEquals(
            setOf(
                "Applied",
                "Duplicate",
                "FactAlreadySuperseded",
                "IdentifierConflict",
                "IntegrityFailure",
                "OrganizationUnavailable",
                "ReplacementIdentifierConflict",
                "ReplacementSourceFactConflict",
                "SourceFactConflict",
                "StaleVersion",
                "SubjectMismatch",
                "SupersededFactNotFound",
                "SupersededTargetNotFact"
            ),
            MarketplaceIndependentEconomicEvidencePersistResult::class.java.declaredClasses
                .mapTo(mutableSetOf()) { it.simpleName }
        )
        val applied = MarketplaceIndependentEconomicEvidencePersistResult.Applied(versionedEvidence)
        val duplicate = MarketplaceIndependentEconomicEvidencePersistResult.Duplicate(versionedEvidence)
        val stale = MarketplaceIndependentEconomicEvidencePersistResult.StaleVersion(version)
        assertSame(versionedEvidence, applied.versionedEvidence)
        assertSame(versionedEvidence, duplicate.versionedEvidence)
        assertEquals(version, stale.currentVersion)

        assertRedacted(
            applied,
            duplicate,
            stale,
            MarketplaceIndependentEconomicEvidencePersistResult.OrganizationUnavailable,
            MarketplaceIndependentEconomicEvidencePersistResult.SubjectMismatch,
            MarketplaceIndependentEconomicEvidencePersistResult.IdentifierConflict,
            MarketplaceIndependentEconomicEvidencePersistResult.SourceFactConflict,
            MarketplaceIndependentEconomicEvidencePersistResult.SupersededFactNotFound,
            MarketplaceIndependentEconomicEvidencePersistResult.SupersededTargetNotFact,
            MarketplaceIndependentEconomicEvidencePersistResult.FactAlreadySuperseded,
            MarketplaceIndependentEconomicEvidencePersistResult.ReplacementIdentifierConflict,
            MarketplaceIndependentEconomicEvidencePersistResult.ReplacementSourceFactConflict,
            MarketplaceIndependentEconomicEvidencePersistResult.IntegrityFailure
        )
    }

    @Test
    fun `repository port exposes exactly find and apply`() {
        val methods = MarketplaceIndependentEconomicEvidenceRepository::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
        assertEquals(2, methods.size)
        assertEquals(setOf("apply", "find"), methods.mapTo(mutableSetOf()) { it.name })

        val find = methods.single { it.name == "find" }
        assertContentEquals(arrayOf(MarketplaceEconomicEvidenceSubject::class.java), find.parameterTypes)
        assertEquals(MarketplaceIndependentEconomicEvidenceReadResult::class.java, find.returnType)

        val apply = methods.single { it.name == "apply" }
        assertContentEquals(
            arrayOf(
                MarketplaceEconomicEvidenceVersion::class.java,
                MarketplaceIndependentEconomicEvidenceUpdate::class.java
            ),
            apply.parameterTypes
        )
        assertEquals(MarketplaceIndependentEconomicEvidencePersistResult::class.java, apply.returnType)
    }

    @Test
    fun `observation identifier persistence bridge round trips canonically`() {
        val canonical = "30000000-0000-0000-0000-000000000001"
        val identifier = MarketplaceEconomicEvidenceObservationId.parse(canonical)
        val persisted = identifier.valueForPersistence()
        assertEquals(UUID.fromString(canonical), persisted)
        assertEquals(canonical, persisted.toString())
        assertEquals(identifier, MarketplaceEconomicEvidenceObservationId.parse(persisted.toString()))
    }

    @Test
    fun `compiled persistence contract contains no forbidden dependency or numeric type`() {
        val classes = java.nio.file.Path.of(
            MarketplaceEconomicEvidenceVersion::class.java.protectionDomain.codeSource.location.toURI()
        ).resolve("io/flooow/marketplace/operations/economics/evidence")
        val ownedPrefixes = listOf(
            "MarketplaceEconomicEvidenceVersion",
            "VersionedMarketplaceIndependentEconomicEvidence",
            "MarketplaceIndependentEconomicEvidenceReadResult",
            "MarketplaceIndependentEconomicEvidencePersistResult",
            "MarketplaceIndependentEconomicEvidenceRepository",
            "MarketplaceIndependentEconomicEvidencePersistenceKt"
        )
        val forbidden = listOf(
            "io/flooow/kernel",
            "java/sql",
            "javax/sql",
            "jdbc",
            "postgres",
            "flyway",
            "kotlinx/serialization",
            "json",
            "provider",
            "connector",
            "/api/",
            "/ui/",
            "java/lang/float",
            "java/lang/double"
        )
        var inspected = 0
        Files.walk(classes).use { files ->
            files.filter { path ->
                path.toString().endsWith(".class") &&
                    ownedPrefixes.any { prefix -> path.fileName.toString().startsWith(prefix) }
            }.forEach { classFile ->
                inspected += 1
                val bytecode = String(
                    Files.readAllBytes(classFile),
                    StandardCharsets.ISO_8859_1
                ).lowercase()
                forbidden.forEach { token -> assertFalse(token in bytecode, token) }
            }
        }
        assertTrue(inspected > 0)
    }

    private fun assertRedacted(vararg values: Any) {
        val sensitive = listOf(
            subject.organizationId.value.toString(),
            subject.orderId.value.toString(),
            subject.externalOrderId.value,
            version.valueForPersistence().toString()
        )
        values.forEach { value ->
            val rendering = value.toString()
            assertEquals("[REDACTED]", rendering)
            sensitive.forEach { secret -> assertFalse(secret in rendering) }
        }
    }
}
