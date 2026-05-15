package dev.happs.aigitassistant.ai.client

import dev.happs.aigitassistant.prompt.AssistantRequestKind

/**
 * Result returned by [AiClient] implementations.
 */
data class AiResponse(
    val generatedText: String,
    val kind: AssistantRequestKind,
    val source: AiResponseSource,
)

/**
 * Response source marker for diagnostics and UI metadata.
 */
enum class AiResponseSource {
    DETERMINISTIC,
}
