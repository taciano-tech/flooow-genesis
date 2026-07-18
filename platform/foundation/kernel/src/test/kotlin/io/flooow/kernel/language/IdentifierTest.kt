package io.flooow.kernel.language

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IdentifierTest {

    @Test
    fun `accepts a valid identifier`() {
        val identifier = Identifier("observation-001")

        assertEquals("observation-001", identifier.value)
        assertEquals("observation-001", identifier.toString())
    }

    @Test
    fun `rejects a blank identifier`() {
        assertFailsWith<IllegalArgumentException> {
            Identifier("   ")
        }
    }

    @Test
    fun `rejects surrounding whitespace`() {
        assertFailsWith<IllegalArgumentException> {
            Identifier(" observation-001 ")
        }
    }

    @Test
    fun `rejects identifiers beyond the maximum length`() {
        assertFailsWith<IllegalArgumentException> {
            Identifier("a".repeat(Identifier.MAX_LENGTH + 1))
        }
    }
}
