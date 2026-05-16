package dev.happs.aigitassistant.ai.prompt

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

class OutputContractBuilderTest {
    private val builder = OutputContractBuilder()

    @Test
    fun `commit contract includes selected style and conventional commits rules`() {
        val contract =
            builder.build(
                AssistantOptions(
                    requestKind = AssistantRequestKind.COMMIT_MESSAGE,
                    commitMessageStyle = CommitMessageStyle.CONVENTIONAL_COMMIT,
                ),
            )

        assertContains(contract, "[OUTPUT_CONTRACT]")
        assertContains(contract, "Return exactly one Git commit message.")
        assertContains(contract, "Use the selected commit message style: CONVENTIONAL_COMMIT.")
        assertContains(contract, "Follow Conventional Commits 1.0.0.")
        assertContains(contract, "<type>[optional scope][!]: <description>")
        assertContains(contract, "Use feat for new features and fix for bug fixes.")
        assertContains(contract, "Use BREAKING CHANGE:")
        assertContains(contract, "Expected response shape example:")
        assertContains(contract, "chore(settings): update provider defaults")
        assertContains(contract, "Never return a change summary for this request.")
    }

    @Test
    fun `branch contract includes count and branch examples`() {
        val contract =
            builder.build(
                AssistantOptions(
                    requestKind = AssistantRequestKind.BRANCH_NAME,
                    branchSuggestionCount = 4,
                ),
            )

        assertContains(contract, "Return only 4 bare lowercase kebab-case branch names, one per line.")
        assertContains(contract, "Do not include bullets, numbering, explanations, summaries, or markdown.")
        assertContains(contract, "update-provider-settings")
        assertContains(contract, "fix-commit-generation")
        assertContains(contract, "add-staged-only-context")
        assertFalse(contract.contains("Summary, Risks, and Suggested tests"))
    }

    @Test
    fun `summary contract includes only summary sections`() {
        val contract = builder.build(AssistantOptions(requestKind = AssistantRequestKind.CHANGE_SUMMARY))

        assertContains(contract, "Return only the sections: Summary, Risks, and Suggested tests.")
        assertContains(contract, "Summary")
        assertContains(contract, "Risks")
        assertContains(contract, "Suggested tests")
        assertFalse(contract.contains("Return exactly one Git commit message."))
        assertFalse(contract.contains("bare lowercase kebab-case branch names"))
    }
}
