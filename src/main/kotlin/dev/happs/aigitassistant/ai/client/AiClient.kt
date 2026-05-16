package dev.happs.aigitassistant.ai.client

import dev.happs.aigitassistant.ai.prompt.AssistantRequest

/**
 * Service boundary for text generation from assistant requests.
 */
interface AiClient {
    /**
     * Generates a response for [request].
     */
    fun generate(request: AssistantRequest): AiResponse
}
