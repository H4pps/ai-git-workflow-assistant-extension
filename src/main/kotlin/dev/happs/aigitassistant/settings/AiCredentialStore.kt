package dev.happs.aigitassistant.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Storage boundary for provider credentials.
 */
interface AiCredentialStore {
    /**
     * Reads the stored API key, or null when no key is configured.
     */
    fun readApiKey(): String?

    /**
     * Writes [apiKey], or clears the key when null.
     */
    fun writeApiKey(apiKey: String?)
}

/**
 * Credential store backed by IntelliJ Password Safe.
 */
class PasswordSafeAiCredentialStore : AiCredentialStore {
    private val credentialAttributes =
        CredentialAttributes(generateServiceName(CREDENTIAL_SERVICE, OPENAI_API_KEY_ENTRY))

    override fun readApiKey(): String? =
        PasswordSafe.instance
            .get(credentialAttributes)
            ?.getPasswordAsString()
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    override fun writeApiKey(apiKey: String?) {
        val normalizedKey = apiKey?.trim()?.takeIf { it.isNotEmpty() }
        val credentials = normalizedKey?.let { key -> Credentials(API_KEY_USERNAME, key) }
        PasswordSafe.instance.set(credentialAttributes, credentials)
    }

    private companion object {
        const val CREDENTIAL_SERVICE = "AI Git Workflow Assistant"
        const val OPENAI_API_KEY_ENTRY = "openai-compatible-api-key"
        const val API_KEY_USERNAME = "api-key"
    }
}
