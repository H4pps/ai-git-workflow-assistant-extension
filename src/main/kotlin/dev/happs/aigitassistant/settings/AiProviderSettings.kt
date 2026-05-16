package dev.happs.aigitassistant.settings

/**
 * Immutable settings snapshot used by AI provider wiring.
 */
data class AiProviderSettings(
    val openAiBaseUrl: String = AiProviderSettingsService.DEFAULT_OPENAI_BASE_URL,
    val openAiModel: String = AiProviderSettingsService.DEFAULT_OPENAI_MODEL,
)

/**
 * Persisted settings state saved by IntelliJ's settings storage.
 */
data class AiProviderSettingsState(
    var openAiBaseUrl: String = AiProviderSettingsService.DEFAULT_OPENAI_BASE_URL,
    var openAiModel: String = AiProviderSettingsService.DEFAULT_OPENAI_MODEL,
)
