package io.flooow.marketplace.api

import java.security.MessageDigest

internal const val LOCAL_SERVICE_TOKEN =
    "flooow-local-only-service-token-change-before-sharing"
private const val MINIMUM_SERVICE_TOKEN_LENGTH = 43

internal class ServiceToken private constructor(private val bytes: ByteArray) {
    fun matches(candidate: String): Boolean = MessageDigest.isEqual(
        bytes,
        candidate.toByteArray(Charsets.UTF_8)
    )

    companion object {
        fun fromEnvironment(environment: Map<String, String> = System.getenv()): ServiceToken {
            val value = requireNotNull(environment["FLOOOW_SERVICE_TOKEN"]) {
                "FLOOOW_SERVICE_TOKEN is required"
            }
            require(value.length >= MINIMUM_SERVICE_TOKEN_LENGTH) {
                "FLOOOW_SERVICE_TOKEN must contain at least 43 characters"
            }
            require(value == value.trim()) {
                "FLOOOW_SERVICE_TOKEN must not contain surrounding whitespace"
            }
            require(value.none { it.code in 0..31 || it.code == 127 }) {
                "FLOOOW_SERVICE_TOKEN must not contain ASCII control characters"
            }
            require(
                value != LOCAL_SERVICE_TOKEN || environment["FLOOOW_ENVIRONMENT"] == "local"
            ) {
                "FLOOOW_SERVICE_TOKEN must not use the local-development placeholder"
            }
            return ServiceToken(value.toByteArray(Charsets.UTF_8))
        }

        fun test(value: String): ServiceToken = ServiceToken(value.toByteArray(Charsets.UTF_8))
    }
}
