# HackAlem Project Rules

## Authority and priorities

Official HackAlem rules and the released task are the highest authority. When requirements conflict, follow the stricter official rule and surface the conflict.

Use this priority order:

1. Exact compliance with the released task.
2. Working end-to-end MVP.
3. Core AI functionality.
4. Demo reliability.
5. Integration reliability.
6. Clear UX.
7. Secondary functionality.
8. Polish.

Prefer a small complete solution to a large incomplete solution. Never overengineer.

Build vertical slices:

`user action → frontend → backend → AI/tool/integration → result → visible UI response`

Finish one complete primary scenario before expanding.

## Required project guidance

Read the relevant project files before changing code:

- `PROJECT_STRUCTURE.md` for modules, ownership, and conflict reduction.
- `FRONTEND.md` for frontend implementation.
- `BACKEND.md` for backend architecture and code rules.
- `GIT.md` before commits, pushes, rebases, or integration work.
- `skills/debug/SKILL.md` for concrete failures.
- `skills/review-against-task/SKILL.md` for the final Code Rules gate and official-task review.

Search for existing implementations before creating new logic. Map directly affected imports, callers, models, mappings, persistence, configuration, and integrations before editing.

## Implementation discipline

Choose the smallest clean architecture that satisfies the actual requirements. Avoid unnecessary microservices, abstraction layers, interfaces, factories, DTO hierarchies, repository patterns, event buses, infrastructure, configuration frameworks, wrappers, generic systems, complex state management, and empty architectural layers.

Keep changes focused, preserve working behavior, and stop when the requested outcome works. Ask only when ambiguity can materially change the project, violate official rules, or risk another developer's work.

Use this default loop:

`read → understand → modify → minimally verify when necessary → Code Rules gate → report briefly`

## Testing

The developers manually test the product by default. Do not create unit, integration, frontend, backend, end-to-end, browser, snapshot, coverage, fixture, TDD, or CI testing infrastructure unless the user explicitly requests tests or automated verification.

Do not run large verification cycles after every change or write extensive test plans. Focused compile, build, start, migration, service-health, and manual scenario checks are allowed when needed to establish that the application works.

## Code rules

Do not write code comments by default. Use clear names, short functions, and obvious structure. Do not add development narration, provenance notes, or temporary-workaround commentary. Add a comment only when explicitly requested or required metadata is necessary for correctness.

Do not hardcode secrets, API keys, environment-specific URLs, or configuration values that belong in environment variables or centralized configuration.

- Do not create classes inside other classes without a necessary technical reason.
- Give every independently responsible concept its own class and file.
- Keep DTOs, mappers, validators, configuration, services, repositories, utilities, helpers, and other independent components out of nested classes created merely to reduce file count.
- Do not create god classes or methods that combine unrelated responsibilities.
- Apply the Single Responsibility Principle when it practically improves cohesion, ownership, and readability.
- Remove dead code, commented-out implementations, unused imports, fields, methods, temporary debug output, and diagnostic shortcuts before finishing a task.
- Reuse existing behavior and extract clearly repeated logic instead of maintaining duplicate implementations.
- Do not introduce abstractions, classes, or interfaces only for architectural appearance. Prefer the simplest structure that remains understandable and extensible for the real task.
- Choose class, method, variable, field, and file names that communicate their actual purpose.
- Do not leave TODO or FIXME markers instead of completing the current task. When work is deliberately deferred outside the current scope, report it explicitly.
- Do not damage the existing project structure merely to write a change faster.

## AI functionality

- Frontend must use only Java API responses and contain no mock transport or demo meeting data, because the team integrates against the actual backend even while its AI adapter is unfinished.

AI must provide meaningful product functionality, not decorative integration. Use model calls, structured outputs, tool calling, agent workflows, context construction, retrieval, retries, and rate-limit handling only when the task needs them.

Prefer structured outputs when application logic depends on model output. Keep AI integration centralized enough to understand and debug. Tool definitions need a clear purpose, minimal validated input, predictable output, and clear errors. Handle malformed responses and external failures practically.

## Team coordination

All developers and Codex instances must work from the same official requirements. Divide ownership immediately and favor independent modules that reduce merge conflicts. Assign one temporary owner to highly conflict-prone files. Coordinate before another developer changes those files.

Build one end-to-end slice early, integrate frequently through `develop`, keep `develop` demonstrable, and manually test throughout development.

Use `debug` for concrete failures. Use `review-against-task` in Code Rules mode after every code-changing task and in task-compliance mode when comparing the implementation with official requirements.

## Mandatory final code quality gate

After every task that changes frontend, backend, AI, integration, infrastructure, scripts, or any other application code, finish in this order:

`implementation → necessary verification → review-against-task Code Rules mode → safe fixes → final response`

Fix every finding that can be corrected safely without changing intended behavior. If a finding requires a business decision, changes an external contract, or risks overwriting another developer's work, do not refactor blindly; report it clearly.

Do not perform another code-changing step after the quality gate without running Code Rules mode again. This is the final code-quality check for every technology and project area.

## Repository content and history

Keep this repository limited to material relevant to the competition project. Do not add preparation history, unrelated provenance discussion, copied reference repositories, credentials, personal configuration, or machine-specific metadata.

Never backdate commits, falsify timestamps, invent history, or rewrite metadata to misrepresent when work occurred. Commit and push only when explicitly requested. Use concise human commit messages without assistant attribution.

## Changelog

Record every meaningful project change in the single `CHANGELOG.md`. Each entry uses the actual local date and time to the minute and states what changed and why. Append chronologically and never alter old timestamps to misrepresent history.
