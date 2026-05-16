package dev.happs.aigitassistant.git

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import git4idea.commands.GitCommand
import java.util.LinkedHashSet

/**
 * Collects Git repository metadata and diffs required by assistant prompts.
 */
class GitContextCollector(
    private val repositoryResolver: GitRepositoryResolver = Git4IdeaRepositoryResolver(),
    private val diffLimit: GitDiffLimit = GitDiffLimit(),
) {
    private val logger = Logger.getInstance(GitContextCollector::class.java)

    /**
     * Collects Git context for [project].
     */
    fun collect(project: Project): GitContext = collectFromRepository(repositoryResolver.resolve(project))

    /**
     * Collects Git context from a resolved repository handle.
     */
    internal fun collectFromRepository(repository: GitRepositoryHandle?): GitContext {
        val context =
            if (repository == null) {
                GitContext(
                    state = GitContextState.NO_REPOSITORY,
                    repositoryRoot = null,
                    branchName = null,
                    changedFilePaths = emptyList(),
                    untrackedFilePaths = emptyList(),
                    stagedDiff = "",
                    unstagedDiff = "",
                    stagedDiffTruncated = false,
                    unstagedDiffTruncated = false,
                )
            } else {
                collectFromExistingRepository(repository)
            }

        if (context.state == GitContextState.FAILED) {
            return context
        }
        logCollectionSuccess(context)
        return context
    }

    /**
     * Parses `git status --porcelain=v1 -z --branch --untracked-files=all` output.
     */
    private fun parseStatusPorcelain(output: String): ParsedStatus {
        if (output.isEmpty()) {
            return ParsedStatus(emptyList(), emptyList(), emptyList())
        }

        val changedPaths = LinkedHashSet<String>()
        val stagedPaths = LinkedHashSet<String>()
        val untrackedPaths = LinkedHashSet<String>()
        val entries = output.split(STATUS_SEPARATOR)

        var index = 0
        while (index < entries.size) {
            val entry = entries[index]
            if (entry.isNotEmpty() && !entry.startsWith(BRANCH_PREFIX) && entry.length >= MIN_STATUS_ENTRY_LENGTH) {
                val status = entry.substring(0, 2)
                val firstPath = entry.substring(3)
                addPathIfPresent(changedPaths, firstPath)
                val indexStatus = status.firstOrNull()
                if (indexStatus != null && indexStatus != ' ' && indexStatus != '?') {
                    addPathIfPresent(stagedPaths, firstPath)
                }
                if (status == UNTRACKED_STATUS) {
                    addPathIfPresent(untrackedPaths, firstPath)
                }
                if (isRenameOrCopy(status) && entries.getOrNull(index + 1).orEmpty().isNotEmpty()) {
                    index += 1
                }
            }
            index += 1
        }

        return ParsedStatus(
            changedPaths = changedPaths.toList(),
            stagedPaths = stagedPaths.toList(),
            untrackedPaths = untrackedPaths.toList(),
        )
    }

    /**
     * Returns whether [status] uses the extra old-path field in `-z` porcelain output.
     */
    private fun isRenameOrCopy(status: String): Boolean = status.firstOrNull() in RENAME_OR_COPY_STATUSES

    /**
     * Adds [path] when it is not blank.
     */
    private fun addPathIfPresent(
        paths: MutableSet<String>,
        path: String,
    ) {
        if (path.isNotEmpty()) {
            paths += path
        }
    }

    /**
     * Collects command output from [repository] and builds a successful or failed context.
     */
    private fun collectFromExistingRepository(repository: GitRepositoryHandle): GitContext {
        val status = runOrFailure(repository, STATUS_REQUEST, "status_failed")
        val staged = runOrFailure(repository, STAGED_DIFF_REQUEST, "staged_diff_failed", status.failure)
        val unstaged = runOrFailure(repository, UNSTAGED_DIFF_REQUEST, "unstaged_diff_failed", staged.failure)
        val failure = unstaged.failure
        return if (failure != null) {
            failure
        } else {
            val parsedStatus = parseStatusPorcelain(requireNotNull(status.result).output)
            val stagedDiff = diffLimit.apply(requireNotNull(staged.result).output)
            val unstagedDiff = diffLimit.apply(requireNotNull(unstaged.result).output)
            val state =
                if (parsedStatus.changedPaths.isEmpty() && stagedDiff.text.isBlank() && unstagedDiff.text.isBlank()) {
                    GitContextState.CLEAN
                } else {
                    GitContextState.CHANGED
                }
            GitContext(
                state = state,
                repositoryRoot = repository.rootPath,
                branchName = repository.branchName,
                changedFilePaths = parsedStatus.changedPaths,
                stagedFilePaths = parsedStatus.stagedPaths,
                untrackedFilePaths = parsedStatus.untrackedPaths,
                stagedDiff = stagedDiff.text,
                unstagedDiff = unstagedDiff.text,
                stagedDiffTruncated = stagedDiff.truncated,
                unstagedDiffTruncated = unstagedDiff.truncated,
            )
        }
    }

    /**
     * Runs [request], preserving [priorFailure] when an earlier command already failed.
     */
    private fun runOrFailure(
        repository: GitRepositoryHandle,
        request: GitCommandRequest,
        errorCode: String,
        priorFailure: GitContext? = null,
    ): CommandOutcome {
        if (priorFailure != null) {
            return CommandOutcome(result = null, failure = priorFailure)
        }
        val result = repository.run(request)
        val failure =
            if (result.success) {
                null
            } else {
                failedContext(repository = repository, errorCode = errorCode, request = request, result = result)
            }
        return CommandOutcome(result = result, failure = failure)
    }

    /**
     * Holds either a Git command result or a previously created failure context.
     */
    private data class CommandOutcome(
        val result: GitCommandExecutionResult?,
        val failure: GitContext?,
    )

    /**
     * Builds a failed context and emits a structured warning log.
     */
    private fun failedContext(
        repository: GitRepositoryHandle,
        errorCode: String,
        request: GitCommandRequest,
        result: GitCommandExecutionResult,
    ): GitContext {
        val context =
            GitContext(
                state = GitContextState.FAILED,
                repositoryRoot = repository.rootPath,
                branchName = repository.branchName,
                changedFilePaths = emptyList(),
                untrackedFilePaths = emptyList(),
                stagedDiff = "",
                unstagedDiff = "",
                stagedDiffTruncated = false,
                unstagedDiffTruncated = false,
                errorCode = errorCode,
            )
        logCollectionFailure(context, request, result)
        return context
    }

    /**
     * Logs a successful context collection with safe metadata only.
     */
    private fun logCollectionSuccess(context: GitContext) {
        logger.info(
            structuredEvent(
                event = "git_context_collect_success",
                metadata =
                    mapOf(
                        "state" to context.state.name,
                        "repository_present" to (context.repositoryRoot != null),
                        "branch" to context.branchName,
                        "changed_file_count" to context.changedFilePaths.size,
                        "untracked_file_count" to context.untrackedFilePaths.size,
                        "staged_diff_char_count" to context.stagedDiff.length,
                        "unstaged_diff_char_count" to context.unstagedDiff.length,
                        "staged_diff_truncated" to context.stagedDiffTruncated,
                        "unstaged_diff_truncated" to context.unstagedDiffTruncated,
                    ),
            ),
        )
    }

    /**
     * Logs a failed command execution with safe metadata only.
     */
    private fun logCollectionFailure(
        context: GitContext,
        request: GitCommandRequest,
        result: GitCommandExecutionResult,
    ) {
        logger.warn(
            structuredEvent(
                event = "git_context_collect_failure",
                metadata =
                    mapOf(
                        "error_code" to context.errorCode,
                        "repository_present" to (context.repositoryRoot != null),
                        "branch" to context.branchName,
                        "command" to request.command.name(),
                        "parameter_count" to request.parameters.size,
                        "exit_code" to result.exitCode,
                        "error_output_char_count" to result.errorOutput.length,
                    ),
            ),
        )
    }

    /**
     * Creates a stable key-value log message.
     */
    private fun structuredEvent(
        event: String,
        metadata: Map<String, Any?>,
    ): String {
        val metadataBody =
            metadata.entries.joinToString(separator = " ") { (key, value) ->
                "$key=${value ?: "null"}"
            }
        return "event=$event $metadataBody"
    }

    private data class ParsedStatus(
        val changedPaths: List<String>,
        val stagedPaths: List<String>,
        val untrackedPaths: List<String>,
    )

    private companion object {
        const val BRANCH_PREFIX = "## "
        const val MIN_STATUS_ENTRY_LENGTH = 3
        val RENAME_OR_COPY_STATUSES = setOf('R', 'C')
        const val UNTRACKED_STATUS = "??"
        const val STATUS_SEPARATOR = '\u0000'

        val STATUS_REQUEST =
            GitCommandRequest(
                command = GitCommand.STATUS,
                parameters = listOf("--porcelain=v1", "-z", "--branch", "--untracked-files=all"),
            )
        val STAGED_DIFF_REQUEST =
            GitCommandRequest(
                command = GitCommand.DIFF,
                parameters = listOf("--cached"),
            )
        val UNSTAGED_DIFF_REQUEST =
            GitCommandRequest(
                command = GitCommand.DIFF,
                parameters = emptyList(),
            )
    }
}
