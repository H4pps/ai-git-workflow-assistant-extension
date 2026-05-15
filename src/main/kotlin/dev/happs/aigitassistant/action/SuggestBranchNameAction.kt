package dev.happs.aigitassistant.action

import dev.happs.aigitassistant.prompt.AssistantOptions
import dev.happs.aigitassistant.prompt.AssistantRequestKind

/**
 * Generates editable branch name suggestions from current Git changes.
 */
class SuggestBranchNameAction :
    AssistantRequestAction(
        options = AssistantOptions(requestKind = AssistantRequestKind.BRANCH_NAME),
        backgroundTitle = "Suggesting Branch Names",
    )
