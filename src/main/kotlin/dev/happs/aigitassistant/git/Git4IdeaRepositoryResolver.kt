package dev.happs.aigitassistant.git

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import git4idea.commands.Git
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager

/**
 * Resolves a Git repository from an IntelliJ project.
 */
interface GitRepositoryResolver {
    /**
     * Returns a handle for the current repository, or `null` when no Git repository is available.
     */
    fun resolve(project: Project): GitRepositoryHandle?
}

/**
 * Git4Idea-based repository resolver and command bridge.
 */
class Git4IdeaRepositoryResolver(
    private val gitProvider: () -> Git = { Git.getInstance() },
) : GitRepositoryResolver {
    /**
     * Resolves a repository by preferring the repository containing the project base path.
     */
    override fun resolve(project: Project): GitRepositoryHandle? {
        val repositoryManager = GitRepositoryManager.getInstance(project)
        val projectRoot =
            project.basePath
                ?.let { LocalFileSystem.getInstance().findFileByPath(it) }
        val repository =
            projectRoot?.let(repositoryManager::getRepositoryForFileQuick)
                ?: repositoryManager.repositories.firstOrNull()
                ?: return null
        return Git4IdeaRepositoryHandle(project, repository, gitProvider())
    }
}

/**
 * Production implementation backed by [GitRepository] and Git4Idea command handlers.
 */
private class Git4IdeaRepositoryHandle(
    private val project: Project,
    private val repository: GitRepository,
    private val git: Git,
) : GitRepositoryHandle {
    override val rootPath: String = repository.root.path
    override val branchName: String? = repository.currentBranch?.name ?: repository.currentBranchName

    /**
     * Executes [request] using [GitLineHandler] and returns the joined output.
     */
    override fun run(request: GitCommandRequest): GitCommandExecutionResult {
        val handler = GitLineHandler(project, repository.root, request.command)
        if (request.parameters.isNotEmpty()) {
            handler.addParameters(request.parameters)
        }

        val result = git.runCommand(handler)
        return GitCommandExecutionResult(
            success = result.success(),
            exitCode = result.exitCode,
            output = result.outputAsJoinedString,
            errorOutput = result.errorOutputAsJoinedString,
        )
    }
}
