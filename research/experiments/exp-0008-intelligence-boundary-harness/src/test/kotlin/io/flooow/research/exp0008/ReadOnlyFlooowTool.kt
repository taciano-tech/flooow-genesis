package io.flooow.research.exp0008

data class ToolReadRequest(
    val organizationId: String,
    val key: String,
)

data class ToolReadResult(
    val key: String,
    val value: String,
    val evidenceReference: String,
    val freshness: String,
)

fun interface ReadOnlyFlooowTool {
    fun read(request: ToolReadRequest): ToolReadResult
}
