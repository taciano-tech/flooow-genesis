package io.flooow.marketplace.operations.inventory

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InventoryRiskBoundaryTest {

    private val evaluator = InventoryRiskEvaluator()

    private val baseline = InventoryRiskInput(
        sku = "RED-MOTO-KIT-001",
        periodEnd = LocalDate.parse("2026-08-31"),
        targetUnits = 300,
        unitsSold = 120,
        availableUnits = 30,
        dailySalesVelocity = 10,
        observedOn = LocalDate.parse("2026-08-01"),
        expectedReplenishmentOn = LocalDate.parse("2026-08-08")
    )

    @Test
    fun `zero inventory starts the projected gap on the observation date`() {
        val assessment = evaluator.evaluate(baseline.copy(availableUnits = 0))

        assertEquals(0, assessment.projection.stockCoverageDays)
        assertEquals(baseline.observedOn, assessment.projection.projectedStockoutOn)
        assertEquals(7, assessment.projection.projectedStockoutDays)
        assertEquals(70, assessment.projection.unitsPotentiallyUnavailable)
        assertEquals(70, assessment.projection.unitsAtRiskAgainstGoal)
        assertTrue(assessment.projection.shortageProjected)
    }

    @Test
    fun `partial final day rounds stock coverage up`() {
        val assessment = evaluator.evaluate(baseline.copy(availableUnits = 31))

        assertEquals(4, assessment.projection.stockCoverageDays)
        assertEquals(
            LocalDate.parse("2026-08-05"),
            assessment.projection.projectedStockoutOn
        )
        assertEquals(3, assessment.projection.projectedStockoutDays)
        assertEquals(30, assessment.projection.unitsPotentiallyUnavailable)
    }

    @Test
    fun `replenishment on the stockout date prevents an inventory gap`() {
        val assessment = evaluator.evaluate(
            baseline.copy(
                expectedReplenishmentOn = LocalDate.parse("2026-08-04")
            )
        )

        assertEquals(0, assessment.projection.projectedStockoutDays)
        assertEquals(0, assessment.projection.unitsPotentiallyUnavailable)
        assertFalse(assessment.projection.shortageProjected)
    }

    @Test
    fun `projected shortage is capped at the commercial period end`() {
        val assessment = evaluator.evaluate(
            baseline.copy(
                periodEnd = LocalDate.parse("2026-08-05"),
                availableUnits = 10,
                expectedReplenishmentOn = LocalDate.parse("2026-08-20")
            )
        )

        assertEquals(LocalDate.parse("2026-08-02"), assessment.projection.projectedStockoutOn)
        assertEquals(4, assessment.projection.projectedStockoutDays)
        assertEquals(40, assessment.projection.unitsPotentiallyUnavailable)
    }

    @Test
    fun `stockout does not put an already achieved goal at risk`() {
        val assessment = evaluator.evaluate(
            baseline.copy(
                unitsSold = baseline.targetUnits,
                availableUnits = 0
            )
        )

        assertEquals(7, assessment.projection.projectedStockoutDays)
        assertEquals(70, assessment.projection.unitsPotentiallyUnavailable)
        assertEquals(0, assessment.projection.unitsRemainingToGoal)
        assertEquals(0, assessment.projection.unitsAtRiskAgainstGoal)
        assertFalse(assessment.projection.shortageProjected)
        assertEquals(
            InterventionType.TAKE_NO_ACTION,
            assessment.alternatives.single {
                it.explanation == assessment.recommendation.statement
            }.type
        )
    }
}
