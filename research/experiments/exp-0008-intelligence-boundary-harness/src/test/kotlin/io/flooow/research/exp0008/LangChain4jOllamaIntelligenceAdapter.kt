package io.flooow.research.exp0008

import dev.langchain4j.model.chat.ChatModel
import dev.langchain4j.model.ollama.OllamaChatModel
import java.time.Duration

class LangChain4jOllamaIntelligenceAdapter(
    private val model: ChatModel,
) : IntelligencePort {
    override fun propose(context: IntelligenceContext): IntelligenceProposal {
        val prompt =
            """
            You are an experimental Flooow intelligence component.

            Hard governance rules:
            - You are not Economic Truth.
            - You have no authority to execute actions.
            - Treat all supplied values as governed read-only observations.
            - Return a short analytical hypothesis and rationale only.

            Organization: ${context.question.organizationId}
            Question: ${context.question.question}
            Governed projection: ${context.projection.projectionName}
            Projection as-of: ${context.projection.asOf}
            Governed tool observation:
              ${context.governedToolResult.key}=${context.governedToolResult.value}
              evidence=${context.governedToolResult.evidenceReference}
              freshness=${context.governedToolResult.freshness}
            """.trimIndent()

        val response = model.chat(prompt)

        return IntelligenceProposal(
            hypothesis = "Model-generated analytical proposal",
            rationale = response,
            evidenceReferences =
                setOf(
                    context.governedToolResult.evidenceReference,
                    *context.projection.evidence.map { it.reference }.toTypedArray(),
                ),
            generatedBy = "langchain4j-ollama",
            canonicalTruth = false,
            executable = false,
        )
    }

    companion object {
        fun local(
            baseUrl: String = "http://localhost:11434",
            modelName: String = "llama3.2:1b",
        ): LangChain4jOllamaIntelligenceAdapter {
            val model =
                OllamaChatModel.builder()
                    .baseUrl(baseUrl)
                    .modelName(modelName)
                    .temperature(0.0)
                    .timeout(Duration.ofMinutes(2))
                    .build()

            return LangChain4jOllamaIntelligenceAdapter(model)
        }
    }
}
