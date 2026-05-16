package dev.happs.aigitassistant.action

import dev.happs.aigitassistant.ai.prompt.AssistantRequestKind

/**
 * Generates an editable change summary from current Git changes.
 */
class SummarizeChangesAction :
    AssistantRequestAction(
        requestKind = AssistantRequestKind.CHANGE_SUMMARY,
    )
