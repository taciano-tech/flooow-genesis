package io.flooow.marketplace.operations.inventory

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Decision
import io.flooow.kernel.model.Evidence
import io.flooow.kernel.model.Observation
import io.flooow.kernel.reasoning.DecisionContext
import io.flooow.kernel.reasoning.EvaluationResult
import io.flooow.kernel.reasoning.Hypothesis
import io.flooow.kernel.reasoning.Judgment
import java.time.LocalDate

data class InventoryRiskInput(
    val sku: String,
    val periodEnd: LocalDate,
    val targetUnits: Int,
    val unitsSold: Int,
    val availableUnits: Int,
    val dailySalesVelocity: Int,
    val observedOn: LocalDate,
    val expectedReplenishmentOn: LocalDate
) {
    init {
        require(sku.isNotBlank()) { "SKU must not be blank" }
        require(sku == sku.trim()) { "SKU must not contain surrounding whitespace" }
        require(targetUnits > 0) { "Target units must be positive" }
        require(unitsSold >= 0) { "Units sold must not be negative" }
        require(availableUnits >= 0) { "Available units must not be negative" }
        require(dailySalesVelocity > 0) { "Daily sales velocity must be positive" }
        require(periodEnd >= observedOn) { "Period end must not precede the observation date" }
        require(expectedReplenishmentOn >= observedOn) {
            "Expected replenishment must not precede the observation date"
        }
    }
}

data class InventoryProjection(
    val stockCoverageDays: Int,
    val projectedStockoutOn: LocalDate,
    val expectedReplenishmentOn: LocalDate,
    val projectedStockoutDays: Int,
    val unitsPotentiallyUnavailable: Int,
    val unitsRemainingToGoal: Int,
    val unitsAtRiskAgainstGoal: Int
) {
    val shortageProjected: Boolean
        get() = projectedStockoutDays > 0 && unitsAtRiskAgainstGoal > 0
}

enum class InterventionType {
    EXPEDITE_REPLENISHMENT,
    REDUCE_PROMOTIONAL_EXPOSURE,
    TAKE_NO_ACTION
}

data class InterventionAlternative(
    val type: InterventionType,
    val explanation: String,
    val expectedUnitsPreserved: Int
)

enum class ReasoningStage {
    OBSERVATION,
    EVIDENCE,
    HYPOTHESIS,
    JUDGMENT,
    DECISION
}

data class ReasoningTraceStep(
    val stage: ReasoningStage,
    val conceptId: Identifier,
    val references: Set<Identifier>
)

data class InventoryRiskAssessment(
    val input: InventoryRiskInput,
    val observations: List<Observation>,
    val evidences: List<Evidence>,
    val projection: InventoryProjection,
    val hypothesis: Hypothesis,
    val evaluation: EvaluationResult,
    val decisionContext: DecisionContext,
    val alternatives: List<InterventionAlternative>,
    val recommendation: Decision,
    val expectedImpact: String,
    val trace: List<String>,
    val reasoningTrace: List<ReasoningTraceStep>
) {
    val judgment: Judgment
        get() = evaluation.judgment

    val selectedAlternative: InterventionAlternative
        get() = alternatives.single {
            it.explanation == recommendation.statement
        }
}

internal fun certainEvidence(
    id: String,
    observation: Observation,
    recordedAt: Timestamp
): Evidence = Evidence(
    id = Identifier(id),
    observationIds = setOf(observation.id),
    confidence = Confidence.CERTAIN,
    recordedAt = recordedAt
)
