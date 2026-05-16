package dev.happs.aigitassistant.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import dev.happs.aigitassistant.prompt.AssistantOptions
import dev.happs.aigitassistant.prompt.AssistantRequestKind
import dev.happs.aigitassistant.prompt.CommitMessageStyle
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Dimension
import javax.swing.Box
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Dialog for selecting optional assistant request settings before generation starts.
 */
class AssistantOptionsDialog(
    project: Project,
    private val requestKind: AssistantRequestKind,
) : DialogWrapper(project) {
    private val taskNoteTextArea =
        JTextArea(NOTE_ROWS, NOTE_COLUMNS).apply {
            lineWrap = true
            wrapStyleWord = true
        }

    private val commitStyleSelector =
        ComboBox(CommitMessageStyle.entries.toTypedArray()).apply {
            selectedItem = CommitMessageStyle.CONVENTIONAL_COMMIT
        }

    init {
        title = titleFor(requestKind)
        setOKButtonText("Generate")
        init()
    }

    /**
     * Converts the selected options to [AssistantOptions].
     */
    fun selectedOptions(): AssistantOptions {
        val commitStyle =
            if (requestKind == AssistantRequestKind.COMMIT_MESSAGE) {
                commitStyleSelector.selectedItem as CommitMessageStyle
            } else {
                CommitMessageStyle.CONVENTIONAL_COMMIT
            }
        return AssistantOptions(
            requestKind = requestKind,
            commitMessageStyle = commitStyle,
            userNote = taskNoteTextArea.text,
        )
    }

    /**
     * Creates the options panel for the selected request kind.
     */
    override fun createCenterPanel(): JComponent =
        JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(PANEL_BORDER)
            add(optionsPanel(), BorderLayout.CENTER)
        }

    private fun optionsPanel(): JComponent =
        JPanel().apply {
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            add(JLabel("Task note (optional):"))
            add(Box.createVerticalStrut(JBUI.scale(SPACING)))
            add(
                JBScrollPane(taskNoteTextArea).apply {
                    preferredSize = Dimension(JBUI.scale(NOTE_WIDTH), JBUI.scale(NOTE_HEIGHT))
                    maximumSize = Dimension(Int.MAX_VALUE, JBUI.scale(NOTE_HEIGHT))
                    alignmentX = Component.LEFT_ALIGNMENT
                },
            )
            if (requestKind == AssistantRequestKind.COMMIT_MESSAGE) {
                add(Box.createVerticalStrut(JBUI.scale(SPACING)))
                add(commitStylePanel())
            }
        }

    private fun commitStylePanel(): JComponent =
        JPanel(BorderLayout(JBUI.scale(SPACING), 0)).apply {
            alignmentX = Component.LEFT_ALIGNMENT
            add(JLabel("Commit style:"), BorderLayout.WEST)
            add(commitStyleSelector, BorderLayout.CENTER)
        }

    private fun titleFor(kind: AssistantRequestKind): String =
        when (kind) {
            AssistantRequestKind.COMMIT_MESSAGE -> "Generate Commit Message"
            AssistantRequestKind.BRANCH_NAME -> "Suggest Branch Names"
            AssistantRequestKind.CHANGE_SUMMARY -> "Summarize Changes"
        }

    private companion object {
        const val NOTE_ROWS = 4
        const val NOTE_COLUMNS = 48
        const val NOTE_WIDTH = 460
        const val NOTE_HEIGHT = 100
        const val SPACING = 8
        const val PANEL_BORDER = 8
    }
}
