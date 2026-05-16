package dev.happs.aigitassistant.service.ai

import com.google.gson.Gson
import dev.happs.aigitassistant.ai.client.AiClient
import dev.happs.aigitassistant.ai.client.AiClientException
import dev.happs.aigitassistant.ai.client.LoggingAiClient
import dev.happs.aigitassistant.ai.client.OpenAiCompatibleAiClient
import dev.happs.aigitassistant.settings.AiProviderSettingsService
import java.net.http.HttpClient

/**
 * Resolves the active AI client implementation at request time.
 */
fun interface AiClientProvider {
    fun currentClient(): AiClient
}

/**
 * Application settings-backed AI client provider.
 */
class DefaultAiClientProvider(
    private val settingsServiceProvider: () -> AiProviderSettingsService = { AiProviderSettingsService.getInstance() },
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val gson: Gson = Gson(),
) : AiClientProvider {
    override fun currentClient(): AiClient = openAiCompatibleClient(settingsServiceProvider())

    private fun openAiCompatibleClient(settingsService: AiProviderSettingsService): AiClient {
        val settings = settingsService.settings()
        val apiKey =
            settingsService
                .apiKeyOrNull()
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: throw AiClientException("OpenAI-compatible API key is not configured. Configure AI settings.")
        return LoggingAiClient(
            OpenAiCompatibleAiClient(
                baseUrl = settings.openAiBaseUrl,
                model = settings.openAiModel,
                apiKey = apiKey,
                httpClient = httpClient,
                gson = gson,
            ),
        )
    }
}
