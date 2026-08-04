package io.flooow.marketplace.operations.inventory

import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals

class Exp0001TrackBReplicationTest {

    @Test
    fun `canonical Track B sequence reproduces the committed semantic snapshot`() {
        val fixture = loadFixture("/exp-0001/track-b-workflow-input.properties")
        val expected = resourceText("/exp-0001/track-b-workflow-expected.snapshot")
        val workflow = InventoryWorkflow()
        var current = fixture.initialSnapshot

        val results = fixture.commands.map { command ->
            workflow.execute(current, command).also { result ->
                if (result is AcceptedInventoryTransition) {
                    current = result.resultingSnapshot
                }
            }
        }

        assertEquals(
            expected.trimEnd(),
            snapshotOf(fixture.initialSnapshot, results, current).trimEnd()
        )
    }

    private fun loadFixture(resource: String): WorkflowFixture {
        val properties = Properties().apply {
            Exp0001TrackBReplicationTest::class.java
                .getResourceAsStream(resource)
                .use { stream ->
                    requireNotNull(stream) { "Missing Track B fixture: $resource" }
                    load(stream)
                }
        }
        val initial = InventorySnapshot(
            sku = SkuRef(Identifier(properties.required("initial.sku"))),
            availableUnits = properties.required("initial.availableUnits").toInt(),
            effectiveAt = Timestamp.parse(properties.required("initial.effectiveAt"))
        )
        val commands = (1..properties.required("command.count").toInt()).map { index ->
            InventoryCommand(
                sku = SkuRef(Identifier(properties.required("command.$index.sku"))),
                type = InventoryCommandType.valueOf(
                    properties.required("command.$index.type")
                ),
                quantity = properties.required("command.$index.quantity").toInt(),
                effectiveAt = Timestamp.parse(
                    properties.required("command.$index.effectiveAt")
                )
            )
        }

        return WorkflowFixture(initialSnapshot = initial, commands = commands)
    }

    private fun snapshotOf(
        initial: InventorySnapshot,
        results: List<InventoryTransitionResult>,
        final: InventorySnapshot
    ): String = buildList {
        add("initial.sku=${initial.sku.id}")
        add("initial.availableUnits=${initial.availableUnits}")
        add("initial.effectiveAt=${initial.effectiveAt}")

        results.forEachIndexed { index, result ->
            val prefix = "transition.${index + 1}"
            add("$prefix.command.sku=${result.command.sku.id}")
            add("$prefix.command.type=${result.command.type}")
            add("$prefix.command.quantity=${result.command.quantity}")
            add("$prefix.command.effectiveAt=${result.command.effectiveAt}")

            when (result) {
                is AcceptedInventoryTransition -> {
                    add("$prefix.status=ACCEPTED")
                    add("$prefix.occurrence.type=${result.occurrence.type}")
                    add("$prefix.result.availableUnits=${result.resultingSnapshot.availableUnits}")
                    add("$prefix.result.effectiveAt=${result.resultingSnapshot.effectiveAt}")
                }

                is RejectedInventoryTransition -> {
                    add("$prefix.status=REJECTED")
                    add("$prefix.reason=${result.reason}")
                    add("$prefix.preserved.availableUnits=${result.inputSnapshot.availableUnits}")
                    add("$prefix.preserved.effectiveAt=${result.inputSnapshot.effectiveAt}")
                }
            }

            add("$prefix.trace=${result.trace.joinToString(",") { it.rule.name }}")
        }

        add("final.sku=${final.sku.id}")
        add("final.availableUnits=${final.availableUnits}")
        add("final.effectiveAt=${final.effectiveAt}")
    }.joinToString("\n")

    private fun resourceText(resource: String): String =
        requireNotNull(Exp0001TrackBReplicationTest::class.java.getResource(resource)) {
            "Missing Track B expected snapshot: $resource"
        }.readText().replace("\r\n", "\n")

    private fun Properties.required(name: String): String =
        requireNotNull(getProperty(name)) { "Missing fixture property: $name" }

    private data class WorkflowFixture(
        val initialSnapshot: InventorySnapshot,
        val commands: List<InventoryCommand>
    )
}
