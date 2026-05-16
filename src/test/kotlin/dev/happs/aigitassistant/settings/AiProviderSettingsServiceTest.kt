package dev.happs.aigitassistant.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AiProviderSettingsServiceTest {
    @Test
    fun `defaults to deterministic provider and baseline openai values`() {
        val service = AiProviderSettingsService(credentialStore = InMemoryCredentialStore())

        assertEquals(AiProviderType.DETERMINISTIC, service.settings().provider)
        assertEquals(AiProviderSettingsService.DEFAULT_OPENAI_BASE_URL, service.settings().openAiBaseUrl)
        assertEquals(AiProviderSettingsService.DEFAULT_OPENAI_MODEL, service.settings().openAiModel)
        assertNull(service.apiKeyOrNull())
    }

    @Test
    fun `stores normalized openai settings and api key via credential backend`() {
        val credentialStore = InMemoryCredentialStore()
        val service = AiProviderSettingsService(credentialStore = credentialStore)
        service.updateSettings(
            AiProviderSettings(
                provider = AiProviderType.OPENAI_COMPATIBLE,
                openAiBaseUrl = " https://llm.example.com/v1/ ",
                openAiModel = " gpt-4o-mini ",
            ),
        )

        service.storeApiKey("  sk-example-key  ")

        assertEquals(AiProviderType.OPENAI_COMPATIBLE, service.settings().provider)
        assertEquals("https://llm.example.com/v1", service.settings().openAiBaseUrl)
        assertEquals("gpt-4o-mini", service.settings().openAiModel)
        assertEquals("sk-example-key", service.apiKeyOrNull())
        assertEquals("sk-example-key", credentialStore.savedApiKey)
    }

    @Test
    fun `load state falls back to deterministic defaults for invalid values`() {
        val service = AiProviderSettingsService(credentialStore = InMemoryCredentialStore())
        service.loadState(
            AiProviderSettingsState(
                provider = "unknown_provider",
                openAiBaseUrl = "   ",
                openAiModel = "   ",
            ),
        )

        assertEquals(AiProviderType.DETERMINISTIC, service.settings().provider)
        assertEquals(AiProviderSettingsService.DEFAULT_OPENAI_BASE_URL, service.settings().openAiBaseUrl)
        assertEquals(AiProviderSettingsService.DEFAULT_OPENAI_MODEL, service.settings().openAiModel)
    }

    private class InMemoryCredentialStore : AiCredentialStore {
        var savedApiKey: String? = null

        override fun readApiKey(): String? = savedApiKey

        override fun writeApiKey(apiKey: String?) {
            savedApiKey = apiKey
        }
    }
}
