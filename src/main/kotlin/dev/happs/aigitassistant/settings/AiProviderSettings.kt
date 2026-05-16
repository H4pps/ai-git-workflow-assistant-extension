package dev.happs.aigitassistant.settings

/**
 * Immutable settings snapshot used by AI provider wiring.
 */
data class AiProviderSettings(
    val provider: AiProviderType = AiProviderType.DETERMINISTIC,
    val openAiBaseUrl: String = AiProviderSettingsService.DEFAULT_OPENAI_BASE_URL,
    val openAiModel: String = AiProviderSettingsService.DEFAULT_OPENAI_MODEL,
)

/**
 * Persisted settings state saved by IntelliJ's settings storage.
 */
data class AiProviderSettingsState(
    var provider: String = AiProviderType.DETERMINISTIC.name,
    var openAiBaseUrl: String = AiProviderSettingsService.DEFAULT_OPENAI_BASE_URL,
    var openAiModel: String = AiProviderSettingsService.DEFAULT_OPENAI_MODEL,
)
