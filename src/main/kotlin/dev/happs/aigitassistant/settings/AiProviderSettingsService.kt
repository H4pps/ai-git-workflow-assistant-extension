package dev.happs.aigitassistant.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * Application-level settings service for AI provider selection and OpenAI-compatible connection details.
 */
@State(
    name = "dev.happs.aigitassistant.settings.AiProviderSettingsService",
    storages = [Storage("AiGitWorkflowAssistant.xml")],
)
class AiProviderSettingsService(
    private val credentialStore: AiCredentialStore = PasswordSafeAiCredentialStore(),
) : PersistentStateComponent<AiProviderSettingsState> {
    private var state = AiProviderSettingsState()

    override fun getState(): AiProviderSettingsState = state

    override fun loadState(state: AiProviderSettingsState) {
        this.state = normalizeState(state)
    }

    /**
     * Returns a normalized immutable settings snapshot.
     */
    fun settings(): AiProviderSettings =
        AiProviderSettings(
            provider = AiProviderType.fromStorage(state.provider),
            openAiBaseUrl = normalizeBaseUrl(state.openAiBaseUrl),
            openAiModel = normalizeModel(state.openAiModel),
        )

    /**
     * Replaces provider settings with [newSettings].
     */
    fun updateSettings(newSettings: AiProviderSettings) {
        state =
            AiProviderSettingsState(
                provider = newSettings.provider.name,
                openAiBaseUrl = normalizeBaseUrl(newSettings.openAiBaseUrl),
                openAiModel = normalizeModel(newSettings.openAiModel),
            )
    }

    /**
     * Reads the configured OpenAI-compatible API key from the credential backend.
     */
    fun apiKeyOrNull(): String? = credentialStore.readApiKey()

    /**
     * Writes the OpenAI-compatible API key to the credential backend.
     */
    fun storeApiKey(apiKey: String?) {
        credentialStore.writeApiKey(
            apiKey
                ?.trim()
                ?.takeIf { it.isNotEmpty() },
        )
    }

    private fun normalizeState(state: AiProviderSettingsState): AiProviderSettingsState =
        AiProviderSettingsState(
            provider = AiProviderType.fromStorage(state.provider).name,
            openAiBaseUrl = normalizeBaseUrl(state.openAiBaseUrl),
            openAiModel = normalizeModel(state.openAiModel),
        )

    private fun normalizeBaseUrl(value: String?): String =
        value
            ?.trim()
            ?.trimEnd('/')
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_OPENAI_BASE_URL

    private fun normalizeModel(value: String?): String =
        value
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_OPENAI_MODEL

    companion object {
        const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_OPENAI_MODEL = "gpt-4o-mini"

        /**
         * Returns the application-level settings service instance.
         */
        fun getInstance(): AiProviderSettingsService =
            ApplicationManager
                .getApplication()
                .getService(AiProviderSettingsService::class.java)
    }
}
