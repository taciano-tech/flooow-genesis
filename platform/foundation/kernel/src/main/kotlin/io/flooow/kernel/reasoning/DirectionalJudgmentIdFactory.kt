package io.flooow.kernel.reasoning

import io.flooow.kernel.language.Identifier
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.math.BigDecimal
import java.security.MessageDigest

internal class DirectionalJudgmentIdFactory {
    fun create(
        hypothesis: Hypothesis,
        policyId: String,
        relationships: List<EvaluatedRelationship>
    ): Identifier {
        val payload = ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeField(hypothesis.id.value)
                output.writeField(policyId)
                relationships.sortedBy { it.relationship.id.value }.forEach {
                    output.writeField(it.relationship.id.value)
                    output.writeField(it.relationship.evidenceId.value)
                    output.writeField(it.relationship.hypothesisId.value)
                    output.writeField(it.relationship.direction.name)
                    output.writeField(canonicalDecimal(it.confidence.value))
                }
            }
            bytes.toByteArray()
        }
        val hash = MessageDigest.getInstance("SHA-256").digest(payload)
            .joinToString("") { "%02x".format(it) }
        return Identifier("directional-judgment-$hash")
    }

    private fun DataOutputStream.writeField(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeInt(bytes.size)
        write(bytes)
    }

    private fun canonicalDecimal(value: Double): String =
        BigDecimal.valueOf(value).stripTrailingZeros().toPlainString()
}
