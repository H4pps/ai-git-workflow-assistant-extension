package dev.happs.aigitassistant.ai.prompt

import dev.happs.aigitassistant.git.GitContext
import dev.happs.aigitassistant.git.GitContextState

/**
 * Builds assistant requests from Git context and user options.
 */
class PromptBuilder {
    /**
     * Builds a complete [AssistantRequest] with stable sectioned prompt text.
     */
    fun build(
        context: GitContext,
        options: AssistantOptions,
    ): AssistantRequest {
        val reasoningContext = context.reasoningContextFor(options)
        val metadata =
            AssistantRequestSafeMetadata(
                contextState = reasoningContext.state,
                repositoryPresent = reasoningContext.repositoryRoot != null,
                branchPresent = reasoningContext.branchName != null,
                changedFileCount = reasoningContext.changedFilePaths.size,
                untrackedFileCount = reasoningContext.untrackedFilePaths.size,
                stagedDiffCharCount = reasoningContext.stagedDiff.length,
                unstagedDiffCharCount = reasoningContext.unstagedDiff.length,
                stagedDiffTruncated = reasoningContext.stagedDiffTruncated,
                unstagedDiffTruncated = reasoningContext.unstagedDiffTruncated,
                hasUserNote = options.userNote != null,
                userNoteCharCount = options.userNote?.length ?: 0,
                branchSuggestionCount = options.branchSuggestionCount,
                requestKind = options.requestKind,
                commitMessageStyle = options.commitMessageStyle,
                stagedOnly = options.stagedOnly,
                errorCode = reasoningContext.errorCode,
            )

        return AssistantRequest(
            kind = options.requestKind,
            options = options,
            promptText = buildPromptText(reasoningContext, options),
            gitContext = context,
            safeMetadata = metadata,
            reasoningContext = reasoningContext,
        )
    }

    private fun buildPromptText(
        context: GitContext,
        options: AssistantOptions,
    ): String =
        buildString {
            appendSection("REQUEST")
            appendLine("kind=${options.requestKind.name}")
            appendLine("commit_message_style=${options.commitMessageStyle.name}")
            appendLine("branch_suggestion_count=${options.branchSuggestionCount}")

            appendSection("INPUT_SCOPE")
            appendInputScope(options)

            appendSection("STATE")
            appendLine("state=${context.state.name}")
            appendLine("repository_present=${context.repositoryRoot != null}")
            appendLine("error_code=${context.errorCode ?: "none"}")

            appendSection("BRANCH")
            appendLine("name=${context.branchName ?: "(none)"}")

            appendSection("USER_NOTE")
            appendLine(options.userNote ?: "(none)")

            appendSection("CHANGED_FILES")
            appendList(context.changedFilePaths)

            appendSection("UNTRACKED_FILES")
            appendList(context.untrackedFilePaths)

            appendSection("STAGED_DIFF")
            appendLine(context.stagedDiff.ifBlank { "(empty)" })

            appendSection("UNSTAGED_DIFF")
            appendLine(context.unstagedDiff.ifBlank { "(empty)" })

            appendSection("TRUNCATION_NOTES")
            appendLine("staged_diff_truncated=${context.stagedDiffTruncated}")
            appendLine("unstaged_diff_truncated=${context.unstagedDiffTruncated}")
        }

    private fun StringBuilder.appendSection(label: String) {
        if (isNotEmpty()) {
            appendLine()
        }
        appendLine("[$label]")
    }

    private fun StringBuilder.appendList(paths: List<String>) {
        if (paths.isEmpty()) {
            appendLine("- (none)")
            return
        }
        paths.take(MAX_RENDERED_PATHS).forEach { path ->
            appendLine("- $path")
        }
        val omittedCount = paths.size - MAX_RENDERED_PATHS
        if (omittedCount > 0) {
            appendLine("...[omitted $omittedCount paths]")
        }
    }

    private fun StringBuilder.appendInputScope(options: AssistantOptions) {
        appendLine("reasoning_scope=${if (options.stagedOnly) "STAGED_ONLY" else "ALL_CHANGES"}")
        if (options.stagedOnly) {
            appendLine("Use only staged files and the staged diff.")
            appendLine("Ignore unstaged and untracked changes.")
        } else {
            appendLine("Use staged, unstaged, and untracked changes.")
        }
    }

    private fun GitContext.reasoningContextFor(options: AssistantOptions): GitContext {
        if (!options.stagedOnly) {
            return this
        }
        val hasStagedChanges = stagedFilePaths.isNotEmpty() || stagedDiff.isNotBlank()
        return copy(
            state = if (state == GitContextState.CHANGED && !hasStagedChanges) GitContextState.CLEAN else state,
            changedFilePaths = stagedFilePaths,
            untrackedFilePaths = emptyList(),
            unstagedDiff = "",
            unstagedDiffTruncated = false,
        )
    }

    private companion object {
        const val MAX_RENDERED_PATHS = 50
    }
}
