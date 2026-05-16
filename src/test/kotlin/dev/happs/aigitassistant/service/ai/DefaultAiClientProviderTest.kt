package dev.happs.aigitassistant.service.ai

import com.google.gson.Gson
import com.sun.net.httpserver.HttpServer
import dev.happs.aigitassistant.ai.client.AiClientException
import dev.happs.aigitassistant.ai.client.AiResponseSource
import dev.happs.aigitassistant.git.GitContext
import dev.happs.aigitassistant.git.GitContextState
import dev.happs.aigitassistant.prompt.AssistantOptions
import dev.happs.aigitassistant.prompt.AssistantRequestKind
import dev.happs.aigitassistant.prompt.PromptBuilder
import dev.happs.aigitassistant.settings.AiCredentialStore
import dev.happs.aigitassistant.settings.AiProviderSettings
import dev.happs.aigitassistant.settings.AiProviderSettingsService
import dev.happs.aigitassistant.settings.AiProviderType
import java.net.InetSocketAddress
import java.net.http.HttpClient
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DefaultAiClientProviderTest {
    @Test
    fun `returns deterministic client by default`() {
        val provider = DefaultAiClientProvider(settingsServiceProvider = { settingsService() })

        val response = provider.currentClient().generate(request())

        assertEquals(AiResponseSource.DETERMINISTIC, response.source)
        assertContains(response.generatedText, "Summary")
    }

    @Test
    fun `requires api key for openai compatible provider`() {
        val service = settingsService()
        service.updateSettings(AiProviderSettings(provider = AiProviderType.OPENAI_COMPATIBLE))
        val provider = DefaultAiClientProvider(settingsServiceProvider = { service })

        val error =
            assertFailsWith<AiClientException> {
                provider.currentClient()
            }

        assertContains(error.message ?: "", "API key is not configured")
    }

    @Test
    fun `returns openai compatible client when settings are complete`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat/completions") { exchange ->
            val response = """{"choices":[{"message":{"content":"Remote summary"}}]}"""
            exchange.requestBody.close()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        server.start()
        try {
            val service = settingsService()
            service.updateSettings(
                AiProviderSettings(
                    provider = AiProviderType.OPENAI_COMPATIBLE,
                    openAiBaseUrl = "http://127.0.0.1:${server.address.port}",
                    openAiModel = "gpt-test",
                ),
            )
            service.storeApiKey("sk-provider")
            val provider =
                DefaultAiClientProvider(
                    settingsServiceProvider = { service },
                    httpClient = HttpClient.newHttpClient(),
                    gson = Gson(),
                )

            val response = provider.currentClient().generate(request())

            assertEquals(AiResponseSource.OPENAI_COMPATIBLE, response.source)
            assertEquals("Remote summary", response.generatedText)
        } finally {
            server.stop(0)
        }
    }

    private fun settingsService() = AiProviderSettingsService(credentialStore = InMemoryCredentialStore())

    private fun request() =
        PromptBuilder().build(
            context =
                GitContext(
                    state = GitContextState.CHANGED,
                    repositoryRoot = "/tmp/repo",
                    branchName = "feature/provider",
                    changedFilePaths = listOf("src/main/kotlin/Provider.kt"),
                    untrackedFilePaths = emptyList(),
                    stagedDiff = "diff --git a/Provider.kt b/Provider.kt\n+provider",
                    unstagedDiff = "",
                    stagedDiffTruncated = false,
                    unstagedDiffTruncated = false,
                ),
            options = AssistantOptions(requestKind = AssistantRequestKind.CHANGE_SUMMARY),
        )

    private class InMemoryCredentialStore : AiCredentialStore {
        private var savedApiKey: String? = null

        override fun readApiKey(): String? = savedApiKey

        override fun writeApiKey(apiKey: String?) {
            savedApiKey = apiKey
        }
    }
}
