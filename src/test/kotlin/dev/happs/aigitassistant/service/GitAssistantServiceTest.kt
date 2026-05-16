package dev.happs.aigitassistant.service

import com.intellij.openapi.project.Project
import dev.happs.aigitassistant.ai.client.AiClient
import dev.happs.aigitassistant.ai.client.AiResponse
import dev.happs.aigitassistant.ai.client.AiResponseSource
import dev.happs.aigitassistant.ai.client.DeterministicAiClient
import dev.happs.aigitassistant.git.GitContext
import dev.happs.aigitassistant.git.GitContextCollector
import dev.happs.aigitassistant.git.GitContextState
import dev.happs.aigitassistant.git.GitRepositoryResolver
import dev.happs.aigitassistant.prompt.AssistantOptions
import dev.happs.aigitassistant.prompt.AssistantRequest
import dev.happs.aigitassistant.prompt.AssistantRequestKind
import dev.happs.aigitassistant.service.ai.AiClientProvider
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitAssistantServiceTest {
    private val service =
        GitAssistantService(
            aiClientProvider = AiClientProvider { DeterministicAiClient() },
        )

    @Test
    fun `generates display-ready commit message result`() {
        val result =
            service.generate(
                context = changedContext(),
                options = AssistantOptions(requestKind = AssistantRequestKind.COMMIT_MESSAGE),
            )

        assertEquals("Generated Commit Message", result.title)
        assertEquals(AssistantRequestKind.COMMIT_MESSAGE, result.requestKind)
        assertEquals(AiResponseSource.DETERMINISTIC, result.source)
        assertEquals(GitContextState.CHANGED, result.gitState)
        assertEquals("feature/editable-output", result.branchName)
        assertEquals(1, result.changedFileCount)
        assertTrue(result.generatedText.isNotBlank())
    }

    @Test
    fun `generates display-ready branch suggestions result`() {
        val result =
            service.generate(
                context = changedContext(),
                options = AssistantOptions(requestKind = AssistantRequestKind.BRANCH_NAME),
            )

        assertEquals("Suggested Branch Names", result.title)
        assertEquals(AssistantRequestKind.BRANCH_NAME, result.requestKind)
        assertTrue(
            result.generatedText
                .lines()
                .filter(String::isNotBlank)
                .isNotEmpty(),
        )
    }

    @Test
    fun `generates display-ready change summary result`() {
        val result =
            service.generate(
                context = changedContext(),
                options = AssistantOptions(requestKind = AssistantRequestKind.CHANGE_SUMMARY),
            )

        assertEquals("Change Summary", result.title)
        assertEquals(AssistantRequestKind.CHANGE_SUMMARY, result.requestKind)
        assertTrue(result.generatedText.contains("Summary"))
        assertTrue(result.generatedText.contains("Suggested tests"))
    }

    @Test
    fun `collects context through project overload when repository is missing`() {
        val collector =
            GitContextCollector(
                repositoryResolver =
                    object : GitRepositoryResolver {
                        override fun resolve(project: Project) = null
                    },
            )
        val service =
            GitAssistantService(
                gitContextCollector = collector,
                aiClientProvider = AiClientProvider { DeterministicAiClient() },
            )

        val result =
            service.generate(
                project = fakeProject(),
                options = AssistantOptions(requestKind = AssistantRequestKind.CHANGE_SUMMARY),
            )

        assertEquals("Change Summary", result.title)
        assertEquals(GitContextState.NO_REPOSITORY, result.gitState)
        assertEquals(0, result.changedFileCount)
        assertEquals(0, result.untrackedFileCount)
    }

    @Test
    fun `uses resolved ai client when provider is overridden`() {
        val customClient =
            object : AiClient {
                override fun generate(request: AssistantRequest): AiResponse =
                    AiResponse(
                        generatedText = "remote provider response",
                        kind = request.kind,
                        source = AiResponseSource.OPENAI_COMPATIBLE,
                    )
            }
        val service =
            GitAssistantService(
                aiClientProvider = AiClientProvider { customClient },
            )

        val result =
            service.generate(
                context = changedContext(),
                options = AssistantOptions(requestKind = AssistantRequestKind.CHANGE_SUMMARY),
            )

        assertEquals(AiResponseSource.OPENAI_COMPATIBLE, result.source)
        assertEquals("remote provider response", result.generatedText)
    }

    private fun changedContext(): GitContext =
        GitContext(
            state = GitContextState.CHANGED,
            repositoryRoot = "/tmp/repo",
            branchName = "feature/editable-output",
            changedFilePaths = listOf("src/main/kotlin/EditableDialog.kt"),
            untrackedFilePaths = emptyList(),
            stagedDiff = "diff --git a/EditableDialog.kt b/EditableDialog.kt\n+text area",
            unstagedDiff = "",
            stagedDiffTruncated = false,
            unstagedDiffTruncated = false,
        )

    private fun fakeProject(): Project =
        Proxy
            .newProxyInstance(
                Project::class.java.classLoader,
                arrayOf(Project::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "isDisposed" -> false
                    "isOpen" -> true
                    "getName" -> "fake-project"
                    "getBasePath" -> null
                    "toString" -> "FakeProject"
                    else -> defaultValue(method.returnType)
                }
            } as Project

    private fun defaultValue(returnType: Class<*>): Any? =
        when (returnType) {
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Float::class.javaPrimitiveType -> 0f
            Double::class.javaPrimitiveType -> 0.0
            Short::class.javaPrimitiveType -> 0.toShort()
            Byte::class.javaPrimitiveType -> 0.toByte()
            Char::class.javaPrimitiveType -> 0.toChar()
            else -> null
        }
}
