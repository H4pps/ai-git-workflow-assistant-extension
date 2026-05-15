package dev.happs.aigitassistant.ai.client

import com.intellij.openapi.diagnostic.Logger
import dev.happs.aigitassistant.prompt.AssistantRequest

/**
 * Log severity used by [LoggingAiClient] sinks.
 */
enum class LoggingLevel {
    INFO,
    WARN,
}

/**
 * Emits structured AI lifecycle logs around another [AiClient].
 */
class LoggingAiClient(
    private val delegate: AiClient,
    private val logSink: (LoggingLevel, String, Map<String, Any?>) -> Unit = defaultLogSink(),
) : AiClient {
    /**
     * Generates a response while logging safe request and result metadata.
     */
    override fun generate(request: AssistantRequest): AiResponse {
        logSink(LoggingLevel.INFO, "ai_request_started", request.safeMetadata.toMap())
        val result = runCatching { delegate.generate(request) }
        val response = result.getOrNull()
        if (response != null) {
            logSink(
                LoggingLevel.INFO,
                "ai_request_succeeded",
                request.safeMetadata.toMap() +
                    mapOf(
                        "response_char_count" to response.generatedText.length,
                        "response_source" to response.source.name,
                    ),
            )
            return response
        }
        logFailure(request, requireNotNull(result.exceptionOrNull()))
        throw requireNotNull(result.exceptionOrNull())
    }

    /**
     * Logs a failed generation attempt with safe metadata.
     */
    private fun logFailure(
        request: AssistantRequest,
        exception: Throwable,
    ) {
        logSink(
            LoggingLevel.WARN,
            "ai_request_failed",
            request.safeMetadata.toMap() +
                mapOf("error_type" to exception::class.simpleName),
        )
    }

    private companion object {
        /**
         * Creates a sink backed by IntelliJ's logger.
         */
        fun defaultLogSink(): (LoggingLevel, String, Map<String, Any?>) -> Unit {
            val logger = Logger.getInstance(LoggingAiClient::class.java)
            return { level, event, metadata ->
                val message = structuredEvent(event, metadata)
                when (level) {
                    LoggingLevel.INFO -> logger.info(message)
                    LoggingLevel.WARN -> logger.warn(message)
                }
            }
        }

        /**
         * Formats [metadata] as stable key-value pairs.
         */
        fun structuredEvent(
            event: String,
            metadata: Map<String, Any?>,
        ): String {
            val metadataBody =
                metadata.entries.joinToString(separator = " ") { (key, value) ->
                    "$key=${value ?: "null"}"
                }
            return "event=$event $metadataBody"
        }
    }
}
