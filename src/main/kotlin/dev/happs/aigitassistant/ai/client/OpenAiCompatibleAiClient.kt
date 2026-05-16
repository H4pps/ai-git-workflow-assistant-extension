package dev.happs.aigitassistant.ai.client

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.google.gson.annotations.SerializedName
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressManager
import dev.happs.aigitassistant.prompt.AssistantRequest
import dev.happs.aigitassistant.prompt.AssistantRequestKind
import java.net.URI
import java.net.URISyntaxException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.concurrent.CancellationException
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.system.measureTimeMillis

/**
 * AI client implementation that targets OpenAI-compatible Chat Completions APIs.
 */
class OpenAiCompatibleAiClient(
    baseUrl: String,
    private val model: String,
    private val apiKey: String,
    private val httpClient: HttpClient = HttpClient.newHttpClient(),
    private val gson: Gson = Gson(),
) : AiClient {
    private val endpoint = OpenAiCompatibleEndpoint(baseUrl)
    private val logger = Logger.getInstance(OpenAiCompatibleAiClient::class.java)

    /**
     * Sends [request] to a configured Chat Completions-compatible API.
     */
    @Suppress("LongMethod", "ThrowsCount")
    override fun generate(request: AssistantRequest): AiResponse {
        if (apiKey.isBlank()) {
            throw AiClientException("OpenAI-compatible API key is not configured. Configure AI settings.")
        }
        val normalizedModel = model.trim()
        if (normalizedModel.isBlank()) {
            throw AiClientException("OpenAI-compatible model is not configured. Configure AI settings.")
        }
        val payload =
            ChatCompletionsRequest(
                model = normalizedModel,
                messages =
                    listOf(
                        ChatMessage(
                            role = "system",
                            content = systemMessageFor(request.kind),
                        ),
                        ChatMessage(
                            role = "user",
                            content = request.promptText,
                        ),
                    ),
            )
        val requestBody = gson.toJson(payload)
        val httpRequest =
            HttpRequest
                .newBuilder(endpoint.uri)
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Authorization", "Bearer $apiKey")
                .timeout(REQUEST_TIMEOUT)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8))
                .build()

        logEvent(
            event = "openai_compatible_request_started",
            metadata =
                mapOf(
                    "model" to normalizedModel,
                    "endpoint_host" to endpoint.host,
                ),
        )
        var httpResponse: HttpResponse<String>? = null
        val elapsedMillis =
            measureTimeMillis {
                httpResponse = send(httpRequest)
            }
        val response = requireNotNull(httpResponse)
        logEvent(
            event = "openai_compatible_request_completed",
            metadata =
                mapOf(
                    "model" to normalizedModel,
                    "endpoint_host" to endpoint.host,
                    "status_class" to statusClass(response.statusCode()),
                    "latency_ms" to elapsedMillis,
                ),
        )
        if (response.statusCode() !in SUCCESS_STATUS_RANGE) {
            throw AiClientException("AI provider request failed with HTTP ${response.statusCode()}.")
        }
        val responseText = parseAssistantText(response.body())
        return AiResponse(
            generatedText = responseText,
            kind = request.kind,
            source = AiResponseSource.OPENAI_COMPATIBLE,
        )
    }

    /**
     * Sends [httpRequest] and wraps transport failures in a sanitized exception.
     */
    private fun send(httpRequest: HttpRequest): HttpResponse<String> {
        val responseFuture =
            httpClient.sendAsync(
                httpRequest,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8),
            )
        return awaitResponse(responseFuture)
    }

    /**
     * Waits for [responseFuture], cancelling it when the current progress is cancelled.
     */
    private fun awaitResponse(responseFuture: CompletableFuture<HttpResponse<String>>): HttpResponse<String> =
        try {
            pollResponse(responseFuture)
        } catch (exception: ProcessCanceledException) {
            responseFuture.cancel(true)
            throw exception
        } catch (exception: InterruptedException) {
            responseFuture.cancel(true)
            Thread.currentThread().interrupt()
            throw AiClientException("AI provider request was interrupted.", exception)
        } catch (exception: ExecutionException) {
            throw AiClientException(
                message = "AI provider request failed. Check network and provider settings.",
                cause = exception.cause ?: exception,
            )
        } catch (exception: CancellationException) {
            throw AiClientException("AI provider request failed. Check network and provider settings.", exception)
        }

    private fun pollResponse(responseFuture: CompletableFuture<HttpResponse<String>>): HttpResponse<String> {
        while (true) {
            ProgressManager.checkCanceled()
            try {
                return responseFuture.get(CANCELLATION_POLL_MILLIS, TimeUnit.MILLISECONDS)
            } catch (_: TimeoutException) {
                // Keep polling so IDE cancellation can interrupt the network request.
            }
        }
    }

    /**
     * Extracts the first non-blank assistant message from [responseBody].
     */
    private fun parseAssistantText(responseBody: String): String =
        try {
            val response = gson.fromJson(responseBody, ChatCompletionsResponse::class.java)
            response
                ?.choices
                ?.asSequence()
                ?.mapNotNull { choice -> choice.message?.content?.trim() }
                ?.firstOrNull { text -> text.isNotEmpty() }
                ?: throw AiClientException("AI provider returned an empty response.")
        } catch (exception: JsonParseException) {
            throw AiClientException("AI provider returned an invalid response payload.", exception)
        }

    /**
     * Builds a compact task-specific system instruction for the provider.
     */
    private fun systemMessageFor(requestKind: AssistantRequestKind): String =
        when (requestKind) {
            AssistantRequestKind.COMMIT_MESSAGE ->
                "You are an IntelliJ Git workflow assistant. Return only an editable Git commit message."
            AssistantRequestKind.BRANCH_NAME ->
                "You are an IntelliJ Git workflow assistant. Return only branch name suggestions, one per line."
            AssistantRequestKind.CHANGE_SUMMARY ->
                "You are an IntelliJ Git workflow assistant. Return only Summary, Risks, and Suggested tests sections."
        }

    /**
     * Writes a safe structured log event without prompt, diff, or credential content.
     */
    private fun logEvent(
        event: String,
        metadata: Map<String, Any?>,
    ) {
        val metadataBody =
            metadata.entries.joinToString(separator = " ") { (key, value) ->
                "$key=${value ?: "null"}"
            }
        logger.info("event=$event provider=openai_compatible $metadataBody")
    }

    private fun statusClass(statusCode: Int): String = "${statusCode / STATUS_CLASS_DIVISOR}xx"

    private data class ChatCompletionsRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.2,
        @SerializedName("max_completion_tokens")
        val maxCompletionTokens: Int = 512,
        val n: Int = 1,
        val stream: Boolean = false,
    )

    private data class ChatMessage(
        val role: String,
        val content: String,
    )

    private data class ChatCompletionsResponse(
        val choices: List<ChatChoice>?,
    )

    private data class ChatChoice(
        val message: ChatChoiceMessage?,
    )

    private data class ChatChoiceMessage(
        val content: String?,
    )

    private companion object {
        val REQUEST_TIMEOUT: Duration = Duration.ofSeconds(45)
        const val CANCELLATION_POLL_MILLIS = 100L
        const val STATUS_CLASS_DIVISOR = 100
        val SUCCESS_STATUS_RANGE = 200..299
    }
}

/**
 * Normalized Chat Completions endpoint for an OpenAI-compatible base URL.
 */
class OpenAiCompatibleEndpoint(
    baseUrl: String,
) {
    val uri: URI = normalize(baseUrl)
    val host: String = normalizedHost(uri) ?: "unknown"

    @Suppress("ThrowsCount")
    private fun normalize(baseUrl: String): URI {
        val trimmed =
            baseUrl
                .trim()
                .trimEnd('/')
                .ifEmpty {
                    throw AiClientException("OpenAI-compatible base URL is not configured.")
                }
        val candidate =
            try {
                URI(trimmed)
            } catch (exception: URISyntaxException) {
                throw AiClientException("OpenAI-compatible base URL is invalid.", exception)
            }
        validateSchemeAndHost(candidate)
        val endpointText =
            if (candidate.path.orEmpty().endsWith(CHAT_COMPLETIONS_PATH)) {
                trimmed
            } else {
                "$trimmed$CHAT_COMPLETIONS_PATH"
            }
        return try {
            URI(endpointText)
        } catch (exception: URISyntaxException) {
            throw AiClientException("OpenAI-compatible base URL is invalid.", exception)
        }
    }

    private fun validateSchemeAndHost(uri: URI) {
        val scheme = uri.scheme?.lowercase()
        val host = normalizedHost(uri)
        if (isMissingRequiredUrlParts(scheme, host, uri)) {
            throw AiClientException("OpenAI-compatible base URL is invalid.")
        }
        if (scheme == "https") {
            return
        }
        if (scheme == "http" && host in LOCAL_HTTP_HOSTS) {
            return
        }
        throw AiClientException("OpenAI-compatible base URL must use HTTPS unless it points to localhost.")
    }

    private fun isMissingRequiredUrlParts(
        scheme: String?,
        host: String?,
        uri: URI,
    ): Boolean = scheme == null || host == null || uri.query != null || uri.fragment != null

    private fun normalizedHost(uri: URI): String? =
        uri.host
            ?.lowercase()
            ?.removePrefix("[")
            ?.removeSuffix("]")

    private companion object {
        const val CHAT_COMPLETIONS_PATH = "/chat/completions"
        val LOCAL_HTTP_HOSTS = setOf("localhost", "127.0.0.1", "::1")
    }
}
