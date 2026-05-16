package dev.happs.aigitassistant.action

import dev.happs.aigitassistant.ai.prompt.AssistantRequestKind

/**
 * Generates an editable commit message from current Git changes.
 */
class GenerateCommitMessageAction :
    AssistantRequestAction(
        requestKind = AssistantRequestKind.COMMIT_MESSAGE,
    )
