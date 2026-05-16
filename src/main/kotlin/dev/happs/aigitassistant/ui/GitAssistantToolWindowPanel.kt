package dev.happs.aigitassistant.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import dev.happs.aigitassistant.ai.prompt.AssistantOptions
import dev.happs.aigitassistant.ai.prompt.AssistantRequestKind
import dev.happs.aigitassistant.ai.prompt.CommitMessageStyle
import dev.happs.aigitassistant.service.GitAssistantResult
import dev.happs.aigitassistant.service.GitAssistantService
import dev.happs.aigitassistant.service.GitAssistantToolWindowService
import dev.happs.aigitassistant.settings.AiProviderSettingsConfigurable
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import java.awt.GridLayout
import java.awt.datatransfer.StringSelection
import javax.swing.BorderFactory
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Primary tool window UI for assistant generation and editable output.
 */
@Suppress("TooManyFunctions")
class GitAssistantToolWindowPanel(
    private val project: Project,
    private val assistantService: GitAssistantService = project.getService(GitAssistantService::class.java),
    private val toolWindowService: GitAssistantToolWindowService =
        project.getService(GitAssistantToolWindowService::class.java),
    private val configureAiAction: () -> Unit = {
        ShowSettingsUtil.getInstance().showSettingsDialog(project, AiProviderSettingsConfigurable::class.java)
    },
) : JPanel(BorderLayout(0, JBUI.scale(PANEL_GAP))),
    Disposable,
    GitAssistantToolWindowService.RequestKindSelectionTarget {
    private val requestKindSelector = ComboBox(RequestKindOption.entries.toTypedArray())
    private val taskNoteTextArea =
        JTextArea(NOTE_ROWS, NOTE_COLUMNS).apply {
            lineWrap = true
            wrapStyleWord = true
        }
    private val stagedOnlyCheckBox = JCheckBox("Reason only from staged files")
    private val commitStyleSelector = ComboBox(CommitStyleOption.entries.toTypedArray())
    private val commitStylePanel = createCommitStylePanel()
    private val generateButton = JButton("Generate")
    private val configureAiButton = JButton("Configure AI")
    private val copyButton = JButton("Copy Output")
    private val outputTextArea =
        JTextArea("", OUTPUT_ROWS, OUTPUT_COLUMNS).apply {
            lineWrap = true
            wrapStyleWord = true
        }

    private val branchSummaryLabel = JLabel("Branch: (none)")
    private val stateSummaryLabel = JLabel("State: (none)")
    private val changedSummaryLabel = JLabel("Changed files: 0")
    private val untrackedSummaryLabel = JLabel("Untracked files: 0")
    private val sourceSummaryLabel = JLabel("Source: (none)")

    init {
        border = JBUI.Borders.empty(PANEL_BORDER)
        add(createOptionsPanel(), BorderLayout.NORTH)
        add(JBScrollPane(outputTextArea), BorderLayout.CENTER)
        add(createSummaryPanel(), BorderLayout.SOUTH)

        requestKindSelector.selectedItem = RequestKindOption.COMMIT_MESSAGE
        commitStyleSelector.selectedItem = CommitStyleOption.CONVENTIONAL_COMMIT
        updateCommitStyleVisibility()

        requestKindSelector.addActionListener { updateCommitStyleVisibility() }
        generateButton.addActionListener { startGeneration() }
        configureAiButton.addActionListener { configureAiAction() }
        copyButton.addActionListener {
            CopyPasteManager.getInstance().setContents(StringSelection(outputTextArea.text))
        }

        toolWindowService.registerPanel(this)
    }

    override fun dispose() {
        toolWindowService.unregisterPanel(this)
    }

    /**
     * Applies request selection from toolbar actions.
     */
    override fun selectRequestKind(requestKind: AssistantRequestKind) {
        requestKindSelector.selectedItem = RequestKindOption.from(requestKind)
        updateCommitStyleVisibility()
        requestKindSelector.requestFocusInWindow()
    }

    internal fun selectedOptionsForTesting(): AssistantOptions = selectedOptions()

    internal fun setTaskNoteForTesting(taskNote: String) {
        taskNoteTextArea.text = taskNote
    }

    internal fun setStagedOnlyForTesting(stagedOnly: Boolean) {
        stagedOnlyCheckBox.isSelected = stagedOnly
    }

    internal fun selectCommitStyleForTesting(commitStyle: CommitMessageStyle) {
        commitStyleSelector.selectedItem = CommitStyleOption.from(commitStyle)
    }

    internal fun isCommitStyleVisibleForTesting(): Boolean = commitStylePanel.isVisible

    internal fun applyResultForTesting(result: GitAssistantResult) {
        applyResult(result)
    }

    internal fun triggerConfigureAiForTesting() {
        configureAiButton.doClick()
    }

    internal fun outputTextForTesting(): String = outputTextArea.text

    internal fun summaryTextForTesting(): List<String> =
        listOf(
            branchSummaryLabel.text,
            stateSummaryLabel.text,
            changedSummaryLabel.text,
            untrackedSummaryLabel.text,
            sourceSummaryLabel.text,
        )

    private fun createOptionsPanel(): JComponent =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(labeledPanel("Request kind:", requestKindSelector))
            add(Box.createVerticalStrut(JBUI.scale(CONTROL_GAP)))
            add(JLabel("Task note (optional):"))
            add(Box.createVerticalStrut(JBUI.scale(SUB_CONTROL_GAP)))
            add(
                JBScrollPane(taskNoteTextArea).apply {
                    preferredSize = Dimension(JBUI.scale(NOTE_WIDTH), JBUI.scale(NOTE_HEIGHT))
                    maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(NOTE_HEIGHT))
                    alignmentX = Component.LEFT_ALIGNMENT
                },
            )
            add(Box.createVerticalStrut(JBUI.scale(CONTROL_GAP)))
            add(
                stagedOnlyCheckBox.apply {
                    alignmentX = Component.LEFT_ALIGNMENT
                },
            )
            add(Box.createVerticalStrut(JBUI.scale(CONTROL_GAP)))
            add(commitStylePanel)
            add(Box.createVerticalStrut(JBUI.scale(CONTROL_GAP)))
            add(buttonPanel())
        }

    private fun labeledPanel(
        label: String,
        component: JComponent,
    ): JComponent =
        JPanel(BorderLayout(JBUI.scale(SUB_CONTROL_GAP), 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            add(JLabel(label), BorderLayout.WEST)
            add(component, BorderLayout.CENTER)
        }

    private fun createCommitStylePanel(): JComponent = labeledPanel("Commit style:", commitStyleSelector)

    private fun buttonPanel(): JComponent =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.X_AXIS)
            alignmentX = Component.LEFT_ALIGNMENT
            add(generateButton)
            add(Box.createHorizontalStrut(JBUI.scale(SUB_CONTROL_GAP)))
            add(configureAiButton)
            add(Box.createHorizontalGlue())
            add(copyButton)
        }

    private fun createSummaryPanel(): JComponent =
        JPanel(GridLayout(0, 1, 0, JBUI.scale(2))).apply {
            border = BorderFactory.createEmptyBorder(JBUI.scale(SUMMARY_TOP_GAP), 0, 0, 0)
            add(branchSummaryLabel)
            add(stateSummaryLabel)
            add(changedSummaryLabel)
            add(untrackedSummaryLabel)
            add(sourceSummaryLabel)
        }

    private fun updateCommitStyleVisibility() {
        commitStylePanel.isVisible = selectedRequestKind() == AssistantRequestKind.COMMIT_MESSAGE
        revalidate()
        repaint()
    }

    private fun selectedRequestKind(): AssistantRequestKind {
        val selected = requestKindSelector.selectedItem as RequestKindOption
        return selected.kind
    }

    private fun selectedCommitStyle(): CommitMessageStyle {
        val selected = commitStyleSelector.selectedItem as CommitStyleOption
        return selected.style
    }

    private fun selectedOptions(): AssistantOptions =
        AssistantOptions(
            requestKind = selectedRequestKind(),
            commitMessageStyle = selectedCommitStyle(),
            userNote = taskNoteTextArea.text,
            stagedOnly = stagedOnlyCheckBox.isSelected,
        )

    private fun startGeneration() {
        setGenerationRunning(true)
        val options = selectedOptions()
        ProgressManager.getInstance().run(GenerationTask(options))
    }

    private fun setGenerationRunning(running: Boolean) {
        generateButton.isEnabled = !running
        configureAiButton.isEnabled = !running
        requestKindSelector.isEnabled = !running
        commitStyleSelector.isEnabled = !running
        stagedOnlyCheckBox.isEnabled = !running
    }

    private fun applyResult(result: GitAssistantResult) {
        outputTextArea.text = result.generatedText
        branchSummaryLabel.text = "Branch: ${result.branchName ?: "(none)"}"
        stateSummaryLabel.text = "State: ${result.gitState.name}"
        changedSummaryLabel.text = "Changed files: ${result.changedFileCount}"
        untrackedSummaryLabel.text = "Untracked files: ${result.untrackedFileCount}"
        sourceSummaryLabel.text = "Source: ${result.source.name}"
    }

    private fun backgroundTitleFor(requestKind: AssistantRequestKind): String =
        when (requestKind) {
            AssistantRequestKind.COMMIT_MESSAGE -> "Generating Commit Message"
            AssistantRequestKind.BRANCH_NAME -> "Suggesting Branch Names"
            AssistantRequestKind.CHANGE_SUMMARY -> "Summarizing Changes"
        }

    /**
     * Background task that performs assistant generation away from the UI thread.
     */
    private inner class GenerationTask(
        private val options: AssistantOptions,
    ) : Task.Backgroundable(project, backgroundTitleFor(options.requestKind), true) {
        private var generationResult: Result<GitAssistantResult>? = null

        override fun run(indicator: ProgressIndicator) {
            indicator.text = "Collecting Git context"
            generationResult =
                runCatching { assistantService.generate(project, options) }
                    .onFailure { error ->
                        if (error is ProcessCanceledException) {
                            throw error
                        }
                    }
        }

        override fun onSuccess() {
            setGenerationRunning(false)
            val result = generationResult ?: return
            result.fold(
                onSuccess = { assistantResult ->
                    applyResult(assistantResult)
                },
                onFailure = { error ->
                    Messages.showErrorDialog(
                        project,
                        error.message ?: "Could not generate assistant output.",
                        "AI Git Workflow Assistant",
                    )
                },
            )
        }

        override fun onCancel() {
            setGenerationRunning(false)
        }
    }

    private enum class RequestKindOption(
        val kind: AssistantRequestKind,
        private val label: String,
    ) {
        COMMIT_MESSAGE(AssistantRequestKind.COMMIT_MESSAGE, "Commit Message"),
        BRANCH_NAME(AssistantRequestKind.BRANCH_NAME, "Branch Names"),
        CHANGE_SUMMARY(AssistantRequestKind.CHANGE_SUMMARY, "Change Summary"),
        ;

        override fun toString(): String = label

        companion object {
            fun from(requestKind: AssistantRequestKind): RequestKindOption = entries.first { it.kind == requestKind }
        }
    }

    private enum class CommitStyleOption(
        val style: CommitMessageStyle,
        private val label: String,
    ) {
        CONCISE(CommitMessageStyle.CONCISE, "Concise"),
        CONVENTIONAL_COMMIT(CommitMessageStyle.CONVENTIONAL_COMMIT, "Conventional Commit"),
        DETAILED(CommitMessageStyle.DETAILED, "Detailed"),
        ;

        override fun toString(): String = label

        companion object {
            fun from(commitStyle: CommitMessageStyle): CommitStyleOption = entries.first { it.style == commitStyle }
        }
    }

    private companion object {
        const val NOTE_ROWS = 4
        const val NOTE_COLUMNS = 48
        const val OUTPUT_ROWS = 14
        const val OUTPUT_COLUMNS = 76
        const val NOTE_WIDTH = 460
        const val NOTE_HEIGHT = 100
        const val PANEL_GAP = 8
        const val PANEL_BORDER = 8
        const val CONTROL_GAP = 8
        const val SUB_CONTROL_GAP = 6
        const val SUMMARY_TOP_GAP = 8
    }
}
