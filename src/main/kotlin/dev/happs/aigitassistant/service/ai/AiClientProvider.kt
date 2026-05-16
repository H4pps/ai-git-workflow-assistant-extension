package dev.happs.aigitassistant.service.ai

import com.google.gson.Gson
import dev.happs.aigitassistant.ai.client.AiClient
import dev.happs.aigitassistant.ai.client.AiClientException
import dev.happs.aigitassistant.ai.client.DeterministicAiClient
import dev.happs.aigitassistant.ai.client.LoggingAiClient
import dev.happs.aigitassistant.ai.client.OpenAiCompatibleAiClient
import dev.happs.aigitassistant.settings.AiProviderSettingsService
import dev.happs.aigitassistant.settings.AiProviderType
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
    private val deterministicClient: AiClient = LoggingAiClient(DeterministicAiClient())

    override fun currentClient(): AiClient {
        val settingsService = settingsServiceProvider()
        val settings = settingsService.settings()
        return when (settings.provider) {
            AiProviderType.DETERMINISTIC -> deterministicClient
            AiProviderType.OPENAI_COMPATIBLE -> openAiCompatibleClient(settingsService)
        }
    }

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
