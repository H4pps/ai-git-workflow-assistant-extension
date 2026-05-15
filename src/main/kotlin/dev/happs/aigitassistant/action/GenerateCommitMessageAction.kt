package dev.happs.aigitassistant.action

import dev.happs.aigitassistant.prompt.AssistantOptions
import dev.happs.aigitassistant.prompt.AssistantRequestKind
import dev.happs.aigitassistant.prompt.CommitMessageStyle

/**
 * Generates an editable commit message from current Git changes.
 */
class GenerateCommitMessageAction :
    AssistantRequestAction(
        options =
            AssistantOptions(
                requestKind = AssistantRequestKind.COMMIT_MESSAGE,
                commitMessageStyle = CommitMessageStyle.CONVENTIONAL_COMMIT,
            ),
        backgroundTitle = "Generating Commit Message",
    )
