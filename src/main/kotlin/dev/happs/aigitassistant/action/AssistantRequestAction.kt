package dev.happs.aigitassistant.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import dev.happs.aigitassistant.prompt.AssistantOptions
import dev.happs.aigitassistant.service.GitAssistantResult
import dev.happs.aigitassistant.service.GitAssistantService
import dev.happs.aigitassistant.ui.GitAssistantDialog

/**
 * Base action that generates an assistant suggestion and opens it in an editable dialog.
 */
abstract class AssistantRequestAction(
    private val options: AssistantOptions,
    private val backgroundTitle: String,
    private val service: GitAssistantService = GitAssistantService(),
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

    /**
     * Runs generation in the background and opens an editable result dialog.
     */
    override fun actionPerformed(event: AnActionEvent) {
        val project = event.project
        if (project == null) {
            Messages.showErrorDialog("Open a project before using the assistant.", "AI Git Workflow Assistant")
            return
        }

        ProgressManager.getInstance().run(GenerationTask(project, backgroundTitle, options, service))
    }

    /**
     * Background task for Git collection and deterministic generation.
     */
    private class GenerationTask(
        project: Project,
        title: String,
        private val options: AssistantOptions,
        private val service: GitAssistantService,
    ) : Task.Backgroundable(project, title, false) {
        private var generationResult: Result<GitAssistantResult>? = null

        /**
         * Generates the suggestion away from the UI thread.
         */
        override fun run(indicator: ProgressIndicator) {
            indicator.text = "Collecting Git context"
            generationResult = runCatching { service.generate(project, options) }
        }

        /**
         * Shows the generated result or a clear error message.
         */
        override fun onSuccess() {
            val result = generationResult ?: return
            ApplicationManager.getApplication().invokeLater {
                result.fold(
                    onSuccess = { GitAssistantDialog(project, it).show() },
                    onFailure = { error ->
                        Messages.showErrorDialog(
                            project,
                            error.message ?: "Could not generate assistant output.",
                            "AI Git Workflow Assistant",
                        )
                    },
                )
            }
        }
    }
}
