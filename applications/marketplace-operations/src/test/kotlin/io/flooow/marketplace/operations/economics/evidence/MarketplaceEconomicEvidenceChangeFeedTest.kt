package io.flooow.marketplace.operations.economics.evidence

import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.organization.OrganizationId
import java.lang.reflect.Modifier
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MarketplaceEconomicEvidenceChangeFeedTest {
    private val organizationId = OrganizationId.parse("10000000-0000-0000-0000-000000000001")
    private val subject = MarketplaceEconomicEvidenceSubject(
        organizationId,
        MarketplaceOrderId.parse("20000000-0000-0000-0000-000000000001"),
        MarketplaceKey("mercado-livre"),
        MarketplaceExternalOrderId("sensitive-order-reference"),
        MarketplaceCurrency("BRL")
    )
    private val checkpoint = ChangeSequenceCheckpoint(17)
    private val projectionName = ProjectionName("sales-intelligence")
    private val version = MarketplaceEconomicEvidenceVersion(7)
    private val change = MarketplaceEconomicEvidenceChange(
        subject,
        version,
        checkpoint,
        MarketplaceEconomicEvidenceChangeKind.FACT
    )

    @Test
    fun `checkpoint rejects negative value`() {
        assertFailsWith<IllegalArgumentException> { ChangeSequenceCheckpoint(-1) }
    }

    @Test
    fun `checkpoint NONE and ZERO represent equal zero`() {
        assertEquals(0L, ChangeSequenceCheckpoint.NONE.valueForPersistence())
        assertEquals(0L, ChangeSequenceCheckpoint.ZERO.valueForPersistence())
        assertEquals(ChangeSequenceCheckpoint.NONE, ChangeSequenceCheckpoint.ZERO)
        assertEquals(ChangeSequenceCheckpoint.NONE.hashCode(), ChangeSequenceCheckpoint.ZERO.hashCode())
    }

    @Test
    fun `checkpoint ordering equality and hash are deterministic`() {
        val equal = ChangeSequenceCheckpoint(17)
        assertEquals(checkpoint, equal)
        assertEquals(checkpoint.hashCode(), equal.hashCode())
        assertTrue(ChangeSequenceCheckpoint(16) < checkpoint)
        assertTrue(ChangeSequenceCheckpoint(18) > checkpoint)
    }

    @Test
    fun `checkpoint JVM surface exposes only persistence raw accessor`() {
        val rawValueReturnTypeNames = setOf("long", "java.lang.Long")
        assertEquals(
            listOf("valueForPersistence"),
            ChangeSequenceCheckpoint::class.java.methods
                .filter { Modifier.isPublic(it.modifiers) && it.returnType.name in rawValueReturnTypeNames }
                .map { it.name }
        )
        val instanceFields = ChangeSequenceCheckpoint::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
        assertEquals(1, instanceFields.size)
        assertEquals("value", instanceFields.single().name)
        assertEquals(Long::class.javaPrimitiveType, instanceFields.single().type)
        assertTrue(Modifier.isPrivate(instanceFields.single().modifiers))
        assertFalse(ChangeSequenceCheckpoint::class.java.isAnnotationPresent(JvmInline::class.java))
        val forbiddenMethodFragments = listOf("unbox", "component", "copy")
        ChangeSequenceCheckpoint::class.java.methods.forEach { method ->
            forbiddenMethodFragments.forEach { fragment ->
                assertFalse(fragment in method.name.lowercase(), method.name)
            }
        }
    }

    @Test
    fun `projection name accepts valid value and is value equal`() {
        val equal = ProjectionName("sales-intelligence")
        assertEquals("sales-intelligence", projectionName.valueForPersistence())
        assertEquals(projectionName, equal)
        assertEquals(projectionName.hashCode(), equal.hashCode())
        val instanceFields = ProjectionName::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
        assertEquals(1, instanceFields.size)
        assertEquals("value", instanceFields.single().name)
        assertEquals(String::class.java, instanceFields.single().type)
        assertTrue(Modifier.isPrivate(instanceFields.single().modifiers))
        assertEquals(
            setOf("toString", "valueForPersistence"),
            ProjectionName::class.java.declaredMethods
                .filter { Modifier.isPublic(it.modifiers) && it.returnType == String::class.java }
                .mapTo(mutableSetOf()) { it.name }
        )
        assertFalse(ProjectionName::class.java.declaredMethods.any { it.name.startsWith("component") })
        assertFalse(ProjectionName::class.java.declaredMethods.any { it.name.startsWith("copy") })
    }

    @Test
    fun `projection name rejects blank`() {
        listOf("", " ", "\t", "\r\n").forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { ProjectionName(invalid) }
        }
    }

    @Test
    fun `projection name rejects more than one hundred characters`() {
        assertEquals(100, ProjectionName("a".repeat(100)).valueForPersistence().length)
        assertFailsWith<IllegalArgumentException> { ProjectionName("a".repeat(101)) }
    }

    @Test
    fun `projection name rejects values outside canonical regex`() {
        listOf(
            "Sales-intelligence",
            "sales_intelligence",
            "sales.intelligence",
            "-sales-intelligence",
            "sales intelligence",
            "sales-intelligence-ç"
        ).forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { ProjectionName(invalid) }
        }
    }

    @Test
    fun `all change feed contract types render redacted or internal`() {
        assertEquals("[INTERNAL]", checkpoint.toString())
        assertEquals("[INTERNAL]", projectionName.toString())
        assertEquals("[REDACTED]", change.toString())

        val success = MarketplaceEconomicEvidenceChangeFeedResult.Success(listOf(change))
        val advanced = CheckpointAdvanceResult.Advanced(checkpoint)
        val stale = CheckpointAdvanceResult.Stale(checkpoint)
        val redacted = listOf(
            MarketplaceEconomicEvidenceChangeKind.FACT,
            MarketplaceEconomicEvidenceChangeKind.ATTEMPT,
            MarketplaceEconomicEvidenceChangeKind.CORRECTION,
            success,
            MarketplaceEconomicEvidenceChangeFeedResult.IntegrityFailure,
            advanced,
            stale,
            CheckpointAdvanceResult.Regression
        )
        assertEquals(List(redacted.size) { "[REDACTED]" }, redacted.map(Any::toString))

        assertSame(change, success.value.single())
        assertEquals(checkpoint, advanced.checkpoint)
        assertEquals(checkpoint, stale.currentCheckpoint)
        assertNoSensitiveRendering(
            change,
            success,
            MarketplaceEconomicEvidenceChangeFeedResult.IntegrityFailure,
            advanced,
            stale,
            CheckpointAdvanceResult.Regression
        )
    }

    @Test
    fun `change contract exposes exactly four fields and no economic payload`() {
        val instanceFields = MarketplaceEconomicEvidenceChange::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
        assertEquals(
            setOf("changeKind", "changeSequence", "evidenceVersion", "subject"),
            instanceFields.mapTo(mutableSetOf()) { it.name }
        )
        assertTrue(instanceFields.all { Modifier.isPrivate(it.modifiers) && Modifier.isFinal(it.modifiers) })
        assertEquals(subject, change.subject)
        assertEquals(version, change.evidenceVersion)
        assertEquals(checkpoint, change.changeSequence)
        assertEquals(MarketplaceEconomicEvidenceChangeKind.FACT, change.changeKind)
        assertEquals(
            setOf("ATTEMPT", "CORRECTION", "FACT"),
            MarketplaceEconomicEvidenceChangeKind.entries.mapTo(mutableSetOf()) { it.name }
        )

        val forbidden = listOf(
            "amount",
            "allocation",
            "attemptpayload",
            "committedat",
            "componentpayload",
            "correctionpayload",
            "factpayload",
            "payload",
            "sql"
        )
        val publicSurface = MarketplaceEconomicEvidenceChange::class.java.methods
            .map { it.name.lowercase() }
        forbidden.forEach { token -> assertFalse(publicSurface.any { token in it }, token) }
    }

    @Test
    fun `result families expose exact variants and preserve payloads`() {
        assertEquals(
            setOf("IntegrityFailure", "Success"),
            MarketplaceEconomicEvidenceChangeFeedResult::class.java.declaredClasses
                .mapTo(mutableSetOf()) { it.simpleName }
        )
        assertEquals(
            setOf("Advanced", "Regression", "Stale"),
            CheckpointAdvanceResult::class.java.declaredClasses
                .mapTo(mutableSetOf()) { it.simpleName }
        )
        assertEquals(
            setOf("ATTEMPT", "CORRECTION", "FACT"),
            MarketplaceEconomicEvidenceChangeKind.entries.mapTo(mutableSetOf()) { it.name }
        )
    }

    @Test
    fun `feed limit contract accepts one and one thousand and rejects all outside values`() {
        assertEquals(1, MarketplaceEconomicEvidenceChangeFeed.requireValidLimit(1))
        assertEquals(1_000, MarketplaceEconomicEvidenceChangeFeed.requireValidLimit(1_000))
        listOf(Int.MIN_VALUE, -1, 0, 1_001, Int.MAX_VALUE).forEach { invalid ->
            assertFailsWith<IllegalArgumentException> {
                MarketplaceEconomicEvidenceChangeFeed.requireValidLimit(invalid)
            }
        }
    }

    @Test
    fun `port exposes exactly four organization scoped methods and no global ordering assertion`() {
        val methods = MarketplaceEconomicEvidenceChangeFeed::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
        assertEquals(4, methods.size)
        assertEquals(
            setOf(
                "advanceCheckpoint",
                "changesSince",
                "currentCheckpoint",
                "organizationsWithPendingChanges"
            ),
            methods.mapTo(mutableSetOf()) { it.name.substringBefore('-') }
        )

        val changesSince = methods.single { it.name.substringBefore('-') == "changesSince" }
        assertEquals(3, changesSince.parameterCount)
        assertEquals(ChangeSequenceCheckpoint::class.java, changesSince.parameterTypes[1])
        assertEquals(Int::class.javaPrimitiveType, changesSince.parameterTypes[2])

        val currentCheckpoint = methods.single { it.name.substringBefore('-') == "currentCheckpoint" }
        assertEquals(2, currentCheckpoint.parameterCount)
        assertEquals(ProjectionName::class.java, currentCheckpoint.parameterTypes[1])

        val advanceCheckpoint = methods.single {
            it.name.substringBefore('-') == "advanceCheckpoint"
        }
        assertEquals(4, advanceCheckpoint.parameterCount)
        assertEquals(ProjectionName::class.java, advanceCheckpoint.parameterTypes[1])
        assertEquals(ChangeSequenceCheckpoint::class.java, advanceCheckpoint.parameterTypes[2])
        assertEquals(ChangeSequenceCheckpoint::class.java, advanceCheckpoint.parameterTypes[3])

        methods.filter { method ->
            method.parameterTypes.contains(ChangeSequenceCheckpoint::class.java)
        }.forEach { method ->
            assertTrue(method.parameterCount >= 2, method.name)
            assertEquals(changesSince.parameterTypes[0], method.parameterTypes[0], method.name)
        }
    }

    @Test
    fun `port has no acknowledgement token batch token or fifth operation`() {
        val methods = MarketplaceEconomicEvidenceChangeFeed::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
        val forbiddenTokens = listOf("ack", "batch", "claim", "lease", "token")
        assertEquals(4, methods.size)
        methods.forEach { method ->
            forbiddenTokens.forEach { token ->
                assertFalse(token in method.name.lowercase(), method.name)
                assertFalse(method.parameterTypes.any { token in it.name.lowercase() }, method.name)
                assertFalse(token in method.returnType.name.lowercase(), method.name)
            }
        }
    }

    @Test
    fun `P0_2 repository still exposes exactly find and apply`() {
        val methods = MarketplaceIndependentEconomicEvidenceRepository::class.java.declaredMethods
            .filter { Modifier.isPublic(it.modifiers) && !it.isSynthetic }
        assertEquals(2, methods.size)
        assertEquals(setOf("apply", "find"), methods.mapTo(mutableSetOf()) { it.name })
    }

    @Test
    fun `compiled contract contains no forbidden dependency or numeric type`() {
        val classes = java.nio.file.Path.of(
            ChangeSequenceCheckpoint::class.java.protectionDomain.codeSource.location.toURI()
        ).resolve("io/flooow/marketplace/operations/economics/evidence")
        val ownedPrefixes = listOf(
            "ChangeSequenceCheckpoint",
            "ProjectionName",
            "MarketplaceEconomicEvidenceChange",
            "MarketplaceEconomicEvidenceChangeKind",
            "MarketplaceEconomicEvidenceChangeFeedResult",
            "CheckpointAdvanceResult",
            "MarketplaceEconomicEvidenceChangeFeed"
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
            "outbox",
            "marketplacefinancialledger",
            "reconciliation",
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
                forbidden.forEach { token -> assertFalse(token in bytecode, "$token in $classFile") }
            }
        }
        assertTrue(inspected > 0)
    }

    private fun assertNoSensitiveRendering(vararg values: Any) {
        val sensitive = listOf(
            organizationId.value.toString(),
            subject.orderId.value.toString(),
            subject.externalOrderId.value,
            projectionName.valueForPersistence(),
            checkpoint.valueForPersistence().toString(),
            version.valueForPersistence().toString()
        )
        values.forEach { value ->
            val rendering = value.toString()
            sensitive.forEach { secret -> assertFalse(secret in rendering, rendering) }
        }
    }
}
