package io.flooow.marketplace.operations.inventory

import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertSame

class InventoryWorkflowTest {

    private val workflow = InventoryWorkflow()
    private val redMoto = SkuRef(Identifier("RED-MOTO-KIT-001"))
    private val baseline = InventorySnapshot(
        sku = redMoto,
        availableUnits = 30,
        effectiveAt = Timestamp.parse("2026-08-01T10:00:00Z")
    )

    @Test
    fun `receiving replenishment produces a later snapshot and occurrence`() {
        val command = command(
            type = InventoryCommandType.RECEIVE,
            quantity = 50,
            effectiveAt = "2026-08-02T10:00:00Z"
        )

        val result = assertIs<AcceptedInventoryTransition>(
            workflow.execute(baseline, command)
        )

        assertEquals(baseline, result.inputSnapshot)
        assertEquals(80, result.resultingSnapshot.availableUnits)
        assertEquals(redMoto, result.resultingSnapshot.sku)
        assertEquals(command.effectiveAt, result.resultingSnapshot.effectiveAt)
        assertEquals(
            InventoryOccurrence(
                sku = redMoto,
                type = InventoryCommandType.RECEIVE,
                quantity = 50,
                effectiveAt = command.effectiveAt
            ),
            result.occurrence
        )
        assertEquals(
            listOf(
                InventoryTransitionRule.IDENTITY,
                InventoryTransitionRule.TEMPORAL_ORDER,
                InventoryTransitionRule.AVAILABILITY,
                InventoryTransitionRule.RESULT
            ),
            result.trace.map { it.rule }
        )
    }

    @Test
    fun `consuming available inventory produces a non-negative snapshot`() {
        val result = assertIs<AcceptedInventoryTransition>(
            workflow.execute(
                baseline,
                command(
                    type = InventoryCommandType.CONSUME,
                    quantity = 20,
                    effectiveAt = "2026-08-01T11:00:00Z"
                )
            )
        )

        assertEquals(10, result.resultingSnapshot.availableUnits)
        assertEquals(30, baseline.availableUnits)
    }

    @Test
    fun `consuming more than available inventory is rejected without a new snapshot`() {
        val result = assertIs<RejectedInventoryTransition>(
            workflow.execute(
                baseline,
                command(
                    type = InventoryCommandType.CONSUME,
                    quantity = 31,
                    effectiveAt = "2026-08-01T11:00:00Z"
                )
            )
        )

        assertEquals(
            InventoryTransitionRejectionReason.INSUFFICIENT_INVENTORY,
            result.reason
        )
        assertSame(baseline, result.inputSnapshot)
        assertEquals(InventoryTransitionRule.AVAILABILITY, result.trace.first().rule)
    }

    @Test
    fun `command for another SKU is rejected and preserves identity`() {
        val result = assertIs<RejectedInventoryTransition>(
            workflow.execute(
                baseline,
                command(
                    sku = SkuRef(Identifier("BLUE-MOTO-KIT-002")),
                    type = InventoryCommandType.RECEIVE,
                    quantity = 10,
                    effectiveAt = "2026-08-01T11:00:00Z"
                )
            )
        )

        assertEquals(InventoryTransitionRejectionReason.SKU_MISMATCH, result.reason)
        assertSame(baseline, result.inputSnapshot)
    }

    @Test
    fun `command without a later timestamp is rejected`() {
        listOf(
            "2026-08-01T10:00:00Z",
            "2026-08-01T09:59:59Z"
        ).forEach { timestamp ->
            val result = assertIs<RejectedInventoryTransition>(
                workflow.execute(
                    baseline,
                    command(
                        type = InventoryCommandType.RECEIVE,
                        quantity = 10,
                        effectiveAt = timestamp
                    )
                )
            )

            assertEquals(
                InventoryTransitionRejectionReason.NON_FORWARD_TIMESTAMP,
                result.reason
            )
            assertSame(baseline, result.inputSnapshot)
        }
    }

    @Test
    fun `identical inputs produce identical accepted and rejected results`() {
        val acceptedCommand = command(
            type = InventoryCommandType.RECEIVE,
            quantity = 50,
            effectiveAt = "2026-08-02T10:00:00Z"
        )
        val rejectedCommand = command(
            type = InventoryCommandType.CONSUME,
            quantity = 31,
            effectiveAt = "2026-08-02T10:00:00Z"
        )

        assertEquals(
            workflow.execute(baseline, acceptedCommand),
            workflow.execute(baseline, acceptedCommand)
        )
        assertEquals(
            workflow.execute(baseline, rejectedCommand),
            workflow.execute(baseline, rejectedCommand)
        )
    }

    @Test
    fun `intervention expected impact and observed outcome remain separate`() {
        val intervention = InventoryIntervention(
            id = Identifier("intervention-expedite-red-moto"),
            sku = redMoto,
            description = "Expedite Red Moto replenishment"
        )
        val plan = InventoryInterventionPlan(
            intervention = intervention,
            expectedImpact = "Avoid 40 units of projected unavailability"
        )
        val outcome = InventoryOutcome(
            id = Identifier("outcome-expedite-red-moto"),
            interventionId = intervention.id,
            observedAt = Timestamp.parse("2026-08-03T10:00:00Z"),
            description = "Replenishment arrived after 20 units became unavailable"
        )

        assertEquals(intervention.id, outcome.interventionId)
        assertNotEquals(plan.expectedImpact, outcome.description)
        assertEquals(redMoto, plan.intervention.sku)
    }

    private fun command(
        sku: SkuRef = redMoto,
        type: InventoryCommandType,
        quantity: Int,
        effectiveAt: String
    ): InventoryCommand = InventoryCommand(
        sku = sku,
        type = type,
        quantity = quantity,
        effectiveAt = Timestamp.parse(effectiveAt)
    )
}
