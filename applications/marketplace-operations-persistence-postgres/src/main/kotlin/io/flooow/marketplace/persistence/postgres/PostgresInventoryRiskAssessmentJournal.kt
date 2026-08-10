package io.flooow.marketplace.persistence.postgres

import io.flooow.marketplace.operations.inventory.InterventionAlternative
import io.flooow.marketplace.operations.inventory.InterventionType
import io.flooow.marketplace.operations.inventory.InventoryProjection
import io.flooow.marketplace.operations.inventory.InventoryRiskAssessmentDigests
import io.flooow.marketplace.operations.inventory.InventoryRiskAssessmentJournal
import io.flooow.marketplace.operations.inventory.InventoryRiskInput
import io.flooow.marketplace.operations.inventory.PersistenceIntegrityException
import io.flooow.marketplace.operations.inventory.PersistenceUnavailableException
import io.flooow.marketplace.operations.inventory.RecordedInventoryRiskAssessment
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.TextColumnType
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.java.javaUUID
import org.jetbrains.exposed.v1.javatime.date
import org.jetbrains.exposed.v1.javatime.timestampWithTimeZone
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.exceptions.ExposedSQLException
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.util.UUID
import java.time.ZoneOffset

data class PostgresConfiguration(
    val url: String,
    val user: String,
    val password: String
) {
    init {
        require(url.isNotBlank()) { "DATABASE_URL must not be blank" }
        require(url.startsWith("jdbc:postgresql:")) {
            "DATABASE_URL must be a PostgreSQL JDBC URL"
        }
        require(user.isNotBlank()) { "DATABASE_USER must not be blank" }
        require(password.isNotBlank()) { "DATABASE_PASSWORD must not be blank" }
    }

    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()) =
            PostgresConfiguration(
                url = environment["DATABASE_URL"].orEmpty(),
                user = environment["DATABASE_USER"].orEmpty(),
                password = environment["DATABASE_PASSWORD"].orEmpty()
            )
    }
}

class PostgresInventoryRiskAssessmentJournal private constructor(
    private val database: Database
) : InventoryRiskAssessmentJournal {

    override fun append(record: RecordedInventoryRiskAssessment) {
        databaseOperation {
            transaction(database) {
                AssessmentJournalTable.insert {
                    it[assessmentId] = UUID.fromString(record.assessmentId)
                    it[schemaVersion] = record.schemaVersion.toShort()
                    it[recordedAt] = record.recordedAt.atOffset(ZoneOffset.UTC)
                    it[sku] = record.input.sku
                    it[periodEnd] = record.input.periodEnd
                    it[targetUnits] = record.input.targetUnits
                    it[unitsSold] = record.input.unitsSold
                    it[availableUnits] = record.input.availableUnits
                    it[dailySalesVelocity] = record.input.dailySalesVelocity
                    it[observedOn] = record.input.observedOn
                    it[expectedReplenishmentOn] = record.input.expectedReplenishmentOn
                    it[stockCoverageDays] = record.projection.stockCoverageDays
                    it[projectedStockoutOn] = record.projection.projectedStockoutOn
                    it[projectedStockoutDays] = record.projection.projectedStockoutDays
                    it[unitsPotentiallyUnavailable] = record.projection.unitsPotentiallyUnavailable
                    it[unitsRemainingToGoal] = record.projection.unitsRemainingToGoal
                    it[unitsAtRiskAgainstGoal] = record.projection.unitsAtRiskAgainstGoal
                    it[shortageProjected] = record.projection.shortageProjected
                    it[recommendationType] = record.recommendation.type.name
                    it[recommendationExplanation] = record.recommendation.explanation
                    it[expectedUnitsPreserved] = record.recommendation.expectedUnitsPreserved
                    it[expectedImpact] = record.expectedImpact
                    it[trace] = Json.encodeToString(
                        JsonArray.serializer(),
                        JsonArray(record.trace.map(::JsonPrimitive))
                    )
                    it[requestDigest] = record.requestDigest
                    it[resultDigest] = record.resultDigest
                }
            }
        }
    }

    override fun findById(assessmentId: String): RecordedInventoryRiskAssessment? = try {
            transaction(database) {
                AssessmentJournalTable.selectAll()
                    .where { AssessmentJournalTable.assessmentId eq UUID.fromString(assessmentId) }
                    .singleOrNull()
                    ?.toRecord()
                    ?.verifyIntegrity()
            }
    } catch (error: PersistenceIntegrityException) {
        throw error
    } catch (error: ExposedSQLException) {
        throw PersistenceUnavailableException(error)
    } catch (error: Exception) {
        throw PersistenceIntegrityException(error)
        }

    private fun <T> databaseOperation(block: () -> T): T = try {
        block()
    } catch (error: PersistenceIntegrityException) {
        throw error
    } catch (error: Exception) {
        throw PersistenceUnavailableException(error)
    }

    companion object {
        fun connect(configuration: PostgresConfiguration): PostgresInventoryRiskAssessmentJournal {
            try {
                Flyway.configure()
                    .dataSource(configuration.url, configuration.user, configuration.password)
                    .load()
                    .migrate()
                val database = Database.connect(
                    url = configuration.url,
                    driver = "org.postgresql.Driver",
                    user = configuration.user,
                    password = configuration.password
                )
                return PostgresInventoryRiskAssessmentJournal(database)
            } catch (error: Exception) {
                throw PersistenceUnavailableException(error)
            }
        }
    }
}

private object AssessmentJournalTable : Table("inventory_risk_assessment_journal") {
    val assessmentId = javaUUID("assessment_id")
    val schemaVersion = short("schema_version")
    val recordedAt = timestampWithTimeZone("recorded_at")
    val sku = text("sku")
    val periodEnd = date("period_end")
    val targetUnits = integer("target_units")
    val unitsSold = integer("units_sold")
    val availableUnits = integer("available_units")
    val dailySalesVelocity = integer("daily_sales_velocity")
    val observedOn = date("observed_on")
    val expectedReplenishmentOn = date("expected_replenishment_on")
    val stockCoverageDays = integer("stock_coverage_days")
    val projectedStockoutOn = date("projected_stockout_on")
    val projectedStockoutDays = integer("projected_stockout_days")
    val unitsPotentiallyUnavailable = integer("units_potentially_unavailable")
    val unitsRemainingToGoal = integer("units_remaining_to_goal")
    val unitsAtRiskAgainstGoal = integer("units_at_risk_against_goal")
    val shortageProjected = bool("shortage_projected")
    val recommendationType = text("recommendation_type")
    val recommendationExplanation = text("recommendation_explanation")
    val expectedUnitsPreserved = integer("expected_units_preserved")
    val expectedImpact = text("expected_impact")
    val trace: Column<String> = registerColumn("trace", JsonbTextColumnType())
    val requestDigest = char("request_digest", 64)
    val resultDigest = char("result_digest", 64)
}

private class JsonbTextColumnType : TextColumnType() {
    override fun sqlType(): String = "JSONB"

    override fun parameterMarker(value: String?): String = "?::jsonb"
}

private fun ResultRow.toRecord(): RecordedInventoryRiskAssessment =
    RecordedInventoryRiskAssessment(
        assessmentId = this[AssessmentJournalTable.assessmentId].toString(),
        schemaVersion = this[AssessmentJournalTable.schemaVersion].toInt(),
        recordedAt = this[AssessmentJournalTable.recordedAt].toInstant(),
        input = InventoryRiskInput(
            sku = this[AssessmentJournalTable.sku],
            periodEnd = this[AssessmentJournalTable.periodEnd],
            targetUnits = this[AssessmentJournalTable.targetUnits],
            unitsSold = this[AssessmentJournalTable.unitsSold],
            availableUnits = this[AssessmentJournalTable.availableUnits],
            dailySalesVelocity = this[AssessmentJournalTable.dailySalesVelocity],
            observedOn = this[AssessmentJournalTable.observedOn],
            expectedReplenishmentOn = this[AssessmentJournalTable.expectedReplenishmentOn]
        ),
        projection = InventoryProjection(
            stockCoverageDays = this[AssessmentJournalTable.stockCoverageDays],
            projectedStockoutOn = this[AssessmentJournalTable.projectedStockoutOn],
            expectedReplenishmentOn = this[AssessmentJournalTable.expectedReplenishmentOn],
            projectedStockoutDays = this[AssessmentJournalTable.projectedStockoutDays],
            unitsPotentiallyUnavailable = this[AssessmentJournalTable.unitsPotentiallyUnavailable],
            unitsRemainingToGoal = this[AssessmentJournalTable.unitsRemainingToGoal],
            unitsAtRiskAgainstGoal = this[AssessmentJournalTable.unitsAtRiskAgainstGoal]
        ),
        recommendation = InterventionAlternative(
            type = InterventionType.valueOf(this[AssessmentJournalTable.recommendationType]),
            explanation = this[AssessmentJournalTable.recommendationExplanation],
            expectedUnitsPreserved = this[AssessmentJournalTable.expectedUnitsPreserved]
        ),
        expectedImpact = this[AssessmentJournalTable.expectedImpact],
        trace = Json.decodeFromString<JsonArray>(this[AssessmentJournalTable.trace])
            .map { it.jsonPrimitive.content },
        requestDigest = this[AssessmentJournalTable.requestDigest],
        resultDigest = this[AssessmentJournalTable.resultDigest]
    )

private fun RecordedInventoryRiskAssessment.verifyIntegrity(): RecordedInventoryRiskAssessment {
    if (
        requestDigest != InventoryRiskAssessmentDigests.request(input) ||
        resultDigest != InventoryRiskAssessmentDigests.result(this)
    ) {
        throw PersistenceIntegrityException()
    }
    return this
}
