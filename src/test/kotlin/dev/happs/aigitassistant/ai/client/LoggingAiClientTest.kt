package dev.happs.aigitassistant.ai.client

import dev.happs.aigitassistant.ai.prompt.AssistantOptions
import dev.happs.aigitassistant.ai.prompt.AssistantRequestKind
import dev.happs.aigitassistant.ai.prompt.PromptBuilder
import dev.happs.aigitassistant.git.GitContext
import dev.happs.aigitassistant.git.GitContextState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LoggingAiClientTest {
    private val promptBuilder = PromptBuilder()

    @Test
    fun `logs request start and success with safe metadata only`() {
        val logs = mutableListOf<CapturedLog>()
        val delegate =
            object : AiClient {
                override fun generate(request: dev.happs.aigitassistant.ai.prompt.AssistantRequest): AiResponse =
                    AiResponse(
                        generatedText = "ok",
                        kind = request.kind,
                        source = AiResponseSource.DETERMINISTIC,
                    )
            }
        val client =
            LoggingAiClient(
                delegate = delegate,
                logSink = { level, event, metadata ->
                    logs += CapturedLog(level = level, event = event, metadata = metadata)
                },
            )
        val request = sensitiveRequest()

        client.generate(request)

        assertEquals(2, logs.size)
        assertEquals("ai_request_started", logs[0].event)
        assertEquals("ai_request_succeeded", logs[1].event)
        logs.forEach { log ->
            assertFalse(log.metadata.containsKey("prompt_text"))
            assertFalse(log.metadata.containsKey("staged_diff"))
            assertFalse(log.metadata.containsKey("unstaged_diff"))
            assertFalse(log.metadata.containsKey("user_note"))
            assertFalse(log.metadata.containsKey("changed_file_paths"))
            assertFalse(log.metadata.containsKey("untracked_file_paths"))
            assertFalse(log.metadata.containsKey("repository_root"))
            val flattened = log.metadata.values.joinToString(separator = " ")
            assertFalse(flattened.contains("very sensitive user note"))
            assertFalse(flattened.contains("src/main/kotlin/dev/happs/aigitassistant/ai/client/LoggingAiClient.kt"))
            assertFalse(flattened.contains("diff --git"))
        }
    }

    @Test
    fun `logs request failure with safe metadata and rethrows`() {
        val logs = mutableListOf<CapturedLog>()
        val delegate =
            object : AiClient {
                override fun generate(request: dev.happs.aigitassistant.ai.prompt.AssistantRequest): AiResponse =
                    throw IllegalStateException("unexpected failure")
            }
        val client =
            LoggingAiClient(
                delegate = delegate,
                logSink = { level, event, metadata ->
                    logs += CapturedLog(level = level, event = event, metadata = metadata)
                },
            )

        assertFailsWith<IllegalStateException> {
            client.generate(sensitiveRequest())
        }
        assertEquals(2, logs.size)
        assertEquals("ai_request_started", logs[0].event)
        assertEquals("ai_request_failed", logs[1].event)
        assertTrue(logs[1].metadata.containsKey("error_type"))
        assertFalse(logs[1].metadata.containsKey("prompt_text"))
    }

    private fun sensitiveRequest() =
        promptBuilder.build(
            context =
                GitContext(
                    state = GitContextState.CHANGED,
                    repositoryRoot = "/tmp/private-root",
                    branchName = "feature/sensitive",
                    changedFilePaths = listOf("src/main/kotlin/dev/happs/aigitassistant/ai/client/LoggingAiClient.kt"),
                    untrackedFilePaths = listOf("secret-plan.txt"),
                    stagedDiff = "diff --git a/secret-plan.txt b/secret-plan.txt",
                    unstagedDiff = "diff --git a/LoggingAiClient.kt b/LoggingAiClient.kt",
                    stagedDiffTruncated = false,
                    unstagedDiffTruncated = false,
                ),
            options =
                AssistantOptions(
                    requestKind = AssistantRequestKind.CHANGE_SUMMARY,
                    userNote = "very sensitive user note",
                ),
        )

    private data class CapturedLog(
        val level: LoggingLevel,
        val event: String,
        val metadata: Map<String, Any?>,
    )
}
