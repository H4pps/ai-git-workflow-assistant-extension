package dev.happs.aigitassistant.ai.prompt

/**
 * Builds task-specific output instructions for model system prompts.
 */
class OutputContractBuilder {
    /**
     * Builds the complete output contract for [options].
     */
    fun build(options: AssistantOptions): String =
        buildString {
            appendSection("OUTPUT_CONTRACT")
            appendLine("When USER_NOTE is not (none), apply it as task-specific user guidance.")
            when (options.requestKind) {
                AssistantRequestKind.COMMIT_MESSAGE -> appendCommitMessageContract()
                AssistantRequestKind.BRANCH_NAME -> appendBranchNameContract(options)
                AssistantRequestKind.CHANGE_SUMMARY -> appendChangeSummaryContract()
            }
        }

    private fun StringBuilder.appendSection(label: String) {
        if (isNotEmpty()) {
            appendLine()
        }
        appendLine("[$label]")
    }

    private fun StringBuilder.appendCommitMessageContract() {
        appendLine("Return exactly one Git commit message.")
        appendLine("Use Conventional Commits 1.0.0.")
        appendConventionalCommitRules()
        appendCommitMessageExample()
        appendLine("Do not include headings, bullets, summaries, risks, rationale, or markdown.")
        appendLine("Never return a change summary for this request.")
    }

    private fun StringBuilder.appendBranchNameContract(options: AssistantOptions) {
        appendLine(
            "Return only ${options.branchSuggestionCount} bare lowercase kebab-case branch names, " +
                "one per line.",
        )
        appendLine("Do not include bullets, numbering, explanations, summaries, or markdown.")
        appendLine("Never return a change summary for this request.")
        appendLine("Expected response shape example:")
        appendLine("update-provider-settings")
        appendLine("fix-commit-generation")
        appendLine("add-staged-only-context")
    }

    private fun StringBuilder.appendChangeSummaryContract() {
        appendLine("Return only the sections: Summary, Risks, and Suggested tests.")
        appendLine("Expected response shape example:")
        appendLine("Summary")
        appendLine("- <one or two bullets>")
        appendLine()
        appendLine("Risks")
        appendLine("- <one or two bullets>")
        appendLine()
        appendLine("Suggested tests")
        appendLine("- <one or two bullets>")
    }

    private fun StringBuilder.appendCommitMessageExample() {
        appendLine("Expected response shape example:")
        appendLine("chore(settings): update provider defaults")
    }

    private fun StringBuilder.appendConventionalCommitRules() {
        appendLine("Follow Conventional Commits 1.0.0.")
        appendLine("Use this header format: <type>[optional scope][!]: <description>.")
        appendLine("Use feat for new features and fix for bug fixes.")
        appendLine(
            "Allowed supporting types include build, chore, ci, docs, style, refactor, perf, and test.",
        )
        appendLine("Use an optional noun scope in parentheses when it clarifies the changed area.")
        appendLine("Use ! before the colon for breaking API changes.")
        appendLine("Use BREAKING CHANGE: in a footer when a breaking change needs explanation.")
        appendLine("Add a body or footer only when the diff needs extra context.")
    }
}
