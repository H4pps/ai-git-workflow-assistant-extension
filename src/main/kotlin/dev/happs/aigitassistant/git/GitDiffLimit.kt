package dev.happs.aigitassistant.git

const val DEFAULT_DIFF_PREFIX_CHARACTERS = 4_000

/**
 * Applies a deterministic kept-prefix limit to Git diff text.
 */
data class GitDiffLimit(
    val maxPrefixCharacters: Int = DEFAULT_DIFF_PREFIX_CHARACTERS,
) {
    init {
        require(maxPrefixCharacters >= 0) { "maxPrefixCharacters must be non-negative." }
    }

    /**
     * Truncates [diff] when it exceeds [maxPrefixCharacters], appending a stable suffix.
     */
    fun apply(diff: String): GitDiffLimitResult {
        if (diff.length <= maxPrefixCharacters) {
            return GitDiffLimitResult(
                text = diff,
                truncated = false,
                originalLength = diff.length,
            )
        }

        val keptPrefix = diff.take(maxPrefixCharacters)
        val omittedCount = diff.length - maxPrefixCharacters
        return GitDiffLimitResult(
            text = "$keptPrefix\n...[truncated $omittedCount chars]",
            truncated = true,
            originalLength = diff.length,
        )
    }
}

/**
 * Result of applying [GitDiffLimit] to a diff text.
 */
data class GitDiffLimitResult(
    val text: String,
    val truncated: Boolean,
    val originalLength: Int,
)
