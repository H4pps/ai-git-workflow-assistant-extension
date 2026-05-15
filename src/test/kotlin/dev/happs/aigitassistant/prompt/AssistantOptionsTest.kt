package dev.happs.aigitassistant.prompt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class AssistantOptionsTest {
    @Test
    fun `defaults commit style and branch count`() {
        val options = AssistantOptions(requestKind = AssistantRequestKind.COMMIT_MESSAGE)

        assertEquals(CommitMessageStyle.CONVENTIONAL_COMMIT, options.commitMessageStyle)
        assertEquals(3, options.branchSuggestionCount)
    }

    @Test
    fun `trims user note and converts blank note to null`() {
        val trimmed =
            AssistantOptions(
                requestKind = AssistantRequestKind.BRANCH_NAME,
                userNote = "  improve naming  ",
            )
        val blank =
            AssistantOptions(
                requestKind = AssistantRequestKind.BRANCH_NAME,
                userNote = "   ",
            )

        assertEquals("improve naming", trimmed.userNote)
        assertNull(blank.userNote)
    }

    @Test
    fun `requires positive branch suggestion count`() {
        assertFailsWith<IllegalArgumentException> {
            AssistantOptions(
                requestKind = AssistantRequestKind.BRANCH_NAME,
                branchSuggestionCount = 0,
            )
        }
    }
}
