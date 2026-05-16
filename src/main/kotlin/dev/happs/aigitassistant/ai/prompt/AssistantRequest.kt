package dev.happs.aigitassistant.ai.prompt

import dev.happs.aigitassistant.git.GitContext
import dev.happs.aigitassistant.git.GitContextState

/**
 * Fully built request payload consumed by [dev.happs.aigitassistant.ai.client.AiClient].
 */
data class AssistantRequest(
    val kind: AssistantRequestKind,
    val options: AssistantOptions,
    val promptText: String,
    val gitContext: GitContext,
    val safeMetadata: AssistantRequestSafeMetadata,
    val reasoningContext: GitContext = gitContext,
)

/**
 * Safe request metadata for logging and diagnostics.
 */
data class AssistantRequestSafeMetadata(
    val contextState: GitContextState,
    val repositoryPresent: Boolean,
    val branchPresent: Boolean,
    val changedFileCount: Int,
    val untrackedFileCount: Int,
    val stagedDiffCharCount: Int,
    val unstagedDiffCharCount: Int,
    val stagedDiffTruncated: Boolean,
    val unstagedDiffTruncated: Boolean,
    val hasUserNote: Boolean,
    val userNoteCharCount: Int,
    val branchSuggestionCount: Int,
    val requestKind: AssistantRequestKind,
    val commitMessageStyle: CommitMessageStyle,
    val stagedOnly: Boolean,
    val errorCode: String?,
) {
    /**
     * Converts this metadata object to a map suitable for structured logging.
     */
    fun toMap(): Map<String, Any?> =
        linkedMapOf(
            "context_state" to contextState.name,
            "repository_present" to repositoryPresent,
            "branch_present" to branchPresent,
            "changed_file_count" to changedFileCount,
            "untracked_file_count" to untrackedFileCount,
            "staged_diff_char_count" to stagedDiffCharCount,
            "unstaged_diff_char_count" to unstagedDiffCharCount,
            "staged_diff_truncated" to stagedDiffTruncated,
            "unstaged_diff_truncated" to unstagedDiffTruncated,
            "has_user_note" to hasUserNote,
            "user_note_char_count" to userNoteCharCount,
            "branch_suggestion_count" to branchSuggestionCount,
            "request_kind" to requestKind.name,
            "commit_message_style" to commitMessageStyle.name,
            "staged_only" to stagedOnly,
            "error_code" to errorCode,
        )
}
