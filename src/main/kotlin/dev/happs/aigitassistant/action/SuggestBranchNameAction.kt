package dev.happs.aigitassistant.action

import dev.happs.aigitassistant.ai.prompt.AssistantRequestKind

/**
 * Generates editable branch name suggestions from current Git changes.
 */
class SuggestBranchNameAction :
    AssistantRequestAction(
        requestKind = AssistantRequestKind.BRANCH_NAME,
    )
