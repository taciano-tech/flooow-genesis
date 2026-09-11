package io.flooow.marketplace.operations.inventory

import io.flooow.organization.OrganizationId
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class InventoryRiskAssessmentRecorderTest {
    private val organizationId =
        OrganizationId.parse("11111111-1111-4111-8111-111111111111")
    private val input = InventoryRiskInput(
        sku = "RED-MOTO-001",
        periodEnd = LocalDate.parse("2026-08-31"),
        targetUnits = 300,
        unitsSold = 180,
        availableUnits = 90,
        dailySalesVelocity = 15,
        observedOn = LocalDate.parse("2026-08-10"),
        expectedReplenishmentOn = LocalDate.parse("2026-08-20")
    )

    @Test
    fun `records only after append succeeds`() {
        val journal = InMemoryJournal()
        val recorder = InventoryRiskAssessmentRecorder(
            journal = journal,
            identifierFactory = AssessmentIdentifierFactory {
                "11111111-1111-4111-8111-111111111111"
            },
            clock = Clock.fixed(Instant.parse("2026-08-10T13:00:00Z"), ZoneOffset.UTC)
        )

        val recorded = recorder.record(organizationId, input)

        assertEquals(recorded, journal.findById(organizationId, recorded.assessmentId))
        assertEquals(64, recorded.requestDigest.length)
        assertEquals(64, recorded.resultDigest.length)
    }

    @Test
    fun `equivalent assessments retain stable business digests`() {
        val journal = InMemoryJournal()
        var sequence = 0
        val recorder = InventoryRiskAssessmentRecorder(
            journal = journal,
            identifierFactory = AssessmentIdentifierFactory {
                sequence += 1
                "00000000-0000-4000-8000-${sequence.toString().padStart(12, '0')}"
            }
        )

        val first = recorder.record(organizationId, input)
        val second = recorder.record(organizationId, input)

        assertNotEquals(first.assessmentId, second.assessmentId)
        assertEquals(first.requestDigest, second.requestDigest)
        assertEquals(first.resultDigest, second.resultDigest)
    }

    private class InMemoryJournal : InventoryRiskAssessmentJournal {
        private val records =
            linkedMapOf<Pair<OrganizationId, String>, RecordedInventoryRiskAssessment>()

        override fun append(
            organizationId: OrganizationId,
            record: RecordedInventoryRiskAssessment
        ) {
            require(record.organizationId == organizationId)
            check(records.putIfAbsent(organizationId to record.assessmentId, record) == null)
        }

        override fun findById(
            organizationId: OrganizationId,
            assessmentId: String
        ): RecordedInventoryRiskAssessment? = records[organizationId to assessmentId]
    }
}
