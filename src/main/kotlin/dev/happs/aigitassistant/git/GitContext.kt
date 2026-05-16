package dev.happs.aigitassistant.git

/**
 * Repository context used by the assistant to build Git-aware prompts.
 */
data class GitContext(
    val state: GitContextState,
    val repositoryRoot: String?,
    val branchName: String?,
    val changedFilePaths: List<String>,
    val stagedFilePaths: List<String> = changedFilePaths,
    val untrackedFilePaths: List<String>,
    val stagedDiff: String,
    val unstagedDiff: String,
    val stagedDiffTruncated: Boolean,
    val unstagedDiffTruncated: Boolean,
    val errorCode: String? = null,
)

/**
 * Collection outcome for [GitContextCollector].
 */
enum class GitContextState {
    NO_REPOSITORY,
    CLEAN,
    CHANGED,
    FAILED,
}
