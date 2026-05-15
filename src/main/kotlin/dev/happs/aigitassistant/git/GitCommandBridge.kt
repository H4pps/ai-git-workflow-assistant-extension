package dev.happs.aigitassistant.git

import git4idea.commands.GitCommand

/**
 * Immutable Git command request for the collector pipeline.
 */
data class GitCommandRequest(
    val command: GitCommand,
    val parameters: List<String> = emptyList(),
)

/**
 * Immutable Git command execution result used by the collector.
 */
data class GitCommandExecutionResult(
    val success: Boolean,
    val exitCode: Int,
    val output: String,
    val errorOutput: String,
)

/**
 * Minimal repository handle used by [GitContextCollector] for testability.
 */
interface GitRepositoryHandle {
    val rootPath: String
    val branchName: String?

    /**
     * Runs a single Git command against this repository.
     */
    fun run(request: GitCommandRequest): GitCommandExecutionResult
}
