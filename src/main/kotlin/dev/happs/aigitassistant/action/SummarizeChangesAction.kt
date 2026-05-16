package dev.happs.aigitassistant.action

import dev.happs.aigitassistant.prompt.AssistantRequestKind

/**
 * Generates an editable change summary from current Git changes.
 */
class SummarizeChangesAction :
    AssistantRequestAction(
        requestKind = AssistantRequestKind.CHANGE_SUMMARY,
        backgroundTitle = "Summarizing Changes",
    )
