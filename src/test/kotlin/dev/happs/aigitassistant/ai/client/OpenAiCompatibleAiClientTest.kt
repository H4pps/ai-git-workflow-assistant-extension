package dev.happs.aigitassistant.ai.client

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.sun.net.httpserver.HttpServer
import dev.happs.aigitassistant.ai.prompt.AssistantOptions
import dev.happs.aigitassistant.ai.prompt.AssistantRequestKind
import dev.happs.aigitassistant.ai.prompt.PromptBuilder
import dev.happs.aigitassistant.git.GitContext
import dev.happs.aigitassistant.git.GitContextState
import java.net.InetSocketAddress
import java.net.http.HttpClient
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class OpenAiCompatibleAiClientTest {
    private val promptBuilder = PromptBuilder()

    @Test
    fun `posts chat completions request and returns first assistant message`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = mutableListOf<CapturedRequest>()
        server.createContext("/chat/completions") { exchange ->
            val body = exchange.requestBody.use { it.readBytes().decodeToString() }
            requests +=
                CapturedRequest(
                    method = exchange.requestMethod,
                    path = exchange.requestURI.path,
                    authorization = exchange.requestHeaders.getFirst("Authorization"),
                    contentType = exchange.requestHeaders.getFirst("Content-Type"),
                    body = body,
                )
            val response =
                """
                {"id":"chatcmpl_01","choices":[{"message":{"role":"assistant","content":"Generated summary"}}]}
                """.trimIndent()
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        server.start()
        try {
            val client =
                OpenAiCompatibleAiClient(
                    baseUrl = "http://127.0.0.1:${server.address.port}",
                    model = "gpt-test",
                    apiKey = "sk-test-secret",
                    httpClient = HttpClient.newHttpClient(),
                    gson = Gson(),
                )

            val response = client.generate(request())

            assertEquals(AiResponseSource.OPENAI_COMPATIBLE, response.source)
            assertEquals(AssistantRequestKind.CHANGE_SUMMARY, response.kind)
            assertEquals("Generated summary", response.generatedText)
            assertEquals(1, requests.size)
            assertEquals("POST", requests[0].method)
            assertEquals("/chat/completions", requests[0].path)
            assertEquals("Bearer sk-test-secret", requests[0].authorization)
            assertContains(requests[0].contentType ?: "", "application/json")
            val requestJson = JsonParser.parseString(requests[0].body).asJsonObject
            assertEquals("gpt-test", requestJson.get("model").asString)
            assertEquals(0.2, requestJson.get("temperature").asDouble)
            assertEquals(512, requestJson.get("max_completion_tokens").asInt)
            assertEquals(1, requestJson.get("n").asInt)
            assertEquals(false, requestJson.get("stream").asBoolean)
            val messages = requestJson.getAsJsonArray("messages")
            val systemMessage = messages[0].asJsonObject
            val userMessage = messages[1].asJsonObject
            assertEquals("system", systemMessage.get("role").asString)
            assertContains(systemMessage.get("content").asString, "Git workflow")
            assertEquals("user", userMessage.get("role").asString)
            assertContains(userMessage.get("content").asString, "[REQUEST]")
            assertContains(userMessage.get("content").asString, "[USER_NOTE]")
            assertContains(userMessage.get("content").asString, "focus on provider settings")
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `commit request sends strict commit contract`() {
        val messages = capturedMessagesFor(AssistantRequestKind.COMMIT_MESSAGE)

        assertContains(messages.system, "Request kind: COMMIT_MESSAGE.")
        assertContains(messages.system, "Return exactly one Git commit message.")
        assertContains(messages.system, "Expected response shape example:")
        assertContains(messages.system, "chore(settings): update provider defaults")
        assertContains(messages.system, "Never return a change summary for this request.")
        assertFalse(messages.system.contains("Summary, Risks, and Suggested tests"))
        assertContains(messages.user, "[REQUEST]")
        assertFalse(messages.user.contains("[OUTPUT_CONTRACT]"))
        assertFalse(messages.user.contains("Return exactly one Git commit message."))
        assertFalse(messages.user.contains("Return only the sections: Summary"))
    }

    @Test
    fun `conventional commit request sends conventional commits guidance`() {
        val messages = capturedMessagesFor(requestKind = AssistantRequestKind.COMMIT_MESSAGE)

        assertContains(messages.system, "Use Conventional Commits 1.0.0.")
        assertContains(messages.system, "Follow Conventional Commits 1.0.0.")
        assertContains(messages.system, "<type>[optional scope][!]: <description>")
        assertContains(messages.system, "Use feat for new features and fix for bug fixes.")
        assertContains(messages.system, "Use BREAKING CHANGE:")
        assertFalse(messages.user.contains("Follow Conventional Commits 1.0.0."))
        assertFalse(messages.user.contains("<type>[optional scope][!]: <description>"))
    }

    @Test
    fun `branch request sends strict branch contract`() {
        val messages = capturedMessagesFor(AssistantRequestKind.BRANCH_NAME)

        assertContains(messages.system, "Request kind: BRANCH_NAME.")
        assertContains(messages.system, "Return only 3 bare lowercase kebab-case branch names, one per line.")
        assertContains(messages.system, "Expected response shape example:")
        assertContains(messages.system, "update-provider-settings")
        assertContains(messages.system, "Never return a change summary for this request.")
        assertFalse(messages.system.contains("Summary, Risks, and Suggested tests"))
        assertContains(messages.user, "[REQUEST]")
        assertFalse(messages.user.contains("[OUTPUT_CONTRACT]"))
        assertFalse(messages.user.contains("bare lowercase kebab-case branch names"))
        assertFalse(messages.user.contains("Return only the sections: Summary"))
    }

    @Test
    fun `summary request sends summary contract in system message only`() {
        val messages = capturedMessagesFor(AssistantRequestKind.CHANGE_SUMMARY)

        assertContains(messages.system, "Request kind: CHANGE_SUMMARY.")
        assertContains(messages.system, "Return only the sections: Summary, Risks, and Suggested tests.")
        assertContains(messages.system, "Summary")
        assertContains(messages.system, "Risks")
        assertContains(messages.system, "Suggested tests")
        assertFalse(messages.system.contains("Return exactly one Git commit message."))
        assertFalse(messages.system.contains("bare lowercase kebab-case branch names"))
        assertContains(messages.user, "[REQUEST]")
        assertContains(messages.user, "[USER_NOTE]")
        assertFalse(messages.user.contains("[OUTPUT_CONTRACT]"))
        assertFalse(messages.user.contains("Summary, Risks, and Suggested tests"))
    }

    @Test
    fun `endpoint normalizes base url variants`() {
        assertEquals(
            "https://api.openai.com/v1/chat/completions",
            OpenAiCompatibleEndpoint(" https://api.openai.com/v1/ ").uri.toString(),
        )
        assertEquals(
            "https://llm.example.com/v1/chat/completions",
            OpenAiCompatibleEndpoint("https://llm.example.com/v1/chat/completions").uri.toString(),
        )
        assertEquals(
            "http://localhost:11434/v1/chat/completions",
            OpenAiCompatibleEndpoint("http://localhost:11434/v1").uri.toString(),
        )
        assertEquals(
            "http://[::1]:11434/v1/chat/completions",
            OpenAiCompatibleEndpoint("http://[::1]:11434/v1").uri.toString(),
        )
    }

    @Test
    fun `endpoint rejects unsafe or invalid urls`() {
        assertFailsWith<AiClientException> {
            OpenAiCompatibleEndpoint("http://llm.example.com/v1")
        }
        assertFailsWith<AiClientException> {
            OpenAiCompatibleEndpoint("ftp://llm.example.com/v1")
        }
        assertFailsWith<AiClientException> {
            OpenAiCompatibleEndpoint("not a url")
        }
        assertFailsWith<AiClientException> {
            OpenAiCompatibleEndpoint("https://api.openai.com/v1?x=1")
        }
        assertFailsWith<AiClientException> {
            OpenAiCompatibleEndpoint("https://api.openai.com/v1#frag")
        }
        assertFailsWith<AiClientException> {
            OpenAiCompatibleEndpoint("   ")
        }
    }

    @Test
    fun `throws sanitized error for non successful status`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat/completions") { exchange ->
            val response = """{"error":{"message":"bad key sk-test-secret"}}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(401, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        server.start()
        try {
            val client =
                OpenAiCompatibleAiClient(
                    baseUrl = "http://127.0.0.1:${server.address.port}",
                    model = "gpt-test",
                    apiKey = "sk-test-secret",
                    httpClient = HttpClient.newHttpClient(),
                    gson = Gson(),
                )
            val request = request()

            val error =
                assertFailsWith<AiClientException> {
                    client.generate(request)
                }

            assertContains(error.message ?: "", "401")
            assertFalse((error.message ?: "").contains("sk-test-secret"))
            assertFalse((error.message ?: "").contains(request.promptText))
            assertFalse((error.message ?: "").contains("diff --git"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `throws sanitized error for malformed response payload`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat/completions") { exchange ->
            val response = """{"choices":[{"message":{"role":"assistant","content":"   "}}]}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        server.start()
        try {
            val client =
                OpenAiCompatibleAiClient(
                    baseUrl = "http://127.0.0.1:${server.address.port}",
                    model = "gpt-test",
                    apiKey = "sk-test-secret",
                    httpClient = HttpClient.newHttpClient(),
                    gson = Gson(),
                )
            val request = request()

            val error =
                assertFailsWith<AiClientException> {
                    client.generate(request)
                }

            assertContains(error.message ?: "", "empty response")
            assertFalse((error.message ?: "").contains("sk-test-secret"))
            assertFalse((error.message ?: "").contains(request.promptText))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `throws sanitized error for invalid json payload`() {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/chat/completions") { exchange ->
            val response = "{not-json"
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        server.start()
        try {
            val client =
                OpenAiCompatibleAiClient(
                    baseUrl = "http://127.0.0.1:${server.address.port}",
                    model = "gpt-test",
                    apiKey = "sk-test-secret",
                    httpClient = HttpClient.newHttpClient(),
                    gson = Gson(),
                )
            val error =
                assertFailsWith<AiClientException> {
                    client.generate(request())
                }

            assertContains(error.message ?: "", "invalid response payload")
            assertFalse((error.message ?: "").contains("sk-test-secret"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun `requires non blank api key`() {
        val client =
            OpenAiCompatibleAiClient(
                baseUrl = "https://api.openai.com/v1",
                model = "gpt-test",
                apiKey = "   ",
                httpClient = HttpClient.newHttpClient(),
                gson = Gson(),
            )

        val error =
            assertFailsWith<AiClientException> {
                client.generate(request())
            }
        assertContains(error.message ?: "", "API key is not configured")
    }

    @Test
    fun `requires non blank model`() {
        val client =
            OpenAiCompatibleAiClient(
                baseUrl = "https://api.openai.com/v1",
                model = "  ",
                apiKey = "sk-test-secret",
                httpClient = HttpClient.newHttpClient(),
                gson = Gson(),
            )

        val error =
            assertFailsWith<AiClientException> {
                client.generate(request())
            }
        assertContains(error.message ?: "", "model is not configured")
    }

    private fun capturedMessagesFor(requestKind: AssistantRequestKind): CapturedMessages {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val requests = mutableListOf<CapturedRequest>()
        server.createContext("/chat/completions") { exchange ->
            val body = exchange.requestBody.use { it.readBytes().decodeToString() }
            requests +=
                CapturedRequest(
                    method = exchange.requestMethod,
                    path = exchange.requestURI.path,
                    authorization = exchange.requestHeaders.getFirst("Authorization"),
                    contentType = exchange.requestHeaders.getFirst("Content-Type"),
                    body = body,
                )
            val response = """{"choices":[{"message":{"content":"ok"}}]}"""
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.toByteArray().size.toLong())
            exchange.responseBody.use { it.write(response.toByteArray()) }
        }
        server.start()
        try {
            val client =
                OpenAiCompatibleAiClient(
                    baseUrl = "http://127.0.0.1:${server.address.port}",
                    model = "gpt-test",
                    apiKey = "sk-test-secret",
                    httpClient = HttpClient.newHttpClient(),
                    gson = Gson(),
                )

            client.generate(request(requestKind = requestKind))

            val requestJson = JsonParser.parseString(requests.single().body).asJsonObject
            val messages = requestJson.getAsJsonArray("messages")
            return CapturedMessages(
                system = messages[0].asJsonObject.get("content").asString,
                user = messages[1].asJsonObject.get("content").asString,
            )
        } finally {
            server.stop(0)
        }
    }

    private fun request(requestKind: AssistantRequestKind = AssistantRequestKind.CHANGE_SUMMARY) =
        promptBuilder.build(
            context =
                GitContext(
                    state = GitContextState.CHANGED,
                    repositoryRoot = "/tmp/repo",
                    branchName = "feature/openai",
                    changedFilePaths =
                        listOf(
                            "src/main/kotlin/dev/happs/aigitassistant/service/GitAssistantService.kt",
                        ),
                    untrackedFilePaths = emptyList(),
                    stagedDiff = "diff --git a/service.kt b/service.kt\n+new behavior",
                    unstagedDiff = "",
                    stagedDiffTruncated = false,
                    unstagedDiffTruncated = false,
                ),
            options =
                AssistantOptions(
                    requestKind = requestKind,
                    userNote = "focus on provider settings",
                ),
        )

    private data class CapturedMessages(
        val system: String,
        val user: String,
    )

    private data class CapturedRequest(
        val method: String,
        val path: String,
        val authorization: String?,
        val contentType: String?,
        val body: String,
    )
}
