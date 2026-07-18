package io.flooow.kernel.language

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfidenceTest {

    @Test
    fun `accepts confidence boundaries`() {
        assertEquals(0.0, Confidence.NONE.value)
        assertEquals(1.0, Confidence.CERTAIN.value)
    }

    @Test
    fun `rejects confidence below zero`() {
        assertFailsWith<IllegalArgumentException> {
            Confidence(-0.01)
        }
    }

    @Test
    fun `rejects confidence above one`() {
        assertFailsWith<IllegalArgumentException> {
            Confidence(1.01)
        }
    }

    @Test
    fun `rejects non-finite confidence`() {
        assertFailsWith<IllegalArgumentException> {
            Confidence(Double.NaN)
        }
    }

    @Test
    fun `supports confidence comparison`() {
        assertTrue(Confidence(0.8) > Confidence(0.4))
    }
}
