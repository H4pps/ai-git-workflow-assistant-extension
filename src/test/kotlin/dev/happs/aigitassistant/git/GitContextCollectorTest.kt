package dev.happs.aigitassistant.git

import git4idea.commands.GitCommand
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GitContextCollectorTest {
    private val diffLimit = GitDiffLimit(maxPrefixCharacters = 5)
    private val collector = GitContextCollector(diffLimit = diffLimit)

    @Test
    fun `returns no repository context when repository is missing`() {
        val context = collector.collectFromRepository(null)

        assertEquals(GitContextState.NO_REPOSITORY, context.state)
        assertNull(context.repositoryRoot)
        assertNull(context.branchName)
        assertTrue(context.changedFilePaths.isEmpty())
        assertTrue(context.untrackedFilePaths.isEmpty())
        assertEquals("", context.stagedDiff)
        assertEquals("", context.unstagedDiff)
    }

    @Test
    fun `returns clean context for branch-only porcelain status`() {
        val repository =
            FakeGitRepositoryHandle(
                rootPath = "/tmp/repo",
                branchName = "main",
                responses =
                    mapOf(
                        command(GitCommand.STATUS, "--porcelain=v1", "-z", "--branch", "--untracked-files=all") to
                            success("## main\u0000"),
                        command(GitCommand.DIFF, "--cached") to success(""),
                        command(GitCommand.DIFF) to success(""),
                    ),
            )

        val context = collector.collectFromRepository(repository)

        assertEquals(GitContextState.CLEAN, context.state)
        assertEquals("/tmp/repo", context.repositoryRoot)
        assertEquals("main", context.branchName)
        assertTrue(context.changedFilePaths.isEmpty())
        assertTrue(context.untrackedFilePaths.isEmpty())
        assertFalse(context.stagedDiffTruncated)
        assertFalse(context.unstagedDiffTruncated)
    }

    @Test
    fun `returns failed context when status command fails`() {
        val repository =
            FakeGitRepositoryHandle(
                rootPath = "/tmp/repo",
                branchName = "main",
                responses =
                    mapOf(
                        command(GitCommand.STATUS, "--porcelain=v1", "-z", "--branch", "--untracked-files=all") to
                            failure(
                                exitCode = 128,
                                errorOutput = "fatal: not a git repository",
                            ),
                    ),
            )

        val context = collector.collectFromRepository(repository)

        assertEquals(GitContextState.FAILED, context.state)
        assertEquals("/tmp/repo", context.repositoryRoot)
        assertEquals("main", context.branchName)
        assertEquals("status_failed", context.errorCode)
    }

    @Test
    fun `collects untracked paths and marks repository as changed`() {
        val repository =
            FakeGitRepositoryHandle(
                rootPath = "/tmp/repo",
                branchName = "feature/untracked",
                responses =
                    mapOf(
                        command(GitCommand.STATUS, "--porcelain=v1", "-z", "--branch", "--untracked-files=all") to
                            success("## feature/untracked\u0000?? new-file.txt\u0000?? src/NewFile.kt\u0000"),
                        command(GitCommand.DIFF, "--cached") to success(""),
                        command(GitCommand.DIFF) to success(""),
                    ),
            )

        val context = collector.collectFromRepository(repository)

        assertEquals(GitContextState.CHANGED, context.state)
        assertEquals(listOf("new-file.txt", "src/NewFile.kt"), context.changedFilePaths)
        assertEquals(listOf("new-file.txt", "src/NewFile.kt"), context.untrackedFilePaths)
        assertEquals("", context.stagedDiff)
        assertEquals("", context.unstagedDiff)
    }

    @Test
    fun `uses destination paths for renamed files in null-delimited status`() {
        val repository =
            FakeGitRepositoryHandle(
                rootPath = "/tmp/repo",
                branchName = "feature/rename",
                responses =
                    mapOf(
                        command(GitCommand.STATUS, "--porcelain=v1", "-z", "--branch", "--untracked-files=all") to
                            success("## feature/rename\u0000R  src/NewName.kt\u0000src/OldName.kt\u0000"),
                        command(GitCommand.DIFF, "--cached") to success(""),
                        command(GitCommand.DIFF) to success(""),
                    ),
            )

        val context = collector.collectFromRepository(repository)

        assertEquals(GitContextState.CHANGED, context.state)
        assertEquals(listOf("src/NewName.kt"), context.changedFilePaths)
        assertTrue(context.untrackedFilePaths.isEmpty())
    }

    @Test
    fun `truncates staged and unstaged diffs using configured limit`() {
        val repository =
            FakeGitRepositoryHandle(
                rootPath = "/tmp/repo",
                branchName = "main",
                responses =
                    mapOf(
                        command(GitCommand.STATUS, "--porcelain=v1", "-z", "--branch", "--untracked-files=all") to
                            success("## main\u0000 M src/App.kt\u0000"),
                        command(GitCommand.DIFF, "--cached") to success("123456"),
                        command(GitCommand.DIFF) to success("abcdefg"),
                    ),
            )

        val context = collector.collectFromRepository(repository)

        assertEquals(GitContextState.CHANGED, context.state)
        assertEquals("12345\n...[truncated 1 chars]", context.stagedDiff)
        assertEquals("abcde\n...[truncated 2 chars]", context.unstagedDiff)
        assertTrue(context.stagedDiffTruncated)
        assertTrue(context.unstagedDiffTruncated)
    }

    @Test
    fun `returns failed context when unstaged diff command fails`() {
        val repository =
            FakeGitRepositoryHandle(
                rootPath = "/tmp/repo",
                branchName = "main",
                responses =
                    mapOf(
                        command(GitCommand.STATUS, "--porcelain=v1", "-z", "--branch", "--untracked-files=all") to
                            success("## main\u0000 M src/App.kt\u0000"),
                        command(GitCommand.DIFF, "--cached") to success("staged"),
                        command(GitCommand.DIFF) to failure(exitCode = 1, errorOutput = "diff failed"),
                    ),
            )

        val context = collector.collectFromRepository(repository)

        assertEquals(GitContextState.FAILED, context.state)
        assertEquals("unstaged_diff_failed", context.errorCode)
    }

    private fun command(
        command: GitCommand,
        vararg parameters: String,
    ): GitCommandRequest = GitCommandRequest(command, parameters.toList())

    private fun success(output: String): GitCommandExecutionResult =
        GitCommandExecutionResult(
            success = true,
            exitCode = 0,
            output = output,
            errorOutput = "",
        )

    private fun failure(
        exitCode: Int,
        errorOutput: String,
    ): GitCommandExecutionResult =
        GitCommandExecutionResult(
            success = false,
            exitCode = exitCode,
            output = "",
            errorOutput = errorOutput,
        )

    private class FakeGitRepositoryHandle(
        override val rootPath: String,
        override val branchName: String?,
        private val responses: Map<GitCommandRequest, GitCommandExecutionResult>,
    ) : GitRepositoryHandle {
        override fun run(request: GitCommandRequest): GitCommandExecutionResult =
            requireNotNull(responses[request]) { "Missing fake response for $request" }
    }
}
