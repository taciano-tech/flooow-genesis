package io.flooow.marketplace.operations.inventory

import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Action
import io.flooow.kernel.model.Outcome

@JvmInline
value class SkuRef(
    val id: Identifier
)

data class InventorySnapshot(
    val sku: SkuRef,
    val availableUnits: Int,
    val effectiveAt: Timestamp
) {
    init {
        require(availableUnits >= 0) {
            "Available units must not be negative"
        }
    }
}

enum class InventoryCommandType {
    RECEIVE,
    CONSUME
}

data class InventoryCommand(
    val sku: SkuRef,
    val type: InventoryCommandType,
    val quantity: Int,
    val effectiveAt: Timestamp
) {
    init {
        require(quantity > 0) {
            "Inventory command quantity must be positive"
        }
    }
}

data class InventoryOccurrence(
    val sku: SkuRef,
    val type: InventoryCommandType,
    val quantity: Int,
    val effectiveAt: Timestamp
) {
    init {
        require(quantity > 0) {
            "Inventory occurrence quantity must be positive"
        }
    }
}

enum class InventoryTransitionRule {
    IDENTITY,
    TEMPORAL_ORDER,
    AVAILABILITY,
    RESULT
}

data class InventoryTransitionTraceStep(
    val rule: InventoryTransitionRule,
    val detail: String
) {
    init {
        require(detail.isNotBlank()) {
            "Inventory transition trace detail must not be blank"
        }
        require(detail == detail.trim()) {
            "Inventory transition trace detail must not contain surrounding whitespace"
        }
    }
}

sealed interface InventoryTransitionResult {
    val inputSnapshot: InventorySnapshot
    val command: InventoryCommand
    val trace: List<InventoryTransitionTraceStep>
}

data class AcceptedInventoryTransition(
    override val inputSnapshot: InventorySnapshot,
    override val command: InventoryCommand,
    val occurrence: InventoryOccurrence,
    val resultingSnapshot: InventorySnapshot,
    override val trace: List<InventoryTransitionTraceStep>
) : InventoryTransitionResult {
    init {
        require(resultingSnapshot.sku == inputSnapshot.sku) {
            "Accepted inventory transition must preserve SKU identity"
        }
        require(resultingSnapshot.effectiveAt > inputSnapshot.effectiveAt) {
            "Accepted inventory transition must move forward in time"
        }
        require(occurrence.sku == command.sku) {
            "Inventory occurrence must refer to the command SKU"
        }
        require(occurrence.effectiveAt == command.effectiveAt) {
            "Inventory occurrence must use the command timestamp"
        }
        require(trace.isNotEmpty()) {
            "Accepted inventory transition trace must not be empty"
        }
    }
}

enum class InventoryTransitionRejectionReason {
    SKU_MISMATCH,
    NON_FORWARD_TIMESTAMP,
    INSUFFICIENT_INVENTORY
}

data class RejectedInventoryTransition(
    override val inputSnapshot: InventorySnapshot,
    override val command: InventoryCommand,
    val reason: InventoryTransitionRejectionReason,
    override val trace: List<InventoryTransitionTraceStep>
) : InventoryTransitionResult {
    init {
        require(trace.isNotEmpty()) {
            "Rejected inventory transition trace must not be empty"
        }
    }
}

data class InventoryIntervention(
    val id: Identifier,
    val sku: SkuRef,
    val description: String
) : Action {
    init {
        require(description.isNotBlank()) {
            "Inventory intervention description must not be blank"
        }
        require(description == description.trim()) {
            "Inventory intervention description must not contain surrounding whitespace"
        }
    }
}

data class InventoryInterventionPlan(
    val intervention: InventoryIntervention,
    val expectedImpact: String
) {
    init {
        require(expectedImpact.isNotBlank()) {
            "Expected impact must not be blank"
        }
        require(expectedImpact == expectedImpact.trim()) {
            "Expected impact must not contain surrounding whitespace"
        }
    }
}

data class InventoryOutcome(
    val id: Identifier,
    val interventionId: Identifier,
    val observedAt: Timestamp,
    val description: String
) : Outcome {
    init {
        require(description.isNotBlank()) {
            "Inventory outcome description must not be blank"
        }
        require(description == description.trim()) {
            "Inventory outcome description must not contain surrounding whitespace"
        }
    }
}
