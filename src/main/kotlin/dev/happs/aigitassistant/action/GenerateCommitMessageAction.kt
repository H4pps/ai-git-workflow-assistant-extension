package dev.happs.aigitassistant.action

import dev.happs.aigitassistant.prompt.AssistantRequestKind

/**
 * Generates an editable commit message from current Git changes.
 */
class GenerateCommitMessageAction :
    AssistantRequestAction(
        requestKind = AssistantRequestKind.COMMIT_MESSAGE,
        backgroundTitle = "Generating Commit Message",
    )
