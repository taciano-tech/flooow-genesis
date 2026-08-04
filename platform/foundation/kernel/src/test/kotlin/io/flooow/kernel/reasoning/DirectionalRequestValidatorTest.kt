package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Confidence
import io.flooow.kernel.language.Identifier
import io.flooow.kernel.language.Timestamp
import io.flooow.kernel.model.Evidence
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DirectionalRequestValidatorTest {
    private val validator = DirectionalRequestValidator()
    private val recordedAt = Timestamp.parse("2026-08-04T12:00:00Z")
    private val hypothesis = hypothesis("hypothesis-1")
    private val evidence1 = evidence("evidence-1", 0.9)
    private val evidence2 = evidence("evidence-2", 0.2)
    private val evidenceSet = EvidenceSet(setOf(evidence1, evidence2))

    @Test
    fun `rejects every specified invalid request`() {
        assertInvalid(emptyList(), DirectionalValidationError.NO_RELATIONSHIPS)
        assertInvalid(
            listOf(
                relationship("r-1", "evidence-1"),
                relationship("r-1", "evidence-2")
            ),
            DirectionalValidationError.DUPLICATE_RELATIONSHIP_ID
        )
        assertInvalid(
            listOf(relationship("r-1", "absent")),
            DirectionalValidationError.EVIDENCE_NOT_FOUND
        )
        assertInvalid(
            listOf(relationship("r-1", "evidence-1", hypothesisId = "other")),
            DirectionalValidationError.HYPOTHESIS_MISMATCH
        )
        assertInvalid(
            listOf(
                relationship("r-1", "evidence-1"),
                relationship(
                    "r-2",
                    "evidence-1",
                    RelationshipDirection.CONTRADICTS
                )
            ),
            DirectionalValidationError.CONTRADICTORY_DUPLICATE_RELATIONSHIP
        )
        assertInvalid(
            listOf(
                relationship("r-1", "evidence-1"),
                relationship("r-2", "evidence-1")
            ),
            DirectionalValidationError.IDENTICAL_DUPLICATE_RELATIONSHIP
        )
    }

    @Test
    fun `uses deterministic validation precedence without repairing input`() {
        val duplicateIdAndAbsentEvidence = listOf(
            relationship("r-1", "absent-1"),
            relationship("r-1", "absent-2")
        )
        assertInvalid(
            duplicateIdAndAbsentEvidence,
            DirectionalValidationError.DUPLICATE_RELATIONSHIP_ID
        )

        val absentEvidenceAndMismatch = listOf(
            relationship("r-1", "absent", hypothesisId = "other")
        )
        assertInvalid(
            absentEvidenceAndMismatch,
            DirectionalValidationError.EVIDENCE_NOT_FOUND
        )

        val mismatchAndDuplicatePair = listOf(
            relationship("r-1", "evidence-1", hypothesisId = "other"),
            relationship("r-2", "evidence-2", hypothesisId = "other")
        )
        assertInvalid(
            mismatchAndDuplicatePair,
            DirectionalValidationError.HYPOTHESIS_MISMATCH
        )

        val contradictoryAndIdenticalPairs = listOf(
            relationship("r-1", "evidence-1"),
            relationship(
                "r-2",
                "evidence-1",
                RelationshipDirection.CONTRADICTS
            ),
            relationship("r-3", "evidence-2"),
            relationship("r-4", "evidence-2")
        )
        assertInvalid(
            contradictoryAndIdenticalPairs,
            DirectionalValidationError.CONTRADICTORY_DUPLICATE_RELATIONSHIP
        )
    }

    @Test
    fun `copies canonical confidence and orders relationships by id`() {
        val validation = validator.validate(
            DirectionalEvaluationRequest(
                hypothesis,
                evidenceSet,
                listOf(
                    relationship("r-2", "evidence-2"),
                    relationship("r-1", "evidence-1")
                )
            )
        )

        val valid = assertIs<DirectionalRequestValidation.Valid>(validation)
        assertEquals(
            listOf(Identifier("r-1"), Identifier("r-2")),
            valid.request.relationships.map { it.relationship.id }
        )
        assertEquals(
            listOf(Confidence(0.9), Confidence(0.2)),
            valid.request.relationships.map { it.confidence }
        )
    }

    @Test
    fun `keeps relationship direction contextual to each hypothesis`() {
        val canonicalEvidenceBefore = evidence1.copy()
        val secondHypothesis = hypothesis("hypothesis-2")
        val supports = validateSingle(
            hypothesis,
            relationship("r-1", "evidence-1", hypothesisId = "hypothesis-1")
        )
        val contradicts = validateSingle(
            secondHypothesis,
            relationship(
                "r-2",
                "evidence-1",
                RelationshipDirection.CONTRADICTS,
                "hypothesis-2"
            )
        )

        assertEquals(
            RelationshipDirection.SUPPORTS,
            supports.request.relationships.single().relationship.direction
        )
        assertEquals(
            RelationshipDirection.CONTRADICTS,
            contradicts.request.relationships.single().relationship.direction
        )
        assertEquals(canonicalEvidenceBefore, evidence1)

        val noRelationships = validator.validate(
            DirectionalEvaluationRequest(hypothesis, evidenceSet, emptyList())
        )
        assertEquals(
            DirectionalRequestValidation.Invalid(
                DirectionalValidationError.NO_RELATIONSHIPS
            ),
            noRelationships
        )
        assertEquals(canonicalEvidenceBefore, evidence1)
    }

    private fun validateSingle(
        target: Hypothesis,
        relationship: EvidenceRelationship
    ): DirectionalRequestValidation.Valid = assertIs(
        validator.validate(
            DirectionalEvaluationRequest(target, evidenceSet, listOf(relationship))
        )
    )

    private fun assertInvalid(
        relationships: List<EvidenceRelationship>,
        expected: DirectionalValidationError
    ) {
        val result = validator.validate(
            DirectionalEvaluationRequest(hypothesis, evidenceSet, relationships)
        )
        assertEquals(
            DirectionalRequestValidation.Invalid(expected),
            result
        )
    }

    private fun relationship(
        id: String,
        evidenceId: String,
        direction: RelationshipDirection = RelationshipDirection.SUPPORTS,
        hypothesisId: String = hypothesis.id.value
    ) = EvidenceRelationship(
        Identifier(id),
        Identifier(evidenceId),
        Identifier(hypothesisId),
        direction
    )

    private fun hypothesis(id: String) = Hypothesis(
        Identifier(id),
        "A testable statement",
        Confidence(0.5),
        recordedAt
    )

    private fun evidence(id: String, confidence: Double) = Evidence(
        Identifier(id),
        setOf(Identifier("observation-$id")),
        Confidence(confidence),
        recordedAt
    )
}
