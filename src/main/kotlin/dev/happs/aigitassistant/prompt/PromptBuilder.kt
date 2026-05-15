package dev.happs.aigitassistant.prompt

import dev.happs.aigitassistant.git.GitContext

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
        val metadata =
            AssistantRequestSafeMetadata(
                contextState = context.state,
                repositoryPresent = context.repositoryRoot != null,
                branchPresent = context.branchName != null,
                changedFileCount = context.changedFilePaths.size,
                untrackedFileCount = context.untrackedFilePaths.size,
                stagedDiffCharCount = context.stagedDiff.length,
                unstagedDiffCharCount = context.unstagedDiff.length,
                stagedDiffTruncated = context.stagedDiffTruncated,
                unstagedDiffTruncated = context.unstagedDiffTruncated,
                hasUserNote = options.userNote != null,
                userNoteCharCount = options.userNote?.length ?: 0,
                branchSuggestionCount = options.branchSuggestionCount,
                requestKind = options.requestKind,
                commitMessageStyle = options.commitMessageStyle,
                errorCode = context.errorCode,
            )

        return AssistantRequest(
            kind = options.requestKind,
            options = options,
            promptText = buildPromptText(context, options),
            gitContext = context,
            safeMetadata = metadata,
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

            appendSection("OUTPUT_INSTRUCTIONS")
            appendInstructions(options)

            appendSection("STATE")
            appendLine("state=${context.state.name}")
            appendLine("repository_present=${context.repositoryRoot != null}")
            appendLine("error_code=${context.errorCode ?: "none"}")

            appendSection("BRANCH")
            appendLine("name=${context.branchName ?: "(none)"}")

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

            appendSection("USER_NOTE")
            appendLine(options.userNote ?: "(none)")
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
        paths.forEach { path ->
            appendLine("- $path")
        }
    }

    private fun StringBuilder.appendInstructions(options: AssistantOptions) {
        when (options.requestKind) {
            AssistantRequestKind.COMMIT_MESSAGE -> {
                appendLine("Generate an editable Git commit message.")
                appendLine("Use the selected commit message style: ${options.commitMessageStyle.name}.")
            }
            AssistantRequestKind.BRANCH_NAME -> {
                appendLine("Suggest ${options.branchSuggestionCount} branch names.")
                appendLine("Use bare lowercase kebab-case names without a type prefix.")
            }
            AssistantRequestKind.CHANGE_SUMMARY -> {
                appendLine("Summarize behavior changes, risks, and suggested tests.")
                appendLine("Use the sections: Summary, Risks, Suggested tests.")
            }
        }
    }
}
