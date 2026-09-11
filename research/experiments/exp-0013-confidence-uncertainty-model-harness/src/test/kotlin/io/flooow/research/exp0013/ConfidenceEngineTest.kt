package io.flooow.research.exp0013

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ConfidenceEngineTest {
    private val engine = ConfidenceEngine()

    @Test
    fun `high quality direct evidence is high confidence`() {
        val result =
            engine.assess(
                ConfidenceInputs(
                    sourceReliability = 0.98,
                    measurementQuality = 0.97,
                    temporalQuality = 0.99,
                    derivationDepth = 0,
                    contradictionCount = 0,
                    unknownCount = 0,
                ),
            )

        assertEquals(ConfidenceDecision.HIGH_CONFIDENCE, result.decision)
        assertTrue(result.score >= 0.80)
        assertFalse(result.canonicalTruth)
        assertFalse(result.executable)
    }

    @Test
    fun `derivation depth reduces confidence`() {
        val direct =
            engine.assess(
                ConfidenceInputs(
                    sourceReliability = 0.95,
                    measurementQuality = 0.95,
                    temporalQuality = 0.95,
                    derivationDepth = 0,
                    contradictionCount = 0,
                    unknownCount = 0,
                ),
            )

        val derived =
            engine.assess(
                ConfidenceInputs(
                    sourceReliability = 0.95,
                    measurementQuality = 0.95,
                    temporalQuality = 0.95,
                    derivationDepth = 4,
                    contradictionCount = 0,
                    unknownCount = 0,
                ),
            )

        assertTrue(derived.score < direct.score)
    }

    @Test
    fun `contradiction materially reduces confidence`() {
        val clean =
            engine.assess(
                ConfidenceInputs(
                    sourceReliability = 0.90,
                    measurementQuality = 0.90,
                    temporalQuality = 0.90,
                    derivationDepth = 1,
                    contradictionCount = 0,
                    unknownCount = 0,
                ),
            )

        val conflicted =
            engine.assess(
                ConfidenceInputs(
                    sourceReliability = 0.90,
                    measurementQuality = 0.90,
                    temporalQuality = 0.90,
                    derivationDepth = 1,
                    contradictionCount = 2,
                    unknownCount = 0,
                ),
            )

        assertTrue(conflicted.score < clean.score)
        assertEquals(ConfidenceDecision.LOW_CONFIDENCE, conflicted.decision)
    }

    @Test
    fun `explicit unknowns reduce confidence`() {
        val known =
            engine.assess(
                ConfidenceInputs(
                    sourceReliability = 0.90,
                    measurementQuality = 0.90,
                    temporalQuality = 0.90,
                    derivationDepth = 0,
                    contradictionCount = 0,
                    unknownCount = 0,
                ),
            )

        val uncertain =
            engine.assess(
                ConfidenceInputs(
                    sourceReliability = 0.90,
                    measurementQuality = 0.90,
                    temporalQuality = 0.90,
                    derivationDepth = 0,
                    contradictionCount = 0,
                    unknownCount = 3,
                ),
            )

        assertTrue(uncertain.score < known.score)
    }

    @Test
    fun `missing essential quality becomes insufficient information`() {
        val result =
            engine.assess(
                ConfidenceInputs(
                    sourceReliability = 0.95,
                    measurementQuality = 0.0,
                    temporalQuality = 0.95,
                    derivationDepth = 0,
                    contradictionCount = 0,
                    unknownCount = 0,
                ),
            )

        assertEquals(
            ConfidenceDecision.INSUFFICIENT_INFORMATION,
            result.decision,
        )
    }

    @Test
    fun `assessment is deterministic`() {
        val inputs =
            ConfidenceInputs(
                sourceReliability = 0.88,
                measurementQuality = 0.91,
                temporalQuality = 0.93,
                derivationDepth = 2,
                contradictionCount = 1,
                unknownCount = 1,
            )

        val first = engine.assess(inputs)
        val second = engine.assess(inputs)

        assertEquals(first, second)
    }
}
