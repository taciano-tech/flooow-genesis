package io.flooow.research.exp0009

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.ollama.OllamaChatModel
import java.time.Duration

class OllamaClaimDraftAdapter(
    private val model: ChatModel,
    private val mapper: ObjectMapper = ObjectMapper(),
) : ClaimDraftPort {
    override fun draft(
        question: String,
        catalog: EvidenceCatalog,
    ): List<DraftClaim> {
        val evidenceRefs = catalog.references().sorted().joinToString(", ")

        val prompt =
            """
            You are an experimental Flooow claim drafter.

            Hard rules:
            - Return JSON only. No markdown.
            - Never invent an evidence reference.
            - OBSERVATION means a directly supplied governed fact.
            - INFERENCE means reasoning linked to supplied evidence.
            - HYPOTHESIS means something to investigate, not a fact.
            - ASSUMPTION means an explicit assumption, not evidence.
            - If a concept is not supplied as evidence, do not label it OBSERVATION.
            - Every OBSERVATION must use predicate EQUALS.

            Available evidence references: $evidenceRefs

            Question: $question

            Return this exact JSON shape:
            {
              "claims": [
                {
                  "kind": "OBSERVATION|INFERENCE|HYPOTHESIS|ASSUMPTION",
                  "factKey": "snake_case_key",
                  "predicate": "EQUALS",
                  "value": "string value",
                  "evidenceReferences": ["known-reference"],
                  "confidence": 0.0,
                  "narrative": "short statement"
                }
              ]
            }
            """.trimIndent()

        val raw = model.chat(prompt)
        val root = mapper.readTree(extractJson(raw))
        val claimsNode = root.path("claims")
        require(claimsNode.isArray) { "model response must contain a claims array" }

        return claimsNode.map(::parseClaim)
    }

    private fun parseClaim(node: JsonNode): DraftClaim =
        DraftClaim(
            kind = ClaimKind.valueOf(node.requiredText("kind")),
            factKey = node.requiredText("factKey"),
            predicate = node.requiredText("predicate"),
            value = node.requiredText("value"),
            evidenceReferences =
                node.path("evidenceReferences")
                    .takeIf { it.isArray }
                    ?.map { it.asText() }
                    ?.toSet()
                    ?: emptySet(),
            confidence = node.path("confidence").asDouble(0.0),
            narrative = node.requiredText("narrative"),
        )

    private fun JsonNode.requiredText(field: String): String {
        val node = path(field)
        require(node.isTextual && node.asText().isNotBlank()) {
            "model claim field '$field' must be non-blank text"
        }
        return node.asText()
    }

    private fun extractJson(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return trimmed
        }

        val start = trimmed.indexOf('{')
        val end = trimmed.lastIndexOf('}')
        require(start >= 0 && end > start) {
            "model response did not contain a JSON object"
        }
        return trimmed.substring(start, end + 1)
    }

    companion object {
        fun local(
            baseUrl: String = "http://localhost:11434",
            modelName: String = "llama3.2:1b",
        ): OllamaClaimDraftAdapter {
            val model =
                OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .temperature(0.0)
                    .timeout(Duration.ofMinutes(2))
                    .build()

            return OllamaClaimDraftAdapter(model)
        }
    }
}
