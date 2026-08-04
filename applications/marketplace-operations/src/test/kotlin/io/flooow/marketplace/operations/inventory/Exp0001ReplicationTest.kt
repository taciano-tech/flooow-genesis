package io.flooow.marketplace.operations.inventory

import java.time.LocalDate
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

class Exp0001ReplicationTest {

    @Test
    fun `canonical fixture reproduces the committed evidence snapshot`() {
        val input = loadInput("/exp-0001/red-moto-input.properties")
        val expected = resourceText("/exp-0001/red-moto-expected.snapshot")

        val assessment = InventoryRiskEvaluator().evaluate(input)

        assertEquals(expected.trimEnd(), snapshotOf(assessment).trimEnd())
    }

    private fun loadInput(resource: String): InventoryRiskInput {
        val properties = Properties().apply {
            Exp0001ReplicationTest::class.java
                .getResourceAsStream(resource)
                .use { stream ->
                    requireNotNull(stream) { "Missing replication fixture: $resource" }
                    load(stream)
                }
        }

        return InventoryRiskInput(
            sku = properties.required("sku"),
            periodEnd = LocalDate.parse(properties.required("periodEnd")),
            targetUnits = properties.required("targetUnits").toInt(),
            unitsSold = properties.required("unitsSold").toInt(),
            availableUnits = properties.required("availableUnits").toInt(),
            dailySalesVelocity = properties.required("dailySalesVelocity").toInt(),
            observedOn = LocalDate.parse(properties.required("observedOn")),
            expectedReplenishmentOn = LocalDate.parse(
                properties.required("expectedReplenishmentOn")
            )
        )
    }

    private fun snapshotOf(assessment: InventoryRiskAssessment): String =
        listOf(
            "sku=${assessment.input.sku}",
            "coverage.days=${assessment.projection.stockCoverageDays}",
            "stockout.projectedOn=${assessment.projection.projectedStockoutOn}",
            "stockout.durationDays=${assessment.projection.projectedStockoutDays}",
            "shortage.unitsUnavailable=${assessment.projection.unitsPotentiallyUnavailable}",
            "goal.unitsRemaining=${assessment.projection.unitsRemainingToGoal}",
            "goal.unitsAtRisk=${assessment.projection.unitsAtRiskAgainstGoal}",
            "hypothesis.id=${assessment.hypothesis.id}",
            "judgment.id=${assessment.judgment.id}",
            "judgment.confidence=${assessment.judgment.confidence.value}",
            "decision.id=${assessment.recommendation.id}",
            "decision.statement=${assessment.recommendation.statement}",
            "reasoning.stages=${assessment.reasoningTrace.joinToString(",") { it.stage.name }}"
        ).joinToString("\n")

    private fun resourceText(resource: String): String =
        requireNotNull(Exp0001ReplicationTest::class.java.getResource(resource)) {
            "Missing expected snapshot: $resource"
        }.readText().replace("\r\n", "\n")

    private fun Properties.required(name: String): String =
        requireNotNull(getProperty(name)) { "Missing fixture property: $name" }
}
