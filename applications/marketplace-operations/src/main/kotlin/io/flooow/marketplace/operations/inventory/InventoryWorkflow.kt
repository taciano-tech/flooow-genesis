package io.flooow.marketplace.operations.inventory

class InventoryWorkflow {

    fun execute(
        snapshot: InventorySnapshot,
        command: InventoryCommand
    ): InventoryTransitionResult {
        if (snapshot.sku != command.sku) {
            return rejected(
                snapshot = snapshot,
                command = command,
                reason = InventoryTransitionRejectionReason.SKU_MISMATCH,
                rule = InventoryTransitionRule.IDENTITY,
                detail = "Rejected command because its SKU differs from the snapshot SKU"
            )
        }

        if (command.effectiveAt <= snapshot.effectiveAt) {
            return rejected(
                snapshot = snapshot,
                command = command,
                reason = InventoryTransitionRejectionReason.NON_FORWARD_TIMESTAMP,
                rule = InventoryTransitionRule.TEMPORAL_ORDER,
                detail = "Rejected command because its timestamp is not later than the snapshot"
            )
        }

        if (
            command.type == InventoryCommandType.CONSUME &&
            command.quantity > snapshot.availableUnits
        ) {
            return rejected(
                snapshot = snapshot,
                command = command,
                reason = InventoryTransitionRejectionReason.INSUFFICIENT_INVENTORY,
                rule = InventoryTransitionRule.AVAILABILITY,
                detail = "Rejected consumption because its quantity exceeds available inventory"
            )
        }

        val resultingUnits = when (command.type) {
            InventoryCommandType.RECEIVE -> snapshot.availableUnits + command.quantity
            InventoryCommandType.CONSUME -> snapshot.availableUnits - command.quantity
        }
        val occurrence = InventoryOccurrence(
            sku = command.sku,
            type = command.type,
            quantity = command.quantity,
            effectiveAt = command.effectiveAt
        )
        val resultingSnapshot = InventorySnapshot(
            sku = snapshot.sku,
            availableUnits = resultingUnits,
            effectiveAt = command.effectiveAt
        )

        return AcceptedInventoryTransition(
            inputSnapshot = snapshot,
            command = command,
            occurrence = occurrence,
            resultingSnapshot = resultingSnapshot,
            trace = listOf(
                InventoryTransitionTraceStep(
                    rule = InventoryTransitionRule.IDENTITY,
                    detail = "Accepted command for the snapshot SKU"
                ),
                InventoryTransitionTraceStep(
                    rule = InventoryTransitionRule.TEMPORAL_ORDER,
                    detail = "Accepted command with a later timestamp"
                ),
                InventoryTransitionTraceStep(
                    rule = InventoryTransitionRule.AVAILABILITY,
                    detail = availabilityDetail(command)
                ),
                InventoryTransitionTraceStep(
                    rule = InventoryTransitionRule.RESULT,
                    detail = "Produced occurrence and resulting inventory snapshot"
                )
            )
        )
    }

    private fun rejected(
        snapshot: InventorySnapshot,
        command: InventoryCommand,
        reason: InventoryTransitionRejectionReason,
        rule: InventoryTransitionRule,
        detail: String
    ): RejectedInventoryTransition = RejectedInventoryTransition(
        inputSnapshot = snapshot,
        command = command,
        reason = reason,
        trace = listOf(
            InventoryTransitionTraceStep(
                rule = rule,
                detail = detail
            ),
            InventoryTransitionTraceStep(
                rule = InventoryTransitionRule.RESULT,
                detail = "Preserved the input snapshot without an accepted occurrence"
            )
        )
    )

    private fun availabilityDetail(command: InventoryCommand): String =
        when (command.type) {
            InventoryCommandType.RECEIVE ->
                "Accepted receipt with a positive quantity"

            InventoryCommandType.CONSUME ->
                "Accepted consumption within available inventory"
        }
}
