# Codex Project Instructions

This file defines the operating workflow for Codex in this repository. The `specs/` directory is local-only agent context and is intentionally ignored by Git.

## Project Context

- Project: AI Git Workflow Assistant.
- Goal: build a small, well-structured AI-related IntelliJ Platform plugin for the JetBrains AI Assistant internship task.
- Primary planning context lives in `specs/project-description.md`.
- Development phase checklists live in `specs/phases/`.
- Project-scoped Codex subagent configuration lives in `.codex/config.toml` and `.codex/agents/*.toml`.

Before making non-trivial changes, read the relevant files under `specs/` and update phase checkboxes as work is completed.

## Agent Roles

### Main Thread

- Configured in `.codex/config.toml`.
- Intended model: `gpt-5.5`.
- Intended reasoning effort: `high`.
- Responsibility: orchestration, planning, subagent spawning, integration, final decisions, and user communication.
- The main thread owns repository-wide consistency and must review subagent output before treating it as complete.

### Coder

- Config file: `.codex/agents/coder.toml`.
- Intended model: `gpt-5.3-codex`.
- Intended reasoning effort: `xhigh`.
- Responsibility: implementation and refactoring.
- Spawn as a coding worker with a clearly defined write scope.
- For large changes, spawn multiple coder agents only when their write sets are disjoint.

### Checker

- Config file: `.codex/agents/checker.toml`.
- Intended model: `gpt-5.3-codex-spark`.
- Intended reasoning effort: `medium`.
- Responsibility: run formatters, lints, tests, build checks, static checks, and make quick small fixes when something breaks.
- If `gpt-5.3-codex-spark` is not available in the current Codex runtime, use the closest available fast coding/checking model and state the substitution.

### Code Reviewer

- Config file: `.codex/agents/code-reviewer.toml`.
- Intended model: `gpt-5.5`.
- Intended reasoning effort: `high`.
- Responsibility: code review, bug finding, architecture review, test-gap review, and risk assessment.
- Default stance is read-only review unless the main thread explicitly asks for a scoped fix.

## Default Workflow

When the user says `use default workflow`, `use DW`, or `DW for ...`, apply this workflow without requiring the user to name the agents explicitly.

1. Main thread clarifies the target task from the user request and relevant `specs/` files.
2. Main thread creates or updates a short plan.
3. Main thread assigns implementation to one or more coder agents when code changes are required.
4. Main thread gives each coder an explicit ownership boundary and tells them they are not alone in the codebase.
5. Main thread integrates coder results and resolves conflicts or inconsistencies.
6. Main thread asks checker to run the appropriate verification commands and fix only small breakages inside the checked scope.
7. Main thread asks code reviewer to review the resulting diff for bugs, regressions, architecture issues, and missing tests.
8. Main thread applies or delegates any required follow-up fixes.
9. Main thread updates relevant `specs/phases/*.md` checkboxes.
10. Main thread gives the user a concise summary of changes, verification, and remaining risks.

## Workflow Variants

- `DW for planning`: use the main thread for planning. Optionally ask the code reviewer for a design review if the plan is large or risky. Do not spawn coder/checker unless implementation is requested.
- `DW for implementation`: use coder, checker, and code reviewer unless the change is trivial.
- `DW for review`: use code reviewer first, then checker if commands are needed to confirm findings.
- `DW for cleanup`: use checker for mechanical cleanup and code reviewer for non-trivial behavior or architecture risks.

## Delegation Rules

- Spawn subagents only when the user explicitly asks for subagents, delegation, parallel work, or the default workflow.
- Do not duplicate work between main and subagents.
- Do not hand off the immediate blocking task if the main thread can make progress locally.
- Give workers concrete, self-contained tasks.
- For code changes, define file or module ownership.
- Tell workers not to revert user changes or other agents' changes.
- Review subagent changes before finalizing.

## Implementation Standards

- Prefer existing repository style over new abstractions.
- Keep the MVP small and explainable.
- Use TDD for coding tasks: write or update focused failing tests first, then implement the smallest code change needed to pass them.
- If a task cannot reasonably be test-first, state why before implementing and keep the manual verification explicit.
- Use Kotlin and IntelliJ Platform SDK patterns appropriate for a plugin.
- Isolate IDE actions, Git context collection, prompt building, AI client logic, services, and UI.
- Use structured logging for meaningful runtime events and failures. Prefer stable event names plus key-value context over ad hoc prose logs.
- Do not log secrets, API keys, full prompts, or full diffs by default.
- Keep generated AI output editable by the user.
- Do not hardcode API keys or secrets.
- Keep `specs/` local-only; do not move private planning notes into public docs.

## Verification Standards

- Treat tests as part of the implementation, not as a final cleanup step.
- Run focused tests after non-trivial Kotlin changes.
- Run Gradle build or plugin verification steps when project setup changes.
- Record verification commands and outcomes in the final response.
- If a check cannot be run, explain why and state the residual risk.

## Git Safety

- Do not revert user changes unless the user explicitly asks.
- Do not use destructive Git commands such as `git reset --hard` or `git checkout --` without explicit instruction.
- Keep public-facing repository files free of secrets and private local planning notes.
