package dev.happs.aigitassistant.service

import dev.happs.aigitassistant.prompt.AssistantRequestKind

/**
 * Holds cross-action tool window state, including pending task selection.
 */
class GitAssistantToolWindowService {
    private var pendingRequestKind: AssistantRequestKind? = null
    private var activePanel: RequestKindSelectionTarget? = null

    /**
     * Requests selection of [requestKind] in the tool window.
     */
    fun requestKindSelection(requestKind: AssistantRequestKind) {
        pendingRequestKind = requestKind
        applyPendingSelection()
    }

    /**
     * Registers the currently visible panel as the target for task selection.
     */
    fun registerPanel(panel: RequestKindSelectionTarget) {
        activePanel = panel
        applyPendingSelection()
    }

    /**
     * Unregisters [panel] when its content is disposed.
     */
    fun unregisterPanel(panel: RequestKindSelectionTarget) {
        if (activePanel === panel) {
            activePanel = null
        }
    }

    private fun applyPendingSelection() {
        val panel = activePanel ?: return
        val requestKind = pendingRequestKind ?: return
        pendingRequestKind = null
        panel.selectRequestKind(requestKind)
    }

    /**
     * Panel contract used to apply pending request-kind selections.
     */
    fun interface RequestKindSelectionTarget {
        fun selectRequestKind(requestKind: AssistantRequestKind)
    }
}
