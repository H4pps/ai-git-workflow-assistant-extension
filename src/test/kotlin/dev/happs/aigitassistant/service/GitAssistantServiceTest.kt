package dev.happs.aigitassistant.service

import dev.happs.aigitassistant.ai.client.AiResponseSource
import dev.happs.aigitassistant.git.GitContext
import dev.happs.aigitassistant.git.GitContextState
import dev.happs.aigitassistant.prompt.AssistantOptions
import dev.happs.aigitassistant.prompt.AssistantRequestKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitAssistantServiceTest {
    private val service = GitAssistantService()

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
}
