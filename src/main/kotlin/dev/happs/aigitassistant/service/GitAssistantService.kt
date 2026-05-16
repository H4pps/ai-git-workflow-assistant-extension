package dev.happs.aigitassistant.service

import com.intellij.openapi.project.Project
import dev.happs.aigitassistant.git.GitContext
import dev.happs.aigitassistant.git.GitContextCollector
import dev.happs.aigitassistant.prompt.AssistantOptions
import dev.happs.aigitassistant.prompt.AssistantRequestKind
import dev.happs.aigitassistant.prompt.PromptBuilder
import dev.happs.aigitassistant.service.ai.AiClientProvider
import dev.happs.aigitassistant.service.ai.DefaultAiClientProvider

/**
 * Coordinates Git context, prompt construction, and AI response generation.
 */
class GitAssistantService(
    private val gitContextCollector: GitContextCollector = GitContextCollector(),
    private val promptBuilder: PromptBuilder = PromptBuilder(),
    private val aiClientProvider: AiClientProvider = DefaultAiClientProvider(),
) {
    /**
     * Collects Git context from [project] and generates a display-ready result.
     */
    fun generate(
        project: Project,
        options: AssistantOptions,
    ): GitAssistantResult = generate(gitContextCollector.collect(project), options)

    /**
     * Generates a display-ready result from an already collected [context].
     */
    fun generate(
        context: GitContext,
        options: AssistantOptions,
    ): GitAssistantResult {
        val request = promptBuilder.build(context, options)
        val response = aiClientProvider.currentClient().generate(request)
        return GitAssistantResult(
            title = titleFor(options.requestKind),
            generatedText = response.generatedText,
            requestKind = options.requestKind,
            source = response.source,
            gitState = context.state,
            branchName = context.branchName,
            changedFileCount = context.changedFilePaths.size,
            untrackedFileCount = context.untrackedFilePaths.size,
        )
    }

    /**
     * Returns the UI title for [requestKind].
     */
    private fun titleFor(requestKind: AssistantRequestKind): String =
        when (requestKind) {
            AssistantRequestKind.COMMIT_MESSAGE -> "Generated Commit Message"
            AssistantRequestKind.BRANCH_NAME -> "Suggested Branch Names"
            AssistantRequestKind.CHANGE_SUMMARY -> "Change Summary"
        }
}
