package dev.happs.aigitassistant.service

import dev.happs.aigitassistant.ai.client.AiResponseSource
import dev.happs.aigitassistant.git.GitContextState
import dev.happs.aigitassistant.prompt.AssistantRequestKind

/**
 * Display-ready assistant generation result for the UI layer.
 */
data class GitAssistantResult(
    val title: String,
    val generatedText: String,
    val requestKind: AssistantRequestKind,
    val source: AiResponseSource,
    val gitState: GitContextState,
    val branchName: String?,
    val changedFileCount: Int,
    val untrackedFileCount: Int,
)
