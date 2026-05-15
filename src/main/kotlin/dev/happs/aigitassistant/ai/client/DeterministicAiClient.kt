package dev.happs.aigitassistant.ai.client

import dev.happs.aigitassistant.git.GitContext
import dev.happs.aigitassistant.git.GitContextState
import dev.happs.aigitassistant.prompt.AssistantRequest
import dev.happs.aigitassistant.prompt.AssistantRequestKind
import dev.happs.aigitassistant.prompt.CommitMessageStyle
import java.util.Locale

/**
 * Offline AI client that produces stable suggestions from structured Git context.
 */
class DeterministicAiClient : AiClient {
    /**
     * Generates deterministic editable text for [request] without network access.
     */
    override fun generate(request: AssistantRequest): AiResponse {
        val text =
            when (request.gitContext.state) {
                GitContextState.NO_REPOSITORY -> "No Git repository was detected for this project."
                GitContextState.FAILED -> failedText(request.gitContext)
                GitContextState.CLEAN -> "No pending changes were detected."
                GitContextState.CHANGED -> changedText(request)
            }
        return AiResponse(
            generatedText = text,
            kind = request.kind,
            source = AiResponseSource.DETERMINISTIC,
        )
    }

    /**
     * Builds output for changed repositories based on the requested assistant task.
     */
    private fun changedText(request: AssistantRequest): String =
        when (request.kind) {
            AssistantRequestKind.COMMIT_MESSAGE -> commitMessage(request)
            AssistantRequestKind.BRANCH_NAME -> branchNames(request)
            AssistantRequestKind.CHANGE_SUMMARY -> changeSummary(request.gitContext)
        }

    /**
     * Builds a user-readable failure response without exposing raw command output.
     */
    private fun failedText(context: GitContext): String {
        val error = context.errorCode ?: "unknown_error"
        return "Git context collection failed ($error). Please refresh Git status and try again."
    }

    /**
     * Builds a commit message for the selected style.
     */
    private fun commitMessage(request: AssistantRequest): String {
        val topic = readableTopic(request)
        return when (request.options.commitMessageStyle) {
            CommitMessageStyle.CONCISE -> "Update $topic"
            CommitMessageStyle.CONVENTIONAL_COMMIT -> "chore: update ${topic.lowercase(Locale.US)}"
            CommitMessageStyle.DETAILED ->
                buildString {
                    appendLine("Update $topic")
                    changedAreas(request.gitContext).forEach { area ->
                        appendLine("- Update $area")
                    }
                }.trimEnd()
        }
    }

    /**
     * Builds count-limited branch-name suggestions in lowercase kebab-case.
     */
    private fun branchNames(request: AssistantRequest): String =
        branchNameCandidates(request)
            .distinct()
            .take(request.options.branchSuggestionCount)
            .joinToString(separator = "\n")

    /**
     * Builds a concise change summary with risk and testing guidance.
     */
    private fun changeSummary(context: GitContext): String =
        buildString {
            appendLine("Summary")
            appendLine("- Updates ${changedAreas(context).joinToString(separator = ", ")}.")
            appendLine()
            appendLine("Risks")
            if (context.stagedDiffTruncated || context.unstagedDiffTruncated) {
                appendLine("- Review changed behavior around the touched files and truncated diff sections.")
            } else {
                appendLine("- Review changed behavior around the touched files.")
            }
            appendLine()
            appendLine("Suggested tests")
            appendLine("- Run focused tests for the changed areas.")
            append("- Run the repository quality checks before committing.")
        }
}

/**
 * Derives branch candidates from note and changed-file topics.
 */
private fun branchNameCandidates(request: AssistantRequest): List<String> {
    val context = request.gitContext
    val candidates = mutableListOf<String>()
    request.options.userNote
        ?.toSlug()
        ?.let(candidates::add)
    context.changedFilePaths.mapTo(candidates) { it.toTopicSlug(prefix = "update") }
    context.untrackedFilePaths.mapTo(candidates) { it.toTopicSlug(prefix = "add") }
    candidates += "update-${primaryTopic(context).toSlug()}"
    candidates += "work-on-${primaryTopic(context).toSlug()}"
    candidates += "revise-${primaryTopic(context).toSlug()}"
    return candidates.mapNotNull { it.takeIf(String::isNotBlank) }
}

/**
 * Builds a readable topic from the user note or changed files.
 */
private fun readableTopic(request: AssistantRequest): String =
    request.options.userNote
        ?: primaryTopic(request.gitContext)

/**
 * Finds the main topic represented by [context].
 */
private fun primaryTopic(context: GitContext): String =
    context.changedFilePaths.firstOrNull()?.fileStem()
        ?: context.untrackedFilePaths.firstOrNull()?.fileStem()
        ?: "changes"

/**
 * Produces readable changed-area names for summaries and detailed commits.
 */
private fun changedAreas(context: GitContext): List<String> {
    val paths =
        (context.changedFilePaths + context.untrackedFilePaths)
            .map { it.fileStem() }
            .filter { it.isNotBlank() }
            .distinct()
    return paths.ifEmpty { listOf("changes") }
}

/**
 * Converts a path-like string into a readable file stem.
 */
private fun String.fileStem(): String =
    substringAfterLast('/')
        .substringBeforeLast('.', missingDelimiterValue = substringAfterLast('/'))
        .split(CAMEL_CASE_BOUNDARY)
        .joinToString(separator = " ")
        .trim()
        .ifEmpty { "changes" }

/**
 * Converts path-like text to a branch topic slug with [prefix].
 */
private fun String.toTopicSlug(prefix: String): String {
    val slug = fileStem().toSlug()
    return if (slug.isBlank()) prefix else "$prefix-$slug"
}

/**
 * Converts arbitrary text to lowercase kebab-case.
 */
private fun String.toSlug(): String =
    split(CAMEL_CASE_BOUNDARY)
        .joinToString(separator = " ")
        .lowercase(Locale.US)
        .replace(NON_ALPHANUMERIC, "-")
        .trim('-')
        .replace(REPEATED_DASHES, "-")

private val CAMEL_CASE_BOUNDARY = Regex("(?<=[a-z0-9])(?=[A-Z])")
private val NON_ALPHANUMERIC = Regex("[^a-z0-9]+")
private val REPEATED_DASHES = Regex("-+")
