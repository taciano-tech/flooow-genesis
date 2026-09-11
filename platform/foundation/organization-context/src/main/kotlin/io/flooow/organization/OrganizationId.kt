package io.flooow.organization

import java.util.UUID

@JvmInline
value class OrganizationId(val value: UUID) {
    override fun toString(): String = value.toString()

    companion object {
        fun parse(value: String): OrganizationId {
            val parsed = try {
                UUID.fromString(value)
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Organization identifier must be a canonical UUID")
            }
            require(parsed.toString() == value) {
                "Organization identifier must be a canonical UUID"
            }
            return OrganizationId(parsed)
        }
    }
}
