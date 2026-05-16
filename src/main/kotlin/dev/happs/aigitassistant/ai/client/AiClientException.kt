package dev.happs.aigitassistant.ai.client

/**
 * Sanitized AI client exception suitable for user-facing error messages.
 */
class AiClientException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
