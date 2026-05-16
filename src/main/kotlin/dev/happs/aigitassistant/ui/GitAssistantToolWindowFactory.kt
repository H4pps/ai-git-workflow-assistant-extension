package dev.happs.aigitassistant.ui

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Registers the AI Git tool window content.
 */
class GitAssistantToolWindowFactory :
    ToolWindowFactory,
    DumbAware {
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        val panel = GitAssistantToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        content.setPreferredFocusableComponent(panel)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }

    companion object {
        const val TOOL_WINDOW_ID = "AI Git"
    }
}
