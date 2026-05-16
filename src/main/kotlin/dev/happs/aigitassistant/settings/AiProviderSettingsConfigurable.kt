package dev.happs.aigitassistant.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * Settings configurable shown under Tools for AI provider selection and OpenAI-compatible credentials.
 */
@Suppress("TooManyFunctions")
class AiProviderSettingsConfigurable(
    private val settingsServiceProvider: () -> AiProviderSettingsService = { AiProviderSettingsService.getInstance() },
) : Configurable {
    private var panel: JPanel? = null
    private var providerField: ComboBox<ProviderOption>? = null
    private var baseUrlField: JBTextField? = null
    private var modelField: JBTextField? = null
    private var apiKeyField: JBPasswordField? = null

    override fun getDisplayName(): String = "AI Git Workflow Assistant"

    override fun createComponent(): JComponent {
        val provider = ComboBox(ProviderOption.entries.toTypedArray())
        val baseUrl = JBTextField()
        val model = JBTextField()
        val apiKey = JBPasswordField()
        provider.addActionListener { updateOpenAiFieldState() }

        providerField = provider
        baseUrlField = baseUrl
        modelField = model
        apiKeyField = apiKey
        panel =
            FormBuilder
                .createFormBuilder()
                .addLabeledComponent("Provider:", provider)
                .addLabeledComponent("Base URL:", baseUrl)
                .addLabeledComponent("Model:", model)
                .addLabeledComponent("API key:", apiKey)
                .addComponent(
                    HyperlinkLabel("OpenAI API keys").apply {
                        setHyperlinkTarget(OPENAI_API_KEYS_URL)
                    },
                ).addComponent(
                    JLabel(
                        "OpenAI-compatible mode sends Git diffs, changed paths, and task notes to the configured API.",
                    ),
                ).addComponentFillVertically(JPanel(), 0)
                .panel
        reset()
        return requireNotNull(panel)
    }

    override fun isModified(): Boolean {
        val service = settingsServiceProvider()
        val settings = service.settings()
        return selectedProvider() != settings.provider ||
            baseUrlText() != settings.openAiBaseUrl ||
            modelText() != settings.openAiModel ||
            apiKeyText() != (service.apiKeyOrNull() ?: "")
    }

    override fun apply() {
        val service = settingsServiceProvider()
        service.updateSettings(
            AiProviderSettings(
                provider = selectedProvider(),
                openAiBaseUrl = baseUrlText(),
                openAiModel = modelText(),
            ),
        )
        service.storeApiKey(apiKeyText().ifBlank { null })
    }

    override fun reset() {
        val service = settingsServiceProvider()
        val settings = service.settings()
        providerField?.selectedItem = ProviderOption.from(settings.provider)
        baseUrlField?.text = settings.openAiBaseUrl
        modelField?.text = settings.openAiModel
        apiKeyField?.text = service.apiKeyOrNull().orEmpty()
        updateOpenAiFieldState()
    }

    override fun disposeUIResources() {
        panel = null
        providerField = null
        baseUrlField = null
        modelField = null
        apiKeyField = null
    }

    internal fun selectProviderForTesting(provider: AiProviderType) {
        providerField?.selectedItem = ProviderOption.from(provider)
        updateOpenAiFieldState()
    }

    internal fun setOpenAiFieldsForTesting(
        baseUrl: String,
        model: String,
        apiKey: String,
    ) {
        baseUrlField?.text = baseUrl
        modelField?.text = model
        apiKeyField?.text = apiKey
    }

    internal fun openAiFieldsEnabledForTesting(): Boolean =
        baseUrlField?.isEnabled == true &&
            modelField?.isEnabled == true &&
            apiKeyField?.isEnabled == true

    private fun selectedProvider(): AiProviderType =
        (providerField?.selectedItem as? ProviderOption)?.providerType ?: AiProviderType.DETERMINISTIC

    private fun baseUrlText(): String =
        baseUrlField
            ?.text
            .orEmpty()
            .trim()
            .trimEnd('/')

    private fun modelText(): String = modelField?.text.orEmpty().trim()

    private fun apiKeyText(): String = String(apiKeyField?.password ?: CharArray(0)).trim()

    private fun updateOpenAiFieldState() {
        val enabled = selectedProvider() == AiProviderType.OPENAI_COMPATIBLE
        baseUrlField?.isEnabled = enabled
        modelField?.isEnabled = enabled
        apiKeyField?.isEnabled = enabled
    }

    private enum class ProviderOption(
        val providerType: AiProviderType,
        private val label: String,
    ) {
        DETERMINISTIC(AiProviderType.DETERMINISTIC, "Deterministic (offline)"),
        OPENAI_COMPATIBLE(AiProviderType.OPENAI_COMPATIBLE, "OpenAI-compatible"),
        ;

        override fun toString(): String = label

        companion object {
            fun from(provider: AiProviderType): ProviderOption = entries.first { it.providerType == provider }
        }
    }

    private companion object {
        const val OPENAI_API_KEYS_URL = "https://platform.openai.com/settings/organization/api-keys"
    }
}
