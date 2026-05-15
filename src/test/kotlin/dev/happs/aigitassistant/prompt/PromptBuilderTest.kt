package dev.happs.aigitassistant.prompt

import dev.happs.aigitassistant.git.GitContext
import dev.happs.aigitassistant.git.GitContextState
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptBuilderTest {
    private val promptBuilder = PromptBuilder()

    @Test
    fun `build includes files diffs user note and truncation notes`() {
        val context =
            gitContext(
                state = GitContextState.CHANGED,
                branchName = "feature/prompt-core",
                changedFilePaths =
                    listOf(
                        "src/main/kotlin/dev/happs/aigitassistant/prompt/PromptBuilder.kt",
                        "README.md",
                    ),
                untrackedFilePaths = listOf("docs/notes.txt"),
                stagedDiff = "diff --git a/README.md b/README.md\n+Phase 03",
                unstagedDiff = "diff --git a/src/main.kt b/src/main.kt\n+todo",
                stagedDiffTruncated = true,
                unstagedDiffTruncated = false,
            )
        val options =
            AssistantOptions(
                requestKind = AssistantRequestKind.COMMIT_MESSAGE,
                commitMessageStyle = CommitMessageStyle.DETAILED,
                userNote = "  tighten logs and prompts  ",
                branchSuggestionCount = 5,
            )

        val request = promptBuilder.build(context, options)

        assertEquals(AssistantRequestKind.COMMIT_MESSAGE, request.kind)
        assertEquals("tighten logs and prompts", request.options.userNote)
        assertContains(request.promptText, "[REQUEST]")
        assertContains(request.promptText, "[OUTPUT_INSTRUCTIONS]")
        assertContains(request.promptText, "[STATE]")
        assertContains(request.promptText, "[BRANCH]")
        assertContains(request.promptText, "[CHANGED_FILES]")
        assertContains(request.promptText, "[UNTRACKED_FILES]")
        assertContains(request.promptText, "[STAGED_DIFF]")
        assertContains(request.promptText, "[UNSTAGED_DIFF]")
        assertContains(request.promptText, "[TRUNCATION_NOTES]")
        assertContains(request.promptText, "[USER_NOTE]")
        assertContains(request.promptText, "src/main/kotlin/dev/happs/aigitassistant/prompt/PromptBuilder.kt")
        assertContains(request.promptText, "docs/notes.txt")
        assertContains(request.promptText, "diff --git a/README.md b/README.md")
        assertContains(request.promptText, "staged_diff_truncated=true")
        assertContains(request.promptText, "unstaged_diff_truncated=false")
        assertContains(request.promptText, "tighten logs and prompts")
        assertEquals(GitContextState.CHANGED, request.safeMetadata.contextState)
        assertEquals(2, request.safeMetadata.changedFileCount)
        assertEquals(1, request.safeMetadata.untrackedFileCount)
        assertTrue(request.safeMetadata.hasUserNote)
        assertEquals(5, request.safeMetadata.branchSuggestionCount)
    }

    @Test
    fun `build includes task specific output instructions`() {
        val context = gitContext(state = GitContextState.CHANGED)
        val commit =
            promptBuilder.build(
                context,
                AssistantOptions(requestKind = AssistantRequestKind.COMMIT_MESSAGE),
            )
        val branch =
            promptBuilder.build(
                context,
                AssistantOptions(requestKind = AssistantRequestKind.BRANCH_NAME, branchSuggestionCount = 4),
            )
        val summary =
            promptBuilder.build(
                context,
                AssistantOptions(requestKind = AssistantRequestKind.CHANGE_SUMMARY),
            )

        assertContains(commit.promptText, "Generate an editable Git commit message.")
        assertContains(branch.promptText, "Suggest 4 branch names.")
        assertContains(summary.promptText, "Summarize behavior changes, risks, and suggested tests.")
    }

    @Test
    fun `build handles clean context`() {
        val context =
            gitContext(
                state = GitContextState.CLEAN,
                branchName = "main",
            )
        val request = promptBuilder.build(context, AssistantOptions(requestKind = AssistantRequestKind.CHANGE_SUMMARY))

        assertContains(request.promptText, "state=CLEAN")
        assertContains(request.promptText, "[CHANGED_FILES]")
        assertContains(request.promptText, "- (none)")
        assertContains(request.promptText, "[STAGED_DIFF]")
        assertContains(request.promptText, "(empty)")
    }

    @Test
    fun `build handles no repository context`() {
        val context =
            gitContext(
                state = GitContextState.NO_REPOSITORY,
                repositoryRoot = null,
                branchName = null,
            )

        val request = promptBuilder.build(context, AssistantOptions(requestKind = AssistantRequestKind.BRANCH_NAME))

        assertContains(request.promptText, "state=NO_REPOSITORY")
        assertContains(request.promptText, "repository_present=false")
        assertContains(request.promptText, "name=(none)")
    }

    @Test
    fun `build handles failed context with error code`() {
        val context =
            gitContext(
                state = GitContextState.FAILED,
                branchName = "main",
                errorCode = "status_failed",
            )
        val request = promptBuilder.build(context, AssistantOptions(requestKind = AssistantRequestKind.COMMIT_MESSAGE))

        assertContains(request.promptText, "state=FAILED")
        assertContains(request.promptText, "error_code=status_failed")
    }

    @Test
    fun `build handles untracked only context with empty diffs`() {
        val context =
            gitContext(
                state = GitContextState.CHANGED,
                branchName = "feature/new-files",
                changedFilePaths = listOf("src/new/NewFile.kt"),
                untrackedFilePaths = listOf("src/new/NewFile.kt"),
                stagedDiff = "",
                unstagedDiff = "",
            )

        val request = promptBuilder.build(context, AssistantOptions(requestKind = AssistantRequestKind.BRANCH_NAME))

        assertContains(request.promptText, "[UNTRACKED_FILES]")
        assertContains(request.promptText, "src/new/NewFile.kt")
        assertContains(request.promptText, "[STAGED_DIFF]")
        assertContains(request.promptText, "(empty)")
        assertContains(request.promptText, "[UNSTAGED_DIFF]")
        assertContains(request.promptText, "(empty)")
    }

    private fun gitContext(
        state: GitContextState,
        repositoryRoot: String? = "/tmp/repo",
        branchName: String? = "main",
        changedFilePaths: List<String> = emptyList(),
        untrackedFilePaths: List<String> = emptyList(),
        stagedDiff: String = "",
        unstagedDiff: String = "",
        stagedDiffTruncated: Boolean = false,
        unstagedDiffTruncated: Boolean = false,
        errorCode: String? = null,
    ): GitContext =
        GitContext(
            state = state,
            repositoryRoot = repositoryRoot,
            branchName = branchName,
            changedFilePaths = changedFilePaths,
            untrackedFilePaths = untrackedFilePaths,
            stagedDiff = stagedDiff,
            unstagedDiff = unstagedDiff,
            stagedDiffTruncated = stagedDiffTruncated,
            unstagedDiffTruncated = unstagedDiffTruncated,
            errorCode = errorCode,
        )
}
