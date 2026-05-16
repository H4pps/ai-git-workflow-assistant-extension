package dev.happs.aigitassistant.settings

import com.intellij.openapi.options.Configurable
import com.intellij.ui.HyperlinkLabel
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import java.awt.Component
import javax.swing.Box
import javax.swing.BoxLayout
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
    private var baseUrlField: JBTextField? = null
    private var modelField: JBTextField? = null
    private var apiKeyField: JBPasswordField? = null

    override fun getDisplayName(): String = "AI Git Workflow Assistant"

    override fun createComponent(): JComponent {
        val baseUrl = JBTextField()
        val model = JBTextField()
        val apiKey = JBPasswordField()

        baseUrlField = baseUrl
        modelField = model
        apiKeyField = apiKey
        panel =
            FormBuilder
                .createFormBuilder()
                .addLabeledComponent("Base URL:", baseUrl)
                .addLabeledComponent("Model:", model)
                .addLabeledComponent("API key:", apiKey)
                .addComponent(createProviderLinksPanel())
                .addComponent(
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
        return baseUrlText() != settings.openAiBaseUrl ||
            modelText() != settings.openAiModel ||
            apiKeyText() != (service.apiKeyOrNull() ?: "")
    }

    override fun apply() {
        val service = settingsServiceProvider()
        service.updateSettings(
            AiProviderSettings(
                openAiBaseUrl = baseUrlText(),
                openAiModel = modelText(),
            ),
        )
        service.storeApiKey(apiKeyText().ifBlank { null })
    }

    override fun reset() {
        val service = settingsServiceProvider()
        val settings = service.settings()
        baseUrlField?.text = settings.openAiBaseUrl
        modelField?.text = settings.openAiModel
        apiKeyField?.text = service.apiKeyOrNull().orEmpty()
    }

    override fun disposeUIResources() {
        panel = null
        baseUrlField = null
        modelField = null
        apiKeyField = null
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

    private fun baseUrlText(): String =
        baseUrlField
            ?.text
            .orEmpty()
            .trim()
            .trimEnd('/')

    private fun modelText(): String = modelField?.text.orEmpty().trim()

    private fun apiKeyText(): String = String(apiKeyField?.password ?: CharArray(0)).trim()

    private fun createProviderLinksPanel(): JComponent =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            add(
                HyperlinkLabel("OpenAI API keys").apply {
                    setHyperlinkTarget(OPENAI_API_KEYS_URL)
                },
            )
            add(Box.createHorizontalStrut(JBUI.scale(LINK_GAP)))
            add(
                HyperlinkLabel("OpenRouter free models").apply {
                    setHyperlinkTarget(OPENROUTER_FREE_MODELS_URL)
                },
            )
            add(Box.createHorizontalGlue())
        }

    private companion object {
        const val OPENAI_API_KEYS_URL = "https://platform.openai.com/settings/organization/api-keys"
        const val OPENROUTER_FREE_MODELS_URL = "https://openrouter.ai/collections/free-models"
        const val LINK_GAP = 12
    }
}
