package dev.happs.aigitassistant.ai.prompt

import dev.happs.aigitassistant.git.GitContext
import dev.happs.aigitassistant.git.GitContextState
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
                        "src/main/kotlin/dev/happs/aigitassistant/ai/prompt/PromptBuilder.kt",
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
        assertTrue(request.promptText.startsWith("[REQUEST]"))
        assertContains(request.promptText, "[REQUEST]")
        assertContains(request.promptText, "[INPUT_SCOPE]")
        assertContains(request.promptText, "[STATE]")
        assertContains(request.promptText, "[BRANCH]")
        assertContains(request.promptText, "[CHANGED_FILES]")
        assertContains(request.promptText, "[UNTRACKED_FILES]")
        assertContains(request.promptText, "[STAGED_DIFF]")
        assertContains(request.promptText, "[UNSTAGED_DIFF]")
        assertContains(request.promptText, "[TRUNCATION_NOTES]")
        assertContains(request.promptText, "[USER_NOTE]")
        assertFalse(request.promptText.contains("[OUTPUT_CONTRACT]"))
        assertContains(request.promptText, "src/main/kotlin/dev/happs/aigitassistant/ai/prompt/PromptBuilder.kt")
        assertContains(request.promptText, "docs/notes.txt")
        assertContains(request.promptText, "diff --git a/README.md b/README.md")
        assertContains(request.promptText, "staged_diff_truncated=true")
        assertContains(request.promptText, "unstaged_diff_truncated=false")
        assertContains(request.promptText, "tighten logs and prompts")
        assertTrue(request.promptText.indexOf("[USER_NOTE]") < request.promptText.indexOf("[CHANGED_FILES]"))
        assertEquals(GitContextState.CHANGED, request.safeMetadata.contextState)
        assertEquals(2, request.safeMetadata.changedFileCount)
        assertEquals(1, request.safeMetadata.untrackedFileCount)
        assertTrue(request.safeMetadata.hasUserNote)
        assertEquals(5, request.safeMetadata.branchSuggestionCount)
    }

    @Test
    fun `build excludes task specific output instructions`() {
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

        listOf(commit, branch, summary).forEach { request ->
            assertFalse(request.promptText.contains("[OUTPUT_CONTRACT]"))
            assertFalse(request.promptText.contains("Expected response shape example:"))
            assertFalse(request.promptText.contains("Return exactly one Git commit message."))
            assertFalse(request.promptText.contains("bare lowercase kebab-case branch names"))
            assertFalse(request.promptText.contains("Summary, Risks, and Suggested tests"))
            assertFalse(request.promptText.contains("Follow Conventional Commits 1.0.0."))
            assertFalse(request.promptText.contains("<type>[optional scope][!]: <description>"))
            assertContains(request.promptText, "[REQUEST]")
            assertContains(request.promptText, "[INPUT_SCOPE]")
            assertContains(request.promptText, "[USER_NOTE]")
        }
    }

    @Test
    fun `staged only scope excludes unstaged diff and untracked files`() {
        val context =
            gitContext(
                state = GitContextState.CHANGED,
                changedFilePaths = listOf("src/Staged.kt", "src/Unstaged.kt", "docs/notes.md"),
                stagedFilePaths = listOf("src/Staged.kt"),
                untrackedFilePaths = listOf("docs/notes.md"),
                stagedDiff = "diff --git a/src/Staged.kt b/src/Staged.kt\n+staged change",
                unstagedDiff = "diff --git a/src/Unstaged.kt b/src/Unstaged.kt\n+unstaged secret",
            )

        val request =
            promptBuilder.build(
                context,
                AssistantOptions(
                    requestKind = AssistantRequestKind.CHANGE_SUMMARY,
                    stagedOnly = true,
                ),
            )

        assertContains(request.promptText, "reasoning_scope=STAGED_ONLY")
        assertContains(request.promptText, "Use only staged files and the staged diff.")
        assertContains(request.promptText, "src/Staged.kt")
        assertContains(request.promptText, "+staged change")
        assertContains(request.promptText, "- (none)")
        assertTrue(!request.promptText.contains("src/Unstaged.kt"))
        assertTrue(!request.promptText.contains("docs/notes.md"))
        assertTrue(!request.promptText.contains("unstaged secret"))
        assertEquals(listOf("src/Staged.kt"), request.reasoningContext.changedFilePaths)
        assertTrue(request.reasoningContext.untrackedFilePaths.isEmpty())
        assertEquals("", request.reasoningContext.unstagedDiff)
        assertEquals(1, request.safeMetadata.changedFileCount)
        assertEquals(0, request.safeMetadata.untrackedFileCount)
        assertEquals(0, request.safeMetadata.unstagedDiffCharCount)
        assertEquals(true, request.safeMetadata.stagedOnly)
    }

    @Test
    fun `all changes scope includes staged unstaged and untracked context`() {
        val context =
            gitContext(
                state = GitContextState.CHANGED,
                changedFilePaths = listOf("src/Staged.kt", "src/Unstaged.kt", "docs/notes.md"),
                stagedFilePaths = listOf("src/Staged.kt"),
                untrackedFilePaths = listOf("docs/notes.md"),
                stagedDiff = "diff --git a/src/Staged.kt b/src/Staged.kt\n+staged change",
                unstagedDiff = "diff --git a/src/Unstaged.kt b/src/Unstaged.kt\n+unstaged change",
            )

        val request =
            promptBuilder.build(
                context,
                AssistantOptions(
                    requestKind = AssistantRequestKind.COMMIT_MESSAGE,
                    stagedOnly = false,
                ),
            )

        assertContains(request.promptText, "reasoning_scope=ALL_CHANGES")
        assertContains(request.promptText, "Use staged, unstaged, and untracked changes.")
        assertContains(request.promptText, "src/Staged.kt")
        assertContains(request.promptText, "src/Unstaged.kt")
        assertContains(request.promptText, "docs/notes.md")
        assertContains(request.promptText, "+staged change")
        assertContains(request.promptText, "+unstaged change")
        assertEquals(context, request.reasoningContext)
        assertEquals(3, request.safeMetadata.changedFileCount)
        assertEquals(1, request.safeMetadata.untrackedFileCount)
    }

    @Test
    fun `staged only scope reports clean when there are no staged changes`() {
        val context =
            gitContext(
                state = GitContextState.CHANGED,
                changedFilePaths = listOf("src/Unstaged.kt"),
                stagedFilePaths = emptyList(),
                stagedDiff = "",
                unstagedDiff = "diff --git a/src/Unstaged.kt b/src/Unstaged.kt\n+unstaged",
            )

        val request =
            promptBuilder.build(
                context,
                AssistantOptions(
                    requestKind = AssistantRequestKind.COMMIT_MESSAGE,
                    stagedOnly = true,
                ),
            )

        assertEquals(GitContextState.CLEAN, request.reasoningContext.state)
        assertContains(request.promptText, "state=CLEAN")
        assertContains(request.promptText, "reasoning_scope=STAGED_ONLY")
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

    @Test
    fun `build limits rendered file path lists`() {
        val changedPaths = (1..55).map { index -> "src/File$index.kt" }
        val untrackedPaths = (1..55).map { index -> "docs/Note$index.md" }
        val context =
            gitContext(
                state = GitContextState.CHANGED,
                changedFilePaths = changedPaths,
                untrackedFilePaths = untrackedPaths,
            )

        val request = promptBuilder.build(context, AssistantOptions(requestKind = AssistantRequestKind.COMMIT_MESSAGE))

        assertContains(request.promptText, "src/File50.kt")
        assertFalse(request.promptText.contains("src/File51.kt"))
        assertContains(request.promptText, "...[omitted 5 paths]")
        assertContains(request.promptText, "docs/Note50.md")
        assertFalse(request.promptText.contains("docs/Note51.md"))
        assertEquals(55, request.safeMetadata.changedFileCount)
        assertEquals(55, request.safeMetadata.untrackedFileCount)
    }

    private fun gitContext(
        state: GitContextState,
        repositoryRoot: String? = "/tmp/repo",
        branchName: String? = "main",
        changedFilePaths: List<String> = emptyList(),
        stagedFilePaths: List<String> = changedFilePaths,
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
            stagedFilePaths = stagedFilePaths,
            untrackedFilePaths = untrackedFilePaths,
            stagedDiff = stagedDiff,
            unstagedDiff = unstagedDiff,
            stagedDiffTruncated = stagedDiffTruncated,
            unstagedDiffTruncated = unstagedDiffTruncated,
            errorCode = errorCode,
        )
}
