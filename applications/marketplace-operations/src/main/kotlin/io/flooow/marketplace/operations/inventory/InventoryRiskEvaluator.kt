package io.flooow.marketplace.operations.inventory

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Decision
import io.flooow.kernel.model.Observation
import io.flooow.kernel.reasoning.EvaluationRequest
import io.flooow.kernel.reasoning.EvidenceSet
import io.flooow.kernel.reasoning.Hypothesis
import io.flooow.kernel.reasoning.ReasoningConfiguration
import io.flooow.kernel.reasoning.ReasoningModule
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset

class InventoryRiskEvaluator {

    fun evaluate(input: InventoryRiskInput): InventoryRiskAssessment {
        val evaluationInstant = input.observedOn.atStartOfDay().toInstant(ZoneOffset.UTC)
        val timestamp = Timestamp(evaluationInstant)
        val clock = Clock.fixed(evaluationInstant, ZoneOffset.UTC)

        val observations = observationsFrom(input, timestamp)
        val evidences = observations.mapIndexed { index, observation ->
            certainEvidence(
                id = "evidence-${input.sku.lowercase()}-${index + 1}",
                observation = observation,
                recordedAt = timestamp
            )
        }

        val projection = project(input)
        val hypothesisOutcome = if (projection.shortageProjected) {
            "inventory-gap"
        } else {
            "inventory-sufficient"
        }
        val hypothesisStatement = if (projection.shortageProjected) {
            "A projected inventory gap will compromise the sales goal for ${input.sku}."
        } else {
            "The observed inventory trajectory is sufficient until replenishment for ${input.sku}."
        }
        val hypothesis = Hypothesis(
            id = Identifier("hypothesis-${input.sku.lowercase()}-$hypothesisOutcome"),
            statement = hypothesisStatement,
            confidence = Confidence.CERTAIN,
            createdAt = timestamp
        )
        val reasoningResult = ReasoningModule.deterministic(
            ReasoningConfiguration(clock = clock)
        ).evaluate(
            EvaluationRequest(
                hypothesis = hypothesis,
                evidenceSet = EvidenceSet(evidences.toSet())
            )
        )

        val alternatives = alternativesFor(projection)
        val selected = selectAlternative(projection, alternatives)
        val recommendation = Decision(
            id = Identifier("decision-${input.sku.lowercase()}-inventory-risk"),
            statement = selected.explanation,
            evidenceIds = evidences.mapTo(linkedSetOf()) { it.id },
            decidedAt = timestamp
        )

        return InventoryRiskAssessment(
            input = input,
            observations = observations,
            evidences = evidences,
            projection = projection,
            judgment = reasoningResult.judgment,
            alternatives = alternatives,
            recommendation = recommendation,
            expectedImpact = expectedImpactFor(projection, selected),
            trace = traceFor(input, projection, selected)
        )
    }

    private fun observationsFrom(
        input: InventoryRiskInput,
        timestamp: Timestamp
    ): List<Observation> = listOf(
        Observation(
            id = Identifier("observation-${input.sku.lowercase()}-goal"),
            description = "Sales goal is ${input.targetUnits} units by ${input.periodEnd}; ${input.unitsSold} units are sold.",
            observedAt = timestamp
        ),
        Observation(
            id = Identifier("observation-${input.sku.lowercase()}-inventory"),
            description = "Available inventory is ${input.availableUnits} units on ${input.observedOn}.",
            observedAt = timestamp
        ),
        Observation(
            id = Identifier("observation-${input.sku.lowercase()}-velocity"),
            description = "Observed daily sales velocity is ${input.dailySalesVelocity} units.",
            observedAt = timestamp
        ),
        Observation(
            id = Identifier("observation-${input.sku.lowercase()}-replenishment"),
            description = "Replenishment is expected on ${input.expectedReplenishmentOn}.",
            observedAt = timestamp
        )
    )

    private fun project(input: InventoryRiskInput): InventoryProjection {
        val coverageDays = ceilingDivision(input.availableUnits, input.dailySalesVelocity)
        val projectedStockoutOn = input.observedOn.plusDays(coverageDays.toLong())
        val gapEnd = minOf(input.expectedReplenishmentOn, input.periodEnd.plusDays(1))
        val stockoutDays = maxOf(
            0,
            Duration.between(
                projectedStockoutOn.atStartOfDay(),
                gapEnd.atStartOfDay()
            ).toDays().toInt()
        )
        val unavailable = stockoutDays * input.dailySalesVelocity
        val remainingToGoal = maxOf(0, input.targetUnits - input.unitsSold)

        return InventoryProjection(
            stockCoverageDays = coverageDays,
            projectedStockoutOn = projectedStockoutOn,
            expectedReplenishmentOn = input.expectedReplenishmentOn,
            projectedStockoutDays = stockoutDays,
            unitsPotentiallyUnavailable = unavailable,
            unitsRemainingToGoal = remainingToGoal,
            unitsAtRiskAgainstGoal = minOf(unavailable, remainingToGoal)
        )
    }

    private fun alternativesFor(
        projection: InventoryProjection
    ): List<InterventionAlternative> = listOf(
        InterventionAlternative(
            type = InterventionType.EXPEDITE_REPLENISHMENT,
            explanation = "Expedite replenishment to arrive no later than ${projection.projectedStockoutOn}.",
            expectedUnitsPreserved = projection.unitsAtRiskAgainstGoal
        ),
        InterventionAlternative(
            type = InterventionType.REDUCE_PROMOTIONAL_EXPOSURE,
            explanation = "Reduce promotional exposure until replenishment arrives.",
            expectedUnitsPreserved = 0
        ),
        InterventionAlternative(
            type = InterventionType.TAKE_NO_ACTION,
            explanation = "Maintain the current plan and accept the projected inventory exposure.",
            expectedUnitsPreserved = 0
        )
    )

    private fun selectAlternative(
        projection: InventoryProjection,
        alternatives: List<InterventionAlternative>
    ): InterventionAlternative = if (projection.shortageProjected) {
        alternatives.first { it.type == InterventionType.EXPEDITE_REPLENISHMENT }
    } else {
        alternatives.first { it.type == InterventionType.TAKE_NO_ACTION }
    }

    private fun expectedImpactFor(
        projection: InventoryProjection,
        selected: InterventionAlternative
    ): String = if (projection.shortageProjected) {
        "If completed before the projected stockout, the intervention is expected to preserve up to " +
            "${selected.expectedUnitsPreserved} units toward the goal; this is a projection, not a guaranteed outcome."
    } else {
        "No inventory-driven loss against the current goal is projected from the supplied observations."
    }

    private fun traceFor(
        input: InventoryRiskInput,
        projection: InventoryProjection,
        selected: InterventionAlternative
    ): List<String> = listOf(
        "goal.remaining=${projection.unitsRemainingToGoal}",
        "inventory.available=${input.availableUnits}",
        "velocity.daily=${input.dailySalesVelocity}",
        "coverage.days=${projection.stockCoverageDays}",
        "stockout.projectedOn=${projection.projectedStockoutOn}",
        "replenishment.expectedOn=${projection.expectedReplenishmentOn}",
        "stockout.durationDays=${projection.projectedStockoutDays}",
        "shortage.unitsUnavailable=${projection.unitsPotentiallyUnavailable}",
        "goal.unitsAtRisk=${projection.unitsAtRiskAgainstGoal}",
        "recommendation=${selected.type}"
    )

    private fun ceilingDivision(dividend: Int, divisor: Int): Int =
        if (dividend == 0) 0 else 1 + (dividend - 1) / divisor
}
