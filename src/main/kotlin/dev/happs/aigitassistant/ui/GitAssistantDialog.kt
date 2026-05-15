package dev.happs.aigitassistant.ui

import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import dev.happs.aigitassistant.service.GitAssistantResult
import java.awt.BorderLayout
import java.awt.GridLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.ActionEvent
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTextArea

/**
 * Editable dialog that displays generated assistant text.
 */
class GitAssistantDialog(
    project: Project,
    private val result: GitAssistantResult,
) : DialogWrapper(project) {
    private val textArea =
        JTextArea(result.generatedText, TEXT_ROWS, TEXT_COLUMNS).apply {
            isEditable = true
            lineWrap = true
            wrapStyleWord = true
        }

    init {
        title = result.title
        setOKButtonText("Close")
        init()
    }

    /**
     * Creates the editable assistant result panel.
     */
    override fun createCenterPanel(): JComponent =
        JPanel(BorderLayout(0, JBUI.scale(PANEL_GAP))).apply {
            border = JBUI.Borders.empty(PANEL_BORDER)
            add(summaryPanel(), BorderLayout.NORTH)
            add(JBScrollPane(textArea), BorderLayout.CENTER)
        }

    /**
     * Adds Copy and Close actions.
     */
    override fun createActions(): Array<Action> = arrayOf(copyAction(), okAction)

    /**
     * Builds a concise Git context summary for the generated text.
     */
    private fun summaryPanel(): JComponent =
        JPanel(GridLayout(0, 1, 0, JBUI.scale(2))).apply {
            add(JLabel("Branch: ${result.branchName ?: "(none)"}"))
            add(
                JLabel(
                    "State: ${result.gitState.name} | " +
                        "Changed files: ${result.changedFileCount} | " +
                        "Untracked files: ${result.untrackedFileCount}",
                ),
            )
            add(JLabel("Source: ${result.source.name}"))
        }

    /**
     * Copies the currently edited text to the system clipboard.
     */
    private fun copyAction(): Action =
        object : AbstractAction("Copy") {
            override fun actionPerformed(event: ActionEvent?) {
                CopyPasteManager.getInstance().setContents(StringSelection(textArea.text))
            }
        }

    private companion object {
        const val TEXT_ROWS = 14
        const val TEXT_COLUMNS = 76
        const val PANEL_GAP = 8
        const val PANEL_BORDER = 8
    }
}
