package dev.happs.aigitassistant.ai.client

import dev.happs.aigitassistant.ai.prompt.AssistantOptions
import dev.happs.aigitassistant.ai.prompt.AssistantRequestKind
import dev.happs.aigitassistant.ai.prompt.CommitMessageStyle
import dev.happs.aigitassistant.ai.prompt.PromptBuilder
import dev.happs.aigitassistant.git.GitContext
import dev.happs.aigitassistant.git.GitContextState
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DeterministicAiClientTest {
    private val promptBuilder = PromptBuilder()
    private val client = DeterministicAiClient()

    @Test
    fun `commit output respects concise style`() {
        val request =
            requestFor(
                context = changedContext(),
                options =
                    AssistantOptions(
                        requestKind = AssistantRequestKind.COMMIT_MESSAGE,
                        commitMessageStyle = CommitMessageStyle.CONCISE,
                    ),
            )

        val response = client.generate(request)

        assertEquals(AssistantRequestKind.COMMIT_MESSAGE, response.kind)
        assertEquals(AiResponseSource.DETERMINISTIC, response.source)
        assertFalse(response.generatedText.contains('\n'))
        assertTrue(response.generatedText.isNotBlank())
    }

    @Test
    fun `commit output respects conventional commit style`() {
        val request =
            requestFor(
                context = changedContext(),
                options =
                    AssistantOptions(
                        requestKind = AssistantRequestKind.COMMIT_MESSAGE,
                        commitMessageStyle = CommitMessageStyle.CONVENTIONAL_COMMIT,
                    ),
            )

        val response = client.generate(request)

        assertTrue(response.generatedText.startsWith("chore: update "))
    }

    @Test
    fun `commit output respects detailed style`() {
        val request =
            requestFor(
                context = changedContext(),
                options =
                    AssistantOptions(
                        requestKind = AssistantRequestKind.COMMIT_MESSAGE,
                        commitMessageStyle = CommitMessageStyle.DETAILED,
                    ),
            )

        val response = client.generate(request)
        val lines = response.generatedText.lines().filter { it.isNotBlank() }

        assertTrue(lines.size >= 2)
        assertTrue(lines[1].startsWith("- "))
    }

    @Test
    fun `branch suggestions are lowercase kebab case deduplicated and limited`() {
        val request =
            requestFor(
                context =
                    changedContext(
                        changedFilePaths =
                            listOf(
                                "src/main/kotlin/dev/happs/aigitassistant/ai/client/LoggingAiClient.kt",
                                "src/main/kotlin/dev/happs/aigitassistant/ai/client/LoggingAiClient.kt",
                                "docs/API Draft!!.md",
                            ),
                        untrackedFilePaths = listOf("Task Note @@@.txt"),
                    ),
                options =
                    AssistantOptions(
                        requestKind = AssistantRequestKind.BRANCH_NAME,
                        userNote = " Add API + logging + logging ",
                        branchSuggestionCount = 2,
                    ),
            )

        val response = client.generate(request)
        val suggestions = response.generatedText.lines().filter { it.isNotBlank() }

        assertEquals(2, suggestions.size)
        assertEquals(suggestions.distinct().size, suggestions.size)
        suggestions.forEach { suggestion ->
            assertTrue(suggestion.matches(Regex("^[a-z0-9]+(?:-[a-z0-9]+)*$")))
            assertFalse(suggestion.startsWith("feature/"))
        }
    }

    @Test
    fun `branch suggestions satisfy requested count when enough topics exist`() {
        val request =
            requestFor(
                context =
                    changedContext(
                        changedFilePaths =
                            listOf(
                                "src/main/kotlin/PromptBuilder.kt",
                                "src/main/kotlin/DeterministicAiClient.kt",
                                "src/test/kotlin/LoggingAiClientTest.kt",
                            ),
                    ),
                options = AssistantOptions(requestKind = AssistantRequestKind.BRANCH_NAME),
            )

        val response = client.generate(request)
        val suggestions = response.generatedText.lines().filter { it.isNotBlank() }

        assertEquals(3, suggestions.size)
    }

    @Test
    fun `summary output includes summary risks and suggested tests sections`() {
        val request =
            requestFor(
                context = changedContext(),
                options = AssistantOptions(requestKind = AssistantRequestKind.CHANGE_SUMMARY),
            )

        val response = client.generate(request)

        assertContains(response.generatedText, "Summary")
        assertContains(response.generatedText, "Risks")
        assertContains(response.generatedText, "Suggested tests")
    }

    @Test
    fun `summary output applies user note`() {
        val request =
            requestFor(
                context = changedContext(),
                options =
                    AssistantOptions(
                        requestKind = AssistantRequestKind.CHANGE_SUMMARY,
                        userNote = "focus on provider settings",
                    ),
            )

        val response = client.generate(request)

        assertContains(response.generatedText, "focus on provider settings")
    }

    @Test
    fun `staged only output ignores unstaged changes when no staged changes exist`() {
        val request =
            requestFor(
                context =
                    changedContext(
                        changedFilePaths = listOf("src/Unstaged.kt"),
                        stagedFilePaths = emptyList(),
                        stagedDiff = "",
                        unstagedDiff = "diff --git a/src/Unstaged.kt b/src/Unstaged.kt\n+unstaged",
                    ),
                options =
                    AssistantOptions(
                        requestKind = AssistantRequestKind.COMMIT_MESSAGE,
                        stagedOnly = true,
                    ),
            )

        val response = client.generate(request)

        assertContains(response.generatedText, "No staged changes")
    }

    @Test
    fun `summary only mentions truncated diffs when truncation is present`() {
        val normal =
            client.generate(
                requestFor(
                    context = changedContext(),
                    options = AssistantOptions(requestKind = AssistantRequestKind.CHANGE_SUMMARY),
                ),
            )
        val truncated =
            client.generate(
                requestFor(
                    context = changedContext(stagedDiffTruncated = true),
                    options = AssistantOptions(requestKind = AssistantRequestKind.CHANGE_SUMMARY),
                ),
            )

        assertFalse(normal.generatedText.contains("truncated diff sections"))
        assertContains(truncated.generatedText, "truncated diff sections")
    }

    @Test
    fun `non changed states return clear user suggestion`() {
        val noRepository =
            client.generate(
                requestFor(
                    context =
                        changedContext(
                            state = GitContextState.NO_REPOSITORY,
                            repositoryRoot = null,
                            branchName = null,
                        ),
                    options = AssistantOptions(requestKind = AssistantRequestKind.COMMIT_MESSAGE),
                ),
            )
        val failed =
            client.generate(
                requestFor(
                    context = changedContext(state = GitContextState.FAILED, errorCode = "status_failed"),
                    options = AssistantOptions(requestKind = AssistantRequestKind.BRANCH_NAME),
                ),
            )
        val clean =
            client.generate(
                requestFor(
                    context = changedContext(state = GitContextState.CLEAN, changedFilePaths = emptyList()),
                    options = AssistantOptions(requestKind = AssistantRequestKind.CHANGE_SUMMARY),
                ),
            )

        assertContains(noRepository.generatedText, "No Git repository")
        assertContains(failed.generatedText, "failed")
        assertContains(clean.generatedText, "No pending changes")
    }

    private fun requestFor(
        context: GitContext,
        options: AssistantOptions,
    ) = promptBuilder.build(context, options)

    private fun changedContext(
        state: GitContextState = GitContextState.CHANGED,
        repositoryRoot: String? = "/tmp/repo",
        branchName: String? = "feature/phase-03",
        changedFilePaths: List<String> =
            listOf(
                "src/main/kotlin/dev/happs/aigitassistant/ai/prompt/PromptBuilder.kt",
                "src/main/kotlin/dev/happs/aigitassistant/ai/client/DeterministicAiClient.kt",
            ),
        stagedFilePaths: List<String> = changedFilePaths,
        untrackedFilePaths: List<String> = emptyList(),
        stagedDiff: String = "diff --git a/prompt b/prompt\n+prompt changes",
        unstagedDiff: String = "diff --git a/ai b/ai\n+ai changes",
        stagedDiffTruncated: Boolean = false,
        unstagedDiffTruncated: Boolean = false,
        errorCode: String? = null,
    ): GitContext =
        GitContext(
            state = state,
            repositoryRoot = repositoryRoot,
            branchName = branchName,
            changedFilePaths = changedFilePaths,
            stagedFilePaths = stagedFilePaths,
            untrackedFilePaths = untrackedFilePaths,
            stagedDiff = stagedDiff,
            unstagedDiff = unstagedDiff,
            stagedDiffTruncated = stagedDiffTruncated,
            unstagedDiffTruncated = unstagedDiffTruncated,
            errorCode = errorCode,
        )
}
