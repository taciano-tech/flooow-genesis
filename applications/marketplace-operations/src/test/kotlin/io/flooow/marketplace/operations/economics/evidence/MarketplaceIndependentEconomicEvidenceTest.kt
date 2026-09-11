package io.flooow.marketplace.operations.economics.evidence

import io.flooow.marketplace.operations.economics.EconomicComponent
import io.flooow.marketplace.operations.economics.EconomicComponentCoverage
import io.flooow.marketplace.operations.economics.EconomicComponentId
import io.flooow.marketplace.operations.economics.EconomicComponentType
import io.flooow.marketplace.operations.economics.EconomicDirection
import io.flooow.marketplace.operations.economics.EconomicEvidenceQuality
import io.flooow.marketplace.operations.economics.EconomicExternalReference
import io.flooow.marketplace.operations.economics.EconomicExternalReferenceAbsenceReason
import io.flooow.marketplace.operations.economics.EconomicExternalReferenceState
import io.flooow.marketplace.operations.economics.EconomicSource
import io.flooow.marketplace.operations.economics.EconomicSourceKind
import io.flooow.marketplace.operations.economics.EconomicSourceSystemKey
import io.flooow.marketplace.operations.economics.MarketplaceCurrency
import io.flooow.marketplace.operations.economics.MarketplaceExternalOrderId
import io.flooow.marketplace.operations.economics.MarketplaceKey
import io.flooow.marketplace.operations.economics.MarketplaceMoney
import io.flooow.marketplace.operations.economics.MarketplaceOrderId
import io.flooow.organization.OrganizationId
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class MarketplaceIndependentEconomicEvidenceTest {
    private val subject = subject()
    private val occurredAt = Instant.parse("2026-08-27T10:00:00.123456Z")
    private val observedAt = Instant.parse("2026-08-27T10:01:00.654321Z")

    @Test
    fun `compiled boundary contains no disallowed dependency reference`() {
        val classes = java.nio.file.Path.of(
            MarketplaceIndependentEconomicEvidenceMerger::class.java.protectionDomain.codeSource.location.toURI()
        ).resolve("io/flooow/marketplace/operations/economics/evidence")
        val forbidden = listOf(
            "io/flooow/kernel",
            "io/flooow/integration/connector",
            "io/flooow/marketplace/persistence",
            "java/sql",
            "javax/sql",
            "okhttp",
            "kotlinx/serialization",
            "/api/",
            "/ui/",
            "/ai/"
        )
        Files.walk(classes).use { files ->
            files.filter { it.toString().endsWith(".class") }.forEach { classFile ->
                val bytecode = String(Files.readAllBytes(classFile), StandardCharsets.ISO_8859_1)
                forbidden.forEach { token -> assertFalse(token in bytecode, token) }
            }
        }
    }

    @Test
    fun `observation identifier is canonical value equal ordered and internally rendered`() {
        val first = observationId(1)
        assertEquals(first, observationId(1))
        assertNotEquals(first, observationId(2))
        assertTrue(first < observationId(2))
        assertTrue(
            MarketplaceEconomicEvidenceObservationId.parse("7fffffff-ffff-ffff-ffff-ffffffffffff") <
                MarketplaceEconomicEvidenceObservationId.parse("ffffffff-ffff-ffff-ffff-ffffffffffff")
        )
        assertEquals("[INTERNAL]", first.toString())
        assertFailsWith<IllegalArgumentException> {
            MarketplaceEconomicEvidenceObservationId.parse("AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA")
        }
        assertFailsWith<IllegalArgumentException> {
            MarketplaceEconomicEvidenceObservationId.parse(" 00000000-0000-0000-0000-000000000001")
        }
        assertFailsWith<IllegalArgumentException> {
            MarketplaceEconomicEvidenceObservationId.parse("not-an-id")
        }
    }

    @Test
    fun `subject is value equal redacted and every isolation dimension fails closed`() {
        assertEquals(subject, subject())
        assertEquals("[REDACTED]", subject.toString())

        val alternatives = listOf(
            subject(organization = "10000000-0000-0000-0000-000000000002"),
            subject(order = "20000000-0000-0000-0000-000000000002"),
            subject(marketplace = "amazon"),
            subject(externalOrder = "order-2"),
            subject(currency = "USD")
        )
        alternatives.forEachIndexed { index, other ->
            val update = MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(
                componentFact(
                    id = 10 + index,
                    subject = other,
                    family = MarketplaceEconomicEvidenceFamily.MARKETPLACE_SHIPPING,
                    type = EconomicComponentType.SHIPPING
                )
            )
            assertEquals(
                MarketplaceIndependentEconomicEvidenceResult.SubjectMismatch,
                MarketplaceIndependentEconomicEvidenceMerger.apply(empty(), update)
            )
        }
    }

    @Test
    fun `financial family mappings are exhaustive and subject ownership is enforced`() {
        val allowed = mapOf(
            MarketplaceEconomicEvidenceFamily.MARKETPLACE_ORDER to setOf(
                EconomicComponentType.REVENUE,
                EconomicComponentType.MARKETPLACE_COMMISSION,
                EconomicComponentType.MARKETPLACE_FEE
            ),
            MarketplaceEconomicEvidenceFamily.MARKETPLACE_SHIPPING to setOf(EconomicComponentType.SHIPPING),
            MarketplaceEconomicEvidenceFamily.PRODUCT_COST to setOf(EconomicComponentType.PRODUCT_COST),
            MarketplaceEconomicEvidenceFamily.FISCAL_TAX to setOf(EconomicComponentType.TAX),
            MarketplaceEconomicEvidenceFamily.ADS_ALLOCATION to setOf(EconomicComponentType.ADVERTISING)
        )
        MarketplaceEconomicEvidenceFamily.entries.forEach { family ->
            EconomicComponentType.entries.forEach { type ->
                val accepted = type in allowed.getOrDefault(family, emptySet())
                val outcome = runCatching { componentFact(1, family = family, type = type) }
                assertEquals(accepted, outcome.isSuccess, "$family / $type")
            }
        }

        assertFailsWith<IllegalArgumentException> {
            componentFact(
                2,
                componentOrganization = OrganizationId.parse("10000000-0000-0000-0000-000000000002")
            )
        }
        assertFailsWith<IllegalArgumentException> {
            componentFact(
                3,
                componentOrder = MarketplaceOrderId.parse("20000000-0000-0000-0000-000000000002")
            )
        }
        assertFailsWith<IllegalArgumentException> {
            componentFact(4, componentCurrency = MarketplaceCurrency("USD"))
        }
    }

    @Test
    fun `external identity family mappings are exhaustive`() {
        val allowed = mapOf(
            MarketplaceEconomicEvidenceFamily.MARKETPLACE_PAYMENT to
                MarketplaceEconomicExternalIdentityKind.MARKETPLACE_PAYMENT,
            MarketplaceEconomicEvidenceFamily.FISCAL_INVOICE to
                MarketplaceEconomicExternalIdentityKind.FISCAL_INVOICE,
            MarketplaceEconomicEvidenceFamily.ADS_IDENTITY to
                MarketplaceEconomicExternalIdentityKind.MARKETPLACE_ITEM_TO_AD_GROUP,
            MarketplaceEconomicEvidenceFamily.MARKETPLACE_ORDER to
                MarketplaceEconomicExternalIdentityKind.ERP_ORDER
        )
        MarketplaceEconomicEvidenceFamily.entries.forEach { family ->
            MarketplaceEconomicExternalIdentityKind.entries.forEach { kind ->
                val outcome = runCatching { identityFact(1, family = family, kind = kind) }
                assertEquals(allowed[family] == kind, outcome.isSuccess, "$family / $kind")
            }
        }
    }

    @Test
    fun `only complete or partial coverage is accepted and exact zero is evidence`() {
        listOf(EconomicComponentCoverage.COMPLETE, EconomicComponentCoverage.PARTIAL).forEach { coverage ->
            assertEquals(coverage, componentFact(1, coverage = coverage).observation.coverageClaim)
        }
        listOf(EconomicComponentCoverage.MISSING, EconomicComponentCoverage.NOT_APPLICABLE).forEach { coverage ->
            assertFailsWith<IllegalArgumentException> { componentFact(1, coverage = coverage) }
        }

        val zero = componentFact(2, amount = "0")
        val evidence = applied(empty(), MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(zero))
        assertEquals(MarketplaceMoney.parse(subject.currency, "0"), componentFacts(evidence).single().component.magnitude)
        assertEquals(EconomicComponentCoverage.COMPLETE, componentFacts(evidence).single().coverageClaim)
    }

    @Test
    fun `attempts contain no amount append independently and never mutate facts`() {
        val attempt = attempt(1, MarketplaceEconomicEvidenceAttemptOutcome.NO_EVIDENCE)
        assertTrue(
            attempt::class.java.declaredFields.none {
                it.type == MarketplaceMoney::class.java || it.type == EconomicComponent::class.java
            }
        )
        val known = applied(empty(), observe(componentFact(2, amount = "14.25")))
        val afterAttempt = applied(known, record(attempt))
        assertEquals(known.facts, afterAttempt.facts)
        assertEquals(listOf(attempt), afterAttempt.attempts)

        val repeated = applied(
            afterAttempt,
            record(attempt(3, MarketplaceEconomicEvidenceAttemptOutcome.NO_EVIDENCE))
        )
        assertEquals(known.facts, repeated.facts)
        assertEquals(2, repeated.attempts.size)
    }

    @Test
    fun `all accepted times require microsecond precision`() {
        val imprecise = Instant.parse("2026-08-27T10:00:00.123456789Z")
        assertFailsWith<IllegalArgumentException> { componentFact(1, occurred = imprecise) }
        assertFailsWith<IllegalArgumentException> { componentFact(2, observed = imprecise) }
        assertFailsWith<IllegalArgumentException> { identityFact(3, occurred = imprecise) }
        assertFailsWith<IllegalArgumentException> { identityFact(4, observed = imprecise) }
        assertFailsWith<IllegalArgumentException> { orderOccurrenceFact(5, occurred = imprecise) }
        assertFailsWith<IllegalArgumentException> { orderOccurrenceFact(6, observed = imprecise) }
        assertFailsWith<IllegalArgumentException> { attempt(7, attempted = imprecise) }

        val replacement = componentFact(8, observed = observedAt)
        assertFailsWith<IllegalArgumentException> {
            correction(7, replacement, observationId(8), observed = imprecise)
        }
    }

    @Test
    fun `manual and calculated clocks require order while external source clocks gain no inference`() {
        val earlier = occurredAt.minusSeconds(60)
        listOf(EconomicSourceKind.MANUAL, EconomicSourceKind.CALCULATED).forEachIndexed { index, kind ->
            assertFailsWith<IllegalArgumentException> {
                componentFact(10 + index, source = internalSource(kind), occurred = occurredAt, observed = earlier)
            }
            assertFailsWith<IllegalArgumentException> {
                identityFact(20 + index, source = internalSource(kind), occurred = occurredAt, observed = earlier)
            }
            assertFailsWith<IllegalArgumentException> {
                orderOccurrenceFact(30 + index, source = internalSource(kind), occurred = occurredAt, observed = earlier)
            }
        }
        listOf(EconomicSourceKind.MARKETPLACE, EconomicSourceKind.ERP).forEachIndexed { index, kind ->
            componentFact(40 + index, source = externalSource(kind, "fact-clock-$index"), observed = earlier)
            identityFact(50 + index, source = externalSource(kind, "identity-clock-$index"), observed = earlier)
            orderOccurrenceFact(60 + index, source = externalSource(kind, "occurrence-clock-$index"), observed = earlier)
        }
    }

    @Test
    fun `order occurrence has fixed marketplace order family and redacted rendering`() {
        val fact = orderOccurrenceFact(70)

        assertEquals(MarketplaceEconomicEvidenceFamily.MARKETPLACE_ORDER, fact.family)
        assertEquals(subject, fact.subject)
        assertEquals(occurredAt, fact.observation.occurredAt)
        assertEquals("[REDACTED]", fact.observation.toString())
        assertEquals("[REDACTED]", fact.toString())
    }

    @Test
    fun `order occurrence external source fact duplicates equal meaning and conflicts on changed time`() {
        listOf(EconomicSourceKind.MARKETPLACE, EconomicSourceKind.ERP).forEachIndexed { index, kind ->
            val source = externalSource(kind, "order-occurrence-$index")
            val original = orderOccurrenceFact(80 + index * 10, source = source)
            val current = applied(empty(), observe(original))

            val duplicateFact = orderOccurrenceFact(
                81 + index * 10,
                source = source,
                observed = observedAt.plusSeconds(10)
            )
            val duplicate = assertIs<MarketplaceIndependentEconomicEvidenceResult.Duplicate>(
                MarketplaceIndependentEconomicEvidenceMerger.apply(current, observe(duplicateFact))
            )
            assertSame(current, duplicate.evidence)

            val changedTime = orderOccurrenceFact(
                82 + index * 10,
                source = source,
                occurred = occurredAt.plusSeconds(1),
                observed = observedAt.plusSeconds(10)
            )
            assertEquals(
                MarketplaceIndependentEconomicEvidenceResult.SourceFactConflict,
                MarketplaceIndependentEconomicEvidenceMerger.apply(current, observe(changedTime))
            )
        }
    }

    @Test
    fun `manual and calculated order occurrences use observation identifier without invented provider identity`() {
        listOf(EconomicSourceKind.MANUAL, EconomicSourceKind.CALCULATED).forEachIndexed { index, kind ->
            val first = orderOccurrenceFact(100 + index * 10, source = internalSource(kind))
            val second = orderOccurrenceFact(
                101 + index * 10,
                source = internalSource(kind),
                occurred = occurredAt.plusSeconds(1),
                observed = observedAt.plusSeconds(1)
            )
            val current = applied(empty(), observe(first))

            assertIs<MarketplaceIndependentEconomicEvidenceResult.Applied>(
                MarketplaceIndependentEconomicEvidenceMerger.apply(current, observe(second))
            )
        }
    }

    @Test
    fun `order occurrence participates in global observation identifier uniqueness`() {
        val occurrence = orderOccurrenceFact(120)
        val current = applied(empty(), observe(occurrence))

        assertEquals(
            MarketplaceIndependentEconomicEvidenceResult.IdentifierConflict,
            MarketplaceIndependentEconomicEvidenceMerger.apply(
                current,
                observe(componentFact(120, componentId = 120))
            )
        )
    }

    @Test
    fun `order occurrence correction preserves history and activates explicit replacement`() {
        val source = externalSource(EconomicSourceKind.MARKETPLACE, "corrected-order-occurrence")
        val original = orderOccurrenceFact(130, source = source)
        val before = applied(empty(), observe(original))
        val replacement = orderOccurrenceFact(
            131,
            source = source,
            occurred = occurredAt.plusSeconds(5),
            observed = observedAt.plusSeconds(1)
        )

        assertEquals(
            MarketplaceIndependentEconomicEvidenceResult.SourceFactConflict,
            MarketplaceIndependentEconomicEvidenceMerger.apply(before, observe(replacement))
        )

        val correction = correction(
            132,
            replacement,
            original.id,
            observedAt.plusSeconds(2)
        )
        val after = applied(before, correct(correction))

        assertEquals(listOf(original, replacement), after.historicalFacts)
        assertEquals(listOf(replacement), after.activeFacts)
        assertEquals(listOf(correction), after.corrections)
        assertFalse(original in after.activeFacts)
    }

    @Test
    fun `order occurrence canonical ordering and aggregate equality are insertion order independent`() {
        val first = orderOccurrenceFact(
            140,
            source = externalSource(EconomicSourceKind.MARKETPLACE, "occurrence-a")
        )
        val second = orderOccurrenceFact(
            141,
            source = externalSource(EconomicSourceKind.MARKETPLACE, "occurrence-b")
        )

        val left = applied(applied(empty(), observe(second)), observe(first))
        val right = applied(applied(empty(), observe(first)), observe(second))

        assertEquals(left, right)
        assertEquals(listOf(first.id, second.id), left.facts.map { it.id })
        assertEquals(left.hashCode(), right.hashCode())
    }

    @Test
    fun `empty factory snapshots and merger results are immutable`() {
        val empty = empty()
        assertTrue(empty.facts.isEmpty())
        assertTrue(empty.attempts.isEmpty())
        assertTrue(empty.corrections.isEmpty())
        assertTrue(empty.activeFacts.isEmpty())
        assertTrue(empty.historicalFacts.isEmpty())

        val fact = componentFact(1)
        val evidence = applied(empty, observe(fact))
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (evidence.facts as MutableList<MarketplaceIndependentEconomicFact>).add(componentFact(2))
        }
        assertFailsWith<UnsupportedOperationException> {
            @Suppress("UNCHECKED_CAST")
            (evidence.activeFacts as MutableList<MarketplaceIndependentEconomicFact>).clear()
        }
    }

    @Test
    fun `exact duplicate is idempotent while reused identifier with another payload conflicts`() {
        val fact = componentFact(1, amount = "9.50")
        val current = applied(empty(), observe(fact))
        val duplicate = assertIs<MarketplaceIndependentEconomicEvidenceResult.Duplicate>(
            MarketplaceIndependentEconomicEvidenceMerger.apply(current, observe(fact))
        )
        assertSame(current, duplicate.evidence)

        val conflict = MarketplaceIndependentEconomicEvidenceMerger.apply(
            current,
            observe(componentFact(1, amount = "9.51"))
        )
        assertEquals(MarketplaceIndependentEconomicEvidenceResult.IdentifierConflict, conflict)

        val recorded = applied(current, record(attempt(2)))
        assertIs<MarketplaceIndependentEconomicEvidenceResult.Duplicate>(
            MarketplaceIndependentEconomicEvidenceMerger.apply(recorded, record(attempt(2)))
        )
        assertEquals(
            MarketplaceIndependentEconomicEvidenceResult.IdentifierConflict,
            MarketplaceIndependentEconomicEvidenceMerger.apply(
                recorded,
                record(attempt(2, MarketplaceEconomicEvidenceAttemptOutcome.AMBIGUOUS))
            )
        )
    }

    @Test
    fun `financial source fact equality is duplicate and changed meaning is conflict`() {
        val original = componentFact(1, amount = "22.10", externalReference = "shipment-charge")
        val current = applied(empty(), observe(original))
        val equalMeaning = componentFact(
            2,
            amount = "22.10",
            externalReference = "shipment-charge",
            componentId = 2,
            observed = observedAt.plusSeconds(10)
        )
        val duplicate = assertIs<MarketplaceIndependentEconomicEvidenceResult.Duplicate>(
            MarketplaceIndependentEconomicEvidenceMerger.apply(current, observe(equalMeaning))
        )
        assertSame(current, duplicate.evidence)

        val changed = componentFact(
            3,
            amount = "22.11",
            externalReference = "shipment-charge",
            componentId = 3
        )
        assertEquals(
            MarketplaceIndependentEconomicEvidenceResult.SourceFactConflict,
            MarketplaceIndependentEconomicEvidenceMerger.apply(current, observe(changed))
        )

        val meaningChanges = listOf(
            componentFact(
                6,
                externalReference = "shipment-charge",
                componentId = 6,
                direction = EconomicDirection.ADDITION
            ),
            componentFact(
                7,
                externalReference = "shipment-charge",
                componentId = 7,
                occurred = occurredAt.plusSeconds(1)
            ),
            componentFact(
                8,
                externalReference = "shipment-charge",
                componentId = 8,
                quality = EconomicEvidenceQuality.ESTIMATED
            ),
            componentFact(
                9,
                externalReference = "shipment-charge",
                componentId = 9,
                coverage = EconomicComponentCoverage.PARTIAL
            )
        )
        meaningChanges.forEach { variant ->
            assertEquals(
                MarketplaceIndependentEconomicEvidenceResult.SourceFactConflict,
                MarketplaceIndependentEconomicEvidenceMerger.apply(current, observe(variant))
            )
        }

        val manualOne = componentFact(4, source = internalSource(EconomicSourceKind.MANUAL))
        val manualTwo = componentFact(5, source = internalSource(EconomicSourceKind.MANUAL), componentId = 5)
        val withManualOne = applied(empty(), observe(manualOne))
        assertIs<MarketplaceIndependentEconomicEvidenceResult.Applied>(
            MarketplaceIndependentEconomicEvidenceMerger.apply(withManualOne, observe(manualTwo))
        )
    }

    @Test
    fun `external identities never create components and multiple Ads relationships remain nonfinancial`() {
        val invoice = identityFact(
            1,
            MarketplaceEconomicEvidenceFamily.FISCAL_INVOICE,
            MarketplaceEconomicExternalIdentityKind.FISCAL_INVOICE
        )
        val adOne = identityFact(
            2,
            MarketplaceEconomicEvidenceFamily.ADS_IDENTITY,
            MarketplaceEconomicExternalIdentityKind.MARKETPLACE_ITEM_TO_AD_GROUP,
            linked = "ad-group-1"
        )
        val adTwo = identityFact(
            3,
            MarketplaceEconomicEvidenceFamily.ADS_IDENTITY,
            MarketplaceEconomicExternalIdentityKind.MARKETPLACE_ITEM_TO_AD_GROUP,
            linked = "ad-group-2"
        )
        val evidence = listOf(invoice, adOne, adTwo).fold(empty()) { current, fact ->
            applied(current, observe(fact))
        }
        assertTrue(componentFacts(evidence).isEmpty())
        assertEquals(3, evidence.activeFacts.filterIsInstance<MarketplaceIndependentEconomicFact.ExternalIdentity>().size)
        assertTrue(evidence.activeFacts.none { it.family == MarketplaceEconomicEvidenceFamily.ADS_ALLOCATION })

        val allocation = componentFact(
            4,
            MarketplaceEconomicEvidenceFamily.ADS_ALLOCATION,
            EconomicComponentType.ADVERTISING,
            amount = "3.25",
            externalReference = "allocated-ad-spend"
        )
        val withAllocation = applied(evidence, observe(allocation))
        assertEquals(1, componentFacts(withAllocation).count { it.component.type == EconomicComponentType.ADVERTISING })
    }

    @Test
    fun `provider failure after known fact preserves amount and provenance`() {
        val knownFact = componentFact(1, amount = "18.00", externalReference = "known-shipping")
        val known = applied(empty(), observe(knownFact))
        val failed = applied(
            known,
            record(attempt(2, MarketplaceEconomicEvidenceAttemptOutcome.TEMPORARY_FAILURE))
        )
        val missing = applied(
            failed,
            record(attempt(3, MarketplaceEconomicEvidenceAttemptOutcome.NO_EVIDENCE))
        )
        assertSame(knownFact, missing.facts.single())
        assertEquals("18", componentFacts(missing).single().component.magnitude.amount.toPlainString())
        assertEquals("known-shipping", externalReferenceOf(componentFacts(missing).single().component.source))
    }

    @Test
    fun `correction target must exist and must be an accepted fact`() {
        val replacement = componentFact(2, amount = "11.00", externalReference = "corrected")
        assertEquals(
            MarketplaceIndependentEconomicEvidenceResult.SupersededFactNotFound,
            MarketplaceIndependentEconomicEvidenceMerger.apply(
                empty(),
                correct(correction(3, replacement, observationId(99)))
            )
        )

        val withAttempt = applied(empty(), record(attempt(4)))
        assertEquals(
            MarketplaceIndependentEconomicEvidenceResult.SupersededTargetNotFact,
            MarketplaceIndependentEconomicEvidenceMerger.apply(
                withAttempt,
                correct(correction(5, replacement, observationId(4)))
            )
        )

        val original = componentFact(6, externalReference = "original")
        val withOriginal = applied(empty(), observe(original))
        val firstCorrection = correction(
            7,
            componentFact(8, amount = "12.00", externalReference = "original", componentId = 8),
            original.id
        )
        val corrected = applied(withOriginal, correct(firstCorrection))
        assertEquals(
            MarketplaceIndependentEconomicEvidenceResult.SupersededTargetNotFact,
            MarketplaceIndependentEconomicEvidenceMerger.apply(
                corrected,
                correct(
                    correction(
                        9,
                        componentFact(10, externalReference = "another", componentId = 10),
                        firstCorrection.id
                    )
                )
            )
        )
    }

    @Test
    fun `correction identifiers are unique and one fact is superseded only once`() {
        val original = componentFact(1, externalReference = "original")
        val other = componentFact(2, externalReference = "other")
        val current = applied(applied(empty(), observe(original)), observe(other))

        val collidingReplacement = componentFact(2, amount = "13.00", externalReference = "original", componentId = 3)
        assertEquals(
            MarketplaceIndependentEconomicEvidenceResult.ReplacementIdentifierConflict,
            MarketplaceIndependentEconomicEvidenceMerger.apply(
                current,
                correct(correction(3, collidingReplacement, original.id))
            )
        )

        val first = correction(
            4,
            componentFact(5, amount = "14.00", externalReference = "original", componentId = 5),
            original.id
        )
        val corrected = applied(current, correct(first))
        val duplicate = assertIs<MarketplaceIndependentEconomicEvidenceResult.Duplicate>(
            MarketplaceIndependentEconomicEvidenceMerger.apply(corrected, correct(first))
        )
        assertSame(corrected, duplicate.evidence)

        val second = correction(
            6,
            componentFact(7, amount = "15.00", externalReference = "original", componentId = 7),
            original.id
        )
        assertEquals(
            MarketplaceIndependentEconomicEvidenceResult.FactAlreadySuperseded,
            MarketplaceIndependentEconomicEvidenceMerger.apply(corrected, correct(second))
        )
    }

    @Test
    fun `correction cannot precede the superseded observation`() {
        val original = componentFact(1, observed = observedAt.plusSeconds(10))
        val current = applied(empty(), observe(original))
        val replacement = componentFact(
            2,
            amount = "11.00",
            componentId = 2,
            observed = observedAt
        )
        val correction = correction(3, replacement, original.id, observedAt.plusSeconds(1))
        val failure = assertFailsWith<IllegalArgumentException> {
            MarketplaceIndependentEconomicEvidenceMerger.apply(current, correct(correction))
        }
        assertFalse(subject.externalOrderId.value in failure.message.orEmpty())
    }

    @Test
    fun `correction preserves history and activates replacement`() {
        val original = componentFact(1, amount = "20.00", externalReference = "shipment-line")
        val before = applied(empty(), observe(original))
        val replacement = componentFact(
            2,
            amount = "19.50",
            externalReference = "shipment-line",
            componentId = 2,
            observed = observedAt.plusSeconds(1)
        )
        val correction = correction(3, replacement, original.id, observedAt.plusSeconds(2))
        val after = applied(before, correct(correction))

        assertEquals(listOf(original, replacement), after.historicalFacts)
        assertEquals(listOf(replacement), after.activeFacts)
        assertEquals(listOf(correction), after.corrections)
        assertEquals("20", componentFactsFrom(after.historicalFacts).first().component.magnitude.amount.toPlainString())
        assertEquals("19.5", componentFactsFrom(after.activeFacts).single().component.magnitude.amount.toPlainString())
    }

    @Test
    fun `correction cannot hide unrelated active source fact conflict`() {
        val original = componentFact(1, externalReference = "source-a")
        val unrelated = componentFact(2, externalReference = "source-b", componentId = 2)
        val current = applied(applied(empty(), observe(original)), observe(unrelated))
        val conflictingReplacement = componentFact(
            3,
            amount = "99.00",
            externalReference = "source-b",
            componentId = 3
        )
        assertEquals(
            MarketplaceIndependentEconomicEvidenceResult.ReplacementSourceFactConflict,
            MarketplaceIndependentEconomicEvidenceMerger.apply(
                current,
                correct(correction(4, conflictingReplacement, original.id))
            )
        )
    }

    @Test
    fun `canonical ordering and value equality do not depend on update order`() {
        val time = observedAt
        val firstFact = componentFact(1, externalReference = "one", observed = time)
        val secondFact = componentFact(2, externalReference = "two", componentId = 2, observed = time)
        val firstAttempt = attempt(3, attempted = time)
        val secondAttempt = attempt(4, MarketplaceEconomicEvidenceAttemptOutcome.AMBIGUOUS, attempted = time)
        val updates = listOf(observe(secondFact), record(secondAttempt), observe(firstFact), record(firstAttempt))
        val reverse = updates.reversed()

        val left = updates.fold(empty()) { current, update -> applied(current, update) }
        val right = reverse.fold(empty()) { current, update -> applied(current, update) }
        assertEquals(left, right)
        assertEquals(listOf(firstFact.id, secondFact.id), left.facts.map { it.id })
        assertEquals(listOf(firstAttempt.id, secondAttempt.id), left.attempts.map { it.id })
        assertEquals(left.hashCode(), right.hashCode())
    }

    @Test
    fun `corrections use canonical time and unsigned identifier ordering`() {
        val originalOne = componentFact(1, externalReference = "one")
        val originalTwo = componentFact(2, externalReference = "two", componentId = 2)
        val baseline = applied(applied(empty(), observe(originalTwo)), observe(originalOne))
        val correctionOne = correction(
            5,
            componentFact(
                3,
                amount = "11.00",
                externalReference = "one",
                componentId = 3,
                observed = observedAt.plusSeconds(1)
            ),
            originalOne.id,
            observedAt.plusSeconds(2)
        )
        val correctionTwo = correction(
            4,
            componentFact(
                6,
                amount = "12.00",
                externalReference = "two",
                componentId = 6,
                observed = observedAt.plusSeconds(1)
            ),
            originalTwo.id,
            observedAt.plusSeconds(2)
        )
        val left = applied(applied(baseline, correct(correctionOne)), correct(correctionTwo))
        val right = applied(applied(baseline, correct(correctionTwo)), correct(correctionOne))
        assertEquals(left, right)
        assertEquals(listOf(correctionTwo.id, correctionOne.id), left.corrections.map { it.id })
    }

    @Test
    fun `accepted MGI scenarios progress independently without ERP order identity`() {
        val zeroShipping = componentFact(1, amount = "0", externalReference = "shipment-zero")
        val productCost = componentFact(
            2,
            MarketplaceEconomicEvidenceFamily.PRODUCT_COST,
            EconomicComponentType.PRODUCT_COST,
            amount = "71.25",
            source = externalSource(EconomicSourceKind.ERP, "omie-product-cost"),
            externalReference = "omie-product-cost",
            componentId = 2
        )
        val invoice = identityFact(
            3,
            MarketplaceEconomicEvidenceFamily.FISCAL_INVOICE,
            MarketplaceEconomicExternalIdentityKind.FISCAL_INVOICE,
            linked = "invoice-123"
        )
        val tax = componentFact(
            4,
            MarketplaceEconomicEvidenceFamily.FISCAL_TAX,
            EconomicComponentType.TAX,
            amount = "12.40",
            source = externalSource(EconomicSourceKind.ERP, "invoice-tax"),
            externalReference = "invoice-tax",
            componentId = 4
        )
        val adIdentity = identityFact(
            5,
            MarketplaceEconomicEvidenceFamily.ADS_IDENTITY,
            MarketplaceEconomicExternalIdentityKind.MARKETPLACE_ITEM_TO_AD_GROUP
        )
        val evidence = listOf(zeroShipping, productCost, invoice, tax, adIdentity).fold(empty()) { current, fact ->
            applied(current, observe(fact))
        }

        assertTrue(evidence.activeFacts.none { identityKind(it) == MarketplaceEconomicExternalIdentityKind.ERP_ORDER })
        assertEquals(
            setOf(EconomicComponentType.SHIPPING, EconomicComponentType.PRODUCT_COST, EconomicComponentType.TAX),
            componentFacts(evidence).mapTo(mutableSetOf()) { it.component.type }
        )
        assertEquals(1, evidence.activeFacts.count { identityKind(it) == MarketplaceEconomicExternalIdentityKind.FISCAL_INVOICE })
        assertEquals(1, evidence.activeFacts.count {
            identityKind(it) == MarketplaceEconomicExternalIdentityKind.MARKETPLACE_ITEM_TO_AD_GROUP
        })
        assertTrue(componentFacts(evidence).none { it.component.type == EconomicComponentType.ADVERTISING })
    }

    @Test
    fun `all new renderings are redacted and controlled failures retain no sensitive state`() {
        val fact = componentFact(1)
        val attempt = attempt(2)
        val correction = correction(3, componentFact(4, componentId = 4), fact.id)
        val evidence = applied(empty(), observe(fact))
        val applied = MarketplaceIndependentEconomicEvidenceResult.Applied(evidence)
        val duplicate = MarketplaceIndependentEconomicEvidenceResult.Duplicate(evidence)
        val renderings = listOf(
            subject,
            MarketplaceEconomicEvidenceFamily.MARKETPLACE_SHIPPING,
            fact.observation,
            orderOccurrenceFact(150).observation,
            orderOccurrenceFact(150),
            MarketplaceEconomicExternalIdentityKind.FISCAL_INVOICE,
            identityFact(5).observation,
            identityFact(5),
            MarketplaceEconomicEvidenceAttemptOutcome.NO_EVIDENCE,
            attempt,
            fact,
            MarketplaceEconomicEvidenceCorrectionReason.SOURCE_CORRECTION,
            correction,
            evidence,
            observe(fact),
            record(attempt),
            correct(correction),
            applied,
            duplicate,
            MarketplaceIndependentEconomicEvidenceResult.SubjectMismatch,
            MarketplaceIndependentEconomicEvidenceResult.IdentifierConflict,
            MarketplaceIndependentEconomicEvidenceResult.SourceFactConflict,
            MarketplaceIndependentEconomicEvidenceResult.SupersededFactNotFound,
            MarketplaceIndependentEconomicEvidenceResult.SupersededTargetNotFact,
            MarketplaceIndependentEconomicEvidenceResult.FactAlreadySuperseded,
            MarketplaceIndependentEconomicEvidenceResult.ReplacementIdentifierConflict,
            MarketplaceIndependentEconomicEvidenceResult.ReplacementSourceFactConflict,
            MarketplaceIndependentEconomicEvidenceMerger
        ).map(Any::toString)
        assertEquals(List(renderings.size) { "[REDACTED]" }, renderings)

        val sensitive = listOf(
            subject.organizationId.value.toString(),
            subject.orderId.value.toString(),
            subject.externalOrderId.value,
            "shipping-reference",
            "10.00",
            observedAt.toString()
        )
        renderings.forEach { rendering -> sensitive.forEach { assertFalse(it in rendering) } }
    }

    @Test
    fun `value equal updates produce deterministic equal results`() {
        val firstFact = componentFact(1)
        val secondFact = componentFact(1)
        val first = MarketplaceIndependentEconomicEvidenceMerger.apply(empty(), observe(firstFact))
        val second = MarketplaceIndependentEconomicEvidenceMerger.apply(empty(), observe(secondFact))
        assertEquals(first, second)

        val firstEvidence = assertIs<MarketplaceIndependentEconomicEvidenceResult.Applied>(first).evidence
        val secondEvidence = assertIs<MarketplaceIndependentEconomicEvidenceResult.Applied>(second).evidence
        assertEquals(firstEvidence, secondEvidence)
        assertEquals(firstEvidence.activeFacts, secondEvidence.activeFacts)
    }

    private fun empty(subject: MarketplaceEconomicEvidenceSubject = this.subject) =
        MarketplaceIndependentEconomicEvidence.empty(subject)

    private fun observe(fact: MarketplaceIndependentEconomicFact) =
        MarketplaceIndependentEconomicEvidenceUpdate.ObserveFact(fact)

    private fun record(attempt: MarketplaceEconomicEvidenceCollectionAttempt) =
        MarketplaceIndependentEconomicEvidenceUpdate.RecordAttempt(attempt)

    private fun correct(correction: MarketplaceEconomicEvidenceCorrection) =
        MarketplaceIndependentEconomicEvidenceUpdate.Correct(correction)

    private fun applied(
        current: MarketplaceIndependentEconomicEvidence,
        update: MarketplaceIndependentEconomicEvidenceUpdate
    ) = assertIs<MarketplaceIndependentEconomicEvidenceResult.Applied>(
        MarketplaceIndependentEconomicEvidenceMerger.apply(current, update)
    ).evidence

    private fun subject(
        organization: String = "10000000-0000-0000-0000-000000000001",
        order: String = "20000000-0000-0000-0000-000000000001",
        marketplace: String = "mercado-livre",
        externalOrder: String = "order-1",
        currency: String = "BRL"
    ) = MarketplaceEconomicEvidenceSubject(
        OrganizationId.parse(organization),
        MarketplaceOrderId.parse(order),
        MarketplaceKey(marketplace),
        MarketplaceExternalOrderId(externalOrder),
        MarketplaceCurrency(currency)
    )

    private fun observationId(number: Int): MarketplaceEconomicEvidenceObservationId =
        MarketplaceEconomicEvidenceObservationId.parse(
            "00000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
        )

    private fun componentId(number: Int): EconomicComponentId = EconomicComponentId.parse(
        "30000000-0000-0000-0000-${number.toString().padStart(12, '0')}"
    )

    private fun componentFact(
        id: Int,
        family: MarketplaceEconomicEvidenceFamily = MarketplaceEconomicEvidenceFamily.MARKETPLACE_SHIPPING,
        type: EconomicComponentType = EconomicComponentType.SHIPPING,
        amount: String = "10.00",
        subject: MarketplaceEconomicEvidenceSubject = this.subject,
        source: EconomicSource = externalSource(EconomicSourceKind.MARKETPLACE, "shipping-reference"),
        externalReference: String? = null,
        componentId: Int = id,
        coverage: EconomicComponentCoverage = EconomicComponentCoverage.COMPLETE,
        occurred: Instant = occurredAt,
        observed: Instant = observedAt,
        componentOrganization: OrganizationId = subject.organizationId,
        componentOrder: MarketplaceOrderId = subject.orderId,
        componentCurrency: MarketplaceCurrency = subject.currency,
        quality: EconomicEvidenceQuality = EconomicEvidenceQuality.CONFIRMED,
        direction: EconomicDirection = if (type == EconomicComponentType.REVENUE) {
            EconomicDirection.ADDITION
        } else {
            EconomicDirection.DEDUCTION
        }
    ): MarketplaceIndependentEconomicFact.Component {
        val effectiveSource = if (externalReference == null) {
            source
        } else {
            EconomicSource(
                source.kind,
                source.systemKey,
                EconomicExternalReferenceState.Present(EconomicExternalReference(externalReference))
            )
        }
        return MarketplaceIndependentEconomicFact.Component(
            MarketplaceEconomicComponentObservation(
                observationId(id),
                subject,
                family,
                EconomicComponent(
                    componentOrganization,
                    componentId(componentId),
                    componentOrder,
                    type,
                    direction,
                    MarketplaceMoney.parse(componentCurrency, amount),
                    effectiveSource,
                    occurred,
                    quality
                ),
                coverage,
                observed
            )
        )
    }

    private fun orderOccurrenceFact(
        id: Int,
        subject: MarketplaceEconomicEvidenceSubject = this.subject,
        source: EconomicSource = externalSource(EconomicSourceKind.MARKETPLACE, "order-occurrence-$id"),
        occurred: Instant = occurredAt,
        observed: Instant = observedAt
    ) = MarketplaceIndependentEconomicFact.OrderOccurrence(
        MarketplaceEconomicOrderOccurrenceObservation(
            observationId(id),
            subject,
            source,
            occurred,
            observed
        )
    )

    private fun identityFact(
        id: Int,
        family: MarketplaceEconomicEvidenceFamily = MarketplaceEconomicEvidenceFamily.ADS_IDENTITY,
        kind: MarketplaceEconomicExternalIdentityKind =
            MarketplaceEconomicExternalIdentityKind.MARKETPLACE_ITEM_TO_AD_GROUP,
        subject: MarketplaceEconomicEvidenceSubject = this.subject,
        linked: String = "ad-group-1",
        source: EconomicSource = externalSource(EconomicSourceKind.MARKETPLACE, "identity-$id"),
        occurred: Instant = occurredAt,
        observed: Instant = observedAt
    ) = MarketplaceIndependentEconomicFact.ExternalIdentity(
        MarketplaceEconomicExternalIdentityObservation(
            observationId(id),
            subject,
            family,
            kind,
            EconomicExternalReference("anchor-$id"),
            EconomicSourceSystemKey("external-system"),
            EconomicExternalReference(linked),
            source,
            occurred,
            observed
        )
    )

    private fun attempt(
        id: Int,
        outcome: MarketplaceEconomicEvidenceAttemptOutcome = MarketplaceEconomicEvidenceAttemptOutcome.NO_EVIDENCE,
        subject: MarketplaceEconomicEvidenceSubject = this.subject,
        attempted: Instant = observedAt
    ) = MarketplaceEconomicEvidenceCollectionAttempt(
        observationId(id),
        subject,
        MarketplaceEconomicEvidenceFamily.MARKETPLACE_SHIPPING,
        EconomicSourceSystemKey("mercado-livre"),
        outcome,
        attempted
    )

    private fun correction(
        id: Int,
        replacement: MarketplaceIndependentEconomicFact,
        supersedes: MarketplaceEconomicEvidenceObservationId,
        observed: Instant = observedAt.plusSeconds(1)
    ) = MarketplaceEconomicEvidenceCorrection(
        observationId(id),
        replacement.subject,
        replacement,
        supersedes,
        MarketplaceEconomicEvidenceCorrectionReason.SOURCE_CORRECTION,
        observed
    )

    private fun externalSource(kind: EconomicSourceKind, reference: String) = EconomicSource(
        kind,
        EconomicSourceSystemKey(if (kind == EconomicSourceKind.ERP) "omie" else "mercado-livre"),
        EconomicExternalReferenceState.Present(EconomicExternalReference(reference))
    )

    private fun internalSource(kind: EconomicSourceKind) = EconomicSource(
        kind,
        EconomicSourceSystemKey("internal"),
        EconomicExternalReferenceState.Absent(EconomicExternalReferenceAbsenceReason.INTERNAL_ORIGIN)
    )

    private fun componentFacts(evidence: MarketplaceIndependentEconomicEvidence) =
        componentFactsFrom(evidence.activeFacts)

    private fun componentFactsFrom(facts: List<MarketplaceIndependentEconomicFact>) =
        facts.filterIsInstance<MarketplaceIndependentEconomicFact.Component>().map { it.observation }

    private fun identityKind(fact: MarketplaceIndependentEconomicFact): MarketplaceEconomicExternalIdentityKind? =
        (fact as? MarketplaceIndependentEconomicFact.ExternalIdentity)?.observation?.kind

    private fun externalReferenceOf(source: EconomicSource): String =
        (source.externalReference as EconomicExternalReferenceState.Present).reference.value
}
