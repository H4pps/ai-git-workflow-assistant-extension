package dev.happs.aigitassistant.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindowManager
import dev.happs.aigitassistant.prompt.AssistantRequestKind
import dev.happs.aigitassistant.service.GitAssistantToolWindowService
import dev.happs.aigitassistant.ui.GitAssistantToolWindowFactory

/**
 * Base action that focuses the assistant tool window and preselects a request kind.
 */
abstract class AssistantRequestAction(
    private val requestKind: AssistantRequestKind,
) : AnAction(),
    DumbAware {
    /**
     * Enables the action only when a project is open.
     */
    override fun update(event: AnActionEvent) {
        event.presentation.isEnabled = event.project != null
    }

    /**
     * Keeps action updates off the UI thread because update only reads project availability.
     */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        if (project == null) {
            Messages.showErrorDialog("Open a project before using the assistant.", "AI Git Workflow Assistant")
            return
        }

        project.getService(GitAssistantToolWindowService::class.java).requestKindSelection(requestKind)
        val toolWindow =
            ToolWindowManager
                .getInstance(project)
                .getToolWindow(GitAssistantToolWindowFactory.TOOL_WINDOW_ID)
        if (toolWindow == null) {
            Messages.showErrorDialog(
                project,
                "Could not open the AI Git tool window.",
                "AI Git Workflow Assistant",
            )
            return
        }
        toolWindow.activate(null, true)
    }
}
