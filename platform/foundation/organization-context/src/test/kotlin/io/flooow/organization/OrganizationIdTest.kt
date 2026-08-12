package io.flooow.organization

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrganizationIdTest {
    @Test
    fun `canonical uuid round trips`() {
        val value = "11111111-1111-4111-8111-111111111111"
        assertEquals(value, OrganizationId.parse(value).toString())
    }

    @Test
    fun `noncanonical organization text is rejected`() {
        listOf("", "not-a-uuid", "11111111-1111-4111-8111-11111111111A").forEach {
            assertFailsWith<IllegalArgumentException> { OrganizationId.parse(it) }
        }
    }
}
