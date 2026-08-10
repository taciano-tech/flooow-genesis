package io.flooow.marketplace.operations.inventory

import java.security.MessageDigest
import java.time.Clock
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID

data class RecordedInventoryRiskAssessment(
    val assessmentId: String,
    val schemaVersion: Int,
    val recordedAt: Instant,
    val input: InventoryRiskInput,
    val projection: InventoryProjection,
    val recommendation: InterventionAlternative,
    val expectedImpact: String,
    val trace: List<String>,
    val requestDigest: String,
    val resultDigest: String
)

interface InventoryRiskAssessmentJournal {
    fun append(record: RecordedInventoryRiskAssessment)

    fun findById(assessmentId: String): RecordedInventoryRiskAssessment?
}

fun interface AssessmentIdentifierFactory {
    fun create(): String
}

class UuidAssessmentIdentifierFactory : AssessmentIdentifierFactory {
    override fun create(): String = UUID.randomUUID().toString()
}

class InventoryRiskAssessmentRecorder(
    private val journal: InventoryRiskAssessmentJournal,
    private val evaluator: InventoryRiskEvaluator = InventoryRiskEvaluator(),
    private val identifierFactory: AssessmentIdentifierFactory = UuidAssessmentIdentifierFactory(),
    private val clock: Clock = Clock.systemUTC()
) {
    fun record(input: InventoryRiskInput): RecordedInventoryRiskAssessment {
        val assessment = evaluator.evaluate(input)
        val record = RecordedInventoryRiskAssessment(
            assessmentId = identifierFactory.create(),
            schemaVersion = 1,
            recordedAt = clock.instant().truncatedTo(ChronoUnit.MICROS),
            input = input,
            projection = assessment.projection,
            recommendation = assessment.selectedAlternative,
            expectedImpact = assessment.expectedImpact,
            trace = assessment.trace.toList(),
            requestDigest = InventoryRiskAssessmentDigests.request(input),
            resultDigest = InventoryRiskAssessmentDigests.result(assessment)
        )
        journal.append(record)
        return record
    }

    fun findById(assessmentId: String): RecordedInventoryRiskAssessment? =
        journal.findById(assessmentId)
}

object InventoryRiskAssessmentDigests {
    fun request(input: InventoryRiskInput): String = sha256(
        buildString {
            append('{')
            property("sku", input.sku)
            property("periodEnd", input.periodEnd.toString())
            property("targetUnits", input.targetUnits)
            property("unitsSold", input.unitsSold)
            property("availableUnits", input.availableUnits)
            property("dailySalesVelocity", input.dailySalesVelocity)
            property("observedOn", input.observedOn.toString())
            property("expectedReplenishmentOn", input.expectedReplenishmentOn.toString(), last = true)
            append('}')
        }
    )

    fun result(assessment: InventoryRiskAssessment): String = result(
        input = assessment.input,
        projection = assessment.projection,
        recommendation = assessment.selectedAlternative,
        expectedImpact = assessment.expectedImpact,
        trace = assessment.trace
    )

    fun result(record: RecordedInventoryRiskAssessment): String = result(
        input = record.input,
        projection = record.projection,
        recommendation = record.recommendation,
        expectedImpact = record.expectedImpact,
        trace = record.trace
    )

    private fun result(
        input: InventoryRiskInput,
        projection: InventoryProjection,
        recommendation: InterventionAlternative,
        expectedImpact: String,
        trace: List<String>
    ): String = sha256(buildString {
        append('{')
        property("sku", input.sku)
        property("observedOn", input.observedOn.toString())
        append("\"projection\":{")
        property("stockCoverageDays", projection.stockCoverageDays)
        property("projectedStockoutOn", projection.projectedStockoutOn.toString())
        property("expectedReplenishmentOn", projection.expectedReplenishmentOn.toString())
        property("projectedStockoutDays", projection.projectedStockoutDays)
        property("unitsPotentiallyUnavailable", projection.unitsPotentiallyUnavailable)
        property("unitsRemainingToGoal", projection.unitsRemainingToGoal)
        property("unitsAtRiskAgainstGoal", projection.unitsAtRiskAgainstGoal)
        property("shortageProjected", projection.shortageProjected, last = true)
        append("},")
        append("\"recommendation\":{")
        property("type", recommendation.type.name)
        property("explanation", recommendation.explanation)
        property("expectedUnitsPreserved", recommendation.expectedUnitsPreserved, last = true)
        append("},")
        property("expectedImpact", expectedImpact)
        append("\"trace\":[")
        trace.forEachIndexed { index, value ->
            if (index > 0) append(',')
            quoted(value)
        }
        append("]}")
    })

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun StringBuilder.property(name: String, value: String, last: Boolean = false) {
        quoted(name)
        append(':')
        quoted(value)
        if (!last) append(',')
    }

    private fun StringBuilder.property(name: String, value: Int, last: Boolean = false) {
        quoted(name)
        append(':').append(value)
        if (!last) append(',')
    }

    private fun StringBuilder.property(name: String, value: Boolean, last: Boolean = false) {
        quoted(name)
        append(':').append(value)
        if (!last) append(',')
    }

    private fun StringBuilder.quoted(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u").append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }
}

open class PersistenceException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

class PersistenceUnavailableException(cause: Throwable? = null) :
    PersistenceException("Assessment persistence is unavailable", cause)

class PersistenceIntegrityException(cause: Throwable? = null) :
    PersistenceException("Persisted assessment failed integrity verification", cause)
