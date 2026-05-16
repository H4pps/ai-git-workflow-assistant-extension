package dev.happs.aigitassistant.ui

import com.intellij.openapi.project.Project
import dev.happs.aigitassistant.ai.client.AiResponseSource
import dev.happs.aigitassistant.git.GitContextState
import dev.happs.aigitassistant.prompt.AssistantRequestKind
import dev.happs.aigitassistant.prompt.CommitMessageStyle
import dev.happs.aigitassistant.service.GitAssistantResult
import dev.happs.aigitassistant.service.GitAssistantToolWindowService
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GitAssistantToolWindowPanelTest {
    @Test
    fun `action preselection updates request kind and commit style visibility`() {
        val panel = panel()

        panel.selectRequestKind(AssistantRequestKind.BRANCH_NAME)

        assertEquals(AssistantRequestKind.BRANCH_NAME, panel.selectedOptionsForTesting().requestKind)
        assertFalse(panel.isCommitStyleVisibleForTesting())
    }

    @Test
    fun `selected options include commit style and trimmed task note`() {
        val panel = panel()

        panel.selectRequestKind(AssistantRequestKind.COMMIT_MESSAGE)
        panel.selectCommitStyleForTesting(CommitMessageStyle.DETAILED)
        panel.setTaskNoteForTesting("  tighten output wording  ")

        val options = panel.selectedOptionsForTesting()

        assertEquals(AssistantRequestKind.COMMIT_MESSAGE, options.requestKind)
        assertEquals(CommitMessageStyle.DETAILED, options.commitMessageStyle)
        assertEquals("tighten output wording", options.userNote)
        assertTrue(panel.isCommitStyleVisibleForTesting())
    }

    @Test
    fun `apply result renders editable output and context summary`() {
        val panel = panel()

        panel.applyResultForTesting(
            GitAssistantResult(
                title = "Change Summary",
                generatedText = "Summary\n- Update tool window.",
                requestKind = AssistantRequestKind.CHANGE_SUMMARY,
                source = AiResponseSource.DETERMINISTIC,
                gitState = GitContextState.CHANGED,
                branchName = "feature/right-pane",
                changedFileCount = 2,
                untrackedFileCount = 1,
            ),
        )

        assertEquals("Summary\n- Update tool window.", panel.outputTextForTesting())
        assertEquals(
            listOf(
                "Branch: feature/right-pane",
                "State: CHANGED",
                "Changed files: 2",
                "Untracked files: 1",
                "Source: DETERMINISTIC",
            ),
            panel.summaryTextForTesting(),
        )
    }

    private fun panel(): GitAssistantToolWindowPanel =
        GitAssistantToolWindowPanel(
            project = fakeProject(),
            toolWindowService = GitAssistantToolWindowService(),
        )

    private fun fakeProject(): Project =
        Proxy
            .newProxyInstance(
                Project::class.java.classLoader,
                arrayOf(Project::class.java),
            ) { _, method, _ ->
                when (method.name) {
                    "isDisposed" -> false
                    "isOpen" -> true
                    "getName" -> "fake-project"
                    "getBasePath" -> null
                    "toString" -> "FakeProject"
                    else -> defaultValue(method.returnType)
                }
            } as Project

    private fun defaultValue(returnType: Class<*>): Any? =
        when (returnType) {
            Boolean::class.javaPrimitiveType -> false
            Int::class.javaPrimitiveType -> 0
            Long::class.javaPrimitiveType -> 0L
            Float::class.javaPrimitiveType -> 0f
            Double::class.javaPrimitiveType -> 0.0
            Short::class.javaPrimitiveType -> 0.toShort()
            Byte::class.javaPrimitiveType -> 0.toByte()
            Char::class.javaPrimitiveType -> 0.toChar()
            else -> null
        }
}
