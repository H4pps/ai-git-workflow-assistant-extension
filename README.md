# AI Git Workflow Assistant

AI Git Workflow Assistant is a small IntelliJ Platform plugin that helps developers prepare Git changes with AI-assisted commit messages, branch names, and change summaries.

## Features

- Right-side `AI Git` tool window for the main workflow.
- VCS menu actions that open the tool window and preselect a task.
- Commit message generation in Conventional Commits format.
- Branch name suggestions from current Git changes and an optional task note.
- Change summaries with risks and suggested tests.
- Optional "Reason only from staged files" mode.
- Editable generated output with copy support.

## How It Works

The plugin collects current Git context from the open IntelliJ project, builds a structured request, and sends it to the selected AI provider.

Collected context includes:

- repository root
- current branch
- changed file paths
- untracked file paths
- staged diff
- unstaged diff
- truncation metadata

Git integration uses IntelliJ's bundled `Git4Idea` APIs and command wrappers. The plugin does not shell out directly with `ProcessBuilder` and does not use JGit.

## AI Providers

The plugin uses an OpenAI-compatible Chat Completions provider. Configure it before generating output:

1. Open `Settings > Tools > AI Git Workflow Assistant`.
2. Configure the base URL, model, and API key.
3. Use the `AI Git` tool window and click `Generate`.

Defaults:

- Base URL: `https://api.openai.com/v1`
- Model: `gpt-5-nano`

The settings page includes links to [OpenAI API keys](https://platform.openai.com/settings/organization/api-keys) and [OpenRouter free models](https://openrouter.ai/collections/free-models).

Generation sends Git diffs, changed paths, and task notes to the configured API. API keys are stored through IntelliJ Password Safe. The plugin avoids logging API keys, authorization headers, full prompts, full diffs, request JSON, and raw provider error bodies.

## Running Locally

Requirements:

- JDK 21
- IntelliJ Platform Gradle plugin dependencies downloaded by Gradle

Run the plugin in an IntelliJ sandbox:

```bash
./gradlew runIde
```

Useful verification commands:

```bash
./gradlew autoFormat
./gradlew test
./gradlew qualityCheck
./gradlew coverageCheck
./gradlew verifyPluginStructure
```

`qualityCheck` runs tests, coverage verification, Kotlin style checks, and Detekt. Line coverage is required to stay at or above 80%.

## Project Structure

```text
src/main/kotlin/dev/happs/aigitassistant/
  action/       VCS menu actions
  ai/client/    AI provider abstraction and implementations
  ai/prompt/    request options, prompt context, and output contracts
  git/          Git4Idea-backed Git context collection
  service/      orchestration services
  settings/     provider settings and credential storage
  ui/           right-side tool window
```

## Design Notes

- Actions stay thin and delegate to services.
- Git collection, prompt construction, provider selection, and UI are separated.
- Model output is always editable; the plugin does not create commits, branches, or Git operations automatically.
- Output contracts for real models live in the system prompt, while the user message carries only Git context and task input.
- Large diffs are truncated before being sent to the model to reduce prompt noise.
- Runtime logs use structured metadata only.

## Current Scope

This is intentionally a small plugin, not a production Git assistant. It does not include streaming chat, history, automatic commits, branch creation, merge conflict handling, or remote repository operations.
