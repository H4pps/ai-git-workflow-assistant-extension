package dev.happs.aigitassistant.prompt

/**
 * User-selected options for a single assistant request.
 */
class AssistantOptions(
    val requestKind: AssistantRequestKind,
    val commitMessageStyle: CommitMessageStyle = CommitMessageStyle.CONVENTIONAL_COMMIT,
    userNote: String? = null,
    val branchSuggestionCount: Int = DEFAULT_BRANCH_SUGGESTION_COUNT,
) {
    val userNote: String? = userNote?.trim()?.takeIf { it.isNotEmpty() }

    init {
        require(branchSuggestionCount > 0) { "branchSuggestionCount must be positive." }
    }

    companion object {
        const val DEFAULT_BRANCH_SUGGESTION_COUNT = 3
    }
}
