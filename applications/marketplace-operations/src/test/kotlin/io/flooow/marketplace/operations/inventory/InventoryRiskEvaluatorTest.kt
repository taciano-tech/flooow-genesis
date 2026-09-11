package io.flooow.marketplace.operations.inventory

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class InventoryRiskEvaluatorTest {

    private val evaluator = InventoryRiskEvaluator()

    private val redMotoScenario = InventoryRiskInput(
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
    fun `explains how an inventory shortage compromises the sales goal`() {
        val assessment = evaluator.evaluate(redMotoScenario)

        assertEquals(3, assessment.projection.stockCoverageDays)
        assertEquals(
            LocalDate.parse("2026-08-04"),
            assessment.projection.projectedStockoutOn
        )
        assertEquals(4, assessment.projection.projectedStockoutDays)
        assertEquals(40, assessment.projection.unitsPotentiallyUnavailable)
        assertEquals(180, assessment.projection.unitsRemainingToGoal)
        assertEquals(40, assessment.projection.unitsAtRiskAgainstGoal)
        assertTrue(assessment.projection.shortageProjected)
        assertEquals(
            InterventionType.EXPEDITE_REPLENISHMENT,
            assessment.alternatives.first().type
        )
        assertTrue(assessment.recommendation.statement.contains("2026-08-04"))
        assertTrue(assessment.expectedImpact.contains("up to 40 units"))
        assertTrue(assessment.expectedImpact.contains("not a guaranteed outcome"))
    }

    @Test
    fun `records explicit observations evidence judgment and calculation trace`() {
        val assessment = evaluator.evaluate(redMotoScenario)

        assertEquals(4, assessment.observations.size)
        assertEquals(4, assessment.evidences.size)
        assertEquals(
            assessment.evidences.map { it.id }.toSet(),
            assessment.recommendation.evidenceIds
        )
        assertEquals(1.0, assessment.judgment.confidence.value)
        assertEquals(
            "goal.unitsAtRisk=40",
            assessment.trace.single { it.startsWith("goal.unitsAtRisk=") }
        )
        assertEquals(
            "recommendation=EXPEDITE_REPLENISHMENT",
            assessment.trace.last()
        )
    }

    @Test
    fun `preserves the complete reasoning chain from observations to decision`() {
        val assessment = evaluator.evaluate(redMotoScenario)

        assertEquals(
            assessment.hypothesis.id,
            assessment.evaluation.judgment.hypothesisId
        )
        assertEquals(
            assessment.evaluation.judgment,
            assessment.decisionContext.judgment
        )
        assertEquals(
            assessment.evaluation.evaluatedEvidence,
            assessment.decisionContext.evidenceSet
        )
        assertEquals(
            assessment.decisionContext.evidenceSet.evidences.map { it.id }.toSet(),
            assessment.recommendation.evidenceIds
        )

        val stages = assessment.reasoningTrace.map { it.stage }.toSet()
        assertEquals(ReasoningStage.entries.toSet(), stages)

        val judgmentStep = assessment.reasoningTrace.single {
            it.stage == ReasoningStage.JUDGMENT
        }
        assertEquals(setOf(assessment.hypothesis.id), judgmentStep.references)

        val decisionStep = assessment.reasoningTrace.single {
            it.stage == ReasoningStage.DECISION
        }
        assertTrue(assessment.judgment.id in decisionStep.references)
        assertTrue(
            assessment.recommendation.evidenceIds.all {
                it in decisionStep.references
            }
        )
    }

    @Test
    fun `produces identical results for identical inputs`() {
        assertEquals(
            evaluator.evaluate(redMotoScenario),
            evaluator.evaluate(redMotoScenario)
        )
    }

    @Test
    fun `does not recommend intervention when replenishment precedes stockout`() {
        val input = redMotoScenario.copy(
            availableUnits = 100,
            expectedReplenishmentOn = LocalDate.parse("2026-08-05")
        )

        val assessment = evaluator.evaluate(input)

        assertFalse(assessment.projection.shortageProjected)
        assertEquals(0, assessment.projection.projectedStockoutDays)
        assertEquals(0, assessment.projection.unitsAtRiskAgainstGoal)
        assertEquals(
            InterventionType.TAKE_NO_ACTION,
            assessment.alternatives.single {
                it.explanation == assessment.recommendation.statement
            }.type
        )
    }

    @Test
    fun `rejects invalid operational input`() {
        assertFailsWith<IllegalArgumentException> {
            redMotoScenario.copy(dailySalesVelocity = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            redMotoScenario.copy(availableUnits = -1)
        }
        assertFailsWith<IllegalArgumentException> {
            redMotoScenario.copy(
                expectedReplenishmentOn = LocalDate.parse("2026-07-31")
            )
        }
    }
}
