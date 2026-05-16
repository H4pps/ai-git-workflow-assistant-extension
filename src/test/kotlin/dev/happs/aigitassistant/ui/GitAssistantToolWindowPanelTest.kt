package dev.happs.aigitassistant.ui

import com.intellij.openapi.project.Project
import dev.happs.aigitassistant.ai.client.AiResponseSource
import dev.happs.aigitassistant.ai.prompt.AssistantRequestKind
import dev.happs.aigitassistant.git.GitContextState
import dev.happs.aigitassistant.service.GitAssistantResult
import dev.happs.aigitassistant.service.GitAssistantService
import dev.happs.aigitassistant.service.GitAssistantToolWindowService
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GitAssistantToolWindowPanelTest {
    @Test
    fun `action preselection updates request kind`() {
        val panel = panel()

        panel.selectRequestKind(AssistantRequestKind.BRANCH_NAME)

        assertEquals(AssistantRequestKind.BRANCH_NAME, panel.selectedOptionsForTesting().requestKind)
    }

    @Test
    fun `selected options include trimmed task note and staged only scope`() {
        val panel = panel()

        panel.selectRequestKind(AssistantRequestKind.COMMIT_MESSAGE)
        panel.setTaskNoteForTesting("  tighten output wording  ")
        panel.setStagedOnlyForTesting(true)

        val options = panel.selectedOptionsForTesting()

        assertEquals(AssistantRequestKind.COMMIT_MESSAGE, options.requestKind)
        assertEquals("tighten output wording", options.userNote)
        assertTrue(options.stagedOnly)
    }

    @Test
    fun `apply result renders editable output and context summary`() {
        val panel = panel()

        panel.applyResultForTesting(
            GitAssistantResult(
                title = "Change Summary",
                generatedText = "Summary\n- Update tool window.",
                requestKind = AssistantRequestKind.CHANGE_SUMMARY,
                source = AiResponseSource.OPENAI_COMPATIBLE,
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
                "Source: OPENAI_COMPATIBLE",
            ),
            panel.summaryTextForTesting(),
        )
    }

    @Test
    fun `configure ai button invokes configured action`() {
        var configureInvocations = 0
        val panel =
            panel(
                onConfigureAi = { configureInvocations += 1 },
            )

        panel.triggerConfigureAiForTesting()

        assertEquals(1, configureInvocations)
    }

    private fun panel(onConfigureAi: () -> Unit = {}): GitAssistantToolWindowPanel =
        GitAssistantToolWindowPanel(
            project = fakeProject(),
            assistantService = GitAssistantService(),
            toolWindowService = GitAssistantToolWindowService(),
            configureAiAction = onConfigureAi,
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
