package dev.happs.aigitassistant.settings

import com.intellij.ui.HyperlinkLabel
import java.awt.Component
import java.awt.Container
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiProviderSettingsConfigurableTest {
    @Test
    fun `settings links include openai keys and openrouter free models`() {
        val component = configurable().createComponent()

        val linkTexts = hyperlinkLabels(component).map { it.text }

        assertContains(linkTexts, "OpenAI API keys")
        assertContains(linkTexts, "OpenRouter free models")
    }

    @Test
    fun `reset renders openai settings and keeps fields enabled`() {
        val configurable = configurable()

        configurable.createComponent()
        configurable.reset()

        assertFalse(configurable.isModified())
        assertTrue(configurable.openAiFieldsEnabledForTesting())
    }

    @Test
    fun `apply stores openai settings and api key`() {
        val credentialStore = InMemoryCredentialStore()
        val service = AiProviderSettingsService(credentialStore = credentialStore)
        val configurable = configurable(service)
        configurable.createComponent()

        configurable.setOpenAiFieldsForTesting(
            baseUrl = " https://llm.example.com/v1/ ",
            model = " custom-model ",
            apiKey = " sk-configurable ",
        )

        assertTrue(configurable.isModified())
        assertTrue(configurable.openAiFieldsEnabledForTesting())

        configurable.apply()

        assertEquals("https://llm.example.com/v1", service.settings().openAiBaseUrl)
        assertEquals("custom-model", service.settings().openAiModel)
        assertEquals("sk-configurable", credentialStore.savedApiKey)
        assertFalse(configurable.isModified())
    }

    @Test
    fun `reset restores stored openai settings`() {
        val credentialStore = InMemoryCredentialStore()
        val service = AiProviderSettingsService(credentialStore = credentialStore)
        service.updateSettings(
            AiProviderSettings(
                openAiBaseUrl = "https://configured.example.com/v1",
                openAiModel = "configured-model",
            ),
        )
        service.storeApiKey("configured-key")
        val configurable = configurable(service)
        configurable.createComponent()

        configurable.setOpenAiFieldsForTesting(
            baseUrl = "https://changed.example.com/v1",
            model = "changed-model",
            apiKey = "changed-key",
        )
        assertTrue(configurable.isModified())

        configurable.reset()

        assertTrue(configurable.openAiFieldsEnabledForTesting())
        assertFalse(configurable.isModified())
    }

    private fun configurable(
        service: AiProviderSettingsService = AiProviderSettingsService(credentialStore = InMemoryCredentialStore()),
    ): AiProviderSettingsConfigurable =
        AiProviderSettingsConfigurable(
            settingsServiceProvider = { service },
        )

    private fun hyperlinkLabels(component: Component): List<HyperlinkLabel> =
        when (component) {
            is HyperlinkLabel -> listOf(component)
            is Container -> component.components.flatMap(::hyperlinkLabels)
            else -> emptyList()
        }

    private class InMemoryCredentialStore : AiCredentialStore {
        var savedApiKey: String? = null

        override fun readApiKey(): String? = savedApiKey

        override fun writeApiKey(apiKey: String?) {
            savedApiKey = apiKey
        }
    }
}
