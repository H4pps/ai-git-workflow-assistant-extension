package dev.happs.aigitassistant.action

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.Messages

/**
 * Opens a minimal placeholder dialog for the AI Git Workflow Assistant action group.
 */
class ShowAssistantPlaceholderAction :
    AnAction(),
    DumbAware {
    /**
     * Shows a short message confirming that the plugin action is registered.
     */
    override fun actionPerformed(event: AnActionEvent) {
        Messages.showInfoMessage(
            event.project,
            "AI Git Workflow Assistant is installed. Git and AI features will be added in later phases.",
            "AI Git Workflow Assistant",
        )
    }
}
